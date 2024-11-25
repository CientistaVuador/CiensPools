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
import cientistavuador.cienspools.resourcepack.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class StreamedAudio implements Audio {

    @Override
    public int getChannels() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public int getSampleRate() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public int getLengthSamples() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    
    public static interface InputStreamFactory {
        public InputStream newInputStream() throws IOException;
    }
    
    private final String id;
    private final InputStreamFactory inputStreamFactory;
    
    public StreamedAudio(String id, InputStreamFactory inputStreamFactory) {
        id = Objects.requireNonNullElse(id, Resource.generateRandomId(null));
        Objects.requireNonNull(inputStreamFactory, "InputStreamFactory is null.");
        this.id = id;
        this.inputStreamFactory = inputStreamFactory;
    }

    public String getId() {
        return id;
    }

    public InputStreamFactory getInputStreamFactory() {
        return inputStreamFactory;
    }
    
    public AudioStream newStream() throws IOException {
        return AudioStream.newStream(getInputStreamFactory().newInputStream());
    }
    
}
