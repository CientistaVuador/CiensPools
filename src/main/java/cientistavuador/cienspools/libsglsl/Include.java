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
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 *
 * @author Cien
 */
public class Include {

    public static interface IncludeResolver {

        public String resolve(String file, boolean relative) throws IOException;
    }

    private static class CharStream {

        String data;
        StringBuilder output = new StringBuilder();
        int index = 0;

        char read() {
            return data.charAt(index);
        }

        boolean canContinue() {
            return index < data.length();
        }
        
        boolean hasNext() {
            return index < data.length() - 1;
        }

        char peek() {
            char n = '\0';
            if (hasNext()) {
                n = data.charAt(index + 1);
            }
            return n;
        }

        void output() {
            output.append(read());
        }

        void move() {
            index++;
        }
    }

    private static void readComment(CharStream stream) {
        while (stream.canContinue()) {
            char c = stream.read();
            char n = stream.peek();

            if (c == '*' && n == '/') {
                stream.output();
                stream.move();

                stream.output();
                stream.move();
                break;
            }

            stream.output();
            stream.move();
        }
    }

    private static boolean readInclude(CharStream stream) {
        char[] text = "#include".toCharArray();
        int localIndex = 0;
        while (stream.canContinue()) {
            char c = stream.read();

            if (c != text[localIndex]) {
                return false;
            }

            stream.output();
            stream.move();
            localIndex++;

            if (localIndex == text.length) {
                return true;
            }
        }
        return false;
    }

    private static void readWhitespace(CharStream stream) {
        boolean escape = false;
        while (stream.canContinue()) {
            char c = stream.read();
            char n = stream.peek();

            if (escape) {
                escape = false;
                if (c == '\r' && n == '\n') {
                    stream.output();
                    stream.move();
                }
                stream.output();
                stream.move();
                continue;
            }

            if (c == '\\' && (n == '\r' || n == '\n')) {
                escape = true;
                stream.output();
                stream.move();
                continue;
            }

            if (c == ' ' || c == '\t') {
                stream.output();
                stream.move();
                continue;
            }

            if (c == '/' && n == '*') {
                readComment(stream);
                continue;
            }

            break;
        }
    }

    private static void skipLine(CharStream stream) {
        while (stream.canContinue()) {
            char c = stream.read();
            char n = stream.peek();

            if (c == '\n' || c == '\r') {
                if (c == '\r' && n == '\n') {
                    stream.output();
                    stream.move();
                }
                stream.output();
                stream.move();
                break;
            }

            switch (c) {
                case ' ', '\t', '\\', '/' -> {
                    int before = stream.index;
                    readWhitespace(stream);
                    if (stream.index - before > 0) {
                        continue;
                    }
                }
            }

            stream.output();
            stream.move();
        }
    }

    private static boolean readQuotes(CharStream stream) {
        boolean firstQuoteFound = false;
        while (stream.canContinue()) {
            char c = stream.read();

            if (c == '"') {
                if (!firstQuoteFound) {
                    firstQuoteFound = true;
                } else {
                    stream.output();
                    stream.move();
                    return true;
                }
            }

            stream.output();
            stream.move();
        }
        return false;
    }

    private static boolean readAngularBrackets(CharStream stream) {
        while (stream.canContinue()) {
            char c = stream.read();

            if (c == '>') {
                stream.output();
                stream.move();
                return true;
            }
            
            stream.output();
            stream.move();
        }
        return false;
    }

    private static String parse(String code, IncludeResolver resolver, int depth) throws IOException {
        if (depth > 64) {
            throw new IllegalArgumentException("Too deep.");
        }
        Objects.requireNonNull(code, "code is null");
        Objects.requireNonNull(resolver, "resolver is null");

        CharStream stream = new CharStream();
        stream.data = code;

        while (true) {
            if (!stream.canContinue()) {
                break;
            }
            
            int lengthLineStart = stream.output.length();
            readWhitespace(stream);
            if (!stream.canContinue()) {
                break;
            }
            
            if (stream.read() != '#') {
                skipLine(stream);
                continue;
            }
            if (!readInclude(stream)) {
                skipLine(stream);
                continue;
            }
            
            readWhitespace(stream);
            if (!stream.canContinue()) {
                break;
            }
            
            boolean relative;
            int lengthFieldStart = stream.output.length();
            {
                char c = stream.read();
                switch (c) {
                    case '"' -> {
                        if (!readQuotes(stream)) {
                            skipLine(stream);
                            continue;
                        }
                        relative = false;
                    }
                    case '<' -> {
                        if (!readAngularBrackets(stream)) {
                            skipLine(stream);
                            continue;
                        }
                        relative = true;
                    }
                    default -> {
                        skipLine(stream);
                        continue;
                    }
                }
            }
            String file = stream.output.substring(lengthFieldStart + 1, stream.output.length() - 1);
            String resolved = parse(resolver.resolve(file, relative), resolver, depth + 1);
            
            skipLine(stream);
            
            stream.output.setLength(lengthLineStart);
            stream.output.append(resolved).append("\n");
        }
        return stream.output.toString();
    }

    public static String parse(String code, IncludeResolver resolver) throws IOException {
        return parse(code, resolver, 0);
    }
    
    public static final Map<String, Supplier<String>> DYNAMIC_FILES = new ConcurrentHashMap<>();
    
    public static final IncludeResolver DEFAULT_RESOLVER = (String file, boolean relative) -> {
        boolean staticFile;
        if (file.startsWith("dynamic/")) {
            file = file.substring("dynamic/".length());
            staticFile = false;
        } else {
            staticFile = true;
        }
        
        if (staticFile) {
            try (InputStream in = Include.class.getResourceAsStream(file)) {
                if (in == null) {
                    return "#error File not found "+file;
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            Supplier<String> s = DYNAMIC_FILES.get(file);
            if (s == null) {
                return "#error File not found "+file;
            }
            return s.get();
        }
    };
    
    public static String parse(String code) {
        try {
            return parse(code, DEFAULT_RESOLVER);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Include() {

    }

}
