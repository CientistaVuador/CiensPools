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
import java.util.concurrent.ConcurrentLinkedQueue;
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

    public static boolean PARALLAX_ENABLED = true;
    public static boolean REFLECTIONS_ENABLED = true;
    public static boolean HDR_OUTPUT = false;
    public static boolean REFLECTIONS_DEBUG = false;
    public static boolean USE_TONEMAPPING = true;

    public static final int OCCLUSION_QUERY_MINIMUM_VERTICES = 1024;
    public static final int OCCLUSION_QUERY_MINIMUM_SAMPLES = 8;

    public static final Matrix4fc IDENTITY = new Matrix4f();

    private static final ConcurrentLinkedQueue<N3DObject> renderQueue = new ConcurrentLinkedQueue<>();

    public static N3DObject[] copyQueueObjects() {
        return renderQueue.toArray(N3DObject[]::new);
    }

    public static void queueRender(N3DObject obj) {
        renderQueue.add(obj);
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

    private static WrappedLight wrapLight(
            Camera camera, NLight light, Vector3f color, float ambientFactor
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
            pX = (float) (position.x() - camera.getPosition().x());
            pY = (float) (position.y() - camera.getPosition().y());
            pZ = (float) (position.z() - camera.getPosition().z());
        }

        return new WrappedLight(lightType, light, new Vector3f(pX, pY, pZ), color, ambientFactor);
    }

    private static List<N3DObject> collectObjects() {
        List<N3DObject> objects = new ArrayList<>();
        N3DObject obj;
        while ((obj = renderQueue.poll()) != null) {
            objects.add(obj);
        }
        return objects;
    }

    private static List<N3DObject> filterOccluded(Camera camera, List<N3DObject> objects) {
        List<N3DObject> notOccludedObjects = new ArrayList<>();

        Matrix4f projectionView = new Matrix4f(camera.getProjection()).mul(camera.getView());

        Vector3f transformedMin = new Vector3f();
        Vector3f transformedMax = new Vector3f();

        Matrix4f modelMatrix = new Matrix4f();

        for (N3DObject obj : objects) {
            obj.calculateModelMatrix(modelMatrix, camera);

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
            if (obj.getN3DModel().getVerticesCount() >= OCCLUSION_QUERY_MINIMUM_VERTICES) {
                occlusionQuery:
                {
                    float x = (transformedMin.x() + transformedMax.x()) * 0.5f;
                    float y = (transformedMin.y() + transformedMax.y()) * 0.5f;
                    float z = (transformedMin.z() + transformedMax.z()) * 0.5f;
                    float width = transformedMax.x() - transformedMin.x();
                    float height = transformedMax.y() - transformedMin.y();
                    float depth = transformedMax.z() - transformedMin.z();

                    if (GPUOcclusion.testCamera(
                            0f, 0f, 0f, camera.getNearPlane() * 1.05f,
                            x, y, z,
                            width, height, depth
                    )) {
                        break occlusionQuery;
                    }

                    if (obj.hasQueryObject()) {
                        int queryObject = obj.getQueryObject();
                        int samplesPassed = glGetQueryObjecti(queryObject, GL_QUERY_RESULT);
                        if (samplesPassed <= OCCLUSION_QUERY_MINIMUM_SAMPLES) {
                            occluded = true;
                        }
                    }
                    if (!obj.hasQueryObject()) {
                        obj.createQueryObject();
                    }
                    GPUOcclusion.occlusionQuery(
                            camera.getProjection(), camera.getView(),
                            x, y, z, width, height, depth,
                            obj.getQueryObject()
                    );
                }
            }

            if (!occluded) {
                double absCenterX = camera.getPosition().x() + ((transformedMin.x() + transformedMax.x()) * 0.5f);
                double absCenterY = camera.getPosition().y() + ((transformedMin.y() + transformedMax.y()) * 0.5f);
                double absCenterZ = camera.getPosition().z() + ((transformedMin.z() + transformedMax.z()) * 0.5f);

                obj.updateAmbientCube(absCenterX, absCenterY, absCenterZ);

                notOccludedObjects.add(obj);
            }
        }

        return notOccludedObjects;
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

    public static void render(
            Camera camera,
            List<NLight> lights,
            NCubemaps cubemaps
    ) {
        if (cubemaps == null) {
            cubemaps = NCubemaps.NULL_CUBEMAPS;
        }

        List<N3DObject> objectsToRender = collectObjects();
        objectsToRender = filterOccluded(
                camera,
                objectsToRender
        );

        Matrix4f projectionView = new Matrix4f(camera.getProjection()).mul(camera.getView());

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

                obj.calculateModelMatrix(modelMatrix, camera);

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
                            toRenderCubemaps = cubemaps
                                    .getCubemapsBVH()
                                    .testRelativeAab(camera.getPosition(), transformedMin, transformedMax);

                            List<NCubemap> toRenderCubemapsFiltered = new ArrayList<>();
                            for (NCubemap c : toRenderCubemaps) {
                                if (c.getIntensity() == 0f) {
                                    continue;
                                }

                                float cubemapMinX = (float) (c.getCubemapBox().getMin().x() - camera.getPosition().x());
                                float cubemapMinY = (float) (c.getCubemapBox().getMin().y() - camera.getPosition().y());
                                float cubemapMinZ = (float) (c.getCubemapBox().getMin().z() - camera.getPosition().z());
                                float cubemapMaxX = (float) (c.getCubemapBox().getMax().x() - camera.getPosition().x());
                                float cubemapMaxY = (float) (c.getCubemapBox().getMax().y() - camera.getPosition().y());
                                float cubemapMaxZ = (float) (c.getCubemapBox().getMax().z() - camera.getPosition().z());

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

                        double absCenterX = camera.getPosition().x() + centerX;
                        double absCenterY = camera.getPosition().y() + centerY;
                        double absCenterZ = camera.getPosition().z() + centerZ;

                        toRenderCubemaps.sort((o1, o2) -> {
                            double o1Dist = Math.min(
                                    o1.getCubemapBox().getCubemapPosition().distanceSquared(camera.getPosition()),
                                    o1.getCubemapBox().getCubemapPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                            );

                            double o2Dist = Math.min(
                                    o2.getCubemapBox().getCubemapPosition().distanceSquared(camera.getPosition()),
                                    o2.getCubemapBox().getCubemapPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                            );

                            return Double.compare(
                                    o1Dist,
                                    o2Dist
                            );
                        });

                        NCubemap[] finalToRenderCubemaps = Arrays.copyOf(toRenderCubemaps.toArray(NCubemap[]::new), NProgram.MAX_AMOUNT_OF_CUBEMAPS);

                        List<WrappedLight> toRenderLights = new ArrayList<>();
                        lights.sort((o1, o2) -> {
                            double disto1 = 0.0;
                            double disto2 = 0.0;

                            if (o1 instanceof NLight.NSpotLight e) {
                                disto1 = Math.min(
                                        e.getPosition().distanceSquared(camera.getPosition()),
                                        e.getPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            } else if (o1 instanceof NLight.NPointLight e) {
                                disto1 = Math.min(
                                        e.getPosition().distanceSquared(camera.getPosition()),
                                        e.getPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            }

                            if (o2 instanceof NLight.NSpotLight e) {
                                disto2 = Math.min(
                                        e.getPosition().distanceSquared(camera.getPosition()),
                                        e.getPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            } else if (o2 instanceof NLight.NPointLight e) {
                                disto2 = Math.min(
                                        e.getPosition().distanceSquared(camera.getPosition()),
                                        e.getPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            }

                            return Double.compare(disto1, disto2);
                        });
                        for (NLight light : lights) {
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
                                    lightDirection.set(lightPosition).sub(absCenterX, absCenterY, absCenterZ);
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
                                        (float) lightDirection.x(), (float) lightDirection.y(), (float) lightDirection.z(),
                                        length,
                                        shadowColor
                                );

                                r = shadowColor.x();
                                g = shadowColor.y();
                                b = shadowColor.z();
                            }
                            if (r != 0f || g != 0f || b != 0f || ambientFactor != 0f) {
                                toRenderLights.add(wrapLight(camera, light, new Vector3f(r, g, b), ambientFactor));
                            }
                        }
                        WrappedLight[] finalLights = toRenderLights.toArray(WrappedLight[]::new);
                        finalLights = Arrays.copyOf(finalLights, NProgram.MAX_AMOUNT_OF_LIGHTS);

                        float distanceSquared = (centerX * centerX) + (centerY * centerY) + (centerZ * centerZ);

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

        List<ToRender> opaqueList = new ArrayList<>();
        List<ToRender> testedList = new ArrayList<>();
        List<ToRender> blendList = new ArrayList<>();

        for (ToRender toRender : toRenderList) {
            if (toRender.geometry.getMaterial().isInvisible()) {
                continue;
            }

            NBlendingMode mode = toRender.geometry.getMaterial().getBlendingMode();

            switch (mode) {
                case OPAQUE ->
                    opaqueList.add(toRender);
                case ALPHA_TESTING ->
                    testedList.add(toRender);
                case ALPHA_BLENDING ->
                    blendList.add(toRender);
            }
        }

        Comparator<ToRender> distanceComparator = (o1, o2) -> {
            return Float.compare(o1.distanceSquared, o2.distanceSquared);
        };

        opaqueList.sort(distanceComparator);
        testedList.sort(distanceComparator);
        blendList.sort(distanceComparator.reversed());

        if (!opaqueList.isEmpty() || !testedList.isEmpty()) {
            glDisable(GL_BLEND);
            if (!opaqueList.isEmpty()) {
                renderVariant(NProgram.VARIANT_OPAQUE, camera, opaqueList);
            }
            if (!testedList.isEmpty()) {
                renderVariant(NProgram.VARIANT_ALPHA_TESTING, camera, testedList);
            }
            glEnable(GL_BLEND);
        }

        renderSkybox(camera, cubemaps.getSkybox());

        GPUOcclusion.executeQueries();

        if (!blendList.isEmpty()) {
            renderVariant(NProgram.VARIANT_ALPHA_BLENDING, camera, blendList);
        }
    }

    private static void renderSkybox(
            Camera camera,
            NCubemap skybox
    ) {
        BetterUniformSetter program = NSkybox.SKYBOX_PROGRAM;

        glUseProgram(program.getProgram());

        BetterUniformSetter.uniformMatrix4fv(program.locationOf(NSkybox.UNIFORM_PROJECTION),
                camera.getProjection()
        );
        BetterUniformSetter.uniformMatrix4fv(program.locationOf(NSkybox.UNIFORM_VIEW),
                camera.getView()
        );

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_CUBE_MAP, skybox.cubemap());
        glUniform1i(program.locationOf(NSkybox.UNIFORM_SKYBOX), 0);

        glUniform1i(program.locationOf(NSkybox.UNIFORM_HDR_OUTPUT),
                (N3DObjectRenderer.HDR_OUTPUT ? 1 : 0)
        );

        glBindVertexArray(NSkybox.VAO);
        glDrawElements(GL_TRIANGLES, NSkybox.AMOUNT_OF_INDICES, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        glUseProgram(0);

        Main.NUMBER_OF_DRAWCALLS++;
        Main.NUMBER_OF_VERTICES += NSkybox.AMOUNT_OF_INDICES;
    }

    private static void renderVariant(
            BetterUniformSetter variant,
            Camera camera,
            List<ToRender> toRender
    ) {
        NLightmaps.NULL_LIGHTMAPS.lightmaps();
        NCubemap.NULL_CUBEMAP.cubemap();
        NTextures.NULL_TEXTURE.textures();

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
                .uniformMatrix4fv(NProgram.UNIFORM_PROJECTION, camera.getProjection())
                .uniformMatrix4fv(NProgram.UNIFORM_VIEW, camera.getView())
                .uniform1i(NProgram.UNIFORM_ENABLE_GAMMA_CORRECTION, (HDR_OUTPUT ? 0 : 1))
                .uniform1i(NProgram.UNIFORM_ENABLE_TONEMAPPING, (HDR_OUTPUT || !USE_TONEMAPPING ? 0 : 1))
                .uniform1i(NProgram.UNIFORM_ENABLE_PARALLAX_MAPPING, (PARALLAX_ENABLED ? 1 : 0))
                .uniform1i(NProgram.UNIFORM_ENABLE_REFLECTIONS, (REFLECTIONS_ENABLED ? 1 : 0))
                .uniform1f(NProgram.UNIFORM_WATER_COUNTER, Water.WATER_COUNTER)
                .uniform1i(NProgram.UNIFORM_WATER_FRAMES, 0)
                .uniform1i(NProgram.UNIFORM_MATERIAL_TEXTURES, 1)
                .uniform1i(NProgram.UNIFORM_LIGHTMAPS, 2)
                .uniform1i(NProgram.UNIFORM_REFLECTION_CUBEMAP_0, 3)
                .uniform1i(NProgram.UNIFORM_REFLECTION_CUBEMAP_1, 4)
                .uniform1i(NProgram.UNIFORM_REFLECTION_CUBEMAP_2, 5)
                .uniform1i(NProgram.UNIFORM_REFLECTION_CUBEMAP_3, 6);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, Water.TEXTURE);

        render(variant, camera, toRender);

        glUseProgram(0);
    }

    private static void render(
            BetterUniformSetter variant,
            Camera camera,
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

        Matrix3f normalMatrix = new Matrix3f();

        for (ToRender render : list) {
            N3DModel n3dmodel = render.obj.getN3DModel();

            WrappedLight[] lights = render.lights;
            AmbientCube ambientCube = render.obj.getAmbientCube();
            NLightmaps lightmaps = render.obj.getLightmaps();
            NMaterial material = render.geometry.getMaterial();
            if (REFLECTIONS_DEBUG) {
                material = NMaterial.MIRROR;
            }
            NTextures textures = material.getTextures();
            NCubemap[] cubemaps = render.cubemaps;
            Matrix4f transformation = render.transformation;
            NMesh mesh = render.geometry.getMesh();
            NAnimator animator = render.obj.getAnimator();

            for (int i = 0; i < lights.length; i++) {
                WrappedLight light = lights[i];
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
                Vector4fc c = material.getNewColor();
                Vector3fc f = material.getNewFresnelOutlineColor();
                variant
                        .uniform4f(NProgram.UNIFORM_MATERIAL_COLOR, c.x(), c.y(), c.z(), c.w())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_METALLIC, material.getNewMetallic())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_ROUGHNESS, material.getNewRoughness())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_INVERSE_ROUGHNESS_EXPONENT, material.getNewInverseRoughnessExponent())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_DIFFUSE_SPECULAR_RATIO, material.getNewDiffuseSpecularRatio())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_HEIGHT, material.getNewHeight())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_HEIGHT_MIN_LAYERS, material.getNewHeightMinLayers())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_HEIGHT_MAX_LAYERS, material.getNewHeightMaxLayers())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_EMISSIVE, material.getNewEmissive())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_WATER, material.getNewWater())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_REFRACTION, material.getNewRefraction())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_REFRACTION_POWER, material.getNewRefractionPower())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_AMBIENT_OCCLUSION, material.getNewAmbientOcclusion())
                        .uniform1f(NProgram.UNIFORM_MATERIAL_FRESNEL_OUTLINE, material.getNewFresnelOutline())
                        .uniform3f(NProgram.UNIFORM_MATERIAL_FRESNEL_OUTLINE_COLOR, f.x(), f.y(), f.z())
                        ;
                
                variant
                        .uniform1i(NProgram.UNIFORM_ENABLE_REFRACTIONS,
                                (material.getNewRefraction() == 0f ? 0 : 1))
                        .uniform1i(NProgram.UNIFORM_ENABLE_PARALLAX_MAPPING, 
                                (material.getNewHeight() == 0f ? 0 : 1))
                        .uniform1i(NProgram.UNIFORM_ENABLE_WATER, 
                                (material.getNewWater() == 0f ? 0 : 1))
                        ;
            }
            
            if (!textures.equals(lastTextures)) {
                int texturesId = textures.textures();

                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D_ARRAY, texturesId);
                
                lastTextures = textures;
            }

            if (lastLightmaps != lightmaps) {
                int maps = lightmaps.lightmaps();

                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D_ARRAY, maps);

                for (int i = 0; i < lightmaps.getNumberOfLightmaps(); i++) {
                    variant.uniform1f(NProgram.UNIFORM_LIGHTMAP_INTENSITY(i),
                            lightmaps.getIntensity(i));
                }
                
                variant.uniform1i(NProgram.UNIFORM_ENABLE_LIGHTMAPS, 
                        (lightmaps == NLightmaps.NULL_LIGHTMAPS ? 0 : 1));
                
                lastLightmaps = lightmaps;
            }

            if (!Arrays.equals(lastCubemaps, cubemaps)) {
                for (int i = 0; i < cubemaps.length; i++) {
                    NCubemap cubemap = cubemaps[i];

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
                        glActiveTexture(GL_TEXTURE3 + i);
                        glBindTexture(GL_TEXTURE_CUBE_MAP, cubemapObj);
                    }

                    boolean enabled = false;
                    float intensity = 0f;

                    if (cubemap != null) {
                        enabled = true;
                        intensity = cubemap.getIntensity();

                        cubemap.getCubemapBox().calculateRelative(
                                camera.getPosition(), worldToLocal, relative);
                    }
                    
                    variant
                            .uniform1i(NProgram.UNIFORM_PARALLAX_CUBEMAP_ENABLED(i), (enabled ? 1 : 0))
                            .uniform1f(NProgram.UNIFORM_PARALLAX_CUBEMAP_INTENSITY(i), intensity)
                            .uniform3f(NProgram.UNIFORM_PARALLAX_CUBEMAP_POSITION(i),
                                    relative.x(), relative.y(), relative.z())
                            .uniformMatrix4fv(NProgram.UNIFORM_PARALLAX_CUBEMAP_WORLD_TO_LOCAL(i),
                                    worldToLocal)
                            ;
                }

                lastCubemaps = cubemaps;
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
                    || !mesh.equals(lastMesh)
                    ) {
                for (int boneIndex = 0; boneIndex < mesh.getNumberOfBones(); boneIndex++) {
                    String bone = mesh.getBone(boneIndex);

                    if (animator != null) {
                        Matrix4fc boneMatrix = animator.getBoneMatrix(bone);
                        N3DModelNode boneNode = n3dmodel.getNode(bone);

                        transformedBone
                                .set(boneMatrix)
                                .mul(boneNode.getToNodeSpace())
                                .mul(render.geometry.getParent().getToRootSpace());

                        variant.uniformMatrix4fv(NProgram.UNIFORM_BONE_MATRIX(boneIndex), transformedBone);
                    } else {
                        variant.uniformMatrix4fv(NProgram.UNIFORM_BONE_MATRIX(boneIndex), IDENTITY);
                    }
                }

                lastAnimator = animator;
            }

            if (!mesh.equals(lastMesh)) {
                glBindVertexArray(mesh.getVAO());
                lastMesh = mesh;
            }
            
            glDrawElements(GL_TRIANGLES, mesh.getIndices().length, GL_UNSIGNED_INT, 0);

            Main.NUMBER_OF_DRAWCALLS++;
            Main.NUMBER_OF_VERTICES += mesh.getIndices().length;
        }

        glBindVertexArray(0);
    }

    private N3DObjectRenderer() {

    }

}
