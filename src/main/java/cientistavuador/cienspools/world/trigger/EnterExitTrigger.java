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
import com.jme3.bullet.objects.PhysicsRigidBody;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Cien
 */
public class EnterExitTrigger extends Trigger {
    
    private final Set<PhysicsRigidBody> lastInside = new HashSet<>();
    private final Set<PhysicsRigidBody> inside = new HashSet<>();
    
    public EnterExitTrigger(String name) {
        super(name);
    }

    public Set<PhysicsRigidBody> getInside() {
        return Collections.unmodifiableSet(this.inside);
    }
    
    @Override
    public void onBeforeInside(PhysicsSpace space, float timestep) {
        this.inside.clear();
    }
    
    @Override
    public void onInside(PhysicsSpace space, float timestep, PhysicsRigidBody body) {
        this.inside.add(body);
    }

    @Override
    public void onAfterInside(PhysicsSpace space, float timestep) {
        for (PhysicsRigidBody enter:this.inside) {
            if (!this.lastInside.contains(enter)) {
                onEnter(space, timestep, enter);
            }
        }
        for (PhysicsRigidBody exit:this.lastInside) {
            if (!this.inside.contains(exit)) {
                onExit(space, timestep, exit);
            }
        }
        onUpdate(space, timestep);
        
        this.lastInside.clear();
        this.lastInside.addAll(this.inside);
    }
    
    public void onEnter(PhysicsSpace space, float timestep, PhysicsRigidBody body) {
        
    }
    
    public void onExit(PhysicsSpace space, float timestep, PhysicsRigidBody body) {
        
    }
    
    public void onUpdate(PhysicsSpace space, float timestep) {
        
    }
}
