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
package cientistavuador.cienspools.audio.data;

import cientistavuador.cienspools.audio.data.impl.AsyncVorbisBufferedAudio;
import cientistavuador.cienspools.audio.data.impl.BufferedAudioImpl;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;
import java.util.Objects;
import org.lwjgl.BufferUtils;

/**
 *
 * @author Cien
 */
public interface BufferedAudio extends Audio {
    
    public static BufferedAudio fromBuffer(
            String id, ShortBuffer data, int channels, int sampleRate) {
        return new BufferedAudioImpl(id, data, channels, sampleRate);
    }
    
    public static BufferedAudio fromArray(
            String id, short[] data, int channels, int sampleRate
    ) {
        return new BufferedAudioImpl(id, 
                BufferUtils.createShortBuffer(data.length).put(data).flip(),
                channels, sampleRate);
    }
    
    public static BufferedAudio fromOggVorbis(String id, byte[] oggFile) {
        return new AsyncVorbisBufferedAudio(id, oggFile);
    }

    public static BufferedAudio fromOggVorbis(String id, InputStream oggFileStream)
            throws IOException {
        Objects.requireNonNull(oggFileStream, "Ogg File Stream is null.");
        byte[] oggFile = oggFileStream.readAllBytes();
        return fromOggVorbis(id, oggFile);
    }

    public ShortBuffer getData();

    @Override
    public default int getLengthSamples() {
        return getData().capacity();
    }
    
    public int buffer();

    public void manualFree();

}
