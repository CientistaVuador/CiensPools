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

import cientistavuador.cienspools.util.postprocess.MarginAutomata;
import cientistavuador.cienspools.util.postprocess.GaussianBlur;
import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.geometry.Geometry;
import cientistavuador.cienspools.resources.mesh.MeshData;
import cientistavuador.cienspools.util.MeshUtils;
import cientistavuador.cienspools.util.RasterUtils;
import cientistavuador.cienspools.util.raycast.RayResult;
import cientistavuador.cienspools.util.SoftwareTexture;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class BakedLighting {

    private static final float EPSILON = 0.0001f;

    private static class LightGroup {

        public String groupName = "";
        public final List<Scene.Light> lights = new ArrayList<>();
    }

    public static interface BakedLightingOutput {

        public void prepare(Geometry geometry, MeshData.LightmapMesh mesh, int lightmapSize, String[] groups);

        public void write(float[] lightmap, int groupIndex);
    }

    public static class Status {

        private Future<Void> task;

        private String currentStatus = "Idle";
        private float currentProgress = 0f;
        private boolean error = false;

        private float progressBarStep = 0f;

        private long timeStart = 0;
        private volatile long rays = 0;

        private long progressBarStart = System.currentTimeMillis();

        public Status() {

        }

        private void setProgressBarStep(float max) {
            this.currentProgress = 0f;
            this.progressBarStep = 100f / max;

            this.progressBarStart = System.currentTimeMillis();
        }

        private void stepProgressBar() {
            if ((this.currentProgress + this.progressBarStep) > 100f) {
                this.currentProgress = 100f;
                return;
            }
            this.currentProgress += this.progressBarStep;
        }

        public int getCurrentProgress() {
            return (int) this.currentProgress;
        }

        public String getCurrentStatus() {
            return currentStatus;
        }

        public String getASCIIProgressBar() {
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            int progress = getCurrentProgress() / 5;
            for (int i = 0; i < progress; i++) {
                builder.append('#');
            }
            for (int i = progress; i < 20; i++) {
                builder.append('.');
            }
            builder.append(']').append(" - ").append(getCurrentProgress()).append('%');
            return builder.toString();
        }

        public boolean isDone() {
            return this.task.isDone();
        }

        public boolean hasError() {
            return this.error;
        }

        public void throwException() throws ExecutionException {
            if (!isDone()) {
                return;
            }
            try {
                this.task.get();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        public double getRaysPerSecond() {
            double time = (System.currentTimeMillis() - this.timeStart);
            if (time == 0) {
                return 0;
            }
            double raysPerSecond = (this.rays / time) * 1000f;
            return raysPerSecond;
        }

        public String getRaysPerSecondFormatted() {
            StringBuilder b = new StringBuilder();
            Formatter formatter = new Formatter(b);
            formatter.format("%,.2f", getRaysPerSecond());
            b.append(" Rays Per Second");
            return b.toString();
        }

        public int getEstimatedTime() {
            float speed = this.currentProgress / ((System.currentTimeMillis() - this.progressBarStart) / 1000f);
            return (int) ((100f - this.currentProgress) / speed);
        }

        public String getEstimatedTimeFormatted() {
            int estimated = getEstimatedTime();

            int hours = estimated / 3600;
            estimated -= hours * 3600;
            int minutes = estimated / 60;
            estimated -= minutes * 60;
            int seconds = estimated;

            return hours + "h " + minutes + "m " + seconds + "s";
        }
    }

    public static Status dummyStatus() {
        Status status = new Status();

        status.currentStatus = "Idle";
        status.currentProgress = 0f;

        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);
        status.task = future;

        return status;
    }

    public static Status bake(BakedLightingOutput output, Scene scene) {
        Status status = new Status();
        BakedLighting baked = new BakedLighting(output, scene, status);
        status.task = CompletableFuture.runAsync(() -> {
            try {
                baked.bake();
                status.currentProgress = 100f;
                status.currentStatus = "Done.";
            } catch (Throwable t) {
                status.currentProgress = 0f;
                status.currentStatus = "Error: " + t.getLocalizedMessage();
                status.error = true;
                throw t;
            }
        });
        return status;
    }

    private static class WeightsBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public WeightsBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public void write(Vector3f position, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = position.x();
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = position.y();
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = position.z();
        }

        public void read(Vector3f position, int x, int y, int sample) {
            position.set(
                    this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)]
            );
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

    private static class BooleanBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final boolean[] data;

        public BooleanBuffer(int size, int samples) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new boolean[size * this.lineSize];
        }

        public void write(boolean value, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = value;
        }

        public boolean read(int x, int y, int sample) {
            return this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
        }
    }

    private static class ColorBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public ColorBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public void write(Vector3f color, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = color.x();
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = color.y();
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = color.z();
        }

        public void read(Vector3f color, int x, int y, int sample) {
            float vx = this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            float vy = this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            float vz = this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            color.set(
                    vx,
                    vy,
                    vz
            );
        }
    }

    private static class GrayBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public GrayBuffer(int size, int samples) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public void write(float value, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = value;
        }

        public float read(int x, int y, int sample) {
            return this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
        }
    }

    //global state
    private final ExecutorService threads = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final BakedLightingOutput output;
    private final Scene scene;
    private final List<Geometry> geometries;
    private final List<LightGroup> lightGroups;
    private final String[] groupsNames;
    private final SamplingMode samplingMode;
    private final boolean fastMode;
    private final float pixelToWorldRatio;
    private final Status status;
    private final Map<Integer, SoftwareTexture> sceneTextures = new HashMap<>();
    private final MeshData.LightmapMesh[] lightmapMeshes;

    //geometry state
    private int geometryIndex = 0;
    private Geometry geometry = null;
    private int geometryLightmapSize;
    private float[] vertices = null;
    private int[] indices = null;
    private LightmapUVs.LightmapperQuad[] lightmapperQuads = null;
    private MeshData.LightmapMesh lightmapMesh = null;

    //geometry state (buffers)
    private BooleanBuffer sampleBuffer = null;
    private IntegerBuffer trianglesBuffer = null;
    private WeightsBuffer weightsBuffer = null;

    //light group state
    private int currentLightGroupIndex = 0;
    private LightGroup currentLightGroup = null;
    private List<Scene.Light> currentLightGroupLights = null;

    //light group state (buffers)
    private ColorBuffer lightGroupOutput = null;

    //light state
    private int currentLightIndex = 0;
    private Scene.Light currentLight = null;
    private int lightType = 0;
    private Scene.DirectionalLight sun = null;
    private Scene.PointLight point = null;
    private Scene.SpotLight spot = null;

    //light state (buffers)
    private ColorBuffer indirectColorBuffer = null;
    private ColorBuffer directColorBuffer = null;
    private GrayBuffer reverseShadowBuffer = null;

    private BakedLighting(BakedLightingOutput output, Scene scene, Status status) {
        this.output = output;
        this.scene = scene;
        this.geometries = scene.getGeometries();
        this.lightmapMeshes = new MeshData.LightmapMesh[this.geometries.size()];
        this.lightGroups = new ArrayList<>();
        this.pixelToWorldRatio = scene.getPixelToWorldRatio();
        this.status = status;
        this.samplingMode = scene.getSamplingMode();
        this.fastMode = scene.isFastModeEnabled();

        List<String> names = new ArrayList<>();
        for (Scene.Light light : scene.getLights()) {
            LightGroup group = null;
            for (LightGroup g : this.lightGroups) {
                if (g.groupName.equals(light.getGroupName())) {
                    group = g;
                    break;
                }
            }
            if (group == null) {
                group = new LightGroup();
                group.groupName = light.getGroupName();
                this.lightGroups.add(group);
                names.add(light.getGroupName());
            }
            group.lights.add(light);
        }
        this.groupsNames = names.toArray(String[]::new);
    }

    private void setStatusText(String s) {
        StringBuilder b = new StringBuilder();
        if (this.geometry != null) {
            b.append('[').append(this.geometry.getMesh().getName()).append("] ");
            b.append('[').append(this.geometryIndex).append('/').append(this.geometries.size()).append("] ");
        }
        if (this.currentLightGroup != null) {
            b.append('[').append(this.currentLightGroup.groupName).append("] ");
            b.append('[').append(this.currentLightGroupIndex).append('/').append(this.lightGroups.size()).append("] ");
        }
        if (this.currentLight != null) {
            b.append('[').append(this.currentLightIndex).append('/').append(this.currentLightGroupLights.size()).append("] ");
        }
        b.append(s);
        this.status.currentStatus = b.toString();
    }

    private void loadTextures() {
        this.status.setProgressBarStep(this.geometries.size());

        for (Geometry geo : this.geometries) {
            setStatusText("Loading Texture [" + geo.getMesh().getName() + "]");

            SoftwareTexture t = this.sceneTextures.get(geo.getMesh().getTextureHint());
            if (t == null) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                Main.MAIN_TASKS.add(() -> {
                    try {
                        this.sceneTextures.put(
                                geo.getMesh().getTextureHint(),
                                SoftwareTexture.fromGLTexture2D(geo.getMesh().getTextureHint())
                        );
                        Main.checkGLError();
                        future.complete(null);
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    }
                });
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }

            this.status.stepProgressBar();
        }
    }

    private void scheduleLightmapMeshes() {
        this.status.setProgressBarStep(this.geometries.size());

        Vector3f scale = new Vector3f();

        for (int i = 0; i < this.lightmapMeshes.length; i++) {
            Geometry geo = this.geometries.get(i);

            setStatusText("[" + geo.getMesh().getName() + "] [" + i + "/" + this.geometries.size() + "] Scheduling Lightmap UVs");

            geo.getModel().getScale(scale);
            this.lightmapMeshes[i] = geo.getMesh()
                    .scheduleLightmapMesh(
                            this.pixelToWorldRatio,
                            scale.x() * geo.getLightmapScale(),
                            scale.y() * geo.getLightmapScale(),
                            scale.z() * geo.getLightmapScale()
                    );

            this.status.stepProgressBar();
        }
    }

    private void waitForLightmapMeshes() {
        this.status.setProgressBarStep(this.geometries.size());

        for (int i = 0; i < this.lightmapMeshes.length; i++) {
            Geometry geo = this.geometries.get(i);

            setStatusText("[" + geo.getMesh().getName() + "] [" + i + "/" + this.geometries.size() + "] Waiting for Lightmap UVs");

            this.lightmapMeshes[i].getLightmapSize();

            this.status.stepProgressBar();
        }
    }

    private void waitForBVHs() {
        this.status.setProgressBarStep(this.geometries.size());
        for (Geometry geo : this.geometries) {
            setStatusText("[" + geo.getMesh().getName() + "] Waiting For BVH");
            geo.getMesh().getBVH();
            this.status.stepProgressBar();
        }
    }

    private void loadGeometry(int index) {
        this.geometryIndex = index;
        this.geometry = this.geometries.get(index);

        setStatusText("Loading Geometry...");
        this.status.currentProgress = 0f;

        this.lightmapMesh = this.lightmapMeshes[index];
        this.geometryLightmapSize = this.lightmapMesh.getLightmapSize();
        this.lightmapperQuads = this.lightmapMesh.getQuads();

        this.vertices = this.geometry.getMesh().getVertices();
        this.indices = this.geometry.getMesh().getIndices();

        int numSamples = this.samplingMode.numSamples();

        this.sampleBuffer = new BooleanBuffer(this.geometryLightmapSize, numSamples);
        this.trianglesBuffer = new IntegerBuffer(this.geometryLightmapSize, numSamples);
        this.weightsBuffer = new WeightsBuffer(this.geometryLightmapSize, numSamples);

        final BakedLightingOutput finalOutput = this.output;
        final Geometry finalGeometry = this.geometry;
        final MeshData.LightmapMesh finalMesh = this.lightmapMesh;
        final int finalLightmapSize = this.geometryLightmapSize;
        final String[] finalGroupsNames = this.groupsNames;

        Main.MAIN_TASKS.add(() -> {
            finalOutput.prepare(finalGeometry, finalMesh, finalLightmapSize, finalGroupsNames);
        });

        this.status.currentProgress = 100f;
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

    private void computeGeometryBuffers() {
        Vector3f weights = new Vector3f();

        Vector3f pixelPos = new Vector3f();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();

        this.status.setProgressBarStep(this.lightmapperQuads.length);

        for (int i = 0; i < this.lightmapperQuads.length; i++) {
            setStatusText("[" + i + "/" + this.lightmapperQuads.length + "] Computing Geometry Buffers");

            LightmapUVs.LightmapperQuad quad = this.lightmapperQuads[i];

            int[] triangles = quad.getTriangles();
            float[] lightmapVertices = quad.getUVs();

            for (int j = 0; j < triangles.length; j++) {
                int triangle = triangles[j];

                float v0x = lightmapVertices[(((j * 3) + 0) * 2) + 0] + quad.getX();
                float v0y = lightmapVertices[(((j * 3) + 0) * 2) + 1] + quad.getY();

                float v1x = lightmapVertices[(((j * 3) + 1) * 2) + 0] + quad.getX();
                float v1y = lightmapVertices[(((j * 3) + 1) * 2) + 1] + quad.getY();

                float v2x = lightmapVertices[(((j * 3) + 2) * 2) + 0] + quad.getX();
                float v2y = lightmapVertices[(((j * 3) + 2) * 2) + 1] + quad.getY();

                a.set(v0x, v0y, 0f);
                b.set(v1x, v1y, 0f);
                c.set(v2x, v2y, 0f);

                int minX = (int) Math.floor(Math.min(v0x, Math.min(v1x, v2x)));
                int minY = (int) Math.floor(Math.min(v0y, Math.min(v1y, v2y)));
                int maxX = (int) Math.ceil(Math.max(v0x, Math.max(v1x, v2x)));
                int maxY = (int) Math.ceil(Math.max(v0y, Math.max(v1y, v2y)));

                minX = clamp(minX, 0, this.geometryLightmapSize - 1);
                minY = clamp(minY, 0, this.geometryLightmapSize - 1);
                maxX = clamp(maxX, 0, this.geometryLightmapSize - 1);
                maxY = clamp(maxY, 0, this.geometryLightmapSize - 1);

                SamplingMode mode = this.samplingMode;

                raster:
                for (int y = minY; y <= maxY; y++) {
                    for (int x = minX; x <= maxX; x++) {
                        if (y % 2 == 0 && x % 2 == 0) {
                            continue;
                        }
                        if (y % 2 != 0 && y % 2 != 0) {
                            continue;
                        }
                        for (int s = 0; s < mode.numSamples(); s++) {
                            float sampleX = mode.sampleX(s);
                            float sampleY = mode.sampleY(s);

                            pixelPos.set(x + sampleX, y + sampleY, 0f);

                            RasterUtils.barycentricWeights(pixelPos, a, b, c, weights);

                            float wx = weights.x();
                            float wy = weights.y();
                            float wz = weights.z();

                            if (!Float.isFinite(wx) || !Float.isFinite(wy) || !Float.isFinite(wz)) {
                                break raster;
                            }

                            if (wx < 0f || wy < 0f || wz < 0f) {
                                continue;
                            }

                            int i0 = this.indices[(triangle * 3) + 0];
                            int i1 = this.indices[(triangle * 3) + 1];
                            int i2 = this.indices[(triangle * 3) + 2];

                            position.set(
                                    lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 0),
                                    lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 1),
                                    lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 2)
                            );

                            this.geometry.getModel().transformProject(position);

                            MeshUtils.calculateTriangleNormal(
                                    this.vertices,
                                    MeshData.SIZE,
                                    MeshData.XYZ_OFFSET,
                                    i0, i1, i2,
                                    normal
                            );
                            
                            this.geometry.getNormalModel().transform(normal).normalize();
                            
                            position.add(normal.mul(this.scene.getRayOffset()));

                            if (Geometry.testSphere(position.x(), position.y(), position.z(), this.scene.getRayOffset() * 0.90f, this.geometries)) {
                                continue;
                            }

                            this.sampleBuffer.write(true, x, y, s);
                            this.trianglesBuffer.write(triangle, x, y, s);
                            this.weightsBuffer.write(weights, x, y, s);
                        }
                    }
                }
            }

            this.status.stepProgressBar();
        }
    }

    private void loadLightGroup(int index) {
        this.currentLightGroup = this.lightGroups.get(index);
        this.currentLightGroupIndex = index;
        this.currentLightGroupLights = this.currentLightGroup.lights;

        setStatusText("Loading Light Group...");
        this.status.currentProgress = 0f;

        this.lightGroupOutput = new ColorBuffer(this.geometryLightmapSize, 1);

        this.status.currentProgress = 100f;
    }

    private void loadLight(int index) {
        this.currentLight = this.currentLightGroup.lights.get(index);
        this.currentLightIndex = index;

        setStatusText("Loading Light...");
        this.status.currentProgress = 0f;

        if (this.currentLight instanceof Scene.DirectionalLight s) {
            this.sun = s;
            this.point = null;
            this.spot = null;
            this.lightType = 0;
        } else if (this.currentLight instanceof Scene.PointLight p) {
            this.sun = null;
            if (this.currentLight instanceof Scene.SpotLight s) {
                this.spot = s;
                this.lightType = 2;
            } else {
                this.spot = null;
                this.lightType = 1;
            }
            this.point = p;
        } else {
            throw new RuntimeException("Unsupported Light Type: " + this.currentLight.getClass());
        }

        int numSamples = this.samplingMode.numSamples();

        this.indirectColorBuffer = new ColorBuffer(this.geometryLightmapSize, numSamples);
        this.directColorBuffer = new ColorBuffer(this.geometryLightmapSize, numSamples);
        this.reverseShadowBuffer = new GrayBuffer(this.geometryLightmapSize, numSamples);

        this.status.currentProgress = 100f;
    }

    private float lerp(Vector3fc weights, int i0, int i1, int i2, int offset) {
        float va  = this.vertices[(i0 * MeshData.SIZE) + offset];
        float vb = this.vertices[(i1 * MeshData.SIZE) + offset];
        float vc = this.vertices[(i2 * MeshData.SIZE) + offset];
        return (va  * weights.x()) + (vb * weights.y()) + (vc * weights.z());
    }

    private void bakeLightComponents() {
        int amountOfCores = Runtime.getRuntime().availableProcessors();

        this.status.setProgressBarStep(this.geometryLightmapSize);

        List<Future<?>> tasks = new ArrayList<>(amountOfCores);

        this.status.timeStart = System.currentTimeMillis();
        this.status.rays = 0;
        for (int y = 0; y < this.geometryLightmapSize; y += amountOfCores) {

            setStatusText("[" + y + "/" + this.geometryLightmapSize + "] Baking");

            for (int i = 0; i < amountOfCores; i++) {
                final int line = y + i;
                if (line >= this.geometryLightmapSize) {
                    break;
                }
                tasks.add(this.threads.submit(() -> {
                    processLine(line);
                }));
            }

            for (Future<?> f : tasks) {
                try {
                    f.get();
                    this.status.stepProgressBar();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }

            tasks.clear();
        }
        this.status.rays = 0;
    }

    private static class SampleState {

        public final Random random = new Random();
        public int x;
        public int y;
        public int s;
        public int triangle = -1;
        public final Vector3f triangleNormal = new Vector3f();
        public final Vector3f position = new Vector3f();
        public final Vector3f normal = new Vector3f();
        public final Matrix3f triangleTBN = new Matrix3f();
    }

    private class DirectState {

        public final Vector3f output = new Vector3f();
    }

    private class ShadowState {

        public float output;
        public final Vector3f randomDirection = new Vector3f();
        public final Vector3f offsetOrigin = new Vector3f();

    }

    private class IndirectState {

        public final Vector3f output = new Vector3f();
        public final Vector3f[] bounceColors = new Vector3f[BakedLighting.this.scene.getIndirectBounces()];

        {
            for (int i = 0; i < this.bounceColors.length; i++) {
                this.bounceColors[i] = new Vector3f();
            }
        }
        public final Vector3f lightColor = new Vector3f();
        public final Vector3f randomLightDirection = new Vector3f();
        public final Vector3f bouncePosition = new Vector3f();
        public final Vector3f bounceDirection = new Vector3f();
        public final Vector3f bounceWeights = new Vector3f();
        public final Vector3f smoothNormal = new Vector3f();
        public final float[] bounceColor = new float[4];
    }

    private void processLine(int y) {
        SampleState state = new SampleState();
        DirectState direct = new DirectState();
        ShadowState shadow = new ShadowState();
        IndirectState indirect = new IndirectState();

        Vector3f weights = new Vector3f();

        Vector3f tangent = new Vector3f();
        Vector3f bitangent = new Vector3f();

        int i0 = -1;
        int i1 = -1;
        int i2 = -1;

        for (int x = 0; x < this.geometryLightmapSize; x++) {
            for (int s = 0; s < this.samplingMode.numSamples(); s++) {
                boolean filled = this.sampleBuffer.read(x, y, s);
                if (!filled) {
                    continue;
                }

                state.x = x;
                state.y = y;
                state.s = s;

                int currentTriangle = this.trianglesBuffer.read(x, y, s);
                if (currentTriangle != state.triangle) {
                    state.triangle = currentTriangle;
                    i0 = this.indices[(currentTriangle * 3) + 0];
                    i1 = this.indices[(currentTriangle * 3) + 1];
                    i2 = this.indices[(currentTriangle * 3) + 2];
                    MeshUtils.calculateTriangleNormal(
                            this.vertices,
                            MeshData.SIZE,
                            MeshData.XYZ_OFFSET,
                            i0, i1, i2,
                            state.triangleNormal
                    );
                    this.geometry
                            .getNormalModel()
                            .transform(state.triangleNormal)
                            .normalize();

                    float upX = 0f;
                    float upY = 1f;
                    float upZ = 0f;

                    if (Math.abs(state.triangleNormal.dot(upX, upY, upZ)) >= (1f - EPSILON)) {
                        upY = 0f;
                        upX = 1f;
                    }

                    state.triangleNormal.cross(upX, upY, upZ, tangent).normalize();
                    state.triangleNormal.cross(tangent, bitangent).normalize();

                    state.triangleTBN.set(tangent, bitangent, state.triangleNormal);
                }

                this.weightsBuffer.read(weights, x, y, s);

                state.position.set(
                        lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 0),
                        lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 1),
                        lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 2)
                );
                state.normal.set(
                        lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 0),
                        lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 1),
                        lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 2)
                );

                tangent.set(
                        lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 0),
                        lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 1),
                        lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 2)
                );

                this.geometry.getModel().transformProject(state.position);
                this.geometry.getNormalModel().transform(state.normal);
                this.geometry.getNormalModel().transform(tangent);

                state.normal.normalize();
                tangent.normalize();
                bitangent.set(state.normal).cross(tangent).normalize();

                direct.output.zero();
                shadow.output = 0f;
                indirect.output.zero();

                processSample(
                        state,
                        direct,
                        shadow,
                        indirect
                );

                this.directColorBuffer.write(direct.output, x, y, s);
                this.reverseShadowBuffer.write(shadow.output, x, y, s);
                this.indirectColorBuffer.write(indirect.output, x, y, s);
            }
        }
    }

    private void randomLightDirection(Vector3f position, Vector3f outDirection, Random random) {
        switch (this.lightType) {
            case 0 -> {
                if (this.fastMode) {
                    outDirection.set(this.sun.getDirectionNegated());
                    return;
                }

                float x;
                float y;
                float z;
                float dist;

                do {
                    x = (random.nextFloat() * 2f) - 1f;
                    y = (random.nextFloat() * 2f) - 1f;
                    z = (random.nextFloat() * 2f) - 1f;
                    dist = (x * x) + (y * y) + (z * z);
                } while (dist > 1f);

                outDirection.set(
                        x,
                        y,
                        z
                )
                        .normalize()
                        .mul(this.sun.getLightSize())
                        .add(this.sun.getDirectionNegated())
                        .normalize();
            }
            case 1, 2 -> {
                if (this.fastMode) {
                    outDirection.set(this.point.getPosition()).sub(position);
                    return;
                }

                float dirX = this.point.getPosition().x() - position.x();
                float dirY = this.point.getPosition().y() - position.y();
                float dirZ = this.point.getPosition().z() - position.z();
                float invlength = (float) (1f / Math.sqrt((dirX * dirX) + (dirY * dirY) + (dirZ * dirZ)));
                dirX *= invlength;
                dirY *= invlength;
                dirZ *= invlength;

                do {
                    float x;
                    float y;
                    float z;
                    float dist;

                    do {
                        x = (random.nextFloat() * 2f) - 1f;
                        y = (random.nextFloat() * 2f) - 1f;
                        z = (random.nextFloat() * 2f) - 1f;
                        dist = (x * x) + (y * y) + (z * z);
                    } while (dist > 1f);

                    outDirection.set(x, y, z).normalize();

                } while (outDirection.dot(-dirX, -dirY, -dirZ) < 0f);

                outDirection
                        .mul(this.point.getLightSize())
                        .add(this.point.getPosition())
                        .sub(position);
            }
        }

    }

    private void randomTangentDirection(Vector3f outDirection, Random random) {
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
    }

    private void processSample(SampleState state, DirectState direct, ShadowState shadow, IndirectState indirect) {
        if (this.lightType == 1 || this.lightType == 2) {
            float falloff = state.position.distance(this.point.getPosition());
            falloff = 1f / (falloff * falloff);
            float luminance = falloff * this.point.getLuminance();
            if (luminance < this.point.getBakeCutoff()) {
                direct.output.zero();
                shadow.output = 1f;
                indirect.output.zero();
                return;
            }
        }

        if (this.scene.isDirectLightingEnabled()) {
            processDirect(state, direct);
        } else if (this.scene.fillEmptyValuesWithLightColors()) {
            switch (this.lightType) {
                case 0 -> {
                    direct.output
                            .set(this.sun.getDiffuse());
                }
                case 1, 2 -> {
                    direct.output
                            .set(this.point.getDiffuse())
                            .div(this.point.getPosition().distanceSquared(state.position) + this.scene.getDirectLightingAttenuation());
                }
            }
        }

        if (this.scene.isShadowsEnabled()) {
            processShadow(state, shadow);
        } else {
            shadow.output = 1f;
        }

        if (this.scene.isIndirectLightingEnabled() && !this.fastMode) {
            processIndirect(state, indirect);
        } else if (this.scene.fillEmptyValuesWithLightColors() || this.fastMode) {
            switch (this.lightType) {
                case 0 -> {
                    indirect.output
                            .set(this.sun.getAmbient());
                }
                case 1, 2 -> {
                    indirect.output
                            .set(this.point.getDiffuse())
                            .mul(0.03f)
                            .div(this.point.getPosition().distanceSquared(state.position) + this.scene.getDirectLightingAttenuation());
                }
            }
        }
    }

    private void calculateDirect(Vector3fc position, Vector3fc normal, Vector3f output) {
        switch (this.lightType) {
            case 0 -> {
                output
                        .set(this.sun.getDiffuse())
                        .mul(Math.max(normal.dot(this.sun.getDirectionNegated()), 0f));
            }
            case 1, 2 -> {
                float dirX = this.point.getPosition().x() - position.x();
                float dirY = this.point.getPosition().y() - position.y();
                float dirZ = this.point.getPosition().z() - position.z();
                float length = (float) Math.sqrt((dirX * dirX) + (dirY * dirY) + (dirZ * dirZ));
                float invlength = 1f / length;
                dirX *= invlength;
                dirY *= invlength;
                dirZ *= invlength;
                float intensity = 1f;
                if (this.lightType == 2) {
                    float theta = this.spot.getDirection().dot(-dirX, -dirY, -dirZ);
                    float epsilon = this.spot.getInnerCutoff() - this.spot.getOuterCutoff();
                    intensity = Math.min(Math.max((theta - this.spot.getOuterCutoff()) / epsilon, 0f), 1f);
                }
                output
                        .set(this.point.getDiffuse())
                        .mul(Math.max(normal.dot(dirX, dirY, dirZ), 0f))
                        .div((length * length) + this.scene.getDirectLightingAttenuation())
                        .mul(intensity);
            }
        }
    }

    private void processDirect(SampleState state, DirectState direct) {
        Vector3fc normal = state.normal;
        if (!normal.isFinite()) {
            normal = state.triangleNormal;
        }
        calculateDirect(state.position, normal, direct.output);
    }

    private void processShadow(SampleState state, ShadowState shadow) {
        shadow.offsetOrigin
                .set(state.triangleNormal)
                .mul(this.scene.getRayOffset())
                .add(state.position);

        int rays = this.scene.getShadowRaysPerSample();
        if (this.fastMode) {
            rays = 1;
        }
        
        float shadowValue = 0f;
        for (int i = 0; i < rays; i++) {
            randomLightDirection(state.position, shadow.randomDirection, state.random);
            switch (this.lightType) {
                case 0 -> {
                    if (Geometry.fastTestRay(shadow.offsetOrigin, shadow.randomDirection, Float.POSITIVE_INFINITY, this.geometries)) {
                        shadowValue++;
                    }
                }
                case 1, 2 -> {
                    float length = shadow.randomDirection.length();
                    if (Geometry.fastTestRay(shadow.offsetOrigin, shadow.randomDirection.div(length), length, this.geometries)) {
                        shadowValue++;
                    }
                }
            }
            this.status.rays++;
        }
        shadowValue /= rays;

        shadow.output = 1f - shadowValue;
    }

    private void processIndirect(SampleState state, IndirectState indirect) {
        for (int i = 0; i < this.scene.getIndirectRaysPerSample(); i++) {
            randomTangentDirection(indirect.bounceDirection, state.random);
            state.triangleTBN.transform(indirect.bounceDirection);

            float rayOffset = this.scene.getRayOffset();
            float offsetX = state.triangleNormal.x() * rayOffset;
            float offsetY = state.triangleNormal.y() * rayOffset;
            float offsetZ = state.triangleNormal.z() * rayOffset;

            if (state.normal.isFinite()) {
                indirect.smoothNormal.set(state.normal);
            } else {
                indirect.smoothNormal.set(state.triangleNormal);
            }

            indirect.bouncePosition
                    .set(state.position)
                    .add(offsetX, offsetY, offsetZ);

            boolean foundLight = false;
            int bounceCount = 0;
            for (int j = 0; j < this.scene.getIndirectBounces(); j++) {
                if (j != 0) {
                    calculateDirect(indirect.bouncePosition, indirect.smoothNormal, indirect.lightColor);
                    if (!indirect.lightColor.equals(0f, 0f, 0f)) {
                        randomLightDirection(indirect.bouncePosition, indirect.randomLightDirection, state.random);
                        this.status.rays++;

                        switch (this.lightType) {
                            case 0 -> {
                                if (!Geometry.fastTestRay(indirect.bouncePosition, indirect.randomLightDirection, Float.POSITIVE_INFINITY, this.geometries)) {
                                    foundLight = true;
                                }
                            }
                            case 1, 2 -> {
                                float length = indirect.randomLightDirection.length();
                                if (!Geometry.fastTestRay(indirect.bouncePosition, indirect.randomLightDirection.div(length), length, this.geometries)) {
                                    foundLight = true;
                                }
                            }
                        }

                        if (foundLight) {
                            break;
                        }
                    }
                }

                this.status.rays++;
                RayResult[] results = Geometry.testRay(indirect.bouncePosition, indirect.bounceDirection, this.scene.getGeometries());
                if (results.length == 0) {
                    if (this.lightType == 0) {
                        foundLight = true;
                        indirect.lightColor.set(this.sun.getAmbient());
                    }
                    break;
                }

                RayResult closestRay = results[0];
                closestRay.weights(indirect.bounceWeights);

                float u = closestRay.lerp(indirect.bounceWeights, MeshData.UV_OFFSET + 0);
                float v = closestRay.lerp(indirect.bounceWeights, MeshData.UV_OFFSET + 1);

                float nx = closestRay.lerp(indirect.bounceWeights, MeshData.N_XYZ_OFFSET + 0);
                float ny = closestRay.lerp(indirect.bounceWeights, MeshData.N_XYZ_OFFSET + 1);
                float nz = closestRay.lerp(indirect.bounceWeights, MeshData.N_XYZ_OFFSET + 2);

                indirect.smoothNormal.set(nx, ny, nz).normalize();
                if (!indirect.smoothNormal.isFinite()) {
                    indirect.smoothNormal.set(closestRay.getTriangleNormal());
                }

                SoftwareTexture rayTexture = this.sceneTextures.get(closestRay.getGeometry().getMesh().getTextureHint());
                rayTexture.sampleNearest(u, v, indirect.bounceColor, 0);

                indirect.bounceColor[0] = (float) Math.pow(indirect.bounceColor[0], 2.2);
                indirect.bounceColor[1] = (float) Math.pow(indirect.bounceColor[1], 2.2);
                indirect.bounceColor[2] = (float) Math.pow(indirect.bounceColor[2], 2.2);

                indirect.bounceColors[bounceCount].set(indirect.bounceColor);
                bounceCount++;

                indirect.bouncePosition.set(closestRay.getTriangleNormal());
                if (!closestRay.frontFace()) {
                    indirect.bouncePosition.negate();
                }
                indirect.bouncePosition
                        .mul(this.scene.getRayOffset())
                        .add(closestRay.getHitPosition());

                indirect.bounceDirection.reflect(closestRay.getTriangleNormal());
            }

            if (foundLight) {
                if (bounceCount != 0) {
                    Vector3f first = indirect.bounceColors[0];
                    for (int j = 1; j < bounceCount; j++) {
                        first.mul(indirect.bounceColors[j]);
                    }
                    first.mul(this.scene.getIndirectLightReflectionFactor());
                    float r = Math.min(Math.max(first.x(), 0f), 1f);
                    float g = Math.min(Math.max(first.y(), 0f), 1f);
                    float b = Math.min(Math.max(first.z(), 0f), 1f);
                    indirect.lightColor.mul(r, g, b);
                }
                indirect.output.add(indirect.lightColor);
            }
        }
        indirect.output.div(this.scene.getIndirectRaysPerSample());
    }

    private void denoiseComponents() {
        if (this.fastMode) {
            return;
        }

        int numSamples = this.samplingMode.numSamples();
        final ColorBuffer indirectOutput = new ColorBuffer(this.geometryLightmapSize, numSamples);
        final GrayBuffer reversedShadowOutput = new GrayBuffer(this.geometryLightmapSize, numSamples);

        int amountOfCores = Runtime.getRuntime().availableProcessors();
        List<Future<?>> tasks = new ArrayList<>(amountOfCores);

        this.status.setProgressBarStep(this.lightmapperQuads.length);
        for (int i = 0; i < this.lightmapperQuads.length; i += amountOfCores) {
            setStatusText("[" + i + "/" + this.lightmapperQuads.length + "] Denoising");

            for (int j = 0; j < amountOfCores; j++) {
                int quad = i + j;
                if (quad >= this.lightmapperQuads.length) {
                    break;
                }
                tasks.add(this.threads.submit(() -> {
                    denoiseQuad(indirectOutput, reversedShadowOutput, quad);
                }));
            }

            for (Future<?> f : tasks) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
                this.status.stepProgressBar();
            }

            tasks.clear();
        }

        this.indirectColorBuffer = indirectOutput;
        this.reverseShadowBuffer = reversedShadowOutput;
    }

    private void denoiseQuad(ColorBuffer indirectOutput, GrayBuffer reversedShadowOutput, int i) {
        int numSamples = this.samplingMode.numSamples();

        Vector3f color = new Vector3f();

        LightmapUVs.LightmapperQuad quad = this.lightmapperQuads[i];

        int minX = clamp(quad.getX(), 0, this.geometryLightmapSize);
        int minY = clamp(quad.getY(), 0, this.geometryLightmapSize);
        int maxX = clamp(quad.getX() + quad.getWidth(), 0, this.geometryLightmapSize);
        int maxY = clamp(quad.getY() + quad.getHeight(), 0, this.geometryLightmapSize);

        final int width = maxX - minX;
        final int height = maxY - minY;
        final int xOffset = minX;
        final int yOffset = minY;
        final boolean[] sampleMap = new boolean[width * height * numSamples];
        final boolean[] boundsMap = new boolean[width * height];
        final float[] colorMap = new float[width * height * 3];
        final float[] reversedShadowMap = new float[width * height];

        if (width <= 0 || height <= 0) {
            return;
        }

        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                float r = 0f;
                float g = 0f;
                float b = 0f;
                float reversedShadow = 0f;
                int sampleCount = 0;
                for (int s = 0; s < numSamples; s++) {
                    if (!this.sampleBuffer.read(x, y, s)) {
                        continue;
                    }

                    this.indirectColorBuffer.read(color, x, y, s);
                    r += color.x();
                    g += color.y();
                    b += color.z();
                    reversedShadow += this.reverseShadowBuffer.read(x, y, s);
                    sampleCount++;

                    sampleMap[s + ((x - xOffset) * numSamples) + ((y - yOffset) * width * numSamples)] = true;
                }
                if (sampleCount != 0) {
                    float invSampleCount = 1f / sampleCount;
                    r *= invSampleCount;
                    g *= invSampleCount;
                    b *= invSampleCount;
                    reversedShadow *= invSampleCount;

                    int localX = x - xOffset;
                    int localY = y - yOffset;

                    colorMap[(localX * 3) + (localY * width * 3) + 0] = r;
                    colorMap[(localX * 3) + (localY * width * 3) + 1] = g;
                    colorMap[(localX * 3) + (localY * width * 3) + 2] = b;
                    reversedShadowMap[localX + (localY * width)] = reversedShadow;

                    boundsMap[localX + (localY * width)] = true;
                }
            }
        }

        GaussianBlur.GaussianIO indirectIO = new GaussianBlur.GaussianIO() {
            private final Vector3f ioColor = new Vector3f();

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public boolean outOfBounds(int x, int y) {
                if (x < 0 || y < 0 || x >= width || y >= height) {
                    return true;
                }
                return !boundsMap[x + (y * width)];
            }

            @Override
            public void write(int x, int y, GaussianBlur.GaussianColor color) {
                this.ioColor.set(color.r, color.g, color.b);
                for (int s = 0; s < numSamples; s++) {
                    if (sampleMap[s + (x * numSamples) + (y * width * numSamples)]) {
                        indirectOutput.write(this.ioColor, x + xOffset, y + yOffset, s);
                    }
                }
            }

            @Override
            public void read(int x, int y, GaussianBlur.GaussianColor color) {
                color.r = colorMap[(x * 3) + (y * width * 3) + 0];
                color.g = colorMap[(x * 3) + (y * width * 3) + 1];
                color.b = colorMap[(x * 3) + (y * width * 3) + 2];
            }
        };

        GaussianBlur.blur(
                indirectIO,
                51,
                this.scene.getIndirectLightingBlurArea()
        );

        GaussianBlur.GaussianIO reversedShadowIO = new GaussianBlur.GaussianIO() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public boolean outOfBounds(int x, int y) {
                if (x < 0 || y < 0 || x >= width || y >= height) {
                    return true;
                }
                return !boundsMap[x + (y * width)];
            }

            @Override
            public void write(int x, int y, GaussianBlur.GaussianColor color) {
                float shadow = (color.r + color.g + color.b) / 3f;
                for (int s = 0; s < numSamples; s++) {
                    if (sampleMap[s + (x * numSamples) + (y * width * numSamples)]) {
                        reversedShadowOutput.write(shadow, x + xOffset, y + yOffset, s);
                    }
                }
            }

            @Override
            public void read(int x, int y, GaussianBlur.GaussianColor color) {
                float shadow = reversedShadowMap[x + (y * width)];
                color.r = shadow;
                color.g = shadow;
                color.b = shadow;
            }
        };

        GaussianBlur.blur(
                reversedShadowIO,
                51,
                this.scene.getShadowBlurArea()
        );
    }

    private void combineComponents() {
        int numSamples = this.samplingMode.numSamples();

        Vector3f currentColor = new Vector3f();

        Vector3f direct = new Vector3f();
        Vector3f indirect = new Vector3f();
        Vector3f sampleAverage = new Vector3f();

        this.status.setProgressBarStep(this.geometryLightmapSize);
        for (int y = 0; y < this.geometryLightmapSize; y++) {
            setStatusText("[" + y + "/" + this.geometryLightmapSize + "] Combining Light Components");

            for (int x = 0; x < this.geometryLightmapSize; x++) {
                int processedSamples = 0;
                for (int s = 0; s < numSamples; s++) {
                    if (this.sampleBuffer.read(x, y, s)) {
                        processedSamples++;
                    }
                }

                direct.zero();
                indirect.zero();

                if (processedSamples != 0) {
                    float invProcessedSamples = 1f / processedSamples;

                    //direct
                    sampleAverage.zero();
                    for (int s = 0; s < numSamples; s++) {
                        this.directColorBuffer.read(direct, x, y, s);
                        sampleAverage.add(direct);
                    }
                    direct.set(sampleAverage.mul(invProcessedSamples));

                    //shadow
                    float reversedShadowAverage = 0f;
                    for (int s = 0; s < numSamples; s++) {
                        reversedShadowAverage += this.reverseShadowBuffer.read(x, y, s);
                    }
                    reversedShadowAverage *= invProcessedSamples;

                    direct.mul(reversedShadowAverage);

                    //indirect
                    sampleAverage.zero();
                    for (int s = 0; s < numSamples; s++) {
                        this.indirectColorBuffer.read(indirect, x, y, s);
                        sampleAverage.add(indirect);
                    }
                    indirect.set(sampleAverage.mul(invProcessedSamples));

                    direct.add(indirect);
                }

                this.lightGroupOutput.read(currentColor, x, y, 0);
                currentColor.add(direct);
                this.lightGroupOutput.write(currentColor, x, y, 0);
            }
            this.status.stepProgressBar();
        }

    }

    private void unloadLight() {
        setStatusText("Unloading Light...");
        this.status.currentProgress = 0f;

        this.currentLightIndex = 0;
        this.currentLight = null;
        this.lightType = 0;
        this.sun = null;
        this.point = null;
        this.spot = null;
        this.indirectColorBuffer = null;
        this.directColorBuffer = null;
        this.reverseShadowBuffer = null;

        this.status.currentProgress = 100f;
    }

    private void generateMargins() {
        int numberOfCores = Runtime.getRuntime().availableProcessors();
        this.status.setProgressBarStep(this.lightmapperQuads.length);

        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < this.lightmapperQuads.length; i += numberOfCores) {
            setStatusText("[" + i + "/" + this.lightmapperQuads.length + "] Generating Margins");
            for (int j = 0; j < numberOfCores; j++) {
                final int index = i + j;
                if (index < this.lightmapperQuads.length) {
                    tasks.add(this.threads.submit(() -> {
                        generateMargin(this.lightmapperQuads[index]);
                    }));
                }
            }
            for (Future<?> task : tasks) {
                try {
                    task.get();
                    this.status.stepProgressBar();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }
            tasks.clear();
        }
    }

    private void generateMargin(LightmapUVs.LightmapperQuad quad) {
        MarginAutomata.MarginAutomataIO io = new MarginAutomata.MarginAutomataIO() {
            final Vector3f vec = new Vector3f();

            @Override
            public int width() {
                return quad.getWidth();
            }

            @Override
            public int height() {
                return quad.getHeight();
            }

            @Override
            public boolean outOfBounds(int x, int y) {
                return x < 0 || y < 0 || x >= width() || y >= height();
            }

            @Override
            public boolean empty(int x, int y) {
                for (int s = 0; s < BakedLighting.this.samplingMode.numSamples(); s++) {
                    if (BakedLighting.this.sampleBuffer.read(quad.getX() + x, quad.getY() + y, s)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void read(int x, int y, MarginAutomata.MarginAutomataColor color) {
                BakedLighting.this.lightGroupOutput.read(this.vec, quad.getX() + x, quad.getY() + y, 0);
                color.r = this.vec.x();
                color.g = this.vec.y();
                color.b = this.vec.z();
            }

            @Override
            public void write(int x, int y, MarginAutomata.MarginAutomataColor color) {
                this.vec.set(color.r, color.g, color.b);
                BakedLighting.this.lightGroupOutput.write(this.vec, quad.getX() + x, quad.getY() + y, 0);
            }
        };

        MarginAutomata.generateMargin(io, 3 + LightmapUVs.DEFAULT_MARGIN);
    }

    private void outputLightmap() {
        final float[] finalOutputBuffer = new float[this.geometryLightmapSize * this.geometryLightmapSize * 3];
        final int finalGroupIndex = this.currentLightGroupIndex;
        final BakedLightingOutput finalOutput = this.output;

        Vector3f color = new Vector3f();

        this.status.setProgressBarStep(this.geometryLightmapSize);
        for (int y = 0; y < this.geometryLightmapSize; y++) {
            setStatusText("[" + y + "/" + this.geometryLightmapSize + "] Writing to Lightmap");
            for (int x = 0; x < this.geometryLightmapSize; x++) {
                this.lightGroupOutput.read(color, x, y, 0);

                finalOutputBuffer[0 + (x * 3) + (y * this.geometryLightmapSize * 3)] = color.x();
                finalOutputBuffer[1 + (x * 3) + (y * this.geometryLightmapSize * 3)] = color.y();
                finalOutputBuffer[2 + (x * 3) + (y * this.geometryLightmapSize * 3)] = color.z();
            }
            this.status.stepProgressBar();
        }

        Main.MAIN_TASKS.add(() -> {
            finalOutput.write(finalOutputBuffer, finalGroupIndex);
        });
    }

    private void unloadLightGroup() {
        setStatusText("Unloading Light Group...");
        this.status.currentProgress = 0f;

        this.currentLightGroupIndex = 0;
        this.currentLightGroup = null;
        this.currentLightGroupLights = null;
        this.lightGroupOutput = null;

        this.status.currentProgress = 100f;
    }

    private void unloadGeometry() {
        setStatusText("Unloading Geometry...");
        this.status.currentProgress = 0f;

        this.geometryIndex = 0;
        this.geometry = null;
        this.geometryLightmapSize = 0;
        this.vertices = null;
        this.indices = null;
        this.lightmapperQuads = null;
        this.lightmapMesh = null;
        this.sampleBuffer = null;
        this.trianglesBuffer = null;
        this.weightsBuffer = null;

        this.status.currentProgress = 100f;
    }

    public void bake() {
        try {
            loadTextures();
            scheduleLightmapMeshes();
            waitForLightmapMeshes();
            waitForBVHs();
            for (int i = 0; i < this.geometries.size(); i++) {
                loadGeometry(i);
                computeGeometryBuffers();
                for (int j = 0; j < this.lightGroups.size(); j++) {
                    loadLightGroup(j);
                    for (int k = 0; k < this.currentLightGroupLights.size(); k++) {
                        loadLight(k);
                        bakeLightComponents();
                        denoiseComponents();
                        combineComponents();
                        unloadLight();
                    }
                    generateMargins();
                    outputLightmap();
                    unloadLightGroup();
                }
                unloadGeometry();
            }
        } finally {
            this.threads.shutdownNow();
        }
    }

}
