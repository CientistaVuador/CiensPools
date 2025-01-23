uniform mat4 view;

//shader configuration
uniform bool enableReflections;
uniform bool enableLightmaps;
uniform bool enableRefractions;
uniform bool enableParallaxMapping;
uniform bool enableWater;
uniform bool enableOpaqueTexture;

//specular brdf lookup table
uniform sampler2D specularBRDFLookupTable;

//water
uniform float waterCounter;
uniform sampler2DArray waterFrames;

vec3 sampleWaterNormal(vec2 uv) {
    float frame = waterCounter * float(textureSize(waterFrames, 0).z);
    vec4 waterNormalA = texture(waterFrames, vec3(uv, floor(frame)));
    vec4 waterNormalB = texture(waterFrames, vec3(uv, ceil(frame)));
    float a = 0.0;
    vec4 waterColor = mix(waterNormalA, waterNormalB, modf(frame, a));
    float nx = (waterColor.g * 2.0) - 1.0;
    float ny = (waterColor.a * 2.0) - 1.0;
    vec3 waterNormal = vec3(
        nx,
        ny,
        sqrt(abs(1.0 - (nx * nx) - (ny * ny)))
    );
    return normalize(waterNormal);
}

//opaque pass buffer
uniform sampler2D screen;
uniform vec2 screenSize;

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

    if (enableOpaqueTexture) {
        c.a = toLinear(c.a);
        c.rgb *= c.a;
        c.a = 1.0;
    }

    return c;
}

vec4 ht_rg_mt_nx(vec2 uv) {
    return texture(materialTextures, vec3(uv, 1.0));
}

vec4 em_ao_wt_ny(vec2 uv) {
    vec4 c = texture(materialTextures, vec3(uv, 2.0));
    c[0] = toLinear(c[0]);
    return c;
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
    float lodLevel = mipLevels * roughness;
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
    float diffuseSpecularRatio;
    float height;
    float heightMinLayers;
    float heightMaxLayers;
    float emissive;
    float water;
    float refraction;
    float refractionPower;
    float ambientOcclusion;
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
    float metallic;
    float roughness;
    vec3 diffuse;
    vec3 specular;
    vec3 ambient;
};

float fresnelFactor(float viewDirectionDot, float roughness) {
    return FRESNEL_F0
            + (max(1.0 - roughness, FRESNEL_F0) - FRESNEL_F0) 
            * pow(clamp(1.0 - viewDirectionDot, 0.0, 1.0), 5.0);
}

