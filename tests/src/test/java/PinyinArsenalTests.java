import arc.util.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinArsenalTests{
    @BeforeAll
    static void init(){ ApplicationTests.launchApplication(); }

    @BeforeEach
    void setup(){
        logic.reset();
        world.loadGenerator(50, 50, tiles -> tiles.fill());
        state.rules.unitCap = 50;
        state.rules.unitCapVariable = false;
        Time.delta = 1f;
    }

    @Test
    void realVanillaBulletFamiliesAreCopiedWithoutChangingTheirContent(){
        assertTrue(PinyinArsenal.create(PinyinArsenal.Kind.scatter, 10f, false) instanceof ShrapnelBulletType);
        assertTrue(PinyinArsenal.create(PinyinArsenal.Kind.missile, 10f, false) instanceof MissileBulletType);
        assertTrue(PinyinArsenal.create(PinyinArsenal.Kind.artillery, 10f, false) instanceof ArtilleryBulletType);
        assertTrue(PinyinArsenal.create(PinyinArsenal.Kind.laser, 10f, false) instanceof LaserBulletType);
        assertTrue(PinyinArsenal.create(PinyinArsenal.Kind.lightning, 10f, false) instanceof LightningBulletType);
        assertTrue(PinyinArsenal.create(PinyinArsenal.Kind.fragment, 10f, false) instanceof FlakBulletType);
        assertTrue(PinyinArsenal.create(PinyinArsenal.Kind.ice, 10f, false) instanceof LiquidBulletType);
        for(var kind : PinyinArsenal.Kind.values()){
            BulletType source = PinyinArsenal.source(kind);
            float damage = source.damage, splash = source.splashDamage;
            float childDamage = source.fragBullet == null ? 0f : source.fragBullet.damage;
            BulletType copy = PinyinArsenal.create(kind, 13f, false);
            assertNotSame(source, copy);
            assertEquals(13f, copy.damage);
            assertTrue(copy.collidesAir && copy.collidesGround);
            assertFalse(copy.killShooter);
            assertEquals(0f, copy.healPercent);
            if(copy.fragBullet != null){
                assertNotSame(source.fragBullet, copy.fragBullet);
                assertTrue(copy.fragBullet.collidesAir && copy.fragBullet.collidesGround);
                copy.fragBullet.damage += 1f;
                assertEquals(childDamage, source.fragBullet.damage);
            }
            assertEquals(damage, source.damage);
            assertEquals(splash, source.splashDamage);
        }
    }

    @Test
    void everyWeaponFamilyCanActuallyHitAFlyingEnemy(){
        for(var kind : PinyinArsenal.Kind.values()){
            Groups.bullet.clear();
            Groups.unit.clear();
            Unit owner = UnitTypes.alpha.spawn(Team.sharded, 100f, 160f);
            Unit target = UnitTypes.alpha.spawn(Team.crux, 160f, 160f);
            target.maxHealth = target.health = 10000f;
            state.teams.updateTeamStats();
            BulletType bullet = PinyinArsenal.create(kind, 15f, false);
            bullet.create(owner, owner.x, owner.y, 0f);
            for(int tick = 0; tick < 150; tick++){
                Groups.bullet.updatePhysics();
                Groups.unit.updatePhysics();
                Groups.bullet.update();
                Groups.bullet.collide();
            }
            assertTrue(target.health < 10000f, kind + " must cause real damage to flying targets");
            assertFalse(owner.dead);
        }
    }

    @Test
    void randomAnswersDoNotAlwaysAddShotsAndEachPlayerOwnsTheirReward(){
        PinyinTeamEffects effects = new PinyinTeamEffects(215), teammate = new PinyinTeamEffects(216);
        Unit first = UnitTypes.alpha.create(Team.sharded), second = UnitTypes.alpha.create(Team.sharded);
        teammate.inherit(second);
        var rewards = new HashSet<PinyinTeamEffects.Reward>();
        int multishots = 0;
        for(int i = 1; i <= 160; i++){
            var reward = effects.correct(i, false, first);
            rewards.add(reward);
            if(reward == PinyinTeamEffects.Reward.multishot) multishots++;
            assertEquals(3 + multishots * 2, effects.bulletsPerShot());
            assertEquals(effects.bulletsPerShot(), first.mounts[0].weapon.shoot.shots);
        }
        assertEquals(PinyinTeamEffects.Reward.values().length, rewards.size());
        assertTrue(multishots > 0 && multishots < 160);
        assertEquals(0, teammate.power());
        assertEquals(3, second.mounts[0].weapon.shoot.shots);
    }

    @Test
    void specialRewardsRollBackOnWrongAnswersAndExpireWithoutPermanentWeapons(){
        PinyinTeamEffects effects = new PinyinTeamEffects(21);
        Unit unit = UnitTypes.alpha.create(Team.sharded);
        effects.correct(1, false, unit, PinyinTeamEffects.Reward.arsenal);
        var weapon = effects.weaponKind();
        assertNotEquals(PinyinArsenal.Kind.rapid, weapon);
        assertEquals(PinyinArsenal.source(weapon).getClass(), unit.mounts[0].weapon.bullet.getClass());
        effects.update(600f, unit);
        effects.correct(2, false, unit, PinyinTeamEffects.Reward.wobble);
        assertEquals(PinyinArsenal.Kind.missile, effects.weaponKind());
        assertTrue(unit.mounts[0].weapon.bullet.weaveMag >= 2f);
        effects.wrong(unit);
        assertEquals(weapon, effects.weaponKind());
        effects.update(PinyinTeamEffects.boostDuration - 600f, unit);
        assertEquals(PinyinArsenal.Kind.rapid, effects.weaponKind());
        assertEquals(3, unit.mounts[0].weapon.shoot.shots);
        assertEquals(0, effects.power());
    }

    @Test
    void cancellingOppositePowerTotalsStillExpiresRandomStatBonuses(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit unit = UnitTypes.alpha.create(Team.sharded);
        effects.wrong(unit);
        effects.correct(1, false, unit, PinyinTeamEffects.Reward.power);
        float damage = unit.mounts[0].weapon.bullet.damage;
        assertEquals(0, effects.power());
        effects.update(PinyinTeamEffects.boostDuration, unit);
        assertEquals(0, effects.power());
        assertTrue(unit.mounts[0].weapon.bullet.damage < damage);
        assertEquals(UnitTypes.alpha.weapons.first().bullet.damage * 2f, unit.mounts[0].weapon.bullet.damage);
    }

    @Test
    void enemyBeamAndLightningWaitForWarningsAndUseDifferentNativeAmmunition(){
        for(var kind : new PinyinEnemyController.Kind[]{PinyinEnemyController.Kind.beam, PinyinEnemyController.Kind.electric}){
            Groups.bullet.clear();
            Unit target = UnitTypes.alpha.create(Team.sharded);
            target.set(160f, 160f);
            Unit enemy = PinyinEnemyController.type(kind).create(Team.crux);
            enemy.set(100f, 160f);
            PinyinEnemyController.equip(enemy, kind, 1);
            enemy.controller(new PinyinEnemyController(kind, from -> target));
            for(int tick = 0; tick < 239; tick++) enemy.controller().updateUnit();
            assertEquals(0, Groups.bullet.size(), "Instant attacks cannot fire before their telegraph ends");
            enemy.controller().updateUnit();
            assertTrue(Groups.bullet.size() > 0);
            assertTrue(kind == PinyinEnemyController.Kind.beam ? enemy.mounts[0].weapon.bullet instanceof LaserBulletType
                : enemy.mounts[0].weapon.bullet instanceof LightningBulletType);
        }
    }
}
