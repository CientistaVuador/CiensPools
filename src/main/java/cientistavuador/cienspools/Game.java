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
package cientistavuador.cienspools;

import cientistavuador.cienspools.camera.FreeCamera;
import cientistavuador.cienspools.debug.AabRender;
import cientistavuador.cienspools.debug.LineRender;
import cientistavuador.cienspools.newrendering.N3DModel;
import cientistavuador.cienspools.newrendering.N3DObject;
import cientistavuador.cienspools.newrendering.N3DObjectRenderer;
import cientistavuador.cienspools.newrendering.NCubemap;
import cientistavuador.cienspools.newrendering.NCubemapBox;
import cientistavuador.cienspools.newrendering.NCubemapRenderer;
import cientistavuador.cienspools.newrendering.NCubemapStore;
import cientistavuador.cienspools.newrendering.NCubemaps;
import cientistavuador.cienspools.newrendering.NLight;
import cientistavuador.cienspools.newrendering.NLightmaps;
import cientistavuador.cienspools.newrendering.NLightmapsStore;
import cientistavuador.cienspools.newrendering.NMap;
import cientistavuador.cienspools.newrendering.NTextures;
import cientistavuador.cienspools.physics.PlayerController;
import cientistavuador.cienspools.popups.BakePopup;
import cientistavuador.cienspools.popups.ContinuePopup;
import cientistavuador.cienspools.text.GLFontRenderer;
import cientistavuador.cienspools.text.GLFontSpecifications;
import cientistavuador.cienspools.ubo.CameraUBO;
import cientistavuador.cienspools.ubo.UBOBindingPoints;
import cientistavuador.cienspools.util.ColorUtils;
import cientistavuador.cienspools.util.DebugRenderer;
import cientistavuador.cienspools.util.PhysicsSpaceDebugger;
import cientistavuador.cienspools.util.StringUtils;
import cientistavuador.cienspools.util.bakedlighting.AmbientCubeDebug;
import cientistavuador.cienspools.util.bakedlighting.Lightmapper;
import cientistavuador.cienspools.util.bakedlighting.Scene;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import static org.lwjgl.glfw.GLFW.*;

/**
 *
 * @author Cien
 */
public class Game {

    private static final Game GAME = new Game();

    public static Game get() {
        return GAME;
    }

    private final FreeCamera camera = new FreeCamera();
    private NMap.BakeStatus status = null;

    private final NCubemap skybox;
    private final String[] cubemapNames = {
        "big_pool",
        "big_pool_refraction",
        "spawn",
        "exit",
        "exit_refraction"
    };
    private final NCubemapBox[] cubemapInfos = {
        new NCubemapBox(4.68, 2.89, -0.68, -11.06, -0.15, -7.09, 20.47, 10.10, 5.06),
        new NCubemapBox(4.07, -0.33, -0.56, -2.29, -1.07, -5.08, 9.69, 0.00, 3.02),
        new NCubemapBox(3.23, 2.20, 12.92, -11.22, -0.05, 7.91, 19.79, 10.59, 18.04),
        new NCubemapBox(-0.15, 2.54, -14.83, -11.11, -0.03, -19.12, 19.56, 10.09, -10.06),
        new NCubemapBox(0.21, -0.39, -17.53, -11.14, -1.00, -16.03, 19.62, -0.09, -19.09)
    };

    private NCubemaps cubemaps;

    private NMap map;
    private NMap nextMap;

    private final N3DModel boomBoxModel;
    private final N3DObject selector;
    private final List<N3DObject> boomBoxes = new ArrayList<>();

    private final NLight.NSpotLight flashlight = new NLight.NSpotLight("flashlight");
    private final NLight.NPointLight lighter = new NLight.NPointLight("lighter");
    private final List<NLight> lights = new ArrayList<>();
    private final Scene scene = new Scene();

    private boolean ambientCubeDebug = false;
    private boolean debugCollision = false;
    
    private final PhysicsSpace physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
    private final PhysicsSpaceDebugger physicsSpaceDebugger = new PhysicsSpaceDebugger(this.physicsSpace);
    private final PlayerController playerController = new PlayerController();

