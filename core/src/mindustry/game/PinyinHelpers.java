package mindustry.game;

import mindustry.content.*;
import mindustry.entities.pattern.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.type.*;

/** Rescued support units use fixed weapons and distinct jobs, independent of player stacking. */
public class PinyinHelpers{
    public enum Kind{medic, gunner, engineer, collector}

    public static UnitType type(Kind kind){
        return switch(kind){
            case medic, engineer -> UnitTypes.poly;
            case gunner -> UnitTypes.flare;
            case collector -> UnitTypes.mono;
        };
    }

    public static void equip(Unit unit, Kind kind){
        unit.maxHealth = unit.health = kind == Kind.engineer ? 240f : 180f;
        unit.armor = 1f;
        if(kind != Kind.gunner){
            unit.mounts = new WeaponMount[0];
        }else{
            Weapon weapon = UnitTypes.alpha.weapons.first().copy();
            weapon.bullet = PinyinArsenal.create(PinyinArsenal.Kind.missile, 8f, false);
            weapon.reload = 45f;
            weapon.shoot = new ShootSpread(1, 0f);
            weapon.rotate = true;
            weapon.minShootVelocity = 0f;
            weapon.maxShootVelocity = -1f;
            unit.mounts = new WeaponMount[]{weapon.mountType.get(weapon)};
        }
    }
}
