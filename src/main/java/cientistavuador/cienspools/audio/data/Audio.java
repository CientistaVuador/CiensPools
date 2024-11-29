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

import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.resourcepack.ResourcePackWriter;
import cientistavuador.cienspools.resourcepack.ResourceRW;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author Cien
 */
public interface Audio {

    public static ResourceRW<Audio> RESOURCES = new ResourceRW<Audio>(true) {
        public static final String AUDIO_FILE_NAME = "audio";
        
        @Override
        public String getResourceType() {
            return "audio";
        }
        
        @Override
        public Audio readResource(Resource r) throws IOException {
            boolean streamingEnabled = false;
            if (r.getMeta().containsKey("streamingEnabled")) {
                streamingEnabled = Boolean.parseBoolean(r.getMeta().get("streamingEnabled"));
            }
            Path audioFile = r.getData().get(AUDIO_FILE_NAME);
            if (!streamingEnabled) {
                return BufferedAudio.fromOggVorbis(r.getId(), Files.newInputStream(audioFile));
            }
            return StreamedAudio.fromInputStreamFactory(r.getId(), () -> Files.newInputStream(audioFile));
        }
        
        @Override
        public void writeResource(Audio obj, ResourcePackWriter.ResourceEntry entry, String path) throws IOException {
            throw new IOException("Writing audio resources not supported.");
        }
    };
    
    public String getId();

    public int getChannels();

    public int getSampleRate();

    public int getLengthSamples();

    public default float getLength() {
        return getLengthSamples() / ((float)getSampleRate());
    }

}
