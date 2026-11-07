import arc.util.*;
import mindustry.ai.types.*;
import mindustry.ai.types.PinyinEnemyController.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinEnemyTests{
    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
    }

    @BeforeEach
    void setup(){
        logic.reset();
        world.loadGenerator(40, 40, tiles -> tiles.fill());
        state.rules.unitCap = 20;
        state.rules.unitCapVariable = false;
        Time.delta = 1f;
    }

    @Test
    void allKindsHaveSafeIndependentWeaponsAndNoDeathExplosion(){
        for(Kind kind : Kind.values()){
            var type = PinyinEnemyController.type(kind);
            float damage = type.weapons.first().bullet.damage, health = type.health;
            boolean deathShot = type.weapons.first().shootOnDeath;
            Unit enemy = type.create(Team.crux);
            PinyinEnemyController.equip(enemy, kind, 1);
            for(var mount : enemy.mounts){
                assertNotSame(type.weapons.first(), mount.weapon);
                assertTrue(mount.weapon.bullet.collidesAir);
                assertTrue(mount.weapon.bullet.damage <= 7f);
                assertFalse(mount.weapon.shootOnDeath);
            }
            assertEquals(damage, type.weapons.first().bullet.damage);
            assertEquals(health, type.health);
            assertEquals(deathShot, type.weapons.first().shootOnDeath);
        }
    }

    @Test
    void rangedKindsActuallyFireAtFlyingPlayers(){
        Unit target = UnitTypes.alpha.create(Team.sharded);
        target.set(20 * tilesize, 20 * tilesize);
        for(Kind kind : new Kind[]{Kind.gunner, Kind.orbit, Kind.spread, Kind.flame, Kind.ice, Kind.burst}){
            Unit enemy = PinyinEnemyController.type(kind).create(Team.crux);
            enemy.set(16 * tilesize, 20 * tilesize);
            PinyinEnemyController.equip(enemy, kind, 1);
            enemy.controller(new PinyinEnemyController(kind, from -> target));
            for(int tick = 0; tick < 140; tick++){
                enemy.controller().updateUnit();
                for(var mount : enemy.mounts) mount.weapon.update(enemy, mount);
            }
            int shots = 0;
            for(var mount : enemy.mounts) shots += mount.totalShots;
            assertTrue(shots > 0, kind + " must fire at the flying player");
        }
    }

    @Test
    void guardianFiresRadialBarragesAndSpeedsUpBelowHalfHealth(){
        Unit target = UnitTypes.alpha.create(Team.sharded), boss = UnitTypes.zenith.create(Team.crux);
        target.set(35 * tilesize, 20 * tilesize);
        boss.set(10 * tilesize, 20 * tilesize);
        PinyinEnemyController.equip(boss, Kind.guardian, 1);
        boss.controller(new PinyinEnemyController(Kind.guardian, from -> target));
        for(int tick = 0; tick < 180; tick++) boss.controller().updateUnit();
        assertEquals(12, Groups.bullet.size());
        boss.health = boss.maxHealth * 0.4f;
        for(int tick = 0; tick < 180; tick++) boss.controller().updateUnit();
        assertEquals(24, Groups.bullet.size());
        for(int tick = 0; tick < 110; tick++) boss.controller().updateUnit();
        assertEquals(36, Groups.bullet.size());
    }

    @Test
    void BombardmentAllowsDodgingAndDamagesPlayersOnlyAfterWarning(){
        Unit target = UnitTypes.alpha.spawn(Team.sharded, 20 * tilesize, 20 * tilesize);
        Unit bomber = UnitTypes.horizon.spawn(Team.crux, 12 * tilesize, 20 * tilesize);
        PinyinEnemyController.equip(bomber, Kind.bomber, 1);
        bomber.controller(new PinyinEnemyController(Kind.bomber, from -> target));
        state.teams.updateTeamStats();
        float health = target.health;
        for(int tick = 0; tick < 239; tick++) bomber.controller().updateUnit();
        assertEquals(health, target.health, "The red warning must not cause early damage");
        target.set(26 * tilesize, 20 * tilesize);
        state.teams.updateTeamStats();
        bomber.controller().updateUnit();
        assertEquals(health, target.health, "Moving out of the marked location must dodge the attack");
        for(int tick = 0; tick < 329; tick++) bomber.controller().updateUnit();
        assertEquals(health, target.health);
        bomber.controller().updateUnit();
        assertTrue(target.health < health, "Remaining at the marked location must take damage");
        assertFalse(target.dead, "A single bombardment should leave time to recover");
    }

    @Test
    void laterStagesKeepIncreasingHealthDamageAndAttackRate(){
        for(Kind kind : Kind.values()){
            Unit early = PinyinEnemyController.type(kind).create(Team.crux);
            Unit middle = PinyinEnemyController.type(kind).create(Team.crux);
            Unit late = PinyinEnemyController.type(kind).create(Team.crux);
            PinyinEnemyController.equip(early, kind, 1);
            PinyinEnemyController.equip(middle, kind, 4);
            PinyinEnemyController.equip(late, kind, 40);
            assertTrue(early.maxHealth < middle.maxHealth);
            assertTrue(middle.maxHealth < late.maxHealth);
            assertTrue(early.mounts[0].weapon.bullet.damage < middle.mounts[0].weapon.bullet.damage);
            assertTrue(middle.mounts[0].weapon.bullet.damage < late.mounts[0].weapon.bullet.damage);
            assertTrue(early.mounts[0].weapon.reload > middle.mounts[0].weapon.reload);
            assertTrue(middle.mounts[0].weapon.reload > late.mounts[0].weapon.reload);
        }
        var unlocked = new java.util.HashSet<Kind>();
        for(int i = 0; i < 30; i++) unlocked.add(PinyinEnemyController.regularKind(i, 6));
        assertEquals(10, unlocked.size());
        assertFalse(unlocked.contains(Kind.guardian));
    }

    @Test
    void iceSlowsBurstHasThreeShotsAndSniperWaitsForItsTelegraph(){
        Unit ice = PinyinEnemyController.type(Kind.ice).create(Team.crux);
        PinyinEnemyController.equip(ice, Kind.ice, 1);
        assertEquals(StatusEffects.slow, ice.mounts[0].weapon.bullet.status);
        assertEquals(120f, ice.mounts[0].weapon.bullet.statusDuration);
        Unit burst = PinyinEnemyController.type(Kind.burst).create(Team.crux);
        PinyinEnemyController.equip(burst, Kind.burst, 1);
        assertEquals(3, burst.mounts[0].weapon.shoot.shots);
        assertTrue(burst.mounts[0].weapon.shoot.shotDelay > 0f);
        Unit target = UnitTypes.alpha.create(Team.sharded);
        target.set(20 * tilesize, 20 * tilesize);
        Unit sniper = PinyinEnemyController.type(Kind.sniper).create(Team.crux);
        sniper.set(10 * tilesize, 20 * tilesize);
        PinyinEnemyController.equip(sniper, Kind.sniper, 1);
        sniper.controller(new PinyinEnemyController(Kind.sniper, from -> target));
        for(int tick = 0; tick < 239; tick++) sniper.controller().updateUnit();
        assertEquals(0, Groups.bullet.size(), "A sniper cannot fire during its warning");
        sniper.controller().updateUnit();
        assertEquals(1, Groups.bullet.size());
    }

    @Test
    void advancedArtilleryHitsCrossArmsOnlyAfterWarning(){
        Unit target = UnitTypes.alpha.spawn(Team.sharded, 20 * tilesize, 20 * tilesize);
        Unit enemy = UnitTypes.fortress.spawn(Team.crux, 10 * tilesize, 20 * tilesize);
        PinyinEnemyController.equip(enemy, Kind.artillery, 3);
        enemy.controller(new PinyinEnemyController(Kind.artillery, from -> target, 3));
        for(int tick = 0; tick < 150; tick++) enemy.controller().updateUnit();
        target.x += 4f * tilesize;
        state.teams.updateTeamStats();
        float health = target.health;
        for(int tick = 0; tick < 89; tick++) enemy.controller().updateUnit();
        assertEquals(health, target.health);
        enemy.controller().updateUnit();
        assertTrue(target.health < health, "Cross-shaped bombardment must hit its marked arms");
    }
    @Test
    void biomePoolsAndSeededBossRollsProduceDifferentLevelEncounters(){
        var allBosses = new java.util.HashSet<Kind>();
        var pools = new java.util.HashSet<java.util.Set<Kind>>();
        for(int theme = 0; theme < 4; theme++){
            var kinds = new java.util.HashSet<Kind>();
            for(int i = 0; i < 20; i++){
                Kind kind = PinyinEnemyController.regularKind(i, 8, theme);
                assertFalse(PinyinEnemyController.boss(kind));
                kinds.add(kind);
            }
            pools.add(kinds);
            for(int roll = 0; roll < 3; roll++){
                Kind boss = PinyinEnemyController.bossKind(theme, roll);
                assertTrue(PinyinEnemyController.boss(boss));
                allBosses.add(boss);
            }
        }
        assertEquals(4, pools.size());
        assertEquals(5, allBosses.size());
        var icePool = new java.util.HashSet<Kind>();
        for(int i = 0; i < 10; i++) icePool.add(PinyinEnemyController.regularKind(i, 1, 2));
        assertTrue(icePool.contains(Kind.ice));
    }

    @Test
    void stormRotatesRingsWhileFrostAlternatesSlowingFanAndRing(){
        Unit target = UnitTypes.alpha.create(Team.sharded);
        target.set(35 * tilesize, 20 * tilesize);
        Unit storm = PinyinEnemyController.type(Kind.storm).create(Team.crux);
        storm.set(10 * tilesize, 20 * tilesize);
        PinyinEnemyController.equip(storm, Kind.storm, 1);
        storm.controller(new PinyinEnemyController(Kind.storm, from -> target));
        for(int tick = 0; tick < 180; tick++) storm.controller().updateUnit();
        assertEquals(12, Groups.bullet.size());
        float angle = Groups.bullet.first().rotation();
        for(int tick = 0; tick < 65; tick++) storm.controller().updateUnit();
        assertEquals(24, Groups.bullet.size());
        assertNotEquals(angle, Groups.bullet.index(12).rotation());
        Groups.bullet.clear();
        Unit frost = PinyinEnemyController.type(Kind.frost).create(Team.crux);
        frost.set(storm);
        PinyinEnemyController.equip(frost, Kind.frost, 1);
        frost.controller(new PinyinEnemyController(Kind.frost, from -> target));
        for(int tick = 0; tick < 180; tick++) frost.controller().updateUnit();
        assertEquals(7, Groups.bullet.size());
        assertEquals(StatusEffects.slow, Groups.bullet.first().type.status);
        for(int tick = 0; tick < 180; tick++) frost.controller().updateUnit();
        assertEquals(19, Groups.bullet.size());
    }

    @Test
    void ramWaitsForWarningThenChargesWithSideShots(){
        Unit target = UnitTypes.alpha.create(Team.sharded);
        target.set(20 * tilesize, 20 * tilesize);
        Unit ram = PinyinEnemyController.type(Kind.ram).create(Team.crux);
        ram.set(10 * tilesize, 20 * tilesize);
        PinyinEnemyController.equip(ram, Kind.ram, 1);
        ram.controller(new PinyinEnemyController(Kind.ram, from -> target));
        for(int tick = 0; tick < 239; tick++) ram.controller().updateUnit();
        assertEquals(0, Groups.bullet.size());
        for(int tick = 0; tick < 14; tick++) ram.controller().updateUnit();
        assertTrue(Groups.bullet.size() >= 2, "Charge must release lateral shots after its warning");
        assertTrue(ram.vel.len() <= target.speed() * 0.85f + 0.01f);
    }

    @Test
    void siegeCrossBombardmentIsTelegraphedEvenInFirstChapter(){
        Unit target = UnitTypes.alpha.spawn(Team.sharded, 20 * tilesize, 20 * tilesize);
        Unit siege = PinyinEnemyController.type(Kind.siege).spawn(Team.crux, 10 * tilesize, 20 * tilesize);
        PinyinEnemyController.equip(siege, Kind.siege, 1);
        siege.controller(new PinyinEnemyController(Kind.siege, from -> target));
        for(int tick = 0; tick < 150; tick++) siege.controller().updateUnit();
        target.x += 4f * tilesize;
        state.teams.updateTeamStats();
        float health = target.health;
        for(int tick = 0; tick < 89; tick++) siege.controller().updateUnit();
        assertEquals(health, target.health);
        siege.controller().updateUnit();
        assertTrue(target.health < health);
    }
}
