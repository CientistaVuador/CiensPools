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
package cientistavuador.cienspools.audio;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.audio.control.AudioControl;
import cientistavuador.cienspools.audio.control.BufferedAudioControl;
import cientistavuador.cienspools.audio.control.StreamedAudioControl;
import cientistavuador.cienspools.audio.data.Audio;
import cientistavuador.cienspools.audio.data.BufferedAudio;
import cientistavuador.cienspools.audio.data.StreamedAudio;
import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.util.ObjectCleaner;
import java.util.Objects;
import org.joml.Vector3d;
import static org.lwjgl.openal.AL11.*;

/**
 *
 * @author Cien
 */
public class AudioNode implements AudioControl {

    private static class WrappedSource {

        int source = 0;
    }

    protected AudioSpace audioSpace;
    
    private final String name;
    private final WrappedSource wrappedSource = new WrappedSource();
    
    private final Vector3d position = new Vector3d();
    private final Vector3d lastPosition = new Vector3d();
    
    private Audio audio = null;
    private boolean looping = false;
    private AudioControl control = null;

    public AudioNode(String name) {
        this.name = Objects.requireNonNullElse(name, Resource.generateRandomId(null));

        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedSource finalWrappedSource = this.wrappedSource;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                if (finalWrappedSource.source != 0) {
                    alDeleteSources(finalWrappedSource.source);
                }
                finalWrappedSource.source = 0;
            });
        });
    }

    public AudioSpace getAudioSpace() {
        return audioSpace;
    }

    public String getName() {
        return name;
    }

    public int source() {
        if (this.wrappedSource.source == 0) {
            this.wrappedSource.source = alGenSources();
        }
        return this.wrappedSource.source;
    }

    public Vector3d getPosition() {
        return position;
    }

    public Audio getAudio() {
        return audio;
    }

    public void setAudio(Audio audio) {
        this.audio = audio;
        stop();
    }
    
    public float getGain() {
        return alGetSourcef(source(), AL_GAIN);
    }
    
    public void setGain(float gain) {
        alSourcef(source(), AL_GAIN, gain);
    }

    @Override
    public boolean isLooping() {
        return looping;
    }
    
    @Override
    public void setLooping(boolean looping) {
        this.looping = looping;
        if (this.control != null) {
            this.control.setLooping(isLooping());
        }
    }
    
    @Override
    public void play() {
        if (this.control != null) {
            this.control.play();
            return;
        }
        if (this.audio == null) {
            return;
        }
        
        if (this.audio instanceof BufferedAudio b) {
            this.control = new BufferedAudioControl(this, b);
        } else if (this.audio instanceof StreamedAudio s) {
            this.control = new StreamedAudioControl(this, s);
        } else {
            throw new IllegalArgumentException("Unknown audio type: "+this.audio.getClass());
        }
        this.control.setLooping(isLooping());
        this.control.play();
    }
    
    @Override
    public boolean isPlaying() {
        if (this.control != null) {
            return this.control.isPlaying();
        }
        return false;
    }
    
    @Override
    public void seek(float length) {
        if (this.control != null) {
            this.control.seek(length);
        }
    }
    
    @Override
    public float elapsed() {
        if (this.control != null) {
            return this.control.elapsed();
        }
        return 0f;
    }
    
    @Override
    public float length() {
        if (this.control != null) {
            return this.control.length();
        }
        if (this.audio != null) {
            return this.audio.getLength();
        }
        return 0f;
    }
    
    @Override
    public void pause() {
        if (this.control != null) {
            this.control.pause();
        }
    }
    
    @Override
    public boolean isPaused() {
        if (this.control != null) {
            return this.control.isPaused();
        }
        return false;
    }
    
    @Override
    public void stop() {
        if (this.control != null) {
            this.control.stop();
            this.control = null;
        }
    }
    
    @Override
    public void update(double tpf) {
        if (getAudioSpace() == null || this.control == null) {
            return;
        }
        
        this.control.update(tpf);
        
        alSource3f(source(), 
                AL_POSITION,
                (float) (getPosition().x() - getAudioSpace().getListenerPosition().x()),
                (float) (getPosition().y() - getAudioSpace().getListenerPosition().y()),
                (float) (getPosition().z() - getAudioSpace().getListenerPosition().z())
        );
        alSource3f(source(),
                AL_VELOCITY,
                (float) ((getPosition().x() - this.lastPosition.x()) / tpf),
                (float) ((getPosition().y() - this.lastPosition.y()) / tpf),
                (float) ((getPosition().z() - this.lastPosition.z()) / tpf)
        );
        
        this.lastPosition.set(getPosition());
    }

    public void manualFree() {
        stop();
        
        final WrappedSource finalWrappedSource = this.wrappedSource;

        if (finalWrappedSource.source != 0) {
            alDeleteSources(finalWrappedSource.source);
        }
        finalWrappedSource.source = 0;
    }

}
