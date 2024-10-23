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
import java.util.Map;

/**
 *
 * @author Cien
 */
public class NProgram {
    
    public static final float FRESNEL_BALANCE = 0.05f;
    public static final float DIFFUSE_BALANCE = 0.50f;
    public static final float MAX_SHININESS = 2048f;
    
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

    private static final String VERTEX_SHADER
            = 
            """
            layout (location = VAO_INDEX_POSITION_XYZ) in vec3 vertexPosition;
            layout (location = VAO_INDEX_TEXTURE_XY) in vec2 vertexTexture;
            layout (location = VAO_INDEX_LIGHTMAP_TEXTURE_XY) in vec2 vertexLightmapTexture;
            layout (location = VAO_INDEX_NORMAL_XYZ) in vec3 vertexNormal;
            layout (location = VAO_INDEX_TANGENT_XYZ) in vec3 vertexTangent;
            layout (location = VAO_INDEX_BONE_IDS_XYZW) in ivec4 vertexBoneIds;
            layout (location = VAO_INDEX_BONE_WEIGHTS_XYZW) in vec4 vertexBoneWeights;
            
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            uniform mat3 normalModel;
            
            uniform mat4 boneMatrices[MAX_AMOUNT_OF_BONES + 1];
            
            out VertexData {
                vec3 worldPosition;
                vec2 worldTexture;
                vec2 worldLightmapTexture;
                vec3 worldNormal;
                
                mat3 TBN;
                vec3 tangentPosition;
            } outVertex;
            
            void main() {
                vec3 localTangent = vec3(0.0);
                vec3 localNormal = vec3(0.0);
                vec4 localPosition = vec4(0.0);
              
                for (int i = 0; i < MAX_AMOUNT_OF_BONE_WEIGHTS; i++) {
                    int boneId = vertexBoneIds[i] + 1;
                    float weight = vertexBoneWeights[i];
                    
                    mat4 boneModel = boneMatrices[boneId];
                    mat3 normalBoneModel = mat3(boneModel);
                    
                    localTangent += normalBoneModel * vertexTangent * weight;
                    localNormal += normalBoneModel * vertexNormal * weight;
                    localPosition += boneModel * vec4(vertexPosition, 1.0) * weight;
                }
                
                vec3 tangent = normalize(normalModel * localTangent);
                vec3 normal = normalize(normalModel * localNormal);
                vec4 worldPosition = model * localPosition;
                
                outVertex.worldPosition = worldPosition.xyz;
                outVertex.worldTexture = vertexTexture;
                outVertex.worldLightmapTexture = vertexLightmapTexture;
                outVertex.worldNormal = normal;
                
                outVertex.TBN = mat3(tangent, cross(normal, tangent), normal);
                outVertex.tangentPosition = transpose(outVertex.TBN) * outVertex.worldPosition;
                
                gl_Position = projection * view * worldPosition;
            }
            """;

