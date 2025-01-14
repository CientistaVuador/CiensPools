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
package cientistavuador.cienspools.newrendering;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.Pipeline;
import cientistavuador.cienspools.camera.PerspectiveCamera;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class NCubemapRenderer {

    public static NCubemap render(
            N3DObjectRenderer renderer,
            String name, NCubemapBox info, int size, int ssaaScale
    ) {
        if (ssaaScale < 1) {
            throw new IllegalArgumentException("SSAA Scale must be larger or equal to 1; " + ssaaScale);
        }
        
        renderer = new N3DObjectRenderer(renderer);
        renderer.setReflectionsEnabled(false);
        
        int fboSize = size * ssaaScale;
        
        float[] cameraRotations = {
            0f, 0f, 180f,
            0f, -180f, 180f,
            90f, -90f, 0f,
            -90f, -90f, 0f,
            0f, 90f, -180f,
            0f, -90f, -180f
        };
        PerspectiveCamera camera = new PerspectiveCamera();
        camera.setPosition(info.getCubemapPosition());
        camera.setDimensions(1f, 1f);
        camera.setFov(90f);
        renderer.setCamera(camera);

        float[][] sides = new float[NCubemap.SIDES][];
        
        glBindFramebuffer(GL_FRAMEBUFFER, Pipeline.MS_FRAMEBUFFER.framebuffer());
        //Pipeline.HDR_FRAMEBUFFER.resize(fboSize, fboSize);
        glViewport(0, 0, fboSize, fboSize);
        
        for (int i = 0; i < NCubemap.SIDES; i++) {
            float pitch = cameraRotations[(i * 3) + 0];
            float yaw = cameraRotations[(i * 3) + 1];
            float roll = cameraRotations[(i * 3) + 2];
            camera.setRotation(pitch, yaw, roll);
            
            float[] ssaaSide = new float[fboSize * fboSize * 3];

            glClear(GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
            renderer.render();
            
            //Pipeline.HDR_FRAMEBUFFER.flip();
            glReadPixels(0, 0, fboSize, fboSize, GL_RGB, GL_FLOAT, ssaaSide);
            //Pipeline.HDR_FRAMEBUFFER.flip();
            
            float[] side = new float[size * size * 3];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float r = 0f;
                    float g = 0f;
                    float b = 0f;
                    for (int yOffset = 0; yOffset < ssaaScale; yOffset++) {
                        for (int xOffset = 0; xOffset < ssaaScale; xOffset++) {
                            int trueX = (x * ssaaScale) + xOffset;
                            int trueY = (y * ssaaScale) + yOffset;

                            r += ssaaSide[0 + (trueX * 3) + (trueY * fboSize * 3)];
                            g += ssaaSide[1 + (trueX * 3) + (trueY * fboSize * 3)];
                            b += ssaaSide[2 + (trueX * 3) + (trueY * fboSize * 3)];
                        }
                    }
                    float inv = 1f / (ssaaScale * ssaaScale);
                    r *= inv;
                    g *= inv;
                    b *= inv;
                    
                    side[0 + (x * 3) + (y * size * 3)] = r;
                    side[1 + (x * 3) + (y * size * 3)] = g;
                    side[2 + (x * 3) + (y * size * 3)] = b;
                }
            }
            
            sides[i] = side;
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        return NCubemapImporter.create(name, null, info, size, sides);
    }
    
    public static NCubemap render(
            N3DObjectRenderer renderer,
            String name, NCubemapBox info, int size
    ) {
        return render(renderer, name, info, size, 4);
    }

    private NCubemapRenderer() {

    }
}
