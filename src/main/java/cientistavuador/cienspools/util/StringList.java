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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Cien
 */
public class StringList {

    public static String toString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int stringIndex = 0; stringIndex < list.size(); stringIndex++) {
            String s = list.get(stringIndex);
            if (s == null || s.isEmpty()) {
                b.append(";");
            } else {
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    switch (c) {
                        case '\\', '#', ';' -> {
                            b.append("\\");
                        }
                        case '\n' -> {
                            b.append("\\n");
                            continue;
                        }
                        case '\r' -> {
                            b.append("\\r");
                            continue;
                        }
                        case '\t' -> {
                            b.append("\\t");
                            continue;
                        }
                    }
                    b.append(c);
                }
            }
            if (stringIndex != (list.size() - 1)) {
                b.append(System.lineSeparator());
            }
        }
        return b.toString();
    }

    public static List<String> fromString(String s) {
        if (s == null || s.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> output = new ArrayList<>();
        String[] lines = s.lines().toArray(String[]::new);
        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith(";")) {
                output.add("");
                continue;
            }
            StringBuilder b = new StringBuilder();
            boolean escape = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (escape) {
                    escape = false;
                    switch (c) {
                        case 'n' -> {
                            b.append('\n');
                            continue;
                        }
                        case 'r' -> {
                            b.append('\r');
                            continue;
                        }
                        case 't' -> {
                            b.append('\t');
                            continue;
                        }
                    }
                    b.append(c);
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                b.append(c);
            }
            output.add(b.toString());
        }
        return output;
    }

    private StringList() {

    }

}
