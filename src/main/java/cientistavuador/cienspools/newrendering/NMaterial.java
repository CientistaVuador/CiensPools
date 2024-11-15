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
import java.io.IOException;
import java.util.Map;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public class NMaterial {

    public static final Vector4fc DEFAULT_COLOR = new Vector4f(1f, 1f, 1f, 1f);

    public static final float DEFAULT_METALLIC = 1f;
    public static final float DEFAULT_ROUGHNESS = 1f;
    
    public static final float DEFAULT_DIFFUSE_SPECULAR_RATIO = 0.5f;

    public static final float DEFAULT_HEIGHT = 0f;
    public static final float DEFAULT_HEIGHT_MIN_LAYERS = 8f;
    public static final float DEFAULT_HEIGHT_MAX_LAYERS = 32f;

    public static final float DEFAULT_EMISSIVE = 0f;
    public static final float DEFAULT_WATER = 0f;
    public static final float DEFAULT_REFRACTION = 0f;
    public static final float DEFAULT_REFRACTION_POWER = 0.25f;

    public static final float DEFAULT_AMBIENT_OCCLUSION = 1f;

    public static final float DEFAULT_FRESNEL_OUTLINE = 0f;
    public static final Vector3fc DEFAULT_FRESNEL_OUTLINE_COLOR = new Vector3f(0f, 1f, 0f);
    
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
                return NMaterial.ERROR_MATERIAL;
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

            if (!obj.getColor().equals(DEFAULT_COLOR)) {
                meta.put("color.r", Float.toString(obj.getColor().x()));
                meta.put("color.g", Float.toString(obj.getColor().y()));
                meta.put("color.b", Float.toString(obj.getColor().z()));
                meta.put("color.a", Float.toString(obj.getColor().w()));
            }

            if (obj.getMetallic() != DEFAULT_METALLIC) {
                meta.put("metallic", Float.toString(obj.getMetallic()));
            }
            if (obj.getRoughness() != DEFAULT_ROUGHNESS) {
                meta.put("roughness", Float.toString(obj.getRoughness()));
            }
            
            if (obj.getDiffuseSpecularRatio() != DEFAULT_DIFFUSE_SPECULAR_RATIO) {
                meta.put("diffuseSpecularRatio", Float.toString(obj.getDiffuseSpecularRatio()));
            }

            if (obj.getHeight() != DEFAULT_HEIGHT) {
                meta.put("height", Float.toString(obj.getHeight()));
            }
            if (obj.getHeightMinLayers() != DEFAULT_HEIGHT_MIN_LAYERS) {
                meta.put("heightMinLayers", Float.toString(obj.getHeightMinLayers()));
            }
            if (obj.getHeightMaxLayers() != DEFAULT_HEIGHT_MAX_LAYERS) {
                meta.put("heightMaxLayers", Float.toString(obj.getHeightMaxLayers()));
            }

            if (obj.getEmissive() != DEFAULT_EMISSIVE) {
                meta.put("emissive", Float.toString(obj.getEmissive()));
            }
            if (obj.getWater() != DEFAULT_WATER) {
                meta.put("water", Float.toString(obj.getWater()));
            }
            if (obj.getRefraction() != DEFAULT_REFRACTION) {
                meta.put("refraction", Float.toString(obj.getRefraction()));
            }
            if (obj.getRefractionPower() != DEFAULT_REFRACTION_POWER) {
                meta.put("refractionPower", Float.toString(obj.getRefractionPower()));
            }

            if (obj.getFresnelOutline() != DEFAULT_FRESNEL_OUTLINE) {
                meta.put("fresnelOutline", Float.toString(obj.getFresnelOutline()));
            }
            if (!obj.getFresnelOutlineColor().equals(DEFAULT_FRESNEL_OUTLINE_COLOR)) {
                meta.put("fresnelOutlineColor.r", Float.toString(obj.getFresnelOutlineColor().x()));
                meta.put("fresnelOutlineColor.g", Float.toString(obj.getFresnelOutlineColor().y()));
                meta.put("fresnelOutlineColor.b", Float.toString(obj.getFresnelOutlineColor().z()));
            }
            
            meta.put("texture", obj.getTextures().getId());
        }
    };
    
    private static final NMaterial ERROR_MATERIAL_FALLBACK;

    static {
        ERROR_MATERIAL_FALLBACK = new NMaterial("Error Material", NTextures.ERROR_TEXTURE);
        ERROR_MATERIAL_FALLBACK.setMetallic(0f);
    }
    
    public static final NMaterial ERROR_MATERIAL;
    
    static {
        NMaterial error;
        if (Resource.get(RESOURCES.getResourceType(), ERROR_MATERIAL_FALLBACK.getId()) != null) {
            error = RESOURCES.get(ERROR_MATERIAL_FALLBACK.getId());
        } else {
            error = ERROR_MATERIAL_FALLBACK;
        }
        ERROR_MATERIAL = error;
    }
    
    private final String id;

    private final Vector4f color = new Vector4f(DEFAULT_COLOR);

    private float metallic = DEFAULT_METALLIC;
    private float roughness = DEFAULT_ROUGHNESS;
    
    private float diffuseSpecularRatio = DEFAULT_DIFFUSE_SPECULAR_RATIO;

    private float height = DEFAULT_HEIGHT;
    private float heightMinLayers = DEFAULT_HEIGHT_MIN_LAYERS;
    private float heightMaxLayers = DEFAULT_HEIGHT_MAX_LAYERS;

    private float emissive = DEFAULT_EMISSIVE;
    private float water = DEFAULT_WATER;
    private float refraction = DEFAULT_REFRACTION;
    private float refractionPower = DEFAULT_REFRACTION_POWER;

    private float ambientOcclusion = DEFAULT_AMBIENT_OCCLUSION;

    private float fresnelOutline = DEFAULT_FRESNEL_OUTLINE;
    private final Vector3f fresnelOutlineColor = new Vector3f(DEFAULT_FRESNEL_OUTLINE_COLOR);

    private NTextures textures = NTextures.ERROR_TEXTURE;

    public NMaterial(String id) {
        this(id, null);
    }

    public NMaterial(String id, NTextures textures) {
        if (id == null) {
            id = Resource.generateRandomId("Unnamed Material");
        }
        this.id = id;
        if (textures == null) {
            textures = NTextures.ERROR_TEXTURE;
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
            textures = NTextures.ERROR_TEXTURE;
        }
        this.textures = textures;
    }

    public NBlendingMode getBlendingMode() {
        NBlendingMode mode = this.textures.getBlendingMode();
        float materialAlpha = this.color.w();

        if (materialAlpha != 1f && NBlendingMode.OPAQUE.equals(mode)) {
            mode = NBlendingMode.ALPHA_BLENDING;
        }
        if (this.refraction != 0f) {
            mode = NBlendingMode.OPAQUE;
        }

        return mode;
    }

    public boolean isInvisible() {
        return this.color.w() <= 0f && this.refraction == 0f;
    }

}
