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
import cientistavuador.cienspools.util.Pair;
import cientistavuador.cienspools.util.StringUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class Water {

    public static final int TEXTURE;
    public static float WATER_COUNTER = 0f;
    public static final float WATER_COUNTER_SPEED = 0.45f;
    
    public static void update(double tpf) {
        WATER_COUNTER += tpf * WATER_COUNTER_SPEED;
        if (WATER_COUNTER > 1f) {
            WATER_COUNTER = 0;
        }
    }
    
    public static void init() {

    }

    public static List<DXT5Texture> readWaterTextures(ZipInputStream in) throws IOException {
        List<Pair<Integer, DXT5Texture>> textures = new ArrayList<>();

        ZipEntry entry;
        while ((entry = in.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            if (!entry.getName().endsWith("." + DXT5TextureStore.EXTENSION)) {
                continue;
            }
            int id = Integer.parseInt(entry.getName().split("\\.")[0]);
            DXT5Texture texture = DXT5TextureStore.readDXT5Texture(in);

            textures.add(new Pair<>(id, texture));
        }

        textures.sort((o1, o2) -> {
            return Integer.compare(o1.getA(), o2.getA());
        });

        List<DXT5Texture> output = new ArrayList<>();
        for (Pair<Integer, DXT5Texture> p : textures) {
            output.add(p.getB());
        }

        return output;
    }

    static {
        try {
            long here = System.currentTimeMillis();
            List<DXT5Texture> waterTextures;
            try (ZipInputStream zipIn = new ZipInputStream(
                    new BufferedInputStream(Water.class.getResourceAsStream("Water.zip")),
                    StandardCharsets.UTF_8
            )) {
                waterTextures = readWaterTextures(zipIn);
            }

            if (waterTextures.isEmpty()) {
                throw new IOException("Water textures zip is empty.");
            }

            int waterTexture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, waterTexture);

            DXT5Texture sample = waterTextures.get(0);
            for (int i = 0; i < sample.mips(); i++) {
                int mipWidth = sample.mipWidth(i);
                int mipHeight = sample.mipHeight(i);
                if (GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
                    glTexImage3D(
                            GL_TEXTURE_2D_ARRAY,
                            i,
                            EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT,
                            mipWidth, mipHeight, waterTextures.size(),
                            0, GL_RGBA, GL_UNSIGNED_BYTE, 0
                    );
                } else {
                    glTexImage3D(
                            GL_TEXTURE_2D_ARRAY,
                            i,
                            GL_RGBA8,
                            mipWidth, mipHeight, waterTextures.size(),
                            0, GL_RGBA, GL_UNSIGNED_BYTE, 0
                    );
                }
            }

            for (int i = 0; i < waterTextures.size(); i++) {
                DXT5Texture texture = waterTextures.get(i);
                if (GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
                    for (int j = 0; j < texture.mips(); j++) {
                        glCompressedTexSubImage3D(
                                GL_TEXTURE_2D_ARRAY, j,
                                0, 0, i,
                                texture.mipWidth(j), texture.mipHeight(j), 1,
                                EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT,
                                texture.mipSlice(j)
                        );
                    }
                } else {
                    byte[] decompressed = texture.decompress();
                    ByteBuffer buffer = BufferUtils
                            .createByteBuffer(decompressed.length)
                            .put(decompressed)
                            .flip();
                    glTexSubImage3D(
                            GL_TEXTURE_2D_ARRAY, 0,
                            0, 0, i,
                            texture.width(), texture.height(), 1,
                            GL_RGBA,
                            GL_UNSIGNED_BYTE,
                            buffer
                    );
                }
                texture.free();
            }

            if (!GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
                glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
            }

            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_REPEAT);

            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                glTexParameterf(
                        GL_TEXTURE_2D_ARRAY,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
                );
            }

            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

            if (GL.getCapabilities().GL_KHR_debug) {
                KHRDebug.glObjectLabel(GL_TEXTURE, waterTexture,
                        StringUtils.truncateStringTo255Bytes("Water Normal Maps")
                );
            }
            
            TEXTURE = waterTexture;
            long time = System.currentTimeMillis() - here;

            System.out.println("Loaded Water Textures, " + time + "ms, " + waterTextures.size() + " frames.");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Water() {

    }

}
