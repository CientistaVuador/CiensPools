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

import cientistavuador.cienspools.audio.AudioNode;
import cientistavuador.cienspools.audio.data.Audio;
import cientistavuador.cienspools.debug.AabRender;
import cientistavuador.cienspools.debug.LineRender;
import cientistavuador.cienspools.editor.Gizmo;
import cientistavuador.cienspools.newrendering.N3DModel;
import cientistavuador.cienspools.newrendering.N3DObject;
import cientistavuador.cienspools.newrendering.NCubemap;
import cientistavuador.cienspools.newrendering.NCubemapBox;
import cientistavuador.cienspools.newrendering.NCubemapRenderer;
import cientistavuador.cienspools.newrendering.NCubemapStore;
import cientistavuador.cienspools.newrendering.NCubemaps;
import cientistavuador.cienspools.newrendering.NGeometry;
import cientistavuador.cienspools.newrendering.NLight;
import cientistavuador.cienspools.newrendering.NLightmaps;
import cientistavuador.cienspools.newrendering.NLightmapsStore;
import cientistavuador.cienspools.newrendering.NMap;
import cientistavuador.cienspools.newrendering.NMaterial;
import cientistavuador.cienspools.newrendering.NTextures;
import cientistavuador.cienspools.popups.BakePopup;
import cientistavuador.cienspools.popups.ContinuePopup;
import cientistavuador.cienspools.text.GLFontRenderer;
import cientistavuador.cienspools.text.GLFontSpecifications;
import cientistavuador.cienspools.ubo.CameraUBO;
import cientistavuador.cienspools.ubo.UBOBindingPoints;
import cientistavuador.cienspools.util.DebugRenderer;
import cientistavuador.cienspools.util.StringUtils;
import cientistavuador.cienspools.util.bakedlighting.AmbientCubeDebug;
import cientistavuador.cienspools.util.bakedlighting.Lightmapper;
import cientistavuador.cienspools.util.bakedlighting.Scene;
import cientistavuador.cienspools.world.World;
import cientistavuador.cienspools.world.player.Player;
import cientistavuador.cienspools.world.trigger.water.WaterTrigger;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.simsilica.mathd.Vec3d;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.joml.Quaternionf;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class Game {

    private static final Game GAME = new Game();

    public static Game get() {
        return GAME;
    }

    private NMap.BakeStatus status = null;

    private final NCubemap skybox;
    private final String[] cubemapNames = {
        "spawn",
        "spawn_corridor",
        "big_pool",
        "big_pool_refraction",
        "big_pool_corridor",
        "exit",
        "exit_refraction",
        "exit_corridor"
    };
    private final NCubemapBox[] cubemapInfos = {
        new NCubemapBox(4.27, 4.96, 12.93, 4.27, 4.96, 12.93, 0.0f, 0.0f, 0.0f, 1.0f, 15.5f, 5.04f, 5.14f),
        new NCubemapBox(4.17, 5.0, 6.5, 4.17, 5.0, 6.5, 0.0f, 0.0f, 0.0f, 1.0f, 15.5f, 5.1f, 1.41f),
        new NCubemapBox(4.68, 2.89, -0.68, -11.06, -0.15, -7.09, 20.47, 10.10, 5.06),
        new NCubemapBox(3.7, -0.53, -1.06, 3.7, -0.53, -1.06, 0.0f, 0.0f, 0.0f, 1.0f, 6.0f, 0.5f, 4.14f),
        new NCubemapBox(4.28, 5.0, -8.6, 4.28, 5.0, -8.6, 0.0f, 0.0f, 0.0f, 1.0f, 15.41f, 5.14f, 1.26f),
        new NCubemapBox(4.25, 5.02, -14.76, 4.25, 5.02, -14.76, 0.0f, 0.0f, 0.0f, 1.0f, 15.45f, 5.2f, 4.88f),
        new NCubemapBox(4.26, -0.56, -17.58, 4.26, -0.56, -17.58, 0.0f, 0.0f, 0.0f, 1.0f, 15.39f, 0.45f, 1.53f),
        new NCubemapBox(-15.21, 2.53, -13.18, -15.21, 2.53, -13.18, 0.0f, 0.0f, 0.0f, 1.0f, 4.06f, 2.59f, 1.13f)
    };

    private NCubemaps cubemaps;

    private NMap nextMap;

    private final World world = new World();
    private final Player player = new Player();

    private final Gizmo gizmo = new Gizmo();

    private final N3DModel boomBoxModel;
    private final N3DObject acUnit;

    private final Scene scene = new Scene();

    private boolean ambientCubeDebug = false;
    private boolean debugCollision = false;

    {
        this.player.getPlayerController().getCharacterController().setPosition(16.72f, 0f, 12.76f);

        NLight.NDirectionalLight sun = new NLight.NDirectionalLight("sun");
        sun.getDiffuse().set(20f);
        sun.getSpecular().set(3f);
        sun.getAmbient().set(0.1f);
        sun.setDynamic(false);
        sun.getDirection().set(-0.5f, -0.75f, -0.45f).normalize();
        this.world.getLights().add(sun);

        try {
            this.skybox = NCubemapStore
                    .readCubemap("cientistavuador/cienspools/resources/cubemaps/skybox.cbm");

            List<N3DObject> mapObjects = new ArrayList<>();
            {
                N3DModel roomModel = N3DModel.RESOURCES.get("[031E114E9B854953|9E778413412CA407]Surface");
                N3DObject room = new N3DObject("room", roomModel);
                mapObjects.add(room);
            }

            NMap map = new NMap("map", mapObjects, NMap.DEFAULT_LIGHTMAP_MARGIN, 45f);
            map.setLightmaps(NLightmapsStore
                    .readLightmaps("cientistavuador/cienspools/resources/lightmaps/lightmap.lit"));
            
            N3DModel mainModel = map.getObject(0).getN3DModel();
            for (int i = 0; i < mainModel.getNumberOfGeometries(); i++) {
                NGeometry geo = mainModel.getGeometry(i);
                if (geo.getMaterial().equals(NMaterial.RESOURCES.get("Water"))) {
                    geo.setFaceCullingEnabled(false);
                }
            }
            
            this.world.setMap(map);

            {
                this.boomBoxModel = N3DModel.RESOURCES.get("[D48EAA8D455A4B57|A34C2F1CE3B5D2C7]BoomBox");

                this.acUnit = new N3DObject("AC Unit", this.boomBoxModel);
                this.gizmo.getExtents().set(this.boomBoxModel.getAabbExtents());
                this.world.addObject(this.acUnit);
            }

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        for (NLight light : this.world.getLights()) {
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
        this.world.getMap().setCubemaps(this.cubemaps);
    }

    private Game() {

    }

    public void start() {
        Audio ambient = Audio.RESOURCES.get("default/sounds/ambient/beach_ambient");
        AudioNode ambientNode = new AudioNode("ambientNode");
        ambientNode.setAudio(ambient);
        ambientNode.setLooping(true);
        ambientNode.setGain(0.1f);
        ambientNode.play();
        this.world.getAudioSpace().addNode(ambientNode);

        WaterTrigger a = new WaterTrigger("water 0");
        a.setTransformation(
                3.76, -0.59, -1.03,
                6.12f, 0.49f, 4.08f,
                0.0f, 0.0f, 0.0f, 1.0f
        );
        this.world.addWorldObject(a);

        this.gizmo.setCamera(this.player.getCamera());
        this.player.getCamera().setUBO(CameraUBO.create(UBOBindingPoints.PLAYER_CAMERA));
        this.world.setPlayer(this.player);

        NTextures.ERROR_TEXTURE.textures();
        NCubemap.NULL_CUBEMAP.cubemap();
        NLightmaps.NULL_LIGHTMAPS.lightmaps();

        for (N3DObject obj : this.world.getObjects()) {
            obj.getN3DModel().load();
        }

        this.skybox.cubemap();

        for (int i = 0; i < this.cubemaps.getNumberOfCubemaps(); i++) {
            this.cubemaps.getCubemap(i).cubemap();
        }

        alDistanceModel(AL_LINEAR_DISTANCE_CLAMPED);

        System.gc();
    }

    public void loop() {
        this.world.update(Main.TPF);
        this.player.update(Main.TPF);

        if (this.nextMap != null) {
            this.world.setMap(this.nextMap);
            this.nextMap = null;
        }

        if (this.world.getMap().getLightmaps() != null && this.ambientCubeDebug) {
            AmbientCubeDebug.render(
                    this.world.getMap()
                            .getLightmaps()
                            .getAmbientCubes()
                            .getAmbientCubes(),
                    this.player.getCamera().getProjection(),
                    this.player.getCamera().getView(),
                    this.player.getCamera().getPosition()
            );
        }

        if (this.debugCollision) {
            this.world.getPhysicsSpaceDebugger().pushToDebugRenderer(
                    this.player.getCamera().getProjection(),
                    this.player.getCamera().getView(),
                    this.player.getCamera().getPosition());
        }

        this.acUnit.getScale().set(this.gizmo.getScale());
        this.gizmo.rotate(this.acUnit.getRotation().identity());
        this.acUnit.getPosition().set(this.gizmo.getPosition());

        this.world.prepareRender();
        this.world.renderOpaqueAlphaTested();

        AabRender.renderQueue(this.player.getCamera());
        LineRender.renderQueue(this.player.getCamera());
        DebugRenderer.render();

        this.world.renderAlpha();

        glClear(GL_DEPTH_BUFFER_BIT);
        if (this.gizmo != null) {
            this.gizmo.render();
        }

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
                        NLightmapsStore.writeLightmaps(this.world.getMap().getLightmaps(), out);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
                this.status = null;
            }
        }

        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + String.format("%,.2f", this.player.getCamera().getPosition().x()) + ",y:" + String.format("%,.2f", this.player.getCamera().getPosition().y()) + ",z:" + String.format("%,.2f", this.player.getCamera().getPosition().z()) + ")";
        Main.WINDOW_TITLE += " (dx:" + String.format("%,.2f", this.player.getCamera().getFront().x()) + ",dy:" + String.format("%,.2f", this.player.getCamera().getFront().y()) + ",dz:" + String.format("%,.2f", this.player.getCamera().getFront().z()) + ")";
        Main.WINDOW_TITLE += " (p:" + String.format("%,.2f", this.player.getCamera().getRotation().x()) + ",y:" + String.format("%,.2f", this.player.getCamera().getRotation().y()) + ",r:" + String.format("%,.2f", this.player.getCamera().getRotation().z()) + ")";
    }

    public void mouseCursorMoved(double x, double y) {
        this.player.getCamera().mouseCursorMoved(x, y);
    }

    public void mouseCursorMovedNormalized(float normalizedX, float normalizedZ) {
        if (this.gizmo != null) {
            this.gizmo.onMouseCursorMoved(normalizedX, normalizedZ);
        }
    }

    public void windowSizeChanged(int width, int height) {
        this.player.getCamera().setDimensions(width, height);
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        this.player.keyCallback(window, key, scancode, action, mods);
        if (key == GLFW_KEY_B && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
            N3DObject boomBox = new N3DObject("boomBox", this.boomBoxModel);
            boomBox.getScale().set(40f);
            this.boomBoxModel.getHullCollisionShape().setScale(40f);
            this.world.addObject(boomBox);

            HullCollisionShape hull = this.boomBoxModel.getHullCollisionShape();
            com.jme3.math.Vector3f center = hull.aabbCenter(null).negate();
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
            rigidBody.applyCentralImpulse(new com.jme3.math.Vector3f(
                    this.player.getCamera().getFront().x() * 5f * Main.TO_PHYSICS_ENGINE_UNITS * rigidBody.getMass(),
                    this.player.getCamera().getFront().y() * 5f * Main.TO_PHYSICS_ENGINE_UNITS * rigidBody.getMass(),
                    this.player.getCamera().getFront().z() * 5f * Main.TO_PHYSICS_ENGINE_UNITS * rigidBody.getMass()
            ));
            rigidBody.setPhysicsLocationDp(new Vec3d(
                    this.player.getCamera().getPosition().x() * Main.TO_PHYSICS_ENGINE_UNITS,
                    this.player.getCamera().getPosition().y() * Main.TO_PHYSICS_ENGINE_UNITS,
                    this.player.getCamera().getPosition().z() * Main.TO_PHYSICS_ENGINE_UNITS
            ));
            rigidBody.setProtectGravity(true);
            rigidBody.setGravity(new com.jme3.math.Vector3f(0f, -9.8f, 0f));
            boomBox.setRigidBody(rigidBody);
            this.world.getPhysicsSpace().addCollisionObject(rigidBody);
        }
        if (key == GLFW_KEY_F1 && action == GLFW_PRESS) {
            this.world.getRenderer().setReflectionsEnabled(!this.world.getRenderer().isReflectionsEnabled());
        }
        if (key == GLFW_KEY_F2 && action == GLFW_PRESS) {
            this.world.getRenderer().setParallaxEnabled(!this.world.getRenderer().isParallaxEnabled());
        }
        if (key == GLFW_KEY_F3 && action == GLFW_PRESS) {
            this.world.getRenderer().setReflectionsDebugEnabled(!this.world.getRenderer().isReflectionsDebugEnabled());
        }
        if (key == GLFW_KEY_F4 && action == GLFW_PRESS) {
            bake:
            {
                if (this.status != null && !this.status.getTask().isDone()) {
                    break bake;
                }

                if (this.player.getCamera().isCaptureMouse()) {
                    this.player.getCamera().pressEscape();
                }

                BakePopup.show(
                        (p) -> {
                            BakePopup.fromScene(this.scene, p);
                        },
                        (p) -> {
                            p.getBakeButton().setEnabled(false);

                            BakePopup.toScene(this.scene, p);

                            List<N3DObject> list = new ArrayList<>();
                            for (int i = 0; i < this.world.getMap().getNumberOfObjects(); i++) {
                                list.add(this.world.getMap().getObject(i));
                            }

                            final NMap newMap = new NMap(
                                    this.world.getMap().getName(),
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
            List<NCubemap> cubemapsList = new ArrayList<>();
            for (int i = 0; i < this.cubemapNames.length; i++) {
                String name = this.cubemapNames[i];
                NCubemapBox info = this.cubemapInfos[i];

                NCubemap cubemap = NCubemapRenderer.render(
                        this.world.getRenderer(),
                        name,
                        info,
                        1024,
                        4
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
            this.world.getMap().setCubemaps(this.cubemaps);
        }
        if (key == GLFW_KEY_F6 && action == GLFW_PRESS) {
            this.ambientCubeDebug = !this.ambientCubeDebug;
        }
        if (key == GLFW_KEY_F7 && action == GLFW_PRESS) {
            this.debugCollision = !this.debugCollision;
        }
        if (key == GLFW_KEY_F8 && action == GLFW_PRESS) {
            Pipeline.USE_FXAA = !Pipeline.USE_FXAA;
        }
        if (key == GLFW_KEY_F9 && action == GLFW_PRESS) {
            Pipeline.USE_MSAA = !Pipeline.USE_MSAA;
        }
        if (key == GLFW_KEY_R && action == GLFW_PRESS) {
            Quaternionf rotation = new Quaternionf();
            this.gizmo.rotate(rotation);
            System.out.print("new NCubemapBox(");
            System.out.print(this.gizmo.getPosition().x() + ", " + this.gizmo.getPosition().y() + ", " + this.gizmo.getPosition().z() + ", ");
            System.out.print(this.gizmo.getPosition().x() + ", " + this.gizmo.getPosition().y() + ", " + this.gizmo.getPosition().z() + ", ");
            System.out.print(rotation.x() + "f, " + rotation.y() + "f, " + rotation.z() + "f, " + rotation.w() + "f, ");
            System.out.print(this.gizmo.getScale().x() * 0.5f + "f, " + this.gizmo.getScale().y() * 0.5f + "f, " + this.gizmo.getScale().z() * 0.5f + "f");
            System.out.println(")");
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {
        if (this.gizmo != null) {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    this.gizmo.onLeftClick(Main.MOUSE_X, Main.MOUSE_Y);
                }
                if (action == GLFW_RELEASE) {
                    this.gizmo.onLeftClickRelease(Main.MOUSE_X, Main.MOUSE_Y);
                }
            }
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                if (action == GLFW_PRESS) {
                    this.gizmo.onRightClick(Main.MOUSE_X, Main.MOUSE_Y);
                }
                if (action == GLFW_RELEASE) {
                    this.gizmo.onRightClickRelease(Main.MOUSE_X, Main.MOUSE_Y);
                }
            }
            if (button == GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW_PRESS) {
                this.gizmo.onMiddleClick(Main.MOUSE_X, Main.MOUSE_Y);
            }
        }
    }
}
