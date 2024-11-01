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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.joml.Vector4f;
import static org.lwjgl.stb.STBImage.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public class RGBA8Image {

    public static RGBA8Image ensureSize(RGBA8Image img, int width, int height) {
        if (img == null) {
            return null;
        }
        if (img.getWidth() != width || img.getHeight() != height) {
            return img.nearestResize(width, height);
        }
        return img;
    }
    
    public static int maxWidth(RGBA8Image... images) {
        int w = -1;
        for (RGBA8Image i:images) {
            if (i == null) {
                continue;
            }
            w = Math.max(i.getWidth(), w);
        }
        return w;
    }
    
    public static int maxHeight(RGBA8Image... images) {
        int h = -1;
        for (RGBA8Image i:images) {
            if (i == null) {
                continue;
            }
            h = Math.max(i.getHeight(), h);
        }
        return h;
    }
    
    public static void validateSize(RGBA8Image img, int expectedWidth, int expectedHeight) {
        Objects.requireNonNull(img, "Img is null");
        if (img.getWidth() != expectedWidth) {
            throw new IllegalArgumentException("Expected width "+expectedWidth+", found "+img.getWidth());
        }
        if (img.getHeight() != expectedHeight) {
            throw new IllegalArgumentException("Expected height "+expectedHeight+", found "+img.getHeight());
        }
    }
    
    public static RGBA8Image ofSameSize(int defaultWidth, int defaultHeight, RGBA8Image... images) {
        int width = -1;
        int height = -1;
        for (RGBA8Image img : images) {
            if (img == null) {
                continue;
            }
            if (width == -1) {
                width = img.getWidth();
            }
            if (height == -1) {
                height = img.getHeight();
            }
            validateSize(img, width, height);
        }
        if (width == -1 || height == -1) {
            width = defaultWidth;
            height = defaultHeight;
        }
        return new RGBA8Image(width, height);
    }

    public static BufferedImage toBufferedImage(byte[] rgba, int width, int height) {
        ImageUtils.validate(rgba, width, height, 4);

        BufferedImage resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < resultImage.getHeight(); y++) {
            for (int x = 0; x < resultImage.getWidth(); x++) {
                int r = rgba[0 + (x * 4) + (y * resultImage.getWidth() * 4)] & 0xFF;
                int g = rgba[1 + (x * 4) + (y * resultImage.getWidth() * 4)] & 0xFF;
                int b = rgba[2 + (x * 4) + (y * resultImage.getWidth() * 4)] & 0xFF;
                int e = rgba[3 + (x * 4) + (y * resultImage.getWidth() * 4)] & 0xFF;

                int argb = (e << 24) | (r << 16) | (g << 8) | (b << 0);

                resultImage.setRGB(x, (resultImage.getHeight() - 1) - y, argb);
            }
        }
        return resultImage;
    }

    public static byte[] toPNG(byte[] rgba, int width, int height) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        try {
            ImageIO.write(toBufferedImage(rgba, width, height), "PNG", byteArray);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return byteArray.toByteArray();
    }

    public static RGBA8Image fromPNG(byte[] png) {
        Objects.requireNonNull(png, "PNG is null.");

        stbi_set_flip_vertically_on_load_thread(1);
        ByteBuffer nativePng = memAlloc(png.length).put(png).flip();
        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.callocInt(1);
                IntBuffer height = stack.callocInt(1);

                ByteBuffer data = stbi_load_from_memory(nativePng, width, height, stack.callocInt(1), 4);
                if (data == null) {
                    throw new RuntimeException("Failed to Load Image: " + stbi_failure_reason());
                }
                try {
                    byte[] rgba = new byte[data.capacity()];
                    data.get(rgba);
                    return new RGBA8Image(rgba, width.get(), height.get());
                } finally {
                    stbi_image_free(data.position(0));
                }
            }
        } finally {
            memFree(nativePng);
        }
    }

    private final byte[] rgba;
    private final int width;
    private final int height;

    public RGBA8Image(byte[] rgba, int width, int height) {
        ImageUtils.validate(rgba, width, height, 4);

        this.rgba = rgba;
        this.width = width;
        this.height = height;
    }

    public RGBA8Image(int width, int height) {
        this(new byte[width * height * 4], width, height);
    }

    public byte[] getRGBA() {
        return rgba;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int sample(int x, int y, int component) {
        return this.rgba[component + (x * 4) + (y * this.width * 4)] & 0xFF;
    }

    public void sample(int x, int y, Vector4f out) {
        out.set(
                sample(x, y, 0),
                sample(x, y, 1),
                sample(x, y, 2),
                sample(x, y, 3)
        ).div(255f);
    }

    public void write(int x, int y, int component, int value) {
        this.rgba[component + (x * 4) + (y * this.width * 4)] = (byte) value;
    }

    public void write(int x, int y, int r, int g, int b, int a) {
        write(x, y, 0, r);
        write(x, y, 1, g);
        write(x, y, 2, b);
        write(x, y, 3, a);
    }

    private int convert(float e) {
        return Math.min(Math.max(Math.round(e * 255f), 0), 255);
    }

    public void write(int x, int y, float r, float g, float b, float a) {
        write(x, y, convert(r), convert(g), convert(b), convert(a));
    }

    public void write(int x, int y, Vector4f color) {
        write(x, y, color.x(), color.y(), color.z(), color.w());
    }

    public byte[] toPNG() {
        return toPNG(this.rgba, this.width, this.height);
    }

    public RGBA8Image mipmap() {
        int newWidth = MipmapUtils.mipmapSize(this.width);
        int newHeight = MipmapUtils.mipmapSize(this.height);

        if (newWidth == this.width && newHeight == this.height) {
            return this;
        }

        Vector4f color = new Vector4f();

        RGBA8Image mip = new RGBA8Image(newWidth, newHeight);
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                float r = 0f;
                float g = 0f;
                float b = 0f;
                float a = 0f;

                for (int yOffset = 0; yOffset < 2; yOffset++) {
                    for (int xOffset = 0; xOffset < 2; xOffset++) {
                        int trueX = (x * 2) + xOffset;
                        int trueY = (y * 2) + yOffset;
                        trueX = Math.min(trueX, this.width - 1);
                        trueY = Math.min(trueY, this.height - 1);

                        sample(trueX, trueY, color);

                        r += color.x();
                        g += color.y();
                        b += color.z();
                        a += color.w();
                    }
                }

                r /= 4f;
                g /= 4f;
                b /= 4f;
                a /= 4f;

                mip.write(x, y, r, g, b, a);
            }
        }

        return mip;
    }
    
    public void fill(int red, int green, int blue, int alpha) {
        byte[] data = {
            (byte) red, (byte) green, (byte) blue, (byte) alpha
        };
        for (int i = 0; i < this.rgba.length; i += 4) {
            System.arraycopy(data, 0, this.rgba, i, data.length);
        }
    }
    
    public void copyChannelOf(RGBA8Image other, int toReadFrom, int toWriteInto) {
        validateSize(other, getWidth(), getHeight());
        byte[] otherData = other.getRGBA();
        for (int i = 0; i < this.rgba.length; i += 4) {
            this.rgba[i + toWriteInto] = otherData[i + toReadFrom];
        }
    }
    
    public RGBA8Image copy() {
        return new RGBA8Image(getRGBA().clone(), getWidth(), getHeight());
    }
    
    public RGBA8Image nearestResize(int newWidth, int newHeight) {
        RGBA8Image copy = new RGBA8Image(newWidth, newHeight);
        for (int y = 0; y < copy.getHeight(); y++) {
            for (int x = 0; x < copy.getWidth(); x++) {
                float u = ((x + 0.5f) / copy.getWidth());
                float v = ((y + 0.5f) / copy.getHeight());
                
                int nx = (int) Math.floor(u * getWidth());
                int ny = (int) Math.floor(v * getHeight());
                
                int r = sample(nx, ny, 0);
                int g = sample(nx, ny, 1);
                int b = sample(nx, ny, 2);
                int a = sample(nx, ny, 3);
                
                copy.write(x, y, r, g, b, a);
            }
        }
        return copy;
    }
}
