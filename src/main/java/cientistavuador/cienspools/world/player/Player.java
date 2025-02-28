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
package cientistavuador.cienspools.world.player;

import cientistavuador.cienspools.Main;
import cientistavuador.cienspools.Pipeline;
import cientistavuador.cienspools.audio.AudioNode;
import cientistavuador.cienspools.audio.data.Audio;
import cientistavuador.cienspools.camera.FreeCamera;
import cientistavuador.cienspools.newrendering.NLight;
import cientistavuador.cienspools.newrendering.NMaterialSoundEffects;
import cientistavuador.cienspools.physics.CharacterController;
import cientistavuador.cienspools.physics.PlayerController;
import cientistavuador.cienspools.util.ColorUtils;
import cientistavuador.cienspools.world.World;
import org.joml.Vector3d;
import static org.lwjgl.glfw.GLFW.*;

/**
 *
 * @author Cien
 */
public class Player {

    public static final float FLASHLIGHT_CHARGE = 80f;
    public static final float LIGHTER_CHARGE = 120f;

    public static final float FLASHLIGHT_CHARGE_RATE = 1f;
    public static final float LIGHTER_CHARGE_RATE = 10f;

    public static final float FLASHLIGHT_DISCHARGE_RATE = 2f;
    public static final float LIGHTER_DISCHARGE_RATE = 1f;

    private World world = null;

    private final FreeCamera camera = new FreeCamera();

    {
        this.camera.setMovementDisabled(true);
    }

    private final PlayerController playerController = new PlayerController();

    private final NLight.NSpotLight flashlight = new NLight.NSpotLight("flashlight");

    {
        this.flashlight.setInnerConeAngle(10f);
        this.flashlight.setOuterConeAngle(40f);
        this.flashlight.setDiffuseSpecularAmbient(50f, 10f, 0.1f);
        this.flashlight.setRange(20f);
        this.flashlight.setSize(0.25f);
    }

    private final NLight.NPointLight lighter = new NLight.NPointLight("lighter");

    {
        ColorUtils.setSRGB(this.lighter.getDiffuse(), 233, 140, 80).mul(4f);
        ColorUtils.setSRGB(this.lighter.getSpecular(), 233, 140, 80).mul(0.03f);
        ColorUtils.setSRGB(this.lighter.getAmbient(), 233, 140, 80).mul(0.015f);
    }

    private final AudioNode stepNode = new AudioNode("step node");
    private final AudioNode flashlightNode = new AudioNode("flashlight node");
    private final AudioNode lighterNode = new AudioNode("lighter node");
    private final AudioNode enterExitNode = new AudioNode("player enter exit node");

    private final Vector3d lastStepPosition = new Vector3d(Double.NaN);
    private float stepDistance = 0f;

    private boolean onWater = false;
    private boolean cameraOnWater = false;

    private float flashlightCharge = FLASHLIGHT_CHARGE;
    private float lighterCharge = LIGHTER_CHARGE;

    public Player() {

    }

    public World getWorld() {
        return world;
    }

    public FreeCamera getCamera() {
        return camera;
    }

    public PlayerController getPlayerController() {
        return playerController;
    }

    public void onAddedToWorld(World world) {
        this.world = world;
        this.playerController.getCharacterController().addToPhysicsSpace(world.getPhysicsSpace());
        world.getAudioSpace().addNode(this.stepNode);
        world.getAudioSpace().addNode(this.flashlightNode);
        world.getAudioSpace().addNode(this.lighterNode);
        world.getAudioSpace().addNode(this.enterExitNode);
    }

    public void onRemovedFromWorld(World world) {
        this.world = null;
        this.playerController.getCharacterController().removeFromPhysicsSpace();
        world.getAudioSpace().removeNode(this.stepNode);
        world.getAudioSpace().removeNode(this.flashlightNode);
        world.getAudioSpace().removeNode(this.lighterNode);
        world.getAudioSpace().removeNode(this.enterExitNode);
    }

    public boolean isOnWater() {
        return onWater;
    }

    public void onEnteredWater() {
        this.onWater = true;
        this.enterExitNode.setAudio(NMaterialSoundEffects.RESOURCES
                .get("default/sounds/materials/water").getRandomEnter());
        this.enterExitNode.play();
    }

    public void onExitedWater() {
        this.onWater = false;
        this.enterExitNode.setAudio(NMaterialSoundEffects.RESOURCES
                .get("default/sounds/materials/water").getRandomExit());
        this.enterExitNode.play();
    }

    public boolean isFlashlightOn() {
        return this.world.getLights().contains(this.flashlight);
    }

    public boolean isLighterOn() {
        return this.world.getLights().contains(this.lighter);
    }

