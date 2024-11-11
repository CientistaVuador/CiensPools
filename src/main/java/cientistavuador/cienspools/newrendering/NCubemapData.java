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
package cientistavuador.cienspools.newrendering;

import cientistavuador.cienspools.util.DXT5TextureStore.DXT5Texture;
import cientistavuador.cienspools.util.E8Image;
import java.lang.ref.WeakReference;
import java.util.Objects;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class NCubemapData {

    public static void calculateColor(DXT5Texture side, Vector3f outColor) {
        Objects.requireNonNull(side, "Side is null.");
        if (side.width() == 0 || side.height() == 0) {
            outColor.set(0f, 0f, 0f);
            return;
        }

        E8Image img = new E8Image(side.decompress(), side.width(), side.height());

        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.read(x, y, outColor);

                r += outColor.x();
                g += outColor.y();
                b += outColor.z();
            }
        }
        float inv = 1f / (img.getWidth() * img.getHeight());
        r *= inv;
        g *= inv;
        b *= inv;

        outColor.set(r, g, b);
    }

    public static final int SIDES = 6;

    public static final int POSITIVE_X = 0;
    public static final int NEGATIVE_X = 1;

    public static final int POSITIVE_Y = 2;
    public static final int NEGATIVE_Y = 3;

    public static final int POSITIVE_Z = 4;
    public static final int NEGATIVE_Z = 5;

    private final int size;
    private final DXT5Texture[] sides;
    private final Vector3f averageColor;

    @SuppressWarnings("unchecked")
    private final WeakReference<E8Image>[] sideTexturesData = new WeakReference[SIDES];

    public NCubemapData(
            DXT5Texture[] sides,
            Vector3fc averageColor
    ) {
        if (sides == null) {
            throw new NullPointerException("Sides textures is null.");
        }
        if (sides.length != SIDES) {
            throw new IllegalArgumentException("Sides textures length is not " + SIDES + ".");
        }

        int cubemapSize = -1;
        this.sides = new DXT5Texture[SIDES];
        for (int i = 0; i < sides.length; i++) {
            DXT5Texture texture = sides[i];
            if (texture == null) {
                throw new NullPointerException("Texture at index " + i + " is null.");
            }
            if (texture.width() != texture.height()) {
                throw new IllegalArgumentException("Texture at index " + i + " is not a square.");
            }
            if (cubemapSize == -1) {
                cubemapSize = texture.width();
            }
            if (texture.width() != cubemapSize) {
                throw new IllegalArgumentException("Texture at index "
                        + i + " does not have the same size, expected "
                        + cubemapSize + ", found " + texture.width());
            }
            this.sides[i] = texture;
        }
        this.size = cubemapSize;
        
        this.averageColor = new Vector3f();
        if (averageColor == null) {
            float r = 0f;
            float g = 0f;
            float b = 0f;
            for (DXT5Texture side : this.sides) {
                calculateColor(side, this.averageColor);
                r += this.averageColor.x();
                g += this.averageColor.y();
                b += this.averageColor.z();
            }
            float inv = 1f / SIDES;
            r *= inv;
            g *= inv;
            b *= inv;

            this.averageColor.set(r, g, b);
        } else {
            this.averageColor.set(averageColor);
        }
    }
    
    public NCubemapData(DXT5Texture[] sides) {
        this(sides, null);
    }

    public int getSize() {
        return this.size;
    }
    
    public int getNumberOfSides() {
        return this.sides.length;
    }
    
    public DXT5Texture getSide(int i) {
        return this.sides[i];
    }
    
    public E8Image getSideDecompressed(int index) {
        WeakReference<E8Image> reference = this.sideTexturesData[index];
        if (reference != null) {
            E8Image cached = reference.get();
            if (cached != null) {
                return cached;
            }
        }

        E8Image decompressed = new E8Image(this.sides[index].decompress(), this.size, this.size);
        this.sideTexturesData[index] = new WeakReference<>(decompressed);
        return decompressed;
    }

    public Vector3fc getAverageColor() {
        return this.averageColor;
    }
    
}
