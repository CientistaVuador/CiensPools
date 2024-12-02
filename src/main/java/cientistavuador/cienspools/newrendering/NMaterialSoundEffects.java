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
package cientistavuador.cienspools.newrendering;

import cientistavuador.cienspools.audio.data.Audio;
import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.resourcepack.ResourcePackWriter;
import cientistavuador.cienspools.resourcepack.ResourceRW;
import cientistavuador.cienspools.util.StringList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 * @author Cien
 */
public class NMaterialSoundEffects {

    public static final NMaterialSoundEffects EMPTY
            = new NMaterialSoundEffects("Empty Material Sound Effects");

    public static final ResourceRW<NMaterialSoundEffects> RESOURCES = new ResourceRW<NMaterialSoundEffects>(true) {
        public static final String FOOTSTEPS_FILE_NAME = "footsteps";
        
        @Override
        public String getResourceType() {
            return "materialSoundEffects";
        }

        @Override
        public NMaterialSoundEffects readResource(Resource r) throws IOException {
            if (r == null) {
                return NMaterialSoundEffects.EMPTY;
            }
            NMaterialSoundEffects effects = new NMaterialSoundEffects(r.getId());
            
            Path footStepsPath = r.getData().get(FOOTSTEPS_FILE_NAME);
            if (footStepsPath != null) {
                effects
                        .getFootsteps()
                        .addAll(StringList
                                .fromString(Files.readString(footStepsPath, StandardCharsets.UTF_8))
                                .stream()
                                .map(Audio.RESOURCES::get)
                                .toList());
            }
            
            return effects;
        }

        @Override
        public void writeResource(NMaterialSoundEffects obj, ResourcePackWriter.ResourceEntry entry, String path) throws IOException {
            entry.setType(getResourceType());
            entry.setId(obj.getId());
            if (!path.isEmpty() && !path.endsWith("/")) {
                path += "/";
            }
            if (!obj.getFootsteps().isEmpty()) {
                entry.getData()
                        .put(FOOTSTEPS_FILE_NAME, 
                                new ResourcePackWriter.DataEntry(path + "footsteps.txt",
                                        new ByteArrayInputStream(
                                                StringList
                                                        .toString(obj
                                                                .getFootsteps()
                                                                .stream()
                                                                .map(Audio::getId)
                                                                .toList())
                                                        .getBytes(StandardCharsets.UTF_8))));
            }
        }
    };

    private static final Random random = new Random();

    private final String id;
    private final List<Audio> footsteps = new ArrayList<>();

    public NMaterialSoundEffects(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<Audio> getFootsteps() {
        return footsteps;
    }

    private Audio randomAudio(List<Audio> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        return list.get(random.nextInt(list.size()));
    }

    public Audio getRandomFootstep() {
        return randomAudio(getFootsteps());
    }

}