    private static final String FRAGMENT_SHADER
            = 
            """
            //shader configuration
            uniform bool enableReflections;
            uniform bool enableLightmaps;
            uniform bool enableTonemapping;
            uniform bool enableGammaCorrection;
            uniform bool enableRefractions;
            uniform bool enableParallaxMapping;
            uniform bool enableWater;
            
            //water
            uniform float waterCounter;
            uniform sampler2DArray waterFrames;
            
            vec3 sampleWaterNormal(vec2 uv) {
                vec4 waterColor = texture(waterFrames, vec3(uv, waterCounter * float(textureSize(waterFrames, 0).z)));
                float nx = (((waterColor.r + waterColor.g + waterColor.b) / 3.0) * 2.0) - 1.0;
                float ny = (waterColor.a * 2.0) - 1.0;
                vec3 waterNormal = vec3(
                    nx,
                    ny,
                    sqrt(abs(1.0 - (nx * nx) - (ny * ny)))
                );
                return normalize(waterNormal);
            }
            
            //material textures
            uniform sampler2DArray materialTextures;
            
            float toLinear(float c) {
                return (c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4));
            }
            
            vec4 r_g_b_a(vec2 uv) {
                #if defined(VARIANT_ALPHA_TESTING)
                vec4 c = texture(materialTextures, vec3(uv, 0.0), -1.0);
                #else
                vec4 c = texture(materialTextures, vec3(uv, 0.0));
                #endif
                
                c.r = toLinear(c.r);
                c.g = toLinear(c.g);
                c.b = toLinear(c.b);
                
                #if defined(VARIANT_OPAQUE)
                c.a = toLinear(c.a);
                c.rgb *= c.a;
                c.a = 1.0;
                #endif
                
                return c;
            }
            
            vec4 ht_rg_mt_nx(vec2 uv) {
                return texture(materialTextures, vec3(uv, 1.0));
            }
            
            vec4 rf_em_wt_ny(vec2 uv) {
                return texture(materialTextures, vec3(uv, 2.0));
            }
            
            //lightmaps texture
            uniform float lightmapIntensity[MAX_AMOUNT_OF_LIGHTMAPS];
            uniform sampler2DArray lightmaps;
            
            vec4 RGBEToRGBA(vec4 rgbe) {
                return vec4(rgbe.rgb * pow(RGBE_BASE, (rgbe.a * RGBE_MAX_EXPONENT) - RGBE_BIAS), 1.0);
            }
            
            vec4 sampleLightmaps(vec2 uv, int index, float intensity) {
                if (intensity == 0.0) return vec4(0.0, 0.0, 0.0, 1.0);
                vec4 c = texture(lightmaps, vec3(uv, float(index)));
                c = RGBEToRGBA(c);
                c.rgb *= intensity;
                return c;
            }
            
            //reflection cubemaps
            uniform samplerCube reflectionCubemap_0;
            uniform samplerCube reflectionCubemap_1;
            uniform samplerCube reflectionCubemap_2;
            uniform samplerCube reflectionCubemap_3;
            
            vec3 cubemapReflection(samplerCube cube, float roughness, vec3 direction) {
                #ifdef SUPPORTED_430
                float mipLevels = float(textureQueryLevels(cube));
                #else
                ivec2 cubemapSize = textureSize(cube, 0);
                float mipLevels = 1.0 + floor(log2(max(float(cubemapSize.x), float(cubemapSize.y))));
                #endif
                float lodLevel = mipLevels * sqrt(roughness);
                #ifdef SUPPORTED_400
                lodLevel = max(textureQueryLod(cube, direction).x, lodLevel);
                #endif
                return RGBEToRGBA(textureLod(cube, direction, lodLevel)).rgb;
            }
            
            vec3 cubemapReflectionIndexed(int index, float roughness, vec3 direction) {
                switch (index) {
                    case 0:
                        return cubemapReflection(reflectionCubemap_0, roughness, direction);
                    case 1:
                        return cubemapReflection(reflectionCubemap_1, roughness, direction);
                    case 2:
                        return cubemapReflection(reflectionCubemap_2, roughness, direction);
                    case 3:
                        return cubemapReflection(reflectionCubemap_3, roughness, direction);
                }
                return vec3(0.0);
            }
            
            //POSITIVE_X
            //NEGATIVE_X
            //POSITIVE_Y
            //NEGATIVE_Y
            //POSITIVE_Z
            //NEGATIVE_Z
            uniform vec3 ambientCube[NUMBER_OF_AMBIENT_CUBE_SIDES];
            
            vec3 ambientLight(vec3 normal) {
                vec3 normalSquared = normal * normal;
                ivec3 negative = ivec3(normal.x < 0.0, normal.y < 0.0, normal.z < 0.0);
                vec3 ambient = normalSquared.x * ambientCube[negative.x]
                    + normalSquared.y * ambientCube[negative.y + 2]
                    + normalSquared.z * ambientCube[negative.z + 4];
                return ambient;
            }
            
            struct ParallaxCubemap {
                bool enabled;
                float intensity;
                vec3 position;
                mat4 worldToLocal;
            };
            
            uniform ParallaxCubemap parallaxCubemaps[MAX_AMOUNT_OF_CUBEMAPS];
            
            struct Material {
                vec4 color;
                float metallic;
                float roughness;
                float height;
                float heightMinLayers;
                float heightMaxLayers;
                float emissive;
                float water;
                float refraction;
                float fresnelOutline;
                vec3 fresnelOutlineColor;
            };
            
            uniform Material material;
            
            struct Light {
                int type;
                float size;
                vec3 position;
                vec3 direction;
                float range;
                float innerCone;
                float outerCone;
                vec3 diffuse;
                vec3 specular;
                vec3 ambient;
            };
            
            uniform Light lights[MAX_AMOUNT_OF_LIGHTS];
            
            in VertexData {
                vec3 worldPosition;
                vec2 worldTexture;
                vec2 worldLightmapTexture;
                vec3 worldNormal;
                
                mat3 TBN;
                vec3 tangentPosition;
            } inVertex;
            
            layout (location = 0) out vec4 outputFragColor;
            
            vec2 parallaxMapping(
                vec2 uv,
                vec3 tangentPosition,
                float minLayers,
                float maxLayers,
                float heightScale
            ) {
                vec3 tangentViewDirection = normalize(-tangentPosition);
                
                float numLayers = mix(maxLayers, minLayers, max(dot(vec3(0.0, 0.0, 1.0), tangentViewDirection), 0.0));
                
                float layerDepth = 1.0 / numLayers;
                float currentLayerDepth = 0.0;
                
                vec2 scaledViewDirection = tangentViewDirection.xy * heightScale;
                vec2 deltaUv = scaledViewDirection / numLayers;
                
                vec2 currentUv = uv;
                float currentDepth = 1.0 - ht_rg_mt_nx(currentUv)[0];
                
                while (currentLayerDepth < currentDepth) {
                    currentUv -= deltaUv;
                    currentDepth = 1.0 - ht_rg_mt_nx(currentUv)[0];
                    currentLayerDepth += layerDepth;
                }
                 
                vec2 previousUv = currentUv + deltaUv;
                
                float afterDepth = currentDepth - currentLayerDepth;
                float beforeDepth = (1.0 - ht_rg_mt_nx(previousUv)[0]) - currentLayerDepth + layerDepth;
                
                float weight = afterDepth / (afterDepth - beforeDepth);
                vec2 finalUv = (previousUv * weight) + (currentUv * (1.0 - weight));
                
                return finalUv;
            }
            
            struct BlinnPhongMaterial {
                float shininess;
                vec3 diffuse;
                vec3 specular;
                vec3 ambient;
            };
            
            float fresnelFactor(vec3 viewDirection, vec3 normal) {
                float fresnelDot = 1.0 - max(dot(-viewDirection, normal), 0.0);
                return FRESNEL_BALANCE + ((1.0 - FRESNEL_BALANCE) * pow(fresnelDot, 5.0));
            }
            
            BlinnPhongMaterial convertPBRMaterialToBlinnPhong(
                vec3 viewDirection, vec3 normal,
                vec3 color, float metallic, float roughness, float ambientOcclusion, float fresnel
            ) {
                float shininess = pow(MAX_SHININESS, 1.0 - roughness);
                float specular = ((shininess + 2.0) * (shininess + 4.0)) / (8.0 * PI * (pow(2.0, -shininess * 0.5) + shininess));
                return BlinnPhongMaterial(
                    shininess,
                    mix(
                        (color * DIFFUSE_BALANCE) / PI,
                        vec3(0.0),
                        metallic
                    ),
                    mix(
                        vec3(max(specular - 0.3496155267919281, 0.0)) * (1.0 - DIFFUSE_BALANCE) * fresnel * PI,
                        vec3(specular) * color,
                        metallic
                    ),
                    mix(
                        vec3(ambientOcclusion) * color,
                        vec3(0.0),
                        metallic
                    )
                );
            }
            
            float calculateSpotlightIntensity(float theta, float innerCone, float outerCone) {
                float epsilon = innerCone - outerCone;
                return clamp((theta - outerCone) / epsilon, 0.0, 1.0);
            }
            
            vec3 calculateLight(
                Light light,
                BlinnPhongMaterial bpMaterial,
                vec3 fragPosition,
                vec3 viewDirection,
                vec3 normal
            ) {
                vec3 lightDirection = (light.type == DIRECTIONAL_LIGHT_TYPE 
                                    ? normalize(light.direction) 
                                    : normalize(fragPosition - light.position));
                
                vec3 halfwayDirection = -normalize(lightDirection + viewDirection);
                float diffuseFactor = max(dot(normal, -lightDirection), 0.0);
                float specularFactor = pow(max(dot(normal, halfwayDirection), 0.0), bpMaterial.shininess) * diffuseFactor;
                float ambientFactor = 1.0;
                
                if (light.type != DIRECTIONAL_LIGHT_TYPE) {
                    float distance = length(light.position - fragPosition);
                    float pointAttenuation = clamp(1.0 - pow(distance / light.range, 4.0), 0.0, 1.0) / ((distance * distance) + light.size);
                    
                    diffuseFactor *= pointAttenuation;
                    specularFactor *= pointAttenuation;
                    ambientFactor *= pointAttenuation;
                    
                    if (light.type == SPOT_LIGHT_TYPE) {
                        float theta = dot(lightDirection, normalize(light.direction));
                        diffuseFactor *= calculateSpotlightIntensity(theta, light.innerCone, light.outerCone);
                        specularFactor *= calculateSpotlightIntensity(theta, light.innerCone, cos(radians(90.0)));
                    }
                }
                
                return (light.diffuse * bpMaterial.diffuse * diffuseFactor) 
                        + (light.specular * bpMaterial.specular * specularFactor) 
                        + (light.ambient * bpMaterial.ambient * ambientFactor);
            }
            
            vec3 computeReflection(
                vec3 fragPosition,
                vec3 viewDirection,
                vec3 reflectedDirection,
                vec3 normal,
                vec3 color,
                float metallic,
                float roughness,
                float fresnel
            ) {
                vec3 totalReflection = vec3(0.0);
                int count = 0;
                
                float furthestDistance = -1.0;
                int furthestIndex = -1;
                vec3 resultDirection = vec3(0.0);
                for (int i = 0; i < MAX_AMOUNT_OF_CUBEMAPS; i++) {
                    ParallaxCubemap parallaxCubemap = parallaxCubemaps[i];
                    
                    if (!parallaxCubemap.enabled) {
                        continue;
                    }
                    
                    vec3 localPosition = (parallaxCubemap.worldToLocal * vec4(fragPosition, 1.0)).xyz;
                    
                    vec3 absLocalPosition = abs(localPosition);
                    if (max(absLocalPosition.x, max(absLocalPosition.y, absLocalPosition.z)) > 1.0) {
                        continue;
                    }
                    
                    vec3 localDirection = mat3(parallaxCubemap.worldToLocal) * reflectedDirection;
                    
                    vec3 firstPlane = (vec3(-1.0) - localPosition) / localDirection;
                    vec3 secondPlane = (vec3(1.0) - localPosition) / localDirection;
                    
                    vec3 furthestPlane = max(firstPlane, secondPlane);
                    float distance = min(furthestPlane.x, min(furthestPlane.y, furthestPlane.z));
                    
                    if (furthestDistance >= 0.0 && distance < furthestDistance) {
                        continue;
                    }
                    
                    vec3 intersectionPosition = fragPosition + (reflectedDirection * distance);
                    resultDirection = normalize(intersectionPosition - parallaxCubemap.position);
                    furthestDistance = distance;
                    furthestIndex = i;
                }
                if (furthestDistance >= 0.0) {
                    vec3 reflectedColor = cubemapReflectionIndexed(furthestIndex, roughness, resultDirection);
                    return mix(
                                reflectedColor * fresnel * pow(1.0 - roughness, 2.0),
                                reflectedColor * color,
                                metallic
                            );
                }
                return vec3(0.0);
            }
            
            vec3 ACESFilm(vec3 rgb) {
                float a = 2.51;
                float b = 0.03;
                float c = 2.43;
                float d = 0.59;
                float e = 0.14;
                return (rgb*(a*rgb+b))/(rgb*(c*rgb+d)+e);
            }
            
            vec3 gammaCorrection(vec3 rgb) {
                return pow(rgb, vec3(1.0/2.2));
            }
            
            void main() {
                vec2 uv = inVertex.worldTexture;
                
                if (enableParallaxMapping) {
                    uv = parallaxMapping(
                            uv,
                            inVertex.tangentPosition,
                            material.heightMinLayers, material.heightMaxLayers, material.height
                        );
                }
                
                vec4 htrgmtnx = ht_rg_mt_nx(uv);
                vec4 rfemwtny = rf_em_wt_ny(uv);
                
                vec4 color = r_g_b_a(uv) * material.color;
                #if defined(VARIANT_ALPHA_TESTING)
                if (color.a < 0.5) {
                    discard;
                }
                #endif
                float metallic = htrgmtnx[2] * material.metallic;
                float roughness = htrgmtnx[1] * material.roughness;
                float emissive = rfemwtny[1] * material.emissive;
                float water = rfemwtny[2] * material.water;
                float refraction = rfemwtny[0] * material.refraction;
                float fresnelOutline = material.fresnelOutline;
                vec3 fresnelOutlineColor = material.fresnelOutlineColor;
                vec3 vertexNormal = normalize(inVertex.worldNormal);
                float nx = (htrgmtnx[3] * 2.0) - 1.0;
                float ny = (rfemwtny[3] * 2.0) - 1.0;
                vec3 normal = normalize(vec3(nx, ny, sqrt(abs(1.0 - (nx * nx) - (ny * ny)))));
                if (enableWater) {
                    normal = mix(normal, sampleWaterNormal(uv), water);
                }
                normal = normalize(inVertex.TBN * normal);
                vec3 position = inVertex.worldPosition;
                vec3 viewDirection = normalize(position);
                
                float fresnel = fresnelFactor(viewDirection, normal);
                
                if (!enableReflections) {
                    metallic = 0.0;
                }
                
                vec4 outputColor = vec4(0.0, 0.0, 0.0, color.a);
                
                if (enableLightmaps) {
                    float lightmapAo = pow(max(dot(vertexNormal, normal), 0.0), 1.4);
                    int amountOfLightmaps = textureSize(lightmaps, 0).z;
                    for (int i = 0; i < amountOfLightmaps; i++) {
                        float intensity = 1.0;
                        if (i < MAX_AMOUNT_OF_LIGHTMAPS) {
                            intensity = lightmapIntensity[i];
                        }
                        outputColor.rgb += 
                            sampleLightmaps(inVertex.worldLightmapTexture, i, intensity).rgb
                            * mix(color.rgb, vec3(0.0), metallic)
                            * lightmapAo;
                    }
                }
                
                BlinnPhongMaterial bpMaterial = convertPBRMaterialToBlinnPhong(
                    viewDirection, normal, color.rgb, metallic, roughness, 1.0, fresnel
                );
                if (!enableReflections) {
                    bpMaterial.specular = vec3(0.0);
                }
                for (int i = 0; i < MAX_AMOUNT_OF_LIGHTS; i++) {
                    Light light = lights[i];
                    if (light.type == NULL_LIGHT_TYPE) {
                        break;
                    }
                    outputColor.rgb += calculateLight(light, bpMaterial, position, viewDirection, normal);
                }
                
                outputColor.rgb += mix(ambientLight(normal) * color.rgb, vec3(0.0), metallic);
                outputColor.rgb += color.rgb * emissive;
                
                if (enableReflections) {
                    vec3 reflectedDirection = reflect(viewDirection, normal);
                    outputColor.rgb += computeReflection(
                            position, viewDirection,
                            reflectedDirection, normal,
                            color.rgb, metallic, roughness, fresnel
                    );
                }
                
                if (enableRefractions) {
                    vec3 refractedDirection = refract(viewDirection, normal, refraction);
                    vec3 refractedColor = computeReflection(
                            position, viewDirection,
                            refractedDirection, normal,
                            vec3(1.0), 1.0, roughness, fresnel
                    );
                    refractedColor *= mix(vec3(1.0), color.rgb, outputColor.a);
                    outputColor.rgb = mix(refractedColor, outputColor.rgb, outputColor.a);
                    outputColor.a = 1.0;
                }
                
                outputColor.rgb = mix(
                        outputColor.rgb,
                        mix(outputColor.rgb, material.fresnelOutlineColor, fresnel),
                        fresnelOutline
                );
                
                if (enableTonemapping) {
                    outputColor.rgb = ACESFilm(outputColor.rgb);
                }
            
                if (enableGammaCorrection) {
                    outputColor.rgb = gammaCorrection(outputColor.rgb);
                }
                
                #if defined(VARIANT_ALPHA_TESTING) || defined(VARIANT_OPAQUE)
                outputFragColor = vec4(outputColor.rgb, 1.0);
                #endif
                
                #if defined(VARIANT_ALPHA_BLENDING)
                outputFragColor = outputColor;
                #endif
            }
            """;

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
        new ProgramCompiler.ShaderConstant("FRESNEL_BALANCE", FRESNEL_BALANCE),
        new ProgramCompiler.ShaderConstant("DIFFUSE_BALANCE", DIFFUSE_BALANCE),
        new ProgramCompiler.ShaderConstant("MAX_SHININESS", MAX_SHININESS)
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
    public static final String UNIFORM_MATERIAL_HEIGHT = "material.height";
    public static final String UNIFORM_MATERIAL_HEIGHT_MIN_LAYERS = "material.heightMinLayers";
    public static final String UNIFORM_MATERIAL_HEIGHT_MAX_LAYERS = "material.heightMaxLayers";
    public static final String UNIFORM_MATERIAL_EMISSIVE = "material.emissive";
    public static final String UNIFORM_MATERIAL_WATER = "material.water";
    public static final String UNIFORM_MATERIAL_REFRACTION = "material.refraction";
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
