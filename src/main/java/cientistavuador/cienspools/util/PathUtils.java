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
import java.util.Map;

/**
 *
 * @author Cien
 */
public class PathUtils {
    
    private static final Map<String, WeakReference<FileSystem>> cache = new HashMap<>();
    
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
    
    private PathUtils() {
        
    }
}
