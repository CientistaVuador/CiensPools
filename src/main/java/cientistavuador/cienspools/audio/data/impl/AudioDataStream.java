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
package cientistavuador.cienspools.audio.data.impl;

import cientistavuador.cienspools.util.ObjectCleaner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Objects;
import org.lwjgl.PointerBuffer;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.stb.STBVorbis.*;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

/**
 *
 * @author Cien
 */
public class AudioDataStream implements AutoCloseable {

    private final InputStream in;

    private boolean started = false;
    private boolean closed = false;

    private int channels = -1;
    private int sampleRate = -1;
    private int samplesRead = 0;

    private static class WrappedDataStreamBuffer {

        final byte[] readBuffer = new byte[8192];
        long decoder = 0;
        ByteBuffer nativeBuffer = memAlloc(8192);
        int nativeBufferPosition = 0;
    }

    private final WrappedDataStreamBuffer data = new WrappedDataStreamBuffer();

    public AudioDataStream(InputStream stream) {
        Objects.requireNonNull(stream, "Stream is null");
        this.in = stream;

        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedDataStreamBuffer finalData = this.data;
        final InputStream finalIn = this.in;

        ObjectCleaner.get().register(this, () -> {
            if (finalData.decoder != 0) {
                stb_vorbis_close(finalData.decoder);
            }
            finalData.decoder = 0;

            if (finalData.nativeBuffer != null) {
                memFree(finalData.nativeBuffer);
            }
            finalData.nativeBuffer = null;

            try {
                finalIn.close();
            } catch (IOException ex) {}
        });
    }

    public int getChannels() {
        return this.channels;
    }

    public int getSampleRate() {
        return this.sampleRate;
    }

    public int getSamplesRead() {
        return this.samplesRead;
    }
    
    private boolean readData() throws IOException {
        int read = this.in.read(this.data.readBuffer);
        if (read == -1) {
            return false;
        }
        int freeSpace = this.data.nativeBuffer.capacity() - this.data.nativeBufferPosition;
        if (freeSpace < read) {
            ByteBuffer newBuffer
                    = memAlloc((this.data.nativeBuffer.capacity() * 2) + (read - freeSpace));
            try {
                newBuffer.put(0, this.data.nativeBuffer, 0, this.data.nativeBufferPosition);
            } finally {
                memFree(this.data.nativeBuffer);
            }
            this.data.nativeBuffer = newBuffer;
        }
        this.data.nativeBuffer
                .put(this.data.nativeBufferPosition, this.data.readBuffer, 0, read);
        this.data.nativeBufferPosition += read;
        return true;
    }

    private void consume(int bytes) {
        byte[] copy = new byte[this.data.nativeBufferPosition - bytes];
        this.data.nativeBuffer.get(bytes, copy);
        this.data.nativeBuffer.put(0, copy, 0, copy.length);
        this.data.nativeBufferPosition -= bytes;
    }

    public void start() throws IOException {
        if (this.started) {
            return;
        }
        if (this.closed) {
            throw new IOException("Closed");
        }

        long decoder = 0;
        while (true) {
            if (!readData()) {
                throw new IOException("Unexpected end of stream");
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer consumedBuffer = stack.mallocInt(1);
                IntBuffer errorBuffer = stack.mallocInt(1);
                decoder = stb_vorbis_open_pushdata(
                        this.data.nativeBuffer.slice(0, this.data.nativeBufferPosition),
                        consumedBuffer, errorBuffer, null);
                int error = errorBuffer.get();
                if (error == VORBIS_need_more_data) {
                    continue;
                }
                if (error != VORBIS__no_error) {
                    throw new IOException("STB Vorbis Decode Error: " + error);
                }

                int consumed = consumedBuffer.get();
                if (consumed != 0) {
                    consume(consumed);
                }

                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                stb_vorbis_get_info(decoder, info);
                this.channels = info.channels();
                this.sampleRate = info.sample_rate();
            }

            break;
        }

        this.data.decoder = decoder;
        this.started = true;
    }
    
    public short[] readSamples() throws IOException {
        if (!this.started) {
            throw new IOException("Not started");
        }
        if (this.closed) {
            throw new IOException("Closed");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer outputBuffer = stack.mallocPointer(1);
            IntBuffer numberOfSamplesBuffer = stack.mallocInt(1);
            int bytesRead = stb_vorbis_decode_frame_pushdata(this.data.decoder,
                    this.data.nativeBuffer.slice(0, this.data.nativeBufferPosition),
                    null, outputBuffer, numberOfSamplesBuffer);
            int numberOfSamples = numberOfSamplesBuffer.get();
            
            int error = stb_vorbis_get_error(this.data.decoder);
            if (error != VORBIS__no_error && error != VORBIS_need_more_data) {
                throw new IOException("STB Vorbis Decode Error: " + error);
            }
            
            if (bytesRead != 0) {
                consume(bytesRead);
            }
            
            if (numberOfSamples == 0) {
                if (bytesRead == 0) {
                    if (!readData()) {
                        return null;
                    }
                }
                return new short[0];
            }
            
            PointerBuffer channelsPointers = outputBuffer.getPointerBuffer(getChannels());
            short[] output = new short[numberOfSamples * getChannels()];
            for (int channel = 0; channel < getChannels(); channel++) {
                FloatBuffer channelBuffer = channelsPointers.getFloatBuffer(channel, numberOfSamples);
                for (int sampleIndex = 0; sampleIndex < numberOfSamples; sampleIndex++) {
                    float sampleFloat = ((channelBuffer.get(sampleIndex) + 1f) / 2f) * 65535f;
                    int sample = Math.min(Math.max(((int)sampleFloat), 0), 65535) - 32768;
                    output[(sampleIndex * getChannels()) + channel] = (short) sample;
                }
            }
            
            this.samplesRead += output.length;
            return output;
        }
    }
    
    public int skipSamples(int samples) throws IOException {
        int read = 0;
        while (read < samples) {
            short[] samplesData = readSamples();
            if (samplesData == null) {
                if (read == 0) {
                    return -1;
                }
                break;
            }
            read += samplesData.length;
        }
        return read;
    }
    
    public short[] readSamples(int samples) throws IOException {
        short[] buffer = new short[8192];
        int bufferPosition = 0;
        while (bufferPosition < samples) {
            short[] samplesData = readSamples();
            if (samplesData == null) {
                if (bufferPosition == 0) {
                    return null;
                }
                break;
            }
            int freeSpace = buffer.length - bufferPosition;
            if (freeSpace < samplesData.length) {
                buffer = Arrays.copyOf(buffer, 
                        (buffer.length * 2) + (samplesData.length - freeSpace));
            }
            System.arraycopy(samplesData, 0, buffer, bufferPosition, samplesData.length);
            bufferPosition += samplesData.length;
        }
        return Arrays.copyOf(buffer, bufferPosition);
    }
    
    @Override
    public void close() throws IOException {
        if (this.closed) {
            throw new IOException("Closed");
        }

        final WrappedDataStreamBuffer finalData = this.data;
        final InputStream finalIn = this.in;

        if (finalData.decoder != 0) {
            stb_vorbis_close(finalData.decoder);
        }
        finalData.decoder = 0;

        if (finalData.nativeBuffer != null) {
            memFree(finalData.nativeBuffer);
        }
        finalData.nativeBuffer = null;

        this.closed = true;

        try {
            finalIn.close();
        } catch (IOException ex) {}
    }
    
}
