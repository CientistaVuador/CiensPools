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

import cientistavuador.cienspools.resourcepack.Resource;
import cientistavuador.cienspools.util.MeshUtils;
import cientistavuador.cienspools.util.Pair;
import cientistavuador.cienspools.util.RGBA8Image;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import static org.lwjgl.assimp.Assimp.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 *
 * @author Cien
 */
public class N3DModelImporter {

    private static class MaterialTextures {

        public final String diffuse;
        public final String opacity;
        public final String metallic;
        public final String roughness;
        public final boolean roughnessIsSpecular;
        public final String ao;
        public final String height;
        public final String normal;
        public final String emissive;

        public MaterialTextures(
                String diffuse, String opacity, String metallic, String roughness,
                boolean roughnessIsSpecular, String ao, String height, String normal, String emissive
        ) {
            this.diffuse = diffuse;
            this.opacity = opacity;
            this.metallic = metallic;
            this.roughness = roughness;
            this.roughnessIsSpecular = roughnessIsSpecular;
            this.ao = ao;
            this.height = height;
            this.normal = normal;
            this.emissive = emissive;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final MaterialTextures other = (MaterialTextures) obj;
            if (this.roughnessIsSpecular != other.roughnessIsSpecular) {
                return false;
            }
            if (!Objects.equals(this.diffuse, other.diffuse)) {
                return false;
            }
            if (!Objects.equals(this.opacity, other.opacity)) {
                return false;
            }
            if (!Objects.equals(this.metallic, other.metallic)) {
                return false;
            }
            if (!Objects.equals(this.roughness, other.roughness)) {
                return false;
            }
            if (!Objects.equals(this.ao, other.ao)) {
                return false;
            }
            if (!Objects.equals(this.height, other.height)) {
                return false;
            }
            if (!Objects.equals(this.normal, other.normal)) {
                return false;
            }
            return Objects.equals(this.emissive, other.emissive);
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + Objects.hashCode(this.diffuse);
            hash = 97 * hash + Objects.hashCode(this.opacity);
            hash = 97 * hash + Objects.hashCode(this.metallic);
            hash = 97 * hash + Objects.hashCode(this.roughness);
            hash = 97 * hash + (this.roughnessIsSpecular ? 1 : 0);
            hash = 97 * hash + Objects.hashCode(this.ao);
            hash = 97 * hash + Objects.hashCode(this.height);
            hash = 97 * hash + Objects.hashCode(this.normal);
            hash = 97 * hash + Objects.hashCode(this.emissive);
            return hash;
        }

        @Override
        public String toString() {
            String[] array = {
                "D:", this.diffuse, "|",
                "O:", this.opacity, "|",
                "M:", this.metallic, "|",
                "R:", this.roughness, "|",
                "S:", Boolean.toString(this.roughnessIsSpecular), "|",
                "A:", this.ao, "|",
                "H:", this.height, "|",
                "N:", this.normal, "|",
                "E:", this.emissive
            };
            return Stream.of(array).collect(Collectors.joining());
        }

    }

    public static double DEFAULT_TICKS_PER_SECOND = 1.0;

    public static final int DEFAULT_FLAGS
            = aiProcess_CalcTangentSpace
            | aiProcess_GenSmoothNormals
            | aiProcess_Triangulate
            | aiProcess_TransformUVCoords
            | aiProcess_FindDegenerates
            | aiProcess_RemoveRedundantMaterials
            | aiProcess_ImproveCacheLocality
            | aiProcess_SplitLargeMeshes
            | aiProcess_LimitBoneWeights
            | aiProcess_FindInvalidData
            | aiProcess_FindInstances
            | aiProcess_SortByPType
            | aiProcess_EmbedTextures;

    public static final AIPropertyStore DEFAULT_PROPERTIES;

    static {
        DEFAULT_PROPERTIES = Assimp.aiCreatePropertyStore();

        Assimp.aiSetImportPropertyInteger(DEFAULT_PROPERTIES, AI_CONFIG_PP_LBW_MAX_WEIGHTS, NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS);
        Assimp.aiSetImportPropertyInteger(DEFAULT_PROPERTIES, AI_CONFIG_PP_SBBC_MAX_BONES, NMesh.MAX_AMOUNT_OF_BONES);
    }

    public static N3DModel importFromFile(String file) {
        Objects.requireNonNull(file, "File is null.");
        AIScene modelScene = Assimp.aiImportFileExWithProperties(
                file,
                DEFAULT_FLAGS,
                null,
                DEFAULT_PROPERTIES
        );
        return process(modelScene);
    }

