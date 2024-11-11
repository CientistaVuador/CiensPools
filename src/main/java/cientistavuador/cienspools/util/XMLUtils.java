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
package cientistavuador.cienspools.util;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import org.joml.Vector3f;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author Cien
 */
public class XMLUtils {

    public static final ErrorHandler ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    };

    private static final Map<Schema, DocumentBuilder> builderMap
            = Collections.synchronizedMap(new HashMap<>());

    private static DocumentBuilder getDocumentBuilderOf(Schema schema) 
            throws ParserConfigurationException {
        DocumentBuilder builder = builderMap.get(schema);
        if (builder != null) {
            return builder;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setSchema(schema);
        factory.setCoalescing(true);

        builder = factory.newDocumentBuilder();
        builder.setErrorHandler(ERROR_HANDLER);
        builderMap.put(schema, builder);
        
        return builder;
    }

    public static Document parseXML(InputSource source, Schema schema)
            throws SAXException, IOException {
        try {
            Document doc = getDocumentBuilderOf(schema).parse(source);
            Element rootElement = doc.getDocumentElement();
            rootElement.normalize();

            return doc;
        } catch (ParserConfigurationException ex) {
            throw new SAXException(ex);
        }
    }

    public static Element getFirstElement(Element parent) {
        NodeList children = parent.getElementsByTagName("*");
        if (children.getLength() != 0) {
            return (Element) children.item(0);
        }
        return null;
    }

    public static Element getElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        for (int i = 0; i < children.getLength(); i++) {
            Element e = (Element) children.item(i);
            if (e.getParentNode() == parent) {
                return e;
            }
        }
        return null;
    }

    public static String getElementText(Element parent, String name) {
        Element top = getElement(parent, name);
        if (top == null) {
            return null;
        }
        return top.getTextContent();
    }

    public static Vector3f getVector3f(Element element) {
        if (element == null) {
            return null;
        }
        return new Vector3f(
                Float.parseFloat(element.getAttribute("x")),
                Float.parseFloat(element.getAttribute("y")),
                Float.parseFloat(element.getAttribute("z"))
        );
    }

    public static String escapeText(String text) {
        return XMLUtils.escapeText(text, true);
    }

    public static String escapeText(String text, boolean escapeControlChars) {
        StringBuilder b = new StringBuilder();

        int codePoint;
        for (int i = 0; i < text.length(); i += Character.charCount(codePoint)) {
            codePoint = text.codePointAt(i);
            if (escapeControlChars) {
                switch (codePoint) {
                    case '\t' -> {
                        b.append("&#9;");
                        continue;
                    }
                    case '\n' -> {
                        b.append("&#10;");
                        continue;
                    }
                    case '\r' -> {
                        b.append("&#13;");
                        continue;
                    }
                }
            }
            if (Character.isISOControl(codePoint)
                    && codePoint != '\n'
                    && codePoint != '\r'
                    && codePoint != '\t') {
                throw new IllegalArgumentException("Invalid ISO Control character at index " + i);
            }
            switch (codePoint) {
                case '&' -> {
                    b.append("&amp;");
                    continue;
                }
                case '<' -> {
                    b.append("&lt;");
                    continue;
                }
                case '>' -> {
                    b.append("&gt;");
                    continue;
                }
            }
            b.appendCodePoint(codePoint);
        }

        return b.toString();
    }

    public static String quoteAttribute(String text) {
        StringBuilder b = new StringBuilder();

        boolean doubleQuotes = text.contains("\"");
        boolean singleQuotes = text.contains("\'");

        char quoteStyle = '"';
        if (doubleQuotes && !singleQuotes) {
            quoteStyle = '\'';
        }

        int codePoint;
        for (int i = 0; i < text.length(); i += Character.charCount(codePoint)) {
            codePoint = text.codePointAt(i);
            switch (codePoint) {
                case '\t' -> {
                    b.append("&#9;");
                    continue;
                }
                case '\n' -> {
                    b.append("&#10;");
                    continue;
                }
                case '\r' -> {
                    b.append("&#13;");
                    continue;
                }
            }
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException("Invalid ISO Control character at index " + i);
            }
            switch (codePoint) {
                case '"' -> {
                    if (quoteStyle == '"') {
                        b.append("&quot;");
                        continue;
                    }
                }
                case '\'' -> {
                    if (quoteStyle == '\'') {
                        b.append("&apos;");
                        continue;
                    }
                }
                case '&' -> {
                    b.append("&amp;");
                    continue;
                }
                case '<' -> {
                    b.append("&lt;");
                    continue;
                }
                case '>' -> {
                    b.append("&gt;");
                    continue;
                }
            }
            b.appendCodePoint(codePoint);
        }

        b.insert(0, quoteStyle);
        b.append(quoteStyle);

        return b.toString();
    }

    private XMLUtils() {

    }
}
