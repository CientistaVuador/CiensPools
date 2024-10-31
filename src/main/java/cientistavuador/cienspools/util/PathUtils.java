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

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Cien
 */
public class PathUtils {
    
    private static final Map<String, WeakReference<FileSystem>> cache = new HashMap<>();
    
    private static final String[] problematicWords = {
        "CON", "PRN", "AUX", "NUL", 
        "COM0", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT0", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };
    private static final Set<String> problematicWordsSet = new HashSet<>();
    static {
        for (String s:problematicWords) {
            problematicWordsSet.add(s.toUpperCase());
            problematicWordsSet.add(s.toLowerCase());
        }
    }
    
    public static String cleanupPathName(String name) {
        if (name == null) {
            return null;
        }
        if (name.isEmpty()) {
            return "empty";
        }
        
        StringBuilder b = new StringBuilder();
        
        int codepoint;
        for (int i = 0; i < name.length(); i += Character.charCount(codepoint)) {
            codepoint = name.codePointAt(i);
            if (!Character.isLetterOrDigit(codepoint)) {
                b.append(' ');
                continue;
            }
            b.appendCodePoint(codepoint);
        }
        
        String output = Stream
                .of(b.toString().split(" "))
                .filter((s) -> !s.isEmpty())
                .collect(Collectors.joining(" "));
        
        if (problematicWordsSet.contains(output)) {
            output = "_" + output;
        }
        
        if (output.length() > 64) {
            output = output.substring(0, 64);
        }
        
        return output;
    }
    
    public static Path pathOf(Class<?> clazz) throws IOException {
        URL clazzOrigin = clazz.getProtectionDomain().getCodeSource().getLocation();
        if (clazzOrigin == null) {
            throw new IOException("Class has no origin.");
        }
        try {
            Path originPath = Path.of(clazzOrigin.toURI());
            if (!Files.exists(originPath)) {
                throw new IOException("Class origin is not valid.");
            }
            
            if (Files.isDirectory(originPath)) {
                Path classFolder = originPath
                    .resolve(clazz.getPackageName().replace(".", File.separator))
                    ;
                if (!Files.isDirectory(classFolder)) {
                    throw new IOException(classFolder+" is not a directory.");
                }
                return classFolder;
            }
            
            if (!Files.isRegularFile(originPath)) {
                throw new IOException(originPath+" is not a regular file.");
            }
            
            FileSystem jarFileSystem;
            synchronized (cache) {
                String key = originPath.toAbsolutePath().toString();
                WeakReference<FileSystem> reference = cache.get(key);
                if (reference == null 
                        || (jarFileSystem = reference.get()) == null 
                        || !jarFileSystem.isOpen()) {
                    jarFileSystem = FileSystems.newFileSystem(originPath);
                    cache.put(key, new WeakReference<>(jarFileSystem));
                }
            }
            
            Path classFolder = jarFileSystem
                    .getPath("/", clazz.getPackageName().replace(".", "/"));
            if (!Files.isDirectory(classFolder)) {
                throw new IOException(classFolder+" is not a directory inside of "+originPath);
            }
            
            return classFolder;
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
    }
    
    public static void createDirectories(Path p) throws IOException {
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
    }
    
    public static FileSystem createFileSystem(Path path) throws IOException {
        createDirectories(path);
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        return FileSystems.newFileSystem(path, env);
    }
    
    private PathUtils() {
        
    }
}
