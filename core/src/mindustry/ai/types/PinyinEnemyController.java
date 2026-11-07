package mindustry.ai.types;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.game.*;
import mindustry.type.*;

import static mindustry.Vars.*;

/** Slow enemies with distinct, readable attacks instead of faster pursuit. */
public class PinyinEnemyController extends AIController{
    public enum Kind{gunner, orbit, crawler, flame, artillery, spread, bomber, guardian, sniper, ice, burst, storm, frost, ram, siege, beam, electric}
    /** Secondary boss abilities. They unlock on later chapters so the first boss stays readable. */
    public enum BossSkill{shieldPulse, lightningGrid, freezeNova, ramAftershock, meteorRain}
    public final Kind kind;
    public boolean selfDestructed;
    private final Func<Unit, Unit> findPlayer;
    private final Vec2 destination = new Vec2(), dashDirection = new Vec2();
    private float cooldown = 150f, warning, dashTime, attackX, attackY, radialTimer = 180f;
    private float skillTimer = 360f;
    private int skillCursor;
    private boolean charging;
    private int barrage;
    private final int difficulty;
    private final float attackScale;

    public PinyinEnemyController(Kind kind, Func<Unit, Unit> findPlayer){
        this(kind, findPlayer, 1);
    }

    public PinyinEnemyController(Kind kind, Func<Unit, Unit> findPlayer, int difficulty){
        this.kind = kind;
        this.findPlayer = findPlayer;
        this.difficulty = Math.max(1, difficulty);
        attackScale = 1f + (this.difficulty - 1) * 0.16f;
    }

    public static Kind regularKind(int index, int difficulty){
        Kind[] kinds = {Kind.gunner, Kind.orbit, Kind.crawler, Kind.flame, Kind.spread, Kind.artillery, Kind.bomber, Kind.ice, Kind.burst, Kind.sniper, Kind.beam, Kind.electric};
        return kinds[Math.floorMod(index, Math.min(kinds.length, 4 + difficulty))];
    }

    private static final Kind[][] themedKinds = {
        {Kind.crawler, Kind.orbit, Kind.gunner, Kind.spread, Kind.burst, Kind.sniper, Kind.electric},
        {Kind.gunner, Kind.artillery, Kind.bomber, Kind.sniper, Kind.spread, Kind.flame, Kind.beam},
        {Kind.ice, Kind.burst, Kind.orbit, Kind.sniper, Kind.artillery, Kind.crawler, Kind.electric},
        {Kind.flame, Kind.crawler, Kind.artillery, Kind.bomber, Kind.burst, Kind.spread, Kind.beam}
    };
    private static final Kind[][] themedBosses = {
        {Kind.ram, Kind.storm, Kind.guardian}, {Kind.siege, Kind.storm, Kind.ram},
        {Kind.frost, Kind.guardian, Kind.storm}, {Kind.siege, Kind.ram, Kind.guardian}
    };

    public static Kind regularKind(int index, int difficulty, int theme){
        Kind[] pool = themedKinds[Math.floorMod(theme, themedKinds.length)];
        return pool[Math.floorMod(index, Math.min(pool.length, 3 + Math.max(1, difficulty)))];
    }

    public static Kind bossKind(int theme, int roll){
        return themedBosses[Math.floorMod(theme, themedBosses.length)][Math.floorMod(roll, 3)];
    }

    public static boolean boss(Kind kind){
        return kind == Kind.guardian || kind == Kind.storm || kind == Kind.frost || kind == Kind.ram || kind == Kind.siege;
    }

    /** Every boss has a small, deterministic skill deck. Keeping this data-driven makes boss variety easy to expand. */
    public static BossSkill[] bossSkills(Kind kind){
        return switch(kind){
            case guardian -> new BossSkill[]{BossSkill.shieldPulse, BossSkill.lightningGrid};
            case storm -> new BossSkill[]{BossSkill.lightningGrid, BossSkill.meteorRain};
            case frost -> new BossSkill[]{BossSkill.freezeNova, BossSkill.lightningGrid};
            case ram -> new BossSkill[]{BossSkill.ramAftershock, BossSkill.meteorRain};
            case siege -> new BossSkill[]{BossSkill.meteorRain, BossSkill.lightningGrid, BossSkill.ramAftershock};
            default -> new BossSkill[0];
        };
    }

