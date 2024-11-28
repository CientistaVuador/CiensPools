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

import cientistavuador.cienspools.audio.data.InputStreamFactory;
import cientistavuador.cienspools.audio.data.StreamedAudio;
import cientistavuador.cienspools.resourcepack.Resource;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Cien
 */
public class StreamedAudioImpl implements StreamedAudio {

    private final String id;
    private final InputStreamFactory inputStreamFactory;

    private final CompletableFuture<Integer> futureChannels = new CompletableFuture<>();
    private final CompletableFuture<Integer> futureSampleRate = new CompletableFuture<>();
    private final CompletableFuture<Integer> futureLengthSamples = new CompletableFuture<>();
    private volatile boolean futureScheduled = false;
    private final Object futureLock = new Object();

    private int channels = -1;
    private int sampleRate = -1;
    private int lengthSamples = -1;

    public StreamedAudioImpl(String id, InputStreamFactory factory) {
        id = Objects.requireNonNullElse(id, Resource.generateRandomId(null));
        Objects.requireNonNull(factory, "factory is null.");
        this.id = id;
        this.inputStreamFactory = factory;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public InputStreamFactory getInputStreamFactory() {
        return this.inputStreamFactory;
    }

    private void scheduleFuture() {
        if (this.futureScheduled) {
            return;
        }
        synchronized (this.futureLock) {
            if (this.futureScheduled) {
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    try (AudioDataStream dataStream
                            = new AudioDataStream(getInputStreamFactory().newInputStream())) {
                        dataStream.start();
                        this.futureChannels.complete(dataStream.getChannels());
                        this.futureSampleRate.complete(dataStream.getSampleRate());
                        this.futureLengthSamples.complete(dataStream.skipSamples(Integer.MAX_VALUE));
                    }
                } catch (Throwable t) {
                    this.futureChannels.completeExceptionally(t);
                    this.futureSampleRate.completeExceptionally(t);
                    this.futureLengthSamples.completeExceptionally(t);
                }
            });
            this.futureScheduled = true;
        }
    }

    @Override
    public int getChannels() {
        scheduleFuture();
        if (this.channels == -1) {
            this.channels = this.futureChannels.join();
        }
        return this.channels;
    }

    @Override
    public int getSampleRate() {
        scheduleFuture();
        if (this.sampleRate == -1) {
            this.sampleRate = this.futureSampleRate.join();
        }
        return this.sampleRate;
    }

    @Override
    public int getLengthSamples() {
        scheduleFuture();
        if (this.lengthSamples == -1) {
            this.lengthSamples = this.futureLengthSamples.join();
        }
        return this.lengthSamples;
    }

}
