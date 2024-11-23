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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class NBoneAnimation {
    
    public static final int POSITION_COMPONENTS = 3;
    public static final int ROTATION_COMPONENTS = 4;
    public static final int SCALING_COMPONENTS = 3;
    
    private static float[] readFloatArray(DataInputStream in) throws IOException {
        int size = in.readInt();
        float[] array = new float[size];
        for (int i = 0; i < size; i++) {
            array[i] = in.readFloat();
        }
        return array;
    }
    
    public static NBoneAnimation read(DataInputStream in) throws IOException {
        String boneName = in.readUTF();
        
        float[] positionTimestamps = readFloatArray(in);
        float[] positions = readFloatArray(in);
        
        float[] rotationTimestamps = readFloatArray(in);
        float[] rotations = readFloatArray(in);
        
        float[] scalingTimestamps = readFloatArray(in);
        float[] scalings = readFloatArray(in);
        
        return new NBoneAnimation(boneName,
                positionTimestamps, positions,
                rotationTimestamps, rotations, 
                scalingTimestamps, scalings);
    }
    
    private final String boneName;
    
    private final float[] positionTimestamps;
    private final float[] positions;
    
    private final float[] rotationTimestamps;
    private final float[] rotations;
    
    private final float[] scalingTimestamps;
    private final float[] scalings;
    
    public NBoneAnimation(
            String boneName,
            float[] positionTimestamps, float[] positions,
            float[] rotationTimestamps, float[] rotations,
            float[] scalingTimestamps, float[] scalings
    ) {
        this.boneName = boneName;
        
        if (positions.length != positionTimestamps.length * POSITION_COMPONENTS) {
            throw new IllegalArgumentException("Invalid amount of positions");
        }
        this.positionTimestamps = positionTimestamps.clone();
        this.positions = positions.clone();
        
        if (rotations.length != rotationTimestamps.length * ROTATION_COMPONENTS) {
            throw new IllegalArgumentException("Invalid amount of rotations");
        }
        this.rotationTimestamps = rotationTimestamps.clone();
        this.rotations = rotations.clone();
        
        if (scalings.length != scalingTimestamps.length * SCALING_COMPONENTS) {
            throw new IllegalArgumentException("Invalid amount of scalings");
        }
        this.scalingTimestamps = scalingTimestamps.clone();
        this.scalings = scalings.clone();
    }
    
    public String getBoneName() {
        return boneName;
    }

    public int getNumberOfPositions() {
        return this.positionTimestamps.length;
    }

    public int getNumberOfRotations() {
        return this.rotationTimestamps.length;
    }

    public int getNumberOfScalings() {
        return this.scalingTimestamps.length;
    }
    
    public void getPosition(int index, Vector3f outPosition) {
        if (index < 0 || (index * 3) > this.positions.length) {
            throw new IndexOutOfBoundsException("Position index "+index+" out of bounds for length "+this.positionTimestamps.length);
        }
        outPosition.set(
                this.positions[(index * POSITION_COMPONENTS) + 0],
                this.positions[(index * POSITION_COMPONENTS) + 1],
                this.positions[(index * POSITION_COMPONENTS) + 2]
        );
    }
    
    public void getRotation(int index, Quaternionf outRotation) {
        if (index < 0 || (index * 4) > this.rotations.length) {
            throw new IndexOutOfBoundsException("Rotation index "+index+" out of bounds for length "+this.rotationTimestamps.length);
        }
        outRotation.set(
                this.rotations[(index * ROTATION_COMPONENTS) + 0],
                this.rotations[(index * ROTATION_COMPONENTS) + 1],
                this.rotations[(index * ROTATION_COMPONENTS) + 2],
                this.rotations[(index * ROTATION_COMPONENTS) + 3]
        );
    }
    
    public void getScaling(int index, Vector3f outScale) {
        if (index < 0 || (index * 3) > this.scalings.length) {
            throw new IndexOutOfBoundsException("Scaling index "+index+" out of bounds for length "+this.scalingTimestamps.length);
        }
        outScale.set(
                this.scalings[(index * SCALING_COMPONENTS) + 0],
                this.scalings[(index * SCALING_COMPONENTS) + 1],
                this.scalings[(index * SCALING_COMPONENTS) + 2]
        );
    }
    
    public float getPositionTime(int index) {
        return this.positionTimestamps[index];
    }
    
    public float getRotationTime(int index) {
        return this.rotationTimestamps[index];
    }
    
    public float getScalingTime(int index) {
        return this.scalingTimestamps[index];
    }

    private void writeFloatArray(DataOutputStream out, float[] array) throws IOException {
        out.writeInt(array.length);
        for (int i = 0; i < array.length; i++) {
            out.writeFloat(array[i]);
        }
    }
    
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(getBoneName());
        
        writeFloatArray(out, this.positionTimestamps);
        writeFloatArray(out, this.positions);
        
        writeFloatArray(out, this.rotationTimestamps);
        writeFloatArray(out, this.rotations);
        
        writeFloatArray(out, this.scalingTimestamps);
        writeFloatArray(out, this.scalings);
    }
    
}
