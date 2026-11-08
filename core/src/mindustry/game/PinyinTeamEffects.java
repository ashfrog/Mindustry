package mindustry.game;

import arc.struct.*;
import arc.math.*;
import arc.graphics.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.gen.*;
import mindustry.type.*;

/** One player's answer effects, retained across respawns. */
public class PinyinTeamEffects{
    public enum Reward{power, multishot, rapid, giant, arsenal, shield, rainbow, wobble}
    private final Rand random = new Rand();

    public PinyinTeamEffects(){ }
    public PinyinTeamEffects(long seed){ random.setSeed(seed); }

    public static final float boostDuration = 60f * 45f;
    private int power;
    private final Seq<AnswerBoost> boosts = new Seq<>();
    private boolean positiveBoostsPermanent;
    private boolean endlessGrowth;
    private int endlessPower;
    private final int[] endlessRewards = new int[Reward.values().length];
    private Reward latestEndlessReward = Reward.power;
    private PinyinArsenal.Kind endlessWeapon = PinyinArsenal.Kind.rapid;
    private float fastTime, overclockTime, shieldedTime;
    private float comboShieldTime, comboShield;
    private Unit comboOwner;

    public void reset(){
        power = 0;
        boosts.clear();
        comboShieldTime = comboShield = 0f;
        comboOwner = null;
        positiveBoostsPermanent = false;
        endlessGrowth = false;
        endlessPower = 0;
        java.util.Arrays.fill(endlessRewards, 0);
        endlessWeapon = PinyinArsenal.Kind.rapid;
        latestEndlessReward = Reward.power;
        fastTime = overclockTime = shieldedTime = 0f;
    }

    public int power(){
        return endlessGrowth ? endlessPower : power;
    }

    /** Aggregate counters keep unlimited survival upgrades without an ever-growing boost list. */
    public void enableEndlessGrowth(){ endlessGrowth = true; }
    public boolean endlessGrowth(){ return endlessGrowth; }

    public boolean positiveBoostsPermanent(){
        return positiveBoostsPermanent;
    }

    public PinyinArsenal.Kind weaponKind(){
        if(endlessGrowth) return endlessWeapon;
        for(int i = boosts.size - 1; i >= 0; i--) if(boosts.get(i).level > 0 && (boosts.get(i).reward == Reward.arsenal || boosts.get(i).reward == Reward.wobble)) return boosts.get(i).weapon;
        return PinyinArsenal.Kind.rapid;
    }

    public float damageMultiplier(){
        if(endlessGrowth){
            if(endlessPower < 0) return 2f / (1f - endlessPower * 0.15f);
            return 2f + endlessPower * 0.18f + (float)Math.sqrt(endlessPower) * 0.3f
                + rewardCount(Reward.power) * 0.12f;
        }
        float base = power >= 0 ? 2f + power * 0.22f : 2f / (1f - power * 0.15f);
        return Math.min(PinyinCombatBalance.maxDamageMultiplier, base + rewardCount(Reward.power) * 0.25f);
    }

    public int bulletsPerShot(){
        int shots = 3 + Math.min(3, rewardCount(Reward.multishot)) * 2
            - (endlessGrowth ? endlessPower < 0 ? 2 : 0 : boosts.contains(boost -> boost.level < 0) ? 2 : 0);
        return PinyinCombatBalance.shots(weaponKind(), shots);
    }

    /** Time until the next positive layer expires; new answers do not refresh older layers. */
    public int secondsLeft(){
        if(endlessGrowth) return 0;
        float remaining = Float.MAX_VALUE;
        for(AnswerBoost boost : boosts) if(boost.level > 0) remaining = Math.min(remaining, boost.remaining);
        return remaining == Float.MAX_VALUE ? 0 : (int)Math.ceil(remaining / 60f);
    }