    public static N3DModel importFromJarFile(String jarFile) throws IOException {
        Objects.requireNonNull(jarFile, "File is null.");
        try (InputStream jarStream = ClassLoader.getSystemResourceAsStream(jarFile)) {
            return importFromMemory(jarStream.readAllBytes());
        }
    }

    public static N3DModel importFromStream(InputStream stream) throws IOException {
        Objects.requireNonNull(stream, "Stream is null.");
        return importFromMemory(stream.readAllBytes());
    }

    public static N3DModel importFromMemory(byte[] memory) {
        Objects.requireNonNull(memory, "Memory is null.");
        ByteBuffer nativeMemory = MemoryUtil.memAlloc(memory.length).put(memory).flip();
        try {
            AIScene modelScene = Assimp.aiImportFileFromMemoryWithProperties(
                    nativeMemory,
                    DEFAULT_FLAGS,
                    "glb",
                    DEFAULT_PROPERTIES
            );

            return process(modelScene);
        } finally {
            MemoryUtil.memFree(nativeMemory);
        }
    }

    private static N3DModel process(AIScene modelScene) {
        String error = aiGetErrorString();
        if (modelScene == null) {
            throw new RuntimeException("Failed to import: " + error);
        }

        try {
            if ((modelScene.mFlags() & AI_SCENE_FLAGS_INCOMPLETE) != 0) {
                throw new RuntimeException("Failed to import: " + error);
            }

            AINode rootNode = modelScene.mRootNode();
            if (rootNode == null) {
                throw new RuntimeException("Failed to import: " + error);
            }

            return new N3DModelImporter(modelScene).process();
        } finally {
            aiFreeScene(modelScene);
        }
    }
    
    private final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private final AIScene scene;

    private final List<NAnimation> loadedAnimations = new ArrayList<>();
    private final Map<Integer, String> missingMeshBones = new HashMap<>();

    private final Map<String, RGBA8Image> images = new HashMap<>();
    private final Map<MaterialTextures, NTextures> textures = new HashMap<>();
    private final Map<Integer, NMaterial> materials = new HashMap<>();
    
    private final Map<Integer, List<NGeometry>> loadedGeometries = new HashMap<>();

    private N3DModelImporter(AIScene scene) {
        this.scene = scene;
    }

    private void loadAnimations() {
        PointerBuffer sceneAnimations = this.scene.mAnimations();
        if (sceneAnimations == null) {
            return;
        }

        int numberOfAnimations = this.scene.mNumAnimations();
        for (int i = 0; i < numberOfAnimations; i++) {
            AIAnimation sceneAnimation = AIAnimation.createSafe(sceneAnimations.get(i));
            if (sceneAnimation == null) {
                continue;
            }

            List<NBoneAnimation> boneAnimations = new ArrayList<>();

            String name = Resource.generateRandomId(sceneAnimation.mName().dataString());

            double tps = sceneAnimation.mTicksPerSecond();
            if (tps == 0.0) {
                tps = DEFAULT_TICKS_PER_SECOND;
            }
            tps = 1.0 / tps;

            float duration = (float) (sceneAnimation.mDuration() * tps);

            int numberOfChannels = sceneAnimation.mNumChannels();
            PointerBuffer channels = sceneAnimation.mChannels();
            if (channels != null) {
                for (int j = 0; j < numberOfChannels; j++) {
                    AINodeAnim channel = AINodeAnim.createSafe(channels.get(j));
                    if (channel != null) {
                        String boneName = channel.mNodeName().dataString();

                        AIVectorKey.Buffer positionsBuffer = channel.mPositionKeys();
                        AIQuatKey.Buffer rotationsBuffer = channel.mRotationKeys();
                        AIVectorKey.Buffer scalingsBuffer = channel.mScalingKeys();

                        int numPosition = channel.mNumPositionKeys();
                        int numRotation = channel.mNumRotationKeys();
                        int numScaling = channel.mNumScalingKeys();

                        float[] positionTimes = new float[numPosition];
                        float[] rotationTimes = new float[numRotation];
                        float[] scalingTimes = new float[numScaling];

                        float[] positions = new float[numPosition * 3];
                        float[] rotations = new float[numRotation * 4];
                        float[] scaling = new float[numScaling * 3];

                        if (positionsBuffer != null) {
                            for (int k = 0; k < numPosition; k++) {
                                AIVectorKey key = positionsBuffer.get(k);

                                positionTimes[k] = (float) (key.mTime() * tps);

                                AIVector3D pos = key.mValue();

                                positions[(k * 3) + 0] = pos.x();
                                positions[(k * 3) + 1] = pos.y();
                                positions[(k * 3) + 2] = pos.z();
                            }
                        }

                        if (rotationsBuffer != null) {
                            for (int k = 0; k < numRotation; k++) {
                                AIQuatKey key = rotationsBuffer.get(k);

                                rotationTimes[k] = (float) (key.mTime() * tps);

                                AIQuaternion rotation = key.mValue();

                                rotations[(k * 4) + 0] = rotation.x();
                                rotations[(k * 4) + 1] = rotation.y();
                                rotations[(k * 4) + 2] = rotation.z();
                                rotations[(k * 4) + 3] = rotation.w();
                            }
                        }

                        if (scalingsBuffer != null) {
                            for (int k = 0; k < numScaling; k++) {
                                AIVectorKey key = scalingsBuffer.get(k);

                                scalingTimes[k] = (float) (key.mTime() * tps);

                                AIVector3D pos = key.mValue();

                                scaling[(k * 3) + 0] = pos.x();
                                scaling[(k * 3) + 1] = pos.y();
                                scaling[(k * 3) + 2] = pos.z();
                            }
                        }

                        boneAnimations.add(new NBoneAnimation(
                                boneName,
                                positionTimes, positions,
                                rotationTimes, rotations,
                                scalingTimes, scaling
                        ));
                    }
                }
            }

            this.loadedAnimations.add(new NAnimation(name, duration, boneAnimations.toArray(NBoneAnimation[]::new)));
        }
    }

