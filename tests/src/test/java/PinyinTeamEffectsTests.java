import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinTeamEffectsTests{
    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
    }

    Unit player(){
        return UnitTypes.alpha.create(Team.sharded);
    }

    @Test
    void startingWeaponsHaveTripleProjectilesDoubleDamageAndFasterReload(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit unit = player();
        effects.inherit(unit);
        assertEquals(3, effects.bulletsPerShot());
        for(int i = 0; i < unit.mounts.length; i++){
            var base = unit.type.weapons.get(i);
            var weapon = unit.mounts[i].weapon;
            assertEquals(base.shoot.shots + 2, weapon.shoot.shots);
            assertEquals(base.bullet.damage * 2f, weapon.bullet.damage);
            assertEquals(base.reload * 0.75f, weapon.reload);
            assertTrue(weapon.shoot instanceof ShootSpread);
        }
    }

    @Test
    void eachPlayerCanUpgradeTheirOwnGunsWithoutChangingBaseContent(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit first = player(), second = player();
        float baseDamage = UnitTypes.alpha.weapons.first().bullet.damage;
        float baseReload = UnitTypes.alpha.weapons.first().reload;
        int baseShots = UnitTypes.alpha.weapons.first().shoot.shots;
        PinyinTeamEffects other = new PinyinTeamEffects();
        for(int energy = 1; energy <= 4; energy++){
            effects.correct(energy, true, first, PinyinTeamEffects.Reward.multishot);
            other.correct(energy, true, second, PinyinTeamEffects.Reward.multishot);
        }
        for(Unit unit : new Unit[]{first, second}){
            for(var mount : unit.mounts){
                assertTrue(mount.weapon.bullet.damage > baseDamage);
                assertTrue(mount.weapon.reload < baseReload);
                assertTrue(mount.weapon.shoot instanceof ShootSpread);
                assertTrue(mount.weapon.shoot.shots > baseShots);
                assertTrue(((BasicBulletType)mount.weapon.bullet).width > ((BasicBulletType)UnitTypes.alpha.weapons.first().bullet).width);
            }
            assertEquals(160f, unit.shield);
            assertTrue(unit.getDuration(StatusEffects.fast) > 0f);
            assertTrue(unit.getDuration(StatusEffects.overclock) > 0f);
            assertTrue(unit.getDuration(StatusEffects.shielded) > 0f);
        }
        assertEquals(baseDamage, UnitTypes.alpha.weapons.first().bullet.damage);
        assertEquals(baseReload, UnitTypes.alpha.weapons.first().reload);
        assertEquals(baseShots, UnitTypes.alpha.weapons.first().shoot.shots);
        Unit respawn = player();
        effects.inherit(respawn);
        assertEquals(first.mounts[0].weapon.bullet.damage, respawn.mounts[0].weapon.bullet.damage);
        assertNotSame(first.mounts[0].weapon, second.mounts[0].weapon);
    }

    @Test
    void wrongAnswerOnlyWeakensThePickerByOneLevelWithoutHealthOrSlowPenalties(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit first = player(), second = player();
        effects.inherit(first);
        PinyinTeamEffects teammate = new PinyinTeamEffects();
        teammate.inherit(second);
        float damage = first.mounts[0].weapon.bullet.damage, reload = first.mounts[0].weapon.reload;
        float health = first.health;
        first.shield = 80f;
        effects.wrong(first);
        assertEquals(-1, effects.power());
        assertEquals(1, effects.bulletsPerShot());
        assertEquals(health, first.health);
        assertEquals(80f, first.shield);
        assertEquals(0f, first.getDuration(StatusEffects.slow));
        for(var mount : first.mounts){
            assertTrue(mount.weapon.bullet.damage < damage);
            assertTrue(mount.weapon.reload > reload);
        }
        assertEquals(0, teammate.power());
        assertEquals(3, teammate.bulletsPerShot());
        assertEquals(damage, second.mounts[0].weapon.bullet.damage);
        assertEquals(reload, second.mounts[0].weapon.reload);
        assertEquals(health, second.health);
    }

    @Test
    void RespawnInheritsOnlyRemainingTeamEffectsAndCurrentPower(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit survivor = player();
        effects.correct(1, true, survivor, PinyinTeamEffects.Reward.multishot);
        effects.wrong(survivor);
        effects.update(300f);
        Unit respawn = player();
        effects.inherit(respawn);
        assertEquals(900f, respawn.getDuration(StatusEffects.fast));
        assertEquals(60f, respawn.getDuration(StatusEffects.shielded));
        assertEquals(0f, respawn.getDuration(StatusEffects.slow));
        assertEquals(survivor.mounts[0].weapon.bullet.damage, respawn.mounts[0].weapon.bullet.damage);
        effects.update(1200f);
        Unit later = player();
        effects.inherit(later);
        assertEquals(0f, later.getDuration(StatusEffects.fast));
        assertEquals(0f, later.getDuration(StatusEffects.shielded));
        effects.reset();
        assertEquals(0, effects.power());
        assertEquals(3, effects.bulletsPerShot());
    }

    @Test
    void thousandAnswersStayBoundedAndStillRefreshAndHealAtCapacity(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit unit = player();
        for(int i = 1; i <= 1000; i++){
            effects.correct(i, false, unit, PinyinTeamEffects.Reward.multishot);
            effects.refreshActiveBoosts();
            assertTrue(effects.power() <= PinyinCombatBalance.maxBoosts);
            assertTrue(unit.mounts[0].weapon.shoot.shots <= PinyinCombatBalance.maxShots);
            assertTrue(unit.mounts[0].weapon.reload >= 18f);
            assertTrue(effects.damageMultiplier() <= PinyinCombatBalance.maxDamageMultiplier);
            assertTrue(unit.shield <= PinyinCombatBalance.maxShield);
            assertTrue(((BasicBulletType)unit.mounts[0].weapon.bullet).width <= ((BasicBulletType)UnitTypes.alpha.weapons.first().bullet).width * 2.2f);
        }
        assertEquals(PinyinCombatBalance.maxBoosts, effects.power());
        assertEquals(9, effects.bulletsPerShot());
        effects.update(60f * 40f, unit);
        unit.health = 30f;
        effects.correct(1001, false, unit, PinyinTeamEffects.Reward.multishot);
        effects.refreshActiveBoosts();
        assertEquals(50f, unit.health);
        assertEquals(45, effects.secondsLeft());
        Unit revived = player();
        effects.inherit(revived);
        assertEquals(unit.mounts[0].weapon.bullet.damage, revived.mounts[0].weapon.bullet.damage);
        assertEquals(unit.mounts[0].weapon.shoot.shots, revived.mounts[0].weapon.shoot.shots);
    }

    @Test
    void wrongAnswersLoseOneLevelAndCorrectAnswerRecoversOneLevel(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit first = player(), second = player();
        effects.inherit(first);
        effects.inherit(second);
        first.health = second.health = 10000f;
        float damage = first.mounts[0].weapon.bullet.damage;
        for(int i = 1; i <= 10; i++){
            effects.wrong(first);
            assertEquals(-Math.min(i, PinyinCombatBalance.maxPenalties), effects.power());
            assertTrue(first.mounts[0].weapon.bullet.damage <= damage);
            assertEquals(UnitTypes.alpha.weapons.first().bullet.damage * 2f, second.mounts[0].weapon.bullet.damage);
            damage = first.mounts[0].weapon.bullet.damage;
        }
        assertEquals(10000f, first.health);
        effects.correct(1, false, first, PinyinTeamEffects.Reward.multishot);
        assertEquals(-3, effects.power());
        assertEquals(3, effects.bulletsPerShot(), "A correct pickup must add two bullets even after heavy penalties");
        assertTrue(first.mounts[0].weapon.bullet.damage > damage, "A correct answer must help even while weakened");
    }

    @Test
    void correctAndWrongHaveEqualOppositeFirepowerStepsAndRespawnKeepsOnlyOwnStats(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit first = player(), second = player();
        PinyinTeamEffects teammate = new PinyinTeamEffects();
        teammate.inherit(second);
        for(int i = 1; i <= 5; i++) effects.correct(i, false, first, PinyinTeamEffects.Reward.multishot);
        assertEquals(9, effects.bulletsPerShot());
        float damage = first.mounts[0].weapon.bullet.damage;
        effects.correct(6, false, first, PinyinTeamEffects.Reward.multishot);
        assertEquals(9, effects.bulletsPerShot());
        effects.wrong(first);
        assertEquals(9, effects.bulletsPerShot());
        assertEquals(5, effects.power());
        assertEquals(damage, first.mounts[0].weapon.bullet.damage);
        Unit revived = player();
        effects.inherit(revived);
        assertEquals(9, revived.mounts[0].weapon.shoot.shots);
        assertEquals(first.mounts[0].weapon.shoot.shots, revived.mounts[0].weapon.shoot.shots);
        assertEquals(first.mounts[0].weapon.bullet.damage, revived.mounts[0].weapon.bullet.damage);
        assertEquals(0, teammate.power(), "A player who has not answered must gain no upgrades");
        assertEquals(3, second.mounts[0].weapon.shoot.shots);
        Unit otherRespawn = player();
        teammate.inherit(otherRespawn);
        assertEquals(3, otherRespawn.mounts[0].weapon.shoot.shots);
    }

    @Test
    void comboRewardHealsAndShieldsOnlyItsOwner(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit first = player(), second = player();
        first.health = second.health = 50f;
        effects.combo(first);
        assertEquals(90f, first.health);
        assertEquals(80f, first.shield);
        assertEquals(600f, first.getDuration(StatusEffects.overclock));
        assertEquals(50f, second.health);
        assertEquals(0f, second.shield);
        assertEquals(0f, second.getDuration(StatusEffects.overclock));
    }
    @Test
    void answerLayersExpireIndependentlyAndRebuildOnlyTheirOwnersActualGuns(){
        PinyinTeamEffects effects = new PinyinTeamEffects(), other = new PinyinTeamEffects();
        Unit first = player(), second = player();
        effects.correct(3, false, first, PinyinTeamEffects.Reward.multishot);
        other.correct(3, false, second, PinyinTeamEffects.Reward.multishot);
        effects.update(60f * 15f, first);
        assertEquals(30, effects.secondsLeft());
        effects.correct(4, false, first, PinyinTeamEffects.Reward.multishot);
        assertEquals(7, first.mounts[0].weapon.shoot.shots);
        assertEquals(30, effects.secondsLeft(), "New answers must not refresh older layers");
        effects.update(0f, first);
        assertEquals(30, effects.secondsLeft(), "Pause must freeze buffs");
        effects.update(60f * 30f, first);
        assertEquals(1, effects.power());
        assertEquals(5, first.mounts[0].weapon.shoot.shots);
        assertEquals(80f, first.shield);
        assertEquals(15, effects.secondsLeft());
        assertEquals(5, second.mounts[0].weapon.shoot.shots);
        assertEquals(1, other.power());
        effects.update(60f * 15f, first);
        assertEquals(0, effects.power());
        assertEquals(3, first.mounts[0].weapon.shoot.shots);
        assertEquals(UnitTypes.alpha.weapons.first().bullet.damage * 2f, first.mounts[0].weapon.bullet.damage);
        assertEquals(0f, first.shield);
        assertEquals(0, effects.secondsLeft());
    }

    @Test
    void deathAndRespawnDoNotRefreshOrRestoreExpiredAnswerBuffs(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit old = player();
        effects.correct(3, false, old, PinyinTeamEffects.Reward.multishot);
        old.dead = true;
        effects.update(60f * 40f, old);
        Unit revived = player();
        revived.shield = 25f;
        effects.inherit(revived);
        assertEquals(5, effects.secondsLeft());
        assertEquals(5, revived.mounts[0].weapon.shoot.shots);
        effects.update(60f * 5f, revived);
        assertEquals(3, revived.mounts[0].weapon.shoot.shots);
        assertEquals(25f, revived.shield, "Old body's shields cannot subtract revival rewards");
        effects.inherit(player());
        assertEquals(0, effects.power());
    }

    @Test
    void wrongCancelsOneLayerAndPenaltiesAndComboShieldsAlsoExpire(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit unit = player();
        effects.correct(3, false, unit, PinyinTeamEffects.Reward.multishot);
        effects.wrong(unit);
        assertEquals(0f, unit.shield);
        effects.update(PinyinTeamEffects.boostDuration, unit);
        assertEquals(0, effects.power(), "Cancelled layer must not expire a second time");
        effects.wrong(unit);
        effects.combo(unit);
        effects.update(600f, unit);
        assertEquals(0f, unit.shield);
        effects.update(PinyinTeamEffects.boostDuration - 600f, unit);
        assertEquals(0, effects.power());
        assertEquals(3, unit.mounts[0].weapon.shoot.shots);
    }

    @Test
    void simultaneousOppositeExpirationsStillRebuildProjectileCount(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit unit = player();
        effects.wrong(unit);
        effects.wrong(unit);
        effects.correct(1, false, unit, PinyinTeamEffects.Reward.multishot);
        effects.correct(2, false, unit, PinyinTeamEffects.Reward.multishot);
        assertEquals(0, effects.power());
        assertEquals(5, unit.mounts[0].weapon.shoot.shots);
        effects.update(PinyinTeamEffects.boostDuration, unit);
        assertEquals(0, effects.power());
        assertEquals(3, unit.mounts[0].weapon.shoot.shots);
    }
}
