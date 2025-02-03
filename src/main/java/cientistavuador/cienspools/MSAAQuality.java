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
package cientistavuador.cienspools;

import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public enum MSAAQuality {
    OFF_1X(1, checkSupport(1)),
    LOW_2X(2, checkSupport(2)),
    MEDIUM_4X(4, checkSupport(4)),
    HIGH_8X(8, checkSupport(8)),
    ULTRA_16X(16, checkSupport(16));
    
    private static boolean checkSupport(int samples) {
        if (samples <= 1) {
            return true;
        }
        int maxSamples = Math.min(
                Math.min(glGetInteger(GL_MAX_SAMPLES), glGetInteger(GL_MAX_INTEGER_SAMPLES)),
                Math.min(glGetInteger(GL_MAX_COLOR_TEXTURE_SAMPLES), glGetInteger(GL_MAX_DEPTH_TEXTURE_SAMPLES))
        );
        return samples <= maxSamples;
    }
    
    private final int samples;
    private final boolean supported;
    
    private MSAAQuality(int samples, boolean supported) {
        this.samples = samples;
        this.supported = supported;
    }

    public int getSamples() {
        return samples;
    }
    
    public boolean isSupported() {
        return supported;
    }
    
    public static MSAAQuality next(MSAAQuality quality) {
        switch (quality) {
            default -> {
                return LOW_2X;
            }
            case LOW_2X -> {
                return MEDIUM_4X;
            }
            case MEDIUM_4X -> {
                return HIGH_8X;
            }
            case HIGH_8X -> {
                return ULTRA_16X;
            }
            case ULTRA_16X -> {
                return OFF_1X;
            }
        }
    }
    
    public static MSAAQuality nextSupported(MSAAQuality quality) {
        MSAAQuality next = next(quality);
        if (!next.isSupported()) {
            next = MSAAQuality.OFF_1X;
        }
        return next;
    }
    
    public static void init() {
        
    }
}