    private String buildMissingBone(Set<String> totalBones, AINode currentNode) {
        if (currentNode == null) {
            return null;
        }

        String nodeName = currentNode.mName().dataString();

        if (totalBones.contains(nodeName)) {
            return nodeName;
        }

        return buildMissingBone(totalBones, currentNode.mParent());
    }

    private void recursiveFindMissingMeshBones(Set<String> totalBones, AINode currentNode) {
        if (currentNode == null) {
            return;
        }

        int amountOfMeshes = currentNode.mNumMeshes();
        IntBuffer meshes = currentNode.mMeshes();
        if (meshes != null) {
            String missingBone = buildMissingBone(totalBones, currentNode);

            if (missingBone != null) {
                for (int i = 0; i < amountOfMeshes; i++) {
                    this.missingMeshBones.put(meshes.get(i), missingBone);
                }
            }
        }

        int amountOfChildren = currentNode.mNumChildren();
        PointerBuffer children = currentNode.mChildren();
        if (children != null) {
            for (int i = 0; i < amountOfChildren; i++) {
                recursiveFindMissingMeshBones(totalBones, AINode.createSafe(children.get(i)));
            }
        }
    }

    private void findMissingMeshBones() {
        Set<String> totalBones = new HashSet<>();

        for (NAnimation animation : this.loadedAnimations) {
            for (int i = 0; i < animation.getNumberOfBoneAnimations(); i++) {
                String boneName = animation.getBoneAnimation(i).getBoneName();
                if (!totalBones.contains(boneName)) {
                    totalBones.add(boneName);
                }
            }
        }

        recursiveFindMissingMeshBones(totalBones, this.scene.mRootNode());
    }

