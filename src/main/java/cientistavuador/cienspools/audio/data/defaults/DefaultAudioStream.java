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
package cientistavuador.cienspools.audio.data.defaults;

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
public class DefaultAudioStream implements AudioStream {

    private static void deliverThrowable(WeakReference<DefaultAudioStream> stream, Throwable t) {
        DefaultAudioStream s = stream.get();
        if (s != null) {
            s.onThrowable(t);
        }
    }

    private static void audioThreadMain(WeakReference<DefaultAudioStream> stream) {
        try {
            audioThreadTask(stream);
        } catch (Throwable t) {
            audioThreadPlayingUpdate(stream, false);
            deliverThrowable(stream, t);
        }
    }

    private static boolean audioThreadCanRun(WeakReference<DefaultAudioStream> stream) {
        DefaultAudioStream s = stream.get();
        if (s == null) {
            return false;
        }
        return !s.isClosed();
    }

    private static boolean audioThreadIsLooping(WeakReference<DefaultAudioStream> stream) {
        DefaultAudioStream s = stream.get();
        if (s != null) {
            return s.isLooping();
        }
        return false;
    }

    private static AudioDataStream audioThreadOpenNewStream(WeakReference<DefaultAudioStream> stream)
            throws IOException {
        DefaultAudioStream s = stream.get();
        if (s == null) {
            return null;
        }
        AudioDataStream e = new AudioDataStream(s.getInputStreamFactory().newInputStream());
        e.start();
        return e;
    }

    private static void audioThreadSendInformation(
            WeakReference<DefaultAudioStream> stream, int channels, int sampleRate) {
        DefaultAudioStream s = stream.get();
        if (s != null) {
            s.onInformationReceived(channels, sampleRate);
        }
    }

    private static int audioThreadNumberOfBuffers(WeakReference<DefaultAudioStream> stream) {
        DefaultAudioStream s = stream.get();
        if (s != null) {
            return s.getNumberOfQueuedSampleArrays();
        }
        return -1;
    }

    private static void audioThreadDeliverBuffer(WeakReference<DefaultAudioStream> stream, short[] samples) {
        DefaultAudioStream s = stream.get();
        if (s != null) {
            s.onSamplesReceived(samples);
        }
    }

    private static int audioThreadSeek(WeakReference<DefaultAudioStream> stream) {
        DefaultAudioStream s = stream.get();
        if (s != null) {
            return s.getSeekAndClear();
        }
        return -1;
    }

    private static void audioThreadUpdateCurrentSample(WeakReference<DefaultAudioStream> stream, AudioDataStream dataStream) {
        DefaultAudioStream s = stream.get();
        if (s != null) {
            s.onCurrentSampleUpdate(dataStream.getSamplesRead());
        }
    }
    
    private static void audioThreadPlayingUpdate(WeakReference<DefaultAudioStream> stream, boolean playing) {
        DefaultAudioStream s = stream.get();
        if (s != null) {
            s.onPlayingUpdate(playing);
        }
    }
    
    private static void audioThreadTask(WeakReference<DefaultAudioStream> stream) throws Throwable {
        AudioDataStream currentStream = null;
        try {
            final int sleepTime = 5;
            
            boolean informationSent = false;
            int channels = 0;
            int sampleRate = 0;

            audioThreadLoop:
            while (audioThreadCanRun(stream)) {
                int seekSample = audioThreadSeek(stream);
                
                if (currentStream == null) {
                    if (informationSent && !audioThreadIsLooping(stream) && seekSample < 0) {
                        audioThreadPlayingUpdate(stream, false);
                        Thread.sleep(sleepTime);
                        continue;
                    }
                    currentStream = audioThreadOpenNewStream(stream);
                    if (currentStream == null) {
                        break;
                    }
                    audioThreadPlayingUpdate(stream, true);
                }

                if (!informationSent) {
                    channels = currentStream.getChannels();
                    sampleRate = currentStream.getSampleRate();
                    audioThreadSendInformation(stream, channels, sampleRate);
                    informationSent = true;
                }

                if (seekSample >= 0) {
                    int skipResult;
                    if (seekSample >= currentStream.getSamplesRead()) {
                        skipResult = currentStream
                                .skipSamples(seekSample - currentStream.getSamplesRead());
                    } else {
                        currentStream.close();
                        currentStream = audioThreadOpenNewStream(stream);
                        if (currentStream == null) {
                            break;
                        }
                        skipResult = currentStream.skipSamples(seekSample);
                    }
                    audioThreadUpdateCurrentSample(stream, currentStream);
                    if (skipResult < 0) {
                        currentStream.close();
                        currentStream = null;
                        continue;
                    }
                }

                int numberOfBuffers;
                while ((numberOfBuffers = audioThreadNumberOfBuffers(stream))
                        < IDEAL_NUMBER_OF_BUFFERS && numberOfBuffers != -1) {
                    int idealSamples = (int) (IDEAL_LENGTH_PER_BUFFER * sampleRate);
                    short[] buffer = currentStream.readSamples(idealSamples);
                    audioThreadUpdateCurrentSample(stream, currentStream);
                    if (buffer == null) {
                        currentStream.close();
                        currentStream = null;
                        continue audioThreadLoop;
                    }
                    audioThreadDeliverBuffer(stream, buffer);
                }

                Thread.sleep(sleepTime);
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
    private volatile boolean playing = false;
    private volatile Throwable throwable = null;

    private final ConcurrentLinkedQueue<short[]> samplesQueue = new ConcurrentLinkedQueue<>();

    private final Set<Integer> buffersCreated = new HashSet<>();
    private final Set<Integer> buffersToBeReturned = new HashSet<>();
    private final ConcurrentLinkedQueue<Integer> buffersToBeRecycled = new ConcurrentLinkedQueue<>();

    private volatile boolean closed = false;

    public DefaultAudioStream(InputStreamFactory factory) {
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
        final WeakReference<DefaultAudioStream> weakReference = new WeakReference<>(this);
        Thread t = new Thread(() -> {
            audioThreadMain(weakReference);
        }, "Audio Stream Thread-" + hashCode());
        t.setDaemon(true);
        this.started = true;
        this.playing = true;
        t.start();
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
    
    protected void onPlayingUpdate(boolean playing) {
        this.playing = playing;
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
        if (sample < 0) {
            sample = 0;
        }
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
    public boolean isPlaying() {
        return playing;
    }
    
    @Override
    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public int nextBuffer() {
        Throwable t = getThrowable();
        if (t != null) {
            throw new AudioStreamException(t);
        }
        
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
            throw new IllegalArgumentException("Buffer " + buffer + " not owned by this stream.");
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
        Main.MAIN_TASKS.add(() -> {
            for (Integer b : finalBuffers) {
                alDeleteBuffers(b);
            }
            finalBuffers.clear();
        });
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
