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
package cientistavuador.cienspools.fbo;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.util.ObjectCleaner;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class MSForwardFramebuffer {

    private int width = 1;
    private int height = 1;
    private int samples = 1;

    private class WrappedState {
        int framebuffer = 0;

        int colorBuffer = 0;
        int depthBuffer = 0;
    }

    private final WrappedState state = new WrappedState();

    public MSForwardFramebuffer() {
        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedState finalState = this.state;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                if (finalState.colorBuffer == 0) {
                    return;
                }

                glDeleteFramebuffers(finalState.framebuffer);
                glDeleteTextures(finalState.colorBuffer);
                glDeleteTextures(finalState.depthBuffer);

                finalState.framebuffer = 0;
                finalState.colorBuffer = 0;
                finalState.depthBuffer = 0;
            });
        });
    }

    private void updateColorBuffer(int colorBuffer) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, colorBuffer);

        glTexImage2DMultisample(
                GL_TEXTURE_2D_MULTISAMPLE, this.samples,
                GL_RGBA16F, this.width, this.height,
                false
        );
        
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, 0);
    }

    private void updateDepthBuffer(int depthBuffer) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, depthBuffer);

        glTexImage2DMultisample(
                GL_TEXTURE_2D_MULTISAMPLE, this.samples,
                GL_DEPTH_COMPONENT32F, this.width, this.height,
                false
        );
        
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, 0);
    }
    
    private void initialize() {
        if (this.state.colorBuffer != 0) {
            return;
        }

        int colorBuffer = glGenTextures();
        this.state.colorBuffer = colorBuffer;

        updateColorBuffer(colorBuffer);
        
        int depthBuffer = glGenTextures();
        this.state.depthBuffer = depthBuffer;
        
        updateDepthBuffer(depthBuffer);

        int framebuffer = glGenFramebuffers();
        this.state.framebuffer = framebuffer;

        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);

        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, colorBuffer, 0);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthBuffer, 0);

        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_NONE);
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public int colorBuffer() {
        initialize();
        return this.state.colorBuffer;
    }
    
    public int depthBuffer() {
        initialize();
        return this.state.depthBuffer;
    }

    public int framebuffer() {
        initialize();
        return this.state.framebuffer;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getSamples() {
        return samples;
    }

    public void resize(int width, int height, int samples) {
        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }
        if (samples <= 0) {
            samples = 1;
        }
        
        this.width = width;
        this.height = height;
        this.samples = samples;
        updateColorBuffer(colorBuffer());
        updateDepthBuffer(depthBuffer());
    }
    
    public void manualFree() {
        final WrappedState finalState = this.state;

        if (finalState.framebuffer == 0) {
            return;
        }

        glDeleteFramebuffers(finalState.framebuffer);
        glDeleteTextures(finalState.colorBuffer);
        glDeleteTextures(finalState.depthBuffer);

        finalState.framebuffer = 0;
        finalState.colorBuffer = 0;
        finalState.depthBuffer = 0;
    }
}
