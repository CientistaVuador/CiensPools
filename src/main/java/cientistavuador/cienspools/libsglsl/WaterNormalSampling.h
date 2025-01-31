vec3 sampleWaterNormal(vec2 uv, sampler2DArray waterFrames, float waterCounter) {
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