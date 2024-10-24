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
import java.util.List;

/**
 *
 * @author Cien
 */
public class ResourceLocator {
    
    public static final ResourceLocator GLOBAL = new ResourceLocator();
    
    static {
        //todo
    }
    
    public static void init() {
        
    }
    
    private final List<ResourcePack> resourcePacks = new ArrayList<>();
    
    public ResourceLocator() {
        
    }

    public List<ResourcePack> getResourcePacks() {
        return Collections.unmodifiableList(this.resourcePacks);
    }
    
    public boolean addResourcePack(ResourcePack pack) {
        boolean success = this.resourcePacks.add(pack);
        if (success) {
            //todo
        }
        return success;
    }
    
    public boolean removeResourcePack(ResourcePack pack) {
        boolean success = this.resourcePacks.remove(pack);
        if (success) {
            //todo
        }
        return success;
    }
    
    protected void onResourceAdded(ResourcePack pack, Resource resource) {
        
    }
    
    protected void onResourceRemoved(ResourcePack pack, Resource resource) {
        
    }
    
    protected void onIdChanged(ResourcePack p, Resource r, String oldId, String newId) {
        
    }
    
    protected void onTypeChanged(ResourcePack p, Resource r, String oldType, String newType) {
        
    }
    
    protected void onAliasAdded(ResourcePack p, Resource r, String alias) {
        
    }
    
    protected void onAliasRemoved(ResourcePack p, Resource r, String alias) {
        
    }
    
}
