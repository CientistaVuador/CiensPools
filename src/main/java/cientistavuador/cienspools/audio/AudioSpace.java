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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3f;
import static org.lwjgl.openal.AL11.*;

/**
 *
 * @author Cien
 */
public class AudioSpace {

    private final Vector3d listenerPosition = new Vector3d();
    private final Vector3f listenerFront = new Vector3f();
    private final Vector3f listenerUp = new Vector3f();
    
    private final Vector3d lastListenerPosition = new Vector3d();

    private final List<AudioNode> nodes = new ArrayList<>();

    public AudioSpace() {

    }

    public Vector3d getListenerPosition() {
        return listenerPosition;
    }

    public Vector3f getListenerFront() {
        return listenerFront;
    }

    public Vector3f getListenerUp() {
        return listenerUp;
    }

    public List<AudioNode> getNodes() {
        return Collections.unmodifiableList(this.nodes);
    }

    public boolean addNode(AudioNode node) {
        if (node == null || node.audioSpace != null) {
            return false;
        }
        boolean result = this.nodes.add(node);
        if (result) {
            node.audioSpace = this;
        }
        return result;
    }

    public boolean removeNode(AudioNode node) {
        boolean result = this.nodes.remove(node);
        if (result) {
            node.stop();
            node.audioSpace = null;
        }
        return result;
    }

    public void update(double tpf) {
        alListener3f(AL_POSITION, 0f, 0f, 0f);
        alListener3f(AL_VELOCITY, 
                (float) ((getListenerPosition().x() - this.lastListenerPosition.x()) / tpf),
                (float) ((getListenerPosition().y() - this.lastListenerPosition.y()) / tpf),
                (float) ((getListenerPosition().z() - this.lastListenerPosition.z()) / tpf)
        );
        alListenerfv(AL_ORIENTATION,
                new float[]{
                    getListenerFront().x(),
                    getListenerFront().y(),
                    getListenerFront().z(),
                    getListenerUp().x(),
                    getListenerUp().y(),
                    getListenerUp().z()
                }
        );

        for (AudioNode n : getNodes().toArray(AudioNode[]::new)) {
            n.update(tpf);
        }
        
        this.lastListenerPosition.set(getListenerPosition());
    }
}
