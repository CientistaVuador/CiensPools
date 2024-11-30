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
import cientistavuador.cienspools.audio.data.BufferedAudio;
import java.util.Objects;
import static org.lwjgl.openal.AL11.*;

/**
 *
 * @author Cien
 */
public class BufferedAudioControl implements AudioControl {

    private final AudioNode node;
    private final BufferedAudio audio;
    
    private boolean started = false;
    
    public BufferedAudioControl(AudioNode node, BufferedAudio audio) {
        Objects.requireNonNull(node, "node is null");
        Objects.requireNonNull(audio, "audio is null");
        this.node = node;
        this.audio = audio;
    }

    public AudioNode getNode() {
        return node;
    }

    public BufferedAudio getAudio() {
        return audio;
    }

    @Override
    public boolean isLooping() {
        return alGetSourcei(getNode().source(), AL_LOOPING) == AL_TRUE;
    }

    @Override
    public void setLooping(boolean looping) {
        alSourcei(getNode().source(), AL_LOOPING, (looping ? AL_TRUE : AL_FALSE));
    }

    @Override
    public void play() {
        if (!this.started) {
            this.started = true;
            alSourcei(getNode().source(), AL_BUFFER, getAudio().buffer());
        }
        alSourcePlay(getNode().source());
    }

    @Override
    public boolean isPlaying() {
        return alGetSourcei(getNode().source(), AL_SOURCE_STATE) == AL_PLAYING;
    }
    
    @Override
    public void seek(float length) {
        int sampleOffset = (int) (length * getAudio().getSampleRate());
        sampleOffset = Math.min(Math.max(sampleOffset, 0), getAudio().getLengthSamples() - 1);
        alSourcei(getNode().source(), AL_SAMPLE_OFFSET, sampleOffset);
    }

    @Override
    public float elapsed() {
        return alGetSourcei(getNode().source(), AL_SAMPLE_OFFSET) 
                / ((float)getAudio().getSampleRate());
    }

    @Override
    public float length() {
        return getAudio().getLength();
    }

    @Override
    public void pause() {
        alSourcePause(getNode().source());
    }

    @Override
    public boolean isPaused() {
        return alGetSourcei(getNode().source(), AL_SOURCE_STATE) == AL_PAUSED;
    }
    
    @Override
    public void stop() {
        alSourceStop(getNode().source());
        alSourcei(getNode().source(), AL_LOOPING, AL_FALSE);
        alSourcei(getNode().source(), AL_BUFFER, 0);
        this.started = false;
    }

    @Override
    public void update(double tpf) {
        
    }
    
}
