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

import cientistavuador.cienspools.util.bakedlighting.AmbientCube;
import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.Pipeline;
import cientistavuador.cienspools.camera.Camera;
import cientistavuador.cienspools.newrendering.NLight.NDirectionalLight;
import cientistavuador.cienspools.newrendering.NLight.NPointLight;
import cientistavuador.cienspools.newrendering.NLight.NSpotLight;
import cientistavuador.cienspools.util.BetterUniformSetter;
import cientistavuador.cienspools.util.GPUOcclusion;
import cientistavuador.cienspools.water.Water;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class N3DObjectRenderer {

    private static final NMaterial REFLECTION_DEBUG;

    static {
        REFLECTION_DEBUG = new NMaterial(
                "bb318e2a-8d14-4915-bec4-bf5c7d99abde",
                NTextures.BLANK_TEXTURE);
        REFLECTION_DEBUG.setMetallic(1f);
        REFLECTION_DEBUG.setRoughness(0f);
    }

    private static class WrappedLight {

        int type;
        NLight light;
        Vector3f position;
        Vector3f color;
        float ambient;

        WrappedLight(int type, NLight light, Vector3f position, Vector3f color, float ambient) {
            this.type = type;
            this.light = light;
            this.position = position;
            this.color = color;
            this.ambient = ambient;
        }
    }

    private static class ToRender {

        public final N3DObject obj;
        public final Matrix4f transformation;
        public final Matrix4f model;
        public final float distanceSquared;
        public final NGeometry geometry;
        public final NCubemap[] cubemaps;
        public final WrappedLight[] lights;

        public ToRender(
                N3DObject obj,
                Matrix4f transformation,
                Matrix4f model,
                float distanceSquared,
                NGeometry geometry,
                NCubemap[] cubemaps,
                WrappedLight[] lights
        ) {
            this.obj = obj;
            this.transformation = transformation;
            this.model = model;
            this.distanceSquared = distanceSquared;
            this.geometry = geometry;
            this.cubemaps = cubemaps;
            this.lights = lights;
        }
    }

    private static final Matrix4fc IDENTITY = new Matrix4f();

    private boolean parallaxEnabled = true;
    private boolean reflectionsEnabled = true;
    private boolean reflectionsDebugEnabled = false;

    private int occlusionQueryMinimumVertices = 1024;
    private int occlusionQueryMinimumSamples = 8;

    private Camera camera = null;
    private NCubemaps cubemaps = null;
    private final List<N3DObject> objects = new ArrayList<>();
    private final List<NLight> lights = new ArrayList<>();

    private final List<ToRender> opaqueList = new ArrayList<>();
    private final List<ToRender> testedList = new ArrayList<>();
    private final List<ToRender> blendList = new ArrayList<>();
    private NCubemap skyboxCubemap = NCubemap.NULL_CUBEMAP;

    public N3DObjectRenderer() {

    }

    public N3DObjectRenderer(N3DObjectRenderer toCopy) {
        if (toCopy == null) {
            return;
        }
        this.parallaxEnabled = toCopy.isParallaxEnabled();
        this.reflectionsEnabled = toCopy.isReflectionsEnabled();
        this.reflectionsDebugEnabled = toCopy.isReflectionsDebugEnabled();
        this.occlusionQueryMinimumVertices = toCopy.getOcclusionQueryMinimumVertices();
        this.occlusionQueryMinimumSamples = toCopy.getOcclusionQueryMinimumSamples();
        this.camera = toCopy.getCamera();
        this.cubemaps = toCopy.getCubemaps();
        this.objects.addAll(toCopy.getObjects());
        this.lights.addAll(toCopy.getLights());
    }

    public boolean isParallaxEnabled() {
        return parallaxEnabled;
    }

    public void setParallaxEnabled(boolean parallaxEnabled) {
        this.parallaxEnabled = parallaxEnabled;
    }

    public boolean isReflectionsEnabled() {
        return reflectionsEnabled;
    }

    public void setReflectionsEnabled(boolean reflectionsEnabled) {
        this.reflectionsEnabled = reflectionsEnabled;
    }

    public boolean isReflectionsDebugEnabled() {
        return reflectionsDebugEnabled;
    }

    public void setReflectionsDebugEnabled(boolean reflectionsDebugEnabled) {
        this.reflectionsDebugEnabled = reflectionsDebugEnabled;
    }

    public int getOcclusionQueryMinimumVertices() {
        return occlusionQueryMinimumVertices;
    }

    public void setOcclusionQueryMinimumVertices(int occlusionQueryMinimumVertices) {
        this.occlusionQueryMinimumVertices = occlusionQueryMinimumVertices;
    }

    public int getOcclusionQueryMinimumSamples() {
        return occlusionQueryMinimumSamples;
    }

    public void setOcclusionQueryMinimumSamples(int occlusionQueryMinimumSamples) {
        this.occlusionQueryMinimumSamples = occlusionQueryMinimumSamples;
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public NCubemaps getCubemaps() {
        return cubemaps;
    }

    public void setCubemaps(NCubemaps cubemaps) {
        this.cubemaps = cubemaps;
    }

    public List<N3DObject> getObjects() {
        return objects;
    }

    public List<NLight> getLights() {
        return lights;
    }

    private List<N3DObject> filterOccluded(List<N3DObject> objects) {
        List<N3DObject> notOccludedObjects = new ArrayList<>();

        Matrix4f projectionView = new Matrix4f(getCamera().getProjection())
                .mul(getCamera().getView());

        Vector3f transformedMin = new Vector3f();
        Vector3f transformedMax = new Vector3f();

        Matrix4f modelMatrix = new Matrix4f();

        for (N3DObject obj : objects) {
            obj.calculateModelMatrix(modelMatrix, getCamera());

            if (obj.getAnimator() != null) {
                obj.transformAnimatedAabb(modelMatrix, transformedMin, transformedMax);
            } else {
                obj.transformAabb(modelMatrix, transformedMin, transformedMax);
            }

            if (!transformedMin.isFinite() || !transformedMax.isFinite()) {
                continue;
            }

            if (!projectionView.testAab(
                    transformedMin.x(), transformedMin.y(), transformedMin.z(),
                    transformedMax.x(), transformedMax.y(), transformedMax.z()
            )) {
                continue;
            }

            boolean occluded = false;
            if (obj.getN3DModel().getVerticesCount() >= getOcclusionQueryMinimumVertices()) {
                occlusionQuery:
                {
                    float x = (transformedMin.x() + transformedMax.x()) * 0.5f;
                    float y = (transformedMin.y() + transformedMax.y()) * 0.5f;
                    float z = (transformedMin.z() + transformedMax.z()) * 0.5f;
                    float width = transformedMax.x() - transformedMin.x();
                    float height = transformedMax.y() - transformedMin.y();
                    float depth = transformedMax.z() - transformedMin.z();

                    if (GPUOcclusion.testCamera(
                            0f, 0f, 0f, getCamera().getNearPlane() * 1.05f,
                            x, y, z,
                            width, height, depth
                    )) {
                        break occlusionQuery;
                    }

                    if (obj.hasQueryObject()) {
                        int queryObject = obj.getQueryObject();
                        int samplesPassed = glGetQueryObjecti(queryObject, GL_QUERY_RESULT);
                        if (samplesPassed <= getOcclusionQueryMinimumSamples()) {
                            occluded = true;
                        }
                    }
                    if (!obj.hasQueryObject()) {
                        obj.createQueryObject();
                    }
                    GPUOcclusion.occlusionQuery(
                            getCamera().getProjection(), getCamera().getView(),
                            x, y, z, width, height, depth,
                            obj.getQueryObject()
                    );
                }
            }

            if (!occluded) {
                double absCenterX = getCamera().getPosition().x()
                        + ((transformedMin.x() + transformedMax.x()) * 0.5f);

                double absCenterY = getCamera().getPosition().y()
                        + ((transformedMin.y() + transformedMax.y()) * 0.5f);

                double absCenterZ = getCamera().getPosition().z()
                        + ((transformedMin.z() + transformedMax.z()) * 0.5f);

                obj.updateAmbientCube(absCenterX, absCenterY, absCenterZ);

                notOccludedObjects.add(obj);
            }
        }

        return notOccludedObjects;
    }

    private WrappedLight wrapLight(
            NLight light, Vector3f color, float ambientFactor
    ) {
        int lightType = NProgram.NULL_LIGHT_TYPE;

        Vector3dc position = null;

        if (light instanceof NLight.NDirectionalLight e) {
            lightType = NProgram.DIRECTIONAL_LIGHT_TYPE;
        } else if (light instanceof NLight.NPointLight e) {
            lightType = NProgram.POINT_LIGHT_TYPE;
            position = e.getPosition();
        } else if (light instanceof NLight.NSpotLight e) {
            lightType = NProgram.SPOT_LIGHT_TYPE;
            position = e.getPosition();
        }

        float pX = 0f;
        float pY = 0f;
        float pZ = 0f;

        if (position != null) {
            pX = (float) (position.x() - getCamera().getPosition().x());
            pY = (float) (position.y() - getCamera().getPosition().y());
            pZ = (float) (position.z() - getCamera().getPosition().z());
        }

        return new WrappedLight(lightType, light, new Vector3f(pX, pY, pZ), color, ambientFactor);
    }

    public void prepare() {
        NCubemaps cbmaps = getCubemaps();
        if (cbmaps == null) {
            cbmaps = NCubemaps.NULL_CUBEMAPS;
        }

        List<N3DObject> objectsToRender = filterOccluded(getObjects());

        Matrix4f projectionView = new Matrix4f(getCamera().getProjection()).mul(getCamera().getView());

        Vector3f transformedMin = new Vector3f();
        Vector3f transformedMax = new Vector3f();

        Vector3d lightDirection = new Vector3d();
        Vector3f shadowColor = new Vector3f();

        List<ToRender> toRenderList = new ArrayList<>();

        {
            for (N3DObject obj : objectsToRender) {
                Matrix4f modelMatrix = new Matrix4f();
                N3DModel n3dmodel = obj.getN3DModel();
                NAnimator animator = obj.getAnimator();

                obj.calculateModelMatrix(modelMatrix, getCamera());

                for (int nodeIndex = 0; nodeIndex < n3dmodel.getNumberOfNodes(); nodeIndex++) {
                    N3DModelNode n = n3dmodel.getNode(nodeIndex);

                    Matrix4f transformation = new Matrix4f(modelMatrix)
                            .mul(n.getToRootSpace());

                    for (int i = 0; i < n.getNumberOfGeometries(); i++) {
                        NGeometry geometry = n.getGeometry(i);
                        if (animator != null) {
                            modelMatrix.transformAab(
                                    geometry.getAnimatedAabbMin(), geometry.getAnimatedAabbMax(),
                                    transformedMin, transformedMax
                            );
                        } else {
                            transformation.transformAab(
                                    geometry.getMesh().getAabbMin(), geometry.getMesh().getAabbMax(),
                                    transformedMin, transformedMax
                            );
                        }

                        if (!transformedMin.isFinite() || !transformedMax.isFinite()) {
                            continue;
                        }

                        if (!projectionView.testAab(
                                transformedMin.x(), transformedMin.y(), transformedMin.z(),
                                transformedMax.x(), transformedMax.y(), transformedMax.z()
                        )) {
                            continue;
                        }

                        List<NCubemap> toRenderCubemaps;
                        {
                            toRenderCubemaps = cbmaps
                                    .getCubemapsBVH()
                                    .testRelativeAab(
                                            getCamera().getPosition(), transformedMin, transformedMax);

                            List<NCubemap> toRenderCubemapsFiltered = new ArrayList<>();
                            for (NCubemap c : toRenderCubemaps) {
                                if (c.getIntensity() == 0f) {
                                    continue;
                                }

                                float cubemapMinX = (float) (c.getCubemapBox().getMin().x() - getCamera().getPosition().x());
                                float cubemapMinY = (float) (c.getCubemapBox().getMin().y() - getCamera().getPosition().y());
                                float cubemapMinZ = (float) (c.getCubemapBox().getMin().z() - getCamera().getPosition().z());

                                float cubemapMaxX = (float) (c.getCubemapBox().getMax().x() - getCamera().getPosition().x());
                                float cubemapMaxY = (float) (c.getCubemapBox().getMax().y() - getCamera().getPosition().y());
                                float cubemapMaxZ = (float) (c.getCubemapBox().getMax().z() - getCamera().getPosition().z());

                                if (projectionView.testAab(
                                        cubemapMinX, cubemapMinY, cubemapMinZ,
                                        cubemapMaxX, cubemapMaxY, cubemapMaxZ
                                )) {
                                    toRenderCubemapsFiltered.add(c);
                                }
                            }

                            toRenderCubemaps = toRenderCubemapsFiltered;
                        }

                        float centerX = (transformedMin.x() + transformedMax.x()) * 0.5f;
                        float centerY = (transformedMin.y() + transformedMax.y()) * 0.5f;
                        float centerZ = (transformedMin.z() + transformedMax.z()) * 0.5f;

                        double absCenterX = getCamera().getPosition().x() + centerX;
                        double absCenterY = getCamera().getPosition().y() + centerY;
                        double absCenterZ = getCamera().getPosition().z() + centerZ;

                        toRenderCubemaps.sort((o1, o2) -> {
                            double o1Dist = Math.min(
                                    o1.getCubemapBox().getCubemapPosition()
                                            .distanceSquared(getCamera().getPosition()),
                                    o1.getCubemapBox().getCubemapPosition()
                                            .distanceSquared(absCenterX, absCenterY, absCenterZ)
                            );

                            double o2Dist = Math.min(
                                    o2.getCubemapBox().getCubemapPosition()
                                            .distanceSquared(getCamera().getPosition()),
                                    o2.getCubemapBox().getCubemapPosition()
                                            .distanceSquared(absCenterX, absCenterY, absCenterZ)
                            );

                            return Double.compare(
                                    o1Dist,
                                    o2Dist
                            );
                        });

                        NCubemap[] finalToRenderCubemaps = Arrays.
                                copyOf(toRenderCubemaps.toArray(NCubemap[]::new),
                                        NProgram.MAX_AMOUNT_OF_CUBEMAPS);

                        List<NLight> lightsList = new ArrayList<>();
                        lightsList.addAll(getLights());
                        List<WrappedLight> toRenderLights = new ArrayList<>();
                        lightsList.sort((o1, o2) -> {
                            double disto1 = 0.0;
                            double disto2 = 0.0;

                            if (o1 instanceof NLight.NSpotLight e) {
                                disto1 = Math.min(
                                        e.getPosition()
                                                .distanceSquared(getCamera().getPosition()),
                                        e.getPosition()
                                                .distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            } else if (o1 instanceof NLight.NPointLight e) {
                                disto1 = Math.min(
                                        e.getPosition()
                                                .distanceSquared(getCamera().getPosition()),
                                        e.getPosition()
                                                .distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            }

                            if (o2 instanceof NLight.NSpotLight e) {
                                disto2 = Math.min(
                                        e.getPosition()
                                                .distanceSquared(getCamera().getPosition()),
                                        e.getPosition()
                                                .distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            } else if (o2 instanceof NLight.NPointLight e) {
                                disto2 = Math.min(
                                        e.getPosition()
                                                .distanceSquared(getCamera().getPosition()),
                                        e.getPosition()
                                                .distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            }

                            return Double.compare(disto1, disto2);
                        });
                        for (NLight light : lightsList) {
                            if (!light.isDynamic() && obj.getLightmaps() != NLightmaps.NULL_LIGHTMAPS) {
                                continue;
                            }
                            float ambientFactor = 1f;
                            if (!light.isDynamic() && obj.getMap() != null) {
                                ambientFactor = 0f;
                            }
                            float r = 1f;
                            float g = 1f;
                            float b = 1f;
                            if (obj.getMap() != null
                                    && obj.getLightmaps() == NLightmaps.NULL_LIGHTMAPS
                                    && !light.isDynamic()) {
                                Vector3d lightPosition = null;
                                double length = Double.POSITIVE_INFINITY;

                                lightDirection.set(0f, 1f, 0f);
                                if (light instanceof NLight.NDirectionalLight d) {
                                    lightDirection.set(d.getDirection()).normalize().negate();
                                } else if (light instanceof NLight.NPointLight p) {
                                    lightPosition = p.getPosition();
                                } else if (light instanceof NLight.NSpotLight p) {
                                    lightPosition = p.getPosition();
                                }

                                if (lightPosition != null) {
                                    lightDirection.set(lightPosition)
                                            .sub(absCenterX, absCenterY, absCenterZ);
                                    length = lightDirection.length();
                                    lightDirection.div(length);
                                    length -= light.getSize();

                                    if (length < 0.0) {
                                        lightDirection.negate();
                                        length = -length;
                                    }
                                }

                                obj.getMap().testShadow(
                                        absCenterX, absCenterY, absCenterZ,
                                        (float) lightDirection.x(),
                                        (float) lightDirection.y(),
                                        (float) lightDirection.z(),
                                        length,
                                        shadowColor
                                );

                                r = shadowColor.x();
                                g = shadowColor.y();
                                b = shadowColor.z();
                            }
                            if (r != 0f || g != 0f || b != 0f || ambientFactor != 0f) {
                                toRenderLights.add(wrapLight(light,
                                        new Vector3f(r, g, b), ambientFactor));
                            }
                        }
                        WrappedLight[] finalLights = toRenderLights.toArray(WrappedLight[]::new);
                        finalLights = Arrays.copyOf(finalLights, NProgram.MAX_AMOUNT_OF_LIGHTS);

                        float distanceSquared = (centerX * centerX)
                                + (centerY * centerY)
                                + (centerZ * centerZ);

                        toRenderList.add(new ToRender(
                                obj,
                                transformation, modelMatrix,
                                distanceSquared,
                                geometry,
                                finalToRenderCubemaps,
                                finalLights
                        ));
                    }
                }
            }
        }

        this.opaqueList.clear();
        this.testedList.clear();
        this.blendList.clear();

        for (ToRender toRender : toRenderList) {
            if (toRender.geometry.getMaterial().isInvisible()) {
                continue;
            }

            NBlendingMode mode = toRender.geometry.getMaterial().getBlendingMode();

            switch (mode) {
                case OPAQUE ->
                    this.opaqueList.add(toRender);
                case ALPHA_TESTING ->
                    this.testedList.add(toRender);
                case ALPHA_BLENDING ->
                    this.blendList.add(toRender);
            }
        }

        Comparator<ToRender> distanceComparator = (o1, o2) -> {
            return Float.compare(o1.distanceSquared, o2.distanceSquared);
        };

        this.opaqueList.sort(distanceComparator);
        this.testedList.sort(distanceComparator);
        this.blendList.sort(distanceComparator.reversed());
        this.skyboxCubemap = cbmaps.getSkybox();
    }

    public void renderOpaque() {
        if (!this.opaqueList.isEmpty()) {
            renderVariant(NProgram.VARIANT_OPAQUE, this.opaqueList);
        }
    }

    public void renderAlphaTested() {
        if (!this.testedList.isEmpty()) {
            renderVariant(NProgram.VARIANT_ALPHA_TESTING, this.testedList);
        }
        GPUOcclusion.executeQueries();
    }

    public void renderSkybox() {
        renderSkybox(this.skyboxCubemap);
    }

    public void renderAlphaBlending() {
        if (!this.blendList.isEmpty()) {
            Pipeline.copyColorBufferToOpaque();
            
            renderVariant(NProgram.VARIANT_ALPHA_BLENDING, this.blendList);
        }
    }
    
    public void render() {
        prepare();
        renderOpaque();
        renderAlphaTested();
        renderSkybox();
        renderAlphaBlending();
    }

    private void renderSkybox(
            NCubemap skybox
    ) {
        BetterUniformSetter program = NSkybox.SKYBOX_PROGRAM;

        glUseProgram(program.getProgram());

        BetterUniformSetter.uniformMatrix4fv(program.locationOf(NSkybox.UNIFORM_PROJECTION),
                getCamera().getProjection()
        );
        BetterUniformSetter.uniformMatrix4fv(program.locationOf(NSkybox.UNIFORM_VIEW),
                getCamera().getView()
        );

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_CUBE_MAP, skybox.cubemap());
        glUniform1i(program.locationOf(NSkybox.UNIFORM_SKYBOX), 0);

        glBindVertexArray(NSkybox.VAO);
        glDrawElements(GL_TRIANGLES, NSkybox.AMOUNT_OF_INDICES, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        glUseProgram(0);

        Main.NUMBER_OF_DRAWCALLS++;
        Main.NUMBER_OF_VERTICES += NSkybox.AMOUNT_OF_INDICES;
    }

    private void renderVariant(
            BetterUniformSetter variant,
            List<ToRender> toRender
    ) {
        NLightmaps.NULL_LIGHTMAPS.lightmaps();
        NCubemap.NULL_CUBEMAP.cubemap();
        NTextures.ERROR_TEXTURE.textures();

        for (ToRender t : toRender) {
            NTextures textures = t.geometry.getMaterial().getTextures();

            textures.textures();

            for (NCubemap e : t.cubemaps) {
                if (e == null) {
                    continue;
                }
                e.cubemap();
            }

            t.obj.getLightmaps().lightmaps();
            t.geometry.getMesh().getVAO();
        }

        glUseProgram(variant.getProgram());

        variant.uniformMatrix4fv(NProgram.UNIFORM_BONE_MATRIX(-1), IDENTITY);

        variant
                .uniformMatrix4fv(NProgram.UNIFORM_PROJECTION,
                        getCamera().getProjection())
                .uniformMatrix4fv(NProgram.UNIFORM_VIEW,
                        getCamera().getView())
                .uniform1i(NProgram.UNIFORM_ENABLE_PARALLAX_MAPPING,
                        (isParallaxEnabled() ? 1 : 0))
                .uniform1i(NProgram.UNIFORM_ENABLE_REFLECTIONS,
                        (isReflectionsEnabled() ? 1 : 0))
                .uniform1f(NProgram.UNIFORM_WATER_COUNTER,
                        Water.WATER_COUNTER)
                .uniform2f(NProgram.UNIFORM_SCREEN_SIZE, Main.WIDTH, Main.HEIGHT)
                .uniform1i(NProgram.UNIFORM_ENABLE_OPAQUE_TEXTURE, 0)
                .uniform1i(NProgram.UNIFORM_SPECULAR_BRDF_LOOKUP_TABLE, 0)
                .uniform1i(NProgram.UNIFORM_WATER_FRAMES, 1)
                .uniform1i(NProgram.UNIFORM_SCREEN, 2)
                .uniform1i(NProgram.UNIFORM_MATERIAL_TEXTURES, 3)
                .uniform1i(NProgram.UNIFORM_LIGHTMAPS, 4)
                .uniform1i(NProgram.UNIFORM_REFLECTION_CUBEMAP_0, 5)
                .uniform1i(NProgram.UNIFORM_REFLECTION_CUBEMAP_1, 6)
                .uniform1i(NProgram.UNIFORM_REFLECTION_CUBEMAP_2, 7)
                .uniform1i(NProgram.UNIFORM_REFLECTION_CUBEMAP_3, 8);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, NSpecularBRDFLookupTable.SPECULAR_BRDF_TEXTURE);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_ARRAY, Water.TEXTURE);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, Pipeline.OPAQUE_FRAMEBUFFER.colorBuffer());

        render(variant, toRender);

        glUseProgram(0);
    }

    private void render(
            BetterUniformSetter variant,
            List<ToRender> list
    ) {
        Matrix4f worldToLocal = new Matrix4f();
        Vector3f relative = new Vector3f();

        Matrix4f transformedBone = new Matrix4f();

        NLightmaps lastLightmaps = null;
        NTextures lastTextures = null;
        NCubemap[] lastCubemaps = null;
        Matrix4fc lastTransformation = null;
        NMesh lastMesh = null;
        NAnimator lastAnimator = null;
        boolean faceCulling = true;

        Matrix3f normalMatrix = new Matrix3f();

        for (ToRender render : list) {
            N3DModel n3dmodel = render.obj.getN3DModel();

            WrappedLight[] wrappedLights = render.lights;
            AmbientCube ambientCube = render.obj.getAmbientCube();
            NLightmaps lightmaps = render.obj.getLightmaps();
            NMaterial material = render.geometry.getMaterial();
            if (isReflectionsDebugEnabled()) {
                material = REFLECTION_DEBUG;
            }
            NTextures textures = material.getTextures();
            NCubemap[] cbmaps = render.cubemaps;
            Matrix4f transformation = render.transformation;
            NMesh mesh = render.geometry.getMesh();
            NAnimator animator = render.obj.getAnimator();

            for (int i = 0; i < wrappedLights.length; i++) {
                WrappedLight light = wrappedLights[i];
                if (light == null) {
                    variant.uniform1i(NProgram.UNIFORM_LIGHT_TYPE(i), NProgram.NULL_LIGHT_TYPE);
                    continue;
                }

                NLight nlight = light.light;
                variant
                        .uniform1i(NProgram.UNIFORM_LIGHT_TYPE(i), light.type)
                        .uniform1f(NProgram.UNIFORM_LIGHT_SIZE(i), nlight.getSize())
                        .uniform3f(NProgram.UNIFORM_LIGHT_DIFFUSE(i),
                                nlight.getDiffuse().x() * light.color.x(),
                                nlight.getDiffuse().y() * light.color.y(),
                                nlight.getDiffuse().z() * light.color.z()
                        )
                        .uniform3f(NProgram.UNIFORM_LIGHT_SPECULAR(i),
                                nlight.getSpecular().x() * light.color.x(),
                                nlight.getSpecular().y() * light.color.y(),
                                nlight.getSpecular().z() * light.color.z()
                        )
                        .uniform3f(NProgram.UNIFORM_LIGHT_AMBIENT(i),
                                nlight.getAmbient().x() * light.color.x() * light.ambient,
                                nlight.getAmbient().y() * light.color.y() * light.ambient,
                                nlight.getAmbient().z() * light.color.z() * light.ambient
                        )
                        .uniform3f(NProgram.UNIFORM_LIGHT_POSITION(i),
                                light.position.x(),
                                light.position.y(),
                                light.position.z()
                        );
                switch (light.type) {
                    case NProgram.DIRECTIONAL_LIGHT_TYPE -> {
                        NDirectionalLight directional = (NDirectionalLight) light.light;
                        variant
                                .uniform3f(NProgram.UNIFORM_LIGHT_DIRECTION(i),
                                        directional.getDirection().x(),
                                        directional.getDirection().y(),
                                        directional.getDirection().z()
                                );
                    }
                    case NProgram.POINT_LIGHT_TYPE -> {
                        NPointLight point = (NPointLight) light.light;
                        variant
                                .uniform1f(NProgram.UNIFORM_LIGHT_RANGE(i), point.getRange());
                    }
                    case NProgram.SPOT_LIGHT_TYPE -> {
                        NSpotLight spot = (NSpotLight) light.light;
                        variant
                                .uniform1f(NProgram.UNIFORM_LIGHT_RANGE(i), spot.getRange())
                                .uniform3f(NProgram.UNIFORM_LIGHT_DIRECTION(i),
                                        spot.getDirection().x(),
                                        spot.getDirection().y(),
                                        spot.getDirection().z()
                                )
                                .uniform1f(NProgram.UNIFORM_LIGHT_INNER_CONE(i), spot.getInnerCone())
                                .uniform1f(NProgram.UNIFORM_LIGHT_OUTER_CONE(i), spot.getOuterCone());
                    }
                }
            }

            for (int i = 0; i < AmbientCube.SIDES; i++) {
                Vector3fc c = ambientCube.getSide(i);
                variant.uniform3f(NProgram.UNIFORM_AMBIENT_CUBE(i),
                        c.x(), c.y(), c.z()
                );
            }

            if (animator != null) {
                transformation = render.model;
            }

            {
                Vector4fc c = material.getColor();
                Vector3fc f = material.getFresnelOutlineColor();
                variant
                        .uniform4f(NProgram.UNIFORM_MATERIAL_COLOR,
                                c.x(), c.y(), c.z(), c.w())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_METALLIC,
                                material.getMetallic())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_ROUGHNESS,
                                material.getRoughness())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_DIFFUSE_SPECULAR_RATIO,
                                material.getDiffuseSpecularRatio())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_HEIGHT,
                                material.getHeight())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_HEIGHT_MIN_LAYERS,
                                material.getHeightMinLayers())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_HEIGHT_MAX_LAYERS,
                                material.getHeightMaxLayers())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_EMISSIVE,
                                material.getEmissive())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_WATER,
                                material.getWater())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_REFRACTION,
                                material.getRefraction())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_REFRACTION_POWER,
                                material.getRefractionPower())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_AMBIENT_OCCLUSION,
                                material.getAmbientOcclusion())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_FRESNEL_OUTLINE,
                                material.getFresnelOutline())
                        .uniform3f(NProgram.UNIFORM_MATERIAL_FRESNEL_OUTLINE_COLOR,
                                f.x(), f.y(), f.z());

                variant
                        .uniform1i(NProgram.UNIFORM_ENABLE_REFRACTIONS,
                                (material.getRefraction() == 0f ? 0 : 1))
                        .uniform1i(NProgram.UNIFORM_ENABLE_PARALLAX_MAPPING,
                                (material.getHeight() == 0f ? 0 : 1))
                        .uniform1i(NProgram.UNIFORM_ENABLE_WATER,
                                (material.getWater() == 0f ? 0 : 1));
            }

            if (!textures.equals(lastTextures)) {
                variant.uniform1i(NProgram.UNIFORM_ENABLE_OPAQUE_TEXTURE,
                        (NBlendingMode.OPAQUE.equals(textures.getBlendingMode()) ? 1 : 0));

                int texturesId = textures.textures();

                glActiveTexture(GL_TEXTURE3);
                glBindTexture(GL_TEXTURE_2D_ARRAY, texturesId);

                lastTextures = textures;
            }

            if (lastLightmaps != lightmaps) {
                int maps = lightmaps.lightmaps();

                glActiveTexture(GL_TEXTURE4);
                glBindTexture(GL_TEXTURE_2D_ARRAY, maps);

                for (int i = 0; i < lightmaps.getNumberOfLightmaps(); i++) {
                    variant.uniform1f(NProgram.UNIFORM_LIGHTMAP_INTENSITY(i),
                            lightmaps.getIntensity(i));
                }

                variant.uniform1i(NProgram.UNIFORM_ENABLE_LIGHTMAPS,
                        (lightmaps == NLightmaps.NULL_LIGHTMAPS ? 0 : 1));

                lastLightmaps = lightmaps;
            }

            if (!Arrays.equals(lastCubemaps, cbmaps)) {
                for (int i = 0; i < cbmaps.length; i++) {
                    NCubemap cubemap = cbmaps[i];

                    int cubemapObj = NCubemap.NULL_CUBEMAP.cubemap();
                    if (cubemap != null) {
                        cubemapObj = cubemap.cubemap();
                    }

                    int lastCubemapObj = -1;
                    if (lastCubemaps != null) {
                        lastCubemapObj = NCubemap.NULL_CUBEMAP.cubemap();
                        if (lastCubemaps[i] != null) {
                            lastCubemapObj = lastCubemaps[i].cubemap();
                        }
                    }

                    if (cubemapObj != lastCubemapObj) {
                        glActiveTexture(GL_TEXTURE5 + i);
                        glBindTexture(GL_TEXTURE_CUBE_MAP, cubemapObj);
                    }

                    boolean enabled = false;
                    float intensity = 0f;

                    if (cubemap != null) {
                        enabled = true;
                        intensity = cubemap.getIntensity();

                        cubemap.getCubemapBox().calculateRelative(
                                getCamera().getPosition(), worldToLocal, relative);
                    }

                    variant
                            .uniform1i(NProgram.UNIFORM_PARALLAX_CUBEMAP_ENABLED(i),
                                    (enabled ? 1 : 0))
                            .uniform1f(NProgram.UNIFORM_PARALLAX_CUBEMAP_INTENSITY(i),
                                    intensity)
                            .uniform3f(NProgram.UNIFORM_PARALLAX_CUBEMAP_POSITION(i),
                                    relative.x(), relative.y(), relative.z())
                            .uniformMatrix4fv(NProgram.UNIFORM_PARALLAX_CUBEMAP_WORLD_TO_LOCAL(i),
                                    worldToLocal);
                }

                lastCubemaps = cbmaps;
            }

            if (!transformation.equals(lastTransformation)) {
                variant.uniformMatrix4fv(
                        NProgram.UNIFORM_MODEL,
                        transformation
                );
                variant.uniformMatrix3fv(
                        NProgram.UNIFORM_NORMAL_MODEL,
                        transformation.normal(normalMatrix)
                );
                lastTransformation = transformation;
            }

            if (animator != lastAnimator
                    || (animator == null && mesh.getNumberOfBones() != 0)
                    || !mesh.equals(lastMesh)) {
                for (int boneIndex = 0; boneIndex < mesh.getNumberOfBones(); boneIndex++) {
                    String bone = mesh.getBone(boneIndex);

                    if (animator != null) {
                        Matrix4fc boneMatrix = animator.getBoneMatrix(bone);
                        N3DModelNode boneNode = n3dmodel.getNode(bone);

                        transformedBone
                                .set(boneMatrix)
                                .mul(boneNode.getToNodeSpace())
                                .mul(render.geometry.getParent().getToRootSpace());

                        variant.uniformMatrix4fv(NProgram.UNIFORM_BONE_MATRIX(boneIndex),
                                transformedBone);
                    } else {
                        variant.uniformMatrix4fv(NProgram.UNIFORM_BONE_MATRIX(boneIndex),
                                IDENTITY);
                    }
                }

                lastAnimator = animator;
            }

            if (!mesh.equals(lastMesh)) {
                glBindVertexArray(mesh.getVAO());
                lastMesh = mesh;
            }
            
            if (render.geometry.isFaceCullingEnabled() != faceCulling) {
                faceCulling = render.geometry.isFaceCullingEnabled();
                if (faceCulling) {
                    glEnable(GL_CULL_FACE);
                } else {
                    glDisable(GL_CULL_FACE);
                }
            }
            
            glDrawElements(GL_TRIANGLES, mesh.getIndices().length, GL_UNSIGNED_INT, 0);

            Main.NUMBER_OF_DRAWCALLS++;
            Main.NUMBER_OF_VERTICES += mesh.getIndices().length;
        }
        
        if (!faceCulling) {
            glEnable(GL_CULL_FACE);
        }
        
        glBindVertexArray(0);
    }

}
