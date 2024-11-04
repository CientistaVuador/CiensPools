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
import cientistavuador.cienspools.resourcepack.ResourcePackWriter;
import cientistavuador.cienspools.resourcepack.ResourcePackWriter.DataEntry;
import cientistavuador.cienspools.resourcepack.ResourcePackWriter.ResourceEntry;
import cientistavuador.cienspools.resourcepack.ResourceRW;
import cientistavuador.cienspools.util.MeshUtils;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class N3DModel {
    
    public static final void writeModelResourcePack(N3DModel model, Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            Files.delete(path);
        }
        try (ResourcePackWriter w = new ResourcePackWriter(path)) {
            for (int i = 0; i < model.getNumberOfAnimations(); i++) {
                NAnimation animation = model.getAnimation(i);
                String p = w.getPathFromId("animations", animation.getName());
                if (p == null) {
                    continue;
                }
                ResourceEntry e = new ResourceEntry();
                NAnimation.RESOURCES.writeResource(animation, e, p);
                w.writeResourceEntry(e);
            }
            for (int i = 0; i < model.getNumberOfTextures(); i++) {
                NTextures texture = model.getTextures(i);
                String p = w.getPathFromId("textures", texture.getName());
                if (p == null) {
                    continue;
                }
                ResourceEntry e = new ResourceEntry();
                NTextures.RESOURCES.writeResource(texture, e, p);
                w.writeResourceEntry(e);
            }
            for (int i = 0; i < model.getNumberOfMaterials(); i++) {
                NMaterial mat = model.getMaterial(i);
                String p = w.getPathFromId("materials", mat.getName());
                if (p == null) {
                    continue;
                }
                ResourceEntry e = new ResourceEntry();
                NMaterial.RESOURCES.writeResource(mat, e, p);
                w.writeResourceEntry(e);
            }
            for (int i = 0; i < model.getNumberOfMeshes(); i++) {
                NMesh mesh = model.getMesh(i);
                String p = w.getPathFromId("meshes", mesh.getName());
                if (p == null) {
                    continue;
                }
                ResourceEntry e = new ResourceEntry();
                NMesh.RESOURCES.writeResource(mesh, e, p);
                w.writeResourceEntry(e);
            }
            for (int i = 0; i < model.getNumberOfGeometries(); i++) {
                NGeometry geo = model.getGeometry(i);
                String p = w.getPathFromId("geometries", geo.getName());
                if (p == null) {
                    continue;
                }
                ResourceEntry e = new ResourceEntry();
                NGeometry.RESOURCES.writeResource(geo, e, p);
                w.writeResourceEntry(e);
            }
            ResourceEntry e = new ResourceEntry();
            N3DModel.RESOURCES.writeResource(model, e, "model");
            w.writeResourceEntry(e);
        }
    }

    public static final ResourceRW<N3DModel> RESOURCES = new ResourceRW<N3DModel>(true) {
        public static final String ANIMATIONS_DATA_TYPE = "text/plain;name=animations";
        public static final String SCENEGRAPH_DATA_TYPE = "application/xml;name=scenegraph";

        @Override
        public String getResourceType() {
            return "model";
        }

        @Override
        public N3DModel readResource(Resource r) throws IOException {
            Vector3f min = new Vector3f();
            Vector3f max = new Vector3f();
            Vector3f animatedMin = new Vector3f();
            Vector3f animatedMax = new Vector3f();
            Map<String, String> meta = r.getMeta();
            if (!ResourceRW.readVector3f(meta, min, "min", false, null)
                    || !ResourceRW.readVector3f(meta, max, "max", false, null)) {
                min = null;
                max = null;
            }
            if (!ResourceRW.readVector3f(meta, animatedMin, "animatedMin", false, null)
                    || !ResourceRW.readVector3f(meta, animatedMax, "animatedMax", false, null)) {
                animatedMin = null;
                animatedMax = null;
            }
            NAnimation[] animations = null;
            Path animationsPath = r.getData().get(ANIMATIONS_DATA_TYPE);
            if (animationsPath != null) {
                animations = Files.readAllLines(animationsPath)
                        .stream()
                        .map(s -> NAnimation.RESOURCES.get(s))
                        .toArray(NAnimation[]::new);
            }
            N3DModelNode rootNode;
            try {
                rootNode = N3DModelNode
                        .fromXML(Files.readString(r.getData().get(SCENEGRAPH_DATA_TYPE)));
            } catch (SAXException ex) {
                throw new IOException(ex);
            }
            return new N3DModel(r.getId(), rootNode, animations, min, max, animatedMin, animatedMax);
        }

        @Override
        public void writeResource(N3DModel obj, ResourcePackWriter.ResourceEntry entry, String path) throws IOException {
            entry.setType(getResourceType());
            entry.setId(obj.getName());
            if (!path.isEmpty() && !path.endsWith("/")) {
                path += "/";
            }
            Map<String, String> meta = entry.getMeta();
            ResourceRW.writeVector3f(meta, obj.getAabbMin(), "min", false);
            ResourceRW.writeVector3f(meta, obj.getAabbMax(), "max", false);
            if (obj.isAnimatedAabbGenerated()) {
                ResourceRW.writeVector3f(meta, obj.getAnimatedAabbMin(), "animatedMin", false);
                ResourceRW.writeVector3f(meta, obj.getAnimatedAabbMax(), "animatedMax", false);
            }
            Map<String, DataEntry> data = entry.getData();
            if (obj.getNumberOfAnimations() != 0) {
                List<NAnimation> animations = new ArrayList<>();
                for (int i = 0; i < obj.getNumberOfAnimations(); i++) {
                    animations.add(obj.getAnimation(i));
                }
                String animationsIds = animations
                        .stream()
                        .map(NAnimation::getName)
                        .collect(Collectors.joining("\n"));
                data.put(ANIMATIONS_DATA_TYPE,
                        new DataEntry(path + "animations.txt",
                                new ByteArrayInputStream(
                                        animationsIds.getBytes(StandardCharsets.UTF_8))));
            }
            String asXML = N3DModelNode.toXML(obj.getRootNode());
            data.put(SCENEGRAPH_DATA_TYPE,
                    new DataEntry(path + "scenegraph.xml",
                            new ByteArrayInputStream(asXML.getBytes(StandardCharsets.UTF_8))));
        }
    };

    private final String name;
    private final N3DModelNode rootNode;
    private final NAnimation[] animations;
    private final Map<String, Integer> animationsMap = new HashMap<>();

    private final Vector3f aabbMin = new Vector3f();
    private final Vector3f aabbMax = new Vector3f();
    private final Vector3f aabbCenter = new Vector3f();

    private final N3DModelNode[] nodes;
    private final Map<String, Integer> nodesMap = new HashMap<>();

    private final String[] bones;
    private final NMesh[] meshes;
    private final NTextures[] textures;
    private final NMaterial[] materials;
    private final NGeometry[] geometries;

    private final Vector3f animatedAabbMin = new Vector3f();
    private final Vector3f animatedAabbMax = new Vector3f();
    private final Vector3f animatedAabbCenter = new Vector3f();
    private boolean animatedAabbGenerated = false;

    private final int indicesCount;
    private final int verticesCount;

    private HullCollisionShape hullCollisionShape;

    public N3DModel(
            String name,
            N3DModelNode rootNode,
            NAnimation[] animations,
            Vector3fc min,
            Vector3fc max,
            Vector3fc animatedMin,
            Vector3fc animatedMax
    ) {

        if (name == null) {
            name = "Unnamed 3D Model";
        }
        Objects.requireNonNull(rootNode, "Root Node is null");
        if (animations == null) {
            animations = new NAnimation[0];
        }

        this.name = name;
        this.rootNode = rootNode;
        this.animations = animations.clone();
        for (int i = 0; i < animations.length; i++) {
            this.animationsMap.put(animations[i].getName(), i);
        }

        int indicesCounter = 0;
        int verticesCounter = 0;

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;

        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        boolean aabbGenerated = false;

        if (min != null && max != null) {
            minX = min.x();
            minY = min.y();
            minZ = min.z();

            maxX = max.x();
            maxY = max.y();
            maxZ = max.z();

            aabbGenerated = true;
        }

        List<N3DModelNode> nodesList = new ArrayList<>();
        int globalNodeId = 0;

        List<NGeometry> geometriesList = new ArrayList<>();
        int globalGeometryId = 0;

        Set<String> boneList = new HashSet<>();
        Set<NMesh> meshList = new HashSet<>();
        Set<NTextures> texturesList = new HashSet<>();
        Set<NMaterial> materialsList = new HashSet<>();

        Queue<N3DModelNode> current = new ArrayDeque<>();
        Queue<N3DModelNode> next = new ArrayDeque<>();

        rootNode.configure(this, globalNodeId, null, 0);
        globalNodeId++;
        nodesList.add(rootNode);

        current.add(rootNode);

        do {
            Vector3f transformed = new Vector3f();

            N3DModelNode currentNode;
            while ((currentNode = current.poll()) != null) {
                currentNode.recalculateMatrices();

                Matrix4fc totalTransformation = currentNode.getToRootSpace();

                for (int geometryIndex = 0; geometryIndex < currentNode.getNumberOfGeometries(); geometryIndex++) {
                    NGeometry g = currentNode.getGeometry(geometryIndex);
                    g.configure(this, globalGeometryId, currentNode, geometryIndex);
                    globalGeometryId++;
                    geometriesList.add(g);

                    NMesh mesh = g.getMesh();
                    NMaterial material = g.getMaterial();
                    NTextures materialTextures = material.getTextures();

                    if (!meshList.contains(mesh)) {
                        meshList.add(mesh);
                    }

                    if (material != NMaterial.NULL_MATERIAL && !materialsList.contains(material)) {
                        materialsList.add(material);
                    }

                    if (materialTextures != NTextures.NULL_TEXTURE && !texturesList.contains(materialTextures)) {
                        texturesList.add(materialTextures);
                    }

                    int bonesLength = mesh.getNumberOfBones();
                    for (int i = 0; i < bonesLength; i++) {
                        String boneName = mesh.getBone(i);
                        if (!boneList.contains(boneName)) {
                            boneList.add(boneName);
                        }
                    }

                    float[] vertices = mesh.getVertices();
                    int numVertices = vertices.length / NMesh.VERTEX_SIZE;

                    if (!aabbGenerated) {
                        for (int i = 0; i < numVertices; i++) {
                            transformed.set(
                                    vertices[(i * NMesh.VERTEX_SIZE) + NMesh.OFFSET_POSITION_XYZ + 0],
                                    vertices[(i * NMesh.VERTEX_SIZE) + NMesh.OFFSET_POSITION_XYZ + 1],
                                    vertices[(i * NMesh.VERTEX_SIZE) + NMesh.OFFSET_POSITION_XYZ + 2]
                            );

                            totalTransformation.transformProject(transformed);

                            minX = Math.min(minX, transformed.x());
                            minY = Math.min(minY, transformed.y());
                            minZ = Math.min(minZ, transformed.z());

                            maxX = Math.max(maxX, transformed.x());
                            maxY = Math.max(maxY, transformed.y());
                            maxZ = Math.max(maxZ, transformed.z());
                        }
                    }

                    verticesCounter += mesh.getVertices().length / NMesh.VERTEX_SIZE;
                    indicesCounter += mesh.getIndices().length;
                }

                for (int i = 0; i < currentNode.getNumberOfChildren(); i++) {
                    N3DModelNode child = currentNode.getChild(i);
                    child.configure(this, globalNodeId, currentNode, i);
                    globalNodeId++;
                    nodesList.add(child);

                    next.add(child);
                }
            }

            Queue<N3DModelNode> a = current;
            Queue<N3DModelNode> b = next;
            current = b;
            next = a;
        } while (!current.isEmpty());

        for (NAnimation animation : this.animations) {
            for (int i = 0; i < animation.getNumberOfBoneAnimations(); i++) {
                String boneName = animation.getBoneAnimation(i).getBoneName();
                if (!boneList.contains(boneName)) {
                    boneList.add(boneName);
                }
            }
        }

        this.aabbMin.set(minX, minY, minZ);
        this.aabbMax.set(maxX, maxY, maxZ);
        this.aabbCenter.set(this.aabbMin).add(this.aabbMax).mul(0.5f);

        if (animatedMin == null || animatedMax == null) {
            this.animatedAabbMin.set(this.aabbMin);
            this.animatedAabbMax.set(this.aabbMax);
            this.animatedAabbCenter.set(this.aabbCenter);
            this.animatedAabbGenerated = false;
        } else {
            this.animatedAabbMin.set(animatedMin);
            this.animatedAabbMax.set(animatedMax);
            this.animatedAabbCenter.set(this.animatedAabbMin).add(this.animatedAabbMax).mul(0.5f);
            this.animatedAabbGenerated = true;
        }

        this.nodes = nodesList.toArray(N3DModelNode[]::new);
        for (int i = 0; i < this.nodes.length; i++) {
            this.nodesMap.put(this.nodes[i].getName(), i);
        }
        this.bones = boneList.toArray(String[]::new);
        this.meshes = meshList.toArray(NMesh[]::new);
        this.textures = texturesList.toArray(NTextures[]::new);
        this.materials = materialsList.toArray(NMaterial[]::new);
        this.geometries = geometriesList.toArray(NGeometry[]::new);

        this.verticesCount = verticesCounter;
        this.indicesCount = indicesCounter;
    }

    public N3DModel(String name, N3DModelNode rootNode, NAnimation[] animations) {
        this(name, rootNode, animations, null, null, null, null);
    }

    public N3DModel(String name, N3DModelNode rootNode) {
        this(name, rootNode, null);
    }

    public String getName() {
        return name;
    }

    public N3DModelNode getRootNode() {
        return rootNode;
    }

    public int getNumberOfAnimations() {
        return this.animations.length;
    }

    public NAnimation getAnimation(int index) {
        return this.animations[index];
    }

    public NAnimation getAnimation(String name) {
        Integer index = this.animationsMap.get(name);
        if (index == null) {
            return null;
        }
        return getAnimation(index);
    }

    public Vector3fc getAabbMin() {
        return aabbMin;
    }

    public Vector3fc getAabbMax() {
        return aabbMax;
    }

    public Vector3fc getAabbCenter() {
        return aabbCenter;
    }

    public int getNumberOfNodes() {
        return this.nodes.length;
    }

    public N3DModelNode getNode(int index) {
        return this.nodes[index];
    }

    public N3DModelNode getNode(String name) {
        Integer index = this.nodesMap.get(name);
        if (index == null) {
            return null;
        }
        return getNode(index);
    }

    public int getNumberOfBones() {
        return this.bones.length;
    }

    public String getBone(int index) {
        return this.bones[index];
    }

    public int getNumberOfMeshes() {
        return this.meshes.length;
    }

    public NMesh getMesh(int index) {
        return this.meshes[index];
    }

    public int getNumberOfTextures() {
        return this.textures.length;
    }

    public NTextures getTextures(int index) {
        return this.textures[index];
    }

    public int getNumberOfMaterials() {
        return this.materials.length;
    }

    public NMaterial getMaterial(int index) {
        return this.materials[index];
    }

    public int getNumberOfGeometries() {
        return this.geometries.length;
    }

    public NGeometry getGeometry(int index) {
        return this.geometries[index];
    }

    public void generateAnimatedAabb() {
        if (this.animatedAabbGenerated) {
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;

        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (N3DModelNode node : this.nodes) {
            for (int i = 0; i < node.getNumberOfGeometries(); i++) {
                NGeometry geometry = node.getGeometry(i);
                geometry.generateAnimatedAabb(this);

                Vector3fc geoMin = geometry.getAnimatedAabbMin();
                Vector3fc geoMax = geometry.getAnimatedAabbMax();

                minX = Math.min(minX, geoMin.x());
                minY = Math.min(minY, geoMin.y());
                minZ = Math.min(minZ, geoMin.z());

                maxX = Math.max(maxX, geoMax.x());
                maxY = Math.max(maxY, geoMax.y());
                maxZ = Math.max(maxZ, geoMax.z());
            }
        }

        this.animatedAabbMin.set(minX, minY, minZ);
        this.animatedAabbMax.set(maxX, maxY, maxZ);
        this.animatedAabbCenter.set(
                (minX * 0.5f) + (maxX * 0.5f),
                (minY * 0.5f) + (maxY * 0.5f),
                (minZ * 0.5f) + (maxZ * 0.5f)
        );

        this.animatedAabbGenerated = true;
    }

    public Vector3fc getAnimatedAabbMin() {
        return animatedAabbMin;
    }

    public Vector3fc getAnimatedAabbMax() {
        return animatedAabbMax;
    }

    public Vector3fc getAnimatedAabbCenter() {
        return animatedAabbCenter;
    }

    public boolean isAnimatedAabbGenerated() {
        return animatedAabbGenerated;
    }

    public int getIndicesCount() {
        return indicesCount;
    }

    public int getVerticesCount() {
        return verticesCount;
    }

    public void load() {
        for (NTextures t : this.textures) {
            t.textures();
        }

        for (NMesh m : this.meshes) {
            m.getVBO();
            m.getEBO();
            m.getVAO();
        }
    }

    public void freeEverything() {
        for (NTextures t : this.textures) {
            t.manualFree();
        }

        for (NMesh m : this.meshes) {
            m.manualFree();
        }
    }

    public HullCollisionShape getHullCollisionShape() {
        if (this.hullCollisionShape != null) {
            return this.hullCollisionShape;
        }
        float[][] vertices = new float[this.geometries.length][];
        int[][] indices = new int[this.geometries.length][];
        Matrix4fc[] matrices = new Matrix4fc[this.geometries.length];
        for (int i = 0; i < this.geometries.length; i++) {
            NGeometry geo = this.geometries[i];
            vertices[i] = geo.getMesh().getVertices();
            indices[i] = geo.getMesh().getIndices();
            matrices[i] = geo.getParent().getToRootSpace();
        }
        this.hullCollisionShape = MeshUtils.createHullCollisionShapeFromMeshes(
                vertices, indices, matrices, NMesh.VERTEX_SIZE, NMesh.OFFSET_POSITION_XYZ);
        return this.hullCollisionShape;
    }

}
