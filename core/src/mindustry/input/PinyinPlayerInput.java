package mindustry.input;

import arc.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import mindustry.gen.*;

/** Independent controls shared by the two local escape players. */
public class PinyinPlayerInput{
    private static final Vec2 movement = new Vec2();

    public static void update(Unit unit, boolean secondPlayer, Unit target, boolean enabled){
        boolean firing = enabled && Core.input.keyDown(secondPlayer ? KeyCode.mouseLeft : KeyCode.j);
        float dx = enabled ? axis(secondPlayer ? KeyCode.left : KeyCode.a, secondPlayer ? KeyCode.right : KeyCode.d) : 0f;
        float dy = enabled ? axis(secondPlayer ? KeyCode.down : KeyCode.s, secondPlayer ? KeyCode.up : KeyCode.w) : 0f;
        unit.movePref(movement.set(dx, dy).nor().scl(unit.speed()));

        float aimX = unit.x + Angles.trnsx(unit.rotation, 100f);
        float aimY = unit.y + Angles.trnsy(unit.rotation, 100f);
        if(secondPlayer && enabled){
            aimX = Core.input.mouseWorldX();
            aimY = Core.input.mouseWorldY();
        }else if(target != null && firing){
            aimX = target.x;
            aimY = target.y;
        }else if(dx != 0f || dy != 0f){
            aimX = unit.x + dx * 100f;
            aimY = unit.y + dy * 100f;
        }

        if(firing || dx != 0f || dy != 0f) unit.lookAt(aimX, aimY);
        unit.aim(aimX, aimY, true);
        // Set both held and released states every frame. Weapon.update handles the reload loop.
        unit.controlWeapons(firing, firing);
    }

    private static float axis(KeyCode negative, KeyCode positive){
        return (Core.input.keyDown(positive) ? 1f : 0f) - (Core.input.keyDown(negative) ? 1f : 0f);
    }
}
