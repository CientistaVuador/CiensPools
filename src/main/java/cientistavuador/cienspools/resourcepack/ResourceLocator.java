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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Cien
 */
public class ResourceLocator {
    
    public static final ResourceLocator GLOBAL = new ResourceLocator();
    
    public static Resource get(String type, String id) {
        return GLOBAL.getResource(type, id);
    }
    
    public static Authorship authorshipOf(String type, String id) {
        Resource planA = get(type, id);
        if (planA == null || planA.getAuthorship() == null) {
            Resource planB = get("authorship", id);
            if (planB == null) {
                return null;
            }
            return planB.getAuthorship();
        }
        return planA.getAuthorship();
    }
    
    private final Set<ResourcePack> resourcePacks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    private final Map<String, Set<Resource>> idMap = new ConcurrentHashMap<>();
    private final Map<String, Set<Resource>> typeMap = new ConcurrentHashMap<>();
    
    public ResourceLocator() {

    }

    public Set<ResourcePack> getResourcePacks() {
        return Collections.unmodifiableSet(this.resourcePacks);
    }

    private void addToIdMap(Resource r, String id) {
        Set<Resource> list = this.idMap.get(id);
        if (list == null) {
            list = Collections.newSetFromMap(new ConcurrentHashMap<>());
            this.idMap.put(id, list);
        }
        list.add(r);
    }

    private void addToTypeMap(Resource r, String type) {
        Set<Resource> list = this.typeMap.get(type);
        if (list == null) {
            list = Collections.newSetFromMap(new ConcurrentHashMap<>());
            this.typeMap.put(type, list);
        }
        list.add(r);
    }

    private void mapResource(Resource r) {
        addToIdMap(r, r.getId());
        for (String alias : r.getAliases()) {
            addToIdMap(r, alias);
        }
        addToTypeMap(r, r.getType());
    }

    public boolean addResourcePack(ResourcePack pack) {
        if (pack == null || pack.locator != null) {
            return false;
        }
        boolean success = this.resourcePacks.add(pack);
        if (success) {
            pack.locator = this;

            for (Resource r : pack.getResources()) {
                mapResource(r);
            }
        }
        return success;
    }

    private void removeFromIdMap(Resource r, String id) {
        Set<Resource> list = this.idMap.get(id);
        if (list == null) {
            return;
        }
        list.remove(r);
        if (list.isEmpty()) {
            this.idMap.remove(id);
        }
    }

    private void removeFromTypeMap(Resource r, String type) {
        Set<Resource> list = this.typeMap.get(type);
        if (list == null) {
            return;
        }
        list.remove(r);
        if (list.isEmpty()) {
            this.idMap.remove(type);
        }
    }

    private void unmapResource(Resource r) {
        removeFromIdMap(r, r.getId());
        for (String alias : r.getAliases()) {
            removeFromIdMap(r, alias);
        }
        removeFromTypeMap(r, r.getType());
    }

    public boolean removeResourcePack(ResourcePack pack) {
        if (pack == null || pack.locator != this) {
            return false;
        }
        boolean success = this.resourcePacks.remove(pack);
        if (success) {
            pack.locator = null;

            for (Resource r : pack.getResources()) {
                unmapResource(r);
            }
        }
        return success;
    }
    
    public Set<Resource> getResourcesById(String id) {
        return ResourcePackUtils.getResourcesById(id, this.idMap);
    }
    
    public Set<Resource> getResourcesByType(String type) {
        return ResourcePackUtils.getResourcesByType(type, this.typeMap);
    }
    
    public Resource getResource(String type, String id) {
        return ResourcePackUtils.getResource(type, id, this.idMap);
    }
}
