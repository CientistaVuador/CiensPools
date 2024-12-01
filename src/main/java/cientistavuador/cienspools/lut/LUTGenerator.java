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
package cientistavuador.cienspools.lut;

import cientistavuador.cienspools.util.RGBA8Image;

/**
 *
 * @author Cien
 */
public class LUTGenerator {

    public static final int DEFAULT_SIZE = 24;
    
    public static RGBA8Image generate(int size) {
        RGBA8Image img = new RGBA8Image(size, size * size);
        for (int r = 0; r < size; r++) {
            for (int g = 0; g < size; g++) {
                for (int b = 0; b < size; b++) {
                    int red = Math.min(Math.round(((r + 0.5f) / (size - 1)) * 255f), 255);
                    int green = Math.min(Math.round(((g + 0.5f) / (size - 1)) * 255f), 255);
                    int blue = Math.min(Math.round(((b + 0.5f) / (size - 1)) * 255f), 255);
                    img.write(r, g + (b * size), red, green, blue, 255);
                }
            }
        }
        return img;
    }
    
    public static RGBA8Image generate() {
        return generate(DEFAULT_SIZE);
    }

    private LUTGenerator() {

    }
}