    public static boolean hasBossSkill(Kind kind, BossSkill skill){
        for(BossSkill candidate : bossSkills(kind)) if(candidate == skill) return true;
        return false;
    }

    public static UnitType type(Kind kind){
        return switch(kind){
            case gunner -> UnitTypes.dagger;
            case orbit -> UnitTypes.flare;
            case crawler -> UnitTypes.crawler;
            case flame -> UnitTypes.mace;
            case artillery -> UnitTypes.fortress;
            case spread -> UnitTypes.atrax;
            case bomber -> UnitTypes.horizon;
            case guardian -> UnitTypes.zenith;
            case sniper -> UnitTypes.fortress;
            case ice -> UnitTypes.pulsar;
            case burst -> UnitTypes.mace;
            case storm -> UnitTypes.antumbra;
            case frost -> UnitTypes.quasar;
            case ram -> UnitTypes.scepter;
            case siege -> UnitTypes.quad;
            case beam -> UnitTypes.quasar;
            case electric -> UnitTypes.pulsar;
        };
    }

    public static PinyinArsenal.Kind ammunition(Kind kind){
        return switch(kind){
            case orbit, guardian, ram -> PinyinArsenal.Kind.missile;
            case flame -> PinyinArsenal.Kind.flame;
            case artillery, bomber, siege -> PinyinArsenal.Kind.artillery;
            case spread, burst, sniper -> PinyinArsenal.Kind.pierce;
            case crawler, storm -> PinyinArsenal.Kind.fragment;
            case ice, frost -> PinyinArsenal.Kind.ice;
            case beam -> PinyinArsenal.Kind.laser;
            case electric -> PinyinArsenal.Kind.lightning;
            default -> PinyinArsenal.Kind.rapid;
        };
    }

    public static void equip(Unit unit, Kind kind, int difficulty){
        float tier = Math.max(0, difficulty - 1), scale = 1f + tier * 0.16f;
        float healthScale = 1f + tier * 0.4f + tier * tier * 0.055f;
        unit.maxHealth = unit.health = (boss(kind) ? (kind == Kind.ram ? 3200f : 2600f) : kind == Kind.flame || kind == Kind.artillery ? 220f : 130f) * healthScale;
        unit.armor = boss(kind) ? 2f : 0f;
        for(int i = 0; i < unit.mounts.length; i++){
            Weapon weapon = unit.type.weapons.get(i).copy();
            BulletType bullet = PinyinArsenal.create(ammunition(kind), (boss(kind) ? 7f : 5f) * scale, true);
            if(kind == Kind.sniper){ bullet.speed = 3.4f; bullet.lifetime = 90f; }
            weapon.shootSound = PinyinArsenal.sound(ammunition(kind));
            weapon.bullet = bullet;
            weapon.shoot = new ShootSpread(kind == Kind.spread || kind == Kind.flame ? 3 : 1, 14f);
            if(kind == Kind.burst){ weapon.shoot.shots = 3; weapon.shoot.shotDelay = 8f; }
            weapon.reload = boss(kind) ? 65f : kind == Kind.flame ? 75f : 90f;
            weapon.reload = Math.max(25f, weapon.reload / (1f + tier * 0.08f));
            weapon.shootCone = 30f;
            // Flare normally requires high flight speed to shoot; escape enemies move slowly.
            weapon.minShootVelocity = 0f;
            weapon.maxShootVelocity = -1f;
            weapon.rotate = true;
            weapon.rotationLimit = 360f;
            weapon.continuous = weapon.alwaysContinuous = weapon.alwaysShooting = weapon.shootOnDeath = false;
            weapon.shootOnDeathEffect = null;
            weapon.controllable = true;
            weapon.noAttack = false;
            unit.mounts[i] = weapon.mountType.get(weapon);
            unit.mounts[i].reload = 90f;
        }
    }

    @Override
    public void updateTargeting(){
        // Target and firing decisions below deliberately exclude cores and unopened chests.
    }

