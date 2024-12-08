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

import cientistavuador.cienspools.world.World;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import cientistavuador.cienspools.world.WorldObject;

/**
 *
 * @author Cien
 */
public class Trigger extends PhysicsGhostObject implements WorldObject {

    private World world = null;
    
    private final String name;
    
    public Trigger(String name) {
        super(new BoxCollisionShape(0.5f));
        this.name = name;
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public void onAddedToWorld(World world) {
        this.world = world;
        world.getPhysicsSpace().add(this);
    }
    
    @Override
    public void onRemovedFromWorld(World world) {
        this.world = null;
        world.getPhysicsSpace().remove(this);
    }
    
    public String getName() {
        return name;
    }
    
    public void setScale(Vector3f scale) {
        getCollisionShape().setScale(scale);
    }
    
    public Vector3f getHalfExtents(Vector3f halfExtents) {
        return getScale(halfExtents).multLocal(0.5f);
    }
    
    public void setHalfExtents(Vector3f halfExtents) {
        setScale(halfExtents.mult(2f));
    }
    
    public void setTransformation(
            double x, double y, double z,
            float sx, float sy, float sz,
            float rx, float ry, float rz, float rw
    ) {
        setPhysicsLocationDp(new Vec3d(x, y, z));
        setHalfExtents(new Vector3f(sx, sy, sz));
        setPhysicsRotation(new Quaternion(rx, ry, rz, rw));
    }
    
    public void setTransformation(
            org.joml.Vector3dc p,
            org.joml.Vector3fc s,
            org.joml.Quaternionfc r) {
        setTransformation(
                p.x(), p.y(), p.z(),
                s.x(), s.y(), s.z(),
                r.x(), r.y(), r.z(), r.w()
        );
    }
    
    @Override
    public void setCollisionShape(CollisionShape collisionShape) {
        throw new UnsupportedOperationException("Trigger cannot change collision shape.");
    }
    
    public void onBeforeInside(PhysicsSpace space, float timestep) {
        
    }
    
    public void onInside(PhysicsSpace space, float timestep, PhysicsRigidBody body) {
        
    }
    
    public void onAfterInside(PhysicsSpace space, float timestep) {
        
    }
    
}
