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
public class FXAAFilter {
    
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
            #ifndef VARIANT_OFF
                #extension GL_ARB_gpu_shader5 : enable
                
                #define FXAA_PC 1
                #define FXAA_GREEN_AS_LUMA 0
                #define FXAA_GLSL_130 1
                
                #ifdef VARIANT_LOW
                    #define FXAA_QUALITY__PRESET 12
                    #define FXAA_QUALITY__SUBPIX 0.25
                    #define FXAA_QUALITY__EDGE_THRESHOLD 0.250
                    #define FXAA_QUALITY__EDGE_THRESHOLD_MIN 0.0833
                #endif
                
                #ifdef VARIANT_MEDIUM
                    #define FXAA_QUALITY__PRESET 23
                    #define FXAA_QUALITY__SUBPIX 0.50
                    #define FXAA_QUALITY__EDGE_THRESHOLD 0.166
                    #define FXAA_QUALITY__EDGE_THRESHOLD_MIN 0.0625
                #endif
                
                #ifdef VARIANT_HIGH
                    #define FXAA_QUALITY__PRESET 29
                    #define FXAA_QUALITY__SUBPIX 0.75
                    #define FXAA_QUALITY__EDGE_THRESHOLD 0.125
                    #define FXAA_QUALITY__EDGE_THRESHOLD_MIN 0.0312
                #endif
                
                #ifdef VARIANT_ULTRA
                    #define FXAA_QUALITY__PRESET 39
                    #define FXAA_QUALITY__SUBPIX 1.00
                    #define FXAA_QUALITY__EDGE_THRESHOLD 0.063
                    #define FXAA_QUALITY__EDGE_THRESHOLD_MIN 0.0312
                #endif
                
                #include "Fxaa3_11.h"
            #endif
            
            uniform sampler2D inputTexture;
             
            in vec2 UV;
            
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                #ifndef VARIANT_OFF
                vec2 screenSize = vec2(textureSize(inputTexture, 0));
                outputColor = vec4(FxaaPixelShader(UV, vec4(0.0), inputTexture, inputTexture, inputTexture,  1.0 / screenSize, vec4(0.0), vec4(0.0), vec4(0.0), FXAA_QUALITY__SUBPIX, FXAA_QUALITY__EDGE_THRESHOLD, FXAA_QUALITY__EDGE_THRESHOLD_MIN, 0.0, 0.0, 0.0, vec4(0.0)).rgb, 1.0);
                #else
                outputColor = texture(inputTexture, UV);
                #endif
            }
            """;
    
    private static final Map<String, Integer> SHADERS = ProgramCompiler.compile(
            VERTEX_SHADER, FRAGMENT_SHADER,
            new String[] {"OFF", "LOW", "MEDIUM", "HIGH", "ULTRA"},
            new ProgramCompiler.ShaderConstant[] {}
    );
    
    public static final BetterUniformSetter OFF = new BetterUniformSetter(SHADERS.get("OFF"));
    public static final BetterUniformSetter LOW = new BetterUniformSetter(SHADERS.get("LOW"));
    public static final BetterUniformSetter MEDIUM = new BetterUniformSetter(SHADERS.get("MEDIUM"));
    public static final BetterUniformSetter HIGH = new BetterUniformSetter(SHADERS.get("HIGH"));
    public static final BetterUniformSetter ULTRA = new BetterUniformSetter(SHADERS.get("ULTRA"));
    
    public static void render(FXAAQuality quality, int inputTexture) {
        if (quality == null) {
            quality = FXAAQuality.OFF;
        }
        BetterUniformSetter shader;
        switch (quality) {
            case LOW -> {
                shader = LOW;
            }
            case MEDIUM -> {
                shader = MEDIUM;
            }
            case HIGH -> {
                shader = HIGH;
            }
            case ULTRA -> {
                shader = ULTRA;
            }
            default -> {
                shader = OFF;
            }
        }
        
        glUseProgram(shader.getProgram());
        glBindVertexArray(ScreenTriangle.VAO);
        
        shader
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
