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
package cientistavuador.cienspools.lut;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.util.ObjectCleaner;
import cientistavuador.cienspools.util.RGBA8Image;
import cientistavuador.cienspools.util.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public class LUT {
    
    private static LUT loadDefaultLUT(String file, String id) {
        try {
            InputStream s = LUT.class.getResourceAsStream(file);
            if (s == null) {
                throw new IOException(file+" not found.");
            }
            try (s) {
                return new LUT(id, RGBA8Image.fromPNG(s.readAllBytes()));
            }
        } catch (IOException ex) {
            System.out.println("Failed to load default LUT of id '"+id+"' from file '"+file+"'.");
            return new LUT(id, LUTGenerator.generate());
        }
    }
    
    public static final LUT NEUTRAL = 
            loadDefaultLUT("neutral.png", "Neutral 3D LUT");
    public static final LUT MEXICAN_FILTER = 
            loadDefaultLUT("mexican_filter.png", "Mexican Filter 3D LUT");
    public static final LUT RUSSIAN_FILTER =
            loadDefaultLUT("russian_filter.png", "Russian Filter 3D LUT");
    
    private static class WrappedTexture {

        public int texture = 0;
    }

    private final String id;
    private final RGBA8Image image;
    private final int size;

    private final WrappedTexture wrappedTexture = new WrappedTexture();

    public LUT(String id, RGBA8Image image) {
        id = Objects.requireNonNullElse(id, Resource.generateRandomId(null));
        Objects.requireNonNull(image, "image is null");
        int lutSize = image.getWidth();
        if (image.getHeight() != (lutSize * lutSize)) {
            throw new IllegalArgumentException(
                    "invalid lut, height must be "
                    + (lutSize * lutSize)
                    + " not " + image.getHeight());
        }
        this.id = id;
        this.image = image;
        this.size = lutSize;

        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedTexture finalWrappedTexture = this.wrappedTexture;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                int texture = finalWrappedTexture.texture;

                if (texture != 0) {
                    glDeleteTextures(texture);
                    finalWrappedTexture.texture = 0;
                }
            });
        });
    }

    public String getId() {
        return id;
    }

    public RGBA8Image getImage() {
        return image;
    }

    public int getSize() {
        return size;
    }

    private void validateTextures() {
        if (this.wrappedTexture.texture != 0) {
            return;
        }
        
        glActiveTexture(GL_TEXTURE0);
        
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, texture);
        
        ByteBuffer nativeData = memAlloc(this.image.getRGBA().length)
                .put(this.image.getRGBA())
                .flip();
        try {
            glTexImage3D(
                    GL_TEXTURE_3D, 0,
                    GL_RGBA8, getSize(), getSize(), getSize(),
                    0,
                    GL_RGBA, GL_UNSIGNED_BYTE, nativeData);
        } finally {
            memFree(nativeData);
        }
        
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        
        glBindTexture(GL_TEXTURE_3D, 0);
        
        if (GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_TEXTURE, texture,
                    StringUtils.truncateStringTo255Bytes(this.id)
            );
        }
        
        this.wrappedTexture.texture = texture;
    }
    
    public int texture() {
        validateTextures();
        return this.wrappedTexture.texture;
    }
    
    public void manualFree() {
        final WrappedTexture finalWrappedTexture = this.wrappedTexture;

        int texture = finalWrappedTexture.texture;

        if (texture != 0) {
            glDeleteTextures(texture);
            finalWrappedTexture.texture = 0;
        }
    }

}
