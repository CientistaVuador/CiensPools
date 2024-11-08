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

import cientistavuador.cienspools.resources.mesh.MeshConfiguration;
import cientistavuador.cienspools.resources.mesh.MeshData;

/**
 *
 * @author Cien
 */
public class Geometries {
    
    public static final MeshData DEBUG_SPHERE;
    public static final MeshData TRANSLATE_GIZMO;
    public static final MeshData ROTATE_GIZMO;
    public static final MeshData SCALE_GIZMO;
    
    static {
        DEBUG_SPHERE = GeometriesLoader.load(
                new MeshConfiguration("debug_sphere.obj", false, false, 0f, 0, 0f))
                .get("debug_sphere.obj");
        TRANSLATE_GIZMO = GeometriesLoader.load(
                new MeshConfiguration("translate_gizmo.obj", false, false, 0f, 0, 0f))
                .get("translate_gizmo.obj");
        ROTATE_GIZMO = GeometriesLoader.load(
                new MeshConfiguration("rotate_gizmo.obj", false, false, 0f, 0, 0f))
                .get("rotate_gizmo.obj");
        SCALE_GIZMO = GeometriesLoader.load(
                new MeshConfiguration("scale_gizmo.obj", false, false, 0f, 0, 0f))
                .get("scale_gizmo.obj");
    }
    
    public static void init() {
        
    }

    private Geometries() {

    }

}
