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
package cientistavuador.cienspools.util.bakedlighting;

import cientistavuador.cienspools.util.ColorUtils;
import cientistavuador.cienspools.util.MeshUtils;
import cientistavuador.cienspools.util.PixelUtils;
import cientistavuador.cienspools.util.RasterUtils;
import cientistavuador.cienspools.util.postprocess.GaussianBlur;
import cientistavuador.cienspools.util.postprocess.MarginAutomata;
import cientistavuador.cienspools.util.raycast.BVH;
import cientistavuador.cienspools.util.raycast.LocalRayResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.joml.primitives.Rectanglei;

/**
 *
 * @author Cien
 */
public class Lightmapper {

    public static long approximatedMemoryUsage(int resolution, int samples, int groups) {
        long memory = 0;

        memory += 1L * Float.BYTES * 3 * resolution * resolution * groups;
        memory += 1L * Float.BYTES * 3 * resolution * resolution * groups;

        memory += 1L * Float.BYTES * 3 * resolution * resolution * samples;
        memory += 1L * Integer.BYTES * 1 * resolution * resolution * samples;
        memory += 1L * Integer.BYTES * 1 * resolution * resolution * samples;

        memory += 1L * Float.BYTES * 4 * resolution * resolution;
        memory += 1L * Float.BYTES * 3 * resolution * resolution;

        memory += 1L * Float.BYTES * 3 * resolution * resolution;
        memory += 1L * Float.BYTES * 3 * resolution * resolution;
        memory += 1L * Float.BYTES * 3 * resolution * resolution;

        memory += 1L * Float.BYTES * 3 * resolution * resolution;
        memory += 1L * Float.BYTES * 3 * resolution * resolution;

        return memory;
    }

    public static interface TextureInput {

        public void textureColor(float[] mesh, float u, float v, int triangle, boolean emissive, Vector4f outputColor);
    }

    public static final float EPSILON = 0.0001f;
    public static final int DEFAULT_GAUSSIAN_BLUR_KERNEL_SIZE = 51;
    public static final int DEFAULT_MARGIN_ITERATIONS = 4;

    public static final int OFFSET_POSITION_XYZ = 0;
    public static final int OFFSET_LIGHTMAP_XY = OFFSET_POSITION_XYZ + 3;
    public static final int OFFSET_TEXTURE_XY = OFFSET_LIGHTMAP_XY + 2;
    public static final int OFFSET_TRIANGLE_NORMAL_XYZ = OFFSET_TEXTURE_XY + 2;
    public static final int OFFSET_NORMAL_XYZ = OFFSET_TRIANGLE_NORMAL_XYZ + 3;
    public static final int OFFSET_USER_XY = OFFSET_NORMAL_XYZ + 3;

    public static final int VERTEX_SIZE = OFFSET_USER_XY + 2;
    
    public static final float AMBIENT_CUBE_INITIAL_RADIUS = 0.5f;
    public static final int MAX_NUMBER_OF_AMBIENT_CUBES = 1_000_000;
    public static final int MAX_NUMBER_OF_AMBIENT_CUBES_FAST_MODE = 10_000;
    public static final float AMBIENT_CUBE_DISTANCE_FROM_WALLS = 0.05f;
    public static final int NUMBER_OF_AMBIENT_CUBE_OCCLUSION_RAYS_PER_SIDE = 24;
    public static final int NUMBER_OF_AMBIENT_CUBE_RAYS_PER_SIDE = 1024;

    private static float[] validate(float[] mesh) {
        if (mesh == null) {
            return new float[0];
        }

        if (mesh.length % VERTEX_SIZE != 0) {
            throw new IllegalArgumentException("Invalid vertex size!");
        }
        if ((mesh.length / VERTEX_SIZE) % 3 != 0) {
            throw new IllegalArgumentException("The mesh does not contains triangles");
        }

        float[] copy = mesh.clone();

        Vector3f normal = new Vector3f();

        for (int i = 0; i < mesh.length; i += VERTEX_SIZE * 3) {
            float v0x = copy[i + (VERTEX_SIZE * 0) + OFFSET_POSITION_XYZ + 0];
            float v0y = copy[i + (VERTEX_SIZE * 0) + OFFSET_POSITION_XYZ + 1];
            float v0z = copy[i + (VERTEX_SIZE * 0) + OFFSET_POSITION_XYZ + 2];

            float v1x = copy[i + (VERTEX_SIZE * 1) + OFFSET_POSITION_XYZ + 0];
            float v1y = copy[i + (VERTEX_SIZE * 1) + OFFSET_POSITION_XYZ + 1];
            float v1z = copy[i + (VERTEX_SIZE * 1) + OFFSET_POSITION_XYZ + 2];

            float v2x = copy[i + (VERTEX_SIZE * 2) + OFFSET_POSITION_XYZ + 0];
            float v2y = copy[i + (VERTEX_SIZE * 2) + OFFSET_POSITION_XYZ + 1];
            float v2z = copy[i + (VERTEX_SIZE * 2) + OFFSET_POSITION_XYZ + 2];

            MeshUtils.calculateTriangleNormal(v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, normal);

            for (int j = 0; j < 3; j++) {
                copy[i + (VERTEX_SIZE * j) + OFFSET_TRIANGLE_NORMAL_XYZ + 0] = normal.x();
                copy[i + (VERTEX_SIZE * j) + OFFSET_TRIANGLE_NORMAL_XYZ + 1] = normal.y();
                copy[i + (VERTEX_SIZE * j) + OFFSET_TRIANGLE_NORMAL_XYZ + 2] = normal.z();
            }
        }

        for (int i = 0; i < mesh.length; i += VERTEX_SIZE) {
            float nx = copy[i + OFFSET_NORMAL_XYZ + 0];
            float ny = copy[i + OFFSET_NORMAL_XYZ + 1];
            float nz = copy[i + OFFSET_NORMAL_XYZ + 2];

            normal.set(nx, ny, nz).normalize();

            if (!normal.isFinite()) {
                normal.set(
                        copy[i + OFFSET_TRIANGLE_NORMAL_XYZ + 0],
                        copy[i + OFFSET_TRIANGLE_NORMAL_XYZ + 1],
                        copy[i + OFFSET_TRIANGLE_NORMAL_XYZ + 2]
                );
            }

            copy[i + OFFSET_NORMAL_XYZ + 0] = normal.x();
            copy[i + OFFSET_NORMAL_XYZ + 1] = normal.y();
            copy[i + OFFSET_NORMAL_XYZ + 2] = normal.z();
        }

        return copy;
    }

    public static class LightmapperOutput {

        private final int size;
        private final String[] names;
        private final float[][] lightmaps;
        private final float[][] lightmapsEmissive;
        private final float[] color;
        private final LightmapAmbientCubeBVH ambientCubes;

        public LightmapperOutput(
                int size,
                String[] names,
                float[][] lightmaps,
                float[][] lightmapsEmissive,
                float[] color,
                LightmapAmbientCubeBVH ambientCubes
        ) {
            this.size = size;
            this.names = names;
            this.lightmaps = lightmaps;
            this.lightmapsEmissive = lightmapsEmissive;
            this.color = color;
            this.ambientCubes = ambientCubes;
        }

        public int getSize() {
            return size;
        }

        public String[] getNames() {
            return names;
        }

        public float[][] getLightmaps() {
            return lightmaps;
        }

        public float[][] getLightmapsEmissive() {
            return lightmapsEmissive;
        }

        public float[] getColor() {
            return color;
        }

        public LightmapAmbientCubeBVH getAmbientCubes() {
            return ambientCubes;
        }

    }

    private static class LightGroup {

        public String groupName = "";
        public final List<Scene.Light> lights = new ArrayList<>();
    }

