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

import cientistavuador.cienspools.util.bakedlighting.AmbientCube;
import cientistavuador.cienspools.util.BetterUniformSetter;
import cientistavuador.cienspools.util.E8Image;
import cientistavuador.cienspools.util.ProgramCompiler;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 *
 * @author Cien
 */
public class NProgram {
    
    public static final float FRESNEL_BALANCE = 0.05f;
    
    public static final int MAX_AMOUNT_OF_LIGHTS = 24;
    public static final int MAX_AMOUNT_OF_LIGHTMAPS = 32;

    public static final int NULL_LIGHT_TYPE = 0;
    public static final int DIRECTIONAL_LIGHT_TYPE = 1;
    public static final int POINT_LIGHT_TYPE = 2;
    public static final int SPOT_LIGHT_TYPE = 3;

    public static final int MAX_AMOUNT_OF_CUBEMAPS = 4;
    
    public static final BetterUniformSetter VARIANT_OPAQUE;
    public static final BetterUniformSetter VARIANT_ALPHA_TESTING;
    public static final BetterUniformSetter VARIANT_ALPHA_BLENDING;

    public static final String VERTEX_SHADER;
    public static final String FRAGMENT_SHADER;
    
    static {
        try {
            try (InputStream in = NProgram.class.getResourceAsStream("nprogram.vert")) {
                VERTEX_SHADER = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            try (InputStream in = NProgram.class.getResourceAsStream("nprogram.frag")) {
                FRAGMENT_SHADER = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final ProgramCompiler.ShaderConstant[] CONSTANTS = {
        new ProgramCompiler.ShaderConstant("VAO_INDEX_POSITION_XYZ", NMesh.VAO_INDEX_POSITION_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_TEXTURE_XY", NMesh.VAO_INDEX_TEXTURE_XY),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_LIGHTMAP_TEXTURE_XY", NMesh.VAO_INDEX_LIGHTMAP_TEXTURE_XY),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_NORMAL_XYZ", NMesh.VAO_INDEX_NORMAL_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_TANGENT_XYZ", NMesh.VAO_INDEX_TANGENT_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_BONE_IDS_XYZW", NMesh.VAO_INDEX_BONE_IDS_XYZW),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_BONE_WEIGHTS_XYZW", NMesh.VAO_INDEX_BONE_WEIGHTS_XYZW),
        new ProgramCompiler.ShaderConstant("PI", Math.PI),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_LIGHTS", MAX_AMOUNT_OF_LIGHTS),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_LIGHTMAPS", MAX_AMOUNT_OF_LIGHTMAPS),
        new ProgramCompiler.ShaderConstant("NULL_LIGHT_TYPE", NULL_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("DIRECTIONAL_LIGHT_TYPE", DIRECTIONAL_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("POINT_LIGHT_TYPE", POINT_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("SPOT_LIGHT_TYPE", SPOT_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_BONES", NMesh.MAX_AMOUNT_OF_BONES),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_BONE_WEIGHTS", NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_CUBEMAPS", MAX_AMOUNT_OF_CUBEMAPS),
        new ProgramCompiler.ShaderConstant("NUMBER_OF_AMBIENT_CUBE_SIDES", AmbientCube.SIDES),
        new ProgramCompiler.ShaderConstant("RGBE_BASE", E8Image.BASE),
        new ProgramCompiler.ShaderConstant("RGBE_MAX_EXPONENT", E8Image.MAX_EXPONENT),
        new ProgramCompiler.ShaderConstant("RGBE_BIAS", E8Image.BIAS),
        new ProgramCompiler.ShaderConstant("FRESNEL_BALANCE", FRESNEL_BALANCE)
    };

    static {
        Map<String, Integer> programs = ProgramCompiler.compile(
                VERTEX_SHADER, FRAGMENT_SHADER,
                new String[]{
                    "OPAQUE",
                    "ALPHA_TESTING",
                    "ALPHA_BLENDING"
                },
                CONSTANTS
        );

        VARIANT_OPAQUE = new BetterUniformSetter(programs.get("OPAQUE"));
        VARIANT_ALPHA_TESTING = new BetterUniformSetter(programs.get("ALPHA_TESTING"));
        VARIANT_ALPHA_BLENDING = new BetterUniformSetter(programs.get("ALPHA_BLENDING"));
    }

    public static final String UNIFORM_PROJECTION = "projection";
    public static final String UNIFORM_VIEW = "view";
    public static final String UNIFORM_MODEL = "model";
    public static final String UNIFORM_NORMAL_MODEL = "normalModel";
    
    public static String UNIFORM_BONE_MATRIX(int index) {
        return "boneMatrices["+(index + 1)+"]";
    }
    
    public static final String UNIFORM_ENABLE_REFLECTIONS = "enableReflections";
    public static final String UNIFORM_ENABLE_LIGHTMAPS = "enableLightmaps";
    public static final String UNIFORM_ENABLE_TONEMAPPING = "enableTonemapping";
    public static final String UNIFORM_ENABLE_GAMMA_CORRECTION = "enableGammaCorrection";
    public static final String UNIFORM_ENABLE_REFRACTIONS = "enableRefractions";
    public static final String UNIFORM_ENABLE_PARALLAX_MAPPING = "enableParallaxMapping";
    public static final String UNIFORM_ENABLE_WATER = "enableWater";
    public static final String UNIFORM_ENABLE_OPAQUE_TEXTURE = "enableOpaqueTexture";
    
    public static final String UNIFORM_WATER_COUNTER = "waterCounter";
    public static final String UNIFORM_WATER_FRAMES = "waterFrames";
    public static final String UNIFORM_MATERIAL_TEXTURES = "materialTextures";
    
    public static String UNIFORM_LIGHTMAP_INTENSITY(int index) {
        return "lightmapIntensity["+index+"]";
    }
    
    public static final String UNIFORM_LIGHTMAPS = "lightmaps";
    
    public static final String UNIFORM_REFLECTION_CUBEMAP_0 = "reflectionCubemap_0";
    public static final String UNIFORM_REFLECTION_CUBEMAP_1 = "reflectionCubemap_1";
    public static final String UNIFORM_REFLECTION_CUBEMAP_2 = "reflectionCubemap_2";
    public static final String UNIFORM_REFLECTION_CUBEMAP_3 = "reflectionCubemap_3";
    
    public static String UNIFORM_AMBIENT_CUBE(int side) {
        return "ambientCube["+side+"]";
    }
    
    public static final String UNIFORM_PARALLAX_CUBEMAP_ENABLED(int index) {
        return "parallaxCubemaps["+index+"].enabled";
    }
    public static final String UNIFORM_PARALLAX_CUBEMAP_INTENSITY(int index) {
        return "parallaxCubemaps["+index+"].intensity";
    }
    public static final String UNIFORM_PARALLAX_CUBEMAP_POSITION(int index) {
        return "parallaxCubemaps["+index+"].position";
    }
    public static final String UNIFORM_PARALLAX_CUBEMAP_WORLD_TO_LOCAL(int index) {
        return "parallaxCubemaps["+index+"].worldToLocal";
    }
    
    public static final String UNIFORM_MATERIAL_COLOR = "material.color";
    public static final String UNIFORM_MATERIAL_METALLIC = "material.metallic";
    public static final String UNIFORM_MATERIAL_ROUGHNESS = "material.roughness";
    public static final String UNIFORM_MATERIAL_INVERSE_ROUGHNESS_EXPONENT = "material.inverseRoughnessExponent";
    public static final String UNIFORM_MATERIAL_DIFFUSE_SPECULAR_RATIO = "material.diffuseSpecularRatio";
    public static final String UNIFORM_MATERIAL_HEIGHT = "material.height";
    public static final String UNIFORM_MATERIAL_HEIGHT_MIN_LAYERS = "material.heightMinLayers";
    public static final String UNIFORM_MATERIAL_HEIGHT_MAX_LAYERS = "material.heightMaxLayers";
    public static final String UNIFORM_MATERIAL_EMISSIVE = "material.emissive";
    public static final String UNIFORM_MATERIAL_WATER = "material.water";
    public static final String UNIFORM_MATERIAL_REFRACTION = "material.refraction";
    public static final String UNIFORM_MATERIAL_REFRACTION_POWER = "material.refractionPower";
    public static final String UNIFORM_MATERIAL_AMBIENT_OCCLUSION = "material.ambientOcclusion";
    public static final String UNIFORM_MATERIAL_FRESNEL_OUTLINE = "material.fresnelOutline";
    public static final String UNIFORM_MATERIAL_FRESNEL_OUTLINE_COLOR = "material.fresnelOutlineColor";
    
    public static final String UNIFORM_LIGHT_TYPE(int index) {
        return "lights["+index+"].type";
    }
    public static final String UNIFORM_LIGHT_SIZE(int index) {
        return "lights["+index+"].size";
    }
    public static final String UNIFORM_LIGHT_POSITION(int index) {
        return "lights["+index+"].position";
    }
    public static final String UNIFORM_LIGHT_DIRECTION(int index) {
        return "lights["+index+"].direction";
    }
    public static final String UNIFORM_LIGHT_RANGE(int index) {
        return "lights["+index+"].range";
    }
    public static final String UNIFORM_LIGHT_INNER_CONE(int index) {
        return "lights["+index+"].innerCone";
    }
    public static final String UNIFORM_LIGHT_OUTER_CONE(int index) {
        return "lights["+index+"].outerCone";
    }
    public static final String UNIFORM_LIGHT_DIFFUSE(int index) {
        return "lights["+index+"].diffuse";
    }
    public static final String UNIFORM_LIGHT_SPECULAR(int index) {
        return "lights["+index+"].specular";
    }
    public static final String UNIFORM_LIGHT_AMBIENT(int index) {
        return "lights["+index+"].ambient";
    }
    
    public static void init() {

    }

    private NProgram() {

    }

}
