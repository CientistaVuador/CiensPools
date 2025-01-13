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

import cientistavuador.cienspools.fbo.HDRFramebuffer;
import cientistavuador.cienspools.fbo.filters.CopyFilter;
import cientistavuador.cienspools.fbo.filters.FXAAFilter;
import cientistavuador.cienspools.fbo.filters.OutputFilter;
import cientistavuador.cienspools.lut.LUT;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class Pipeline {

    public static final HDRFramebuffer HDR_FRAMEBUFFER = new HDRFramebuffer();
    
    public static float GAMMA = 1.4f;
    public static float EXPOSURE = 3.0f;
    public static LUT COLOR_LUT = LUT.NEUTRAL;
    
    public static boolean USE_FXAA = true;
    public static boolean USE_MSAA = false;
    
    public static void init() {
        
    }
    
    public static void windowSizeChanged(int width, int height) {
        Pipeline.HDR_FRAMEBUFFER.resize(width, height);
        Game.get().windowSizeChanged(width, height);
    }
    
    public static void keyCallback(long window, int key, int scancode, int action, int mods) {
        Game.get().keyCallback(window, key, scancode, action, mods);
    }
    
    public static void mouseCallback(long window, int button, int action, int mods) {
        Game.get().mouseCallback(window, button, action, mods);
    }
    
    public static void start() {
        HDR_FRAMEBUFFER.resize(Main.WIDTH, Main.HEIGHT);
        
        Game.get().start();
    }
    
    public static void loop() {
        glBindFramebuffer(GL_FRAMEBUFFER, HDR_FRAMEBUFFER.framebuffer());
        glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

        Game.get().loop();

        HDR_FRAMEBUFFER.flip();

        glDisable(GL_BLEND);
        OutputFilter.render(
                Main.WIDTH, Main.HEIGHT,
                Pipeline.EXPOSURE, Pipeline.GAMMA,
                (Pipeline.COLOR_LUT == null ? LUT.NEUTRAL.texture() : Pipeline.COLOR_LUT.texture()),
                HDR_FRAMEBUFFER.colorBufferRead()
        );

        if (USE_FXAA) {
            HDR_FRAMEBUFFER.flip();

            FXAAFilter.render(Main.WIDTH, Main.HEIGHT, HDR_FRAMEBUFFER.colorBufferRead());
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        CopyFilter.render(HDR_FRAMEBUFFER.colorBufferWrite());
        glEnable(GL_BLEND);
    }

    private Pipeline() {

    }
}
