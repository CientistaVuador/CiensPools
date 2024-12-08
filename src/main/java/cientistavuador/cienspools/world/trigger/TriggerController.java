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
package cientistavuador.cienspools.world.trigger;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Cien
 */
public class TriggerController implements PhysicsTickListener {

    public static List<Trigger> getTriggers(PhysicsSpace space) {
        Objects.requireNonNull(space, "space is null");
        List<Trigger> triggers = new ArrayList<>();
        for (PhysicsGhostObject ghost : space.getGhostObjectList()) {
            if (ghost instanceof Trigger t) {
                triggers.add(t);
            }
        }
        return triggers;
    }

    public TriggerController() {

    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        List<Trigger> triggers = getTriggers(space);
        
        for (Trigger t:triggers) {
            t.onBeforeInside(space, timeStep);
        }
        
        for (Trigger t : triggers) {
            List<PhysicsCollisionObject> overlapping = t.getOverlappingObjects();
            for (PhysicsCollisionObject collisionObject : overlapping) {
                if (collisionObject instanceof PhysicsRigidBody body) {
                    if (space.pairTest(t, body, null) != 0) {
                        t.onInside(space, timeStep, body);
                    }
                }
            }
        }
        
        for (Trigger t:triggers) {
            t.onAfterInside(space, timeStep);
        }
    }

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        
    }

}
