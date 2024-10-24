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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

/**
 *
 * @author Cien
 */
public class Resource {

    protected ResourcePack resourcePack;
    
    private String type = "";
    private String id = "";
    private int priority = 0;
    private final Set<String> aliases = new HashSet<>();
    private String origin = null;
    private String preview = null;
    private String title = null;
    private String description = null;
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private final Map<String, String> data = new LinkedHashMap<>();
    private Element extension = null;
    
    public Resource() {

    }
    
    public ResourcePack getResourcePack() {
        return this.resourcePack;
    }
    
    public String getType() {
        return this.type;
    }
    
    public void setType(String newType) {
        if (newType == null) {
            newType = "";
        }
        String oldType = this.type;
        if (!oldType.equals(newType)) {
            this.type = newType;
            if (getResourcePack() != null) {
                getResourcePack().onTypeChanged(this, oldType, newType);
            }
        }
    }
    
    public String getId() {
        return this.id;
    }

    public void setId(String newId) {
        if (newId == null) {
            newId = "";
        }
        String oldId = this.id;
        if (!oldId.equals(newId)) {
            this.id = newId;
            if (getResourcePack() != null) {
                getResourcePack().onIdChanged(this, oldId, newId);
            }
        }
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public Set<String> getAliases() {
        return Collections.unmodifiableSet(this.aliases);
    }
    
    public boolean addAlias(String alias) {
        boolean success = this.aliases.add(alias);
        if (success && getResourcePack() != null) {
            getResourcePack().onAliasAdded(this, alias);
        }
        return success;
    }
    
    public boolean removeAlias(String alias) {
        boolean success = this.aliases.remove(alias);
        if (success && getResourcePack() != null) {
            getResourcePack().onAliasRemoved(this, alias);
        }
        return success;
    }

    public String getOrigin() {
        return this.origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getPreview() {
        return this.preview;
    }
    
    public Path getPreviewPath() {
        return getPath(getPreview());
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    public Map<String, String> getData() {
        return this.data;
    }
    
    public Path getData(String key) {
        return getPath(getData().get(key));
    }

    public Element getExtension() {
        return this.extension;
    }

    public void setExtension(Element extension) {
        this.extension = extension;
    }
    
    public Path getPath(String path) {
        if (path == null) {
            return null;
        }
        ResourcePack pack = getResourcePack();
        if (pack == null) {
            return null;
        }
        FileSystem system = pack.getFileSystem();
        if (system == null || !system.isOpen()) {
            return null;
        }
        return system.getPath("/").resolve(path);
    }
}
