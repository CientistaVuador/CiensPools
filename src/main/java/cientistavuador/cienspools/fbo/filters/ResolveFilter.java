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
package cientistavuador.cienspools.fbo.filters;

import cientistavuador.cienspools.fbo.filters.mesh.ScreenTriangle;
import cientistavuador.cienspools.util.BetterUniformSetter;
import cientistavuador.cienspools.util.ProgramCompiler;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class ResolveFilter {
    public static final int SHADER_PROGRAM = ProgramCompiler.compile(
            """
            #version 330 core
            
            layout (location = 0) in vec2 vertexPosition;
            layout (location = 1) in vec2 vertexUV;
            
            out vec2 UV;
            
            void main() {
                UV = vertexUV;
                gl_Position = vec4(vertexPosition.x, vertexPosition.y, -1.0, 1.0);
            }
            """,
            """
            #version 330 core
            
            uniform sampler2DMS colorTexture;
            uniform sampler2DMS depthTexture;
            
            uniform int colorSamples;
            
            in vec2 UV;
            
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                ivec2 colorPoint = ivec2(floor(vec2(textureSize(colorTexture)) * UV));
                ivec2 depthPoint = ivec2(floor(vec2(textureSize(depthTexture)) * UV));
                
                float colorWeight = 1.0 / float(colorSamples);
                
                vec4 sum = vec4(0.0);
                for (int i = 0; i < colorSamples; i++) {
                    sum += texelFetch(colorTexture, colorPoint, i) * colorWeight;
                }
                
                outputColor = sum;
                gl_FragDepth = texelFetch(depthTexture, depthPoint, 0).r;
            }
            """
    );
    
    public static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);
    
    public static void render(int colorTexture, int depthTexture, int samples) {
        glClear(GL_DEPTH_BUFFER_BIT);
        glUseProgram(SHADER_PROGRAM);
        glBindVertexArray(ScreenTriangle.VAO);
        
        UNIFORMS
                .uniform1i("colorTexture", 0)
                .uniform1i("depthTexture", 1)
                .uniform1i("colorSamples", samples)
                ;
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, colorTexture);
        
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, depthTexture);
        
        glDrawArrays(GL_TRIANGLES, 0, ScreenTriangle.NUMBER_OF_VERTICES);
        
        glBindVertexArray(0);
        glUseProgram(0);
    }
    
    public static void init() {
        
    }
    
    private ResolveFilter() {
        
    }
}
