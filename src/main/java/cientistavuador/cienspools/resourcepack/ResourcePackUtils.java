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

import java.util.Map;
import java.util.Set;

/**
 *
 * @author Cien
 */
public class ResourcePackUtils {
    protected static Set<Resource> getResourcesById(String id, Map<String, Set<Resource>> idMap) {
        if (id == null) {
            id = "null";
        }
        Set<Resource> list = idMap.get(id);
        if (list == null) {
            return Set.of();
        }
        return list;
    }

    protected static Set<Resource> getResourcesByType(String type, Map<String, Set<Resource>> typeMap) {
        if (type == null) {
            type = "null";
        }
        Set<Resource> list = typeMap.get(type);
        if (list == null) {
            return Set.of();
        }
        return list;
    }

    protected static Resource getResource(String type, String id, Map<String, Set<Resource>> idMap) {
        Set<Resource> list = getResourcesById(id, idMap);
        if (list.isEmpty()) {
            return null;
        }
        Resource highestPriority = null;
        for (Resource other : list) {
            if (type != null && !other.getType().equals(type)) {
                continue;
            }
            if (highestPriority == null) {
                highestPriority = other;
                continue;
            }
            if (other.getPriority() > highestPriority.getPriority()) {
                highestPriority = other;
            }
        }
        return highestPriority;
    }
    
    private ResourcePackUtils() {
        
    }
}
