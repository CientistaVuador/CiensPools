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
package cientistavuador.cienspools.camera;

import cientistavuador.cienspools.Main;
import static org.lwjgl.glfw.GLFW.*;

/**
 *
 * @author Shinoa Hiragi
 * @author Cien
 */
public class FreeCamera extends PerspectiveCamera {

    public static final float DEFAULT_SENSITIVITY = 0.1f;
    public static final float DEFAULT_SPEED = 4.5f;
    public static final float DEFAULT_RUN_SPEED_FACTOR = 4f;
    public static final float DEFAULT_CROUCH_SPEED_FACTOR = 0.25f;

    private float sensitivity = DEFAULT_SENSITIVITY;
    private float speed = DEFAULT_SPEED;
    private float runSpeedFactor = DEFAULT_RUN_SPEED_FACTOR;
    private float crouchSpeedFactor = DEFAULT_CROUCH_SPEED_FACTOR;

    //whatever it should capture the cursor or not.
    // press LeftControl in game to capture/release the cursor
    private boolean captureMouse = false;
    private boolean controlAlreadyPressed = false;

    //Last mouse position
    private double lastX = 0;
    private double lastY = 0;

    //Movement control
    private boolean movementDisabled = false;

    private boolean escapeOverride = false;

    public FreeCamera() {

    }

    //movimentation magic
    public void updateMovement() {
        if (isControlPressedOnce() || this.escapeOverride) {
            if (this.escapeOverride) {
                this.escapeOverride = false;
            }
            this.captureMouse = !this.captureMouse;
            // if true, disable cursor,
            // if false, set cursor to normal
            glfwSetInputMode(Main.WINDOW_POINTER, GLFW_CURSOR,
                    this.captureMouse ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
            System.out.println("Free Camera capture state: " + this.captureMouse);
        }

        if (isMovementDisabled()) {
            return;
        }

        int directionX = 0;
        int directionZ = 0;

        if (isKeyDown(GLFW_KEY_W)) {
            directionZ += 1;
        }
        if (isKeyDown(GLFW_KEY_S)) {
            directionZ += -1;
        }
        if (isKeyDown(GLFW_KEY_A)) {
            directionX += -1;
        }
        if (isKeyDown(GLFW_KEY_D)) {
            directionX += 1;
        }

        float diagonal = (Math.abs(directionX) == 1 && Math.abs(directionZ) == 1) ? 0.707106781186f : 1f;
        float currentSpeed = getSpeed();
        if (isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
            currentSpeed *= getRunSpeedFactor();
        }
        if (isKeyDown(GLFW_KEY_LEFT_ALT)) {
            currentSpeed *= getCrouchSpeedFactor();
        }

        //acceleration in X and Z axis
        float xa = currentSpeed * diagonal * directionX;
        float za = currentSpeed * diagonal * directionZ;

        setPosition(
                getPosition().x() + ((getRight().x() * xa + getFront().x() * za) * Main.TPF),
                getPosition().y() + ((getRight().y() * xa + getFront().y() * za) * Main.TPF),
                getPosition().z() + ((getRight().z() * xa + getFront().z() * za) * Main.TPF)
        );
    }

    // rotates camera using the cursor's position
    public void mouseCursorMoved(double mx, double my) {
        if (captureMouse) {
            double x = lastX - mx;
            double y = lastY - my;

            float pitch = getRotation().x() + (float) (y * sensitivity);
            float yaw = getRotation().y() + (float) (x * -sensitivity);

            pitch = Math.min(Math.max(pitch, -89f), 89f);
            yaw = yaw % 360f;

            setPitchYaw(pitch, yaw);
        }
        lastX = mx;
        lastY = my;
    }

    //returns true if the key was pressed
    private boolean isKeyDown(int key) {
        return glfwGetKey(Main.WINDOW_POINTER, key) == GLFW_PRESS;
    }

    // returns true if the key wasn't pressed
    private boolean isKeyUp(int key) {
        return glfwGetKey(Main.WINDOW_POINTER, key) == GLFW_RELEASE;
    }

    /**
     * May be a little of Overengineering by me, but, here's the idea: it only returns true one time if the left control key is pressed, it won't return true again until that key is released; and pressed again,
     */
    private boolean isControlPressedOnce() {
        if (isKeyDown(GLFW_KEY_ESCAPE)) {
            if (!controlAlreadyPressed) {
                controlAlreadyPressed = true;
                return true;
            }
            return false;
        }
        if (isKeyUp(GLFW_KEY_ESCAPE)) {
            controlAlreadyPressed = false;
        }
        return false;
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
    }

    public float getSensitivity() {
        return sensitivity;
    }
    
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getSpeed() {
        return speed;
    }
    
    public float getRunSpeedFactor() {
        return runSpeedFactor;
    }

    public void setRunSpeedFactor(float runSpeedFactor) {
        this.runSpeedFactor = runSpeedFactor;
    }

    public float getCrouchSpeedFactor() {
        return crouchSpeedFactor;
    }

    public void setCrouchSpeedFactor(float crouchSpeedFactor) {
        this.crouchSpeedFactor = crouchSpeedFactor;
    }

    public boolean isMovementDisabled() {
        return movementDisabled;
    }

    public void setMovementDisabled(boolean movementDisabled) {
        this.movementDisabled = movementDisabled;
    }

    public boolean isCaptureMouse() {
        return captureMouse;
    }

    public void pressEscape() {
        this.escapeOverride = true;
    }

}
