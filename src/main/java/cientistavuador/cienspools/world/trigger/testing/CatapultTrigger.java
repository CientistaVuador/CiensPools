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
package cientistavuador.cienspools.world.trigger.testing;

import cientistavuador.cienspools.audio.AudioNode;
import cientistavuador.cienspools.audio.data.Audio;
import cientistavuador.cienspools.world.World;
import cientistavuador.cienspools.world.WorldEntity;
import cientistavuador.cienspools.world.trigger.EnterExitTrigger;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.simsilica.mathd.Vec3d;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class CatapultTrigger extends EnterExitTrigger implements WorldEntity {
    
    private float force = 100f;
    private final Vector3f direction = new Vector3f(0f, 1f, 0f);
    private final AudioNode catapultAudioNode;
    
    public CatapultTrigger(String name) {
        super(name);
        this.catapultAudioNode = new AudioNode(name+" audio node");
        this.catapultAudioNode.setAudio(Audio.RESOURCES.get("default/sounds/testing/catapult"));
    }

    @Override
    public void onAddedToWorld(World world) {
        super.onAddedToWorld(world);
        world.getAudioSpace().addNode(this.catapultAudioNode);
    }

    @Override
    public void onWorldUpdate(World world, double tpf) {
        Vec3d catapultPosition = getPhysicsLocationDp(null);
        this.catapultAudioNode.getPosition()
                .set(catapultPosition.x, catapultPosition.y, catapultPosition.z);
    }
    
    @Override
    public void onRemovedFromWorld(World world) {
        super.onRemovedFromWorld(world);
        world.getAudioSpace().removeNode(this.catapultAudioNode);
    }
    
    public float getForce() {
        return force;
    }

    public void setForce(float force) {
        this.force = force;
    }

    public Vector3f getDirection() {
        return direction;
    }

    @Override
    public void onEnter(PhysicsSpace space, float timestep, PhysicsRigidBody body) {
        if (!body.isDynamic()) {
            return;
        }
        com.jme3.math.Vector3f impulse = 
                new com.jme3.math.Vector3f(this.direction.x(), this.direction.y(), this.direction.z())
                        .normalize()
                        .multLocal(getForce());
        body.applyCentralImpulse(impulse);
        this.catapultAudioNode.play();
    }
    
}
