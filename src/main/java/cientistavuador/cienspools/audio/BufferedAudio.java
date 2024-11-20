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

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.util.ObjectCleaner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.stb.STBVorbis.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public interface BufferedAudio extends Audio {
    
    public static class Implementation implements BufferedAudio {

        private static class WrappedAudioBuffer {

            int buffer = 0;
        }

        private final String id;
        private final ShortBuffer data;
        private final int channels;
        private final int sampleRate;
        private final float length;

        private final WrappedAudioBuffer audioBuffer = new WrappedAudioBuffer();

        private Implementation(String id, ShortBuffer data, int channels, int sampleRate) {
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
            this.length = Audio.length(data.capacity(), channels, sampleRate);

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
        public float getLength() {
            return length;
        }

        @Override
        public int buffer() {
            int buffer = this.audioBuffer.buffer;
            if (buffer == 0) {
                buffer = alGenBuffers();
                if (getChannels() == 1) {
                    alBufferData(buffer, AL_FORMAT_MONO16, getData(), getSampleRate());
                } else {
                    alBufferData(buffer, AL_FORMAT_STEREO16, getData(), getSampleRate());
                }
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

    public static BufferedAudio fromBuffer(
            String id, ShortBuffer data, int channels, int sampleRate) {
        return new Implementation(id, data, channels, sampleRate);
    }
    
    public static BufferedAudio fromArray(
            String id, short[] data, int channels, int sampleRate
    ) {
        return new Implementation(id, 
                BufferUtils.createShortBuffer(data.length).put(data).flip(),
                channels, sampleRate);
    }

    public static abstract class Decorator implements BufferedAudio {
        protected Decorator() {
            
        }
        
        protected abstract BufferedAudio getBufferedAudio();
        
        @Override
        public String getId() {
            return getBufferedAudio().getId();
        }

        @Override
        public ShortBuffer getData() {
            return getBufferedAudio().getData();
        }

        @Override
        public int getChannels() {
            return getBufferedAudio().getChannels();
        }

        @Override
        public int getSampleRate() {
            return getBufferedAudio().getSampleRate();
        }

        @Override
        public int getLengthSamples() {
            return getBufferedAudio().getLengthSamples();
        }
        
        @Override
        public float getLength() {
            return getBufferedAudio().getLength();
        }

        @Override
        public int buffer() {
            return getBufferedAudio().buffer();
        }

        @Override
        public void manualFree() {
            getBufferedAudio().manualFree();
        }
    }
    
    public static class AsynchronousOggVorbis extends Decorator {

        private final CompletableFuture<BufferedAudio> future;
        private BufferedAudio wrapped = null;

        private AsynchronousOggVorbis(String id, byte[] oggFile) {
            Objects.requireNonNull(oggFile, "Ogg File is null.");
            final ByteBuffer nativeMemory = memAlloc(oggFile.length).put(oggFile).flip();
            this.future = CompletableFuture.supplyAsync(() -> {
                try {
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        IntBuffer channelsBuffer = stack.mallocInt(1);
                        IntBuffer sampleRateBuffer = stack.mallocInt(1);
                        PointerBuffer pointerBuffer = stack.mallocPointer(1);

                        int result = stb_vorbis_decode_memory(
                                nativeMemory, channelsBuffer, sampleRateBuffer, pointerBuffer);
                        if (result < 0) {
                            throw new IllegalArgumentException("Invalid ogg file: " + result);
                        }
                        int channels = channelsBuffer.get();
                        int sampleRate = sampleRateBuffer.get();
                        ShortBuffer audioData
                                = memByteBuffer(pointerBuffer.get(), result * Short.BYTES * channels)
                                        .asShortBuffer();
                        try {
                            BufferedAudio audio = BufferedAudio.fromBuffer(
                                    id, audioData, channels, sampleRate);
                            ObjectCleaner.get().register(audioData, () -> {
                                memFree(audioData);
                            });
                            return audio;
                        } catch (Throwable t) {
                            memFree(audioData);
                            throw t;
                        }
                    }
                } finally {
                    memFree(nativeMemory);
                }
            });
        }
        
        @Override
        protected BufferedAudio getBufferedAudio() {
            if (this.wrapped != null) {
                return this.wrapped;
            }
            this.wrapped = this.future.join();
            return this.wrapped;
        }
    }

    public static BufferedAudio fromOggVorbis(String id, byte[] oggFile) {
        return new AsynchronousOggVorbis(id, oggFile);
    }

    public static BufferedAudio fromOggVorbis(String id, InputStream oggStream) throws IOException {
        Objects.requireNonNull(oggStream, "Ogg Stream is null.");
        byte[] oggFile = oggStream.readAllBytes();
        return fromOggVorbis(id, oggFile);
    }

    public String getId();

    public ShortBuffer getData();

    public int getChannels();

    public int getSampleRate();

    public default int getLengthSamples() {
        return getData().capacity();
    }
    
    public default float getLength() {
        return Audio.length(getLengthSamples(), getChannels(), getSampleRate());
    }

    public int buffer();

    public void manualFree();

}
