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

import cientistavuador.cienspools.fbo.filters.ScreenTriangle;
import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.util.BetterUniformSetter;
import cientistavuador.cienspools.util.ProgramCompiler;
import static org.lwjgl.opengl.GL33.*;

/**
 *
 * @author Cien
 */
public class FXAAFilter {
    
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
            
            #define FXAA_PC 1
            #define FXAA_QUALITY__PRESET 12
            #define FXAA_GREEN_AS_LUMA 0
            #define FXAA_GLSL_130 1
            
            #define FXAA_QUALITY__SUBPIX 0.75
            #define FXAA_QUALITY__EDGE_THRESHOLD 0.333
            #define FXAA_QUALITY__EDGE_THRESHOLD_MIN 0.0833
            
            #extension GL_ARB_gpu_shader5 : enable
            
            #include "Fxaa3_11.h"
            
            uniform vec2 screenSize;
            uniform sampler2D inputTexture;
            
            in vec2 UV;
            
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                outputColor = vec4(FxaaPixelShader(UV, vec4(0.0), inputTexture, inputTexture, inputTexture,  1.0 / screenSize, vec4(0.0), vec4(0.0), vec4(0.0), FXAA_QUALITY__SUBPIX, FXAA_QUALITY__EDGE_THRESHOLD, FXAA_QUALITY__EDGE_THRESHOLD_MIN, 0.0, 0.0, 0.0, vec4(0.0)).rgb, 1.0);
            }
            """
    );
    
    public static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);
    
    public static void render(int width, int height, int inputTexture) {
        glUseProgram(SHADER_PROGRAM);
        glBindVertexArray(ScreenTriangle.VAO);
        
        UNIFORMS
                .uniform2f("screenSize", width, height)
                .uniform1i("inputTexture", 0)
                ;
        
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
    
    private FXAAFilter() {
        
    }
}
