package mindustry.ai.types;

import arc.func.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.game.PinyinSurvival.*;
import mindustry.gen.*;
import mindustry.graphics.*;

import static mindustry.Vars.*;

/** Cooperative pursuit: roadblocks predict the escape route, flankers pinch, tanks crowd. */
public class PinyinSurvivalController extends PinyinEnemyController{
    public final Role role;
    public final Pattern pattern;
    public final int squad, slot, wave;
    private final boolean boss;
    private final Func<Unit, Unit> findTarget;
    private final Vec2 destination = new Vec2();
    private final BulletType ammunition;
    private final BulletType laserAmmunition, mineAmmunition;
    private float age, reload, warning, aimAngle, supportTimer;
    private int volley;

    public PinyinSurvivalController(Kind kind, Role role, Pattern pattern, int wave, int squad, int slot,
        boolean boss, Func<Unit, Unit> findTarget){
        super(kind, findTarget, wave, true);
        this.role = role;
        this.pattern = pattern;
        this.wave = wave;
        this.squad = squad;
        this.slot = slot;
        this.boss = boss;
        this.findTarget = findTarget;
        reload = 100f + slot * 13f;
        PinyinArsenal.Kind ammo = pattern == Pattern.beam ? PinyinArsenal.Kind.laser
            : PinyinArsenal.Kind.values()[Math.floorMod(wave + slot, PinyinArsenal.Kind.values().length)];
        ammunition = PinyinArsenal.create(ammo, (boss ? 12f : 6f) * PinyinSurvival.damageScale(wave), true);
        laserAmmunition = PinyinArsenal.create(PinyinArsenal.Kind.laser, (boss ? 12f : 6f) * PinyinSurvival.damageScale(wave), true);
        mineAmmunition = PinyinArsenal.create(PinyinArsenal.Kind.rapid, (boss ? 12f : 6f) * PinyinSurvival.damageScale(wave), true);
        mineAmmunition.speed = 0.45f;
        mineAmmunition.lifetime = 190f;
        mineAmmunition.homingPower = 0f;
    }

    public static void equip(Unit unit, Kind kind, Role role, int wave, boolean boss){
        PinyinEnemyController.equip(unit, kind, wave);
        unit.hitSize = boss ? 32f : role.size;
        unit.maxHealth = unit.health = (boss ? 6500f : 95f * role.health) * PinyinSurvival.healthScale(wave);
        unit.armor = boss ? 3f : role == Role.tank ? 2f : 0f;
        unit.shield = boss ? 300f : 0f;
        if(unit instanceof PinyinSurvivalBoss guarded) guarded.prepareArmor();
        // Native guns are retained as safe mounts but attack timing is owned here.
        for(var mount : unit.mounts){ mount.shoot = false; mount.weapon.alwaysShooting = false; }
    }

    @Override
    public void updateMovement(){
        Unit target = findTarget.get(unit);
        unit.controlWeapons(false, false);
        if(target == null || target.dead || !target.isAdded()){
            unit.moveAt(destination.setZero());
            return;
        }
        age += Time.delta;
        if(role == Role.tank && (supportTimer -= Time.delta) <= 0f){
            supportTimer = 240f;
            // Shield carriers protect their own squad, rewarding crossfire and flanking.
            Groups.unit.intersect(unit.x - 65f, unit.y - 65f, 130f, 130f, other -> {
                if(other != unit && other.team == unit.team && other.controller() instanceof PinyinSurvivalController ally
                    && ally.squad == squad && other.within(unit, 65f)){
                    other.shield = Math.min(35f, other.shield + 12f);
                    Fx.shieldApply.at(other);
                }
            });
        }
        float distance = unit.dst(target);
        PinyinSurvival.destination(destination, target, role, squad, slot, age + squad * 41f);
        destination.x = Mathf.clamp(destination.x, 12f, world.unitWidth() - 12f);
        destination.y = Mathf.clamp(destination.y, 12f, world.unitHeight() - 12f);
        destination.sub(unit);
        float speed = UnitTypes.alpha.speed * 2f * role.speed;
        if(boss) speed *= 0.58f;
        // Runners catch up, brake near the player, then make short visible rushes.
        if(role == Role.runner && distance < 100f) speed *= age % 240f > 190f ? 1.15f : 0.68f;
        unit.moveAt(destination.limit(speed));
        unit.lookAt(target);
        unit.aim(target.x, target.y, true);

        // Distant squads move but do not add off-screen projectile load.
        if(distance > 260f) return;
        if(warning > 0f){
            warning = Math.max(0f, warning - Time.delta);
            if(warning == 0f) fireVolley();
        }else if((reload -= Time.delta) <= 0f){
            aimAngle = unit.angleTo(target.x + target.vel.x * 10f, target.y + target.vel.y * 10f);
            Pattern next = attackPattern();
            warning = next == Pattern.beam || next == Pattern.mines || next == Pattern.ambush ? 65f : boss ? 48f : 32f;
            reload = Math.max(boss ? 42f : 78f, (boss ? 65f : 165f) / (1f + (float)Math.sqrt(wave) * 0.04f)) + (slot % 4) * 12f;
        }
    }