    private void loadImages() {
        PointerBuffer imgs = this.scene.mTextures();
        if (imgs == null) {
            return;
        }

        final List<Future<?>> futures = new ArrayList<>();

        int amountOfImages = this.scene.mNumTextures();
        for (int i = 0; i < amountOfImages; i++) {
            final int imageIndex = i;

            AITexture tex = AITexture.createSafe(imgs.get(imageIndex));
            if (tex == null) {
                continue;
            }

            final String fileName = tex.mFilename().dataString();

            futures.add(this.service.submit(() -> {
                RGBA8Image img;
                if (tex.mHeight() == 0) {
                    byte[] data = new byte[tex.mWidth()];
                    tex.pcDataCompressed().get(data);

                    img = RGBA8Image.fromPNG(data);
                } else {
                    int width = tex.mWidth();
                    int height = tex.mHeight();
                    AITexel.Buffer texels = tex.pcData();

                    img = new RGBA8Image(width, height);

                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            AITexel texel = texels.get(x + (((height - 1) - y) * width));
                            img.write(x, y, texel.r(), texel.g(), texel.b(), texel.a());
                        }
                    }
                }
                synchronized (this.images) {
                    this.images.put(fileName, img);
                    this.images.put("*" + imageIndex, img);
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private String getImageOf(AIMaterial material, int type) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString pathString = AIString.calloc(stack);

            int result = aiGetMaterialTexture(material,
                    type, 0, pathString, null, null, null, null, null, (IntBuffer) null);

            if (result != aiReturn_SUCCESS) {
                return null;
            }

            return pathString.dataString();
        }
    }

    private MaterialTextures getMaterialTextures(AIMaterial material) {
        String diffuse = getImageOf(material, aiTextureType_BASE_COLOR);
        String opacity = getImageOf(material, aiTextureType_OPACITY);

        String ao = getImageOf(material, aiTextureType_AMBIENT_OCCLUSION);
        boolean roughnessIsSpecular = false;
        String roughness = getImageOf(material, aiTextureType_DIFFUSE_ROUGHNESS);
        String metallic = getImageOf(material, aiTextureType_METALNESS);

        String height = getImageOf(material, aiTextureType_HEIGHT);
        String normal = getImageOf(material, aiTextureType_NORMALS);
        String emissive = getImageOf(material, aiTextureType_EMISSION_COLOR);

        if (height == null) {
            height = getImageOf(material, aiTextureType_DISPLACEMENT);
        }
        if (diffuse == null) {
            diffuse = getImageOf(material, aiTextureType_DIFFUSE);
        }
        if (roughness == null) {
            roughness = getImageOf(material, aiTextureType_SPECULAR);
            if (roughness != null) {
                roughnessIsSpecular = true;
            }
        }
        if (emissive == null) {
            emissive = getImageOf(material, aiTextureType_EMISSIVE);
        }

        return new MaterialTextures(
                diffuse, opacity, metallic, roughness, roughnessIsSpecular,
                ao, height, normal, emissive);
    }

    private void loadTexturesFromMaterialTextures(MaterialTextures textures) {
        synchronized (this.textures) {
            NTextures e = this.textures.get(textures);
            if (e != null) {
                return;
            }
        }
        
        RGBA8Image diffuse = this.images.get(textures.diffuse);
        RGBA8Image opacity = this.images.get(textures.opacity);
        RGBA8Image metallic = this.images.get(textures.metallic);
        RGBA8Image roughness = this.images.get(textures.roughness);
        RGBA8Image ao = this.images.get(textures.ao);
        RGBA8Image height = this.images.get(textures.height);
        RGBA8Image normal = this.images.get(textures.normal);
        RGBA8Image emissive = this.images.get(textures.emissive);

        int w = RGBA8Image.maxWidth(
                diffuse, opacity, metallic, roughness, ao, height, normal, emissive);
        int h = RGBA8Image.maxHeight(
                diffuse, opacity, metallic, roughness, ao, height, normal, emissive);

        if (w == -1 || h == -1) {
            return;
        }

        if (diffuse == null) {
            diffuse = new RGBA8Image(w, h);
            diffuse.fill(255, 255, 255, 255);
        }

        diffuse = RGBA8Image.ensureSize(diffuse, w, h);
        opacity = RGBA8Image.ensureSize(opacity, w, h);
        metallic = RGBA8Image.ensureSize(metallic, w, h);
        roughness = RGBA8Image.ensureSize(roughness, w, h);
        ao = RGBA8Image.ensureSize(ao, w, h);
        height = RGBA8Image.ensureSize(height, w, h);
        normal = RGBA8Image.ensureSize(normal, w, h);
        emissive = RGBA8Image.ensureSize(emissive, w, h);

        if (opacity != null) {
            diffuse = diffuse.copy();
            diffuse.copyChannelOf(opacity, 0, 3);
        }

        if (textures.roughnessIsSpecular) {
            roughness = roughness.copy();
            for (int y = 0; y < roughness.getHeight(); y++) {
                for (int x = 0; x < roughness.getWidth(); x++) {
                    roughness.write(x, y, 0, 255 - roughness.sample(x, y, 0));
                }
            }
        }
        
        if (metallic != null && roughness != null) {
            if (textures.metallic.equals(textures.roughness)) {
                RGBA8Image aoMetallicRoughness = roughness;
                metallic = metallic.copy();
                roughness = roughness.copy();
                ao = new RGBA8Image(w, h);
                ao.copyChannelOf(aoMetallicRoughness, 0, 0);
                roughness.copyChannelOf(aoMetallicRoughness, 1, 0);
                metallic.copyChannelOf(aoMetallicRoughness, 2, 0);
            }
        }

        if (emissive != null) {
            diffuse = diffuse.copy();
            emissive = emissive.copy();
            NTexturesImporter.bakeEmissiveIntoColor(diffuse, emissive);
        }
        
        NTextures e = NTexturesImporter.create(
                false, Resource.generateRandomId(textures.toString()),
                diffuse, normal, height,
                roughness, metallic, ao, emissive, null);
        synchronized (this.textures) {
            this.textures.put(textures, e);
        }
    }

    private void loadMaterials() {
        PointerBuffer mats = this.scene.mMaterials();
        if (mats == null) {
            return;
        }

        List<Future<?>> futures = new ArrayList<>();
        
        int amountOfMaterials = this.scene.mNumMaterials();
        for (int i = 0; i < amountOfMaterials; i++) {
            final int materialIndex = i;

            AIMaterial aiMaterial = AIMaterial.createSafe(mats.get(materialIndex));
            if (aiMaterial == null) {
                continue;
            }
            
            MaterialTextures t = getMaterialTextures(aiMaterial);
            futures.add(this.service.submit(() -> {
                loadTexturesFromMaterialTextures(t);
            }));
        }
        
        for (Future<?> f:futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        for (int i = 0; i < amountOfMaterials; i++) {
            AIMaterial aiMaterial = AIMaterial.createSafe(mats.get(i));
            if (aiMaterial == null) {
                continue;
            }
            
            String materialName = "Material "+Integer.toString(i);
            
            AIString out = AIString.create();
            int result = aiGetMaterialString(aiMaterial, AI_MATKEY_NAME, aiTextureType_NONE, 0, out);
            if (result == aiReturn_SUCCESS) {
                materialName = out.dataString();
            }
            
            MaterialTextures texs = getMaterialTextures(aiMaterial);
            NTextures materialTexture = this.textures.get(texs);
            if (materialTexture == null) {
                materialTexture = NTextures.BLANK_TEXTURE;
            }
            NMaterial material = new NMaterial(Resource.generateRandomId(materialName), materialTexture);
            
            float metallic = 0f;
            if (texs.metallic != null) {
                metallic = 1f;
            }
            float roughness = 0f;
            if (texs.roughness != null) {
                roughness = 1f;
            }
            float height = 0f;
            if (texs.height != null) {
                height = 1f;
            }
            float emissive = 0f;
            if (texs.emissive != null) {
                emissive = 1f;
            }
            
            AIColor4D colorOut = AIColor4D.create();
            
            result = aiGetMaterialColor(aiMaterial,
                    AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, colorOut);
            if (result == aiReturn_SUCCESS) {
                material.getNewColor()
                        .set(colorOut.r(), colorOut.g(), colorOut.b(), colorOut.a());
            }
            
            result = aiGetMaterialColor(aiMaterial,
                    AI_MATKEY_METALLIC_FACTOR, aiTextureType_NONE, 0, colorOut);
            if (result == aiReturn_SUCCESS) {
                metallic = colorOut.r();
            }
            
            result = aiGetMaterialColor(aiMaterial,
                    AI_MATKEY_ROUGHNESS_FACTOR, aiTextureType_NONE, 0, colorOut);
            if (result == aiReturn_SUCCESS) {
                roughness = colorOut.r();
            }
            
            result = aiGetMaterialColor(aiMaterial,
                    AI_MATKEY_BUMPSCALING, aiTextureType_NONE, 0, colorOut);
            if (result == aiReturn_SUCCESS) {
                height = colorOut.r();
            }
            
            result = aiGetMaterialColor(aiMaterial,
                    AI_MATKEY_EMISSIVE_INTENSITY, aiTextureType_NONE, 0, colorOut);
            if (result == aiReturn_SUCCESS) {
                emissive = colorOut.r();
            }
            
            material.setNewMetallic(metallic);
            material.setNewRoughness(roughness);
            material.setNewHeight(height);
            material.setNewEmissive(emissive);
            material.setNewAmbientOcclusion(1f);
            
            this.materials.put(i, material);
        }
    }
    
    private void clearImages() {
        this.images.clear();
    }

    private List<Pair<float[], String[]>> splitByMaxBones(float[] toSplit, String[] totalBones) {
        List<float[]> splitMeshes = new ArrayList<>();

        int lastSplitIndex = 0;
        Set<Integer> addedBones = new HashSet<>();

        for (int triangle = 0; triangle < toSplit.length; triangle += NMesh.VERTEX_SIZE * 3) {
            for (int vertex = 0; vertex < NMesh.VERTEX_SIZE * 3; vertex += NMesh.VERTEX_SIZE) {
                for (int boneOffset = 0; boneOffset < NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS; boneOffset++) {
                    int index = triangle + vertex + NMesh.OFFSET_BONE_IDS_XYZW + boneOffset;
                    int bone = Float.floatToRawIntBits(toSplit[index]);
                    if (!addedBones.contains(bone)) {
                        addedBones.add(bone);
                    }
                }
            }
            if (addedBones.size() > NMesh.MAX_AMOUNT_OF_BONES) {
                addedBones.clear();
                splitMeshes.add(Arrays.copyOfRange(toSplit, lastSplitIndex, triangle));
                lastSplitIndex = triangle;
                triangle -= NMesh.VERTEX_SIZE * 3;
            }
        }

        if ((toSplit.length - lastSplitIndex) != 0) {
            splitMeshes.add(Arrays.copyOfRange(toSplit, lastSplitIndex, toSplit.length));
        }

        List<Pair<float[], String[]>> outputList = new ArrayList<>();

        for (float[] splitMesh : splitMeshes) {
            List<String> meshBones = new ArrayList<>();
            Map<Integer, Integer> absoluteToRelativeMap = new HashMap<>();

            for (int triangle = 0; triangle < splitMesh.length; triangle += NMesh.VERTEX_SIZE * 3) {
                for (int vertex = 0; vertex < NMesh.VERTEX_SIZE * 3; vertex += NMesh.VERTEX_SIZE) {
                    for (int boneOffset = 0; boneOffset < NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS; boneOffset++) {
                        int index = triangle + vertex + NMesh.OFFSET_BONE_IDS_XYZW + boneOffset;

                        int bone = Float.floatToRawIntBits(splitMesh[index]);

                        if (bone < 0) {
                            continue;
                        }

                        Integer relativeBone = absoluteToRelativeMap.get(bone);
                        if (relativeBone != null) {
                            bone = relativeBone;
                        } else {
                            int relative = meshBones.size();

                            absoluteToRelativeMap.put(bone, relative);
                            meshBones.add(totalBones[bone]);
                            bone = relative;
                        }

                        splitMesh[index] = Float.intBitsToFloat(bone);
                    }
                }
            }

            outputList.add(new Pair<>(splitMesh, meshBones.toArray(String[]::new)));
        }

        return outputList;
    }

    private Pair<Integer, List<NGeometry>> loadMesh(AIMesh mesh, int meshIndex) {
        AIVector3D.Buffer positions = mesh.mVertices();
        AIVector3D.Buffer uvs = mesh.mTextureCoords(0);
        AIVector3D.Buffer normals = mesh.mNormals();
        AIVector3D.Buffer tangents = mesh.mTangents();

        int amountOfFaces = mesh.mNumFaces();
        AIFace.Buffer faces = mesh.mFaces();

        int amountOfBones = mesh.mNumBones();
        PointerBuffer bones = mesh.mBones();

        String meshName = mesh.mName().dataString();

        float[] vertices = new float[amountOfFaces * 3 * NMesh.VERTEX_SIZE];
        int verticesIndex = 0;

        List<String> meshBones = new ArrayList<>();
        Map<Integer, List<Pair<Integer, Float>>> boneVertexWeightMap = new HashMap<>();

        String missingBone = this.missingMeshBones.get(meshIndex);

        if (bones != null) {
            for (int boneIndex = 0; boneIndex < amountOfBones; boneIndex++) {
                AIBone bone = AIBone.create(bones.get(boneIndex));
                String boneName = bone.mName().dataString();

                meshBones.add(boneName);

                int numWeights = bone.mNumWeights();
                AIVertexWeight.Buffer weights = bone.mWeights();
                if (numWeights != 0 && weights != null) {
                    for (int weightIndex = 0; weightIndex < numWeights; weightIndex++) {
                        AIVertexWeight weight = weights.get(weightIndex);

                        int vertexIndex = weight.mVertexId();

                        List<Pair<Integer, Float>> weightList = boneVertexWeightMap.get(vertexIndex);
                        if (weightList == null) {
                            weightList = new ArrayList<>();
                            boneVertexWeightMap.put(vertexIndex, weightList);
                        }

                        weightList.add(new Pair<>(boneIndex, weight.mWeight()));
                    }
                }
            }
        } else if (missingBone != null) {
            meshBones.add(missingBone);
        }

        for (int faceIndex = 0; faceIndex < amountOfFaces; faceIndex++) {
            AIFace face = faces.get(faceIndex);
            if (face.mNumIndices() != 3) {
                continue;
            }
            for (int vertex = 0; vertex < 3; vertex++) {
                int index = face.mIndices().get(vertex);

                float posX = 0f;
                float posY = 0f;
                float posZ = 0f;

                if (positions != null) {
                    AIVector3D pos = positions.get(index);
                    posX = pos.x();
                    posY = pos.y();
                    posZ = pos.z();
                }

                float texX = 0f;
                float texY = 0f;

                if (uvs != null) {
                    AIVector3D uv = uvs.get(index);
                    texX = uv.x();
                    texY = uv.y();
                }

                float norX = 0f;
                float norY = 0f;
                float norZ = 0f;

                if (normals != null) {
                    AIVector3D normal = normals.get(index);
                    norX = normal.x();
                    norY = normal.y();
                    norZ = normal.z();
                }

                float tanX = 0f;
                float tanY = 0f;
                float tanZ = 0f;

                if (tangents != null) {
                    AIVector3D tangent = tangents.get(index);
                    tanX = tangent.x();
                    tanY = tangent.y();
                    tanZ = tangent.z();
                }

                vertices[verticesIndex + NMesh.OFFSET_POSITION_XYZ + 0] = posX;
                vertices[verticesIndex + NMesh.OFFSET_POSITION_XYZ + 1] = posY;
                vertices[verticesIndex + NMesh.OFFSET_POSITION_XYZ + 2] = posZ;

                vertices[verticesIndex + NMesh.OFFSET_TEXTURE_XY + 0] = texX;
                vertices[verticesIndex + NMesh.OFFSET_TEXTURE_XY + 1] = texY;

                vertices[verticesIndex + NMesh.OFFSET_NORMAL_XYZ + 0] = norX;
                vertices[verticesIndex + NMesh.OFFSET_NORMAL_XYZ + 1] = norY;
                vertices[verticesIndex + NMesh.OFFSET_NORMAL_XYZ + 2] = norZ;

                vertices[verticesIndex + NMesh.OFFSET_TANGENT_XYZ + 0] = tanX;
                vertices[verticesIndex + NMesh.OFFSET_TANGENT_XYZ + 1] = tanY;
                vertices[verticesIndex + NMesh.OFFSET_TANGENT_XYZ + 2] = tanZ;

                vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 0] = Float.intBitsToFloat(-1);
                vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 1] = Float.intBitsToFloat(-1);
                vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 2] = Float.intBitsToFloat(-1);
                vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 3] = Float.intBitsToFloat(-1);

                vertices[verticesIndex + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 0] = 1f;
                vertices[verticesIndex + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 1] = 0f;
                vertices[verticesIndex + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 2] = 0f;
                vertices[verticesIndex + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 3] = 0f;

                List<Pair<Integer, Float>> boneVertexWeightList = boneVertexWeightMap.get(index);
                if (boneVertexWeightList != null) {
                    for (int j = 0; j < NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS; j++) {
                        if (j >= boneVertexWeightList.size()) {
                            break;
                        }
                        Pair<Integer, Float> pair = boneVertexWeightList.get(j);

                        int bone = pair.getA();
                        float weight = pair.getB();

                        vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + j] = Float.intBitsToFloat(bone);
                        vertices[verticesIndex + NMesh.OFFSET_BONE_WEIGHTS_XYZW + j] = weight;
                    }
                } else if (missingBone != null) {
                    vertices[verticesIndex + NMesh.OFFSET_BONE_IDS_XYZW + 0] = Float.intBitsToFloat(0);
                    vertices[verticesIndex + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 0] = 1f;
                }

                verticesIndex += NMesh.VERTEX_SIZE;
            }
        }

        String[] bonesArray = meshBones.toArray(String[]::new);
        vertices = Arrays.copyOf(vertices, verticesIndex);

        List<Pair<float[], String[]>> splitMeshes = splitByMaxBones(vertices, bonesArray);
        List<NGeometry> outputGeometries = new ArrayList<>();

        NMaterial material = this.materials.get(mesh.mMaterialIndex());
        if (material == null) {
            material = NMaterial.NULL_MATERIAL;
        }

        for (int i = 0; i < splitMeshes.size(); i++) {
            Pair<float[], String[]> splitMesh = splitMeshes.get(i);

            float[] splitMeshVertices = splitMesh.getA();

            Pair<float[], int[]> newMesh = MeshUtils.generateIndices(splitMeshVertices, NMesh.VERTEX_SIZE);

            float[] finalVertices = newMesh.getA();
            int[] finalIndices = newMesh.getB();

            String name = meshName;
            if (splitMeshes.size() > 1) {
                name += "_" + i;
            }
            
            NMesh loadedMesh = new NMesh(
                    Resource.generateRandomId(name),
                    finalVertices, finalIndices,
                    splitMesh.getB()
            );
            loadedMesh.generateBVH();

            outputGeometries.add(new NGeometry(Resource.generateRandomId(name+" Geometry"), loadedMesh, material));
        }

        return new Pair<>(
                meshIndex,
                outputGeometries
        );
    }

    private void loadMeshes() {
        PointerBuffer meshes = this.scene.mMeshes();
        if (meshes == null) {
            return;
        }

        List<Future<Pair<Integer, List<NGeometry>>>> futureGeometries = new ArrayList<>();

        int amountOfMeshes = this.scene.mNumMeshes();
        for (int i = 0; i < amountOfMeshes; i++) {
            final int meshIndex = i;

            AIMesh mesh = AIMesh.createSafe(meshes.get(meshIndex));
            if (mesh == null) {
                continue;
            }

            if (mesh.mFaces() == null) {
                continue;
            }

            futureGeometries.add(this.service.submit(() -> loadMesh(mesh, meshIndex)));
        }

        Map<String, NMesh> loadedMeshes = new HashMap<>();

        for (Future<Pair<Integer, List<NGeometry>>> futurePair : futureGeometries) {
            try {
                Pair<Integer, List<NGeometry>> pair = futurePair.get();

                int geometryIndex = pair.getA();
                List<NGeometry> geometries = pair.getB();

                for (NGeometry geometry : geometries) {
                    NMesh mesh = geometry.getMesh();

                    String sha256 = mesh.getSha256();
                    NMesh alreadyLoaded = loadedMeshes.get(sha256);

                    if (alreadyLoaded != null) {
                        geometry = new NGeometry(geometry.getName(), alreadyLoaded, geometry.getMaterial());
                    } else {
                        loadedMeshes.put(sha256, mesh);
                    }
                }

                this.loadedGeometries.put(geometryIndex, geometries);
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void clearMaterials() {
        this.materials.clear();
    }

    private N3DModelNode recursiveNodeGeneration(AINode node) {
        AIMatrix4x4 t = node.mTransformation();

        String name = node.mName().dataString();
        Matrix4f transformation = new Matrix4f(
                t.a1(), t.b1(), t.c1(), t.d1(),
                t.a2(), t.b2(), t.c2(), t.d2(),
                t.a3(), t.b3(), t.c3(), t.d3(),
                t.a4(), t.b4(), t.c4(), t.d4()
        );

        List<NGeometry> geometries = new ArrayList<>();

        int amountOfGeometries = node.mNumMeshes();
        IntBuffer geometriesIndex = node.mMeshes();
        if (geometriesIndex != null) {
            for (int i = 0; i < amountOfGeometries; i++) {
                List<NGeometry> geometriesList = this.loadedGeometries.get(geometriesIndex.get(i));
                if (geometriesList != null) {
                    for (NGeometry geo : geometriesList) {
                        geometries.add(new NGeometry(geo.getName(), geo.getMesh(), geo.getMaterial()));
                    }
                }
            }
        }

        List<N3DModelNode> children = new ArrayList<>();

        int amountOfChildren = node.mNumChildren();
        PointerBuffer childrenBuffer = node.mChildren();
        if (childrenBuffer != null) {
            for (int i = 0; i < amountOfChildren; i++) {
                AINode child = AINode.createSafe(childrenBuffer.get(i));
                if (child != null) {
                    children.add(recursiveNodeGeneration(child));
                }
            }
        }

        return new N3DModelNode(
                name,
                transformation,
                geometries.toArray(NGeometry[]::new),
                children.toArray(N3DModelNode[]::new)
        );
    }

    private N3DModelNode generateRootNode() {
        return recursiveNodeGeneration(this.scene.mRootNode());
    }

    private N3DModel process() {
        try {
            loadAnimations();
            findMissingMeshBones();
            
            loadImages();
            loadMaterials();
            clearImages();
            loadMeshes();
            clearMaterials();
            
            N3DModelNode rootNode = generateRootNode();
            
            String name = this.scene.mName().dataString();
            if (name.isEmpty()) {
                name = rootNode.getName();
            }
            name = Resource.generateRandomId(name);
            
            N3DModel finalModel = new N3DModel(
                    name,
                    rootNode,
                    this.loadedAnimations.toArray(NAnimation[]::new)
            );

            if (finalModel.getNumberOfAnimations() > 0) {
                finalModel.generateAnimatedAabb();
            }

            return finalModel;
        } finally {
            this.service.shutdownNow();
        }
    }

}
