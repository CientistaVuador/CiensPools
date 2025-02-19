#ifndef PI
#define PI 3.1415926535
#endif

#ifndef LIGHT_DISTANCE_OFFSET
#define LIGHT_DISTANCE_OFFSET 1.0
#endif

#ifndef FRESNEL_F0
#define FRESNEL_F0 0.05
#endif

#ifndef NULL_LIGHT_TYPE
#define NULL_LIGHT_TYPE 0
#endif

#ifndef DIRECTIONAL_LIGHT_TYPE
#define DIRECTIONAL_LIGHT_TYPE 1
#endif

#ifndef POINT_LIGHT_TYPE
#define POINT_LIGHT_TYPE 2
#endif

#ifndef SPOT_LIGHT_TYPE
#define SPOT_LIGHT_TYPE 3
#endif

struct Light {
    int type;
    vec3 position;
    vec3 direction;
    float size;
    float range;
    float innerCone;
    float outerCone;
    vec3 diffuse;
    vec3 specular;
    vec3 ambient;
};

struct BPMaterial {
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

BPMaterial computeBPMaterial(
    vec3 color, float metallic, float roughness, float ambientOcclusion
) {
    float shininess = pow(65535.0, 1.0 - roughness);
    float specular = ((shininess + 2.0) * (shininess + 4.0))
                        / (8.0 * PI * (pow(2.0, -shininess * 0.5) + shininess));
    return BPMaterial(
        shininess, metallic, roughness,
        mix(color / PI, vec3(0.0), metallic),
        mix(vec3(specular), vec3(specular) * color, metallic),
        mix(vec3(ambientOcclusion) * color, vec3(0.0), metallic)
    );
}

float calculateSpotlightIntensity(float theta, float innerCone, float outerCone) {
    float epsilon = innerCone - outerCone;
    return pow(clamp((theta - outerCone) / epsilon, 0.0, 1.0), 2.0);
}

vec3 calculateLight(
    Light light, BPMaterial mat, vec3 fragPosition, vec3 viewDirection, vec3 normal
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
                                / ((distance * distance) + LIGHT_DISTANCE_OFFSET);

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