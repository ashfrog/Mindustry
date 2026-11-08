import arc.math.geom.*;
import arc.util.*;
import java.util.*;
import mindustry.ai.types.*;
import mindustry.ai.types.PinyinEnemyController.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.PinyinSurvival.*;
import mindustry.gen.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinSurvivalTests{
    @BeforeAll
    static void init(){ ApplicationTests.launchApplication(); }

    @BeforeEach
    void setup(){
        logic.reset();
        world.loadGenerator(176, 408, tiles -> tiles.fill());
        state.rules.unitCap = 260;
        state.rules.unitCapVariable = false;
        Time.delta = 1f;
    }

    @Test
    void wavesContinueAndLaterSquadsHaveMoreEnemiesAndEveryRoleAndAttack(){
        assertTrue(PinyinSurvival.waveSize(40) > PinyinSurvival.waveSize(1));
        assertTrue(PinyinSurvival.enemyLimit(40) > PinyinSurvival.enemyLimit(1));
        assertTrue(PinyinSurvival.interval(40) < PinyinSurvival.interval(1));
        Set<Role> roles = new HashSet<>();
        Set<Kind> kinds = new HashSet<>();
        Set<Pattern> patterns = new HashSet<>();
        for(int wave = 1; wave <= 1000; wave++){
            assertTrue(PinyinSurvival.enemyLimit(wave) <= PinyinSurvival.maxEnemies);
            assertTrue(PinyinSurvival.waveSize(wave) > 0);
            assertTrue(PinyinSurvival.interval(wave) >= 60f * 20f, "Every wave leaves time to learn and build");
            for(int slot = 0; slot < PinyinSurvival.waveSize(wave); slot++){
                roles.add(PinyinSurvival.role(wave, slot));
                kinds.add(PinyinSurvival.kind(wave, slot));
                patterns.add(PinyinSurvival.pattern(wave, slot));
            }
        }
        assertEquals(Role.values().length, roles.size());
        assertEquals(Pattern.values().length, patterns.size());
        assertEquals(23, kinds.size());
        assertTrue(PinyinSurvival.healthScale(1000) > PinyinSurvival.healthScale(100));
    }

    @Test
    void roadblocksLeadThePlayerAndFlankersOccupyBothSidesThenCloseIn(){
        Unit target = UnitTypes.alpha.create(Team.sharded);
        target.set(600f, 900f);
        target.vel.set(2f, 0f);
        Vec2 front = PinyinSurvival.destination(new Vec2(), target, Role.blocker, 0, 1, 0f);
        Vec2 left = PinyinSurvival.destination(new Vec2(), target, Role.flanker, 0, 0, 0f);
        Vec2 right = PinyinSurvival.destination(new Vec2(), target, Role.flanker, 0, 1, 0f);
        Vec2 converged = PinyinSurvival.destination(new Vec2(), target, Role.flanker, 0, 0, 320f);
        assertTrue(front.x > target.x + 70f, "Roadblocks must intercept ahead of motion");
        assertTrue(left.y > target.y && right.y < target.y, "Flankers need opposite sides");
        assertTrue(converged.dst(target) < left.dst(target), "Squads must converge after surrounding");
    }

    @Test
    void spawningRemainsSafeAtEveryMapEdge(){
        Unit target = UnitTypes.alpha.create(Team.sharded);
        for(float x : new float[]{12f, 704f, 1395f}) for(float y : new float[]{12f, 1600f, 3250f}){
            target.set(x, y);
            for(int angle = 0; angle < 360; angle += 15){
                Vec2 point = PinyinSurvival.spawnPoint(new Vec2(), target, angle, 300f, 1408f, 3264f);
                assertTrue(point.x >= 26f && point.x <= 1382f && point.y >= 26f && point.y <= 3238f);
                assertTrue(point.dst(target) >= 140f, "Edge clamping must not put enemies on the player");
            }
        }
    }

    @Test
    void tankIsToughRunnerIsFastAndAllCarriersFlyWithSafeMounts(){
        float baseHealth = UnitTypes.flare.health;
        for(Role role : Role.values()){
            Unit enemy = PinyinSurvival.carrier(role, false).create(Team.crux);
            assertTrue(enemy.type.flying);
            PinyinSurvivalController.equip(enemy, Kind.mono, role, 15, false);
            assertTrue(enemy.mounts.length > 0);
            assertEquals(role.size, enemy.hitSize);
            assertTrue(Float.isFinite(enemy.maxHealth));
        }
        assertTrue(Role.tank.health > Role.runner.health * 5f);
        assertTrue(Role.runner.speed > Role.chaser.speed);
        assertEquals(baseHealth, UnitTypes.flare.health, "Role tuning must not mutate original content");
    }

    @Test
    void everyBarrageProducesRealProjectilesOnlyAfterItsWarning(){
        for(Pattern pattern : Pattern.values()){
            Groups.bullet.clear();
            Unit target = UnitTypes.alpha.spawn(Team.sharded, 600f, 900f);
            Unit enemy = UnitTypes.flare.spawn(Team.crux, 680f, 900f);
            PinyinSurvivalController.equip(enemy, Kind.gunner, Role.artillery, 5, false);
            enemy.controller(new PinyinSurvivalController(Kind.gunner, Role.artillery, pattern, 5, 0, 0, false, from -> target));
            for(int tick = 0; tick < 100; tick++) enemy.controller().updateUnit();
            assertEquals(0, Groups.bullet.size(), pattern + " fired before warning");
            for(int tick = 0; tick < 90; tick++) enemy.controller().updateUnit();
            assertEquals(PinyinSurvivalController.volleySize(pattern, false), Groups.bullet.size(), pattern.name());
            assertTrue(enemy.vel.len() > 0f);
            target.remove();
            enemy.remove();
        }
    }

    @Test
    void playerBulletFloodCannotSilenceEnemiesButEnemyBudgetStillApplies(){
        Unit target = UnitTypes.alpha.spawn(Team.sharded, 600f, 900f);
        Unit enemy = UnitTypes.flare.spawn(Team.crux, 680f, 900f);
        PinyinSurvivalController.equip(enemy, Kind.gunner, Role.artillery, 5, false);
        enemy.controller(new PinyinSurvivalController(Kind.gunner, Role.artillery, Pattern.aimed, 5, 0, 0, false, from -> target));
        var bullet = PinyinArsenal.create(PinyinArsenal.Kind.rapid, 1f, false);
        for(int i = 0; i < PinyinSurvival.maxEnemyBullets; i++) bullet.create(target, target.team, 600f, 900f, 0f);
        for(int tick = 0; tick < 190; tick++) enemy.controller().updateUnit();
        int enemyCount = 0;
        for(Bullet shot : Groups.bullet) if(shot.team == Team.crux) enemyCount++;
        assertTrue(enemyCount > 0, "Player projectiles must not consume the enemy budget");
        Groups.bullet.clear();
        for(int i = 0; i < PinyinSurvival.maxEnemyBullets; i++) bullet.create(enemy, enemy.team, 680f, 900f, 0f);
        for(int tick = 0; tick < 600; tick++) enemy.controller().updateUnit();
        assertEquals(PinyinSurvival.maxEnemyBullets, Groups.bullet.size());
    }

    @Test
    void answerTextAlwaysMatchesCorrectFlagInEitherSlot(){
        for(var question : PinyinQuestions.all) for(int slot = 0; slot < 2; slot++){
            String[] choices = PinyinSurvival.answerChoices(question, slot);
            assertEquals(question.pinyin, choices[slot]);
            assertNotEquals(question.pinyin, choices[1 - slot]);
        }
    }

    @Test
    void eachTenthWaveWaitsForBossDeathAndThenStartsAFreshRound(){
        PinyinSurvival.Rounds rounds = new PinyinSurvival.Rounds();
        for(int round = 1; round <= 1000; round++){
            assertEquals(round, rounds.round());
            assertEquals(0, rounds.wave());
            assertFalse(rounds.defeatBoss());
            for(int wave = 1; wave <= 10; wave++){
                assertTrue(rounds.nextWave());
                assertEquals(wave, rounds.wave());
                assertEquals((round - 1) * 10 + wave, rounds.totalWave());
                assertEquals(wave == 10, rounds.bossActive());
            }
            for(int timerTick = 0; timerTick < 100; timerTick++) assertFalse(rounds.nextWave());
            assertEquals(10, rounds.wave(), "A timer must never bypass a live boss");
            assertTrue(rounds.defeatBoss());
            assertFalse(rounds.bossActive());
        }
    }

    @Test
    void anyPlayerCanImmediatelyConsumeSharedAnswerExactlyOnce(){
        for(int owner = 0; owner < 2; owner++) for(int collector = 0; collector < 2; collector++){
            PinyinSurvival.AnswerAccess access = new PinyinSurvival.AnswerAccess(owner);
            assertTrue(access.canAnswer(0));
            assertTrue(access.canAnswer(1));
            assertTrue(access.collect(collector));
            assertTrue(access.complete());
            assertFalse(access.collect(0));
            assertFalse(access.collect(1), "Overlapping players and options must never award twice");
            access.age = 10000f;
            assertFalse(access.canAnswer(0));
        }
    }

    @Test
    void clearedWavesResumeWithinFiveSecondsButNeverSkipLiveBoss(){
        assertEquals(300f, PinyinSurvival.afterClear(1500f, false, false));
        assertEquals(12f, PinyinSurvival.afterClear(12f, false, false));
        assertEquals(1500f, PinyinSurvival.afterClear(1500f, true, false));
        assertEquals(1500f, PinyinSurvival.afterClear(1500f, false, true));
        for(int wave = 1; wave < 85; wave++){
            assertTrue(PinyinSurvival.waveSize(wave + 1) > PinyinSurvival.waveSize(wave));
            assertTrue(PinyinSurvival.healthScale(wave + 1) > PinyinSurvival.healthScale(wave));
            assertTrue(PinyinSurvival.damageScale(wave + 1) > PinyinSurvival.damageScale(wave));
        }
    }

    @Test
    void bossSurvivesBurstAndPiercingDamageButSustainedFireCanDefeatIt(){
        float nativeHealth = UnitTypes.antumbra.health;
        PinyinSurvivalBoss boss = PinyinSurvivalBoss.spawn(UnitTypes.antumbra, Team.crux, 600f, 900f);
        PinyinSurvivalController.equip(boss, Kind.gunner, Role.tank, 10, true);
        boss.shield = 0f;
        float max = boss.maxHealth;
        for(int hit = 0; hit < 100; hit++){
            boss.damage(100000000f);
            boss.damagePierce(100000000f);
        }
        assertFalse(boss.dead, "All damage channels must share the same per-frame budget");
        assertEquals(max * 0.96f, boss.health, max * 0.0001f);
        for(int tick = 0; tick < 600; tick++){
            boss.update();
            boss.rawDamage(100000000f);
        }
        assertFalse(boss.dead, "A boss must still be fighting after ten seconds of extreme damage");
        for(int tick = 0; tick < 1000 && !boss.dead; tick++){
            boss.update();
            boss.rawDamage(100000000f);
        }
        assertTrue(boss.dead, "Armor cannot make the boss invulnerable");
        assertEquals(nativeHealth, UnitTypes.antumbra.health);
    }

    @Test
    void weaponGrowthPassesOldCapsAndSurvivesDeathWithoutAffectingOtherPlayer(){
        PinyinTeamEffects effects = new PinyinTeamEffects(), other = new PinyinTeamEffects();
        effects.enableEndlessGrowth();
        Unit first = UnitTypes.alpha.create(Team.sharded), second = UnitTypes.alpha.create(Team.sharded);
        other.inherit(second);
        float previousDamage = 0f;
        for(int level = 1; level <= 10000; level++){
            effects.correct(0, false, null, PinyinTeamEffects.Reward.rapid);
            if(level == 12 || level == 100 || level == 1000 || level == 10000){
                effects.inherit(first);
                float damage = first.mounts[0].weapon.bullet.damage;
                assertTrue(Float.isFinite(damage) && damage > previousDamage);
                assertEquals(level, effects.power());
                previousDamage = damage;
            }
        }
        effects.update(60f * 3600f, first);
        assertEquals(10000, effects.power(), "Survival growth cannot time out");
        effects.wrong(first);
        assertEquals(9999, effects.power());
        assertTrue(first.mounts[0].weapon.bullet.damage < previousDamage);
        Unit revived = UnitTypes.alpha.create(Team.sharded);
        effects.inherit(revived);
        assertEquals(first.mounts[0].weapon.bullet.damage, revived.mounts[0].weapon.bullet.damage);
        assertEquals(0, other.power());
        assertEquals(UnitTypes.alpha.weapons.first().bullet.damage * 2f, second.mounts[0].weapon.bullet.damage);
    }
}
