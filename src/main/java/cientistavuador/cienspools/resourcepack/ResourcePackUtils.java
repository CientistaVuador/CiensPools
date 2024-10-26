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
package cientistavuador.cienspools.resourcepack;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author Cien
 */
public class ResourcePackUtils {

    public static void validate(
            Resource resource,
            String type,
            boolean validateFileSystem,
            String[] requiredMeta,
            String[] requiredData
    ) {
        if (resource == null) {
            throw new NullPointerException("Resource is null.");
        }
        if (!resource.getType().equals(type)) {
            throw new IllegalArgumentException("Invalid resource type, expected '" + type + "', found '" + resource.getType() + "'");
        }
        if (validateFileSystem) {
            ResourcePack pack = resource.getResourcePack();
            if (pack == null) {
                throw new NullPointerException("A valid file system is required but the resource has no resource pack.");
            }
            FileSystem fs = pack.getFileSystem();
            if (fs == null) {
                throw new NullPointerException("A valid file system is required but the associated file system is null.");
            }
            if (!fs.isOpen()) {
                throw new IllegalArgumentException("A valid file system is required but the associated file system is closed.");
            }
        }
        if (requiredMeta != null && requiredMeta.length != 0) {
            for (String required : requiredMeta) {
                if (!resource.getMetadata().containsKey(required)) {
                    throw new IllegalArgumentException("Required resource metadata '" + required + "' not found.");
                }
            }
        }
        if (requiredData != null && requiredData.length != 0) {
            for (String required : requiredData) {
                if (!resource.getData().containsKey(required)) {
                    throw new IllegalArgumentException("Required resource data '" + required + "' not found.");
                }
                String data = resource.getData().get(required);
                if (data == null) {
                    throw new NullPointerException("Required resource data '" + required + "' is null.");
                }
                if (validateFileSystem) {
                    Path path = resource.getPath(data);
                    if (!Files.exists(path)) {
                        throw new IllegalArgumentException("Required resource data '" + required + "' does not exists in the file system.");
                    }
                }
            }
        }
    }

    private ResourcePackUtils() {

    }
}
