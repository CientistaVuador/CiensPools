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

import cientistavuador.cienspools.fbo.DepthlessForwardFramebuffer;
import cientistavuador.cienspools.fbo.ForwardFramebuffer;
import cientistavuador.cienspools.fbo.Framebuffer;
import cientistavuador.cienspools.fbo.MSForwardFramebuffer;
import cientistavuador.cienspools.fbo.MipFramebuffer;
import cientistavuador.cienspools.fbo.filters.BlurDownsample;
import cientistavuador.cienspools.fbo.filters.BlurUpsample;
import cientistavuador.cienspools.fbo.filters.CopyFilter;
import cientistavuador.cienspools.fbo.filters.FXAAFilter;
import cientistavuador.cienspools.fbo.filters.OutputFilter;
import cientistavuador.cienspools.fbo.filters.ResolveFilter;
import cientistavuador.cienspools.lut.LUT;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class Pipeline {

    public static final int MSAA_SAMPLES = 4;

    private static MSForwardFramebuffer MS_FORWARD_FRAMEBUFFER = null;
    private static final ForwardFramebuffer FORWARD_FRAMEBUFFER = new ForwardFramebuffer();

    public static Framebuffer RENDERING_FRAMEBUFFER = FORWARD_FRAMEBUFFER;
    public static final DepthlessForwardFramebuffer OPAQUE_FRAMEBUFFER = new DepthlessForwardFramebuffer();
    public static final DepthlessForwardFramebuffer DEPTHLESS_FRAMEBUFFER = new DepthlessForwardFramebuffer();
    public static final MipFramebuffer[] MIP_FRAMEBUFFERS = new MipFramebuffer[]{
        new MipFramebuffer(1),
        new MipFramebuffer(2)
    };

    public static float GAMMA = 1.4f;
    public static float EXPOSURE = 3.0f;
    public static LUT COLOR_LUT = LUT.NEUTRAL;

    public static boolean USE_MSAA = true;
    public static boolean USE_FXAA = true;
    private static boolean LAST_MSAA_STATE = USE_MSAA;

    public static void init() {

    }

    private static void updateFramebuffers(int width, int height) {
        if (RENDERING_FRAMEBUFFER instanceof ForwardFramebuffer && USE_MSAA) {
            MS_FORWARD_FRAMEBUFFER = new MSForwardFramebuffer();
            RENDERING_FRAMEBUFFER = MS_FORWARD_FRAMEBUFFER;
        }
        if (RENDERING_FRAMEBUFFER instanceof MSForwardFramebuffer && !USE_MSAA) {
            MS_FORWARD_FRAMEBUFFER.manualFree();
            MS_FORWARD_FRAMEBUFFER = null;
            RENDERING_FRAMEBUFFER = FORWARD_FRAMEBUFFER;
        }

        if (MS_FORWARD_FRAMEBUFFER != null) {
            MS_FORWARD_FRAMEBUFFER.resize(width, height, MSAA_SAMPLES);
        }
        FORWARD_FRAMEBUFFER.resize(width, height);
        DEPTHLESS_FRAMEBUFFER.resize(width, height);
        for (int i = 0; i < MIP_FRAMEBUFFERS.length; i++) {
            MIP_FRAMEBUFFERS[i].resize(width, height);
        }
        OPAQUE_FRAMEBUFFER.resize(width, height);
    }

    public static void copyColorBufferToOpaque() {
        glBindFramebuffer(GL_FRAMEBUFFER, OPAQUE_FRAMEBUFFER.framebuffer());
        if (RENDERING_FRAMEBUFFER instanceof MSForwardFramebuffer msaa) {
            ResolveFilter.render(msaa.colorBuffer(), msaa.depthBuffer(), msaa.getSamples());
        } else if (RENDERING_FRAMEBUFFER instanceof ForwardFramebuffer forward) {
            CopyFilter.render(forward.colorBuffer());
        }
        glBindFramebuffer(GL_FRAMEBUFFER, RENDERING_FRAMEBUFFER.framebuffer());
    }

    public static void windowSizeChanged(int width, int height) {
        updateFramebuffers(width, height);
        Game.get().windowSizeChanged(width, height);
    }

    public static void keyCallback(long window, int key, int scancode, int action, int mods) {
        Game.get().keyCallback(window, key, scancode, action, mods);
    }

    public static void mouseCallback(long window, int button, int action, int mods) {
        Game.get().mouseCallback(window, button, action, mods);
    }

    public static void start() {
        updateFramebuffers(Main.WIDTH, Main.HEIGHT);
        Game.get().start();
    }

    public static void loop() {
        if (LAST_MSAA_STATE != USE_MSAA) {
            LAST_MSAA_STATE = USE_MSAA;
            updateFramebuffers(Main.WIDTH, Main.HEIGHT);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, RENDERING_FRAMEBUFFER.framebuffer());
        glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

        Game.get().loop();

        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        
        if (MS_FORWARD_FRAMEBUFFER != null) {
            glBindFramebuffer(GL_FRAMEBUFFER, FORWARD_FRAMEBUFFER.framebuffer());
            ResolveFilter.render(
                    MS_FORWARD_FRAMEBUFFER.colorBuffer(), MS_FORWARD_FRAMEBUFFER.depthBuffer(),
                    MS_FORWARD_FRAMEBUFFER.getSamples()
            );
        }

        if (USE_FXAA) {
            glBindFramebuffer(GL_FRAMEBUFFER, DEPTHLESS_FRAMEBUFFER.framebuffer());
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        OutputFilter.render(
                EXPOSURE, GAMMA,
                (Pipeline.COLOR_LUT == null ? LUT.NEUTRAL.texture() : Pipeline.COLOR_LUT.texture()),
                FORWARD_FRAMEBUFFER.colorBuffer()
        );

        BlurDownsample.prepare();
        for (int i = 0; i < MIP_FRAMEBUFFERS.length; i++) {
            glBindFramebuffer(GL_FRAMEBUFFER, MIP_FRAMEBUFFERS[i].framebuffer());
            glViewport(0, 0, MIP_FRAMEBUFFERS[i].getWidth(), MIP_FRAMEBUFFERS[i].getHeight());
            BlurDownsample.render(i == 0 ? DEPTHLESS_FRAMEBUFFER.colorBuffer() : MIP_FRAMEBUFFERS[i - 1].colorBuffer());
        }
        BlurDownsample.done();
        
        BlurUpsample.prepare();
        for (int i = MIP_FRAMEBUFFERS.length - 1; i > 0; i--) {
            glBindFramebuffer(GL_FRAMEBUFFER, MIP_FRAMEBUFFERS[i - 1].framebuffer());
            glViewport(0, 0, MIP_FRAMEBUFFERS[i - 1].getWidth(), MIP_FRAMEBUFFERS[i - 1].getHeight());
            BlurUpsample.render(MIP_FRAMEBUFFERS[i].colorBuffer());
        }
        BlurUpsample.done();
        
        
        if (USE_FXAA) {
            glBindFramebuffer(GL_FRAMEBUFFER, DEPTHLESS_FRAMEBUFFER.framebuffer());
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        glViewport(0, 0, RENDERING_FRAMEBUFFER.getWidth(), RENDERING_FRAMEBUFFER.getHeight());

        if (USE_FXAA) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            FXAAFilter.render(DEPTHLESS_FRAMEBUFFER.colorBuffer());
            //BlurUpsample.render(MIP_FRAMEBUFFERS[0].colorBuffer());
        }
        
        //BlurUpsample.done();

        glEnable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    private Pipeline() {

    }
}