    public void setFlashlightOn(boolean on) {
        if (!isFlashlightOn()) {
            if (on) {
                this.world.getLights().add(this.flashlight);
                this.flashlightNode.setAudio(Audio.RESOURCES
                        .get("default/sounds/flashlight/flashlight_on"));
                this.flashlightNode.play();
            }
        } else {
            if (!on) {
                this.world.getLights().remove(this.flashlight);
                this.flashlightNode.setAudio(Audio.RESOURCES
                        .get("default/sounds/flashlight/flashlight_off"));
                this.flashlightNode.play();
            }
        }
    }

    public void setLighterOn(boolean on) {
        if (!isLighterOn()) {
            if (on) {
                this.world.getLights().add(this.lighter);
                this.lighterNode.setAudio(Audio.RESOURCES
                        .get("default/sounds/lighter/lighter_on"));
                this.lighterNode.play();
            }
        } else {
            if (!on) {
                this.world.getLights().remove(this.lighter);
            }
        }
    }

    public float getFlashlightCharge() {
        return flashlightCharge;
    }

    public float getLighterCharge() {
        return lighterCharge;
    }

    public void onCameraEnteredWater() {
        Pipeline.WATER_EFFECT = true;
        this.cameraOnWater = true;
        if (isLighterOn()) {
            setLighterOn(false);
        }
        this.enterExitNode.setAudio(NMaterialSoundEffects.RESOURCES
                .get("default/sounds/materials/water").getRandomEnter());
        this.enterExitNode.play();
    }

    public void onCameraExitedWater() {
        Pipeline.WATER_EFFECT = false;
        this.cameraOnWater = false;
        this.enterExitNode.setAudio(NMaterialSoundEffects.RESOURCES
                .get("default/sounds/materials/water").getRandomExit());
        this.enterExitNode.play();
    }

    public void update(double tpf) {
        CharacterController controller = this.playerController.getCharacterController();
        this.stepNode.getPosition().set(controller.getPosition());
        this.enterExitNode.getPosition().set(controller.getPosition());
        this.flashlightNode.getPosition().set(controller.getPosition());
        this.flashlightNode.getPosition().add(0f, 1f, 0f);
        this.lighterNode.getPosition().set(controller.getPosition());
        this.lighterNode.getPosition().add(0f, 1f, 0f);

        if (!this.lastStepPosition.isFinite()) {
            this.lastStepPosition.set(controller.getPosition());
        } else {
            this.stepDistance += this.lastStepPosition
                    .distance(new Vector3d(controller.getPosition()));
            this.lastStepPosition.set(controller.getPosition());
            if (this.stepDistance > 1.5f) {
                if (isOnWater()) {
                    this.stepNode.setAudio(NMaterialSoundEffects.RESOURCES
                            .get("default/sounds/materials/water").getRandomFootstep());
                } else {
                    this.stepNode.setAudio(NMaterialSoundEffects.RESOURCES
                            .get("default/sounds/materials/stone").getRandomFootstep());
                }
                this.stepNode.play();
                this.stepDistance = 0f;
            }
        }

        if (controller.getPosition().y() < -10f) {
            controller.setPosition(0f, 0.1f, 0f);
        }

        this.flashlight.getPosition().set(this.camera.getPosition());
        this.flashlight.getDirection().set(this.camera.getFront()).add(0f, -0.15f, 0f).normalize();

        this.lighter.getPosition().set(this.camera.getRight()).negate()
                .mul(0.05f).add(this.camera.getPosition());

        if (!isLighterOn()) {
            this.lighterCharge += tpf * LIGHTER_CHARGE_RATE;
            if (this.lighterCharge > LIGHTER_CHARGE) {
                this.lighterCharge = LIGHTER_CHARGE;
            }
        } else {
            this.lighterCharge -= tpf * LIGHTER_DISCHARGE_RATE;
            if (this.lighterCharge < 0f) {
                this.lighterCharge = 0f;
                setLighterOn(false);
            }
        }

        if (!isFlashlightOn()) {
            this.flashlightCharge += tpf * FLASHLIGHT_CHARGE_RATE;
            if (this.flashlightCharge > FLASHLIGHT_CHARGE) {
                this.flashlightCharge = FLASHLIGHT_CHARGE;
            }
        } else {
            this.flashlightCharge -= tpf * FLASHLIGHT_DISCHARGE_RATE;
            if (this.flashlightCharge < 0f) {
                this.flashlightCharge = 0f;
                setFlashlightOn(false);
            }
        }
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_F && action == GLFW_PRESS) {
            setFlashlightOn(!isFlashlightOn());
        }
        if (key == GLFW_KEY_L && action == GLFW_PRESS) {
            if (!this.cameraOnWater) {
                setLighterOn(!isLighterOn());
            }
        }
        if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
            this.playerController.jump();
        }
        if (key == GLFW_KEY_V && action == GLFW_PRESS) {
            this.playerController.getCharacterController()
                    .setNoclipEnabled(!this.playerController.getCharacterController().isNoclipEnabled());
        }
    }

}
