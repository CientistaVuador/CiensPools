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
package cientistavuador.cienspools.editor;

import cientistavuador.cienspools.camera.Camera;
import cientistavuador.cienspools.geometry.Geometries;
import cientistavuador.cienspools.geometry.Geometry;
import cientistavuador.cienspools.util.BetterUniformSetter;
import cientistavuador.cienspools.util.ProgramCompiler;
import cientistavuador.cienspools.util.raycast.RayResult;
import java.util.Objects;
import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class Gizmo {

    public static final int SHADER_PROGRAM = ProgramCompiler.compile(
            """
            #version 330 core
            
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            
            uniform mat3 normalModel;
            
            layout (location = 0) in vec3 vertexPosition;
            layout (location = 2) in vec3 vertexNormal;
            
            out vec3 normal;
            
            void main() {
                normal = normalModel * vertexNormal;
                gl_Position = projection * view * model * vec4(vertexPosition, 1.0);
            }
            """,
            """
            #version 330 core
            
            uniform vec3 color;
            
            in vec3 normal;
            
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                float intensity = abs(dot(normalize(normal), vec3(0.0, 1.0, 0.0)));
                outputColor = vec4(color * ((intensity * 0.5) + 0.5), 1.0);
            }
            """
    );

    public static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);

    public static void init() {

    }

    private static final Vector3f ZERO = new Vector3f(0f);

    public static enum GizmoState {
        INACTIVE, TRANSLATING, ROTATING, SCALING;
    }
    
    private static final Matrix4f localRotationModelPitch = new Matrix4f();
    private static final Matrix4f localRotationModelYaw = new Matrix4f();
    private static final Matrix4f localRotationModelRoll = new Matrix4f();

    static {
        localRotationModelPitch
                .identity()
                .translate(0f, 0f, 0.4f)
                .rotateXYZ(
                        (float) Math.toRadians(90f),
                        0f,
                        0f
                );
        localRotationModelYaw
                .identity()
                .translate(0f, 0f, 0.8f)
                .rotateXYZ(
                        0f,
                        (float) Math.toRadians(90f),
                        (float) Math.toRadians(90f)
                );
        localRotationModelRoll
                .identity()
                .rotateXYZ(
                        (float) Math.toRadians(90f),
                        0f,
                        (float) Math.toRadians(90f)
                );
    }
    
    private Camera camera = null;

    private GizmoState state = GizmoState.INACTIVE;
    private final Vector3d position = new Vector3d();
    private final Vector3f rotation = new Vector3f();
    private final Vector3f extents = new Vector3f(1f);
    private final Vector3f scale = new Vector3f(1f);

    private float translationPrecision = 100f;
    private float rotationPrecision = 64f;
    private float scalingPrecision = 50f;
    
    private final Vector3d cameraRayOrigin = new Vector3d();
    private final Vector3f cameraRayDirection = new Vector3f();

    private final Vector3d gizmoPlanePosition = new Vector3d();
    private final Vector3d gizmoPlaneOffset = new Vector3d();
    private final Vector3f gizmoPlaneScaleOffset = new Vector3f();

    private final Geometry translateGizmoX = new Geometry(Geometries.TRANSLATE_GIZMO);
    private final Geometry translateGizmoY = new Geometry(Geometries.TRANSLATE_GIZMO);
    private final Geometry translateGizmoZ = new Geometry(Geometries.TRANSLATE_GIZMO);
    
    {
        this.translateGizmoX.getColorHint().set(1f, 0f, 0f, 1f);
        this.translateGizmoY.getColorHint().set(0f, 1f, 0f, 1f);
        this.translateGizmoZ.getColorHint().set(0f, 0f, 1f, 1f);
    }
    
    private final Geometry rotateGizmoXPitch = new Geometry(Geometries.ROTATE_GIZMO);
    private final Geometry rotateGizmoYYaw = new Geometry(Geometries.ROTATE_GIZMO);
    private final Geometry rotateGizmoZRoll = new Geometry(Geometries.ROTATE_GIZMO);
    
    {
        this.rotateGizmoXPitch.getColorHint().set(1f, 0f, 0f, 1f);
        this.rotateGizmoYYaw.getColorHint().set(0f, 1f, 0f, 1f);
        this.rotateGizmoZRoll.getColorHint().set(0f, 0f, 1f, 1f);
    }

    private final Geometry scaleGizmoX = new Geometry(Geometries.SCALE_GIZMO);
    private final Geometry scaleGizmoY = new Geometry(Geometries.SCALE_GIZMO);
    private final Geometry scaleGizmoZ = new Geometry(Geometries.SCALE_GIZMO);
    
    {
        this.scaleGizmoX.getColorHint().set(1f, 0f, 0f, 1f);
        this.scaleGizmoY.getColorHint().set(0f, 1f, 0f, 1f);
        this.scaleGizmoZ.getColorHint().set(0f, 0f, 1f, 1f);
    }
    
    private boolean holdingRightClick = false;
    private int hoverAxis = -1;
    private int selectedAxis = -1;

    public Gizmo() {
        
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public GizmoState getState() {
        return state;
    }

    public void setState(GizmoState state) {
        this.state = (state == null ? GizmoState.INACTIVE : state);
    }
    
    public boolean isActive() {
        return !getState().equals(GizmoState.INACTIVE);
    }

    public Vector3d getPosition() {
        return position;
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public Quaternionf rotate(Quaternionf receiver, float cx, float cy, float cz) {
        receiver = Objects.requireNonNullElse(receiver, new Quaternionf());
        receiver.rotateYXZ(
                getRotation().y() * cy,
                getRotation().x() * cx,
                getRotation().z() * cz
        );
        return receiver;
    }

    public Quaternionf rotate(Quaternionf receiver) {
        return rotate(receiver, 1f, 1f, 1f);
    }

    public Matrix4f rotate(Matrix4f receiver, float cx, float cy, float cz) {
        receiver = Objects.requireNonNullElse(receiver, new Matrix4f());
        receiver.rotateYXZ(
                getRotation().y() * cy,
                getRotation().x() * cx,
                getRotation().z() * cz
        );
        return receiver;
    }

    public Matrix4f rotate(Matrix4f receiver) {
        return rotate(receiver, 1f, 1f, 1f);
    }

    public Vector3f rotate(Vector3f receiver, float cx, float cy, float cz) {
        receiver = Objects.requireNonNullElse(receiver, new Vector3f(0f, 0f, 1f));
        receiver
                .rotateY(getRotation().y() * cy)
                .rotateX(getRotation().x() * cx)
                .rotateZ(getRotation().z() * cz);
        return receiver;
    }

    public Vector3f rotate(Vector3f receiver) {
        return rotate(receiver, 1f, 1f, 1f);
    }

    public Vector3f getExtents() {
        return extents;
    }
    
    public Vector3f getScale() {
        return scale;
    }
    
    public float getTranslationPrecision() {
        return translationPrecision;
    }

    public void setTranslationPrecision(float translationPrecision) {
        this.translationPrecision = translationPrecision;
    }

    public float getRotationPrecision() {
        return rotationPrecision;
    }

    public void setRotationPrecision(float rotationPrecision) {
        this.rotationPrecision = rotationPrecision;
    }

    public float getScalingPrecision() {
        return scalingPrecision;
    }

    public void setScalingPrecision(float scalingPrecision) {
        this.scalingPrecision = scalingPrecision;
    }

    private void updateTranslationModelMatrix(float scale, Geometry geo, int axis) {
        Matrix4f model = new Matrix4f();
        float xOffset = getScale().x() * getExtents().x() * 0.5f * (axis == 0 ? 1f : 0f);
        float yOffset = getScale().y() * getExtents().y() * 0.5f * (axis == 1 ? 1f : 0f);
        float zOffset = getScale().z() * getExtents().z() * 0.5f * (axis == 2 ? 1f : 0f);
        model
                .identity()
                .translate(
                        (float) ((getPosition().x() + xOffset) - getCamera().getPosition().x()),
                        (float) ((getPosition().y() + yOffset) - getCamera().getPosition().y()),
                        (float) ((getPosition().z() + zOffset) - getCamera().getPosition().z())
                );
        switch (axis) {
            case 0 -> {
                model.rotateZ((float) Math.toRadians(-90f));
            }
            case 2 -> {
                model.rotateX((float) Math.toRadians(90f));
            }
        }
        model.scale(scale);
        geo.setModel(model);
    }

    private void updateRotationModelMatrix(float scale, Geometry geo, int axis) {
        Matrix4f model = new Matrix4f();
        float radius = Math.max(Math.max(
                Math.abs(getScale().x() * getExtents().x() * 0.5f),
                Math.abs(getScale().y() * getExtents().y() * 0.5f)),
                Math.abs(getScale().z() * getExtents().z() * 0.5f)
        );
        model
                .identity()
                .translate(
                        (float) (getPosition().x() - getCamera().getPosition().x()),
                        (float) (getPosition().y() - getCamera().getPosition().y()),
                        (float) (getPosition().z() - getCamera().getPosition().z())
                );
        switch (axis) {
            case 0 -> {
                rotate(model, 1f, 1f, 0f);
                model.translate(0f, 0f, radius).scale(scale);
                model.mul(localRotationModelPitch);
            }
            case 1 -> {
                rotate(model, 0f, 1f, 0f);
                model.translate(0f, 0f, radius).scale(scale);
                model.mul(localRotationModelYaw);
            }
            case 2 -> {
                rotate(model, 1f, 1f, 1f);
                model.translate(-radius, 0f, 0f).scale(scale);
                model.mul(localRotationModelRoll);
            }

        }
        geo.setModel(model);
    }

    private void updateScaleModelMatrix(float scale, Geometry geo, int axis) {
        Matrix4f model = new Matrix4f();
        float xOffset = getScale().x() * getExtents().x() * 0.5f * (axis == 0 ? 1f : 0f);
        float yOffset = getScale().y() * getExtents().y() * 0.5f * (axis == 1 ? 1f : 0f);
        float zOffset = getScale().z() * getExtents().z() * 0.5f * (axis == 2 ? 1f : 0f);
        model
                .identity()
                .translate(
                        (float) (getPosition().x() - getCamera().getPosition().x()),
                        (float) (getPosition().y() - getCamera().getPosition().y()),
                        (float) (getPosition().z() - getCamera().getPosition().z())
                );
        rotate(model);
        model
                .translate(
                        xOffset,
                        yOffset,
                        zOffset
                );
        switch (axis) {
            case 0 -> {
                model.rotateZ((float) Math.toRadians(-90f));
            }
            case 2 -> {
                model.rotateX((float) Math.toRadians(90f));
            }
        }
        model.scale(scale);
        geo.setModel(model);
    }

    private int testGeometryAxis(Geometry x, Geometry y, Geometry z) {
        RayResult[] results = Geometry.testRay(ZERO, this.cameraRayDirection, x, y, z);
        if (results.length == 0) {
            return -1;
        }
        Geometry closestGeometry = results[0].getGeometry();
        if (x == closestGeometry) {
            return 0;
        }
        if (y == closestGeometry) {
            return 1;
        }
        return 2;
    }

    private void paintAxis(Geometry x, Geometry y, Geometry z, int axis) {
        x.getColorHint().set((axis == 0 ? 1f : 0.5f), 0f, 0f);
        y.getColorHint().set(0f, (axis == 1 ? 1f : 0.5f), 0f);
        z.getColorHint().set(0f, 0f, (axis == 2 ? 1f : 0.5f));
    }

    private void updateGeometries() {
        float distance = (float) getCamera().getPosition().distance(getPosition());
        distance *= 0.2f;

        updateTranslationModelMatrix(distance, this.translateGizmoX, 0);
        updateTranslationModelMatrix(distance, this.translateGizmoY, 1);
        updateTranslationModelMatrix(distance, this.translateGizmoZ, 2);

        updateRotationModelMatrix(distance, this.rotateGizmoXPitch, 0);
        updateRotationModelMatrix(distance, this.rotateGizmoYYaw, 1);
        updateRotationModelMatrix(distance, this.rotateGizmoZRoll, 2);

        updateScaleModelMatrix(distance, this.scaleGizmoX, 0);
        updateScaleModelMatrix(distance, this.scaleGizmoY, 1);
        updateScaleModelMatrix(distance, this.scaleGizmoZ, 2);

        this.hoverAxis = -1;
        switch (getState()) {
            case TRANSLATING -> {
                this.hoverAxis = testGeometryAxis(
                        this.translateGizmoX, this.translateGizmoY, this.translateGizmoZ);
            }
            case ROTATING -> {
                this.hoverAxis = testGeometryAxis(
                        this.rotateGizmoXPitch, this.rotateGizmoYYaw, this.rotateGizmoZRoll);
            }
            case SCALING -> {
                this.hoverAxis = testGeometryAxis(
                        this.scaleGizmoX, this.scaleGizmoY, this.scaleGizmoZ);
            }
        }

        int axis = this.selectedAxis;
        if (axis == -1) {
            axis = this.hoverAxis;
        }
        paintAxis(this.translateGizmoX, this.translateGizmoY, this.translateGizmoZ, axis);
        paintAxis(this.rotateGizmoXPitch, this.rotateGizmoYYaw, this.rotateGizmoZRoll, axis);
        paintAxis(this.scaleGizmoX, this.scaleGizmoY, this.scaleGizmoZ, axis);
    }

    public void render() {
        if (getCamera() == null) {
            return;
        }
        updateGeometries();
        Geometry[] renderList;
        switch (getState()) {
            case TRANSLATING -> {
                renderList = new Geometry[]{
                    this.translateGizmoX, this.translateGizmoY, this.translateGizmoZ
                };
            }
            case ROTATING -> {
                renderList = new Geometry[]{
                    this.rotateGizmoXPitch, this.rotateGizmoYYaw, this.rotateGizmoZRoll
                };
            }
            case SCALING -> {
                renderList = new Geometry[]{
                    this.scaleGizmoX, this.scaleGizmoY, this.scaleGizmoZ
                };
            }
            default -> {
                return;
            }
        }
        glUseProgram(SHADER_PROGRAM);
        UNIFORMS.uniformMatrix4fv("projection", getCamera().getProjection());
        UNIFORMS.uniformMatrix4fv("view", getCamera().getView());
        for (Geometry geo : renderList) {
            UNIFORMS.uniformMatrix4fv("model", geo.getModel());
            UNIFORMS.uniformMatrix3fv("normalModel", geo.getInverseNormalModel());
            UNIFORMS.uniform3f("color",
                    geo.getColorHint().x(), geo.getColorHint().y(), geo.getColorHint().z());
            geo.getMesh().bindRenderUnbind();
        }
        glUseProgram(0);
    }

    private boolean updateGizmoPlane(float normalizedX, float normalizedY) {
        if (getCamera() == null) {
            return false;
        }
        this.cameraRayDirection.set(normalizedX, normalizedY, 0f);
        getCamera().getInverseProjection().transformProject(this.cameraRayDirection);
        getCamera().getInverseView().transformProject(this.cameraRayDirection);
        this.cameraRayDirection.normalize();

        this.cameraRayOrigin.set(this.camera.getPosition());
        
        double planeIntersection = Intersectiond.intersectRayPlane(
                this.cameraRayOrigin.x(), this.cameraRayOrigin.y(), this.cameraRayOrigin.z(),
                this.cameraRayDirection.x(), this.cameraRayDirection.y(), this.cameraRayDirection.z(),
                getPosition().x(), getPosition().y(), getPosition().z(),
                -this.camera.getFront().x(), -this.camera.getFront().y(), -this.camera.getFront().z(),
                0.001
        );
        if (planeIntersection < 0.0) {
            return false;
        }
        
        this.gizmoPlanePosition
                .set(this.cameraRayDirection)
                .mul(planeIntersection)
                .add(this.cameraRayOrigin);
        
        return true;
    }
    
    public void onLeftClick(float normalizedX, float normalizedY) {
        if (!isActive()
                || !updateGizmoPlane(normalizedX, normalizedY)) {
            return;
        }
        this.gizmoPlaneOffset
                .set(this.gizmoPlanePosition)
                .sub(getPosition());

        this.gizmoPlaneScaleOffset
                .set(
                        this.gizmoPlanePosition.x() - getPosition().x(),
                        this.gizmoPlanePosition.y() - getPosition().y(),
                        this.gizmoPlanePosition.z() - getPosition().z()
                );
        rotate(this.gizmoPlaneScaleOffset, -1f, -1f, -1f);
        this.gizmoPlaneScaleOffset
                .absolute()
                .mul(2f)
                .sub(
                        getScale().x() * getExtents().x(),
                        getScale().y() * getExtents().y(),
                        getScale().z() * getExtents().z());

        this.selectedAxis = this.hoverAxis;
    }

    public void onLeftClickRelease(float normalizedX, float normalizedY) {
        if (!isActive()
                || !updateGizmoPlane(normalizedX, normalizedY)) {
            return;
        }
        this.selectedAxis = -1;
    }

    public void onRightClick(float normalizedX, float normalizedY) {
        this.holdingRightClick = true;
        if (getState().equals(GizmoState.ROTATING) && this.selectedAxis != -1) {
            getRotation().setComponent(this.selectedAxis, 0f);
            this.selectedAxis = -1;
        }
    }

    public void onRightClickRelease(float normalizedX, float normalizedY) {
        this.holdingRightClick = false;
        onLeftClickRelease(normalizedX, normalizedY);
    }

    public void onMiddleClick(float normalizedX, float normalizedY) {
        this.selectedAxis = -1;
        onLeftClickRelease(normalizedX, normalizedY);
        switch (getState()) {
            case INACTIVE -> {
                setState(Gizmo.GizmoState.TRANSLATING);
            }
            case TRANSLATING -> {
                setState(Gizmo.GizmoState.ROTATING);
            }
            case ROTATING -> {
                setState(Gizmo.GizmoState.SCALING);
            }
            case SCALING -> {
                setState(Gizmo.GizmoState.INACTIVE);
            }
        }
    }

    private double applyTranslationPrecision(double e) {
        if (this.translationPrecision <= 0f || !Float.isFinite(this.translationPrecision)) {
            return e;
        }
        return Math.floor(e * this.translationPrecision) / this.translationPrecision;
    }

    private void doTranslation() {
        switch (this.selectedAxis) {
            case 0 -> {
                getPosition().setComponent(0,
                        applyTranslationPrecision(
                                this.gizmoPlanePosition.x() - this.gizmoPlaneOffset.x()));
            }
            case 1 -> {
                getPosition().setComponent(1,
                        applyTranslationPrecision(
                                this.gizmoPlanePosition.y() - this.gizmoPlaneOffset.y()));
            }
            case 2 -> {
                getPosition().setComponent(2,
                        applyTranslationPrecision(
                                this.gizmoPlanePosition.z() - this.gizmoPlaneOffset.z()));
            }
        }
    }

    private float angle(float x, float y) {
        return (float) Math.atan2(y, x);
    }

    private float applyRotationPrecision(float e) {
        if (this.rotationPrecision <= 0f || !Float.isFinite(this.rotationPrecision)) {
            return e;
        }
        return (float) 
                ((Math.floor((e / Math.PI) * this.rotationPrecision) / this.rotationPrecision)
                * Math.PI);
    }

    private void doRotation() {
        Vector3d translation = this.gizmoPlanePosition;

        Vector3f dir = new Vector3f(
                (float) (translation.x() - getPosition().x()),
                (float) (translation.y() - getPosition().y()),
                (float) (translation.z() - getPosition().z())
        ).normalize();

        switch (this.selectedAxis) {
            case 0 -> {
                rotate(dir, 0f, -1f, 0f);
            }
            case 1 -> {
                //rotate(dir, 0f, 0f, 0f);
            }
            case 2 -> {
                rotate(dir, -1f, -1f, 0f);
            }
        }
        switch (this.selectedAxis) {
            case 0 -> {
                float pitch = applyRotationPrecision(angle(dir.z(), -dir.y()));
                this.rotation.setComponent(0, pitch);
            }
            case 1 -> {
                float yaw = applyRotationPrecision(angle(dir.z(), dir.x()));
                this.rotation.setComponent(1, yaw);
            }
            case 2 -> {
                float roll = applyRotationPrecision(angle(-dir.x(), -dir.y()));
                this.rotation.setComponent(2, roll);
            }
        }
    }

    private float applyScalingPrecision(float e) {
        if (this.scalingPrecision <= 0f || !Float.isFinite(this.scalingPrecision)) {
            return e;
        }
        return (float) (Math.floor(e * this.scalingPrecision) / this.scalingPrecision);
    }

    private void doScaling() {
        Vector3f sc = new Vector3f(
                (float) (this.gizmoPlanePosition.x() - getPosition().x()),
                (float) (this.gizmoPlanePosition.y() - getPosition().y()),
                (float) (this.gizmoPlanePosition.z() - getPosition().z())
        );
        rotate(sc, -1f, -1f, -1f);
        sc
                .absolute()
                .mul(2f)
                .sub(this.gizmoPlaneScaleOffset)
                ;
        sc.set(
                Math.max(applyScalingPrecision(sc.x()), 0f),
                Math.max(applyScalingPrecision(sc.y()), 0f),
                Math.max(applyScalingPrecision(sc.z()), 0f)
        );
        sc.div(getExtents());
        switch (this.selectedAxis) {
            case 0 -> {
                if (this.holdingRightClick) {
                    this.scale.set(sc.x());
                } else {
                    this.scale.setComponent(0, sc.x());
                }
            }
            case 1 -> {
                if (this.holdingRightClick) {
                    this.scale.set(sc.y());
                } else {
                    this.scale.setComponent(1, sc.y());
                }
            }
            case 2 -> {
                if (this.holdingRightClick) {
                    this.scale.set(sc.z());
                } else {
                    this.scale.setComponent(2, sc.z());
                }
            }

        }
    }

    public void onMouseCursorMoved(float normalizedX, float normalizedY) {
        if (!isActive()
                || !updateGizmoPlane(normalizedX, normalizedY)
                || this.selectedAxis == -1) {
            return;
        }
        switch (getState()) {
            case TRANSLATING -> {
                doTranslation();
            }
            case ROTATING -> {
                doRotation();
            }
            case SCALING -> {
                doScaling();
            }
        }
    }

}
