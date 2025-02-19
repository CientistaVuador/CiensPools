//E8Image

#ifndef RGBE_BASE
#define RGBE_BASE 1.0905076026656215
#endif

#ifndef RGBE_BIAS
#define RGBE_BIAS 127
#endif

#ifndef RGBE_MAX_EXPONENT
#define RGBE_MAX_EXPONENT 255
#endif

vec4 RGBEToRGBA(vec4 rgbe) {
    return vec4(rgbe.rgb * pow(RGBE_BASE, (rgbe.a * RGBE_MAX_EXPONENT) - RGBE_BIAS), 1.0);
}