    /**
     * Extend only active positive answer layers. The cap keeps a child from banking an
     * unlimited permanent advantage while still making a rare pickup feel valuable.
     */
    public int extendBoosts(float ticks){
        if(ticks <= 0f) return 0;
        int changed = 0;
        for(AnswerBoost boost : boosts){
            if(boost.level <= 0) continue;
            float before = boost.remaining;
            boost.remaining = Math.min(boostDuration + 60f * 15f, boost.remaining + ticks);
            if(boost.remaining > before) changed++;
        }
        return changed;
    }

    /** Keep a child's active answer streak alive when they keep finding correct answers. */
    public void refreshActiveBoosts(){
        for(AnswerBoost boost : boosts) if(boost.level > 0) boost.remaining = Math.max(boost.remaining, boostDuration);
    }

    /** Adventure mode keeps rescue growth until an explicit wrong answer removes a layer. */
    public void makePositiveBoostsPermanent(){
        positiveBoostsPermanent = true;
        refreshActiveBoosts();
    }

    public void update(float delta){ update(delta, null); }

    public void update(float delta, Unit unit){
        if(delta <= 0f) return;
        boolean expired = false;
        for(int i = boosts.size - 1; i >= 0; i--){
            AnswerBoost boost = boosts.get(i);
            if(boost.level > 0 && positiveBoostsPermanent) continue;
            boost.remaining -= delta;
            if(boost.remaining <= 0f){
                expired = true;
                power -= boost.level;
                expireShield(boost, unit);
                boosts.remove(i);
            }
        }
        fastTime = Math.max(0f, fastTime - delta);
        overclockTime = Math.max(0f, overclockTime - delta);
        shieldedTime = Math.max(0f, shieldedTime - delta);
        if(comboShieldTime > 0f && (comboShieldTime = Math.max(0f, comboShieldTime - delta)) == 0f){
            if(unit != null && unit == comboOwner) unit.shield = Math.max(0f, unit.shield - comboShield);
            comboShield = 0f;
        }
        if(expired && unit != null && !unit.dead) inherit(unit);
    }

    private int rewardCount(Reward reward){
        return endlessGrowth ? endlessRewards[reward.ordinal()] : boosts.count(boost -> boost.level > 0 && boost.reward == reward);
    }

    public Reward correct(int energy, boolean shieldReward, Unit unit){
        return correct(energy, shieldReward, unit, Reward.values()[random.random(Reward.values().length - 1)]);
    }

    public Reward correct(int energy, boolean shieldReward, Unit unit, Reward reward){
        return correct(energy, shieldReward, unit, reward, null);
    }

    /** Apply a planned bunker reward while keeping the random overload for ordinary answers. */
    public Reward correct(int energy, boolean shieldReward, Unit unit, Reward reward, PinyinArsenal.Kind forcedWeapon){
        PinyinArsenal.Kind weapon = reward == Reward.wobble ? PinyinArsenal.Kind.missile : PinyinArsenal.Kind.rapid;
        if(reward == Reward.arsenal){
            weapon = forcedWeapon == null ? PinyinArsenal.reward(random.random(1, PinyinArsenal.Kind.values().length - 1)) : forcedWeapon;
            if(forcedWeapon == null){
                while(weapon == weaponKind()) weapon = PinyinArsenal.reward(random.random(1, PinyinArsenal.Kind.values().length - 1));
            }
        }
        if(endlessGrowth){
            endlessPower++;
            endlessRewards[reward.ordinal()]++;
            latestEndlessReward = reward;
            if(reward == Reward.arsenal || reward == Reward.wobble) endlessWeapon = weapon;
            if(unit != null && !unit.dead){
                if(shieldReward || reward == Reward.shield) unit.shield = Math.min(PinyinCombatBalance.maxShield, unit.shield + 40f);
                inherit(unit);
            }
            return reward;
        }
        // At capacity the oldest layer is replaced, so a new weapon can still be chosen.
        if(boosts.count(boost -> boost.level > 0) >= PinyinCombatBalance.maxBoosts){
            for(int i = 0; i < boosts.size; i++) if(boosts.get(i).level > 0){
                AnswerBoost old = boosts.remove(i);
                power--;
                expireShield(old, unit);
                break;
            }
            if(unit != null && !unit.dead) unit.heal(20f);
        }
        power++;
        AnswerBoost boost = new AnswerBoost(1, unit, (energy >= 3 ? 80f : 0f) + (reward == Reward.shield ? 120f : 0f), weapon, reward);
        boosts.add(boost);
        if(energy == 1) fastTime = 60f * 20f;
        if(energy == 2) overclockTime = 60f * 20f;
        if(shieldReward || reward == Reward.shield) shieldedTime = 60f * 6f;
        if(unit != null && !unit.dead){
            inherit(unit);
            float added = Math.min(boost.shield, Math.max(0f, PinyinCombatBalance.maxShield - unit.shield));
            unit.shield += added;
            boost.shield = added;
        }
        return reward;
    }

