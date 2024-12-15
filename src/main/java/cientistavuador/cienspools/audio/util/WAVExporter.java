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
