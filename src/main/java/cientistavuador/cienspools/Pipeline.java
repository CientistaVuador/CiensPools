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
import cientistavuador.cienspools.fbo.filters.DefaultKernelFilters;
import cientistavuador.cienspools.fbo.filters.FXAAFilter;
import cientistavuador.cienspools.fbo.filters.FXAAQuality;
import cientistavuador.cienspools.fbo.filters.KernelFilter;
import cientistavuador.cienspools.fbo.filters.TonemappingFilter;
import cientistavuador.cienspools.fbo.filters.ResolveFilter;
import cientistavuador.cienspools.fbo.filters.SharpenQuality;
import cientistavuador.cienspools.fbo.filters.WaterFilter;
import cientistavuador.cienspools.lut.LUT;
import cientistavuador.cienspools.util.E8Image;
import cientistavuador.cienspools.util.RasterUtils;
import java.util.Objects;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class Pipeline {
    
    private static MSForwardFramebuffer MS_FORWARD_FRAMEBUFFER = null;
    private static ForwardFramebuffer FORWARD_FRAMEBUFFER = new ForwardFramebuffer();
    public static Framebuffer RENDERING_FRAMEBUFFER = FORWARD_FRAMEBUFFER;

    public static final ForwardFramebuffer OPAQUE_FRAMEBUFFER = new ForwardFramebuffer();

    public static final DepthlessForwardFramebuffer TONEMAP_FRAMEBUFFER = new DepthlessForwardFramebuffer();
    public static final DepthlessForwardFramebuffer FXAA_OUTPUT = new DepthlessForwardFramebuffer(true);
    
    public static final MipFramebuffer[] MIP_FRAMEBUFFERS = new MipFramebuffer[]{
        new MipFramebuffer(1),
        new MipFramebuffer(2)
    };

    public static float GAMMA = 1.4f;
    public static float EXPOSURE = 3.0f;
    public static LUT COLOR_LUT = LUT.NEUTRAL;

    public static MSAAQuality MSAA_QUALITY = MSAAQuality.MEDIUM_4X;
    public static FXAAQuality FXAA_QUALITY = FXAAQuality.HIGH;
    public static SharpenQuality SHARPEN_QUALITY = SharpenQuality.LOW;
    
    private static MSAAQuality LAST_MSAA_QUALITY = MSAA_QUALITY;
    
    public static boolean WATER_EFFECT = false;
    
    public static void init() {
        
    }

    private static void updateFramebuffers(int width, int height) {
        if (RENDERING_FRAMEBUFFER instanceof ForwardFramebuffer && !MSAAQuality.OFF_1X.equals(MSAA_QUALITY)) {
            FORWARD_FRAMEBUFFER.manualFree();
            FORWARD_FRAMEBUFFER = null;
            MS_FORWARD_FRAMEBUFFER = new MSForwardFramebuffer();
            RENDERING_FRAMEBUFFER = MS_FORWARD_FRAMEBUFFER;
        }
        if (RENDERING_FRAMEBUFFER instanceof MSForwardFramebuffer && MSAAQuality.OFF_1X.equals(MSAA_QUALITY)) {
            MS_FORWARD_FRAMEBUFFER.manualFree();
            MS_FORWARD_FRAMEBUFFER = null;
            FORWARD_FRAMEBUFFER = new ForwardFramebuffer();
            RENDERING_FRAMEBUFFER = FORWARD_FRAMEBUFFER;
        }

        if (MS_FORWARD_FRAMEBUFFER != null) {
            MS_FORWARD_FRAMEBUFFER.resize(width, height, MSAA_QUALITY.getSamples());
        }
        if (FORWARD_FRAMEBUFFER != null) {
            FORWARD_FRAMEBUFFER.resize(width, height);
        }
        TONEMAP_FRAMEBUFFER.resize(width, height);
        for (int i = 0; i < MIP_FRAMEBUFFERS.length; i++) {
            MIP_FRAMEBUFFERS[i].resize(width, height);
        }
        OPAQUE_FRAMEBUFFER.resize(width, height);
        FXAA_OUTPUT.resize(width, height);
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
        if (!Objects.equals(LAST_MSAA_QUALITY, MSAA_QUALITY)) {
            LAST_MSAA_QUALITY = MSAA_QUALITY;
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
            glBindFramebuffer(GL_FRAMEBUFFER, FXAA_OUTPUT.framebuffer());
            FXAAFilter.render(FXAA_QUALITY, TONEMAP_FRAMEBUFFER.colorBuffer());
            
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            KernelFilter.render(FXAA_OUTPUT.colorBuffer(), SHARPEN_QUALITY.getKernel());
        }
        
        glEnable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    private Pipeline() {

    }
}
