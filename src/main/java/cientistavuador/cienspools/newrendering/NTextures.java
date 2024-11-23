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
package cientistavuador.cienspools.newrendering;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.resourcepack.ResourcePackWriter.DataEntry;
import cientistavuador.cienspools.resourcepack.ResourcePackWriter.ResourceEntry;
import cientistavuador.cienspools.resourcepack.ResourceRW;
import cientistavuador.cienspools.util.DXT5TextureStore;
import cientistavuador.cienspools.util.DXT5TextureStore.DXT5Texture;
import cientistavuador.cienspools.util.M8Image;
import cientistavuador.cienspools.util.MipmapUtils;
import cientistavuador.cienspools.util.ObjectCleaner;
import cientistavuador.cienspools.util.StringUtils;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.KHRDebug;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL42C;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public class NTextures {

    public static final String ERROR_CR_CG_CB_CA_DATA = "KLUv/aDwVQEAvQMAgoYSGcBrDn5V/R//n//xyqWOjLN/YHhkdo1dOwXvvZcpmZK4xA1DFP3+37b33tMWbfm3rYMUG+pyF84MpigLeumxqsYHBj4Q4hgzORcNAF2Bn9cf+CT/z+TrgT4AqEN+UAAsaD9fVFB+qAoEAAcQEkicKw7YrQM=";
    public static final String ERROR_HT_RG_MT_NX_DATA = "KLUv/aDwVQEAVQIAcsQNFdBdAwAADZgIQJSUiqMtKueISDeQFNZae++9/1/r6pATnOsXPzo8hLL9Oybj5cEP3pzxU/TWCgUAuqpm5gsgJJA4V4x4Sxg=";
    public static final String ERROR_EM_AO_WT_NY_DATA = "KLUv/aDwVQEAVQIAcsQNFdBdAwAADZgIQJSUiqMtKueISDeQFNZae++9/1/r6pATnOsXPzo8hLL9Oybj5cEP3pzxU/TWCgUAuqpm5gsgJJA4V4x4Sxg=";

    private static final NTextures ERROR_TEXTURE_FALLBACK;

    static {
        try {
            DXT5Texture cr_cg_cb_ca = DXT5TextureStore.readDXT5Texture(
                    new ByteArrayInputStream(Base64.getDecoder().decode(ERROR_CR_CG_CB_CA_DATA)));
            DXT5Texture ht_rg_mt_nx = DXT5TextureStore.readDXT5Texture(
                    new ByteArrayInputStream(Base64.getDecoder().decode(ERROR_HT_RG_MT_NX_DATA)));
            DXT5Texture em_ao_wt_ny = DXT5TextureStore.readDXT5Texture(
                    new ByteArrayInputStream(Base64.getDecoder().decode(ERROR_EM_AO_WT_NY_DATA)));

            ERROR_TEXTURE_FALLBACK = new NTextures(
                    "Error Texture",
                    NBlendingMode.OPAQUE,
                    cr_cg_cb_ca, ht_rg_mt_nx, em_ao_wt_ny
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static final String BLANK_CR_CG_CB_CA_DATA = "KLUv/SCwPQIAogQNF8CnNf//b/0v/+sjvUa5JOv/b6RaNhcp19x/l0DBYS3q9vS8lmL8USjJ80e3WwEBZxKiVAYgwOMB0whwgQMhJU6ROQE=";
    public static final String BLANK_HT_RG_MT_NX_DATA = "KLUv/SCwTQIAooQNFtBnDAAApbD9NBIi0vhjX9EJ1EqzSgFRzv+PcbPiwt759SeDZ8ylf4VcG/z/++uvjw0/JOucBiDA4wHTCHCBAyElTpE5AQ==";
    public static final String BLANK_EM_AO_WT_NY_DATA = "KLUv/SCwTQIAooQNFtBnDAAApbD9NBIi0vhjX9EJ1EqzSgFRzv+PcbPiwt759SeDZ8ylf4VcG/z/++uvjw0/JOucBiDA4wHTCHCBAyElTpE5AQ==";

    private static final NTextures BLANK_TEXTURE_FALLBACK;
    
    static {
        try {
            DXT5Texture cr_cg_cb_ca = DXT5TextureStore.readDXT5Texture(
                    new ByteArrayInputStream(Base64.getDecoder().decode(BLANK_CR_CG_CB_CA_DATA)));
            DXT5Texture ht_rg_mt_nx = DXT5TextureStore.readDXT5Texture(
                    new ByteArrayInputStream(Base64.getDecoder().decode(BLANK_HT_RG_MT_NX_DATA)));
            DXT5Texture em_ao_wt_ny = DXT5TextureStore.readDXT5Texture(
                    new ByteArrayInputStream(Base64.getDecoder().decode(BLANK_EM_AO_WT_NY_DATA)));

            BLANK_TEXTURE_FALLBACK = new NTextures(
                    "Blank Texture",
                    NBlendingMode.OPAQUE,
                    cr_cg_cb_ca, ht_rg_mt_nx, em_ao_wt_ny
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    public static final ResourceRW<NTextures> RESOURCES = new ResourceRW<NTextures>(true) {
        public static final String CR_CG_CB_CA_FILE_NAME = "cr_cg_cb_ca";
        public static final String HT_RG_MT_NX_FILE_NAME = "ht_rg_mt_nx";
        public static final String EM_AO_WT_NY_FILE_NAME = "em_ao_wt_ny";
        
        @Override
        public String getResourceType() {
            return "texture";
        }

        private DXT5Texture readTexture(Resource r, String type) throws IOException {
            Path p = r.getData().get(type);
            if (p == null) {
                return null;
            }
            try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(p))) {
                return DXT5TextureStore.readDXT5Texture(in);
            }
        }
        
        @Override
        public NTextures readResource(Resource r) throws IOException {
            if (r == null) {
                return NTextures.ERROR_TEXTURE;
            }
            NBlendingMode mode;
            try {
                mode = NBlendingMode.valueOf(r.getMeta().get("blendingMode"));
            } catch (NullPointerException | IllegalArgumentException ex) {
                mode = NBlendingMode.OPAQUE;
            }
            
            DXT5Texture texture_cr_cg_cb_ca = readTexture(r, CR_CG_CB_CA_FILE_NAME);
            DXT5Texture texture_ht_rg_mt_nx = readTexture(r, HT_RG_MT_NX_FILE_NAME);
            DXT5Texture texture_em_ao_wt_ny = readTexture(r, EM_AO_WT_NY_FILE_NAME);
            
            return new NTextures(
                    r.getId(), mode,
                    texture_cr_cg_cb_ca,
                    texture_ht_rg_mt_nx,
                    texture_em_ao_wt_ny
            );
        }

        private void writeTexture(
                ResourceEntry entry, String type, String path, DXT5Texture texture
        ) {
            entry.getData().put(type,
                    new DataEntry(path,
                            new ByteArrayInputStream(
                                    DXT5TextureStore.writeDXT5Texture(texture))));
        }

        @Override
        public void writeResource(NTextures obj, ResourceEntry entry, String path) throws IOException {
            entry.setType(getResourceType());
            entry.setId(obj.getId());
            entry.getMeta().put("blendingMode", obj.getBlendingMode().name());
            if (!path.isEmpty() && !path.endsWith("/")) {
                path += "/";
            }
            writeTexture(entry, CR_CG_CB_CA_FILE_NAME,
                    path + "cr_cg_cb_ca.dds.zst", obj.texture_cr_cg_cb_ca());
            writeTexture(entry, HT_RG_MT_NX_FILE_NAME,
                    path + "ht_rg_mt_nx.dds.zst", obj.texture_ht_rg_mt_nx());
            writeTexture(entry, EM_AO_WT_NY_FILE_NAME,
                    path + "em_ao_wt_ny.dds.zst", obj.texture_em_ao_wt_ny());
        }
        
    };
    
    public static final NTextures ERROR_TEXTURE;
    public static final NTextures BLANK_TEXTURE;
    
    static {
        NTextures error;
        if (Resource.get(RESOURCES.getResourceType(), ERROR_TEXTURE_FALLBACK.getId()) != null) {
            error = RESOURCES.get(ERROR_TEXTURE_FALLBACK.getId());
        } else {
            error = ERROR_TEXTURE_FALLBACK;
        }
        ERROR_TEXTURE = error;
        
        NTextures blank;
        if (Resource.get(RESOURCES.getResourceType(), BLANK_TEXTURE_FALLBACK.getId()) != null) {
            blank = RESOURCES.get(BLANK_TEXTURE_FALLBACK.getId());
        } else {
            blank = BLANK_TEXTURE_FALLBACK;
        }
        BLANK_TEXTURE = blank;
    }
    
    private static class WrappedTextures {

        public int textures = 0;
    }
    
    private final String id;
    private final NBlendingMode blendingMode;
    private final int width;
    private final int height;
    private final DXT5Texture texture_cr_cg_cb_ca;
    private final DXT5Texture texture_ht_rg_mt_nx;
    private final DXT5Texture texture_em_ao_wt_ny;

    private final WrappedTextures wrappedTextures = new WrappedTextures();

    private WeakReference<byte[]> decompressed_cr_cg_cb_ca_ref = null;
    private WeakReference<byte[]> decompressed_ht_rg_mt_nx_ref = null;
    private WeakReference<byte[]> decompressed_em_ao_wt_ny_ref = null;
    
    public NTextures(
            String id,
            NBlendingMode blendingMode,
            DXT5Texture texture_r_g_b_a,
            DXT5Texture texture_ht_rg_mt_nx,
            DXT5Texture texture_er_eg_eb_ny
    ) {
        Objects.requireNonNull(texture_r_g_b_a, "texture_r_g_b_a is null.");
        Objects.requireNonNull(texture_ht_rg_mt_nx, "texture_ht_rg_mt_nx is null.");
        Objects.requireNonNull(texture_er_eg_eb_ny, "texture_er_eg_eb_ny is null.");

        this.width = texture_r_g_b_a.width();
        this.height = texture_r_g_b_a.height();

        if (texture_ht_rg_mt_nx.width() != this.width || texture_ht_rg_mt_nx.height() != this.height) {
            throw new IllegalArgumentException("Textures sizes are different!");
        }

        if (texture_er_eg_eb_ny.width() != this.width || texture_er_eg_eb_ny.height() != this.height) {
            throw new IllegalArgumentException("Textures sizes are different!");
        }

        this.texture_cr_cg_cb_ca = texture_r_g_b_a;
        this.texture_ht_rg_mt_nx = texture_ht_rg_mt_nx;
        this.texture_em_ao_wt_ny = texture_er_eg_eb_ny;

        if (id == null) {
            id = Resource.generateRandomId(null);
        }
        this.id = id;

        if (blendingMode == null) {
            blendingMode = NBlendingMode.OPAQUE;
        }
        this.blendingMode = blendingMode;
        
        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedTextures final_textures = this.wrappedTextures;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                int tex_textures = final_textures.textures;

                if (tex_textures != 0) {
                    glDeleteTextures(tex_textures);
                    final_textures.textures = 0;
                }
            });
        });
    }
    
    public String getId() {
        return id;
    }
    
    public NBlendingMode getBlendingMode() {
        return blendingMode;
    }
    
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public DXT5Texture texture_cr_cg_cb_ca() {
        return texture_cr_cg_cb_ca;
    }

    public DXT5Texture texture_ht_rg_mt_nx() {
        return texture_ht_rg_mt_nx;
    }

    public DXT5Texture texture_em_ao_wt_ny() {
        return texture_em_ao_wt_ny;
    }

    private byte[] getOrNull(WeakReference<byte[]> ref) {
        if (ref != null) {
            byte[] result = ref.get();
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public byte[] data_r_g_b_a() {
        byte[] cached = getOrNull(this.decompressed_cr_cg_cb_ca_ref);
        if (cached != null) {
            return cached;
        }

        byte[] decompressed = texture_cr_cg_cb_ca().decompress();
        if (NBlendingMode.OPAQUE.equals(getBlendingMode())) {
            M8Image.m8ToRGBA(decompressed, this.width, this.height);
        }

        this.decompressed_cr_cg_cb_ca_ref = new WeakReference<>(decompressed);
        return decompressed;
    }

    public byte[] data_ht_rg_mt_nx() {
        byte[] cached = getOrNull(this.decompressed_ht_rg_mt_nx_ref);
        if (cached != null) {
            return cached;
        }

        byte[] decompressed = texture_ht_rg_mt_nx().decompress();
        this.decompressed_ht_rg_mt_nx_ref = new WeakReference<>(decompressed);
        return decompressed;
    }

    public byte[] data_er_eg_eb_ny() {
        byte[] cached = getOrNull(this.decompressed_em_ao_wt_ny_ref);
        if (cached != null) {
            return cached;
        }

        byte[] decompressed = texture_em_ao_wt_ny().decompress();
        this.decompressed_em_ao_wt_ny_ref = new WeakReference<>(decompressed);
        return decompressed;
    }

    private void validateTextures() {
        if (this.wrappedTextures.textures != 0) {
            return;
        }
        
        glActiveTexture(GL_TEXTURE0);

        int textures = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textures);

        int internalFormat = GL_RGBA8;
        if (GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
            internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
        }

        DXT5Texture[] texturesArray = {
            this.texture_cr_cg_cb_ca,
            this.texture_ht_rg_mt_nx,
            this.texture_em_ao_wt_ny
        };

        int mipLevels = MipmapUtils.numberOfMipmaps(getWidth(), getHeight());
        if (Main.isSupported(4, 2)) {
            GL42C.glTexStorage3D(
                    GL_TEXTURE_2D_ARRAY,
                    mipLevels,
                    internalFormat,
                    getWidth(), getHeight(), texturesArray.length
            );
        } else {
            for (int i = 0; i < mipLevels; i++) {
                glTexImage3D(
                        GL_TEXTURE_2D_ARRAY,
                        i,
                        internalFormat,
                        MipmapUtils.mipmapSize(getWidth(), i),
                        MipmapUtils.mipmapSize(getHeight(), i),
                        texturesArray.length,
                        0,
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        (ByteBuffer) null
                );
            }
        }

        for (int i = 0; i < texturesArray.length; i++) {
            DXT5Texture texture = texturesArray[i];
            if (internalFormat == GL_RGBA8) {
                byte[] uncompressed = texture.decompress();

                ByteBuffer data = memAlloc(uncompressed.length).put(uncompressed).flip();
                try {
                    glTexSubImage3D(
                            GL_TEXTURE_2D_ARRAY,
                            0,
                            0,
                            0,
                            i,
                            texture.width(),
                            texture.height(),
                            1,
                            GL_RGBA,
                            GL_UNSIGNED_BYTE,
                            data
                    );
                } finally {
                    memFree(data);
                }
            } else {
                for (int j = 0; j < texture.mips(); j++) {
                    glCompressedTexSubImage3D(
                            GL_TEXTURE_2D_ARRAY,
                            j,
                            0,
                            0,
                            i,
                            texture.mipWidth(j),
                            texture.mipHeight(j),
                            1,
                            internalFormat,
                            texture.mipSlice(j)
                    );
                }
            }
        }

        if (internalFormat == GL_RGBA8) {
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
            KHRDebug.glObjectLabel(GL_TEXTURE, textures,
                    StringUtils.truncateStringTo255Bytes(this.id)
            );
        }

        this.wrappedTextures.textures = textures;
    }

    public int textures() {
        validateTextures();
        return this.wrappedTextures.textures;
    }

    public void manualFree() {
        final WrappedTextures final_textures = this.wrappedTextures;

        int tex_textures = final_textures.textures;

        if (tex_textures != 0) {
            glDeleteTextures(tex_textures);
            final_textures.textures = 0;
        }
    }
    
}
