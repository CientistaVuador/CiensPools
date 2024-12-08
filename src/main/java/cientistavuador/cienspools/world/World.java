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
package cientistavuador.cienspools.world;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.audio.AudioSpace;
import cientistavuador.cienspools.camera.Camera;
import cientistavuador.cienspools.newrendering.N3DObject;
import cientistavuador.cienspools.newrendering.N3DObjectRenderer;
import cientistavuador.cienspools.newrendering.NLight;
import cientistavuador.cienspools.newrendering.NMap;
import cientistavuador.cienspools.util.PhysicsSpaceDebugger;
import cientistavuador.cienspools.world.player.Player;
import cientistavuador.cienspools.world.trigger.TriggerController;
import com.jme3.bullet.PhysicsSpace;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Cien
 */
public class World {

    private final PhysicsSpace physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);

    {
        this.physicsSpace.setAccuracy(1f / 480f);
        this.physicsSpace.setMaxSubSteps(16);
        this.physicsSpace.setGravity(
                new com.jme3.math.Vector3f(0f, -9.8f * Main.TO_PHYSICS_ENGINE_UNITS, 0f));
    }

    private final TriggerController triggerController = new TriggerController();
    
    {
        this.physicsSpace.addTickListener(this.triggerController);
    }
    
    private final PhysicsSpaceDebugger physicsSpaceDebugger = new PhysicsSpaceDebugger(this.physicsSpace);
    private final AudioSpace audioSpace = new AudioSpace();
    private final Set<NLight> lights = new HashSet<>();
    private final Set<N3DObject> objects = new HashSet<>();
    private final N3DObjectRenderer renderer = new N3DObjectRenderer();
    private final Set<WorldObject> worldObjects = new HashSet<>();
    private final Set<WorldEntity> worldEntities = new HashSet<>();

    private NMap map = null;
    private Player player = null;

    public World() {
        
    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }

    public TriggerController getTriggerController() {
        return triggerController;
    }

    public PhysicsSpaceDebugger getPhysicsSpaceDebugger() {
        return physicsSpaceDebugger;
    }

    public AudioSpace getAudioSpace() {
        return audioSpace;
    }

    public Set<NLight> getLights() {
        return lights;
    }

    public Set<N3DObject> getObjects() {
        return Collections.unmodifiableSet(this.objects);
    }

    public N3DObjectRenderer getRenderer() {
        return renderer;
    }

    public Set<WorldObject> getWorldObjects() {
        return Collections.unmodifiableSet(worldObjects);
    }
    
    public Set<WorldObject> getWorldEntities() {
        return Collections.unmodifiableSet(worldEntities);
    }
    
    public boolean addWorldObject(WorldObject obj) {
        if (obj == null) {
            return false;
        }
        boolean success = this.worldObjects.add(obj);
        if (success) {
            if (obj instanceof WorldEntity e) {
                this.worldEntities.add(e);
            }
            obj.onAddedToWorld(this);
        }
        return success;
    }
    
    public boolean removeWorldObject(WorldObject obj) {
        if (obj == null) {
            return false;
        }
        boolean success = this.worldObjects.remove(obj);
        if (success) {
            if (obj instanceof WorldEntity e) {
                this.worldEntities.remove(e);
            }
            obj.onRemovedFromWorld(this);
        }
        return success;
    }

    public NMap getMap() {
        return map;
    }

    public void setMap(NMap map) {
        NMap currentMap = getMap();
        if (currentMap != null) {
            for (int i = 0; i < currentMap.getNumberOfObjects(); i++) {
                this.objects.remove(currentMap.getObject(i));
            }
            for (N3DObject obj : getObjects()) {
                obj.setMap(null);
            }
            this.physicsSpace.removeCollisionObject(currentMap.getRigidBody());
        }

        if (map != null) {
            for (int i = 0; i < map.getNumberOfObjects(); i++) {
                this.objects.add(map.getObject(i));
            }
            for (N3DObject obj : getObjects()) {
                obj.setMap(map);
            }
            this.physicsSpace.addCollisionObject(map.getRigidBody());
        }
        this.map = map;
    }

    public boolean addObject(N3DObject object) {
        if (object == null) {
            return false;
        }

        boolean success = this.objects.add(object);
        if (success) {
            object.setMap(getMap());
        }
        return success;
    }

    public boolean removeObject(N3DObject object) {
        if (object == null) {
            return false;
        }

        boolean success = this.objects.remove(object);
        if (success) {
            object.setMap(null);
        }
        return success;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        if (this.player != null) {
            this.player.onRemovedFromWorld(this);
        }
        this.player = player;
        if (this.player != null) {
            this.player.onAddedToWorld(this);
        }
    }

    public void update(double tpf) {
        for (WorldEntity e:this.worldEntities) {
            e.onWorldUpdate(this, tpf);
        }
        
        if (this.player != null) {
            this.audioSpace.getListenerPosition().set(this.player.getCamera().getPosition());
            this.audioSpace.getListenerUp().set(this.player.getCamera().getUp());
            this.audioSpace.getListenerFront().set(this.player.getCamera().getFront());

            this.player.getCamera().updateMovement();
            this.player.getCamera().updateUBO();

            this.player.getPlayerController().update(
                    this.player.getCamera().getFront(),
                    this.player.getCamera().getRight());

            this.player.getCamera().setPosition(
                    this.player.getPlayerController().getEyePosition().x(),
                    this.player.getPlayerController().getEyePosition().y(),
                    this.player.getPlayerController().getEyePosition().z()
            );
        }
        this.audioSpace.update(Main.TPF);
        this.physicsSpace.update((float) Main.TPF);
    }

    public void prepareRender(Camera camera) {
        this.renderer.setCamera(camera);
        this.renderer.getObjects().clear();
        this.renderer.getLights().clear();

        this.renderer.getObjects().addAll(getObjects());
        this.renderer.getLights().addAll(getLights());
        if (this.map == null) {
            this.renderer.setCubemaps(null);
        } else {
            this.renderer.setCubemaps(this.map.getCubemaps());
        }

        this.renderer.prepare();
    }
    
    public void prepareRender() {
        prepareRender(this.player.getCamera());
    }
    
    public void renderOpaqueAlphaTested() {
        this.renderer.renderOpaque();
        this.renderer.renderAlphaTested();
        this.renderer.renderSkybox();
    }

    public void renderAlpha() {
        this.renderer.renderAlphaBlending();
    }

}
