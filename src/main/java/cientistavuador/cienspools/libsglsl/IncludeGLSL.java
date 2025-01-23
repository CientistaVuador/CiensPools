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
package cientistavuador.cienspools.libsglsl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 *
 * @author Cien
 */
public class IncludeGLSL {

    private static final Map<String, String> cache = Collections.synchronizedMap(new WeakHashMap<>());

    private static String get(String file) {
        {
            String cached = cache.get(file);
            if (cached != null) {
                return cached;
            }
        }

        String result;
        readFile:
        {
            try {
                try (InputStream in = IncludeGLSL.class.getResourceAsStream(file);) {
                    if (in == null) {
                        result = "#error " + file + " not found.";
                        break readFile;
                    }
                    result = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                            .lines()
                            .collect(Collectors.joining("\n"));
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        
        cache.put(file, result);
        
        return result;
    }

    public static String parse(String code) {
        if (code == null) {
            return null;
        }
        
        StringBuilder b = new StringBuilder();
        String[] lines = code.lines().toArray(String[]::new);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            findInclude: {
                String trimmed = line.trim();
                if (!trimmed.startsWith("#include")) {
                    break findInclude;
                }
                String sub = trimmed.substring("#include".length()).trim();
                if (!sub.startsWith("\"") || !sub.endsWith("\"")) {
                    line = "#error Invalid include syntax, expected a enclosed double quotes file name.";
                    break findInclude;
                }
                String sub2 = sub.substring(1, sub.length() - 1);
                if (sub2.isBlank()) {
                    line = "#error Blank include file name.";
                    break findInclude;
                }
                line = get(sub2);
            }
            
            b.append(line);
            if (i != (lines.length - 1)) {
                b.append("\n");
            }
        }
        return b.toString();
    }
    
    private IncludeGLSL() {

    }

}
