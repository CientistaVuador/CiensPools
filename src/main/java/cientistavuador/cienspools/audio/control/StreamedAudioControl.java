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
package cientistavuador.cienspools.audio.control;

import cientistavuador.cienspools.audio.AudioNode;
import cientistavuador.cienspools.audio.data.AudioStream;
import cientistavuador.cienspools.audio.data.StreamedAudio;
import java.util.Objects;
import static org.lwjgl.openal.AL11.*;

/**
 *
 * @author Cien
 */
public class StreamedAudioControl implements AudioControl {

    private final AudioNode node;
    private final StreamedAudio audio;
    private final AudioStream stream;

    public StreamedAudioControl(AudioNode node, StreamedAudio audio) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(audio, "audio is null");
        this.node = node;
        this.audio = audio;
        this.stream = audio.openNewStream();
    }

    public AudioNode getNode() {
        return node;
    }

    public StreamedAudio getAudio() {
        return audio;
    }

    @Override
    public boolean isLooping() {
        return this.stream.isLooping();
    }

    @Override
    public void setLooping(boolean looping) {
        this.stream.setLooping(looping);
    }

    @Override
    public void play() {
        if (this.stream.isClosed()) {
            return;
        }
        
        if (!this.stream.isStarted()) {
            this.stream.start();
        } else if (!isPaused() || !this.stream.isPlaying()) {
            seek(0f);
            alSourcePlay(getNode().source());
            return;
        }
        
        if (!isPlaying()) {
            alSourcePlay(getNode().source());
        }
    }

    @Override
    public boolean isPlaying() {
        return alGetSourcei(getNode().source(), AL_SOURCE_STATE) == AL_PLAYING;
    }

    @Override
    public void seek(float length) {
        this.stream.seek((int) (length * this.stream.getSampleRate()));
    }

    @Override
    public float elapsed() {
        return this.stream.getCurrentSample() / ((float)this.stream.getSampleRate());
    }

    @Override
    public float length() {
        return this.audio.getLength();
    }

    @Override
    public void pause() {
        if (this.stream.isClosed()) {
            return;
        }
        alSourcePause(getNode().source());
    }

    @Override
    public boolean isPaused() {
        return alGetSourcei(getNode().source(), AL_SOURCE_STATE) == AL_PAUSED;
    }

    private void returnProcessedBuffers() {
        int processed = alGetSourcei(getNode().source(), AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int unqueued = alSourceUnqueueBuffers(getNode().source());
            if (unqueued != 0) {
                this.stream.returnBuffer(unqueued);
            }
        }
    }

    @Override
    public void stop() {
        if (this.stream.isClosed()) {
            return;
        }
        try (this.stream) {
            alSourceStop(getNode().source());
            returnProcessedBuffers();
        }
    }

    @Override
    public void update() {
        if (this.stream.isClosed()) {
            return;
        }

        if (this.stream.isStarted()) {
            int toQueue = Math.max(AudioStream.IDEAL_NUMBER_OF_BUFFERS
                    - alGetSourcei(getNode().source(), AL_BUFFERS_QUEUED), 0);
            for (int i = 0; i < toQueue; i++) {
                int buffer = this.stream.nextBuffer();
                if (buffer != 0) {
                    alSourceQueueBuffers(getNode().source(), buffer);
                }
            }
            
            if (alGetSourcei(getNode().source(), AL_SOURCE_STATE) == AL_STOPPED
                    && this.stream.isPlaying()
                ) {
                alSourcePlay(getNode().source());
            }

            returnProcessedBuffers();
        }
    }

}
