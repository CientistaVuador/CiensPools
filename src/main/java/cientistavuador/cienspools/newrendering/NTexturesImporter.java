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

import cientistavuador.cienspools.util.ColorUtils;
import cientistavuador.cienspools.util.DXT5TextureStore;
import cientistavuador.cienspools.util.DXT5TextureStore.DXT5Texture;
import cientistavuador.cienspools.util.M8Image;
import cientistavuador.cienspools.util.RGBA8Image;
import java.util.Objects;
import org.joml.Vector4f;

/**
 *
 * @author Cien
 */
public class NTexturesImporter {

    public static NBlendingMode findBlendingMode(RGBA8Image image) {
        Objects.requireNonNull(image, "Image is null");
        NBlendingMode mode = NBlendingMode.OPAQUE;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.sample(x, y, 3);
                if (alpha != 255 && mode.equals(NBlendingMode.OPAQUE)) {
                    mode = NBlendingMode.ALPHA_TESTING;
                }
                if (alpha != 0 && alpha != 255 && mode.equals(NBlendingMode.ALPHA_TESTING)) {
                    mode = NBlendingMode.ALPHA_BLENDING;
                    return mode;
                }
            }
        }
        return mode;
    }
    
    public static DXT5Texture create_cr_cg_cb_ca(RGBA8Image image, NBlendingMode mode) {
        if (mode.equals(NBlendingMode.OPAQUE)) {
            image = image.copy();
            M8Image.rgbaToM8(image.getRGBA(), image.getWidth(), image.getHeight());
        }
        return DXT5TextureStore.createDXT5Texture(image.getRGBA(), image.getWidth(), image.getHeight());
    }
    
    public static DXT5Texture create_ht_rg_mt_nx(
            int defaultWidth, int defaultHeight,
            RGBA8Image height, RGBA8Image roughness, RGBA8Image metallic, RGBA8Image normalMap
    ) {
        RGBA8Image compacted = RGBA8Image
                .ofSameSize(defaultWidth, defaultHeight, height, roughness, metallic, normalMap);
        compacted.fill(255, 255, 255, 127);
        if (height != null) {
            compacted.copyChannelOf(height, 0, 0);
        }
        if (roughness != null) {
            compacted.copyChannelOf(roughness, 0, 1);
        }
        if (metallic != null) {
            compacted.copyChannelOf(metallic, 0, 2);
        }
        if (normalMap != null) {
            compacted.copyChannelOf(normalMap, 0, 3);
        }
        return DXT5TextureStore
                .createDXT5Texture(compacted.getRGBA(), compacted.getWidth(), compacted.getHeight());
    }
    
    public static DXT5Texture create_em_ao_wt_ny(
            int defaultWidth, int defaultHeight,
            RGBA8Image emissive, RGBA8Image ambientOcclusion, RGBA8Image water, RGBA8Image normalMap
    ) {
        RGBA8Image compacted = RGBA8Image
                .ofSameSize(defaultWidth, defaultHeight, ambientOcclusion, emissive, water, normalMap);
        compacted.fill(255, 255, 255, 127);
        if (emissive != null) {
            compacted.copyChannelOf(emissive, 0, 0);
        }
        if (ambientOcclusion != null) {
            compacted.copyChannelOf(ambientOcclusion, 0, 1);
        }
        if (water != null) {
            compacted.copyChannelOf(water, 0, 2);
        }
        if (normalMap != null) {
            compacted.copyChannelOf(normalMap, 1, 3);
        }
        return DXT5TextureStore
                .createDXT5Texture(compacted.getRGBA(), compacted.getWidth(), compacted.getHeight());
    }
    
    public static void bakeEmissiveIntoColor(RGBA8Image color, RGBA8Image emissive) {
        Objects.requireNonNull(color, "color is null.");
        Objects.requireNonNull(emissive, "emissive is null.");
        RGBA8Image.validateSize(emissive, color.getWidth(), color.getHeight());
        
        Vector4f c = new Vector4f();
        Vector4f e = new Vector4f();
        for (int y = 0; y < color.getHeight(); y++) {
            for (int x = 0; x < color.getWidth(); x++) {
                color.sample(x, y, c);
                emissive.sample(x, y, e);
                
                ColorUtils.mergeEmissiveWithColor(c, e);
                
                color.write(x, y, c);
                emissive.write(x, y, e);
            }
        }
    }
    
    public static NTextures create(
            boolean useAlphaTestingHint,
            String name,
            RGBA8Image color,
            RGBA8Image normalMap,
            RGBA8Image height, RGBA8Image roughness, RGBA8Image metallic,
            RGBA8Image emissive, RGBA8Image ambientOcclusion, RGBA8Image water
    ) {
        Objects.requireNonNull(color, "Color is null.");
        int defaultWidth = color.getWidth();
        int defaultHeight = color.getHeight();
        NBlendingMode optimalBlendingMode = findBlendingMode(color);
        if (useAlphaTestingHint 
                && optimalBlendingMode.equals(NBlendingMode.ALPHA_BLENDING)) {
            optimalBlendingMode = NBlendingMode.ALPHA_TESTING;
        }
        DXT5Texture cr_cg_cb_ca = create_cr_cg_cb_ca(color, optimalBlendingMode);
        DXT5Texture ht_rg_mt_nx = create_ht_rg_mt_nx(defaultWidth, defaultHeight,
                height, roughness, metallic, normalMap);
        DXT5Texture em_ao_wt_ny = create_em_ao_wt_ny(defaultWidth, defaultHeight,
                emissive, ambientOcclusion, water, normalMap);
        return new NTextures(name, optimalBlendingMode, cr_cg_cb_ca, ht_rg_mt_nx, em_ao_wt_ny);
    }
    
    private NTexturesImporter() {

    }

}