    public void wrong(Unit unit){
        if(endlessGrowth){
            if(endlessPower <= -PinyinCombatBalance.maxPenalties) return;
            endlessPower--;
            int index = latestEndlessReward.ordinal();
            if(endlessRewards[index] == 0){
                for(int i = 0; i < endlessRewards.length; i++) if(endlessRewards[i] > 0){ index = i; break; }
            }
            if(endlessRewards[index] > 0) endlessRewards[index]--;
            if(unit != null && !unit.dead) inherit(unit);
            return;
        }
        if(power <= -PinyinCombatBalance.maxPenalties) return;
        power--;
        AnswerBoost removed = null;
        for(int i = boosts.size - 1; i >= 0; i--){
            if(boosts.get(i).level > 0){ removed = boosts.remove(i); break; }
        }
        if(removed != null) expireShield(removed, unit);
        else boosts.add(new AnswerBoost(-1, unit, 0f, PinyinArsenal.Kind.rapid, null));
        if(unit != null && !unit.dead) inherit(unit);
    }

    public void combo(Unit unit){
        overclockTime = 60f * 10f;
        shieldedTime = 60f * 6f;
        if(unit == null || unit.dead) return;
        unit.heal(40f);
        float added = Math.min(80f, Math.max(0f, PinyinCombatBalance.maxShield - unit.shield));
        unit.shield += added;
        comboShield = unit == comboOwner ? comboShield + added : added;
        comboOwner = unit;
        comboShieldTime = 60f * 10f;
        inherit(unit);
    }

