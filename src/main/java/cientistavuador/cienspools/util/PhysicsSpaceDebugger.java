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
package cientistavuador.cienspools.util;

import cientistavuador.cienspools.Main;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.GImpactCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.DebugShapeFactory;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class PhysicsSpaceDebugger {

    public static final Vector3fc ACTIVE_COLOR = new org.joml.Vector3f(0f, 1f, 0f);
    public static final Vector3fc INACTIVE_COLOR = new org.joml.Vector3f(1f, 0f, 0f);
    public static final Vector3fc KINEMATIC_COLOR = new org.joml.Vector3f(1f, 1f, 0f);
    public static final Vector3fc STATIC_COLOR = new org.joml.Vector3f(1f, 1f, 1f);
    public static final Vector3fc OTHER_COLOR = new org.joml.Vector3f(0f, 1f, 1f);
    public static final Vector3fc CENTER_OF_MASS_COLOR = new org.joml.Vector3f(1f, 0.5f, 0f);

    private static final float[] sphereTriangles;

    static {
        SphereCollisionShape sphere = new SphereCollisionShape(1f);
        FloatBuffer trianglesBuffer = DebugShapeFactory
                .getDebugTriangles(sphere, DebugShapeFactory.lowResolution)
                .flip();
        sphereTriangles = new float[trianglesBuffer.limit()];
        trianglesBuffer.get(sphereTriangles);
    }

    private static final Matrix4fc IDENTITY = new Matrix4f();

    private final PhysicsSpace physicsSpace;
    private final WeakHashMap<CollisionShape, Pair<float[], Vector3f>> collisionShapeTriangles = new WeakHashMap<>();

    public PhysicsSpaceDebugger(PhysicsSpace physicsSpace) {
        this.physicsSpace = physicsSpace;
    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }

    private Pair<float[], Vector3f> getTriangles(CollisionShape collisionShape) {
        Pair<float[], Vector3f> shapeTriangles
                = this.collisionShapeTriangles.get(collisionShape);
        if (shapeTriangles == null) {
            FloatBuffer trianglesBuffer = DebugShapeFactory
                    .getDebugTriangles(collisionShape, DebugShapeFactory.highResolution2)
                    .flip();
            float[] triangles = new float[trianglesBuffer.limit()];
            trianglesBuffer.get(triangles);

            Vector3f scale = collisionShape.getScale(null);
            shapeTriangles = new Pair<>(triangles, scale);
            this.collisionShapeTriangles.put(collisionShape, shapeTriangles);
        }
        return shapeTriangles;
    }

    public void pushToDebugRenderer(
            Matrix4fc projection,
            Matrix4fc view,
            Vector3dc camPosition
    ) {
        Collection<PhysicsCollisionObject> list = this.physicsSpace.getPcoList();
        if (list.isEmpty()) {
            return;
        }
        for (PhysicsCollisionObject obj : list) {
            CollisionShape shape = obj.getCollisionShape();
            if (shape == null
                    || shape instanceof MeshCollisionShape
                    || shape instanceof GImpactCollisionShape) {
                continue;
            }

            Pair<float[], Vector3f> pair = getTriangles(shape);

            float[] triangles = pair.getA();
            Vector3f originalScale = pair.getB();

            Vec3d location = obj.getPhysicsLocationDp(null);
            Quaternion rotation = obj.getPhysicsRotation(null);
            Vector3f scale = shape.getScale(null);

            Matrix4f model = new Matrix4f()
                    .translate(
                            (float) ((location.x * Main.FROM_PHYSICS_ENGINE_UNITS) - camPosition.x()),
                            (float) ((location.y * Main.FROM_PHYSICS_ENGINE_UNITS) - camPosition.y()),
                            (float) ((location.z * Main.FROM_PHYSICS_ENGINE_UNITS) - camPosition.z())
                    )
                    .rotate(new Quaternionf(
                            rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW())
                    )
                    .scale(
                            (scale.x / originalScale.x)
                            * Main.FROM_PHYSICS_ENGINE_UNITS,
                            (scale.y / originalScale.y)
                            * Main.FROM_PHYSICS_ENGINE_UNITS,
                            (scale.z / originalScale.z)
                            * Main.FROM_PHYSICS_ENGINE_UNITS
                    );

            float r = ACTIVE_COLOR.x();
            float g = ACTIVE_COLOR.y();
            float b = ACTIVE_COLOR.z();

            if (obj instanceof PhysicsRigidBody body) {
                if (body.isKinematic()) {
                    r = KINEMATIC_COLOR.x();
                    g = KINEMATIC_COLOR.y();
                    b = KINEMATIC_COLOR.z();
                } else {
                    if (body.getMass() == 0f) {
                        r = STATIC_COLOR.x();
                        g = STATIC_COLOR.y();
                        b = STATIC_COLOR.z();
                    } else {
                        if (!body.isActive()) {
                            r = INACTIVE_COLOR.x();
                            g = INACTIVE_COLOR.y();
                            b = INACTIVE_COLOR.z();
                        }
                    }
                }
            } else {
                r = OTHER_COLOR.x();
                g = OTHER_COLOR.y();
                b = OTHER_COLOR.z();
            }

            DebugRenderer.VertexStream stream = DebugRenderer.begin(
                    projection, view, model, r, g, b);
            for (int i = 0; i < triangles.length; i += 3) {
                stream.push(
                        triangles[i + 0],
                        triangles[i + 1],
                        triangles[i + 2]
                );
            }
            stream.end();

            DebugRenderer.VertexStream massCenterStream = DebugRenderer
                    .begin(projection, view, IDENTITY,
                            CENTER_OF_MASS_COLOR.x(), CENTER_OF_MASS_COLOR.y(), CENTER_OF_MASS_COLOR.z());
            for (int i = 0; i < sphereTriangles.length; i += 3) {
                float radius = obj.getCcdSweptSphereRadius();
                if (radius == 0f && obj.getCcdMotionThreshold() == 0f) {
                    radius = 0.05f * Main.TO_PHYSICS_ENGINE_UNITS;
                }
                massCenterStream.push(
                        (sphereTriangles[i + 0] * radius * Main.FROM_PHYSICS_ENGINE_UNITS)
                        + ((float) ((location.x * Main.FROM_PHYSICS_ENGINE_UNITS) - camPosition.x())),
                        (sphereTriangles[i + 1] * radius * Main.FROM_PHYSICS_ENGINE_UNITS)
                        + ((float) ((location.y * Main.FROM_PHYSICS_ENGINE_UNITS) - camPosition.y())),
                        (sphereTriangles[i + 2] * radius * Main.FROM_PHYSICS_ENGINE_UNITS)
                        + ((float) ((location.z * Main.FROM_PHYSICS_ENGINE_UNITS) - camPosition.z()))
                );
            }
            massCenterStream.end();
        }
    }

}