    private static class Float3Buffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public Float3Buffer(int width, int height, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = width * this.vectorSize;
            this.data = new float[height * this.lineSize];
        }

        public Float3Buffer(int size, int samples) {
            this(size, size, samples);
        }

        public void write(Vector3f vec, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.x();
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.y();
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.z();
        }

        public void read(Vector3f vec, int x, int y, int sample) {
            vec.set(
                    this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)]
            );
        }

        public float[] getData() {
            return data;
        }

    }

    private static class Float4Buffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public Float4Buffer(int size, int samples) {
            this.sampleSize = 4;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public void write(Vector4f vec, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.x();
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.y();
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.z();
            this.data[3 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.w();
        }

        public void read(Vector4f vec, int x, int y, int sample) {
            vec.set(
                    this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[3 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)]
            );
        }

        public float[] getData() {
            return data;
        }

    }

    private static class Float3ImageBuffer extends Float3Buffer {

        public Float3ImageBuffer(int width, int height) {
            super(width, height, 1);
        }

        public Float3ImageBuffer(int size) {
            super(size, 1);
        }

        public void write(Vector3f vec, int x, int y) {
            this.write(vec, x, y, 0);
        }

        public void read(Vector3f vec, int x, int y) {
            this.read(vec, x, y, 0);
        }

    }

    private static class Float4ImageBuffer extends Float4Buffer {

        public Float4ImageBuffer(int size) {
            super(size, 1);
        }

        public void write(Vector4f vec, int x, int y) {
            this.write(vec, x, y, 0);
        }

        public void read(Vector4f vec, int x, int y) {
            this.read(vec, x, y, 0);
        }

    }

    private static class IntegerBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final int[] data;

        public IntegerBuffer(int size, int samples) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new int[size * this.lineSize];
        }

        public void write(int data, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = data;
        }

        public int read(int x, int y, int sample) {
            return this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
        }
    }

    public static volatile int NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors();
    public static final int IGNORE_TRIGGER_SIZE = 32;

    private static final int EMPTY = 0;
    private static final int FILLED = 0b00000001;
    private static final int IGNORE_SHADOW = 0b00000010;
    private static final int IGNORE_AMBIENT = 0b00000100;

    //lightmapper status
    private String status = "Idle";
    private long progressCount = 0;
    private long progressMax = 0;
    private long raysCount = 0;
    private long raysTime = System.currentTimeMillis();

    //lightmapper geometry/scene state
    private final TextureInput textureInput;
    private final Scene scene;
    private final int lightmapMargin;
    private final int lightmapSize;
    private final Rectanglei[] lightmapRectangles;
    private final float[] opaqueMesh;
    private final float[] alphaMesh;
    private final BVH opaqueBVH;
    private final BVH alphaBVH;
    private final float[] mesh;
    private final LightGroup[] lightGroups;

    //total lightmaps
    private final String[] lightmapsNames;
    private final float[][] totalLightmaps;
    private final float[][] totalLightmapsEmissive;

    //barycentric buffers
    private final Float3Buffer weights;
    private final IntegerBuffer triangles;
    private final IntegerBuffer sampleStates;

    //texture buffers
    private final Float4ImageBuffer textureColors;
    private final Float3ImageBuffer textureEmissiveColors;

    //ambient cubes
    private final List<LightmapAmbientCube> ambientCubes = new ArrayList<>();

    //threads
    private int numberOfThreads;
    private ExecutorService service;

    //light group
    private LightGroup group;
    private int groupIndex;

    //lightmap
    private Float3ImageBuffer lightmap;
    private Float3ImageBuffer lightmapIndirect;
    private Float3ImageBuffer lightmapEmissive;

    //light buffers
    private Scene.Light light;
    private int lightIndex;
    private Float3ImageBuffer direct;
    private Float3ImageBuffer shadow;

    public Lightmapper(
            TextureInput textureInput,
            Scene scene,
            int lightmapMargin,
            int lightmapSize,
            Rectanglei[] lightmapRectangles,
            float[] opaqueMesh,
            float[] alphaMesh
    ) {
        this.textureInput = textureInput;
        this.scene = scene;
        this.lightmapMargin = lightmapMargin;
        this.lightmapSize = lightmapSize;
        this.lightmapRectangles = lightmapRectangles;
        this.opaqueMesh = validate(opaqueMesh);
        this.alphaMesh = validate(alphaMesh);

        int[] opaqueIndices = new int[this.opaqueMesh.length / VERTEX_SIZE];
        for (int i = 0; i < opaqueIndices.length; i++) {
            opaqueIndices[i] = i;
        }

        int[] alphaIndices = new int[this.alphaMesh.length / VERTEX_SIZE];
        for (int i = 0; i < alphaIndices.length; i++) {
            alphaIndices[i] = i;
        }

        this.opaqueBVH = BVH.create(this.opaqueMesh, opaqueIndices, VERTEX_SIZE, OFFSET_POSITION_XYZ);
        this.alphaBVH = BVH.create(this.alphaMesh, alphaIndices, VERTEX_SIZE, OFFSET_POSITION_XYZ);

        this.mesh = new float[this.opaqueMesh.length + this.alphaMesh.length];
        System.arraycopy(this.opaqueMesh, 0, this.mesh, 0, this.opaqueMesh.length);
        System.arraycopy(this.alphaMesh, 0, this.mesh, this.opaqueMesh.length, this.alphaMesh.length);

        List<LightGroup> groups = new ArrayList<>();
        for (Scene.Light light : scene.getLights()) {
            LightGroup group = null;
            for (LightGroup g : groups) {
                if (g.groupName.equals(light.getGroupName())) {
                    group = g;
                    break;
                }
            }
            if (group == null) {
                group = new LightGroup();
                group.groupName = light.getGroupName();
                groups.add(group);
            }
            group.lights.add(light);
        }
        this.lightGroups = groups.toArray(LightGroup[]::new);

        this.lightmapsNames = new String[this.lightGroups.length];
        for (int i = 0; i < this.lightGroups.length; i++) {
            this.lightmapsNames[i] = this.lightGroups[i].groupName;
        }
        this.totalLightmaps = new float[this.lightmapsNames.length][];
        this.totalLightmapsEmissive = new float[this.lightmapsNames.length][];

        this.weights = new Float3Buffer(lightmapSize, this.scene.getSamplingMode().numSamples());
        this.triangles = new IntegerBuffer(lightmapSize, this.scene.getSamplingMode().numSamples());
        this.sampleStates = new IntegerBuffer(lightmapSize, this.scene.getSamplingMode().numSamples());

        this.textureColors = new Float4ImageBuffer(lightmapSize);
        this.textureEmissiveColors = new Float3ImageBuffer(lightmapSize);
    }

    private void setStatus(String status, long progressMax) {
        this.status = status;
        this.progressCount = 0;
        this.progressMax = progressMax;
        this.raysCount = 0;
        this.raysTime = System.currentTimeMillis();
    }

    private void addProgress(long progress) {
        this.progressCount += progress;
    }

    private void addRay() {
        this.raysCount++;
    }

    private int clamp(int v, int min, int max) {
        if (v > max) {
            return max;
        }
        if (v < min) {
            return min;
        }
        return v;
    }

    private float lerp(Vector3fc weights, int triangle, int offset) {
        float va  = this.mesh[triangle + (VERTEX_SIZE * 0) + offset];
        float vb = this.mesh[triangle + (VERTEX_SIZE * 1) + offset];
        float vc = this.mesh[triangle + (VERTEX_SIZE * 2) + offset];
        return (va  * weights.x()) + (vb * weights.y()) + (vc * weights.z());
    }

    private void rasterizeBarycentricBuffers() {
        Vector3f samplePosition = new Vector3f();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        Vector3f sampleWeights = new Vector3f();

        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f otherNormal = new Vector3f();

        SamplingMode mode = this.scene.getSamplingMode();

        setStatus("Rasterizing Barycentric Buffers", this.mesh.length);
        for (int triangle = 0; triangle < this.mesh.length; triangle += VERTEX_SIZE * 3) {
            normal.set(
                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 0],
                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 1],
                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 2]
            );

            float v0x = this.mesh[triangle + (VERTEX_SIZE * 0) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v0y = this.mesh[triangle + (VERTEX_SIZE * 0) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

            float v1x = this.mesh[triangle + (VERTEX_SIZE * 1) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v1y = this.mesh[triangle + (VERTEX_SIZE * 1) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

            float v2x = this.mesh[triangle + (VERTEX_SIZE * 2) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v2y = this.mesh[triangle + (VERTEX_SIZE * 2) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

            a.set(v0x, v0y, 0f);
            b.set(v1x, v1y, 0f);
            c.set(v2x, v2y, 0f);

            int minX = (int) Math.floor(Math.min(v0x, Math.min(v1x, v2x))) - 1;
            int minY = (int) Math.floor(Math.min(v0y, Math.min(v1y, v2y))) - 1;
            int maxX = (int) Math.ceil(Math.max(v0x, Math.max(v1x, v2x))) + 1;
            int maxY = (int) Math.ceil(Math.max(v0y, Math.max(v1y, v2y))) + 1;

            minX = clamp(minX, 0, this.lightmapSize - 1);
            minY = clamp(minY, 0, this.lightmapSize - 1);
            maxX = clamp(maxX, 0, this.lightmapSize - 1);
            maxY = clamp(maxY, 0, this.lightmapSize - 1);

            boolean ignoreEnabled = true;

            Rectanglei rect = new Rectanglei(minX, minY, maxX, maxY);
            for (Rectanglei other : this.lightmapRectangles) {
                if (other.intersectsRectangle(rect)) {
                    int minSize = Math.min(other.lengthX(), other.lengthY());
                    if (minSize < IGNORE_TRIGGER_SIZE) {
                        ignoreEnabled = false;
                        break;
                    }
                }
            }

            raster:
            for (int y = minY; y < maxY; y++) {
                for (int x = minX; x < maxX; x++) {
                    int sampleState = FILLED;

                    if (ignoreEnabled) {
                        if (!PixelUtils.dither50(x, y)) {
                            sampleState |= IGNORE_SHADOW;
                        }
                        if (!PixelUtils.dither25(x, y)) {
                            sampleState |= IGNORE_AMBIENT;
                        }
                    }

                    sampleLoop:
                    for (int s = 0; s < mode.numSamples(); s++) {
                        float sampleX = mode.sampleX(s);
                        float sampleY = mode.sampleY(s);

                        samplePosition.set(x + sampleX, y + sampleY, 0f);

                        RasterUtils.barycentricWeights(samplePosition, a, b, c, sampleWeights);

                        float wx = sampleWeights.x();
                        float wy = sampleWeights.y();
                        float wz = sampleWeights.z();

                        if (!Float.isFinite(wx) || !Float.isFinite(wy) || !Float.isFinite(wz)) {
                            break raster;
                        }

                        if (wx < 0f || wy < 0f || wz < 0f) {
                            continue;
                        }

                        position.set(
                                lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 0),
                                lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 1),
                                lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 2)
                        ).add(
                                normal.x() * this.scene.getRayOffset(),
                                normal.y() * this.scene.getRayOffset(),
                                normal.z() * this.scene.getRayOffset()
                        );

                        Set<Integer> tri = this.opaqueBVH.testSphere(
                                position.x(), position.y(), position.z(),
                                this.scene.getRayOffset()
                        );

                        for (Integer i : tri) {
                            otherNormal.set(
                                    this.mesh[(i * VERTEX_SIZE * 3) + OFFSET_TRIANGLE_NORMAL_XYZ + 0],
                                    this.mesh[(i * VERTEX_SIZE * 3) + OFFSET_TRIANGLE_NORMAL_XYZ + 1],
                                    this.mesh[(i * VERTEX_SIZE * 3) + OFFSET_TRIANGLE_NORMAL_XYZ + 2]
                            );

                            if (normal.dot(otherNormal) < 0.5f) {
                                continue sampleLoop;
                            }
                        }

                        this.weights.write(sampleWeights, x, y, s);
                        this.triangles.write(triangle, x, y, s);
                        this.sampleStates.write(sampleState, x, y, s);
                    }
                }
            }

            addProgress(VERTEX_SIZE * 3);
        }
    }

    private void readTextureColors() {
        Vector4f textureColor = new Vector4f();
        Vector4f totalColor = new Vector4f();

        Vector3f barycentricWeights = new Vector3f();

        setStatus("Reading Texture Colors", this.lightmapSize);
        for (int y = 0; y < this.lightmapSize; y++) {
            for (int x = 0; x < this.lightmapSize; x++) {
                int numSamples = this.scene.getSamplingMode().numSamples();
                totalColor.zero();
                int samplesPassed = 0;
                for (int s = 0; s < numSamples; s++) {
                    if ((this.sampleStates.read(x, y, s) & FILLED) == 0) {
                        continue;
                    }

                    this.weights.read(barycentricWeights, x, y, s);
                    int triangle = this.triangles.read(x, y, s);

                    float u = lerp(barycentricWeights, triangle, OFFSET_TEXTURE_XY + 0);
                    float v = lerp(barycentricWeights, triangle, OFFSET_TEXTURE_XY + 1);

                    this.textureInput.textureColor(this.mesh, u, v, triangle, false, textureColor);

                    totalColor.add(textureColor);
                    samplesPassed++;
                }
                if (samplesPassed != 0) {
                    totalColor.div(samplesPassed);
                }
                this.textureColors.write(totalColor, x, y);
            }
            addProgress(1);
        }
    }

    private void readTextureEmissiveColors() {
        Vector4f textureColor = new Vector4f();
        Vector3f totalColor = new Vector3f();

        Vector3f barycentricWeights = new Vector3f();

        setStatus("Reading Texture Emissive Colors", this.lightmapSize);
        for (int y = 0; y < this.lightmapSize; y++) {
            for (int x = 0; x < this.lightmapSize; x++) {
                int numSamples = this.scene.getSamplingMode().numSamples();
                totalColor.zero();
                int samplesPassed = 0;
                for (int s = 0; s < numSamples; s++) {
                    if ((this.sampleStates.read(x, y, s) & FILLED) == 0) {
                        continue;
                    }

                    this.weights.read(barycentricWeights, x, y, s);
                    int triangle = this.triangles.read(x, y, s);

                    float u = lerp(barycentricWeights, triangle, OFFSET_TEXTURE_XY + 0);
                    float v = lerp(barycentricWeights, triangle, OFFSET_TEXTURE_XY + 1);

                    this.textureInput.textureColor(this.mesh, u, v, triangle, true, textureColor);

                    totalColor.add(textureColor.x(), textureColor.y(), textureColor.z());
                    samplesPassed++;
                }
                if (samplesPassed != 0) {
                    totalColor.div(samplesPassed);
                }
                this.textureEmissiveColors.write(totalColor, x, y);
            }
            addProgress(1);
        }
    }

    private MarginAutomata.MarginAutomataIO createAutomataIO(
            final Rectanglei rectangle,
            final Float4ImageBuffer buffer,
            final int ignoreFlag
    ) {
        MarginAutomata.MarginAutomataIO marginIO = new MarginAutomata.MarginAutomataIO() {
            @Override
            public int width() {
                return rectangle.lengthX();
            }

            @Override
            public int height() {
                return rectangle.lengthY();
            }

            @Override
            public boolean empty(int x, int y) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                boolean empty = true;
                int numSamples = Lightmapper.this.scene.getSamplingMode().numSamples();
                for (int s = 0; s < numSamples; s++) {
                    int state = Lightmapper.this.sampleStates.read(absX, absY, s);
                    if ((state & Lightmapper.FILLED) != 0 && (state & ignoreFlag) == 0) {
                        empty = false;
                        break;
                    }
                }

                return empty;
            }

            final Vector4f colorVector = new Vector4f();

            @Override
            public void read(int x, int y, MarginAutomata.MarginAutomataColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                buffer.read(this.colorVector, absX, absY);
                color.r = this.colorVector.x();
                color.g = this.colorVector.y();
                color.b = this.colorVector.z();
                color.a = this.colorVector.w();
            }

            @Override
            public void write(int x, int y, MarginAutomata.MarginAutomataColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                this.colorVector.set(color.r, color.g, color.b, color.a);
                buffer.write(this.colorVector, absX, absY);
            }
        };

        return marginIO;
    }

    private void generateTextureColorsMargins() {
        setStatus("Generating Texture Colors Margins", this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.textureColors, Lightmapper.EMPTY
            );
            MarginAutomata.generateMargin(io, this.lightmapMargin * 2);
            addProgress(1);
        }
    }

    private MarginAutomata.MarginAutomataIO createAutomataIO(
            final Rectanglei rectangle,
            final Float3ImageBuffer buffer,
            final int ignoreFlag
    ) {
        MarginAutomata.MarginAutomataIO marginIO = new MarginAutomata.MarginAutomataIO() {
            @Override
            public int width() {
                return rectangle.lengthX();
            }

            @Override
            public int height() {
                return rectangle.lengthY();
            }

            @Override
            public boolean empty(int x, int y) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                boolean empty = true;
                int numSamples = Lightmapper.this.scene.getSamplingMode().numSamples();
                for (int s = 0; s < numSamples; s++) {
                    int state = Lightmapper.this.sampleStates.read(absX, absY, s);
                    if ((state & Lightmapper.FILLED) != 0 && (state & ignoreFlag) == 0) {
                        empty = false;
                        break;
                    }
                }

                return empty;
            }

            final Vector3f colorVector = new Vector3f();

            @Override
            public void read(int x, int y, MarginAutomata.MarginAutomataColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                buffer.read(this.colorVector, absX, absY);
                color.r = this.colorVector.x();
                color.g = this.colorVector.y();
                color.b = this.colorVector.z();
            }

            @Override
            public void write(int x, int y, MarginAutomata.MarginAutomataColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                this.colorVector.set(color.r, color.g, color.b);
                buffer.write(this.colorVector, absX, absY);
            }
        };

        return marginIO;
    }

    private void generateTextureEmissiveColorsMargins() {
        setStatus("Generating Texture Emissive Colors Margins", this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.textureEmissiveColors, Lightmapper.EMPTY
            );
            MarginAutomata.generateMargin(io, this.lightmapMargin * 2);
            addProgress(1);
        }
    }

    private void placeAmbientCubes() {
        Vector3f worldMin = new Vector3f(this.alphaBVH.getMin())
                .min(this.opaqueBVH.getMin())
                .sub(
                        AMBIENT_CUBE_DISTANCE_FROM_WALLS * 2f,
                        AMBIENT_CUBE_DISTANCE_FROM_WALLS * 2f,
                        AMBIENT_CUBE_DISTANCE_FROM_WALLS * 2f
                );
        Vector3f worldMax = new Vector3f(this.alphaBVH.getMax())
                .max(this.opaqueBVH.getMax())
                .add(
                        AMBIENT_CUBE_DISTANCE_FROM_WALLS * 2f,
                        AMBIENT_CUBE_DISTANCE_FROM_WALLS * 2f,
                        AMBIENT_CUBE_DISTANCE_FROM_WALLS * 2f
                );

        float width = worldMax.x() - worldMin.x();
        float height = worldMax.y() - worldMin.y();
        float depth = worldMax.z() - worldMin.z();
        
        int maxNumber = MAX_NUMBER_OF_AMBIENT_CUBES;
        if (this.scene.isFastModeEnabled()) {
            maxNumber = MAX_NUMBER_OF_AMBIENT_CUBES_FAST_MODE;
        }
        
        float radius = 0f;
        int numberOfAmbientCubes = 0;
        do {
            radius += AMBIENT_CUBE_INITIAL_RADIUS;
            numberOfAmbientCubes = (int) (Math.ceil(width / radius) * Math.ceil(height / radius) * Math.ceil(depth / radius));
        } while (numberOfAmbientCubes > maxNumber);
        
        Vector3f rayDirection = new Vector3f();
        Vector3f rayPosition = new Vector3f();
        
        setStatus("Placing Ambient Cubes (" + numberOfAmbientCubes + ", "+radius+")", numberOfAmbientCubes);
        for (float z = worldMin.z(); z < worldMax.z(); z += radius) {
            for (float y = worldMin.y(); y < worldMax.y(); y += radius) {
                loop:
                for (float x = worldMin.x(); x < worldMax.x(); x += radius) {
                    addProgress(1);
                    
                    if (this.alphaBVH.fastTestSphere(x, y, z, AMBIENT_CUBE_DISTANCE_FROM_WALLS)
                            || this.opaqueBVH.fastTestSphere(x, y, z, AMBIENT_CUBE_DISTANCE_FROM_WALLS)) {
                        continue;
                    }

                    rayPosition.set(x, y, z);
                    
                    for (int side = 0; side < AmbientCube.SIDES; side++) {
                        for (int j = 0; j < NUMBER_OF_AMBIENT_CUBE_OCCLUSION_RAYS_PER_SIDE; j++) {
                            AmbientCube.randomSideDirection90(side, rayDirection);

                            List<LocalRayResult> rays = this.opaqueBVH.testRay(
                                    rayPosition,
                                    rayDirection
                            );
                            rays.addAll(this.alphaBVH.testRay(rayPosition, rayDirection));

                            rays.sort((o1, o2) -> Float.compare(o1.getLocalDistance(), o2.getLocalDistance()));

                            if (!rays.isEmpty() && !rays.get(0).frontFace()) {
                                continue loop;
                            }
                        }
                    }

                    this.ambientCubes.add(new LightmapAmbientCube(
                            x, y, z,
                            radius,
                            this.lightGroups.length
                    ));
                }
            }
        }
    }

    private String getGroupName() {
        if (this.group.groupName.isEmpty()) {
            return "(Unnamed)";
        }
        return this.group.groupName;
    }

    private void prepareLightmap(int index) {
        this.group = this.lightGroups[index];
        this.groupIndex = index;

        setStatus("Preparing Lightmap " + getGroupName(), 1);
        this.lightmap = new Float3ImageBuffer(this.lightmapSize);
        this.lightmapIndirect = new Float3ImageBuffer(this.lightmapSize);
        this.lightmapEmissive = new Float3ImageBuffer(this.lightmapSize);
        addProgress(1);
    }

    private void prepareLight(int index) {
        this.light = this.group.lights.get(index);
        this.lightIndex = index;

        setStatus(getGroupName() + " - Preparing Light - " + this.lightIndex + ", " + this.light.getClass().getSimpleName(), 1);
        this.direct = new Float3ImageBuffer(this.lightmapSize);
        this.shadow = new Float3ImageBuffer(this.lightmapSize);
        addProgress(1);
    }

    private void bakeDirect() {
        setStatus(getGroupName() + " - Baking Direct - " + this.lightIndex + ", " + this.light.getClass().getSimpleName(), this.lightmapSize);
        for (int i = 0; i < this.lightmapSize; i += this.numberOfThreads) {
            List<Future<?>> tasks = new ArrayList<>();

            for (int j = 0; j < this.numberOfThreads; j++) {
                final int y = i + j;
                if (y >= this.lightmapSize) {
                    break;
                }
                tasks.add(this.service.submit(() -> {
                    Vector3f totalColor = new Vector3f();

                    Vector3f sampleWeights = new Vector3f();
                    Vector3f position = new Vector3f();
                    Vector3f normal = new Vector3f();

                    Vector3f outLightDirection = new Vector3f();
                    Vector3f outLightDirectColor = new Vector3f();

                    Vector3f emissiveColor = new Vector3f();

                    int numSamples = this.scene.getSamplingMode().numSamples();
                    for (int x = 0; x < this.lightmapSize; x++) {
                        totalColor.zero();
                        int samplesPassed = 0;
                        for (int s = 0; s < numSamples; s++) {
                            int sampleState = this.sampleStates.read(x, y, s);
                            if ((sampleState & FILLED) == 0) {
                                continue;
                            }

                            this.weights.read(sampleWeights, x, y, s);
                            int triangle = this.triangles.read(x, y, s);

                            position.set(
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 0),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 1),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 2)
                            );
                            normal.set(
                                    lerp(sampleWeights, triangle, OFFSET_NORMAL_XYZ + 0),
                                    lerp(sampleWeights, triangle, OFFSET_NORMAL_XYZ + 1),
                                    lerp(sampleWeights, triangle, OFFSET_NORMAL_XYZ + 2)
                            ).normalize();

                            this.light.calculateDirect(
                                    position, normal,
                                    outLightDirection, outLightDirectColor,
                                    this.scene.getDirectLightingAttenuation()
                            );

                            if (this.light instanceof Scene.EmissiveLight) {
                                this.textureEmissiveColors.read(emissiveColor, x, y);
                                outLightDirectColor.mul(emissiveColor);
                            }

                            totalColor.add(outLightDirectColor);
                            samplesPassed++;
                        }
                        if (samplesPassed != 0) {
                            totalColor.div(samplesPassed);
                        }
                        this.direct.write(totalColor, x, y);
                    }
                }));
            }

            for (Future<?> t : tasks) {
                try {
                    t.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }

            addProgress(tasks.size());
            tasks.clear();
        }
    }

    private void generateDirectMargins() {
        setStatus(getGroupName() + " - Generating Direct Margins - " + this.lightIndex + ", " + this.light.getClass().getSimpleName(), this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.direct, Lightmapper.EMPTY
            );
            MarginAutomata.generateMargin(io, DEFAULT_MARGIN_ITERATIONS);
            addProgress(1);
        }
    }

    private void randomDirection(
            Vector3fc normal,
            Vector3f outDirection
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        float x;
        float y;
        float z;
        float dist;

        do {
            x = (random.nextFloat() * 2f) - 1f;
            y = (random.nextFloat() * 2f) - 1f;
            z = random.nextFloat();
            dist = (x * x) + (y * y) + (z * z);
        } while (dist > 1f);

        outDirection.set(
                x,
                y,
                z
        )
                .normalize();

        Vector3f worldUp = new Vector3f(0f, 1f, 0f);

        if (Math.abs(normal.dot(worldUp)) >= (1f - EPSILON)) {
            worldUp.set(1f, 0f, 0f);
        }

        Vector3f tangent = new Vector3f();
        Vector3f bitangent = new Vector3f();

        normal.cross(worldUp, tangent).normalize();
        normal.cross(tangent, bitangent).normalize();

        new Matrix3f(tangent, bitangent, normal).transform(outDirection);
    }

    private Vector3f shadowBlend(Vector3fc position, Vector3fc direction, float length) {
        List<LocalRayResult> alphaResults = this.alphaBVH.testRaySorted(position, direction, true);
        addRay();

        if (alphaResults.isEmpty()) {
            return null;
        }

        Vector3f rayWeights = new Vector3f();

        List<Vector4fc> colors = new ArrayList<>();
        for (LocalRayResult ray : alphaResults) {
            if (Float.isFinite(length) && ray.getLocalDistance() > length) {
                break;
            }

            ray.weights(rayWeights);

            float lu = ray.lerp(rayWeights, OFFSET_LIGHTMAP_XY + 0);
            float lv = ray.lerp(rayWeights, OFFSET_LIGHTMAP_XY + 1);

            int tx = Math.min(Math.max((int) (lu * this.lightmapSize), 0), this.lightmapSize - 1);
            int ty = Math.min(Math.max((int) (lv * this.lightmapSize), 0), this.lightmapSize - 1);

            Vector4f textureColor = new Vector4f();
            this.textureColors.read(textureColor, tx, ty);
            colors.add(textureColor);
        }

        if (colors.isEmpty()) {
            return null;
        }

        Vector4f dest = new Vector4f();
        ColorUtils.blend(colors, dest);

        Vector3f outShadow = new Vector3f(dest.x(), dest.y(), dest.z())
                .mul(dest.w())
                .add(1f - dest.w(), 1f - dest.w(), 1f - dest.w())
                .mul(1f - dest.w());

        return outShadow;
    }

    private void bakeShadow() {
        float lightSize = this.light.getLightSize();
        if (this.scene.isFastModeEnabled()) {
            this.light.setLightSize(0f);
        }

        setStatus(getGroupName() + " - Baking Shadow - " + this.lightIndex + ", " + this.light.getClass().getSimpleName(), this.lightmapSize);
        for (int i = 0; i < this.lightmapSize; i += this.numberOfThreads) {
            List<Future<?>> tasks = new ArrayList<>();

            for (int j = 0; j < this.numberOfThreads; j++) {
                final int y = i + j;
                if (y >= this.lightmapSize) {
                    break;
                }
                tasks.add(this.service.submit(() -> {
                    Vector3f totalShadow = new Vector3f();

                    Vector3f sampleWeights = new Vector3f();
                    Vector3f position = new Vector3f();
                    Vector3f normal = new Vector3f();

                    Vector3f outLightDirection = new Vector3f();

                    Vector3f rayWeights = new Vector3f();

                    Vector3f emissiveColor = new Vector3f();

                    int numSamples = this.scene.getSamplingMode().numSamples();
                    for (int x = 0; x < this.lightmapSize; x++) {
                        totalShadow.zero();
                        int samplesPassed = 0;
                        for (int s = 0; s < numSamples; s++) {
                            int sampleState = this.sampleStates.read(x, y, s);
                            if ((sampleState & FILLED) == 0 || (sampleState & IGNORE_SHADOW) != 0) {
                                continue;
                            }

                            this.weights.read(sampleWeights, x, y, s);
                            int triangle = this.triangles.read(x, y, s);

                            position.set(
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 0),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 1),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 2)
                            );
                            normal.set(
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 0],
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 1],
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 2]
                            );

                            position.add(
                                    normal.x() * this.scene.getRayOffset(),
                                    normal.y() * this.scene.getRayOffset(),
                                    normal.z() * this.scene.getRayOffset()
                            );

                            if (this.light instanceof Scene.EmissiveLight emissiveLight) {
                                for (int k = 0; k < emissiveLight.getEmissiveRays(); k++) {
                                    randomDirection(normal, outLightDirection);

                                    List<LocalRayResult> results = this.opaqueBVH.testRaySorted(position, outLightDirection, true);
                                    addRay();
                                    if (!results.isEmpty()) {
                                        LocalRayResult closest = results.get(0);
                                        closest.weights(rayWeights);

                                        float lu = closest.lerp(rayWeights, OFFSET_LIGHTMAP_XY + 0);
                                        float lv = closest.lerp(rayWeights, OFFSET_LIGHTMAP_XY + 1);

                                        int tx = Math.min(Math.max((int) (lu * this.lightmapSize), 0), this.lightmapSize - 1);
                                        int ty = Math.min(Math.max((int) (lv * this.lightmapSize), 0), this.lightmapSize - 1);

                                        this.direct.read(emissiveColor, tx, ty);

                                        if (emissiveColor.x() != 0f || emissiveColor.y() != 0f || emissiveColor.z() != 0f) {
                                            Vector3f blend = shadowBlend(position, outLightDirection, closest.getLocalDistance());
                                            if (blend != null) {
                                                emissiveColor.mul(
                                                        blend.x(),
                                                        blend.y(),
                                                        blend.z()
                                                );
                                            }
                                            totalShadow.add(emissiveColor.x(), emissiveColor.y(), emissiveColor.z());
                                        }
                                    }
                                }
                                samplesPassed += emissiveLight.getEmissiveRays();
                            } else {
                                int rays = this.scene.getShadowRaysPerSample();
                                if (this.light instanceof Scene.AmbientLight ambient) {
                                    rays = ambient.getAmbientRays();
                                }

                                if (this.scene.isFastModeEnabled()) {
                                    rays = 1;
                                }

                                for (int k = 0; k < rays; k++) {
                                    float length = Float.POSITIVE_INFINITY;

                                    if (this.light instanceof Scene.AmbientLight) {
                                        randomDirection(normal, outLightDirection);
                                    } else {
                                        this.light.randomLightDirection(position, outLightDirection);
                                        if (!(this.light instanceof Scene.DirectionalLight)) {
                                            length = outLightDirection.length();
                                            outLightDirection.div(length);
                                        }
                                    }

                                    if (!this.opaqueBVH.fastTestRay(position, outLightDirection, length)) {
                                        Vector3f blend = shadowBlend(position, outLightDirection, length);
                                        if (blend != null) {
                                            totalShadow.add(blend);
                                        } else {
                                            totalShadow.add(1f, 1f, 1f);
                                        }
                                    }
                                    addRay();
                                }

                                samplesPassed += rays;
                            }
                        }
                        if (samplesPassed != 0) {
                            totalShadow.div(samplesPassed);
                        }
                        this.shadow.write(totalShadow, x, y);
                    }
                }));
            }

            for (Future<?> t : tasks) {
                try {
                    t.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }

            addProgress(tasks.size());
            tasks.clear();
        }

        this.light.setLightSize(lightSize);
    }

    private void generateShadowMargins() {
        setStatus(getGroupName() + " - Generating Shadow Margins - " + this.lightIndex + ", " + this.light.getClass().getSimpleName(), this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.shadow, Lightmapper.IGNORE_SHADOW
            );
            MarginAutomata.generateMargin(io, DEFAULT_MARGIN_ITERATIONS);
            addProgress(1);
        }
    }

    private GaussianBlur.GaussianIO createGaussianIO(
            final Rectanglei rectangle,
            final Float3ImageBuffer buffer
    ) {
        GaussianBlur.GaussianIO gaussianIO = new GaussianBlur.GaussianIO() {
            @Override
            public int width() {
                return rectangle.lengthX();
            }

            @Override
            public int height() {
                return rectangle.lengthY();
            }

            @Override
            public boolean ignore(int x, int y) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                boolean ignore = true;
                int numSamples = Lightmapper.this.scene.getSamplingMode().numSamples();
                for (int s = 0; s < numSamples; s++) {
                    int state = Lightmapper.this.sampleStates.read(absX, absY, s);
                    if ((state & Lightmapper.FILLED) != 0) {
                        ignore = false;
                        break;
                    }
                }

                return ignore;
            }

            final Vector3f colorVector = new Vector3f();

            @Override
            public void read(int x, int y, GaussianBlur.GaussianColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                buffer.read(this.colorVector, absX, absY);
                color.r = this.colorVector.x();
                color.g = this.colorVector.y();
                color.b = this.colorVector.z();
            }

            @Override
            public void write(int x, int y, GaussianBlur.GaussianColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                this.colorVector.set(color.r, color.g, color.b);
                buffer.write(this.colorVector, absX, absY);
            }
        };

        return gaussianIO;
    }

    private void denoiseShadow() {
        float blurArea = this.scene.getShadowBlurArea();
        if (this.light instanceof Scene.EmissiveLight emissiveLight) {
            blurArea = emissiveLight.getEmissiveBlurArea();
        } else if (this.light instanceof Scene.AmbientLight ambientLight) {
            blurArea = ambientLight.getAmbientBlurArea();
        } else if (this.scene.isFastModeEnabled()) {
            blurArea = 0f;
        }
        if (blurArea == 0f) {
            return;
        }

        setStatus(getGroupName() + " - Denoising Shadow - " + this.lightIndex + ", " + this.light.getClass().getSimpleName(), this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            GaussianBlur.GaussianIO io = createGaussianIO(this.lightmapRectangles[i], this.shadow);
            GaussianBlur.blur(io, DEFAULT_GAUSSIAN_BLUR_KERNEL_SIZE, blurArea);
            addProgress(1);
        }
    }

    private void outputLight() {
        Vector3f directLight = new Vector3f();
        Vector3f shadowLight = new Vector3f();

        Vector3f resultLight = new Vector3f();
        Vector3f currentLight = new Vector3f();

        Vector3f currentEmissiveLight = new Vector3f();

        setStatus(getGroupName() + " - Writing to Lightmap - " + this.lightIndex + ", " + this.light.getClass().getSimpleName(), this.lightmapSize);
        for (int y = 0; y < this.lightmapSize; y++) {
            for (int x = 0; x < this.lightmapSize; x++) {
                this.direct.read(directLight, x, y);
                this.shadow.read(shadowLight, x, y);

                if (!this.scene.isShadowsEnabled()
                        && !(this.light instanceof Scene.EmissiveLight)
                        && !(this.light instanceof Scene.AmbientLight)) {
                    shadowLight.set(1f, 1f, 1f);
                }

                if (this.light instanceof Scene.EmissiveLight) {
                    this.lightmapEmissive.read(currentEmissiveLight, x, y);
                    currentEmissiveLight.add(directLight);
                    this.lightmapEmissive.write(currentEmissiveLight, x, y);

                    directLight.set(1f, 1f, 1f);
                }

                this.lightmap.read(currentLight, x, y);
                resultLight.set(directLight).mul(shadowLight).add(currentLight);
                this.lightmap.write(resultLight, x, y);
            }
            addProgress(1);
        }

        this.direct = null;
        this.shadow = null;
    }

    private void finishLightmapMargins() {
        setStatus(getGroupName() + " - Finishing Lightmap Margins", this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.lightmap, Lightmapper.EMPTY
            );
            MarginAutomata.generateMargin(io, this.lightmapMargin * 2);
            addProgress(1);
        }
    }

    private void finishEmissiveMargins() {
        setStatus(getGroupName() + " - Finishing Emissive Margins", this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.lightmapEmissive, Lightmapper.EMPTY
            );
            MarginAutomata.generateMargin(io, this.lightmapMargin * 2);
            addProgress(1);
        }
    }

    private class IndirectRay {

        Vector4f color;
        Vector3f light;

        IndirectRay reflected;
        IndirectRay refracted;
    }

    private IndirectRay testIndirect(Vector3f position, Vector3f direction, int depth) {
        if (depth >= this.scene.getIndirectBounces()) {
            return null;
        }

        List<LocalRayResult> opaqueRays = this.opaqueBVH.testRay(position, direction);
        List<LocalRayResult> alphaRays = this.alphaBVH.testRay(position, direction);

        addRay();
        addRay();

        List<LocalRayResult> rays = new ArrayList<>();

        rays.addAll(opaqueRays);
        rays.addAll(alphaRays);

        rays.sort((o1, o2) -> Float.compare(o1.getLocalDistance(), o2.getLocalDistance()));

        if (rays.isEmpty()) {
            return null;
        }

        Vector3f rayWeights = new Vector3f();
        Vector4f rayColor = new Vector4f();
        Vector3f rayLight = new Vector3f();
        Vector3f rayNormal = new Vector3f();

        IndirectRay firstRay = new IndirectRay();

        IndirectRay currentRay = firstRay;
        for (int i = 0; i < rays.size(); i++) {
            if (i != 0) {
                currentRay.refracted = new IndirectRay();
                currentRay = currentRay.refracted;
            }

            LocalRayResult ray = rays.get(i);
            ray.weights(rayWeights);

            float lu = ray.lerp(rayWeights, OFFSET_LIGHTMAP_XY + 0);
            float lv = ray.lerp(rayWeights, OFFSET_LIGHTMAP_XY + 1);

            int tx = Math.min(Math.max((int) (lu * this.lightmapSize), 0), this.lightmapSize - 1);
            int ty = Math.min(Math.max((int) (lv * this.lightmapSize), 0), this.lightmapSize - 1);

            this.textureColors.read(rayColor, tx, ty);
            this.lightmap.read(rayLight, tx, ty);

            rayNormal.set(
                    ray.lerp(rayWeights, OFFSET_TRIANGLE_NORMAL_XYZ + 0),
                    ray.lerp(rayWeights, OFFSET_TRIANGLE_NORMAL_XYZ + 1),
                    ray.lerp(rayWeights, OFFSET_TRIANGLE_NORMAL_XYZ + 2)
            );

            if (!ray.frontFace()) {
                rayNormal.negate();
            }

            currentRay.color = new Vector4f(rayColor);
            currentRay.light = new Vector3f(rayLight);

            if (currentRay.color.w() > 0f) {
                currentRay.reflected = testIndirect(
                        new Vector3f(ray.getLocalHitPosition())
                                .add(
                                        rayNormal.x() * this.scene.getRayOffset(),
                                        rayNormal.y() * this.scene.getRayOffset(),
                                        rayNormal.z() * this.scene.getRayOffset()
                                ),
                        new Vector3f(direction).reflect(rayNormal),
                        depth + 1
                );
            }

            if (currentRay.color.w() >= 1f) {
                break;
            }
        }

        return firstRay;
    }

    private Vector3f collapseIndirectRay(IndirectRay ray) {
        List<IndirectRay> rays = new ArrayList<>();

        {
            IndirectRay currentRay = ray;
            do {
                rays.add(currentRay);
                currentRay = currentRay.refracted;
            } while (currentRay != null);
        }

        Vector3f totalLightColor = new Vector3f(0f);
        Vector3f lightColor = new Vector3f();
        for (int i = (rays.size() - 1); i >= 0; i--) {
            IndirectRay currentRay = rays.get(i);

            totalLightColor.mul(
                    ((currentRay.color.x() * currentRay.color.w()) + (1f - currentRay.color.w())) * (1f - currentRay.color.w()),
                    ((currentRay.color.y() * currentRay.color.w()) + (1f - currentRay.color.w())) * (1f - currentRay.color.w()),
                    ((currentRay.color.z() * currentRay.color.w()) + (1f - currentRay.color.w())) * (1f - currentRay.color.w())
            );

            lightColor
                    .set(currentRay.light);

            if (currentRay.reflected != null) {
                lightColor.add(
                        collapseIndirectRay(currentRay.reflected)
                );
            }

            lightColor.mul(
                    currentRay.color.x() * currentRay.color.w(),
                    currentRay.color.y() * currentRay.color.w(),
                    currentRay.color.z() * currentRay.color.w()
            );

            totalLightColor.add(lightColor);
        }

        return totalLightColor;
    }

    private void bakeIndirect() {
        setStatus(getGroupName() + " - Baking Indirect", this.lightmapSize);
        for (int i = 0; i < this.lightmapSize; i += this.numberOfThreads) {
            List<Future<?>> tasks = new ArrayList<>();

            for (int j = 0; j < this.numberOfThreads; j++) {
                final int y = i + j;
                if (y >= this.lightmapSize) {
                    break;
                }
                tasks.add(this.service.submit(() -> {
                    Vector3f totalIndirect = new Vector3f();

                    Vector3f sampleWeights = new Vector3f();

                    Vector3f normal = new Vector3f();
                    Vector3f position = new Vector3f();
                    Vector3f direction = new Vector3f();

                    int numSamples = this.scene.getSamplingMode().numSamples();
                    for (int x = 0; x < this.lightmapSize; x++) {
                        totalIndirect.zero();
                        int samplesPassed = 0;
                        for (int s = 0; s < numSamples; s++) {
                            int sampleState = this.sampleStates.read(x, y, s);
                            if ((sampleState & FILLED) == 0 || (sampleState & IGNORE_AMBIENT) != 0) {
                                continue;
                            }

                            this.weights.read(sampleWeights, x, y, s);
                            int triangle = this.triangles.read(x, y, s);

                            position.set(
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 0),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 1),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 2)
                            );
                            normal.set(
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 0],
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 1],
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 2]
                            );

                            position.add(
                                    normal.x() * this.scene.getRayOffset(),
                                    normal.y() * this.scene.getRayOffset(),
                                    normal.z() * this.scene.getRayOffset()
                            );

                            for (int k = 0; k < this.scene.getIndirectRaysPerSample(); k++) {
                                randomDirection(normal, direction);
                                IndirectRay indirect = testIndirect(position, direction, 0);
                                if (indirect != null) {
                                    totalIndirect.add(collapseIndirectRay(indirect).mul(this.scene.getIndirectLightReflectionFactor()));
                                }
                            }
                            samplesPassed += this.scene.getIndirectRaysPerSample();
                        }
                        if (samplesPassed != 0) {
                            totalIndirect.div(samplesPassed);
                        }
                        this.lightmapIndirect.write(totalIndirect, x, y);
                    }
                }));
            }

            for (Future<?> t : tasks) {
                try {
                    t.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }
            addProgress(tasks.size());
            tasks.clear();
        }
    }

    private void generateIndirectMargins() {
        setStatus(getGroupName() + " - Generating Indirect Margins", this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.lightmapIndirect, Lightmapper.IGNORE_AMBIENT
            );
            MarginAutomata.generateMargin(io, DEFAULT_MARGIN_ITERATIONS);
            addProgress(1);
        }
    }

    private void denoiseIndirect() {
        float blurArea = this.scene.getIndirectLightingBlurArea();
        if (blurArea == 0f) {
            return;
        }
        setStatus(getGroupName() + " - Denoising Indirect", this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            GaussianBlur.GaussianIO io = createGaussianIO(this.lightmapRectangles[i], this.lightmapIndirect);
            GaussianBlur.blur(io, DEFAULT_GAUSSIAN_BLUR_KERNEL_SIZE, blurArea);
            addProgress(1);
        }
    }

    private void finishIndirectMargins() {
        setStatus(getGroupName() + " - Finishing Indirect Margins", this.lightmapRectangles.length);
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.lightmapIndirect, Lightmapper.EMPTY
            );
            MarginAutomata.generateMargin(io, this.lightmapMargin * 2);
            addProgress(1);
        }
    }

    private void outputIndirect() {
        Vector3f directLight = new Vector3f();
        Vector3f indirectLight = new Vector3f();

        if (this.scene.fillEmptyValuesWithLightColors() && !this.scene.isIndirectLightingEnabled()) {
            indirectLight.zero();
            for (Scene.Light li : this.group.lights) {
                if (li instanceof Scene.AmbientLight ambient) {
                    indirectLight.add(
                            ambient.getDiffuse().x(),
                            ambient.getDiffuse().y(),
                            ambient.getDiffuse().z()
                    );
                }
            }
        }

        setStatus(getGroupName() + " - Writing Indirect to Lightmap", this.lightmapSize);
        for (int y = 0; y < this.lightmapSize; y++) {
            for (int x = 0; x < this.lightmapSize; x++) {
                if (this.scene.isIndirectLightingEnabled()) {
                    this.lightmapIndirect.read(indirectLight, x, y);
                }

                this.lightmap.read(directLight, x, y);
                if (this.scene.isDirectLightingEnabled()) {
                    directLight.add(indirectLight);
                } else {
                    directLight.set(indirectLight);
                }
                this.lightmap.write(directLight, x, y);
            }
            addProgress(1);
        }

        this.lightmapIndirect = null;
    }

    private void sampleAmbientCubes() {
        Vector3f ambient = new Vector3f(0f, 0f, 0f);
        for (Scene.Light l : this.group.lights) {
            if (l instanceof Scene.AmbientLight ambientLight) {
                ambient.add(ambientLight.getDiffuse());
            }
        }

        setStatus(getGroupName() + " - Sampling Ambient Cubes ("+this.ambientCubes.size()+")", this.ambientCubes.size());
        for (int i = 0; i < this.ambientCubes.size(); i += this.numberOfThreads) {
            List<Future<?>> tasks = new ArrayList<>();

            for (int j = 0; j < this.numberOfThreads; j++) {
                final int index = i + j;
                if (index >= this.ambientCubes.size()) {
                    break;
                }
                tasks.add(this.service.submit(() -> {
                    LightmapAmbientCube cube = this.ambientCubes.get(index);
                    AmbientCube currentCube = cube.getAmbientCube(this.groupIndex);

                    Vector3f sideColor = new Vector3f(0f, 0f, 0f);
                    Vector3f rayDirection = new Vector3f(0f, 0f, 0f);
                    Vector3f rayWeights = new Vector3f();

                    Vector3f rayLight = new Vector3f();
                    Vector3f rayEmissive = new Vector3f();
                    Vector4f rayColor = new Vector4f();

                    Vector3f finalColor = new Vector3f();

                    for (int side = 0; side < AmbientCube.SIDES; side++) {
                        sideColor.zero();
                        for (int k = 0; k < NUMBER_OF_AMBIENT_CUBE_RAYS_PER_SIDE; k++) {
                            AmbientCube.randomSideDirection180(side, rayDirection);

                            List<LocalRayResult> results = this.opaqueBVH.testRay(
                                    cube.getPosition(),
                                    rayDirection
                            );
                            results.addAll(this.alphaBVH.testRay(cube.getPosition(), rayDirection));
                            Comparator<LocalRayResult> comparator = ((o1, o2) -> Float.compare(o1.getLocalDistance(), o2.getLocalDistance()));
                            results.sort(comparator.reversed());

                            finalColor.set(ambient);

                            for (LocalRayResult ray : results) {
                                ray.weights(rayWeights);

                                float lu = ray.lerp(rayWeights, OFFSET_LIGHTMAP_XY + 0);
                                float lv = ray.lerp(rayWeights, OFFSET_LIGHTMAP_XY + 1);

                                int tx = Math.min(Math.max((int) (lu * this.lightmapSize), 0), this.lightmapSize - 1);
                                int ty = Math.min(Math.max((int) (lv * this.lightmapSize), 0), this.lightmapSize - 1);

                                this.lightmap.read(rayLight, tx, ty);
                                this.lightmapEmissive.read(rayEmissive, tx, ty);
                                this.textureColors.read(rayColor, tx, ty);

                                rayLight
                                        .mul(rayColor.x(), rayColor.y(), rayColor.z())
                                        .add(rayEmissive.x(), rayEmissive.y(), rayEmissive.z())
                                        .mul(rayColor.w());

                                finalColor.mul(
                                        (rayColor.x() * rayColor.w()) + (1f - rayColor.w()),
                                        (rayColor.y() * rayColor.w()) + (1f - rayColor.w()),
                                        (rayColor.z() * rayColor.w()) + (1f - rayColor.w())
                                ).mul(1f - rayColor.w());

                                finalColor.add(rayLight);
                            }

                            sideColor.add(
                                    finalColor.x(),
                                    finalColor.y(),
                                    finalColor.z()
                            );

                            addRay();
                        }
                        sideColor.div(NUMBER_OF_AMBIENT_CUBE_RAYS_PER_SIDE);
                        currentCube.setSide(side, sideColor);
                    }
                }));
            }

            for (Future<?> t : tasks) {
                try {
                    t.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }

            addProgress(tasks.size());
            tasks.clear();
        }
    }

    private void outputLightmap() {
        setStatus(getGroupName() + " - Finishing Lightmap", 1);

        this.totalLightmaps[this.groupIndex] = this.lightmap.getData();
        this.totalLightmapsEmissive[this.groupIndex] = this.lightmapEmissive.getData();

        this.lightmap = null;
        this.lightmapEmissive = null;

        addProgress(1);
    }

    public LightmapperOutput bake() {
        this.numberOfThreads = NUMBER_OF_THREADS;
        this.service = Executors.newFixedThreadPool(this.numberOfThreads);
        try {
            rasterizeBarycentricBuffers();

            readTextureColors();
            readTextureEmissiveColors();

            generateTextureColorsMargins();
            generateTextureEmissiveColorsMargins();

            placeAmbientCubes();

            for (int i = 0; i < this.lightGroups.length; i++) {
                prepareLightmap(i);

                for (int j = 0; j < this.group.lights.size(); j++) {
                    if (this.scene.isFastModeEnabled() && (this.group.lights.get(j) instanceof Scene.EmissiveLight || this.group.lights.get(j) instanceof Scene.AmbientLight)) {
                        continue;
                    }

                    prepareLight(j);

                    bakeDirect();
                    generateDirectMargins();

                    if (this.scene.isShadowsEnabled()) {
                        bakeShadow();
                        generateShadowMargins();
                        denoiseShadow();
                    }

                    outputLight();
                }

                finishLightmapMargins();
                finishEmissiveMargins();

                if (this.scene.isIndirectLightingEnabled()) {
                    bakeIndirect();
                    generateIndirectMargins();
                    denoiseIndirect();

                    finishIndirectMargins();
                }

                outputIndirect();
                sampleAmbientCubes();
                outputLightmap();
            }

            setStatus("Generating Ambient Cube BVH", 1);
            LightmapAmbientCubeBVH ambientCubeBVH = LightmapAmbientCubeBVH.create(this.ambientCubes);
            addProgress(1);

            setStatus("Done", 1);
            addProgress(1);

            return new LightmapperOutput(
                    this.lightmapSize,
                    this.lightmapsNames,
                    this.totalLightmaps,
                    this.totalLightmapsEmissive,
                    this.textureColors.getData(),
                    ambientCubeBVH
            );
        } finally {
            this.service.shutdownNow();
        }
    }

    public String getStatus() {
        return status;
    }

    public double getRaysPerSecond() {
        return (((double) this.raysCount) / (System.currentTimeMillis() - this.raysTime)) * 1000.0;
    }

    public double getProgress() {
        if (this.progressMax == 0) {
            return 0.0;
        }
        return ((double) this.progressCount) / this.progressMax;
    }

}