    private void fireVolley(){
        // Player upgrades must not exhaust the enemy bullet budget and silence the boss.
        int enemyBullets = 0;
        for(Bullet bullet : Groups.bullet) if(bullet.team == unit.team) enemyBullets++;
        if(enemyBullets >= PinyinSurvival.maxEnemyBullets) return;
        Pattern pattern = attackPattern();
        BulletType shot = pattern == Pattern.beam ? laserAmmunition : pattern == Pattern.mines ? mineAmmunition : ammunition;
        volley++;
        int count = volleySize(pattern, boss);
        for(int i = 0; i < count; i++){
            if(enemyBullets++ >= PinyinSurvival.maxEnemyBullets) break;
            float angle = switch(pattern){
                case aimed, beam -> aimAngle;
                case fan -> aimAngle + (i - (count - 1) / 2f) * 15f;
                case burst -> aimAngle + (i - 1) * 4f;
                case ring -> i * 360f / count + volley * 13f;
                case spiral -> aimAngle + volley * 26f + i * 120f;
                case pincer -> aimAngle + (i % 2 == 0 ? -28f : 28f);
                case wave -> aimAngle + (float)Math.sin(volley + i) * 34f;
                case mines -> aimAngle + i * 90f;
                case crossfire -> aimAngle + (i % 2 == 0 ? -18f : 18f);
                case sweep -> aimAngle - 50f + (volley % 5) * 20f + i * 12f;
                case ambush -> aimAngle + 180f + (i - 1) * 20f;
                case flower -> i * 360f / count + Mathf.sin(volley * 0.7f) * 45f;
            };
            float offset = pattern == Pattern.pincer || pattern == Pattern.crossfire ? (i % 2 == 0 ? -25f : 25f) : 0f;
            float ahead = pattern == Pattern.ambush ? 90f : 0f;
            shot.create(unit, unit.team, unit.x + Angles.trnsx(aimAngle + 90f, offset) + Angles.trnsx(aimAngle, ahead),
                unit.y + Angles.trnsy(aimAngle + 90f, offset) + Angles.trnsy(aimAngle, ahead), angle,
                pattern == Pattern.burst ? 0.7f + i * 0.22f : 1f, 1f);
        }
    }

    private Pattern attackPattern(){
        return boss ? Pattern.values()[Math.floorMod(wave + volley, Pattern.values().length)] : pattern;
    }

    public static int volleySize(Pattern pattern, boolean boss){
        return switch(pattern){
            case aimed, beam -> 1;
            case fan, wave -> boss ? 7 : 3;
            case ring -> boss ? 14 : 8;
            case pincer -> 2;
            case burst, spiral -> 3;
            case mines -> 4;
            case crossfire -> boss ? 6 : 4;
            case sweep -> boss ? 5 : 3;
            case ambush -> 3;
            case flower -> boss ? 12 : 6;
        };
    }

    @Override
    public void drawWarning(){
        Draw.z(Layer.playerName + 8f);
        Draw.color(role == Role.tank ? Pal.lightOrange : role == Role.runner ? Pal.lancerLaser : Pal.remove);
        if(warning > 0f){
            Lines.stroke(1.3f);
            Pattern next = attackPattern();
            float length = next == Pattern.beam ? 130f : next == Pattern.ambush ? 90f : 45f;
            Lines.lineAngle(unit.x, unit.y, aimAngle, length);
            if(next == Pattern.ambush){
                Lines.circle(unit.x + Angles.trnsx(aimAngle, 90f), unit.y + Angles.trnsy(aimAngle, 90f), 18f);
            }
            Lines.circle(unit.x, unit.y, unit.hitSize * 0.65f + 4f + warning / 12f);
        }
        if(role == Role.tank || role == Role.blocker){
            Lines.stroke(role == Role.tank ? 2f : 1f);
            Lines.poly(unit.x, unit.y, role == Role.tank ? 6 : 4, unit.hitSize * 0.6f + 3f, unit.rotation);
        }
        Draw.reset();
    }
}