    @Override
    public void updateMovement(){
        Unit player = findPlayer.get(unit);
        if(player == null) return;
        unit.aim(player.x, player.y, true);
        unit.lookAt(player);
        boolean bomb = kind == Kind.crawler || kind == Kind.artillery || kind == Kind.bomber || kind == Kind.sniper || kind == Kind.siege || kind == Kind.beam || kind == Kind.electric;
        float range = kind == Kind.flame ? 6f : 15f;
        boolean telegraphed = kind == Kind.crawler || kind == Kind.sniper || kind == Kind.beam || kind == Kind.electric;
        unit.controlWeapons(!telegraphed && unit.within(player, range * tilesize), !telegraphed && unit.within(player, range * tilesize));

        cooldown -= Time.delta;
        if(warning > 0f){
            warning -= Time.delta;
            unit.controlWeapons(false, false);
            if(warning <= 0f){
                if(charging){
                    dashTime = 60f;
                    dashDirection.set(attackX, attackY).sub(unit).nor();
                }else if(kind == Kind.sniper || kind == Kind.beam || kind == Kind.electric){
                    unit.mounts[0].weapon.bullet.create(unit, unit.x, unit.y, unit.angleTo(attackX, attackY));
                }else{
                    Damage.damage(unit.team, attackX, attackY, blastRadius(), (boss(kind) ? 28f : 22f) * attackScale, true, true);
                    if((kind == Kind.artillery && difficulty >= 3) || kind == Kind.siege){
                        for(Point2 direction : Geometry.d4){
                            float x = attackX + direction.x * tilesize * 4f, y = attackY + direction.y * tilesize * 4f;
                            Damage.damage(unit.team, x, y, blastRadius(), 18f * attackScale, true, true);
                            Fx.blastExplosion.at(x, y);
                        }
                    }
                    Fx.blastExplosion.at(attackX, attackY);
                    Sounds.explosion.play();
                    if(kind == Kind.crawler){
                        selfDestructed = true;
                        unit.kill();
                    }
                }
            }
            unit.movePref(vec.setZero());
            return;
        }
        if(dashTime > 0f){
            dashTime -= Time.delta;
            unit.movePref(vec.set(dashDirection).scl(Math.min(kind == Kind.ram ? 2.2f : 1.5f, player.speed() * 0.85f)));
            if(kind == Kind.ram && (int)dashTime % 12 == 0){
                float angle = dashDirection.angle();
                for(int side : new int[]{-1, 1}) unit.mounts[0].weapon.bullet.create(unit, unit.x, unit.y, angle + side * 90f);
            }
            return;
        }
        if(cooldown <= 0f && unit.within(player, (kind == Kind.crawler ? 4f : kind == Kind.electric ? 10f : kind == Kind.beam ? 16f : 21f) * tilesize)
            && (bomb || kind == Kind.flame || kind == Kind.guardian || kind == Kind.ram)){
            attackX = player.x;
            attackY = player.y;
            charging = kind == Kind.flame || kind == Kind.ram;
            warning = kind == Kind.crawler ? 60f : 90f;
            cooldown = Math.max(110f, (boss(kind) ? (unit.health < unit.maxHealth / 2f ? 165f : 240f) : 330f) / (1f + (difficulty - 1) * 0.08f));
            return;
        }
        if((kind == Kind.guardian || kind == Kind.storm || kind == Kind.frost) && (radialTimer -= Time.delta) <= 0f){
            boolean enraged = unit.health < unit.maxHealth / 2f;
            radialTimer = Math.max(kind == Kind.storm ? 35f : 60f,
                (kind == Kind.storm ? (enraged ? 40f : 65f) : enraged ? 110f : 180f) / (1f + (difficulty - 1) * 0.08f));
            int count = Math.min(36, 12 + (difficulty - 1) * 2);
            barrage++;
            if(kind == Kind.frost && barrage % 2 == 1){
                // Aimed frost fan alternates with an expanding ring; all shots can be dodged.
                for(int i = -3; i <= 3; i++) unit.mounts[0].weapon.bullet.create(unit, unit.x, unit.y, unit.angleTo(player) + i * 14f);
            }else{
                for(int i = 0; i < count; i++){
                    float angle = i * 360f / count + (kind == Kind.storm ? barrage * 17f : unit.rotation);
                    unit.mounts[0].weapon.bullet.create(unit, unit.x, unit.y, angle);
                }
            }
        }

        // Later chapters add a second ability deck. These effects are deliberately separate from
        // the tested radial barrages, so the readable warning cadence remains intact in chapter 1.
        if(boss(kind) && difficulty >= 2 && (skillTimer -= Time.delta) <= 0f){
            BossSkill[] deck = bossSkills(kind);
            if(deck.length > 0){
                releaseBossSkill(deck[skillCursor++ % deck.length], player);
                boolean enraged = unit.health < unit.maxHealth * 0.5f;
                skillTimer = Math.max(150f, (enraged ? 250f : 360f) / (1f + (difficulty - 1) * 0.08f));
            }
        }

        float speed = Math.min(unit.speed() * 0.38f, player.speed() * 0.55f);
        float distance = unit.dst(player);
        if((kind == Kind.orbit || kind == Kind.bomber || kind == Kind.guardian || kind == Kind.storm || kind == Kind.frost) && distance < tilesize * 15f){
            vec.set(player).sub(unit).nor().rotate(75f).scl(speed);
        }else if((kind == Kind.gunner || kind == Kind.spread || kind == Kind.artillery) && distance < tilesize * 10f){
            vec.set(player).sub(unit).nor().scl(distance < tilesize * 6f ? -speed : 0f);
        }else{
            destination.set(player);
            if(!unit.isFlying() && controlPath != null){
                var path = controlPath.getPathPosition(unit, destination);
                if(!path.move){
                    unit.movePref(vec.setZero());
                    return;
                }
                destination.set(path.dest);
            }
            vec.set(destination).sub(unit).limit(speed);
        }
        unit.movePref(vec);
    }

