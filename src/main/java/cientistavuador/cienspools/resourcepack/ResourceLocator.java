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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Cien
 */
public class ResourceLocator {

    public static final ResourceLocator GLOBAL = new ResourceLocator();
    
    public static Resource get(String id) {
        return GLOBAL.getResource(id);
    }
    
    public static Resource get(String type, String id) {
        return GLOBAL.getResource(type, id);
    }
    
    private final Set<ResourcePack> resourcePacks = new HashSet<>();

    private final Map<String, List<Resource>> idMap = new HashMap<>();
    private final Map<String, List<Resource>> typeMap = new HashMap<>();

    public ResourceLocator() {

    }

    public Set<ResourcePack> getResourcePacks() {
        return Collections.unmodifiableSet(this.resourcePacks);
    }

    private void addToIdMap(Resource r, String id) {
        List<Resource> list = this.idMap.get(id);
        if (list == null) {
            list = new ArrayList<>();
            this.idMap.put(id, list);
        }
        list.add(r);
    }

    private void addToTypeMap(Resource r, String type) {
        List<Resource> list = this.typeMap.get(type);
        if (list == null) {
            list = new ArrayList<>();
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
        List<Resource> list = this.idMap.get(id);
        if (list == null) {
            return;
        }
        list.remove(r);
        if (list.isEmpty()) {
            this.idMap.remove(id);
        }
    }

    private void removeFromTypeMap(Resource r, String type) {
        List<Resource> list = this.typeMap.get(type);
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

    protected void onResourceAdded(ResourcePack pack, Resource resource) {
        mapResource(resource);
    }

    protected void onResourceRemoved(ResourcePack pack, Resource resource) {
        unmapResource(resource);
    }

    protected void onIdChanged(ResourcePack p, Resource r, String oldId, String newId) {
        removeFromIdMap(r, oldId);
        addToIdMap(r, newId);
    }

    protected void onTypeChanged(ResourcePack p, Resource r, String oldType, String newType) {
        removeFromTypeMap(r, oldType);
        addToTypeMap(r, newType);
    }

    protected void onAliasAdded(ResourcePack p, Resource r, String alias) {
        addToIdMap(r, alias);
    }

    protected void onAliasRemoved(ResourcePack p, Resource r, String alias) {
        removeFromIdMap(r, alias);
    }
    
    public List<Resource> getResourcesById(String id) {
        return ResourcePack.getResourcesById(id, this.idMap);
    }
    
    public Resource getResource(String id) {
        return ResourcePack.getResource(id, this.idMap);
    }
    
    public Resource getResource(String type, String id) {
        return ResourcePack.getResource(type, id, this.idMap);
    }
    
    public List<Resource> getResourcesByType(String type) {
        return ResourcePack.getResourcesByType(type, this.typeMap);
    }

}
