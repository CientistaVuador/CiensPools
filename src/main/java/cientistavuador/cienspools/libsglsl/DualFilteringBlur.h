vec2 blurHalfpixel(sampler2D tex) {
    return 1.0 / vec2(textureSize(tex, 0));
}

vec4 blurDownsample(vec2 uv, vec2 halfpixel, sampler2D tex) {
    vec4 sum = texture(tex, uv) * 4.0;
    sum += texture(tex, uv - halfpixel.xy);
    sum += texture(tex, uv + halfpixel.xy);
    sum += texture(tex, uv + vec2(halfpixel.x, -halfpixel.y));
    sum += texture(tex, uv - vec2(halfpixel.x, -halfpixel.y));
    return sum / 8.0;
}

vec4 blurUpsample(vec2 uv, vec2 halfpixel, sampler2D tex) {
    vec4 sum = texture(tex, uv + vec2(-halfpixel.x * 2.0, 0.0));
    sum += texture(tex, uv + vec2(-halfpixel.x, halfpixel.y)) * 2.0;
    sum += texture(tex, uv + vec2(0.0, halfpixel.y * 2.0));
    sum += texture(tex, uv + vec2(halfpixel.x, halfpixel.y)) * 2.0;
    sum += texture(tex, uv + vec2(halfpixel.x * 2.0, 0.0));
    sum += texture(tex, uv + vec2(halfpixel.x, -halfpixel.y)) * 2.0;
    sum += texture(tex, uv + vec2(0.0, -halfpixel.y * 2.0));
    sum += texture(tex, uv + vec2(-halfpixel.x, -halfpixel.y)) * 2.0;
    return sum / 12.0;
}
