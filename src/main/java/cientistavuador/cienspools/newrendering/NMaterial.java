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

import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.resourcepack.ResourcePackWriter.ResourceEntry;
import cientistavuador.cienspools.resourcepack.ResourceRW;
import cientistavuador.cienspools.util.ColorUtils;
import java.io.IOException;
import java.util.Map;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 *
 * @author Cien
 */
public class NMaterial {
    
    public static final NMaterial NULL_MATERIAL;

    static {
        NULL_MATERIAL = new NMaterial("Error/Null Material", NTextures.NULL_TEXTURE);
    }

    public static final NMaterial MIRROR;

    static {
        MIRROR = new NMaterial("Mirror", NTextures.BLANK_TEXTURE);
        MIRROR.setMetallic(1f);
        MIRROR.setRoughness(0f);
    }
    public static final NMaterial DARK_GLASS;

    static {
        DARK_GLASS = new NMaterial("Dark Glass", NTextures.BLANK_TEXTURE);
        DARK_GLASS.getColor().set(0.0075f, 0.0075f, 0.0075f, 0.7f);
        DARK_GLASS.setMetallic(0f);
        DARK_GLASS.setRoughness(0f);
    }
    public static final NMaterial WATER;

    static {
        WATER = new NMaterial("Water", NTextures.BLANK_TEXTURE);
        WATER.getColor().set(0.005f, 0.005f, 0.0075f, 0.50f);
        WATER.setMetallic(0f);
        WATER.setRoughness(0f);
        WATER.setRefraction(1f / 1.33f);
        WATER.setWater(1f);
    }
    public static final NMaterial ORANGE_JUICE;

    static {
        ORANGE_JUICE = new NMaterial("Orange Juice", NTextures.BLANK_TEXTURE);
        ColorUtils.setSRGBA(ORANGE_JUICE.getColor(), 255, 92, 0, 255);
        ORANGE_JUICE.setMetallic(0f);
        ORANGE_JUICE.setRoughness(0.7f);
        ORANGE_JUICE.setWater(0.5f);
    }
    public static final NMaterial GLASS;

    static {
        GLASS = new NMaterial("Glass", NTextures.BLANK_TEXTURE);
        GLASS.getColor().set(0.94f, 0.95f, 0.96f, 0.25f);
        GLASS.setRoughness(0f);
        GLASS.setMetallic(1f);
        GLASS.setRefraction(1f / 1.53f);
    }
    public static final NMaterial ROUGH_BLACK;

    static {
        ROUGH_BLACK = new NMaterial("Rough Black", NTextures.BLANK_TEXTURE);
        ROUGH_BLACK.getColor().set(0f, 0f, 0f, 1f);
        ROUGH_BLACK.setMetallic(0f);
        ROUGH_BLACK.setRoughness(1f);
    }
    public static final NMaterial SHINY_BLACK;

    static {
        SHINY_BLACK = new NMaterial("Shiny Black", NTextures.BLANK_TEXTURE);
        SHINY_BLACK.getColor().set(0f, 0f, 0f, 1f);
        SHINY_BLACK.setMetallic(0f);
        SHINY_BLACK.setRoughness(0f);
    }
    public static final NMaterial GOLD;

    static {
        GOLD = new NMaterial("Gold", NTextures.BLANK_TEXTURE);
        ColorUtils.setSRGBA(GOLD.getColor(), 255, 215, 0, 255);
        GOLD.setMetallic(1f);
        GOLD.setRoughness(0.6f);
    }

