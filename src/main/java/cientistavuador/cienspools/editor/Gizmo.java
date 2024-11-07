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
import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Vector2f;
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

    private Camera camera = null;

    private final Vector3d cameraRayOrigin = new Vector3d();
    private final Vector3f cameraRayDirection = new Vector3f();

    private final Vector3d gizmoPlanePosition = new Vector3d();
    private final Vector3d gizmoPlaneOffset = new Vector3d();

    private final Vector3f extents = new Vector3f(1f);

    private GizmoState state = GizmoState.TRANSLATING;
    private final Vector3d position = new Vector3d();
    private final Vector3f rotation = new Vector3f();
    private final Vector3f scale = new Vector3f(1f);

    private final Matrix4f recycledModel = new Matrix4f();

    private int hoverAxis = -1;
    private int selectedAxis = -1;
    private final Geometry translateGizmoX = new Geometry(Geometries.TRANSLATE_GIZMO);
    private final Geometry translateGizmoY = new Geometry(Geometries.TRANSLATE_GIZMO);
    private final Geometry translateGizmoZ = new Geometry(Geometries.TRANSLATE_GIZMO);

    public Gizmo() {
        this.translateGizmoX.getColorHint().set(1f, 0f, 0f, 1f);
        this.translateGizmoY.getColorHint().set(0f, 1f, 0f, 1f);
        this.translateGizmoZ.getColorHint().set(0f, 0f, 1f, 1f);
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public Vector3f getExtents() {
        return extents;
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

    public Vector3f getScale() {
        return scale;
    }

    private boolean testGeometry(Geometry geo) {
        return Geometry.fastTestRay(
                ZERO, this.cameraRayDirection,
                Float.POSITIVE_INFINITY, geo
        );
    }

    private void updateModelMatrix(float scale, Geometry geo, int axis) {
        Matrix4f model = this.recycledModel;
        float xOffset = (getScale().x() * (getExtents().x() * 0.5f)) * (axis == 0 ? 1f : 0f);
        float yOffset = (getScale().y() * (getExtents().y() * 0.5f)) * (axis == 1 ? 1f : 0f);
        float zOffset = (getScale().z() * (getExtents().z() * 0.5f)) * (axis == 2 ? 1f : 0f);
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

    private void updateGeometries() {
        float distance = (float) getCamera().getPosition().distance(getPosition());
        distance *= 0.1f;

        updateModelMatrix(distance, this.translateGizmoX, 0);
        updateModelMatrix(distance, this.translateGizmoY, 1);
        updateModelMatrix(distance, this.translateGizmoZ, 2);
        
        this.hoverAxis = -1;
        switch (getState()) {
            case TRANSLATING -> {
                if (testGeometry(this.translateGizmoX)) {
                    this.hoverAxis = 0;
                }
                if (testGeometry(this.translateGizmoY)) {
                    this.hoverAxis = 1;
                }
                if (testGeometry(this.translateGizmoZ)) {
                    this.hoverAxis = 2;
                }
            }
        }

        int axis = this.selectedAxis;
        if (axis == -1) {
            axis = this.hoverAxis;
        }
        this.translateGizmoX.getColorHint().set((axis == 0 ? 1f : 0.5f), 0f, 0f);
        this.translateGizmoY.getColorHint().set(0f, (axis == 1 ? 1f : 0.5f), 0f);
        this.translateGizmoZ.getColorHint().set(0f, 0f, (axis == 2 ? 1f : 0.5f));
    }

    public void render() {
        if (getCamera() == null) {
            return;
        }
        updateGeometries();
        Geometry[] renderList = {
            this.translateGizmoX, this.translateGizmoY, this.translateGizmoZ
        };
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

    private boolean updateCameraRay(float normalizedX, float normalizedY) {
        if (getCamera() == null) {
            return false;
        }
        this.cameraRayDirection.set(normalizedX, normalizedY, 0f);
        getCamera().getInverseProjection().transformProject(this.cameraRayDirection);
        getCamera().getInverseView().transformProject(this.cameraRayDirection);
        this.cameraRayDirection.normalize();

        this.cameraRayOrigin.set(this.camera.getPosition());
        return true;
    }

    private boolean getGizmoPlanePosition() {
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
        double px = this.cameraRayOrigin.x() + (this.cameraRayDirection.x() * planeIntersection);
        double py = this.cameraRayOrigin.y() + (this.cameraRayDirection.y() * planeIntersection);
        double pz = this.cameraRayOrigin.z() + (this.cameraRayDirection.z() * planeIntersection);
        this.gizmoPlanePosition.set(px, py, pz);
        return true;
    }

    public void onMouseButtonClick(float normalizedX, float normalizedY) {
        if (!updateCameraRay(normalizedX, normalizedY)) {
            return;
        }
        if (!getGizmoPlanePosition()) {
            return;
        }
        switch (getState()) {
            case TRANSLATING -> {
                this.gizmoPlaneOffset
                        .set(this.gizmoPlanePosition)
                        .sub(getPosition());
                this.selectedAxis = this.hoverAxis;
            }
        }
    }

    public void onMouseButtonRelease(float normalizedX, float normalizedY) {
        if (!updateCameraRay(normalizedX, normalizedY)) {
            return;
        }
        this.selectedAxis = -1;
    }

    private void rotation() {
        Vector3d translation = new Vector3d();

        Vector3f direction = new Vector3f(
                (float) (translation.x() - getPosition().x()),
                (float) (translation.y() - getPosition().y()),
                (float) (translation.z() - getPosition().z())
        ).normalize();

        Vector2f pitchDirection = new Vector2f(direction.z(), direction.y()).normalize();
        Vector2f yawDirection = new Vector2f(direction.x(), direction.z()).normalize();
        Vector2f rollDirection = new Vector2f(direction.x(), direction.y()).normalize();

        float pitch = (float) -Math.atan2(pitchDirection.y(), pitchDirection.x());
        float yaw = (float) (-Math.atan2(yawDirection.y(), yawDirection.x()) + (Math.PI / 2.0));
        float roll = (float) (Math.atan2(rollDirection.y(), rollDirection.x()) + Math.PI);

        this.rotation.set(
                pitch,
                yaw,
                roll
        );
    }

    public void onMouseCursorMoved(float normalizedX, float normalizedY) {
        if (!isActive()) {
            return;
        }
        if (!updateCameraRay(normalizedX, normalizedY)) {
            return;
        }
        if (!getGizmoPlanePosition()) {
            setState(GizmoState.INACTIVE);
            return;
        }
        switch (this.selectedAxis) {
            case 0 -> {
                getPosition().setComponent(0, this.gizmoPlanePosition.x() - this.gizmoPlaneOffset.x());
            }
            case 1 -> {
                getPosition().setComponent(1, this.gizmoPlanePosition.y() - this.gizmoPlaneOffset.y());
            }
            case 2 -> {
                getPosition().setComponent(2, this.gizmoPlanePosition.z() - this.gizmoPlaneOffset.z());
            }
        }
    }

}
