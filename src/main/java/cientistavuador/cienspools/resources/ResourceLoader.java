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
package cientistavuador.cienspools.resources;

import cientistavuador.cienspools.resourcepack.ResourcePack;
import cientistavuador.cienspools.util.PathUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class ResourceLoader {

    private static void emitWarning(String warning) {
        System.out.println("Resource Loader Warning: " + warning);
    }

    public static void init() {

    }

    private static void loadResourcePacksIn(Path path, Set<String> extensions) throws IOException {
        if (!Files.exists(path)) {
            emitWarning(path.toString() + " not found.");
            return;
        }
        Path[] files = Files.list(path).toArray(Path[]::new);
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            String[] split = file.getFileName().toString().split("\\.");
            String extension = split[split.length - 1];
            if (!extensions.contains(extension)) {
                continue;
            }
            try {
                long here = System.currentTimeMillis();
                ResourcePack p = ResourcePack.of(file).global();
                long time = System.currentTimeMillis() - here;
                System.out.println("Loaded " 
                        + file.getParent().getFileName()+"/"+file.getFileName()
                        + " with " 
                        + p.getResources().size() 
                        + " resources!"
                        + " (" + time + "ms)"
                );
            } catch (IOException | SAXException ex) {
                emitWarning("Failed to load " + file + ": " + ex.getMessage());
                ex.printStackTrace(System.out);
            }
        }
    }
    
    static {
        String[] extensions = {
            "n3dm"
        };
        Set<String> validExtensions = new HashSet<>();
        validExtensions.addAll(Arrays.asList(extensions));
        try {
            Path resourceLoaderPath = PathUtils.pathOf(ResourceLoader.class);
            Path[] folders = Files.list(resourceLoaderPath).toArray(Path[]::new);
            for (Path folder:folders) {
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                loadResourcePacksIn(folder, validExtensions);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private ResourceLoader() {

    }
}
