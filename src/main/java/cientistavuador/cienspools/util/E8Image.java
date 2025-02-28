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

import cientistavuador.cienspools.libsglsl.Include;
import java.awt.image.BufferedImage;
import java.util.Objects;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class E8Image {

    public static final int MAX_EXPONENT = 255;
    public static final int BIAS = 127;
    public static final double BASE = Math.pow(65535.0, 1.0 / (MAX_EXPONENT - BIAS));
    public static final double INVERSE_LOG_BASE = 1.0 / Math.log(BASE);
    
    public static final float MAX_VALUE = (float) Math.pow(BASE, MAX_EXPONENT - BIAS);
    public static final float MIN_VALUE = (float) (Math.pow(BASE, 0 - BIAS) * (1.0 / 255.0));
    
    private static final float[] LOOKUP_TABLE = new float[MAX_EXPONENT + 1];
    
    static {
        for (int exp = 0; exp < LOOKUP_TABLE.length; exp++) {
            LOOKUP_TABLE[exp] = (float) Math.pow(BASE, exp - BIAS);
        }
    }
    
    private static void encodeTo(float r, float g, float b, int index, byte[] data) {
        int mR = 255;
        int mG = 255;
        int mB = 255;
        int exp = 255;
        
        encode:
        {
            if (!Float.isFinite(r) || !Float.isFinite(g) || !Float.isFinite(b)) {
                break encode;
            }
            r = Math.min(Math.max(r, 0f), MAX_VALUE);
            g = Math.min(Math.max(g, 0f), MAX_VALUE);
            b = Math.min(Math.max(b, 0f), MAX_VALUE);
            
            float intensity = Math.max(r, Math.max(g, b));
            if (intensity < MIN_VALUE) {
                mR = 0;
                mG = 0;
                mB = 0;
                exp = 0;
                break encode;
            }

            exp = (int) Math.ceil((Math.log(intensity) * INVERSE_LOG_BASE) + BIAS);
            exp = Math.min(Math.max(exp, 0), MAX_EXPONENT);
            
            intensity = LOOKUP_TABLE[exp];
            
            r /= intensity;
            g /= intensity;
            b /= intensity;
            
            mR = Math.min(Math.max(Math.round(r * 255f), 0), 255);
            mG = Math.min(Math.max(Math.round(g * 255f), 0), 255);
            mB = Math.min(Math.max(Math.round(b * 255f), 0), 255);
        }
        
        data[0 + index] = (byte) mR;
        data[1 + index] = (byte) mG;
        data[2 + index] = (byte) mB;
        data[3 + index] = (byte) exp;
    }
    
    public static final E8Image NULL_IMAGE = new E8Image(new float[0], 0, 0);
    
    private final byte[] rgbe;
    private final int width;
    private final int height;

    public E8Image(byte[] rgbe, int width, int height) {
        Objects.requireNonNull(rgbe, "data is null");

        int pixels = width * height;
        if (rgbe.length / 4 != pixels) {
            throw new IllegalArgumentException("Invalid amount of pixels! required " + pixels + ", found " + (rgbe.length / 4));
        }

        this.rgbe = rgbe;
        this.width = width;
        this.height = height;
    }
    
    public E8Image(int width, int height) {
        this(new byte[width * height * 4], width, height);
    }
    
    public E8Image(RGBA8Image image) {
        this(image.getRGBA(), image.getWidth(), image.getHeight());
    }
    
    public E8Image(float[] rgb, int width, int height) {
        Objects.requireNonNull(rgb, "data is null");

        int pixels = width * height;
        if (rgb.length / 3 != pixels) {
            throw new IllegalArgumentException("Invalid amount of pixels! required " + pixels + ", found " + (rgb.length / 4));
        }

        this.rgbe = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = rgb[0 + (x * 3) + (y * width * 3)];
                float g = rgb[1 + (x * 3) + (y * width * 3)];
                float b = rgb[2 + (x * 3) + (y * width * 3)];

                encodeTo(r, g, b, (x * 4) + (y * width * 4), this.rgbe);
            }
        }

        this.width = width;
        this.height = height;
    }
    
    public byte[] getRGBE() {
        return rgbe;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void read(int x, int y, Vector3f outColor) {
        int index = (x * 4) + (y * this.width * 4);

        int mR = this.rgbe[0 + index] & 0xFF;
        int mG = this.rgbe[1 + index] & 0xFF;
        int mB = this.rgbe[2 + index] & 0xFF;
        int exp = this.rgbe[3 + index] & 0xFF;
        
        outColor.set(mR, mG, mB)
                .div(255f)
                .mul(LOOKUP_TABLE[exp])
                ;
    }
    
    public void write(int x, int y, Vector3fc inColor) {
        encodeTo(inColor.x(), inColor.y(), inColor.z(),
                (x * 4) + (y * this.width * 4),
                this.rgbe
        );
    }
    
    public float[] toFloatArray() {
        Vector3f color = new Vector3f();

        float[] array = new float[this.width * this.height * 3];

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                read(x, y, color);

                array[0 + (x * 3) + (y * this.width * 3)] = color.x();
                array[1 + (x * 3) + (y * this.width * 3)] = color.y();
                array[2 + (x * 3) + (y * this.width * 3)] = color.z();
            }
        }

        return array;
    }
    
    public BufferedImage toBufferedImage() {
        return RGBA8Image.toBufferedImage(this.rgbe, this.width, this.height);
    }
    
}
