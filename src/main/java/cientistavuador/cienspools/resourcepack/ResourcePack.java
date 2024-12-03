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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class ResourcePack implements AutoCloseable {
    
    private static void putResource(String key, Resource r, Map<String, Set<Resource>> map) {
        Set<Resource> at = map.get(key);
        if (at == null) {
            at = new HashSet<>();
            map.put(key, at);
        }
        at.add(r);
    }
    
    public static ResourcePack of(Path p) throws SAXException, IOException {
        return ResourcePackReader.read(p);
    }
    
    protected ResourceLocator locator;
    
    private final FileSystem fileSystem;
    private final Path originPath;
    
    private final Set<Resource> resources;
    
    private final Map<String, Set<Resource>> idMap = new ConcurrentHashMap<>();
    private final Map<String, Set<Resource>> typeMap = new ConcurrentHashMap<>();
    
    protected ResourcePack(
            FileSystem fs,
            Path originPath,
            Set<Resource> resources
    ) {
        this.fileSystem = Objects.requireNonNull(fs);
        this.originPath = Objects.requireNonNull(originPath);
        
        this.resources = Collections.unmodifiableSet(resources);
        
        for (Resource e:this.resources) {
            putResource(e.getId(), e, this.idMap);
            for (String alias:e.getAliases()) {
                putResource(alias, e, this.idMap);
            }
            putResource(e.getType(), e, this.typeMap);
        }
        
        for (Entry<String, Set<Resource>> e:this.idMap.entrySet()) {
            e.setValue(Collections.unmodifiableSet(e.getValue()));
        }
        
        for (Entry<String, Set<Resource>> e:this.typeMap.entrySet()) {
            e.setValue(Collections.unmodifiableSet(e.getValue()));
        }
        
        leak();
    }
    
    private void leak() {
        for (Resource r:this.resources) {
            r.attachResourcePack(this);
        }
    }

    public ResourceLocator getLocator() {
        return locator;
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public Path getOriginPath() {
        return originPath;
    }

    public Set<Resource> getResources() {
        return resources;
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
    
    public ResourcePack global() {
        ResourceLocator.GLOBAL.addResourcePack(this);
        return this;
    }

    @Override
    public void close() throws IOException {
        if (getLocator() != null) {
            getLocator().removeResourcePack(this);
        }
        getFileSystem().close();
    }
    
}