    public void inherit(Unit unit){
        if(fastTime > 0f) unit.apply(StatusEffects.fast, fastTime);
        if(overclockTime > 0f) unit.apply(StatusEffects.overclock, overclockTime);
        if(shieldedTime > 0f) unit.apply(StatusEffects.shielded, shieldedTime);
        for(int i = 0; i < unit.mounts.length; i++){
            var previous = unit.mounts[i];
            Weapon base = i < unit.type.weapons.size ? unit.type.weapons.get(i) : UnitTypes.alpha.weapons.first();
            Weapon weapon = base.copy();
            weapon.bullet = PinyinArsenal.create(weaponKind(), base.bullet.damage, false);
            weapon.shootSound = PinyinArsenal.sound(weaponKind());
            int level = power();
            float growth = (float)Math.log1p(Math.max(0, endlessGrowth ? level : Math.min(PinyinCombatBalance.maxBoosts, level)));
            weapon.bullet.damage *= damageMultiplier();
            weapon.bullet.speed *= level >= 0 ? 1f + Math.min(5f, growth) * 0.12f : 1f / (1f - level * 0.04f);
            weapon.reload *= 0.75f * (level >= 0 ? 1f / (1f + growth * 0.6f) : 1f - level * 0.2f);
            weapon.reload /= 1f + rewardCount(Reward.rapid) * 0.35f;
            float giant = 1f + Math.min(3, rewardCount(Reward.giant)) * 0.18f;
            weapon.bullet.knockback = Math.min(1.2f, weapon.bullet.knockback + Math.min(3, rewardCount(Reward.giant)) * 0.1f);
            // Bound the actual native gun, including beam and fragmentation families.
            int shots = bulletsPerShot();
            weapon.reload = Math.max(weapon.bullet instanceof LaserBulletType || weapon.bullet instanceof LightningBulletType ? 35f : 18f, weapon.reload);
            weapon.shoot = new ShootSpread(shots, 45f / shots);
            if(endlessGrowth && weapon.bullet.pierce){
                weapon.bullet.pierceCap = 3 + (int)Math.sqrt(Math.max(0, level));
            }
            if(weapon.bullet instanceof BasicBulletType basic){
                float size = level >= 0 ? 1.35f + growth * 0.75f : 1.35f / (1f - level * 0.08f);
                basic.width *= Math.min(2.2f, size * giant);
                basic.height *= Math.min(2.2f, size * giant);
            }
            if(weapon.bullet instanceof LaserBulletType laser) laser.width *= Math.min(1.8f, (1f + growth * 0.25f) * giant);
            if(weapon.bullet instanceof ShrapnelBulletType scatter) scatter.width *= Math.min(1.8f, (1f + growth * 0.25f) * giant);
            if(weapon.bullet instanceof LiquidBulletType liquid) liquid.orbSize *= Math.min(1.8f, (1f + growth * 0.2f) * giant);
            if(rewardCount(Reward.rainbow) > 0){
                weapon.bullet.homingPower = Math.max(weapon.bullet.homingPower, 0.04f);
                weapon.bullet.homingRange = Math.max(weapon.bullet.homingRange, 160f);
                weapon.bullet.hitColor = Color.valueOf("ffdf5b");
                if(weapon.bullet instanceof BasicBulletType candy){
                    candy.backColor = Color.valueOf("ff72c6");
                    candy.frontColor = Color.valueOf("8dffef");
                    candy.trailColor = Color.valueOf("b48aff");
                    candy.trailLength = 12;
                    candy.trailWidth = 2f;
                }
                if(weapon.bullet instanceof LaserBulletType laser) laser.colors = new Color[]{Color.valueOf("ff72c6"), Color.valueOf("8dffef"), Color.valueOf("ffdf5b")};
            }
            if(rewardCount(Reward.wobble) > 0){
                weapon.bullet.weaveMag += 2f;
                weapon.bullet.weaveScale = 7f;
                weapon.bullet.homingPower = Math.max(weapon.bullet.homingPower, 0.06f);
                weapon.bullet.homingRange = Math.max(weapon.bullet.homingRange, 160f);
            }
            // Scale every nested damage channel along with the direct projectile.
            scaleSecondary(weapon.bullet, damageMultiplier());
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

    private void scaleSecondary(BulletType bullet, float multiplier){
        bullet.splashDamage *= multiplier;
        bullet.lightningDamage *= multiplier;
        if(bullet.fragBullet != null){ bullet.fragBullet.damage *= multiplier; scaleSecondary(bullet.fragBullet, multiplier); }
        if(bullet.lightningType != null && (bullet.lightning > 0 || bullet instanceof LightningBulletType)){
            bullet.lightningType.damage *= multiplier;
            scaleSecondary(bullet.lightningType, multiplier);
        }
    }

    private void expireShield(AnswerBoost boost, Unit unit){
        if(unit != null && unit == boost.owner) unit.shield = Math.max(0f, unit.shield - boost.shield);
    }

    private static class AnswerBoost{
        final int level;
        final Unit owner;
        final PinyinArsenal.Kind weapon;
        final Reward reward;
        float shield;
        float remaining = boostDuration;

        AnswerBoost(int level, Unit owner, float shield, PinyinArsenal.Kind weapon, Reward reward){
            this.level = level;
            this.weapon = weapon;
            this.reward = reward;
            this.owner = owner;
            this.shield = shield;
        }
    }
}
