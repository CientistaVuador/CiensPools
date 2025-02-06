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

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.fbo.filters.mesh.ScreenTriangle;
import cientistavuador.cienspools.util.BetterUniformSetter;
import cientistavuador.cienspools.util.ProgramCompiler;
import java.util.Objects;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class KernelFilter {
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
            
            uniform sampler2D inputTexture;
            uniform float kernel[9];
            
            in vec2 UV;
            
            layout (location = 0) out vec4 outputColor;
            
            vec4 fetchPixel(sampler2D tex, ivec2 pos, ivec2 minValue, ivec2 maxValue) {
                return texelFetch(tex, clamp(pos, minValue, maxValue), 0);
            }
            
            void main() {
                const ivec2 minValue = ivec2(0, 0);
                const ivec2 offsets[9] = ivec2[](
                    ivec2(-1, 1), ivec2(0, 1), ivec2(1, 1),
                    ivec2(-1, 0), ivec2(0, 0), ivec2(1, 0),
                    ivec2(-1, -1), ivec2(0, -1), ivec2(1, -1)
                );
                
                ivec2 texSize = textureSize(inputTexture, 0).xy;
                ivec2 maxValue = texSize - ivec2(1, 1);
                ivec2 pos = ivec2(UV * vec2(texSize));
                
                vec4 sum = vec4(0.0);
                for (int i = 0; i < kernel.length(); i++) {
                    sum += fetchPixel(inputTexture, pos + offsets[i], minValue, maxValue) * kernel[i];
                }
                
                outputColor = sum;
            }
            """
    );
    
    public static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);
    
    public static void render(int inputTexture, float[] kernel) {
        Objects.requireNonNull(kernel, "kernel is null.");
        if (kernel.length != 9) {
            throw new IllegalArgumentException("kernel must be 3x3 (9 elements)");
        }
        
        glUseProgram(SHADER_PROGRAM);
        glBindVertexArray(ScreenTriangle.VAO);
        
        UNIFORMS
                .uniform1i("inputTexture", 0)
                ;
        
        for (int i = 0; i < kernel.length; i++) {
            UNIFORMS.uniform1f("kernel["+i+"]", kernel[i]);
        }
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTexture);
        
        glDrawArrays(GL_TRIANGLES, 0, ScreenTriangle.NUMBER_OF_VERTICES);
        
        Main.NUMBER_OF_DRAWCALLS++;
        Main.NUMBER_OF_VERTICES += ScreenTriangle.NUMBER_OF_VERTICES;
        
        glBindVertexArray(0);
        glUseProgram(0);
    }
    
    public static void init() {
        
    }
    
    private KernelFilter() {
        
    }
}
