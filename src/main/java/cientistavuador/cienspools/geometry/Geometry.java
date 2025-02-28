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
package cientistavuador.cienspools.geometry;

import cientistavuador.cienspools.resources.mesh.MeshData;
import cientistavuador.cienspools.texture.Textures;
import cientistavuador.cienspools.util.raycast.LocalRayResult;
import cientistavuador.cienspools.util.raycast.RayResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 *
 * @author Cien
 */
public class Geometry {
    
    public static boolean testSphere(float x, float y, float z, float radius, List<Geometry> geometries) {
        if (geometries.isEmpty()) {
            return false;
        }
        
        Vector3f transformedPosition = new Vector3f();
        
        //warning: radius is not transformed
        
        for (Geometry g:geometries) {
            g.getInverseModel().transformProject(transformedPosition.set(x, y, z));
            
            if (g.getMesh().getBVH().fastTestSphere(
                    transformedPosition.x(), transformedPosition.y(), transformedPosition.z(),
                    radius
            )) {
                return true;
            }
        }
        
        return false;
    }
    
    public static boolean fastTestRay(Vector3fc origin, Vector3fc direction, float maxLength, List<Geometry> geometries) {
        if (geometries.isEmpty()) {
            return false;
        }

        Vector3f transformedOrigin = new Vector3f();
        Vector3f transformedDirection = new Vector3f();

        for (Geometry g : geometries) {
            g.getInverseModel().transformProject(transformedOrigin.set(origin));

            float length = maxLength;
            if (Float.isFinite(length)) {
                g.getInverseModel().transformProject(transformedDirection
                        .set(direction)
                        .mul(maxLength)
                        .add(origin)
                );
                transformedDirection.sub(transformedOrigin);
                length = transformedDirection.length();
                transformedDirection.div(length);
            } else {
                g.getInverseModel().transformProject(transformedDirection
                        .set(direction)
                        .add(origin)
                );
                transformedDirection.sub(transformedOrigin).normalize();
            }

            if (g.getMesh().getBVH().fastTestRay(transformedOrigin, transformedDirection, length)) {
                return true;
            }
        }
        return false;
    }

    public static RayResult[] testRay(Vector3fc origin, Vector3fc direction, List<Geometry> geometries) {
        if (geometries.isEmpty()) {
            return new RayResult[0];
        }

        Vector3f transformedOrigin = new Vector3f();
        Vector3f transformedDirection = new Vector3f();

        List<RayResult> rays = new ArrayList<>();
        for (Geometry g : geometries) {
            g.getInverseModel().transformProject(transformedOrigin.set(origin));
            g.getInverseModel().transformProject(transformedDirection
                    .set(direction)
                    .add(origin)
            );
            transformedDirection.sub(transformedOrigin).normalize();

            List<LocalRayResult> localTest = g.getMesh().getBVH().testRay(transformedOrigin, transformedDirection);
            for (LocalRayResult e : localTest) {
                rays.add(new RayResult(e, g));
            }
        }
        RayResult[] array = rays.toArray(RayResult[]::new);
        Arrays.sort(array);
        return array;
    }

    public static RayResult[] testRay(Vector3fc origin, Vector3fc direction, Geometry... geometries) {
        return testRay(origin, direction, Arrays.asList(geometries));
    }
    
    public static boolean fastTestRay(Vector3fc origin, Vector3fc direction, float maxLength, Geometry... geometries) {
        return fastTestRay(origin, direction, maxLength, Arrays.asList(geometries));
    }
    
    private final MeshData mesh;
    
    private final Matrix4f model = new Matrix4f();
    private final Matrix4f inverseModel = new Matrix4f();
    private final Matrix3f normalModel = new Matrix3f();
    private final Matrix3f inverseNormalModel = new Matrix3f();
    
    private final Vector4f colorHint = new Vector4f();
    
    @Deprecated
    private int lightmapTextureHint = Textures.EMPTY_LIGHTMAP;
    @Deprecated
    private MeshData.LightmapMesh lightmapMesh = null;
    
    @Deprecated
    private float lightmapScale = 1f;
    
    public Geometry(MeshData mesh) {
        this.mesh = mesh;
    }
    
    public MeshData getMesh() {
        return mesh;
    }
    
    public Matrix4fc getModel() {
        return model;
    }
    
    public Matrix4fc getInverseModel() {
        return inverseModel;
    }
    
    public Matrix3fc getNormalModel() {
        return normalModel;
    }

    public Matrix3fc getInverseNormalModel() {
        return inverseNormalModel;
    }

    public void setModel(Matrix4fc model) {
        this.model.set(model);
        this.model.invert(this.inverseModel);
        this.inverseModel.transpose3x3(this.normalModel);
        this.normalModel.invert(this.inverseNormalModel);
    }

    public Vector4f getColorHint() {
        return colorHint;
    }

    @Deprecated
    public int getLightmapTextureHint() {
        return lightmapTextureHint;
    }

    @Deprecated
    public void setLightmapTextureHint(int lightmapTextureHint) {
        this.lightmapTextureHint = lightmapTextureHint;
    }

    @Deprecated
    public MeshData.LightmapMesh getLightmapMesh() {
        return lightmapMesh;
    }

    @Deprecated
    public void setLightmapMesh(MeshData.LightmapMesh lightmapMesh) {
        this.lightmapMesh = lightmapMesh;
    }

    @Deprecated
    public float getLightmapScale() {
        return lightmapScale;
    }

    @Deprecated
    public void setLightmapScale(float lightmapScale) {
        this.lightmapScale = lightmapScale;
    }

}
