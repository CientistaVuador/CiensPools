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
import cientistavuador.cienspools.util.BetterUniformSetter;
import cientistavuador.cienspools.util.ProgramCompiler;
import static org.lwjgl.opengl.GL33.*;

/**
 *
 * @author Cien
 */
public class OutputFilter {
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
            
            uniform vec2 screenSize;
            
            uniform float exposure;
            uniform float gamma;
            uniform sampler3D LUT;
            uniform sampler2D inputTexture;
            
            in vec2 UV;
            
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                vec4 color = texture(inputTexture, UV);
                
                color.rgb = vec3(1.0) - exp(-color.rgb * exposure);
                color.rgb = pow(color.rgb, vec3(1.0/gamma));
                color.rgb = texture(LUT, color.rgb).rgb;
                const float noise = 0.5 / 255.0;
                vec2 coords = gl_FragCoord.xy / screenSize;
                color.rgb += mix(-noise, noise,
                    fract(sin(dot(coords, vec2(12.9898,78.233))) * 43758.5453));
                color.rgb = clamp(color.rgb, 0.0, 1.0);
                color.a = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                
                outputColor = color;
            }
            """
    );
    
    public static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);
    
    public static void render(
            int width, int height,
            float exposure, float gamma, int LUT,
            int inputTexture) {
        glUseProgram(SHADER_PROGRAM);
        glBindVertexArray(ScreenTriangle.VAO);
        
        UNIFORMS
                .uniform2f("screenSize", width, height)
                .uniform1f("exposure", exposure)
                .uniform1f("gamma", gamma)
                .uniform1i("LUT", 0)
                .uniform1i("inputTexture", 1)
                ;
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_3D, LUT);
        
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, inputTexture);
        
        glDrawArrays(GL_TRIANGLES, 0, ScreenTriangle.NUMBER_OF_VERTICES);
        
        Main.NUMBER_OF_DRAWCALLS++;
        Main.NUMBER_OF_VERTICES += ScreenTriangle.NUMBER_OF_VERTICES;
        
        glBindVertexArray(0);
        glUseProgram(0);
    }
    
    public static void init() {
        
    }
    
    private OutputFilter() {
        
    }
}
