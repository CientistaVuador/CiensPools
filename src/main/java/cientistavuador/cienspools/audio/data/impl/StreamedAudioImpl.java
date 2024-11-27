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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class StreamedAudioImpl implements StreamedAudio {

    private final String id;
    private final InputStreamFactory inputStreamFactory;
    
    private final Object audioDataLock = new Object();
    private volatile boolean audioDataPopulated = false;
    private int channels = 0;
    private int sampleRate = 0;
    private int lengthSamples = 0;
    
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

    private void populateAudioData() {
        if (this.audioDataPopulated) {
            return;
        }
        synchronized (this.audioDataLock) {
            if (this.audioDataPopulated) {
                return;
            }
            try {
                try (AudioDataStream dataStream = 
                        new AudioDataStream(getInputStreamFactory().newInputStream())) {
                    dataStream.start();
                    this.channels = dataStream.getChannels();
                    this.sampleRate = dataStream.getSampleRate();
                    this.lengthSamples = dataStream.skipSamples(Integer.MAX_VALUE);
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            this.audioDataPopulated = true;
        }
    }
    
    @Override
    public int getChannels() {
        populateAudioData();
        return this.channels;
    }

    @Override
    public int getSampleRate() {
        populateAudioData();
        return this.sampleRate;
    }

    @Override
    public int getLengthSamples() {
        populateAudioData();
        return this.lengthSamples;
    }
    
}
