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
import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.util.BetterUniformSetter;
import cientistavuador.cienspools.util.ProgramCompiler;
import java.util.Map;
import static org.lwjgl.opengl.GL33.*;

/**
 *
 * @author Cien
 */
public class TonemappingFilter {

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
            uniform float exposure;
            uniform float gamma;
            uniform sampler3D LUT;
            
            #ifdef VARIANT_NO_MSAA
            uniform sampler2D inputTexture;
            #endif
            
            #ifdef VARIANT_MSAA
            uniform sampler2DMS inputTexture;
            uniform int inputSamples;
            #endif
            
            #include "Tonemapping.h"
            
            in vec2 UV;
            
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                vec4 color = vec4(0.0);
                
                #ifdef VARIANT_NO_MSAA
                color = texture(inputTexture, UV);
                color = applyTonemapping(UV, exposure, gamma, LUT, color);
                #endif
                
                #ifdef VARIANT_MSAA
                ivec2 inputPos = ivec2(floor(vec2(textureSize(inputTexture)) * UV));
                float weight = 1.0 / float(inputSamples);
                for (int i = 0; i < inputSamples; i++) {
                    vec4 hdrColor = texelFetch(inputTexture, inputPos, i);
                    color += applyTonemapping(UV, exposure, gamma, LUT, hdrColor) * weight;
                }
                #endif
                
                outputColor = color;
            }
            """;
    
    private static final Map<String, Integer> SHADERS = ProgramCompiler.compile(
            VERTEX_SHADER, FRAGMENT_SHADER,
            new String[] {
                "NO_MSAA", "MSAA"
            },
            new ProgramCompiler.ShaderConstant[] {}
    );

    public static final BetterUniformSetter SHADER_PROGRAM =
            new BetterUniformSetter(SHADERS.get("NO_MSAA"));
    public static final BetterUniformSetter SHADER_PROGRAM_MSAA =
            new BetterUniformSetter(SHADERS.get("MSAA"));

    public static void render(
            float exposure, float gamma, int LUT,
            int inputTexture) {
        glUseProgram(SHADER_PROGRAM.getProgram());
        glBindVertexArray(ScreenTriangle.VAO);
        
        SHADER_PROGRAM
                .uniform1f("exposure", exposure)
                .uniform1f("gamma", gamma)
                .uniform1i("LUT", 0)
                .uniform1i("inputTexture", 1);
        
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
    
    public static void renderMSAA(
            float exposure, float gamma, int LUT,
            int samples, int inputTexture) {
        glUseProgram(SHADER_PROGRAM_MSAA.getProgram());
        glBindVertexArray(ScreenTriangle.VAO);
        
        SHADER_PROGRAM_MSAA
                .uniform1f("exposure", exposure)
                .uniform1f("gamma", gamma)
                .uniform1i("LUT", 0)
                .uniform1i("inputTexture", 1)
                .uniform1i("inputSamples", samples)
                ;
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_3D, LUT);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, inputTexture);

        glDrawArrays(GL_TRIANGLES, 0, ScreenTriangle.NUMBER_OF_VERTICES);

        Main.NUMBER_OF_DRAWCALLS++;
        Main.NUMBER_OF_VERTICES += ScreenTriangle.NUMBER_OF_VERTICES;

        glBindVertexArray(0);
        glUseProgram(0);
    }

    public static void init() {

    }

    private TonemappingFilter() {

    }
}
