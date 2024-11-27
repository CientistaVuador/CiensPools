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
import cientistavuador.cienspools.audio.data.BufferedAudio;
import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.util.ObjectCleaner;
import java.nio.ShortBuffer;
import java.util.Objects;
import static org.lwjgl.openal.AL11.*;

/**
 *
 * @author Cien
 */
public class BufferedAudioImpl implements BufferedAudio {

    private static class WrappedAudioBuffer {

        int buffer = 0;
    }

    private final String id;
    private final ShortBuffer data;
    private final int channels;
    private final int sampleRate;

    private final WrappedAudioBuffer audioBuffer = new WrappedAudioBuffer();

    public BufferedAudioImpl(String id, ShortBuffer data, int channels, int sampleRate) {
        id = Objects.requireNonNullElse(id, Resource.generateRandomId(null));
        Objects.requireNonNull(data, "Data is null.");
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("Channels must be 1 or 2.");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be larger than 0.");
        }

        this.id = id;
        this.data = data;
        this.channels = channels;
        this.sampleRate = sampleRate;

        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedAudioBuffer wrapped = this.audioBuffer;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                if (wrapped.buffer != 0) {
                    alDeleteBuffers(wrapped.buffer);
                }
                wrapped.buffer = 0;
            });
        });
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ShortBuffer getData() {
        return data;
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
    public int buffer() {
        int buffer = this.audioBuffer.buffer;
        if (buffer == 0) {
            buffer = alGenBuffers();
            alBufferData(
                    buffer,
                    (getChannels() == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16),
                    getData(), getSampleRate());
            this.audioBuffer.buffer = buffer;
        }
        return buffer;
    }

    @Override
    public void manualFree() {
        final WrappedAudioBuffer wrapped = this.audioBuffer;

        if (wrapped.buffer != 0) {
            alDeleteBuffers(wrapped.buffer);
        }
        wrapped.buffer = 0;
    }
}
