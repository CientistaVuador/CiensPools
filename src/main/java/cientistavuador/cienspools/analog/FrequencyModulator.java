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
package cientistavuador.cienspools.analog;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class FrequencyModulator {

    private final OutputStream output;
    private final float minFrequency;
    private final float maxFrequency;
    private final int internalSampleRate;
    private final int externalSampleRateMultiplier;
    
    private double time = 0.0;
    private float lastSample = Float.NaN;
    private long numberOfSamples = 0;
    
    public FrequencyModulator(
            OutputStream output,
            float minFrequency, float maxFrequency,
            int internalSampleRate, int externalSampleRateMultipler
    ) {
        Objects.requireNonNull(output, "output is null");
        if (minFrequency <= 0) {
            throw new IllegalArgumentException("min frequency <= 0");
        }
        if (maxFrequency <= 0) {
            throw new IllegalArgumentException("max frequency <= 0");
        }
        if (minFrequency > maxFrequency) {
            throw new IllegalArgumentException("min frequency > max frequency");
        }
        if (internalSampleRate <= 0) {
            throw new IllegalArgumentException("internal sample rate <= 0");
        }
        if (externalSampleRateMultipler <= 0) {
            throw new IllegalArgumentException("external sample rate multiplier <= 0");
        }
        this.output = output;
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
        this.internalSampleRate = internalSampleRate;
        this.externalSampleRateMultiplier = externalSampleRateMultipler;
    }

    public OutputStream getOutput() {
        return output;
    }

    public float getMinFrequency() {
        return minFrequency;
    }

    public float getMaxFrequency() {
        return maxFrequency;
    }
    
    public float getCarrierFrequency() {
        return (getMinFrequency() + getMaxFrequency()) * 0.5f;
    }
    
    public float getFrequencyDeviation() {
        return (getMaxFrequency() - getMinFrequency()) * 0.5f;
    }

    public int getInternalSampleRate() {
        return internalSampleRate;
    }
    
    public int getExternalSampleRateMultiplier() {
        return externalSampleRateMultiplier;
    }
    
    public int getExternalSampleRate() {
        return getInternalSampleRate() * getExternalSampleRateMultiplier();
    }

    public long getNumberOfSamples() {
        return numberOfSamples;
    }
    
    public void writeSample(float sample) throws IOException {
        this.numberOfSamples++;
        
        sample = Math.min(Math.max(sample, -1f), 1f);
        if (!Float.isFinite(this.lastSample)) {
            this.lastSample = sample;
            return;
        }
        
        byte[] outputArray = new byte[getExternalSampleRateMultiplier() * Short.BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(outputArray).order(ByteOrder.LITTLE_ENDIAN);
        
        final double timeStep = 1.0 / getExternalSampleRate();
        final float smoothing = 0.3f;
        final int multiplier = getExternalSampleRateMultiplier();
        
        float a = this.lastSample;
        float b = sample;
        
        this.lastSample = sample;
        
        for (int i = 0; i < multiplier; i++) {
            //upscaling with gaussian
            float c;
            if (multiplier != 1) {
                c = ((float)i) / (multiplier - 1);
            } else {
                c = 0;
            }
            c = (float) (1f - Math.exp(-((c * c) / (2f * smoothing * smoothing))));
            float upsample = (a * (1f - c)) + (b * c);
            
            //modulation
            float frequency = getCarrierFrequency() + (getFrequencyDeviation() * upsample);
            float timeDilation = frequency / getCarrierFrequency();
            
            upsample = (float) Math.cos(Math.PI * 2.0 * getCarrierFrequency() * this.time);
            this.time += timeStep * timeDilation;
            
            //to 16 bits
            upsample = (upsample * 0.5f) + 0.5f;
            upsample = (upsample * 65535f) - 32768f;
            int upsample16Bits = Math.min(Math.max((int)upsample, Short.MIN_VALUE), Short.MAX_VALUE);
            
            //output
            buffer.putShort((short) upsample16Bits);
        }
        
        this.output.write(outputArray);
    }
    
    public void writeFrequencyAsSample(float frequency) throws IOException {
        float sample = (((frequency - getMinFrequency()) / (getFrequencyDeviation() * 2f)) * 2f) - 1f;
        writeSample(sample);
    }
    
}