BlinnPhongMaterial convertPBRMaterialToBlinnPhong(
    float diffuseSpecularRatio, vec3 color,
    float metallic, float roughness, float ambientOcclusion
) {
    float shininess = pow(65535.0, 1.0 - roughness);
    float specular = ((shininess + 2.0) * (shininess + 4.0))
                        / (8.0 * PI * (pow(2.0, -shininess * 0.5) + shininess));
    return BlinnPhongMaterial(
        shininess,
        metallic,
        roughness,
        mix(
            (color * diffuseSpecularRatio) / PI,
            vec3(0.0),
            metallic
        ),
        mix(
            vec3(specular) * (1.0 - diffuseSpecularRatio),
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
    return pow(clamp((theta - outerCone) / epsilon, 0.0, 1.0), 2.0);
}

vec3 calculateLight(
    Light light,
    BlinnPhongMaterial mat,
    vec3 fragPosition,
    vec3 viewDirection,
    vec3 normal
) {
    vec3 lightDirection = (light.type == DIRECTIONAL_LIGHT_TYPE
                        ? normalize(light.direction)
                        : normalize(fragPosition - light.position));
    vec3 halfwayDirection = -normalize(lightDirection + viewDirection);
    
    float normalDotHalfway = max(dot(normal, halfwayDirection), 0.0);
    float fresnel = mix(fresnelFactor(normalDotHalfway, mat.roughness), 1.0, mat.metallic);
    
    float diffuseFactor = clamp(dot(normal, -lightDirection), 0.0, 1.0);
    float specularFactor = pow(normalDotHalfway, mat.shininess) * diffuseFactor * fresnel;
    
    float ambientFactor = 1.0;
    if (light.type != DIRECTIONAL_LIGHT_TYPE) {
        float distance = length(light.position - fragPosition);
        float pointAttenuation = clamp(1.0 - pow(distance / light.range, 4.0), 0.0, 1.0)
                                / ((distance * distance) + light.size);

        diffuseFactor *= pointAttenuation;
        specularFactor *= pointAttenuation;
        ambientFactor *= pointAttenuation;

        if (light.type == SPOT_LIGHT_TYPE) {
            float theta = dot(lightDirection, normalize(light.direction));
            diffuseFactor *= calculateSpotlightIntensity(theta, light.innerCone, light.outerCone);
            specularFactor *= calculateSpotlightIntensity(theta, light.innerCone, cos(radians(90.0)));
        }
    }

    return (light.diffuse * mat.diffuse * diffuseFactor)
            + (light.specular * mat.specular * specularFactor)
            + (light.ambient * mat.ambient * ambientFactor);
}

//P is rayOrigin + (rayDirection * t)
//where t is the return value
//returns -1.0 if the ray is outside the box
float intersectRayInsideBox(vec3 rayOrigin, vec3 rayDirection, mat4 worldToLocal) {
    vec3 localOrigin = (worldToLocal * vec4(rayOrigin, 1.0)).xyz;
    vec3 aabbCheck = abs(localOrigin);
    if (max(aabbCheck.x, max(aabbCheck.y, aabbCheck.z)) > 1.0) {
        return -1.0;
    }
    vec3 localDirection = mat3(worldToLocal) * rayDirection;
    vec3 firstPlane = (vec3(-1.0) - localOrigin) / localDirection;
    vec3 secondPlane = (vec3(1.0) - localOrigin) / localDirection;
    vec3 furthestPlane = max(firstPlane, secondPlane);
    return min(furthestPlane.x, min(furthestPlane.y, furthestPlane.z));
}

vec3 computeReflection(
    vec2 brdf,
    float specularRatio,
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
        
        float distance = 
                intersectRayInsideBox(fragPosition, reflectedDirection, parallaxCubemap.worldToLocal);
        if (distance < 0.0) {
            continue;
        }
        
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
                    reflectedColor * ((fresnel * brdf.x) + brdf.y) * specularRatio,
                    reflectedColor * (brdf.x + brdf.y) * color,
                    metallic
                );
    }
    return vec3(0.0);
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
    
    vec4 color = r_g_b_a(uv) * material.color;
    #if defined(VARIANT_ALPHA_TESTING)
    if (color.a < 0.5) {
        discard;
    }
    #endif
    
    vec4 htrgmtnx = ht_rg_mt_nx(uv);
    vec4 emaowtny = em_ao_wt_ny(uv);
    
    vec4 outputColor = vec4(0.0, 0.0, 0.0, color.a);
    
    vec3 vertexNormal = normalize(inVertex.worldNormal);
    float nx = (htrgmtnx[3] * 2.0) - 1.0;
    float ny = (emaowtny[3] * 2.0) - 1.0;
    vec3 tangentNormal = normalize(vec3(nx, ny, sqrt(abs(1.0 - (nx * nx) - (ny * ny)))));
    float water = emaowtny[2] * material.water;
    if (enableWater) {
        tangentNormal = normalize(mix(tangentNormal, sampleWaterNormal(uv), water));
    }
    vec3 normal = normalize(inVertex.TBN * tangentNormal);
    
    float ambientOcclusion = emaowtny[1] * material.ambientOcclusion;
    
    float diffuseSpecularRatio = material.diffuseSpecularRatio;
    float diffuseRatio = min(diffuseSpecularRatio, 0.5) * 2.0;
    float specularRatio = min(1.0 - diffuseSpecularRatio, 0.5) * 2.0;
    
    float metallic = htrgmtnx[2] * material.metallic;
    float roughness = htrgmtnx[1] * material.roughness;
    
    if (!enableReflections) {
        metallic = 0.0;
    }
    
    if (enableLightmaps) {
        vec3 lightmapFactor = 
                mix(color.rgb, vec3(0.0), metallic) 
                * ambientOcclusion
                * diffuseRatio
                ;
        int amountOfLightmaps = textureSize(lightmaps, 0).z;
        for (int i = 0; i < amountOfLightmaps; i++) {
            float intensity = 1.0;
            if (i < MAX_AMOUNT_OF_LIGHTMAPS) {
                intensity = lightmapIntensity[i];
            }
            outputColor.rgb +=
                sampleLightmaps(inVertex.worldLightmapTexture, i, intensity).rgb
                * lightmapFactor;
        }
    }

    vec3 position = inVertex.worldPosition;
    vec3 viewDirection = normalize(position);
    float viewDirectionDot = clamp(dot(normal, -viewDirection), 0.0, 1.0);
    float fresnel = fresnelFactor(viewDirectionDot, roughness);
    
    BlinnPhongMaterial bpMaterial = convertPBRMaterialToBlinnPhong(
        diffuseSpecularRatio, color.rgb, metallic, roughness, ambientOcclusion
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

    outputColor.rgb += mix(
            ambientLight(normal) * color.rgb * ambientOcclusion * diffuseRatio, vec3(0.0), metallic);
    
    float emissive = emaowtny[0] * material.emissive;
    outputColor.rgb += color.rgb * emissive;
    
    vec2 brdf = texture(specularBRDFLookupTable, vec2(viewDirectionDot, roughness)).rg;
    if (enableReflections) {
        vec3 reflectedDirection = reflect(viewDirection, normal);
        outputColor.rgb += computeReflection(
                brdf, specularRatio,
                position, viewDirection,
                reflectedDirection, normal,
                color.rgb, metallic, roughness, fresnel
        );
    }

    if (enableRefractions) {
        //float refraction = material.refraction;
        //vec3 refractedDirection = refract(viewDirection, normal, refraction);
        //refractedDirection = normalize(mix(viewDirection, refractedDirection, material.refractionPower));
        //vec3 refractedColor = computeReflection(
        //        brdf, specularRatio,
        //        position, viewDirection,
        //        refractedDirection, normal,
        //        vec3(1.0), 1.0, roughness, fresnel
        //);
        
        vec3 viewSpaceNormal = normalize(mat3(view) * normal);
        
        float refraction = material.refraction;
        vec3 tangentViewDirection = vec3(0.0, 0.0, -1.0);
        vec3 refractedDirection = refract(tangentViewDirection, normal, refraction);
        refractedDirection = normalize(mix(tangentViewDirection, tangentNormal, 0.05));
        
        vec3 refractedColor = texture(screen, (gl_FragCoord.xy / screenSize) + refractedDirection.xy).rgb;
        refractedColor *= mix(vec3(1.0), color.rgb, outputColor.a);
        outputColor.rgb = mix(refractedColor, outputColor.rgb, outputColor.a);
        outputColor.a = 1.0;
    }
    
    float fresnelOutline = material.fresnelOutline;
    vec3 fresnelOutlineColor = material.fresnelOutlineColor;
    outputColor.rgb = mix(
            outputColor.rgb,
            mix(outputColor.rgb, material.fresnelOutlineColor, fresnel),
            fresnelOutline
    );
    
    #if defined(VARIANT_ALPHA_TESTING) || defined(VARIANT_OPAQUE)
    outputFragColor = vec4(outputColor.rgb, 1.0);
    #endif

    #if defined(VARIANT_ALPHA_BLENDING)
    outputFragColor = outputColor;
    #endif
}