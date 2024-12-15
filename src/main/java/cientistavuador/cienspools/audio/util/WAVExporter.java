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
package cientistavuador.cienspools.audio.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class WAVExporter {

    public static void write(short[] samples, int sampleRate, OutputStream out)
            throws IOException {
        Objects.requireNonNull(samples, "samples is null");
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sample rate <= 0");
        }
        Objects.requireNonNull(out, "out is null");

        int numberOfChannels = 1;
        int bitsPerSample = Short.SIZE;
        int bytesPerBlock = (numberOfChannels * bitsPerSample) / 8;
        int bytesPerSec = sampleRate * bytesPerBlock;

        byte[] header = new byte[44];
        ByteBuffer
                .wrap(header)
                .order(ByteOrder.LITTLE_ENDIAN)
                //RIFF Chunk
                .put("RIFF".getBytes(StandardCharsets.US_ASCII))
                .putInt((44 + samples.length * Short.BYTES) - 8)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                //Data format
                .put("fmt ".getBytes(StandardCharsets.US_ASCII))
                .putInt(16)
                .putShort((short) 1)
                .putShort((short) numberOfChannels)
                .putInt(sampleRate)
                .putInt(bytesPerSec)
                .putShort((short) bytesPerBlock)
                .putShort((short) bitsPerSample)
                //Sampled data
                .put("data".getBytes(StandardCharsets.US_ASCII))
                .putInt(samples.length * Short.BYTES);
        out.write(header);
        
        for (short sample:samples) {
            int sampleInt = Short.toUnsignedInt(sample);
            out.write((byte) sampleInt);
            out.write((byte) (sampleInt >> 8));
        }
    }

    private WAVExporter() {

    }

}