    private void releaseBossSkill(BossSkill skill, Unit player){
        float scale = attackScale;
        switch(skill){
            case shieldPulse -> {
                unit.shield = Math.min(unit.maxHealth * 0.42f, unit.shield + 120f * scale);
                unit.apply(StatusEffects.overclock, 90f + difficulty * 12f);
                Fx.shockwave.at(unit.x, unit.y);
            }
            case lightningGrid -> {
                // Five short telegraphed-looking strikes make standing still dangerous without
                // turning the attack into an unavoidable homing projectile.
                for(int i = -2; i <= 2; i++){
                    float x = player.x + i * tilesize * 2.5f;
                    float y = player.y + (i % 2 == 0 ? tilesize * 2f : -tilesize * 2f);
                    Damage.damage(unit.team, x, y, tilesize * 1.5f, 18f * scale, true, true);
                    Fx.lightning.at(x, y);
                }
            }
            case freezeNova -> {
                Damage.damage(unit.team, player.x, player.y, tilesize * 4.5f, 24f * scale, true, true);
                player.apply(StatusEffects.slow, 90f + difficulty * 10f);
                Fx.freezing.at(player.x, player.y);
            }
            case ramAftershock -> {
                Damage.damage(unit.team, unit.x, unit.y, tilesize * 5f, 30f * scale, true, true);
                unit.apply(StatusEffects.fast, 90f + difficulty * 10f);
                Fx.blastExplosion.at(unit.x, unit.y);
            }
            case meteorRain -> {
                for(int i = 0; i < 5; i++){
                    float angle = i * 72f + skillCursor * 17f;
                    float distance = tilesize * (3f + (i % 2) * 2f);
                    float x = player.x + Angles.trnsx(angle, distance);
                    float y = player.y + Angles.trnsy(angle, distance);
                    Damage.damage(unit.team, x, y, tilesize * 2.2f, 22f * scale, true, true);
                    Fx.blastExplosion.at(x, y);
                }
            }
        }
    }

    private float blastRadius(){
        return tilesize * (boss(kind) ? 4f : kind == Kind.crawler ? 2.5f : 3.2f);
    }

    public void drawWarning(){
        if(warning <= 0f) return;
        Draw.z(Layer.playerName);
        Draw.color(Pal.remove);
        Lines.stroke(1.5f);
        if(charging || kind == Kind.sniper || kind == Kind.beam || kind == Kind.electric){
            Lines.line(unit.x, unit.y, attackX, attackY);
            Drawf.arrow(unit.x, unit.y, attackX, attackY, 4f, 4f, Pal.remove);
        }else{
            float radius = blastRadius();
            Lines.circle(attackX, attackY, radius);
            if((kind == Kind.artillery && difficulty >= 3) || kind == Kind.siege){
                for(Point2 direction : Geometry.d4) Lines.circle(attackX + direction.x * tilesize * 4f, attackY + direction.y * tilesize * 4f, radius);
            }
            Lines.circle(attackX, attackY, radius * (1f - warning / 90f));
            Draw.alpha(0.12f);
            Fill.circle(attackX, attackY, radius);
        }
        Draw.reset();
    }
}
