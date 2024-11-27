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
package cientistavuador.cienspools.audio.data.impl;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.audio.data.InputStreamFactory;
import cientistavuador.cienspools.util.ObjectCleaner;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import static org.lwjgl.openal.AL11.*;
import cientistavuador.cienspools.audio.data.AudioStream;

/**
 *
 * @author Cien
 */
public class AudioStreamImpl implements AudioStream {

    private static void deliverThrowable(WeakReference<AudioStreamImpl> stream, Throwable t) {
        AudioStreamImpl s = stream.get();
        if (s != null) {
            s.onThrowable(t);
        }
    }

    private static void audioThreadMain(WeakReference<AudioStreamImpl> stream) {
        try {
            audioThreadTask(stream);
        } catch (Throwable t) {
            deliverThrowable(stream, t);
        }
    }

    private static boolean audioThreadCanRun(WeakReference<AudioStreamImpl> stream) {
        AudioStreamImpl s = stream.get();
        if (s == null) {
            return false;
        }
        return !s.isClosed();
    }

    private static boolean audioThreadIsLooping(WeakReference<AudioStreamImpl> stream) {
        AudioStreamImpl s = stream.get();
        if (s != null) {
            return s.isLooping();
        }
        return false;
    }

    private static AudioDataStream audioThreadOpenNewStream(WeakReference<AudioStreamImpl> stream)
            throws IOException {
        AudioStreamImpl s = stream.get();
        if (s == null) {
            return null;
        }
        AudioDataStream e = new AudioDataStream(s.getInputStreamFactory().newInputStream());
        e.start();
        return e;
    }

    private static void audioThreadSendInformation(
            WeakReference<AudioStreamImpl> stream, int channels, int sampleRate) {
        AudioStreamImpl s = stream.get();
        if (s != null) {
            s.onInformationReceived(channels, sampleRate);
        }
    }

    private static int audioThreadNumberOfBuffers(WeakReference<AudioStreamImpl> stream) {
        AudioStreamImpl s = stream.get();
        if (s != null) {
            return s.getNumberOfQueuedSampleArrays();
        }
        return -1;
    }

    private static void audioThreadDeliverBuffer(WeakReference<AudioStreamImpl> stream, short[] samples) {
        AudioStreamImpl s = stream.get();
        if (s != null) {
            s.onSamplesReceived(samples);
        }
    }

    private static int audioThreadSeek(WeakReference<AudioStreamImpl> stream) {
        AudioStreamImpl s = stream.get();
        if (s != null) {
            return s.getSeekAndClear();
        }
        return -1;
    }

    private static void audioThreadUpdateCurrentSample(WeakReference<AudioStreamImpl> stream, AudioDataStream dataStream) {
        AudioStreamImpl s = stream.get();
        if (s != null) {
            s.onCurrentSampleUpdate(dataStream.getSamplesRead());
        }
    }

    private static void audioThreadTask(WeakReference<AudioStreamImpl> stream) throws Throwable {
        AudioDataStream currentStream = null;
        try {
            boolean informationSent = false;
            int channels = 0;
            int sampleRate = 0;

            audioThreadLoop:
            while (audioThreadCanRun(stream)) {
                if (currentStream == null) {
                    if (informationSent && !audioThreadIsLooping(stream)) {
                        break;
                    }
                    currentStream = audioThreadOpenNewStream(stream);
                    if (currentStream == null) {
                        break;
                    }
                }

                if (!informationSent) {
                    channels = currentStream.getChannels();
                    sampleRate = currentStream.getSampleRate();
                    audioThreadSendInformation(stream, channels, sampleRate);
                    informationSent = true;
                }

                int seekSample = audioThreadSeek(stream);
                if (seekSample >= 0) {
                    if (seekSample >= currentStream.getSamplesRead()) {
                        currentStream.skipSamples(seekSample - currentStream.getSamplesRead());
                    } else {
                        currentStream.close();
                        currentStream = audioThreadOpenNewStream(stream);
                        if (currentStream == null) {
                            break;
                        }
                        currentStream.skipSamples(seekSample);
                    }
                    audioThreadUpdateCurrentSample(stream, currentStream);
                }

                int numberOfBuffers;
                while ((numberOfBuffers = audioThreadNumberOfBuffers(stream))
                        < IDEAL_NUMBER_OF_BUFFERS && numberOfBuffers != -1) {
                    int idealSamples = (int) (IDEAL_LENGTH_PER_BUFFER * sampleRate * channels);
                    short[] buffer = currentStream.readSamples(idealSamples);
                    audioThreadUpdateCurrentSample(stream, currentStream);
                    if (buffer == null) {
                        currentStream.close();
                        currentStream = null;
                        continue audioThreadLoop;
                    }
                    audioThreadDeliverBuffer(stream, buffer);
                }

                Thread.sleep(10);
            }
        } finally {
            if (currentStream != null) {
                currentStream.close();
            }
        }
    }

