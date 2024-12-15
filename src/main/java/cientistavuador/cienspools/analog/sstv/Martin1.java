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
package cientistavuador.cienspools.analog.sstv;

import cientistavuador.cienspools.analog.FrequencyModulator;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class Martin1 extends FrequencyModulator {

    public Martin1(OutputStream output) {
        super(output, 1100f, 2300f, 11025, 4);
    }

    private void toggleDigitalVis() throws IOException {
        int ms30 = (int) (getInternalSampleRate() * 0.03);
        for (int i = 0; i < ms30; i++) {
            writeFrequencyAsSample(1200f);
        }
    }
    
    private void writeBit(int bit) throws IOException {
        int ms30 = (int) (getInternalSampleRate() * 0.03);
        float frequency = (bit != 0 ? 1100f : 1300f);
        for (int i = 0; i < ms30; i++) {
            writeFrequencyAsSample(frequency);
        }
    }
    
    private void writeHeader() throws IOException {
        int ms300 = (int) (getInternalSampleRate() * 0.3);
        int ms10 = (int) (getInternalSampleRate() * 0.01);

        for (int i = 0; i < ms300; i++) {
            writeFrequencyAsSample(1900f);
        }
        for (int i = 0; i < ms10; i++) {
            writeFrequencyAsSample(1200f);
        }
        for (int i = 0; i < ms300; i++) {
            writeFrequencyAsSample(1900f);
        }
        
        toggleDigitalVis();
        
        writeBit(0);
        writeBit(0);
        writeBit(1);
        writeBit(1);
        writeBit(0);
        writeBit(1);
        writeBit(0);
        
        writeBit(1);
        
        toggleDigitalVis();
    }

    private void writeImageLine(BufferedImage image, int y, int bitshift, int length) throws IOException {
        for (int s = 0; s < length; s++) {
            int x = (int) ((((float) s) / (length - 1)) * (image.getWidth() - 1));
            float color = ((image.getRGB(x, y) >> bitshift) & 0xFF) / 255f;
            float signal = (color * 2f) - 1f;
            float frequency = 1900f + (signal * 400f);
            writeFrequencyAsSample(frequency);
        }
    }

    private void writeGap() throws IOException {
        int gapLength = (int) (0.000572 * getInternalSampleRate());
        for (int i = 0; i < gapLength; i++) {
            writeFrequencyAsSample(1500f);
        }
    }
    
    public void writeImage(BufferedImage image) throws IOException {
        Objects.requireNonNull(image, "image is null");
        final int width = 320;
        final int height = 256;
        if (image.getWidth() != width) {
            throw new IllegalArgumentException("image width must be 320");
        }
        if (image.getHeight() != height) {
            throw new IllegalArgumentException("image height must be 256");
        }

        writeHeader();

        int syncLength = (int) (0.004862 * getInternalSampleRate());
        int colorLength = (int) (0.146432 * getInternalSampleRate());

        for (int y = 0; y < height; y++) {
            //sync
            for (int i = 0; i < syncLength; i++) {
                writeFrequencyAsSample(1200f);
            }
            
            writeGap();
            
            //green
            writeImageLine(image, y, 8, colorLength);
            writeGap();
            
            //blue
            writeImageLine(image, y, 0, colorLength);
            writeGap();

            //red
            writeImageLine(image, y, 16, colorLength);
            writeGap();
        }
    }
}
