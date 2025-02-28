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

import cientistavuador.cienspools.util.PixelUtils.PixelStructure;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class MipmapUtils {
    
    public static int mipmapSize(int size, int level) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(size >> level, 1);
    }
    
    public static int mipmapSize(int size) {
        return mipmapSize(size, 1);
    }
    
    public static int numberOfMipmaps(int width, int height) {
        int x = (width > height) ? width : height;
        return (31 - Integer.numberOfLeadingZeros(x)) + 1;
    }
    
    public static Pair<Pair<Integer, Integer>, byte[]> mipmap(
            byte[] data,
            int width, int height
    ) {
        ImageUtils.validate(data, width, height, 4);
        
        int mipWidth = mipmapSize(width);
        int mipHeight = mipmapSize(height);
        
        PixelStructure inSt = PixelUtils.getPixelStructure(width, height, 4, true);
        PixelStructure outSt = PixelUtils.getPixelStructure(mipWidth, mipHeight, 4, true);
        
        byte[] outMipmap = new byte[mipWidth * mipHeight * 4];
        
        for (int y = 0; y < mipHeight; y++) {
            for (int x = 0; x < mipWidth; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int alpha = 0;
                
                for (int yOffset = 0; yOffset < 2; yOffset++) {
                    for (int xOffset = 0; xOffset < 2; xOffset++) {
                        int totalX = (x * 2) + xOffset;
                        int totalY = (y * 2) + yOffset;
                        
                        red += data[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 0)] & 0xFF;
                        green += data[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 1)] & 0xFF;
                        blue += data[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 2)] & 0xFF;
                        alpha += data[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 3)] & 0xFF;
                    }
                }
                
                red /= 4;
                green /= 4;
                blue /= 4;
                alpha /= 4;
                
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 0)] = (byte) red;
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 1)] = (byte) green;
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 2)] = (byte) blue;
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 3)] = (byte) alpha;
            }
        }
        
        return new Pair<>(
                new Pair<>(outSt.width(), outSt.height()),
                outMipmap
        );
    }
    
    public static Pair<Pair<Integer, Integer>, float[]> mipmapHDR(
            float[] rgb,
            int width, int height
    ) {
        Objects.requireNonNull(rgb, "rgb is null.");
        ImageUtils.validate(rgb.length, width, height, 3);
        
        int mipWidth = mipmapSize(width);
        int mipHeight = mipmapSize(height);
        
        PixelStructure inSt = PixelUtils.getPixelStructure(width, height, 3, true);
        PixelStructure outSt = PixelUtils.getPixelStructure(mipWidth, mipHeight, 3, true);
        
        float[] outMipmap = new float[mipWidth * mipHeight * 3];
        
        for (int y = 0; y < mipHeight; y++) {
            for (int x = 0; x < mipWidth; x++) {
                float red = 0f;
                float green = 0f;
                float blue = 0f;
                
                for (int yOffset = 0; yOffset < 2; yOffset++) {
                    for (int xOffset = 0; xOffset < 2; xOffset++) {
                        int totalX = (x * 2) + xOffset;
                        int totalY = (y * 2) + yOffset;
                        
                        red += rgb[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 0)];
                        green += rgb[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 1)];
                        blue += rgb[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 2)];
                    }
                }
                
                red /= 4;
                green /= 4;
                blue /= 4;
                
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 0)] = red;
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 1)] = green;
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 2)] = blue;
            }
        }
        
        return new Pair<>(
                new Pair<>(outSt.width(), outSt.height()),
                outMipmap
        );
    }
    
    private MipmapUtils() {
        
    }
    
}
