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

import cientistavuador.cienspools.audio.data.BufferedAudio;
import cientistavuador.cienspools.util.ObjectCleaner;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.PointerBuffer;
import static org.lwjgl.stb.STBVorbis.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public class AsyncVorbisBufferedAudio extends BufferedAudioDecorator {

    private final CompletableFuture<BufferedAudio> future;
    private BufferedAudio wrapped = null;

    public AsyncVorbisBufferedAudio(String id, byte[] oggFile) {
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

    public boolean isDone() {
        return this.future.isDone();
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
