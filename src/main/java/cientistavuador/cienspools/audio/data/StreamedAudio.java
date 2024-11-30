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

import cientistavuador.cienspools.audio.data.defaults.DefaultStreamedAudio;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public interface StreamedAudio extends Audio {

    public static StreamedAudio fromInputStreamFactory(String id, InputStreamFactory factory) {
        return new DefaultStreamedAudio(id, factory);
    }

    public static StreamedAudio fromOggVorbis(String id, byte[] data) {
        Objects.requireNonNull(data, "data is null.");
        return fromInputStreamFactory(id, () -> new ByteArrayInputStream(data));
    }

    public static StreamedAudio fromOggVorbis(String id, InputStream oggFileStream)
            throws IOException {
        Objects.requireNonNull(oggFileStream, "Ogg File Stream is null.");
        byte[] oggFile = oggFileStream.readAllBytes();
        return fromOggVorbis(id, oggFile);
    }

    public InputStreamFactory getInputStreamFactory();

    public default AudioStream openNewStream() {
        return AudioStream.newAudioStream(getInputStreamFactory());
    }
}