    {
        this.playerController.getCharacterController().setPosition(16.72f, 0f, 12.76f);
        this.camera.setRotation(0f, -180f, 0f);

        NLight.NDirectionalLight sun = new NLight.NDirectionalLight("sun");
        sun.getDiffuse().set(20f);
        sun.getSpecular().set(3f);
        sun.getAmbient().set(0.1f);
        sun.setDynamic(false);
        sun.getDirection().set(-0.5f, -0.75f, -0.45f).normalize();
        this.lights.add(sun);

        try {
            this.skybox = NCubemapStore
                    .readCubemap("cientistavuador/cienspools/resources/cubemaps/skybox.cbm");
            
            List<N3DObject> mapObjects = new ArrayList<>();
            {
                N3DModel roomModel = N3DModel.RESOURCES.get("[031E114E9B854953|9E778413412CA407]Surface");
                N3DObject room = new N3DObject("room", roomModel);
                mapObjects.add(room);
            }

            this.map = new NMap("map", mapObjects, NMap.DEFAULT_LIGHTMAP_MARGIN, 45f);
            this.map.setLightmaps(NLightmapsStore
                    .readLightmaps("cientistavuador/cienspools/resources/lightmaps/lightmap.lit"));

            this.flashlight.setInnerConeAngle(10f);
            this.flashlight.setOuterConeAngle(40f);
            this.flashlight.setDiffuseSpecularAmbient(50f, 10f, 0.1f);
            this.flashlight.setRange(20f);
            this.flashlight.setSize(0.25f);

            ColorUtils.setSRGB(this.lighter.getDiffuse(), 233, 140, 80).mul(4f);
            ColorUtils.setSRGB(this.lighter.getSpecular(), 233, 140, 80).mul(0.03f);
            ColorUtils.setSRGB(this.lighter.getAmbient(), 233, 140, 80).mul(0.015f);

            {
                this.boomBoxModel = N3DModel.RESOURCES.get("[D48EAA8D455A4B57|A34C2F1CE3B5D2C7]BoomBox");
                this.selector = new N3DObject("selector", boomBoxModel);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        for (NLight light : this.lights) {
            if (light.isDynamic()) {
                continue;
            }
            this.scene.getLights().add(NMap.convertLight(light));
        }

        List<NCubemap> cubemapsList = new ArrayList<>();
        try {
            for (String name : this.cubemapNames) {
                cubemapsList.add(NCubemapStore.readCubemap("cientistavuador/cienspools/resources/cubemaps/" + name + ".cbm"));
            }
        } catch (NullPointerException | IOException ex) {
            ex.printStackTrace();
        }
        this.cubemaps = new NCubemaps(this.skybox, cubemapsList);
        this.map.setCubemaps(this.cubemaps);

        this.camera.setMovementDisabled(true);
        this.playerController.getCharacterController().addToPhysicsSpace(this.physicsSpace);

        this.physicsSpace.setGravity(new com.jme3.math.Vector3f(0f, -9.8f * Main.TO_PHYSICS_ENGINE_UNITS, 0f));
        this.physicsSpace.addCollisionObject(new PhysicsRigidBody(this.map.getMeshCollision(), 0f));
        this.physicsSpace.setAccuracy(1f / 480f);
        this.physicsSpace.setMaxSubSteps(16);
    }

    private Game() {

    }

    public void start() {
        
        
        NTextures.NULL_TEXTURE.textures();
        NCubemap.NULL_CUBEMAP.cubemap();
        NLightmaps.NULL_LIGHTMAPS.lightmaps();

        this.camera.setUBO(CameraUBO.create(UBOBindingPoints.PLAYER_CAMERA));

        for (int i = 0; i < this.map.getNumberOfObjects(); i++) {
            this.map.getObject(i).getN3DModel().load();
        }

        this.skybox.cubemap();

        for (int i = 0; i < this.cubemaps.getNumberOfCubemaps(); i++) {
            this.cubemaps.getCubemap(i).cubemap();
        }

        System.gc();
    }

    public void loop() {
        this.camera.updateMovement();
        this.camera.updateUBO();

        this.playerController.update(this.camera.getFront(), this.camera.getRight());

        this.camera.setPosition(
                this.playerController.getEyePosition().x(),
                this.playerController.getEyePosition().y(),
                this.playerController.getEyePosition().z()
        );

        this.physicsSpace.update((float) Main.TPF);

        if (this.playerController.getCharacterController().getPosition().y() < -10f) {
            this.playerController.getCharacterController().setPosition(0f, 0.1f, 0f);
        }

        this.flashlight.getPosition().set(this.camera.getPosition());
        this.flashlight.getDirection().set(this.camera.getFront()).add(0f, -0.15f, 0f).normalize();

        this.lighter.getPosition().set(this.camera.getRight()).negate()
                .mul(0.05f).add(this.camera.getPosition());

        if (this.nextMap != null) {
            this.map = this.nextMap;

            this.nextMap = null;
        }

        if (this.map.getLightmaps() != null && this.ambientCubeDebug) {
            AmbientCubeDebug.render(
                    this.map
                            .getLightmaps()
                            .getAmbientCubes()
                            .getAmbientCubes(),
                    this.camera.getProjection(), this.camera.getView(), this.camera.getPosition()
            );
        }
        
        this.selector.getPosition().set(this.camera.getPosition()).add(this.camera.getFront());
        
        if (this.debugCollision) {
            this.physicsSpaceDebugger.pushToDebugRenderer(
                    this.camera.getProjection(), this.camera.getView(), this.camera.getPosition());
        }

        for (int i = 0; i < this.map.getNumberOfObjects(); i++) {
            N3DObjectRenderer.queueRender(this.map.getObject(i));
        }
        for (N3DObject boomBox : this.boomBoxes) {
            N3DObjectRenderer.queueRender(boomBox);
        }
        N3DObjectRenderer.queueRender(selector);

        N3DObjectRenderer.render(this.camera, this.lights, this.cubemaps);

        AabRender.renderQueue(this.camera);
        LineRender.renderQueue(this.camera);
        DebugRenderer.render();

        if (this.status != null) {
            if (!this.status.getTask().isDone()) {
                String text = this.status.getStatus() + '\n'
                        + String.format("%,.2f", this.status.getRaysPerSecond()) + " Rays Per Second" + '\n'
                        + String.format("%,.2f", this.status.getProgress() * 100.0) + "%";
                GLFontRenderer.render(-0.94f, 0.94f, GLFontSpecifications.SPACE_MONO_REGULAR_0_035_BLACK, text);
                GLFontRenderer.render(-0.95f, 0.95f, GLFontSpecifications.SPACE_MONO_REGULAR_0_035_WHITE, text);
            } else {
                try {
                    this.status.getTask().get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
                try {
                    try (FileOutputStream out = new FileOutputStream("lightmap.lit")) {
                        NLightmapsStore.writeLightmaps(this.map.getLightmaps(), out);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
                this.status = null;
            }
        }

        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + String.format("%,.2f", this.camera.getPosition().x()) + ",y:" + String.format("%,.2f", this.camera.getPosition().y()) + ",z:" + String.format("%,.2f", this.camera.getPosition().z()) + ")";
        Main.WINDOW_TITLE += " (dx:" + String.format("%,.2f", this.camera.getFront().x()) + ",dy:" + String.format("%,.2f", this.camera.getFront().y()) + ",dz:" + String.format("%,.2f", this.camera.getFront().z()) + ")";
        Main.WINDOW_TITLE += " (p:" + String.format("%,.2f", this.camera.getRotation().x()) + ",y:" + String.format("%,.2f", this.camera.getRotation().y()) + ",r:" + String.format("%,.2f", this.camera.getRotation().z()) + ")";
    }

    public void mouseCursorMoved(double x, double y) {
        this.camera.mouseCursorMoved(x, y);
    }

    public void windowSizeChanged(int width, int height) {
        this.camera.setDimensions(width, height);
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_B && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
            N3DObject boomBox = new N3DObject("boomBox", this.boomBoxModel);
            boomBox.setMap(this.map);
            boomBox.getScale().set(40f);
            this.boomBoxModel.getHullCollisionShape().setScale(40f);
            this.boomBoxes.add(boomBox);

            HullCollisionShape hull = this.boomBoxModel.getHullCollisionShape();
            Vector3f center = hull.aabbCenter(null).negate();
            CompoundCollisionShape compound = new CompoundCollisionShape();
            compound.addChildShape(hull, center);

            boomBox.getPosition().set(
                    center.x * Main.FROM_PHYSICS_ENGINE_UNITS,
                    center.y * Main.FROM_PHYSICS_ENGINE_UNITS,
                    center.z * Main.FROM_PHYSICS_ENGINE_UNITS
            );

            PhysicsRigidBody rigidBody = new PhysicsRigidBody(
                    compound,
                    5f
            );
            rigidBody.applyCentralImpulse(new Vector3f(
                    this.camera.getFront().x() * 5f * Main.TO_PHYSICS_ENGINE_UNITS * rigidBody.getMass(),
                    this.camera.getFront().y() * 5f * Main.TO_PHYSICS_ENGINE_UNITS * rigidBody.getMass(),
                    this.camera.getFront().z() * 5f * Main.TO_PHYSICS_ENGINE_UNITS * rigidBody.getMass()
            ));
            rigidBody.setPhysicsLocationDp(new Vec3d(
                    this.camera.getPosition().x() * Main.TO_PHYSICS_ENGINE_UNITS,
                    this.camera.getPosition().y() * Main.TO_PHYSICS_ENGINE_UNITS,
                    this.camera.getPosition().z() * Main.TO_PHYSICS_ENGINE_UNITS
            ));
            rigidBody.setProtectGravity(true);
            rigidBody.setGravity(new Vector3f(0f, -98f, 0f));
            boomBox.setRigidBody(rigidBody);
            this.physicsSpace.addCollisionObject(rigidBody);
        }
        if (key == GLFW_KEY_F1 && action == GLFW_PRESS) {
            N3DObjectRenderer.REFLECTIONS_ENABLED = !N3DObjectRenderer.REFLECTIONS_ENABLED;
        }
        if (key == GLFW_KEY_F2 && action == GLFW_PRESS) {
            N3DObjectRenderer.PARALLAX_ENABLED = !N3DObjectRenderer.PARALLAX_ENABLED;
        }
        if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
            N3DObjectRenderer.REFLECTIONS_DEBUG = !N3DObjectRenderer.REFLECTIONS_DEBUG;
        }
        if (key == GLFW_KEY_F4 && action == GLFW_PRESS) {
            bake:
            {
                if (this.status != null && !this.status.getTask().isDone()) {
                    break bake;
                }

                if (this.camera.isCaptureMouse()) {
                    this.camera.pressEscape();
                }

                BakePopup.show(
                        (p) -> {
                            BakePopup.fromScene(this.scene, p);
                        },
                        (p) -> {
                            p.getBakeButton().setEnabled(false);

                            BakePopup.toScene(this.scene, p);

                            List<N3DObject> list = new ArrayList<>();
                            for (int i = 0; i < this.map.getNumberOfObjects(); i++) {
                                list.add(this.map.getObject(i));
                            }

                            final NMap newMap = new NMap(
                                    this.map.getName(),
                                    list,
                                    NMap.DEFAULT_LIGHTMAP_MARGIN,
                                    this.scene.getPixelToWorldRatio()
                            );

                            this.nextMap = newMap;

                            Set<String> groups = new HashSet<>();
                            for (Scene.Light light : this.scene.getLights()) {
                                if (!groups.contains(light.getGroupName())) {
                                    groups.add(light.getGroupName());
                                }
                            }

                            int originalSize = newMap.getOriginalLightmapSize();
                            int size = newMap.getLightmapSize();
                            long requiredMemory = Lightmapper.approximatedMemoryUsage(size, this.scene.getSamplingMode().numSamples(), groups.size());

                            ContinuePopup.show(p,
                                    "Original Lightmap Size: " + originalSize + "x" + originalSize + "\n"
                                    + "Lightmap Size: " + size + "x" + size + "\n"
                                    + "Required Memory: " + StringUtils.formatMemory(requiredMemory) + "\n"
                                    + "\n"
                                    + "Do you want to continue?",
                                    (e) -> {
                                        this.status = newMap.bake(this.scene);
                                        e.setVisible(false);
                                        e.dispose();

                                        p.setVisible(false);
                                        p.dispose();
                                    },
                                    (e) -> {
                                        e.setVisible(false);
                                        e.dispose();

                                        p.getBakeButton().setEnabled(true);
                                    }
                            );
                        },
                        (p) -> {
                            BakePopup.toScene(this.scene, p);
                        }
                );
            }
        }
        if (key == GLFW_KEY_F5 && action == GLFW_PRESS) {
            boolean reflectionsEnabled = N3DObjectRenderer.REFLECTIONS_ENABLED;
            boolean reflectionsDebug = N3DObjectRenderer.REFLECTIONS_DEBUG;

            N3DObjectRenderer.REFLECTIONS_ENABLED = false;
            N3DObjectRenderer.HDR_OUTPUT = true;
            N3DObjectRenderer.REFLECTIONS_DEBUG = false;

            List<NCubemap> cubemapsList = new ArrayList<>();
            for (int i = 0; i < this.cubemapNames.length; i++) {
                String name = this.cubemapNames[i];
                NCubemapBox info = this.cubemapInfos[i];

                for (int j = 0; j < this.map.getNumberOfObjects(); j++) {
                    N3DObjectRenderer.queueRender(this.map.getObject(j));
                }

                NCubemap cubemap = NCubemapRenderer.render(
                        name,
                        info,
                        1024,
                        4,
                        this.lights,
                        this.cubemaps
                );

                cubemapsList.add(cubemap);
            }

            for (NCubemap cubemap : cubemapsList) {
                try {
                    try (FileOutputStream out = new FileOutputStream(cubemap.getName() + ".cbm")) {
                        NCubemapStore.writeCubemap(cubemap, out);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }

            this.cubemaps = new NCubemaps(this.skybox, cubemapsList);

            N3DObjectRenderer.REFLECTIONS_ENABLED = reflectionsEnabled;
            N3DObjectRenderer.HDR_OUTPUT = false;
            N3DObjectRenderer.REFLECTIONS_DEBUG = reflectionsDebug;
        }
        if (key == GLFW_KEY_F6 && action == GLFW_PRESS) {
            this.ambientCubeDebug = !this.ambientCubeDebug;
        }
        if (key == GLFW_KEY_F7 && action == GLFW_PRESS) {
            this.debugCollision = !this.debugCollision;
        }
        if (key == GLFW_KEY_F8 && action == GLFW_PRESS) {
            N3DObjectRenderer.USE_TONEMAPPING = !N3DObjectRenderer.USE_TONEMAPPING;
        }
        if (key == GLFW_KEY_F && action == GLFW_PRESS) {
            if (this.lights.contains(this.flashlight)) {
                this.lights.remove(this.flashlight);
            } else {
                this.lights.add(this.flashlight);
            }
        }
        if (key == GLFW_KEY_L && action == GLFW_PRESS) {
            if (this.lights.contains(this.lighter)) {
                this.lights.remove(this.lighter);
            } else {
                this.lights.add(this.lighter);
            }
        }
        if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
            this.playerController.jump();
        }
        if (key == GLFW_KEY_V && action == GLFW_PRESS) {
            this.playerController.getCharacterController().setNoclipEnabled(!this.playerController.getCharacterController().isNoclipEnabled());
        }
        if (key == GLFW_KEY_R && action == GLFW_PRESS) {
            System.out.println(this.selector.getPosition().x());
            System.out.println(this.selector.getPosition().y());
            System.out.println(this.selector.getPosition().z());
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
