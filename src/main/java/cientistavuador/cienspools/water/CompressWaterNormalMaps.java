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
package cientistavuador.cienspools.water;

import cientistavuador.cienspools.util.DXT5TextureStore;
import cientistavuador.cienspools.util.DXT5TextureStore.DXT5Texture;
import cientistavuador.cienspools.util.RGBA8Image;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Cien
 */
public class CompressWaterNormalMaps {

    private static void compressImpl(ZipInputStream in, ZipOutputStream out) 
            throws IOException 
    {
        String[] acceptedExtensions = {
            ".png", ".jpg", ".jpeg"
        };
        
        ZipEntry entryInput;
        while ((entryInput = in.getNextEntry()) != null) {
            if (entryInput.isDirectory()) {
                continue;
            }
            boolean accepted = false;
            for (String ext:acceptedExtensions) {
                if (entryInput.getName().endsWith(ext)) {
                    accepted = true;
                }
            }
            if (!accepted) {
                continue;
            }
            
            RGBA8Image image = RGBA8Image.fromPNG(in.readAllBytes()).mipmap();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int nx = image.sample(x, y, 0);
                    int ny = image.sample(x, y, 1);
                    image.write(x, y, nx, nx, nx, ny);
                }
            }
            DXT5Texture texture = DXT5TextureStore.createDXT5Texture(
                    image.getRGBA(), image.getWidth(), image.getHeight());
            
            out.putNextEntry(new ZipEntry(entryInput.getName() + "." + DXT5TextureStore.EXTENSION));
            DXT5TextureStore.writeDXT5Texture(texture, out);
            out.closeEntry();
        }
    }
    
    public static void compress(
            Path inputZip,
            Path outputZip
    ) throws IOException {
        try (ZipInputStream in = new ZipInputStream(
                new BufferedInputStream(
                        Files.newInputStream(inputZip)), StandardCharsets.UTF_8);) {
            try (ZipOutputStream out = new ZipOutputStream(
                        new BufferedOutputStream(
                                Files.newOutputStream(outputZip)), StandardCharsets.UTF_8);) {
                compressImpl(in, out);
            }
        }
    }

    private CompressWaterNormalMaps() {

    }
}
