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
package cientistavuador.cienspools.world.trigger.water;

import cientistavuador.cienspools.world.World;
import cientistavuador.cienspools.world.WorldEntity;
import cientistavuador.cienspools.world.player.Player;
import cientistavuador.cienspools.world.trigger.EnterExitTrigger;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;

/**
 *
 * @author Cien
 */
public class WaterTrigger extends EnterExitTrigger implements WorldEntity {
    
    public WaterTrigger(String name) {
        super(name);
    }

    @Override
    public void onEnter(PhysicsSpace space, float timestep, PhysicsRigidBody body) {
        Player player = getWorld().getPlayer();
        if (player != null) {
            if (body.equals(player.getPlayerController().getCharacterController().getRigidBody())) {
                player.onEnteredWater();
            }
        }
    }

    @Override
    public void onExit(PhysicsSpace space, float timestep, PhysicsRigidBody body) {
        Player player = getWorld().getPlayer();
        if (player != null) {
            if (body.equals(player.getPlayerController().getCharacterController().getRigidBody())) {
                player.onExitedWater();
            }
        }
    }

    @Override
    public void onWorldUpdate(World world, double tpf) {
        Player player = world.getPlayer();
        if (player != null) {
            if (isPointInside(player.getCamera().getPosition())) {
                //System.out.println("inside!");
            } else {
                //System.out.println("not inside!");
            }
        }
    }
    
}