    public static final ResourceRW<NMaterial> RESOURCES = new ResourceRW<NMaterial>(true) {
        @Override
        public String getResourceType() {
            return "material";
        }
        
        private float readString(String s, float defaultValue) {
            if (s == null) {
                return defaultValue;
            }
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        
        @Override
        public NMaterial readResource(Resource r) throws IOException {
            if (r == null) {
                return NMaterial.NULL_MATERIAL;
            }
            
            NMaterial material = new NMaterial(r.getId());
            
            Map<String, String> meta = r.getMeta();
            
            material.getColor().set(
                    readString(meta.get("color.r"), material.getColor().x()),
                    readString(meta.get("color.g"), material.getColor().y()),
                    readString(meta.get("color.b"), material.getColor().z()),
                    readString(meta.get("color.a"), material.getColor().w())
            );
            
            material.setMetallic(readString(meta.get("metallic"), material.getMetallic()));
            material.setRoughness(readString(meta.get("roughness"), material.getRoughness()));
            
            material.setInverseRoughnessExponent(
                    readString(meta.get("inverseRoughnessExponent"), material.getInverseRoughnessExponent())
            );
            material.setDiffuseSpecularRatio(
                    readString(meta.get("diffuseSpecularRatio"), material.getDiffuseSpecularRatio())
            );
            
            material.setHeight(
                    readString(meta.get("height"), material.getHeight()));
            material.setHeightMinLayers(
                    readString(meta.get("heightMinLayers"), material.getHeightMinLayers()));
            material.setHeightMaxLayers(
                    readString(meta.get("heightMaxLayers"), material.getHeightMaxLayers()));
            
            material.setEmissive(
                    readString(meta.get("emissive"), material.getEmissive()));
            material.setWater(
                    readString(meta.get("water"), material.getWater()));
            material.setRefraction(
                    readString(meta.get("refraction"), material.getRefraction()));
            material.setRefractionPower(
                    readString(meta.get("refractionPower"), material.getRefractionPower()));
            
            material.setFresnelOutline(
                    readString(meta.get("fresnelOutline"), material.getFresnelOutline()));
            material.getFresnelOutlineColor().set(
                    readString(meta.get("fresnelOutlineColor.r"), material.getFresnelOutlineColor().x()),
                    readString(meta.get("fresnelOutlineColor.g"), material.getFresnelOutlineColor().y()),
                    readString(meta.get("fresnelOutlineColor.b"), material.getFresnelOutlineColor().z())
            );
            
            material.setTextures(NTextures.RESOURCES.get(meta.get("texture")));
            
            return material;
        }

        @Override
        public void writeResource(NMaterial obj, ResourceEntry entry, String path) throws IOException {
            entry.setType(getResourceType());
            entry.setId(obj.getId());

            Map<String, String> meta = entry.getMeta();
            
            meta.put("color.r", Float.toString(obj.getColor().x()));
            meta.put("color.g", Float.toString(obj.getColor().y()));
            meta.put("color.b", Float.toString(obj.getColor().z()));
            meta.put("color.a", Float.toString(obj.getColor().w()));

            meta.put("metallic", Float.toString(obj.getMetallic()));
            meta.put("roughness", Float.toString(obj.getRoughness()));

            meta.put("inverseRoughnessExponent", Float.toString(obj.getInverseRoughnessExponent()));
            meta.put("diffuseSpecularRatio", Float.toString(obj.getDiffuseSpecularRatio()));

            meta.put("height", Float.toString(obj.getHeight()));
            meta.put("heightMinLayers", Float.toString(obj.getHeightMinLayers()));
            meta.put("heightMaxLayers", Float.toString(obj.getHeightMaxLayers()));

            meta.put("emissive", Float.toString(obj.getEmissive()));
            meta.put("water", Float.toString(obj.getWater()));
            meta.put("refraction", Float.toString(obj.getRefraction()));
            meta.put("refractionPower", Float.toString(obj.getRefractionPower()));

            meta.put("fresnelOutline", Float.toString(obj.getFresnelOutline()));
            meta.put("fresnelOutlineColor.r", Float.toString(obj.getFresnelOutlineColor().x()));
            meta.put("fresnelOutlineColor.g", Float.toString(obj.getFresnelOutlineColor().y()));
            meta.put("fresnelOutlineColor.b", Float.toString(obj.getFresnelOutlineColor().z()));
            
            meta.put("texture", obj.getTextures().getName());
        }
    };

    private final String id;

    private final Vector4f color = new Vector4f(1f, 1f, 1f, 1f);

    private float metallic = 0f;
    private float roughness = 1f;

    private float inverseRoughnessExponent = 2f;
    private float diffuseSpecularRatio = 0.5f;

    private float height = 0f;
    private float heightMinLayers = 8f;
    private float heightMaxLayers = 32f;

    private float emissive = 0f;
    private float water = 0f;
    private float refraction = 0f;
    private float refractionPower = 0.25f;

    private float ambientOcclusion = 1f;

    private float fresnelOutline = 0f;
    private final Vector3f fresnelOutlineColor = new Vector3f(0f, 1f, 0f);

    private NTextures textures = NTextures.NULL_TEXTURE;
    
    public NMaterial(String id) {
        this(id, null);
    }

    public NMaterial(String id, NTextures textures) {
        if (id == null) {
            id = Resource.generateRandomId("Unnamed Material");
        }
        this.id = id;
        if (textures == null) {
            textures = NTextures.NULL_TEXTURE;
        }
        this.textures = textures;
    }

    public String getId() {
        return id;
    }

    public Vector4f getColor() {
        return color;
    }

    public float getMetallic() {
        return metallic;
    }

    public void setMetallic(float metallic) {
        this.metallic = metallic;
    }

    public float getRoughness() {
        return roughness;
    }

    public void setRoughness(float roughness) {
        this.roughness = roughness;
    }

    public float getInverseRoughnessExponent() {
        return inverseRoughnessExponent;
    }

    public void setInverseRoughnessExponent(float inverseRoughnessExponent) {
        this.inverseRoughnessExponent = inverseRoughnessExponent;
    }

    public float getDiffuseSpecularRatio() {
        return diffuseSpecularRatio;
    }

    public void setDiffuseSpecularRatio(float diffuseSpecularRatio) {
        this.diffuseSpecularRatio = diffuseSpecularRatio;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getHeightMinLayers() {
        return heightMinLayers;
    }

    public void setHeightMinLayers(float heightMinLayers) {
        this.heightMinLayers = heightMinLayers;
    }

    public float getHeightMaxLayers() {
        return heightMaxLayers;
    }

    public void setHeightMaxLayers(float heightMaxLayers) {
        this.heightMaxLayers = heightMaxLayers;
    }

    public float getEmissive() {
        return emissive;
    }

    public void setEmissive(float emissive) {
        this.emissive = emissive;
    }

    public float getWater() {
        return water;
    }

    public void setWater(float water) {
        this.water = water;
    }

    public float getRefraction() {
        return refraction;
    }

    public void setRefraction(float refraction) {
        this.refraction = refraction;
    }

    public float getRefractionPower() {
        return refractionPower;
    }

    public void setRefractionPower(float refractionPower) {
        this.refractionPower = refractionPower;
    }

    public float getAmbientOcclusion() {
        return ambientOcclusion;
    }

    public void setAmbientOcclusion(float ambientOcclusion) {
        this.ambientOcclusion = ambientOcclusion;
    }

    public float getFresnelOutline() {
        return fresnelOutline;
    }

    public void setFresnelOutline(float fresnelOutline) {
        this.fresnelOutline = fresnelOutline;
    }

    public Vector3f getFresnelOutlineColor() {
        return fresnelOutlineColor;
    }

    public NTextures getTextures() {
        return textures;
    }

    public void setTextures(NTextures textures) {
        if (textures == null) {
            textures = NTextures.NULL_TEXTURE;
        }
        this.textures = textures;
    }
    
    public NBlendingMode getBlendingMode() {
        NBlendingMode mode = this.textures.getBlendingMode();
        float materialAlpha = this.color.w();

        if (materialAlpha != 1f && NBlendingMode.OPAQUE.equals(mode)) {
            mode = NBlendingMode.ALPHA_BLENDING;
        }

        return mode;
    }

    public boolean isInvisible() {
        return this.color.w() <= 0f;
    }
    
}
