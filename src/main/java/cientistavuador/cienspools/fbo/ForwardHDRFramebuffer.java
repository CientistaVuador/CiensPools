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
public class ForwardHDRFramebuffer {

    private int width = 0;
    private int height = 0;

    private class WrappedState {

        boolean flipped = false;
        int framebuffer = 0;

        int colorBufferWrite = 0;
        int colorBufferRead = 0;
        int depthBuffer = 0;
    }

    private final WrappedState state = new WrappedState();

    public ForwardHDRFramebuffer() {
        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedState finalState = this.state;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                if (finalState.colorBufferWrite == 0) {
                    return;
                }

                glDeleteFramebuffers(finalState.framebuffer);
                glDeleteTextures(finalState.colorBufferWrite);
                glDeleteTextures(finalState.colorBufferRead);
                glDeleteTextures(finalState.depthBuffer);

                finalState.framebuffer = 0;
                finalState.colorBufferWrite = 0;
                finalState.colorBufferRead = 0;
                finalState.depthBuffer = 0;
            });
        });
    }

    private void updateColorBuffer(int colorBuffer) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorBuffer);

        glTexImage2D(
                GL_TEXTURE_2D, 0,
                GL_RGBA16F, this.width, this.height,
                0,
                GL_RGBA, GL_FLOAT, 0
        );

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void updateDepthBuffer(int depthBuffer) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, depthBuffer);

        glTexImage2D(
                GL_TEXTURE_2D, 0,
                GL_DEPTH_COMPONENT32F, this.width, this.height,
                0,
                GL_DEPTH_COMPONENT, GL_FLOAT, 0
        );

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void defaultTextureConfiguration(int texture, boolean depth) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        if (!depth) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } else {
            glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, new float[]{1f, 1f, 1f, 1f});

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        }

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void initialize() {
        if (this.state.colorBufferWrite != 0) {
            return;
        }

        int colorBufferWrite = glGenTextures();
        this.state.colorBufferWrite = colorBufferWrite;

        updateColorBuffer(colorBufferWrite);
        defaultTextureConfiguration(colorBufferWrite, false);

        int colorBufferRead = glGenTextures();
        this.state.colorBufferRead = colorBufferRead;

        updateColorBuffer(colorBufferRead);
        defaultTextureConfiguration(colorBufferRead, false);

        int depthBuffer = glGenTextures();
        this.state.depthBuffer = depthBuffer;

        updateDepthBuffer(depthBuffer);
        defaultTextureConfiguration(depthBuffer, true);

        int framebuffer = glGenFramebuffers();
        this.state.framebuffer = framebuffer;

        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);

        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, colorBufferWrite, 0);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, colorBufferRead, 0);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthBuffer, 0);

        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT1);
        this.state.flipped = false;

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public int colorBufferWrite() {
        initialize();
        return this.state.colorBufferWrite;
    }

    public int colorBufferRead() {
        initialize();
        return this.state.colorBufferRead;
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

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        updateColorBuffer(colorBufferWrite());
        updateColorBuffer(colorBufferRead());
        updateDepthBuffer(depthBuffer());
    }

    public void flip() {
        initialize();
        
        int write = this.state.colorBufferWrite;
        int read = this.state.colorBufferRead;
        this.state.colorBufferWrite = read;
        this.state.colorBufferRead = write;

        this.state.flipped = !this.state.flipped;
        if (this.state.flipped) {
            glDrawBuffer(GL_COLOR_ATTACHMENT1);
            glReadBuffer(GL_COLOR_ATTACHMENT0);
        } else {
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glReadBuffer(GL_COLOR_ATTACHMENT1);
        }
    }

    public void manualFree() {
        final WrappedState finalState = this.state;

        if (finalState.framebuffer == 0) {
            return;
        }

        glDeleteFramebuffers(finalState.framebuffer);
        glDeleteTextures(finalState.colorBufferWrite);
        glDeleteTextures(finalState.colorBufferRead);
        glDeleteTextures(finalState.depthBuffer);

        finalState.framebuffer = 0;
        finalState.colorBufferWrite = 0;
        finalState.colorBufferRead = 0;
        finalState.depthBuffer = 0;
    }
}
