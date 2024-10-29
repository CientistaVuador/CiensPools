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

import static cientistavuador.cienspools.generic.GenericData.INDENT;
import static cientistavuador.cienspools.generic.GenericData.SERIALIZERS;
import cientistavuador.cienspools.util.XMLUtils;
import org.w3c.dom.Element;

/**
 *
 * @author Cien
 */
public class DefaultSerializers {
    
    static {
        SERIALIZERS.add(new GenericData.Serializer() {
            @Override
            public String getTypeName() {
                return "string";
            }

            @Override
            public String write(Object object, String indent) {
                if (object instanceof String s) {
                    return XMLUtils.escapeText(s);
                }
                return null;
            }

            @Override
            public Object read(Element element) {
                return element.getTextContent();
            }
        });
        SERIALIZERS.add(new GenericData.Serializer() {
            @Override
            public String getTypeName() {
                return "stringArray";
            }

            @Override
            public String write(Object object, String indent) {
                if (object instanceof String[] array) {
                    if (array.length == 0) {
                        return "";
                    }
                    StringBuilder b = new StringBuilder();
                    b.append("\n");
                    for (String s:array) {
                        b.append(indent).append("<s>").append(XMLUtils.escapeText(s)).append("</s>\n");
                    }
                    return b.toString();
                }
                return null;
            }

            @Override
            public Object read(Element element) {
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }
        });
    }
    
    public static void init() {
        
    }
    
    private DefaultSerializers() {
        
    }
}
