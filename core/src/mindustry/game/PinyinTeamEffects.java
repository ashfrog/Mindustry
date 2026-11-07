package mindustry.game;

import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.gen.*;
import mindustry.type.*;

/** Team answer effects, retained while a teammate waits to respawn. */
public class PinyinTeamEffects{
    private int power;
    private float fastTime, overclockTime, slowTime, shieldedTime;

    public void reset(){
        power = 0;
        fastTime = overclockTime = slowTime = shieldedTime = 0f;
    }

    public int power(){
        return power;
    }

    public void update(float delta){
        fastTime = Math.max(0f, fastTime - delta);
        overclockTime = Math.max(0f, overclockTime - delta);
        slowTime = Math.max(0f, slowTime - delta);
        shieldedTime = Math.max(0f, shieldedTime - delta);
    }

    public void correct(int energy, boolean shieldReward, Unit... units){
        power = Math.min(5, power + 1);
        if(energy == 1) fastTime = 60f * 20f;
        if(energy == 2) overclockTime = 60f * 20f;
        if(shieldReward) shieldedTime = 60f * 6f;
        for(Unit unit : units){
            if(unit == null || unit.dead) continue;
            inherit(unit);
            if(energy >= 3) unit.shield += 80f;
        }
    }

    public void wrong(Unit... units){
        power = Math.max(-3, power - 1);
        slowTime = 60f * 4f;
        for(Unit unit : units){
            if(unit == null || unit.dead) continue;
            inherit(unit);
            unit.damage(12f);
        }
    }

    public void inherit(Unit unit){
        if(fastTime > 0f) unit.apply(StatusEffects.fast, fastTime);
        if(overclockTime > 0f) unit.apply(StatusEffects.overclock, overclockTime);
        if(slowTime > 0f) unit.apply(StatusEffects.slow, slowTime);
        if(shieldedTime > 0f) unit.apply(StatusEffects.shielded, shieldedTime);
        for(int i = 0; i < unit.mounts.length; i++){
            var previous = unit.mounts[i];
            Weapon base = unit.type.weapons.get(i), weapon = base.copy();
            weapon.bullet = base.bullet.copy();
            weapon.shoot = base.shoot.copy();
            weapon.bullet.damage *= Math.max(0.35f, 1f + power * 0.3f);
            weapon.bullet.speed *= Math.max(0.7f, 1f + power * 0.08f);
            weapon.reload *= power >= 0 ? 1f / (1f + power * 0.12f) : 1f - power * 0.2f;
            weapon.inaccuracy += Math.max(0, power - 2) * 3f;
            weapon.shoot.shots += Math.max(0, power - 1);
            if(power >= 4) weapon.shoot = new ShootSpread(weapon.shoot.shots, 9f);
            if(weapon.bullet instanceof BasicBulletType basic){
                float size = Math.max(0.55f, 1f + power * 0.22f);
                basic.width *= size;
                basic.height *= size;
            }
            // Keep content definitions immutable: both mirrored guns get their own copies.
            var mount = weapon.mountType.get(weapon);
            mount.reload = Math.min(previous.reload, weapon.reload);
            mount.rotation = previous.rotation;
            mount.side = previous.side;
            mount.aimX = previous.aimX;
            mount.aimY = previous.aimY;
            mount.totalShots = previous.totalShots;
            mount.shoot = previous.shoot;
            mount.rotate = previous.rotate;
            unit.mounts[i] = mount;
        }
    }
}
