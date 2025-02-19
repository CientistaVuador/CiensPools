#ifndef PARALLAX_MAPPING_NAME
#define PARALLAX_MAPPING_NAME parallaxMapping
#endif

#ifndef PARALLAX_MAPPING_SAMPLER_TYPE
#define PARALLAX_MAPPING_SAMPLER_TYPE sampler2D
#endif

#ifndef PARALLAX_MAPPING_HEIGHT
#define PARALLAX_MAPPING_HEIGHT(tex, uv) texture(tex, uv).r
#endif

vec2 PARALLAX_MAPPING_NAME(
    PARALLAX_MAPPING_SAMPLER_TYPE heightMap,
    vec2 uv,
    vec3 tangentViewDirection,
    float minLayers,
    float maxLayers,
    float heightScale
) {
    float numLayers = mix(maxLayers, minLayers, max(dot(vec3(0.0, 0.0, 1.0), tangentViewDirection), 0.0));

    float layerDepth = 1.0 / numLayers;
    float currentLayerDepth = 0.0;

    vec2 scaledViewDirection = tangentViewDirection.xy * heightScale;
    vec2 deltaUv = scaledViewDirection / numLayers;

    vec2 currentUv = uv;
    float currentDepth = 1.0 - PARALLAX_MAPPING_HEIGHT(heightMap, currentUv);

    while (currentLayerDepth < currentDepth) {
        currentUv -= deltaUv;
        currentDepth = 1.0 - PARALLAX_MAPPING_HEIGHT(heightMap, currentUv);
        currentLayerDepth += layerDepth;
    }

    vec2 previousUv = currentUv + deltaUv;

    float afterDepth = currentDepth - currentLayerDepth;
    float beforeDepth = (1.0 - PARALLAX_MAPPING_HEIGHT(heightMap, currentUv))
                         - currentLayerDepth + layerDepth;

    float weight = afterDepth / (afterDepth - beforeDepth);
    vec2 finalUv = (previousUv * weight) + (currentUv * (1.0 - weight));

    return finalUv;
}
