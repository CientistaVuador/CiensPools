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

import cientistavuador.cienspools.util.XMLUtils;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

/**
 *
 * @author Cien
 */
public class Resource {
    
    public static String generateRandomId(String suffix) {
        UUID uuid = UUID.randomUUID();
        String most = Long.toHexString(uuid.getMostSignificantBits()).toUpperCase();
        String least = Long.toHexString(uuid.getLeastSignificantBits()).toUpperCase();
        return most + "|" + least + (suffix != null && !suffix.isEmpty() ? "|" + suffix : "");
    }
    
    protected volatile ResourcePack resourcePack;
    
    private String type = "unknown";
    private String id = generateRandomId(null);
    private int priority = 0;
    private final Set<String> aliases = new HashSet<>();
    private String origin = null;
    private String preview = null;
    private String title = null;
    private String description = null;
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private final Map<String, String> data = new LinkedHashMap<>();
    
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
    
    public void setRandomId(String suffix) {
        setId(generateRandomId(suffix));
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
        if (alias == null) {
            alias = "";
        }
        boolean success = this.aliases.add(alias);
        if (success && getResourcePack() != null) {
            getResourcePack().onAliasAdded(this, alias);
        }
        return success;
    }
    
    public boolean removeAlias(String alias) {
        if (alias == null) {
            alias = "";
        }
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
    
    private String writeAliases(int indent) {
        if (getAliases().isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append("<aliases>\n");
        String in = " ".repeat(indent);
        for (String alias:getAliases()) {
            b.append(in).append("<id>").append(XMLUtils.escapeText(alias)).append("</id>\n");
        }
        b.append("</aliases>");
        return b.toString().indent(4);
    }
    
    private String writeAuthorship(int indent) {
        String o = getOrigin();
        String p = getPreview();
        String t = getTitle();
        String d = getDescription();
        
        if (o == null 
                && p == null 
                && t == null 
                && d == null) {
            return "";
        }
        String in = " ".repeat(indent);
        
        StringBuilder b = new StringBuilder();
        b.append("<authorship>\n");
        if (o != null) {
            b.append(in).append("<origin>").append(XMLUtils.escapeText(o)).append("</origin>\n");
        }
        if (p != null) {
            b.append(in).append("<preview>").append(XMLUtils.escapeText(p)).append("</preview>\n");
        }
        if (t != null) {
            b.append(in).append("<title>").append(XMLUtils.escapeText(t)).append("</title>\n");
        }
        if (d != null) {
            b.append(in).append("<description>").append(XMLUtils.escapeText(d)).append("</description>\n");
        }
        b.append("</authorship>");
        return b.toString().indent(indent);
    }
    
    private String writeMeta(int indent) {
        if (getMetadata().isEmpty()) {
            return "";
        }
        
        String in = " ".repeat(indent);
        
        StringBuilder b = new StringBuilder();
        b.append("<meta>\n");
        for (Entry<String, String> entry:getMetadata().entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String value = entry.getValue();
            if (value == null) {
                value = "";
            }
            
            b.append(in).append("<entry key=").append(XMLUtils.quoteAttribute(key));
            if (value.isEmpty()) {
                b.append("/>\n");
            } else {
                b.append(">").append(XMLUtils.escapeText(value)).append("</entry>\n");
            }
        }
        b.append("</meta>");
        return b.toString().indent(indent);
    }
    
    private String writeData(int indent) {
        if (getData().isEmpty()) {
            return "";
        }
        
        String in = " ".repeat(indent);
        
        StringBuilder b = new StringBuilder();
        b.append("<data>\n");
        for (Entry<String, String> entry:getData().entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String value = entry.getValue();
            if (value == null) {
                value = "";
            }
            
            b.append(in)
                    .append("<file type=")
                    .append(XMLUtils.quoteAttribute(key))
                    .append(">")
                    .append(XMLUtils.escapeText(value))
                    .append("</file>\n")
                    ;
        }
        b.append("</data>");
        return b.toString().indent(indent);
    }
    
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("<resource type=")
                .append(XMLUtils.quoteAttribute(getType()))
                .append(" id=")
                .append(XMLUtils.quoteAttribute(getId()))
                ;
        if (getPriority() != 0) {
            b.append(" priority=\"").append(getPriority()).append("\"");
        }
        b.append(">\n");
        final int indent = 4;
        b.append(writeAliases(indent));
        b.append(writeAuthorship(indent));
        b.append(writeMeta(indent));
        b.append(writeData(indent));
        b.append("</resource>");
        return b.toString();
    }
    
    
}
