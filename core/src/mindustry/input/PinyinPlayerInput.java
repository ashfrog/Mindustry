package mindustry.input;

import arc.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import mindustry.gen.*;

/** Independent controls shared by the two local escape players. */
public class PinyinPlayerInput{
    private static final Vec2 movement = new Vec2();
    private static boolean controllerAim;

    public static void update(Unit unit, boolean secondPlayer, Position target, boolean enabled){
        update(unit, secondPlayer, target, enabled, true);
    }

    public static void update(Unit unit, boolean secondPlayer, Position target, boolean enabled, boolean mouseAllowed){
        PinyinGamepad.State pad = PinyinGamepad.state;
        boolean padActive = secondPlayer && pad.connected && (!pad.move.isZero() || !pad.aim.isZero() || pad.fire || pad.dashTap);
        boolean firing = enabled && ((secondPlayer ? mouseAllowed && Core.input.keyDown(KeyCode.mouseLeft) : Core.input.keyDown(KeyCode.j)) || secondPlayer && pad.connected && pad.fire);
        float dx = enabled ? axis(secondPlayer ? KeyCode.left : KeyCode.a, secondPlayer ? KeyCode.right : KeyCode.d) : 0f;
        float dy = enabled ? axis(secondPlayer ? KeyCode.down : KeyCode.s, secondPlayer ? KeyCode.up : KeyCode.w) : 0f;
        if(secondPlayer){
            if(!pad.connected || (!padActive && (dx != 0f || dy != 0f || Math.abs(Core.input.deltaX()) + Math.abs(Core.input.deltaY()) > 2))) controllerAim = false;
            if(padActive) controllerAim = true;
            if(enabled && pad.connected){ dx += pad.move.x; dy += pad.move.y; }
        }
        unit.movePref(movement.set(dx, dy).limit(1f).scl(unit.speed()));

        float aimX = unit.x + Angles.trnsx(unit.rotation, 100f);
        float aimY = unit.y + Angles.trnsy(unit.rotation, 100f);
        if(secondPlayer && controllerAim && enabled){
            if(!pad.aim.isZero()){
                aimX = unit.x + pad.aim.x * 160f;
                aimY = unit.y + pad.aim.y * 160f;
            }else if(target != null && firing){
                aimX = target.getX();
                aimY = target.getY();
            }else if(dx != 0f || dy != 0f){
                aimX = unit.x + dx * 100f;
                aimY = unit.y + dy * 100f;
            }
        }else if(secondPlayer && enabled && mouseAllowed){
            aimX = Core.input.mouseWorldX();
            aimY = Core.input.mouseWorldY();
        }else if(target != null && firing){
            aimX = target.getX();
            aimY = target.getY();
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
