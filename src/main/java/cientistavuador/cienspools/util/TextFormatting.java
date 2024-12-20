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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Cien
 */
public class TextFormatting {
    
    private static String[] splitByComma(String text) {
        List<String> list = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int unicode = text.codePointAt(i);
            if (Character.charCount(unicode) == 2) {
                i++;
            }
            boolean hasNext = (i < text.length() - 1);
            if (unicode == ',') {
                if (hasNext && text.codePointAt(i + 1) == ',') {
                    i++;
                    b.append(',');
                    continue;
                }
                list.add(b.toString());
                b.setLength(0);
                continue;
            }
            b.appendCodePoint(unicode);
        }
        list.add(b.toString());
        return list.toArray(String[]::new);
    }
    
    public static String collapse(String text) {
        return Stream.of(
                text
                        .replaceAll("[\r\n\t]", " ")
                        .split(" ")
        )
                .filter((s) -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }
    
    public static String getTitleFormatted(String text) {
        return text
                .lines()
                .map(s -> s.trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    public static String getParagraphFormatted(String text) {
        return text
                .lines()
                .map(s -> s.trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
    }
    
    public static String[] getListFormatted(String text) {
        return Stream
                .of(splitByComma(text))
                .map(s -> getTitleFormatted(s))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
    
    public static String getCodeFormatted(String text) {
        return text
                .lines()
                .filter(new Predicate<String>() {
                    boolean startFound = false;

                    @Override
                    public boolean test(String t) {
                        if (t.isBlank() && !this.startFound) {
                            return false;
                        }
                        this.startFound = true;
                        return true;
                    }
                })
                .collect(Collectors.joining("\n"))
                .stripTrailing()
                .stripIndent();
    }
    
    public static String beautify(String text) {
        String[] words = collapse(text).split(" ");
        
        StringBuilder b = new StringBuilder();
        
        int currentCharCount = 0;
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (currentCharCount != 0 && (currentCharCount + word.length()) >= 64) {
                b.append("\n");
                currentCharCount = 0;
            } else if (i != 0) {
                b.append(" ");
                currentCharCount++;
            }
            b.append(word);
            currentCharCount += word.length();
        }
        
        return b.toString();
    }
    
    private TextFormatting() {

    }

}