    private final InputStreamFactory inputStreamFactory;

    private boolean started = false;
    private final CompletableFuture<Void> joinTask = new CompletableFuture<>();

    private volatile int channels = -1;
    private volatile int sampleRate = -1;
    private volatile int currentSample = 0;
    private volatile int seek = -1;
    private volatile boolean looping = false;
    private volatile Throwable throwable = null;

    private final ConcurrentLinkedQueue<short[]> samplesQueue = new ConcurrentLinkedQueue<>();

    private final Set<Integer> buffersCreated = new HashSet<>();
    private final Set<Integer> buffersToBeReturned = new HashSet<>();
    private final ConcurrentLinkedQueue<Integer> buffersToBeRecycled = new ConcurrentLinkedQueue<>();

    private volatile boolean closed = false;

    public AudioStreamImpl(InputStreamFactory factory) {
        Objects.requireNonNull(factory, "Factory is null.");
        this.inputStreamFactory = factory;

        registerForCleaning();
    }

    private void registerForCleaning() {
        final Set<Integer> finalBuffers = this.buffersCreated;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                for (Integer b : finalBuffers) {
                    alDeleteBuffers(b);
                }
                finalBuffers.clear();
            });
        });
    }

    @Override
    public InputStreamFactory getInputStreamFactory() {
        return inputStreamFactory;
    }

    @Override
    public void start() {
        if (this.started) {
            return;
        }
        final WeakReference<AudioStreamImpl> weakReference = new WeakReference<>(this);
        Thread t = new Thread(() -> {
            audioThreadMain(weakReference);
        }, "Audio Stream Thread-" + hashCode());
        t.setDaemon(true);
        t.start();
        this.started = true;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public void join() {
        if (!this.started) {
            throw new UnsupportedOperationException("Not started");
        }
        this.joinTask.join();
    }

    protected void onInformationReceived(int channels, int sampleRate) {
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.joinTask.complete(null);
    }

    protected void onThrowable(Throwable t) {
        this.throwable = t;
        this.closed = true;
        this.joinTask.completeExceptionally(t);
    }

    protected void onSamplesReceived(short[] samples) {
        this.samplesQueue.add(samples);
    }

    protected void onCurrentSampleUpdate(int currentSample) {
        this.currentSample = currentSample;
    }

    protected int getSeekAndClear() {
        int s = this.seek;
        this.seek = -1;
        return s;
    }

    protected int getNumberOfQueuedSampleArrays() {
        return this.samplesQueue.size();
    }
    
    @Override
    public int getChannels() {
        return channels;
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    @Override
    public int getCurrentSample() {
        return currentSample;
    }

    @Override
    public void seek(int sample) {
        this.seek = sample;
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    @Override
    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    @Override
    public Throwable getThrowable() {
        return throwable;
    }
    
    @Override
    public int nextBuffer() {
        short[] data = this.samplesQueue.poll();
        if (data == null) {
            return 0;
        }
        Integer buffer = this.buffersToBeRecycled.poll();
        if (buffer == null) {
            buffer = alGenBuffers();
            this.buffersCreated.add(buffer);
        }
        alBufferData(buffer, 
                (getChannels() == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16),
                data, getSampleRate());
        this.buffersToBeReturned.add(buffer);
        return buffer;
    }

    @Override
    public void returnBuffer(int buffer) {
        if (!this.buffersCreated.contains(buffer)) {
            throw new IllegalArgumentException("Buffer "+buffer+" not owned by this stream.");
        }
        if (this.buffersToBeReturned.remove(buffer)) {
            this.buffersToBeRecycled.add(buffer);
        }
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        
        this.buffersToBeReturned.clear();
        this.buffersToBeRecycled.clear();
        
        final Set<Integer> finalBuffers = this.buffersCreated;
        for (Integer b : finalBuffers) {
            alDeleteBuffers(b);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
