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
import java.util.Map;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class CopyFilter {
    public static final String VERTEX_SHADER = 
            """
            layout (location = 0) in vec2 vertexPosition;
            layout (location = 1) in vec2 vertexUV;
            
            out vec2 UV;
            
            void main() {
                UV = vertexUV;
                gl_Position = vec4(vertexPosition.x, vertexPosition.y, -1.0, 1.0);
            }
            """;
    
    public static final String FRAGMENT_SHADER =
            """
            uniform sampler2D colorTexture;
            #ifdef VARIANT_DEPTH
            uniform sampler2D depthTexture;
            #endif
            
            in vec2 UV;
            
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                outputColor = texture(colorTexture, UV);
                #ifdef VARIANT_DEPTH
                gl_FragDepth = texture(depthTexture, UV).r;
                #endif
            }
            """;
    
    private static final Map<String, Integer> SHADERS = ProgramCompiler.compile(
            VERTEX_SHADER, FRAGMENT_SHADER,
            new String[] {"DEPTH", "NO_DEPTH"},
            new ProgramCompiler.ShaderConstant[] {}
    );
    
    public static final BetterUniformSetter SHADER_PROGRAM =
            new BetterUniformSetter(SHADERS.get("DEPTH"));
    public static final BetterUniformSetter SHADER_PROGRAM_NO_DEPTH =
            new BetterUniformSetter(SHADERS.get("NO_DEPTH"));
    
    public static void render(int colorTexture, int depthTexture) {
        glUseProgram(SHADER_PROGRAM.getProgram());
        glBindVertexArray(ScreenTriangle.VAO);
        
        SHADER_PROGRAM
                .uniform1i("colorTexture", 0)
                .uniform1i("depthTexture", 1)
                ;
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        
        glDrawArrays(GL_TRIANGLES, 0, ScreenTriangle.NUMBER_OF_VERTICES);
        
        glBindVertexArray(0);
        glUseProgram(0);
    }
    
    public static void render(int colorTexture) {
        glUseProgram(SHADER_PROGRAM_NO_DEPTH.getProgram());
        glBindVertexArray(ScreenTriangle.VAO);
        
        SHADER_PROGRAM_NO_DEPTH
                .uniform1i("colorTexture", 0)
                ;
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        
        glDrawArrays(GL_TRIANGLES, 0, ScreenTriangle.NUMBER_OF_VERTICES);
        
        glBindVertexArray(0);
        glUseProgram(0);
    }
    
    public static void init() {
        
    }
    
    private CopyFilter() {
        
    }
}
