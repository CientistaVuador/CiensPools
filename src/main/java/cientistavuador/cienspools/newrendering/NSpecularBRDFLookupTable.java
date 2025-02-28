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
import cientistavuador.cienspools.util.ProgramCompiler;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class NSpecularBRDFLookupTable {

    public static final int TEXTURE_SIZE = 512;
    public static final int SPECULAR_BRDF_TEXTURE;

    public static void init() {

    }
    
    private static float[] renderBRDF() {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, new float[] {
            -1f, -1f, 0f,  0f, 0f,
            +1f, -1f, 0f,  1f, 0f,
            -1f, +1f, 0f,  0f, 1f,
            
            -1f, +1f, 0f,  0f, 1f,
            +1f, -1f, 0f,  1f, 0f,
            +1f, +1f, 0f,  1f, 1f,
        }, GL_STATIC_DRAW);
        
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, (3 + 2) * Float.BYTES, 0);
        
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, (3 + 2) * Float.BYTES, 3 * Float.BYTES);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        
        glBindVertexArray(0);
        
        int shader = ProgramCompiler.compile(
                """
                #version 330 core
                
                layout (location = 0) in vec3 vertexPosition;
                layout (location = 1) in vec2 vertexUV;
                
                out vec2 uv;
                
                void main() {
                    uv = vertexUV;
                    gl_Position = vec4(vertexPosition, 1.0);
                }
                """,
                """
                #version 330 core
                
                in vec2 uv;
                
                const float PI = 3.14159265359;
                
                float RadicalInverse_VdC(uint bits) 
                {
                    bits = (bits << 16u) | (bits >> 16u);
                    bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
                    bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
                    bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
                    bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
                    return float(bits) * 2.3283064365386963e-10; // / 0x100000000
                }
                
                vec2 Hammersley(uint i, uint N)
                {
                    return vec2(float(i)/float(N), RadicalInverse_VdC(i));
                }
                
                float GeometrySchlickGGX(float NdotV, float roughness)
                {
                    float a = roughness;
                    float k = (a * a) / 2.0;
                
                    float nom   = NdotV;
                    float denom = NdotV * (1.0 - k) + k;
                
                    return nom / denom;
                }
                
                float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
                {
                    float NdotV = max(dot(N, V), 0.0);
                    float NdotL = max(dot(N, L), 0.0);
                    float ggx2 = GeometrySchlickGGX(NdotV, roughness);
                    float ggx1 = GeometrySchlickGGX(NdotL, roughness);
                
                    return ggx1 * ggx2;
                } 
                
                vec3 ImportanceSampleGGX(vec2 Xi, vec3 N, float roughness)
                {
                    float a = roughness*roughness;
                	
                    float phi = 2.0 * PI * Xi.x;
                    float cosTheta = sqrt((1.0 - Xi.y) / (1.0 + (a*a - 1.0) * Xi.y));
                    float sinTheta = sqrt(1.0 - cosTheta*cosTheta);
                	
                    // from spherical coordinates to cartesian coordinates
                    vec3 H;
                    H.x = cos(phi) * sinTheta;
                    H.y = sin(phi) * sinTheta;
                    H.z = cosTheta;
                	
                    // from tangent-space vector to world-space sample vector
                    vec3 up        = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
                    vec3 tangent   = normalize(cross(up, N));
                    vec3 bitangent = cross(N, tangent);
                	
                    vec3 sampleVec = tangent * H.x + bitangent * H.y + N * H.z;
                    return normalize(sampleVec);
                }
                
                vec2 IntegrateBRDF(float NdotV, float roughness)
                {
                    vec3 V;
                    V.x = sqrt(1.0 - NdotV*NdotV);
                    V.y = 0.0;
                    V.z = NdotV;
                
                    float A = 0.0;
                    float B = 0.0;
                
                    vec3 N = vec3(0.0, 0.0, 1.0);
                
                    const uint SAMPLE_COUNT = 1024u;
                    for(uint i = 0u; i < SAMPLE_COUNT; ++i)
                    {
                        vec2 Xi = Hammersley(i, SAMPLE_COUNT);
                        vec3 H  = ImportanceSampleGGX(Xi, N, roughness);
                        vec3 L  = normalize(2.0 * dot(V, H) * H - V);
                
                        float NdotL = max(L.z, 0.0);
                        float NdotH = max(H.z, 0.0);
                        float VdotH = max(dot(V, H), 0.0);
                
                        if(NdotL > 0.0)
                        {
                            float G = GeometrySmith(N, V, L, roughness);
                            float G_Vis = (G * VdotH) / (NdotH * NdotV);
                            float Fc = pow(1.0 - VdotH, 5.0);
                
                            A += (1.0 - Fc) * G_Vis;
                            B += Fc * G_Vis;
                        }
                    }
                    A /= float(SAMPLE_COUNT);
                    B /= float(SAMPLE_COUNT);
                    return vec2(A, B);
                }
                
                layout (location = 0) out vec4 outputColor;
                
                void main() {
                    vec2 integratedBRDF = IntegrateBRDF(uv.x, uv.y);
                    outputColor = vec4(integratedBRDF.x, integratedBRDF.y, 1.0, 1.0);
                }
                """
        );

        final int fboSize = TEXTURE_SIZE;

        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        int rboColor = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rboColor);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_RGB32F, fboSize, fboSize);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rboColor);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalArgumentException("Fatal framebuffer error, could not render specular ibl brdf, framebuffer is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, fboSize, fboSize);

        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(shader);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glUseProgram(0);

        float[] data = new float[fboSize * fboSize * 4];
        glReadPixels(0, 0, fboSize, fboSize, GL_RGBA, GL_FLOAT, data);
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, Main.WIDTH, Main.HEIGHT);

        glDeleteRenderbuffers(rboColor);
        glDeleteFramebuffers(fbo);
        
        glDeleteProgram(shader);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);

        return data;
    }

    static {
        long here = System.currentTimeMillis();
        float[] data = renderBRDF();
        
        int texture = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        
        glTexImage2D(
                GL_TEXTURE_2D, 0,
                GL_RG16F,
                TEXTURE_SIZE, TEXTURE_SIZE,
                0, 
                GL_RGBA, GL_FLOAT, data
        );
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        SPECULAR_BRDF_TEXTURE = texture;
        System.out.println("(Ignore GL Debug Warning Above)");
        System.out.println("Specular BRDF Lookup Table Generated in "+(System.currentTimeMillis()-here)+"ms!");
    }

}
