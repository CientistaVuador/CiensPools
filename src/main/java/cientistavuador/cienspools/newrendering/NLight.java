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

import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public abstract class NLight {
    
    private final String name;
    
    private boolean dynamic = true;
    private String groupName = "";
    
    private float size = 0.05f;
    
    private final Vector3f diffuse = new Vector3f(1f);
    private final Vector3f specular = new Vector3f(1f);
    private final Vector3f ambient = new Vector3f(0.05f);
    
    public NLight(String name) {
        if (name == null) {
            name = "";
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        if (groupName == null) {
            groupName = "";
        }
        this.groupName = groupName;
    }
    
    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = size;
    }
    
    public Vector3f getDiffuse() {
        return diffuse;
    }

    public Vector3f getSpecular() {
        return specular;
    }

    public Vector3f getAmbient() {
        return ambient;
    }
    
    public void setDiffuseSpecularAmbient(float r, float g, float b) {
        this.diffuse.set(r, g, b).mul(5f);
        this.specular.set(r, g, b);
        this.ambient.set(r, g, b).mul(0.05f);
    }
    
    public void setDiffuseSpecularAmbient(float value) {
        setDiffuseSpecularAmbient(value, value, value);
    }
    
    public static class NDirectionalLight extends NLight {
        
        private final Vector3f direction = new Vector3f(0f, -1f, -0.5f).normalize();
        
        public NDirectionalLight(String name) {
            super(name);
        }

        public Vector3f getDirection() {
            return direction;
        }

    }
    
    public static class NPointLight extends NLight {
        
        private final Vector3d position = new Vector3d();
        private float range = 10f;
        
        public NPointLight(String name) {
            super(name);
        }

        public Vector3d getPosition() {
            return position;
        }
        
        public float getRange() {
            return range;
        }

        public void setRange(float range) {
            this.range = range;
        }
        
    }
    
    public static class NSpotLight extends NLight {
        private final Vector3d position = new Vector3d();
        private final Vector3f direction = new Vector3f();
        private float range = 10f;
        
        private float innerCone = (float) Math.cos(Math.toRadians(25f));
        private float outerCone = (float) Math.cos(Math.toRadians(65f));
        
        public NSpotLight(String name) {
            super(name);
        }

        public Vector3d getPosition() {
            return position;
        }

        public Vector3f getDirection() {
            return direction;
        }
        
        public float getRange() {
            return range;
        }

        public void setRange(float range) {
            this.range = range;
        }
        
        public float getInnerCone() {
            return innerCone;
        }

        public void setInnerCone(float innerCone) {
            this.innerCone = innerCone;
        }
        
        public void setInnerConeAngle(float innerConeAngle) {
            this.innerCone = (float) Math.cos(Math.toRadians(innerConeAngle));
        }

        public float getOuterCone() {
            return outerCone;
        }

        public void setOuterCone(float outerCone) {
            this.outerCone = outerCone;
        }
        
        public void setOuterConeAngle(float outerConeAngle) {
            this.outerCone = (float) Math.cos(Math.toRadians(outerConeAngle));
        }
        
    }
    
}
