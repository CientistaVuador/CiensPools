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
package cientistavuador.cienspools.generic;

import cientistavuador.cienspools.util.XMLUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class GenericData {
    
    public static class Reference {
        private final String reference;

        public Reference(String reference) {
            this.reference = reference;
        }

        public String get() {
            return reference;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 23 * hash + Objects.hashCode(this.reference);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Reference other = (Reference) obj;
            return Objects.equals(this.reference, other.reference);
        }
        
    }
    
    public static interface Serializer {
        public String getTypeName();
        public String write(Object object, String indent);
        public Object read(Element element);
    }
    
    public static final String INDENT = " ".repeat(4);
    public static final Set<Serializer> SERIALIZERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    static {
        DefaultSerializers.init();
    }
    
    private String type = "unknown";
    private final Map<String, Object> data = Collections.synchronizedMap(new LinkedHashMap<>());
    
    public GenericData() {
        
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        if (type == null) {
            type = "";
        }
        this.type = type;
    }

    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(String key, Object d) {
        getData().put(key, d);
    }
    
    public <T> T getDataOrDefault(String key, Class<T> clazz, T def) {
        Objects.requireNonNull(clazz, "Clazz is null.");
        
        Object d = getData().get(key);
        if (d != null && clazz.isInstance(d)) {
            return clazz.cast(d);
        }
        return def;
    } 
    
    private String writeData(String indent) {
        StringBuilder b = new StringBuilder();
        for (Entry<String, Object> entry:getData().entrySet()) {
            String key = entry.getKey();
            Object object = entry.getValue();
            
            String serialized = null;
            for (Serializer serializer:SERIALIZERS) {
                serialized = serializer.write(object, indent);
                if (serialized != null) {
                    break;
                }
            }
            if (serialized == null) {
                throw new IllegalArgumentException("No serializer found for "+object.getClass());
            }
            
            b.append(b);
        }
        return b.toString();
    }
    
    public String toString(boolean includeXmlHeader) {
        StringBuilder b = new StringBuilder();
        if (includeXmlHeader) {
            b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        }
        b.append("<generic ofType=")
                .append(XMLUtils.quoteAttribute(getType()))
                .append(" xmlns=\"https://cientistavuador.github.io/schemas/generic.xsd\">\n");
        b.append(writeData(INDENT));
        b.append("</generic>");
        return b.toString();
    }
    
    @Override
    public String toString() {
        return toString(false);
    }
    
}
