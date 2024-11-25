/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.cienspools.audio;

import cientistavuador.cienspools.audio.data.Audio;
import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.util.ObjectCleaner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.openal.AL11.*;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public class AudioStream implements Audio {

    public static AudioStream newStream(InputStream in) {
        AudioStream stream = new AudioStream(in);
        stream.start();
        return stream;
    }

    public static AudioStream newStream(byte[] oggFile) {
        return newStream(new ByteArrayInputStream(oggFile));
    }

    public static final float IDEAL_BUFFERED_LENGTH = 0.5f;
    public static final int IDEAL_NUMBER_OF_BUFFERS = 8;
    public static final float IDEAL_LENGTH_PER_BUFFER = IDEAL_BUFFERED_LENGTH / IDEAL_NUMBER_OF_BUFFERS;
    public static final int SLEEP_TIME = 500;

    private final InputStream in;
    private final Object threadNotifier = new Object();

    private volatile int channels = -1;
    private volatile int sampleRate = -1;
    private volatile int lengthSamples = -1;
    private volatile int bufferedSamples = 0;
    private volatile int seek = -1;
    private volatile int currentSample = 0;

    private final CompletableFuture<Void> waitForHeadersFuture = new CompletableFuture<>();
    private final ConcurrentLinkedQueue<short[]> buffers = new ConcurrentLinkedQueue<>();
    private final Set<Integer> audioBuffers = new HashSet<>();
    private final ConcurrentLinkedQueue<Integer> forRecycling = new ConcurrentLinkedQueue<>();

    private volatile Throwable throwable = null;
    private volatile boolean looping = false;
    private volatile boolean finished = false;

    protected AudioStream(InputStream in) {
        Objects.requireNonNull(in, "in is null");
        this.in = in;

        registerForCleaning();
    }

    private void registerForCleaning() {
        final Set<Integer> finalAudioBuffers = this.audioBuffers;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                for (Integer i : finalAudioBuffers) {
                    alDeleteSources(i);
                }
                finalAudioBuffers.clear();
            });
        });
    }

    protected void start() {
        Thread decoderThread = new Thread(() -> {
            try {
                run();
            } catch (Throwable t) {
                this.throwable = t;
                this.waitForHeadersFuture.completeExceptionally(t);
            }
            this.finished = true;
        }, "Audio Stream Thread - " + hashCode());
        decoderThread.setDaemon(true);
        decoderThread.start();
    }

    private ByteBuffer readData() throws IOException {
        try (this.in) {
            byte[] data = this.in.readAllBytes();
            ByteBuffer b = memAlloc(data.length).put(data).flip();
            return b;
        }
    }

    private long openDecoder(ByteBuffer nativeMemory) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(1);
            long decoder = stb_vorbis_open_memory(nativeMemory, buffer, null);
            int error = buffer.get();
            if (error != VORBIS__no_error) {
                throw new IOException("Vorbis error " + error);
            }
            return decoder;
        }
    }

    private void readHeader(long decoder) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            stb_vorbis_get_info(decoder, info);
            this.channels = info.channels();
            this.sampleRate = info.sample_rate();
            this.lengthSamples = stb_vorbis_stream_length_in_samples(decoder) * this.channels;
        }
    }

    private short[] readFrame(long decoder) throws IOException {
        short[] buffer = new short[4096 * Short.BYTES * getChannels()];

        int read = stb_vorbis_get_frame_short_interleaved(
                decoder, getChannels(), buffer) * getChannels();

        int error = stb_vorbis_get_error(decoder);
        if (error != VORBIS__no_error) {
            throw new IOException("Vorbis error: " + error);
        }
        return Arrays.copyOf(buffer, read);
    }

    private short[] readBuffer(long decoder) throws IOException {
        short[] buffer = new short[8192];
        int bufferPosition = 0;

        short[] read;
        while (Audio.length(bufferPosition, getChannels(), getSampleRate()) < IDEAL_LENGTH_PER_BUFFER
                && ((read = readFrame(decoder)).length != 0)) {
            int freeSpace = buffer.length - bufferPosition;
            if (freeSpace < read.length) {
                buffer = Arrays.copyOf(buffer, (buffer.length * 2) + (read.length - freeSpace));
            }
            System.arraycopy(read, 0, buffer, bufferPosition, read.length);
            bufferPosition += read.length;
        }

        return Arrays.copyOf(buffer, bufferPosition);
    }

    private void run() throws IOException {
        ByteBuffer nativeMemory = readData();
        try {
            long decoder = openDecoder(nativeMemory);
            try {
                readHeader(decoder);

                this.waitForHeadersFuture.complete(null);

                do {
                    stb_vorbis_seek(decoder, 0);
                    
                    mainLoop:
                    while (!isFinished()) {
                        if (this.seek != -1) {
                            stb_vorbis_seek(decoder, this.seek);
                            this.currentSample = this.seek * getChannels();
                            this.seek = -1;
                            this.buffers.clear();
                        }
                        while (getBufferedLength() < IDEAL_BUFFERED_LENGTH) {
                            short[] buffer = readBuffer(decoder);
                            if (buffer.length == 0) {
                                break mainLoop;
                            }
                            this.bufferedSamples += buffer.length;
                            this.buffers.add(buffer);
                        }
                        try {
                            synchronized (this.threadNotifier) {
                                this.threadNotifier.wait(SLEEP_TIME);
                            }
                        } catch (InterruptedException ex) {
                            throw new IOException(ex);
                        }
                    }
                } while (isLooping() && !isFinished());
            } finally {
                stb_vorbis_close(decoder);
            }
        } finally {
            memFree(nativeMemory);
        }
    }

    public int getChannels() {
        return channels;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getLengthSamples() {
        return lengthSamples;
    }

    public float getLength() {
        return Audio.length(getLengthSamples(), getChannels(), getSampleRate());
    }

    public int getBufferedSamples() {
        return bufferedSamples;
    }

    public float getBufferedLength() {
        return Audio.length(getBufferedSamples(), getChannels(), getSampleRate());
    }

    public void waitForHeaders() {
        this.waitForHeadersFuture.join();
    }

    public int nextBuffer() {
        if (this.buffers.size() <= (IDEAL_NUMBER_OF_BUFFERS / 2)) {
            synchronized (this.threadNotifier) {
                this.threadNotifier.notify();
            }
        }

        short[] data = this.buffers.poll();
        if (data == null) {
            return 0;
        }

        Integer buffer = this.forRecycling.poll();
        if (buffer == null) {
            buffer = alGenBuffers();
            this.audioBuffers.add(buffer);
        }
        if (getChannels() == 1) {
            alBufferData(buffer, AL_FORMAT_MONO16, data, getSampleRate());
        } else {
            alBufferData(buffer, AL_FORMAT_STEREO16, data, getSampleRate());
        }
        this.bufferedSamples -= data.length;
        this.currentSample += data.length;
        if (this.currentSample > getLengthSamples()) {
            this.currentSample = this.currentSample % getLengthSamples();
        }

        return buffer;
    }

    public boolean returnBuffer(int buffer) {
        if (!this.audioBuffers.contains(buffer)) {
            return false;
        }
        this.forRecycling.add(buffer);
        return true;
    }

    public void seek(int sample) {
        int samplesMono = getLengthSamples() / getChannels();
        if (sample < 0 || sample >= samplesMono) {
            throw new IndexOutOfBoundsException(sample);
        }
        this.seek = sample;
    }

    public void seek(float seconds) {
        int sample = Math.min(Math.max((int) (seconds * getSampleRate()), 0), getLengthSamples() - 1);
        seek(sample);
    }

    public int getCurrentSample() {
        return currentSample;
    }

    public float getCurrentSampleSeconds() {
        return Audio.length(getCurrentSample(), getChannels(), getSampleRate());
    }

    public boolean isLooping() {
        return looping;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void finish() {
        this.finished = true;
        synchronized (this.threadNotifier) {
            this.threadNotifier.notify();
        }
    }

    public boolean isFinished() {
        return this.finished;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void manualFree() {
        final Set<Integer> finalAudioBuffers = this.audioBuffers;

        for (Integer i : finalAudioBuffers) {
            alDeleteSources(i);
        }
        finalAudioBuffers.clear();

        this.forRecycling.clear();
    }

    @Override
    public String getId() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
