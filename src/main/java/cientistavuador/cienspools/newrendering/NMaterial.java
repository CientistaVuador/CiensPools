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
import cientistavuador.cienspools.util.ColorUtils;
import java.util.Map;
import java.util.Objects;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public class NMaterial {
    
    public static final Vector4fc DEFAULT_DIFFUSE_COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Vector3fc DEFAULT_SPECULAR_COLOR = new Vector3f(1.0f, 1.0f, 1.0f);
    public static final Vector3fc DEFAULT_EMISSIVE_COLOR = new Vector3f(1.2f, 1.2f, 1.2f);
    public static final Vector3fc DEFAULT_REFLECTION_COLOR = new Vector3f(1.0f, 1.0f, 1.0f);
    public static final float DEFAULT_MIN_EXPONENT = 0.1f;
    public static final float DEFAULT_MAX_EXPONENT = 2048f;
    public static final float DEFAULT_PARALLAX_HEIGHT_COEFFICIENT = 0.065f;
    public static final float DEFAULT_PARALLAX_MIN_LAYERS = 8f;
    public static final float DEFAULT_PARALLAX_MAX_LAYERS = 32f;

    public static final NMaterial NULL_MATERIAL = new NMaterial("NULL_MATERIAL");

    static {
        NULL_MATERIAL.getDiffuseColor().set(1f, 1f, 1f, 1f);
        NULL_MATERIAL.getSpecularColor().set(0f, 0f, 0f);
        NULL_MATERIAL.getReflectionColor().set(0f, 0f, 0f);
        NULL_MATERIAL.setParallaxHeightCoefficient(0f);
    }
    
    public static final NMaterial MIRROR;
    static {
        MIRROR = new NMaterial("Mirror", NTextures.BLANK_TEXTURE);
        MIRROR.setNewMetallic(1f);
        MIRROR.setNewRoughness(0f);
    }
    public static final NMaterial DARK_GLASS;
    static {
        DARK_GLASS = new NMaterial("Dark Glass", NTextures.BLANK_TEXTURE);
        DARK_GLASS.getNewColor().set(0.0075f, 0.0075f, 0.0075f, 0.7f);
        DARK_GLASS.setNewMetallic(0f);
        DARK_GLASS.setNewRoughness(0f);
    }
    public static final NMaterial WATER;
    static {
        WATER = new NMaterial("Water", NTextures.BLANK_TEXTURE);
        WATER.getNewColor().set(0.005f, 0.005f, 0.0075f, 0.50f);
        WATER.setNewMetallic(0f);
        WATER.setNewRoughness(0f);
        WATER.setNewRefraction(1f / 1.33f);
        WATER.setNewWater(1f);
    }
    public static final NMaterial ORANGE_JUICE;
    static {
        ORANGE_JUICE = new NMaterial("Orange Juice", NTextures.BLANK_TEXTURE);
        ColorUtils.setSRGBA(ORANGE_JUICE.getNewColor(), 255, 92, 0, 255);
        ORANGE_JUICE.setNewMetallic(0f);
        ORANGE_JUICE.setNewRoughness(0.7f);
        ORANGE_JUICE.setNewWater(0.5f);
    }
    public static final NMaterial GLASS;
    static {
        GLASS = new NMaterial("Glass", NTextures.BLANK_TEXTURE);
        GLASS.getNewColor().set(0.94f, 0.95f, 0.96f, 0.25f);
        GLASS.setNewRoughness(0f);
        GLASS.setNewMetallic(1f);
        GLASS.setNewRefraction(1f / 1.53f);
    }
    public static final NMaterial ROUGH_BLACK;
    static {
        ROUGH_BLACK = new NMaterial("Rough Black", NTextures.BLANK_TEXTURE);
        ROUGH_BLACK.getNewColor().set(0f, 0f, 0f, 1f);
        ROUGH_BLACK.setNewMetallic(0f);
        ROUGH_BLACK.setNewRoughness(1f);
    }
    public static final NMaterial SHINY_BLACK;
    static {
        SHINY_BLACK = new NMaterial("Shiny Black", NTextures.BLANK_TEXTURE);
        SHINY_BLACK.getNewColor().set(0f, 0f, 0f, 1f);
        SHINY_BLACK.setNewMetallic(0f);
        SHINY_BLACK.setNewRoughness(0f);
    }
    public static final NMaterial GOLD;
    static {
        GOLD = new NMaterial("Gold", NTextures.BLANK_TEXTURE);
        ColorUtils.setSRGBA(GOLD.getNewColor(), 255, 215, 0, 255);
        GOLD.setNewMetallic(1f);
        GOLD.setNewRoughness(0.6f);
    }
    
    public static final String RESOURCE_TYPE = "material";
    
    private Resource associatedResource = null;
    
    private final String name;
    
    private final Vector4f newColor = new Vector4f(1f, 1f, 1f, 1f);
    
    private float newMetallic = 0f;
    private float newRoughness = 1f;
    
    private float newInverseRoughnessExponent = 2f;
    private float newDiffuseSpecularRatio = 0.5f;
    
    private float newHeight = 0f;
    private float newHeightMinLayers = 8f;
    private float newHeightMaxLayers = 32f;
    
    private float newEmissive = 0f;
    private float newWater = 0f;
    private float newRefraction = 0f;
    private float newRefractionPower = 0.25f;
    
    private float newAmbientOcclusion = 1f;
    
    private float newFresnelOutline = 0f;
    private final Vector3f newFresnelOutlineColor = new Vector3f(0f, 1f, 0f);
    
    private NTextures textures = NTextures.NULL_TEXTURE;

    private final Vector4f diffuseColor = new Vector4f(DEFAULT_DIFFUSE_COLOR);
    private final Vector3f specularColor = new Vector3f(DEFAULT_SPECULAR_COLOR);
    private final Vector3f emissiveColor = new Vector3f(DEFAULT_EMISSIVE_COLOR);
    private final Vector3f reflectionColor = new Vector3f(DEFAULT_REFLECTION_COLOR);

    private float minExponent = DEFAULT_MIN_EXPONENT;
    private float maxExponent = DEFAULT_MAX_EXPONENT;
    private float parallaxHeightCoefficient = DEFAULT_PARALLAX_HEIGHT_COEFFICIENT;
    private float parallaxMinLayers = DEFAULT_PARALLAX_MIN_LAYERS;
    private float parallaxMaxLayers = DEFAULT_PARALLAX_MAX_LAYERS;

    public NMaterial(String name) {
        this(name, null);
    }

    public NMaterial(String name, NTextures textures) {
        this.name = name;
        if (textures == null) {
            textures = NTextures.NULL_TEXTURE;
        }
        this.textures = textures;
    }

    public Resource getAssociatedResource() {
        return associatedResource;
    }

    public void setAssociatedResource(Resource associatedResource) {
        this.associatedResource = associatedResource;
    }
    
    public void writeResourceEntry(ResourceEntry entry) {
        entry.setType(RESOURCE_TYPE);
        
        Map<String, String> meta = entry.getMeta();
        
        meta.put("name", getName());
        
        meta.put("color.r", Float.toString(getNewColor().x()));
        meta.put("color.g", Float.toString(getNewColor().y()));
        meta.put("color.b", Float.toString(getNewColor().z()));
        meta.put("color.a", Float.toString(getNewColor().w()));
        
        meta.put("metallic", Float.toString(getNewMetallic()));
        meta.put("roughness", Float.toString(getNewRoughness()));
        
        meta.put("inverseRoughnessExponent", Float.toString(getNewInverseRoughnessExponent()));
        meta.put("diffuseSpecularRatio", Float.toString(getNewDiffuseSpecularRatio()));
        
        meta.put("height", Float.toString(getNewHeight()));
        meta.put("heightMinLayers", Float.toString(getNewHeightMinLayers()));
        meta.put("heightMaxLayers", Float.toString(getNewHeightMaxLayers()));
        
        meta.put("emissive", Float.toString(getNewEmissive()));
        meta.put("water", Float.toString(getNewWater()));
        meta.put("refraction", Float.toString(getNewRefraction()));
        meta.put("refractionPower", Float.toString(getNewRefractionPower()));
        
        meta.put("fresnelOutline", Float.toString(getNewFresnelOutline()));
        meta.put("fresnelOutlineColor.r", Float.toString(getNewFresnelOutlineColor().x()));
        meta.put("fresnelOutlineColor.g", Float.toString(getNewFresnelOutlineColor().y()));
        meta.put("fresnelOutlineColor.b", Float.toString(getNewFresnelOutlineColor().z()));
        
        Resource resourceTexture = getTextures().getAssociatedResource();
        if (resourceTexture == null) {
            meta.remove("texture");
        } else {
            meta.put("texture", resourceTexture.getId());
        }
    }

    public String getName() {
        return name;
    }

    public Vector4f getNewColor() {
        return newColor;
    }

    public float getNewMetallic() {
        return newMetallic;
    }

    public void setNewMetallic(float newMetallic) {
        this.newMetallic = newMetallic;
    }
    
    public float getNewRoughness() {
        return newRoughness;
    }

    public void setNewRoughness(float newRoughness) {
        this.newRoughness = newRoughness;
    }

    public float getNewInverseRoughnessExponent() {
        return newInverseRoughnessExponent;
    }

    public void setNewInverseRoughnessExponent(float newInverseRoughnessExponent) {
        this.newInverseRoughnessExponent = newInverseRoughnessExponent;
    }

    public float getNewDiffuseSpecularRatio() {
        return newDiffuseSpecularRatio;
    }

    public void setNewDiffuseSpecularRatio(float newDiffuseSpecularRatio) {
        this.newDiffuseSpecularRatio = newDiffuseSpecularRatio;
    }

    public float getNewHeight() {
        return newHeight;
    }

    public void setNewHeight(float newHeight) {
        this.newHeight = newHeight;
    }

    public float getNewHeightMinLayers() {
        return newHeightMinLayers;
    }

    public void setNewHeightMinLayers(float newHeightMinLayers) {
        this.newHeightMinLayers = newHeightMinLayers;
    }

    public float getNewHeightMaxLayers() {
        return newHeightMaxLayers;
    }

    public void setNewHeightMaxLayers(float newHeightMaxLayers) {
        this.newHeightMaxLayers = newHeightMaxLayers;
    }

    public float getNewEmissive() {
        return newEmissive;
    }

    public void setNewEmissive(float newEmissive) {
        this.newEmissive = newEmissive;
    }

    public float getNewWater() {
        return newWater;
    }

    public void setNewWater(float newWater) {
        this.newWater = newWater;
    }

    public float getNewRefraction() {
        return newRefraction;
    }

    public void setNewRefraction(float newRefraction) {
        this.newRefraction = newRefraction;
    }

    public float getNewRefractionPower() {
        return newRefractionPower;
    }
    
    public void setNewRefractionPower(float newRefractionPower) {
        this.newRefractionPower = newRefractionPower;
    }

    public float getNewAmbientOcclusion() {
        return newAmbientOcclusion;
    }

    public void setNewAmbientOcclusion(float newAmbientOcclusion) {
        this.newAmbientOcclusion = newAmbientOcclusion;
    }
    
    public float getNewFresnelOutline() {
        return newFresnelOutline;
    }

    public void setNewFresnelOutline(float newFresnelOutline) {
        this.newFresnelOutline = newFresnelOutline;
    }
    
    public Vector3f getNewFresnelOutlineColor() {
        return newFresnelOutlineColor;
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
    
    public Vector4f getDiffuseColor() {
        return diffuseColor;
    }

    public Vector3f getSpecularColor() {
        return specularColor;
    }

    public Vector3f getEmissiveColor() {
        return emissiveColor;
    }

    public Vector3f getReflectionColor() {
        return reflectionColor;
    }

    public float getMinExponent() {
        return minExponent;
    }

    public void setMinExponent(float minExponent) {
        this.minExponent = minExponent;
    }

    public float getMaxExponent() {
        return maxExponent;
    }

    public void setMaxExponent(float maxExponent) {
        this.maxExponent = maxExponent;
    }

    public float getParallaxHeightCoefficient() {
        return parallaxHeightCoefficient;
    }

    public void setParallaxHeightCoefficient(float parallaxHeightCoefficient) {
        this.parallaxHeightCoefficient = parallaxHeightCoefficient;
    }

    public float getParallaxMinLayers() {
        return parallaxMinLayers;
    }

    public void setParallaxMinLayers(float parallaxMinLayers) {
        this.parallaxMinLayers = parallaxMinLayers;
    }

    public float getParallaxMaxLayers() {
        return parallaxMaxLayers;
    }

    public void setParallaxMaxLayers(float parallaxMaxLayers) {
        this.parallaxMaxLayers = parallaxMaxLayers;
    }

    public boolean equalsPropertiesOnly(NMaterial other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (Float.floatToIntBits(this.minExponent) != Float.floatToIntBits(other.minExponent)) {
            return false;
        }
        if (Float.floatToIntBits(this.maxExponent) != Float.floatToIntBits(other.maxExponent)) {
            return false;
        }
        if (Float.floatToIntBits(this.parallaxHeightCoefficient) != Float.floatToIntBits(other.parallaxHeightCoefficient)) {
            return false;
        }
        if (Float.floatToIntBits(this.parallaxMinLayers) != Float.floatToIntBits(other.parallaxMinLayers)) {
            return false;
        }
        if (Float.floatToIntBits(this.parallaxMaxLayers) != Float.floatToIntBits(other.parallaxMaxLayers)) {
            return false;
        }
        if (!Objects.equals(this.diffuseColor, other.diffuseColor)) {
            return false;
        }
        if (!Objects.equals(this.specularColor, other.specularColor)) {
            return false;
        }
        return Objects.equals(this.emissiveColor, other.emissiveColor);
    }

    public NBlendingMode getBlendingMode() {
        NBlendingMode mode = this.textures.getBlendingMode();
        float materialAlpha = this.newColor.w();
        
        if (materialAlpha != 1f && NBlendingMode.OPAQUE.equals(mode)) {
            mode = NBlendingMode.ALPHA_BLENDING;
        }
        
        return mode;
    }
    
    public boolean isInvisible() {
        return this.diffuseColor.w() <= 0f;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.textures);
        hash = 37 * hash + Objects.hashCode(this.diffuseColor);
        hash = 37 * hash + Objects.hashCode(this.specularColor);
        hash = 37 * hash + Objects.hashCode(this.emissiveColor);
        hash = 37 * hash + Float.floatToIntBits(this.minExponent);
        hash = 37 * hash + Float.floatToIntBits(this.maxExponent);
        hash = 37 * hash + Float.floatToIntBits(this.parallaxHeightCoefficient);
        hash = 37 * hash + Float.floatToIntBits(this.parallaxMinLayers);
        hash = 37 * hash + Float.floatToIntBits(this.parallaxMaxLayers);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NMaterial other = (NMaterial) obj;
        if (!Objects.equals(this.textures, other.textures)) {
            return false;
        }
        return equalsPropertiesOnly(other);
    }

}
