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
package cientistavuador.cienspools.util;

import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public class ColorUtils {

    public static void lightBlend(
            List<Vector4fc> colors,
            List<Vector3fc> lights,
            Vector3f outLight
    ) {
        if (colors.size() != lights.size()) {
            throw new IllegalArgumentException("Colors and lights must have the same size!");
        }

        outLight.zero();
        for (int i = 0; i < colors.size(); i++) {
            Vector4fc color = colors.get(i);
            Vector3fc light = lights.get(i);

            outLight.mul(
                    (color.x() * color.w()) + (1f - color.w()),
                    (color.y() * color.w()) + (1f - color.w()),
                    (color.z() * color.w()) + (1f - color.w())
            ).mul(1f - color.w());

            outLight.add(
                    light.x() * color.x() * color.w(),
                    light.y() * color.y() * color.w(),
                    light.z() * color.z() * color.w()
            );
        }
    }

    public static void blend(List<Vector4fc> colors, Vector4f outColor) {
        outColor.zero();
        if (colors == null || colors.isEmpty()) {
            return;
        }
        outColor.set(colors.get(0));
        for (int i = 1; i < colors.size(); i++) {
            Vector4fc source = colors.get(i);

            float alpha = source.w() + outColor.w() * (1f - source.w());
            if (alpha < 0.00001f) {
                continue;
            }
            float invalpha = 1f / alpha;
            outColor.set(
                    (source.x() * source.w() + outColor.x() * outColor.w() * (1f - source.w())) * invalpha,
                    (source.y() * source.w() + outColor.y() * outColor.w() * (1f - source.w())) * invalpha,
                    (source.z() * source.w() + outColor.z() * outColor.w() * (1f - source.w())) * invalpha,
                    alpha
            );
        }
    }

    public static Vector4f setSRGBA(Vector4f out, int red, int green, int blue, int alpha) {
        return out.set(
                Math.pow(red / 255f, 2.2),
                Math.pow(green / 255f, 2.2),
                Math.pow(blue / 255f, 2.2),
                alpha / 255f
        );
    }

    public static Vector3f setSRGB(Vector3f out, int red, int green, int blue) {
        return out.set(
                Math.pow(red / 255f, 2.2),
                Math.pow(green / 255f, 2.2),
                Math.pow(blue / 255f, 2.2)
        );
    }

    public static float toLinearSpace(float c) {
        if (c <= 0.04045f) {
            return c / 12.92f;
        }
        return (float) Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static float toSRGBSpace(float c) {
        if (c <= 0f) {
            return 0f;
        }
        if (c >= 1f) {
            return 1f;
        }
        if (c < 0.0031308f) {
            return 12.92f * c;
        }
        return (float) ((1.055 * Math.pow(c, 0.41666)) - 0.055);
    }

    public static Vector4f toLinearSpace(Vector4f color) {
        color.set(
                toLinearSpace(color.x()),
                toLinearSpace(color.y()),
                toLinearSpace(color.z()),
                color.w()
        );
        return color;
    }

    public static Vector4f toSRGBSpace(Vector4f color) {
        color.set(
                toSRGBSpace(color.x()),
                toSRGBSpace(color.y()),
                toSRGBSpace(color.z()),
                color.w()
        );
        return color;
    }

    public static float mix(float a, float b, float v) {
        return (a * (1f - v)) + (b * v);
    }

    public static void mergeEmissiveWithColor(Vector4f color, Vector4f emissive) {
        ColorUtils.toLinearSpace(color);
        ColorUtils.toLinearSpace(emissive);

        float luminance = Math.max(Math.max(emissive.x(), emissive.y()), emissive.z());

        color.set(
                ColorUtils.mix(color.x(), emissive.x(), luminance),
                ColorUtils.mix(color.y(), emissive.y(), luminance),
                ColorUtils.mix(color.z(), emissive.z(), luminance)
        );
        emissive.set(luminance, luminance, luminance);
        
        ColorUtils.toSRGBSpace(color);
        ColorUtils.toSRGBSpace(emissive);
    }

    private ColorUtils() {

    }
}
