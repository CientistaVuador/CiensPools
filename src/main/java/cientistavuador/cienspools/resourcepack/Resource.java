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

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 *
 * @author Cien
 */
public class Resource {

    public static class IDSyntax {

        private final UUID uuid;
        private final String name;

        public IDSyntax(String id) {
            if (id == null) {
                id = "null";
            }
            if (id.length() < 35) {
                this.uuid = null;
                this.name = id;
                return;
            }
            StringBuilder b = new StringBuilder();
            String most = null;
            String least = null;
            parse: {
                int index = 0;
                if (id.charAt(index++) != '[') {
                    break parse;
                }
                for (int i = 0; i < 16; i++) {
                    char c = id.charAt(index++);
                    if (c < '0' && c > '9' && c < 'A' && c > 'F') {
                        break parse;
                    }
                    b.append(c);
                }
                most = b.toString();
                b.setLength(0);
                if (id.charAt(index++) != '|') {
                    break parse;
                }
                for (int i = 0; i < 16; i++) {
                    char c = id.charAt(index++);
                    if (c < '0' && c > '9' && c < 'A' && c > 'F') {
                        break parse;
                    }
                    b.append(c);
                }
                least = b.toString();
                b.setLength(0);
                if (id.charAt(index++) != ']') {
                    break parse;
                }
            }
            if (most == null || least == null) {
                this.uuid = null;
                this.name = id;
                return;
            }
            this.uuid = new UUID(Long.parseUnsignedLong(most, 16), Long.parseUnsignedLong(least, 16));
            this.name = id.substring(35);
        }
        
        public IDSyntax(UUID uuid, String name) {
            if (name == null) {
                name = "null";
            }
            this.uuid = uuid;
            this.name = name;
        }

        public UUID uuid() {
            return uuid;
        }

        public String name() {
            return name;
        }

        @Override
        public String toString() {
            if (uuid() == null) {
                return name();
            }
            
            String most = Long.toHexString(uuid().getMostSignificantBits()).toUpperCase();
            String least = Long.toHexString(uuid().getLeastSignificantBits()).toUpperCase();
            
            most = "0".repeat(16 - most.length()) + most;
            least = "0".repeat(16 - least.length()) + least;
            
            return "[" + most + "|" + least + "]" + name();
        }
    }

    public static Resource get(String type, String id) {
        return ResourceLocator.get(type, id);
    }
    
    public static String generateRandomId(String suffix) {
        return new IDSyntax(UUID.randomUUID(), suffix).toString();
    }
    
    private ResourcePack resourcePack;
    private Authorship externalAuthorship;
    
    private final String type;
    private final String id;
    private final int priority;
    private final Set<String> aliases;
    private final Authorship authorship;
    private final Map<String, String> meta;
    private final Map<String, Path> data;
    private WeakReference<Object> associatedObject = null;

    protected Resource(
            String type,
            String id,
            int priority,
            Set<String> aliases,
            Authorship authorship,
            Map<String, String> meta,
            Map<String, Path> data
    ) {
        this.type = Objects.requireNonNull(type);
        this.id = Objects.requireNonNull(id);
        this.priority = priority;
        this.aliases = Collections.unmodifiableSet(aliases);
        this.authorship = authorship;
        this.meta = Collections.unmodifiableMap(meta);
        this.data = Collections.unmodifiableMap(data);

        leak();
    }

    private void leak() {
        if (this.authorship != null) {
            this.authorship.resource = this;
        }
    }

    protected void attachResourcePack(ResourcePack p) {
        this.resourcePack = p;
        this.externalAuthorship = this.authorship;
        
        Resource externalAuthorshipResource = p.getResource("authorship", this.id);
        if (externalAuthorshipResource != null) {
            if (externalAuthorshipResource.getAuthorship() != null) {
                this.externalAuthorship = externalAuthorshipResource.getAuthorship();
            }
        }
    }
    
    public ResourcePack getResourcePack() {
        return resourcePack;
    }
    
    public Authorship getExternalAuthorship() {
        return externalAuthorship;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public Set<String> getAliases() {
        return aliases;
    }

    public Authorship getAuthorship() {
        return authorship;
    }
    
    public Map<String, String> getMeta() {
        return meta;
    }

    public Map<String, Path> getData() {
        return data;
    }

    public Object getAssociatedObject() {
        if (this.associatedObject == null) {
            return null;
        }
        Object obj = this.associatedObject.get();
        if (obj == null) {
            this.associatedObject = null;
        }
        return obj;
    }

    public void setAssociatedObject(Object obj) {
        if (obj == null) {
            this.associatedObject = null;
        }
        this.associatedObject = new WeakReference<>(obj);
    }

}
