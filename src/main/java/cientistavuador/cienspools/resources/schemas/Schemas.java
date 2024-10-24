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
package cientistavuador.cienspools.resources.schemas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author Cien
 */
public class Schemas {

    private static final ConcurrentHashMap<String, Schema> schemas = new ConcurrentHashMap<>();

    public static Schema getSchema(String file, String... more) {
        List<String> files = new ArrayList<>();
        files.add(file);
        files.addAll(Arrays.asList(more));
        files.sort(String.CASE_INSENSITIVE_ORDER);
        String key = files.stream().collect(Collectors.joining("\n"));
        
        {
            Schema sc = schemas.get(key);
            if (sc != null) {
                return sc;
            }
        }
        SchemaFactory factory = SchemaFactory.newDefaultInstance();
        factory.setErrorHandler(new ErrorHandler() {
            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        List<Source> sources = new ArrayList<>();
        sources.add(new StreamSource(Schemas.class.getResourceAsStream(file)));
        for (String m:more) {
            sources.add(new StreamSource(Schemas.class.getResourceAsStream(m)));
        }
        try {
            Schema sc = factory.newSchema(sources.toArray(Source[]::new));
            schemas.put(key, sc);
            return sc;
        } catch (SAXException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Schemas() {

    }
}
