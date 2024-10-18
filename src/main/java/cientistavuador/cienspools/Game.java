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
package cientistavuador.cienspools;

import cientistavuador.cienspools.camera.FreeCamera;
import cientistavuador.cienspools.debug.AabRender;
import cientistavuador.cienspools.debug.LineRender;
import cientistavuador.cienspools.newrendering.N3DObjectRenderer;
import cientistavuador.cienspools.newrendering.NCubemap;
import cientistavuador.cienspools.newrendering.NLightmaps;
import cientistavuador.cienspools.newrendering.NTextures;
import cientistavuador.cienspools.ubo.CameraUBO;
import cientistavuador.cienspools.ubo.UBOBindingPoints;
import cientistavuador.cienspools.util.DebugRenderer;
import static org.lwjgl.glfw.GLFW.*;

/**
 *
 * @author Cien
 */
public class Game {

    private static final Game GAME = new Game();

    public static Game get() {
        return GAME;
    }

    private final FreeCamera camera = new FreeCamera();
    
    private Game() {
        
    }

    public void start() {
        NTextures.NULL_TEXTURES.textures();
        NCubemap.NULL_CUBEMAP.cubemap();
        NLightmaps.NULL_LIGHTMAPS.lightmaps();

        this.camera.setUBO(CameraUBO.create(UBOBindingPoints.PLAYER_CAMERA));
        
        System.gc();
    }

    public void loop() {
        this.camera.updateMovement();
        this.camera.updateUBO();
        AabRender.renderQueue(this.camera);
        LineRender.renderQueue(this.camera);
        DebugRenderer.render();
        
        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + String.format("%,.2f", this.camera.getPosition().x()) + ",y:" + String.format("%,.2f", this.camera.getPosition().y()) + ",z:" + String.format("%,.2f", this.camera.getPosition().z()) + ")";
        Main.WINDOW_TITLE += " (dx:" + String.format("%,.2f", this.camera.getFront().x()) + ",dy:" + String.format("%,.2f", this.camera.getFront().y()) + ",dz:" + String.format("%,.2f", this.camera.getFront().z()) + ")";
        Main.WINDOW_TITLE += " (pt:" + String.format("%,.2f", this.camera.getRotation().x()) + ",yw:" + String.format("%,.2f", this.camera.getRotation().y()) + ",rl:" + String.format("%,.2f", this.camera.getRotation().z()) + ")";
    }

    public void mouseCursorMoved(double x, double y) {
        this.camera.mouseCursorMoved(x, y);
    }

    public void windowSizeChanged(int width, int height) {
        this.camera.setDimensions(width, height);
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_F1 && action == GLFW_PRESS) {
            N3DObjectRenderer.REFLECTIONS_ENABLED = !N3DObjectRenderer.REFLECTIONS_ENABLED;
        }
        if (key == GLFW_KEY_F2 && action == GLFW_PRESS) {
            N3DObjectRenderer.PARALLAX_ENABLED = !N3DObjectRenderer.PARALLAX_ENABLED;
        }
        if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
            N3DObjectRenderer.REFLECTIONS_DEBUG = !N3DObjectRenderer.REFLECTIONS_DEBUG;
        }
    }
    
    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
