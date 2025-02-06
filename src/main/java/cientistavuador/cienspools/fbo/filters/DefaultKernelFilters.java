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
package cientistavuador.cienspools.fbo.filters;

/**
 *
 * @author Cien
 */
public class DefaultKernelFilters {
    
    private static float[] normalize(float[] kernel) {
        kernel = kernel.clone();
        
        float totalSum = 0f;
        for (int i = 0; i < kernel.length; i++) {
            totalSum += kernel[i];
        }
        if (totalSum == 0f) {
            return new float[kernel.length];
        }
        
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= totalSum;
        }
        
        return kernel;
    }
    
    private static float[] add(float[] a, float[] b) {
        float[] result = new float[a.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = a[i] + b[i];
        }
        return result;
    }
    
    private static float[] subtract(float[] a, float[] b) {
        float[] result = new float[a.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = a[i] - b[i];
        }
        return result;
    }
    
    private static float[] multiply(float[] a, float scale) {
        float[] result = new float[a.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = a[i] * scale;
        }
        return result;
    }
    
    public static final float[] IDENTITY = new float[] {
        0f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 0f
    };
    
    public static final float[] BLUR_BOX = normalize(new float[] {
        1f, 1f, 1f,
        1f, 1f, 1f,
        1f, 1f, 1f
    });
    public static final float[] BLUR_GAUSSIAN = normalize(new float[] {
        1f, 2f, 1f,
        2f, 4f, 2f,
        1f, 2f, 1f
    });
    
    public static final float[] EDGE_DETECTION = new float[] {
        -1f, -1f, -1f,
        -1f, 8f, -1f,
        -1f, -1f, -1f
    };
    
    public static final float[] SHARPEN_LOW;
    public static final float[] SHARPEN_MEDIUM;
    public static final float[] SHARPEN_HIGH;
    public static final float[] SHARPEN_ULTRA;
    
    static {
        float[] sharpenMask = subtract(IDENTITY, BLUR_GAUSSIAN);
        SHARPEN_LOW = add(IDENTITY, multiply(sharpenMask, 0.5f));
        SHARPEN_MEDIUM = add(IDENTITY, multiply(sharpenMask, 1f));
        SHARPEN_HIGH = add(IDENTITY, multiply(sharpenMask, 1.5f));
        SHARPEN_ULTRA = add(IDENTITY, multiply(sharpenMask, 2f));
    }
    
    private DefaultKernelFilters() {
        
    }
}
