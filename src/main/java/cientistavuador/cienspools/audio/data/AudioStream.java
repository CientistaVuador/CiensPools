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
package cientistavuador.cienspools.audio.data;

import cientistavuador.cienspools.audio.data.impl.AudioStreamImpl;

/**
 *
 * @author Cien
 */
public interface AudioStream extends AutoCloseable {

    public static class AudioStreamException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public AudioStreamException(Throwable t) {
            super(t);
        }
    }

    public static final float IDEAL_BUFFERED_LENGTH = 1f;
    public static final int IDEAL_NUMBER_OF_BUFFERS = 8;
    public static final float IDEAL_LENGTH_PER_BUFFER
            = IDEAL_BUFFERED_LENGTH / IDEAL_NUMBER_OF_BUFFERS;

    public static AudioStream newAudioStream(InputStreamFactory factory) {
        return new AudioStreamImpl(factory);
    }

    public InputStreamFactory getInputStreamFactory();

    public void start();

    public boolean isStarted();

    public void join();

    public int getChannels();

    public int getSampleRate();

    public int getCurrentSample();

    public void seek(int sample);

    public boolean isLooping();

    public void setLooping(boolean looping);

    public Throwable getThrowable();

    public default void throwException() {
        Throwable t = getThrowable();
        if (t != null) {
            throw new AudioStreamException(t);
        }
    }
    
    public int nextBuffer();

    public void returnBuffer(int buffer);

    public boolean isClosed();

}