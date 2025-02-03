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
import cientistavuador.cienspools.fbo.filters.FXAAQuality;
import cientistavuador.cienspools.fbo.filters.TonemappingFilter;
import cientistavuador.cienspools.fbo.filters.ResolveFilter;
import cientistavuador.cienspools.fbo.filters.WaterFilter;
import cientistavuador.cienspools.lut.LUT;
import cientistavuador.cienspools.util.RasterUtils;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class Pipeline {

    public static final int MSAA_SAMPLES = 4;

    private static MSForwardFramebuffer MS_FORWARD_FRAMEBUFFER = null;
    private static ForwardFramebuffer FORWARD_FRAMEBUFFER = new ForwardFramebuffer();
    public static Framebuffer RENDERING_FRAMEBUFFER = FORWARD_FRAMEBUFFER;

    public static final ForwardFramebuffer OPAQUE_FRAMEBUFFER = new ForwardFramebuffer();

    public static final DepthlessForwardFramebuffer TONEMAP_FRAMEBUFFER = new DepthlessForwardFramebuffer();
    
    public static final MipFramebuffer[] MIP_FRAMEBUFFERS = new MipFramebuffer[]{
        new MipFramebuffer(1),
        new MipFramebuffer(2)
    };

    public static float GAMMA = 1.4f;
    public static float EXPOSURE = 3.0f;
    public static LUT COLOR_LUT = LUT.NEUTRAL;

    public static boolean USE_MSAA = true;
    public static FXAAQuality FXAA_QUALITY = FXAAQuality.MEDIUM;
    private static boolean LAST_MSAA_STATE = USE_MSAA;

    public static boolean WATER_EFFECT = false;

    private static boolean testPixel(float x, float y) {
        Vector3f p = new Vector3f(x, y, 0f);
        Vector3f a = new Vector3f(128f, 164f, 0f);
        Vector3f b = new Vector3f(384f, 296f, 0f);
        Vector3f c = new Vector3f(256f, 428f, 0f);
        Vector3f w = new Vector3f();
        RasterUtils.barycentricWeights(p, a, b, c, w);
        return !(w.x() < 0f || w.y() < 0f || w.z() < 0f);
    }
    
    private static float testPixelFloat(float x, float y) {
        return (testPixel(x, y) ? 0f : 1f);
    }
    
    public static void init() {
        
    }

    private static void updateFramebuffers(int width, int height) {
        if (RENDERING_FRAMEBUFFER instanceof ForwardFramebuffer && USE_MSAA) {
            FORWARD_FRAMEBUFFER.manualFree();
            FORWARD_FRAMEBUFFER = null;
            MS_FORWARD_FRAMEBUFFER = new MSForwardFramebuffer();
            RENDERING_FRAMEBUFFER = MS_FORWARD_FRAMEBUFFER;
        }
        if (RENDERING_FRAMEBUFFER instanceof MSForwardFramebuffer && !USE_MSAA) {
            MS_FORWARD_FRAMEBUFFER.manualFree();
            MS_FORWARD_FRAMEBUFFER = null;
            FORWARD_FRAMEBUFFER = new ForwardFramebuffer();
            RENDERING_FRAMEBUFFER = FORWARD_FRAMEBUFFER;
        }

        if (MS_FORWARD_FRAMEBUFFER != null) {
            MS_FORWARD_FRAMEBUFFER.resize(width, height, MSAA_SAMPLES);
        }
        if (FORWARD_FRAMEBUFFER != null) {
            FORWARD_FRAMEBUFFER.resize(width, height);
        }
        TONEMAP_FRAMEBUFFER.resize(width, height);
        for (int i = 0; i < MIP_FRAMEBUFFERS.length; i++) {
            MIP_FRAMEBUFFERS[i].resize(width, height);
        }
        OPAQUE_FRAMEBUFFER.resize(width, height);
    }

    public static void copyColorBufferToOpaque() {
        glBindFramebuffer(GL_FRAMEBUFFER, OPAQUE_FRAMEBUFFER.framebuffer());
        glClear(GL_DEPTH_BUFFER_BIT);
        if (RENDERING_FRAMEBUFFER instanceof MSForwardFramebuffer msaa) {
            ResolveFilter.render(msaa.colorBuffer(), msaa.depthBuffer(), msaa.getSamples());
        } else if (RENDERING_FRAMEBUFFER instanceof ForwardFramebuffer forward) {
            CopyFilter.render(forward.colorBuffer(), forward.depthBuffer());
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

        glBindFramebuffer(GL_FRAMEBUFFER, TONEMAP_FRAMEBUFFER.framebuffer());
        int lut = (Pipeline.COLOR_LUT == null ? LUT.NEUTRAL.texture() : Pipeline.COLOR_LUT.texture());
        if (MS_FORWARD_FRAMEBUFFER == null) {
            TonemappingFilter.render(
                    EXPOSURE, GAMMA, lut,
                    FORWARD_FRAMEBUFFER.colorBuffer()
            );
        } else {
            TonemappingFilter.renderMSAA(
                    EXPOSURE, GAMMA, lut,
                    MS_FORWARD_FRAMEBUFFER.getSamples(), MS_FORWARD_FRAMEBUFFER.colorBuffer()
            );
        }

        if (WATER_EFFECT) {
            BlurDownsample.prepare();
            for (int i = 0; i < MIP_FRAMEBUFFERS.length; i++) {
                glBindFramebuffer(GL_FRAMEBUFFER, MIP_FRAMEBUFFERS[i].framebuffer());
                glViewport(0, 0, MIP_FRAMEBUFFERS[i].getWidth(), MIP_FRAMEBUFFERS[i].getHeight());

                int tex = i == 0
                        ? TONEMAP_FRAMEBUFFER.colorBuffer()
                        : MIP_FRAMEBUFFERS[i - 1].colorBuffer();
                BlurDownsample.render(tex);
            }
            BlurDownsample.done();

            BlurUpsample.prepare();
            for (int i = MIP_FRAMEBUFFERS.length - 1; i >= 0; i--) {
                int fbo = i == 0
                        ? TONEMAP_FRAMEBUFFER.framebuffer()
                        : MIP_FRAMEBUFFERS[i - 1].framebuffer();
                glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                int width = i == 0 ? RENDERING_FRAMEBUFFER.getWidth() : MIP_FRAMEBUFFERS[i - 1].getWidth();
                int height = i == 0 ? RENDERING_FRAMEBUFFER.getHeight() : MIP_FRAMEBUFFERS[i - 1].getHeight();
                glViewport(0, 0, width, height);
                BlurUpsample.render(MIP_FRAMEBUFFERS[i].colorBuffer());
            }
            BlurUpsample.done();

            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            WaterFilter.render(TONEMAP_FRAMEBUFFER.colorBuffer(), 0.5f, 0.5f, -0.5f, -0.5f, 0.05f);
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            FXAAFilter.render(FXAA_QUALITY, TONEMAP_FRAMEBUFFER.colorBuffer());
        }
        
        glEnable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    private Pipeline() {

    }
}
