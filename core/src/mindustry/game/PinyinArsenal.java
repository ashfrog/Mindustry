package mindustry.game;

import arc.audio.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.world.blocks.defense.turrets.*;

/** Copies real vanilla ammunition while scaling its combat values for the learning adventure. */
public class PinyinArsenal{
    public enum Kind{rapid, scatter, flame, missile, artillery, laser, lightning, pierce, fragment, ice, slag}

    public static Kind reward(int answers){
        return Kind.values()[1 + Math.floorMod(answers - 1, Kind.values().length - 1)];
    }

    public static BulletType source(Kind kind){
        return switch(kind){
            case rapid -> UnitTypes.alpha.weapons.first().bullet;
            case scatter -> ((ItemTurret)Blocks.fuse).ammoTypes.get(Items.thorium);
            case flame -> UnitTypes.mace.weapons.first().bullet;
            case missile -> UnitTypes.zenith.weapons.first().bullet;
            case artillery -> UnitTypes.fortress.weapons.first().bullet;
            case laser -> UnitTypes.quasar.weapons.first().bullet;
            case lightning -> ((PowerTurret)Blocks.arc).shootType;
            case pierce -> ((ItemTurret)Blocks.spectre).ammoTypes.get(Items.thorium);
            case fragment -> ((ItemTurret)Blocks.cyclone).ammoTypes.get(Items.metaglass);
            case ice -> ((LiquidTurret)Blocks.tsunami).ammoTypes.get(Liquids.cryofluid);
            case slag -> UnitTypes.atrax.weapons.first().bullet;
        };
    }

    public static Sound sound(Kind kind){
        return switch(kind){
            case flame -> UnitTypes.mace.weapons.first().shootSound;
            case missile -> UnitTypes.zenith.weapons.first().shootSound;
            case artillery -> UnitTypes.fortress.weapons.first().shootSound;
            case laser, scatter -> UnitTypes.quasar.weapons.first().shootSound;
            case lightning -> UnitTypes.pulsar.weapons.first().shootSound;
            case slag -> UnitTypes.atrax.weapons.first().shootSound;
            default -> UnitTypes.alpha.weapons.first().shootSound;
        };
    }

    public static BulletType create(Kind kind, float damage, boolean enemy){
        BulletType bullet = copy(source(kind), damage, enemy);
        if(bullet.speed > 0.1f){
            bullet.speed = kind == Kind.flame ? (enemy ? 2.4f : 4.2f) : enemy ? 1.8f : 4f;
            bullet.lifetime = kind == Kind.flame ? (enemy ? 25f : 34f) : enemy ? 90f : 65f;
        }
        if(bullet instanceof MissileBulletType){
            bullet.homingPower = enemy ? 0.012f : 0.06f;
            bullet.homingRange = enemy ? 80f : 160f;
            bullet.homingDelay = enemy ? 18f : 5f;
        }
        if(bullet instanceof LaserBulletType laser){ laser.length = enemy ? 130f : 160f; laser.width = enemy ? 8f : 12f; }
        if(bullet instanceof ShrapnelBulletType scatter){ scatter.length = enemy ? 100f : 130f; scatter.width = enemy ? 8f : 14f; }
        if(kind == Kind.lightning){
            bullet.lightningLength = enemy ? 15 : 25;
            bullet.lightningLengthRand = 0;
        }
        if(kind == Kind.ice){ bullet.status = StatusEffects.slow; bullet.statusDuration = 120f; }
        bullet.init();
        return bullet;
    }

    private static BulletType copy(BulletType source, float damage, boolean enemy){
        BulletType bullet = source.copy();
        bullet.damage = damage;
        bullet.keepVelocity = false;
        bullet.collidesAir = bullet.collidesGround = true;
        bullet.collidesTeam = false;
        bullet.healPercent = bullet.healAmount = bullet.lifesteal = 0f;
        bullet.killShooter = false;
        bullet.spawnUnit = null;
        bullet.spawnBullets = new Seq<>();
        bullet.intervalBullet = null;
        bullet.lightning = Math.min(bullet.lightning, 2);
        if(bullet.pierce || bullet.pierceArmor) bullet.pierceCap = 3;
        bullet.splashDamage = source.splashDamage > 0f ? damage * 0.65f : 0f;
        bullet.splashDamageRadius = Math.min(source.splashDamageRadius, enemy ? 22f : 32f);
        bullet.lightningDamage = damage * 0.35f;
        bullet.statusDuration = Math.min(source.statusDuration, enemy ? 90f : 180f);
        bullet.knockback = Math.min(source.knockback, enemy ? 0.25f : 0.7f);
        bullet.incendChance = 0f;
        bullet.makeFire = false;
        bullet.puddles = 0;
        if(bullet instanceof LiquidBulletType liquid) liquid.puddleSize = 0f;
        if(source.fragBullet != null){
            bullet.fragBullet = copy(source.fragBullet, damage * 0.22f, enemy);
            bullet.fragBullets = Math.min(source.fragBullets, 2);
        }
        if(source.lightningType != null && (source.lightning > 0 || source instanceof LightningBulletType)){
            bullet.lightningType = copy(source.lightningType, damage * 0.35f, enemy);
        }
        bullet.init();
        return bullet;
    }
}
