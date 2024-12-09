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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        public static final String ENTER_FILE_NAME = "enter";
        public static final String EXIT_FILE_NAME = "exit";

        @Override
        public String getResourceType() {
            return "materialSoundEffects";
        }

        private void readAudioList(List<Audio> list, Path p) throws IOException {
            list
                    .addAll(StringList
                            .fromString(Files.readString(p, StandardCharsets.UTF_8))
                            .stream()
                            .map(Audio.RESOURCES::get)
                            .toList());
        }

        @Override
        public NMaterialSoundEffects readResource(Resource r) throws IOException {
            if (r == null) {
                return NMaterialSoundEffects.EMPTY;
            }
            NMaterialSoundEffects effects = new NMaterialSoundEffects(r.getId());

            Path footStepsPath = r.getData().get(FOOTSTEPS_FILE_NAME);
            if (footStepsPath != null) {
                readAudioList(effects.getFootsteps(), footStepsPath);
            }

            Path enterPath = r.getData().get(ENTER_FILE_NAME);
            if (enterPath != null) {
                readAudioList(effects.getEnter(), enterPath);
            }

            Path exitPath = r.getData().get(EXIT_FILE_NAME);
            if (exitPath != null) {
                readAudioList(effects.getExit(), exitPath);
            }

            return effects;
        }

        private void writeAudioList(
                String filename,
                String path,
                List<Audio> list,
                ResourcePackWriter.ResourceEntry entry) {
            entry.getData()
                    .put(filename,
                            new ResourcePackWriter.DataEntry(path + "footsteps.txt",
                                    new ByteArrayInputStream(
                                            StringList
                                                    .toString(list
                                                            .stream()
                                                            .map(Audio::getId)
                                                            .toList())
                                                    .getBytes(StandardCharsets.UTF_8))));
        }

        @Override
        public void writeResource(NMaterialSoundEffects obj, ResourcePackWriter.ResourceEntry entry, String path) throws IOException {
            entry.setType(getResourceType());
            entry.setId(obj.getId());
            if (!path.isEmpty() && !path.endsWith("/")) {
                path += "/";
            }
            if (!obj.getFootsteps().isEmpty()) {
                writeAudioList(FOOTSTEPS_FILE_NAME, path + "footsteps.txt", obj.getFootsteps(), entry);
            }
            if (!obj.getEnter().isEmpty()) {
                writeAudioList(ENTER_FILE_NAME, path + "enter.txt", obj.getEnter(), entry);
            }
            if (!obj.getExit().isEmpty()) {
                writeAudioList(EXIT_FILE_NAME, path + "exit.txt", obj.getExit(), entry);
            }
        }
    };

    private static final Random random = new Random();

    private final String id;
    private final List<Audio> footsteps = new ArrayList<>();
    private final List<Audio> enter = new ArrayList<>();
    private final List<Audio> exit = new ArrayList<>();

    private final Map<List<Audio>, Integer> lastRandomIndex = new HashMap<>();
    
    public NMaterialSoundEffects(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<Audio> getFootsteps() {
        return footsteps;
    }

    public List<Audio> getEnter() {
        return enter;
    }

    public List<Audio> getExit() {
        return exit;
    }

    private Audio randomAudio(List<Audio> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        Integer lastIndex;
        if ((lastIndex = this.lastRandomIndex.get(list)) == null) {
            lastIndex = -1;
        }
        int currentIndex;
        do {
            currentIndex = random.nextInt(list.size());
        } while (currentIndex == lastIndex);
        this.lastRandomIndex.put(list, currentIndex);
        return list.get(currentIndex);
    }

    public Audio getRandomFootstep() {
        return randomAudio(getFootsteps());
    }

    public Audio getRandomEnter() {
        return randomAudio(getEnter());
    }

    public Audio getRandomExit() {
        return randomAudio(getExit());
    }

}
