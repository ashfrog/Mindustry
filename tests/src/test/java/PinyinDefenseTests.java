import arc.math.geom.*;
import arc.math.*;
import arc.struct.Seq;
import mindustry.game.*;
import mindustry.game.PinyinDefense.*;
import org.junit.jupiter.api.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinDefenseTests{
    @Test
    void constructionCooldownBlocksSpamWithoutSpendingAndIsIndependentForBothPlayers(){
        PinyinDefense game = new PinyinDefense(19, 1);
        assertTrue(game.reserveConstruction(0));
        game.answer(true, 0, game.deck.first());
        int resources = game.resources;
        assertFalse(game.reserveConstruction(0));
        assertEquals(resources, game.resources);
        assertTrue(game.reserveConstruction(1), "P1 cooldown must not lock P2");
        game.answer(false, 1, game.deck.first());
        assertFalse(game.reserveConstruction(1), "A wrong answer must not skip cooldown");
        for(int tick = 0; tick < 120; tick++) game.update(1f / 30f);
        assertFalse(game.reserveConstruction(0), "Cooldown must last five seconds");
        for(int tick = 0; tick < 32; tick++) game.update(1f / 30f);
        assertTrue(game.reserveConstruction(0));
    }

    @Test
    void distractorsMixSyllablesAndTonesWhileKeepingExactlyOneAnswer(){
        PinyinDefense game = new PinyinDefense(4, 1);
        Set<String> variants = new HashSet<>();
        boolean toneConfusion = false;
        for(PinyinQuestions.Question question : PinyinQuestions.all) for(int seed = 0; seed < 16; seed++){
            PinyinDefensePickup drops = new PinyinDefensePickup(game.map, 0, game.deck.first(), question, new Rand(seed), new Seq<>());
            Set<String> unique = new HashSet<>(Arrays.asList(drops.choices));
            assertEquals(3, unique.size());
            int right = 0, differentSyllables = 0;
            String plain = java.text.Normalizer.normalize(question.pinyin, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            for(int choice = 0; choice < 3; choice++){
                if(drops.correct(choice)) right++;
                else{
                    String value = java.text.Normalizer.normalize(drops.choices[choice], java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                    if(!value.equals(plain)) differentSyllables++;
                    else toneConfusion = true;
                }
            }
            assertEquals(1, right);
            assertTrue(differentSyllables >= 1, "Not just tone changes: " + question.pinyin);
            if(question == PinyinQuestions.all[0]) variants.add(unique.toString());
        }
        assertTrue(toneConfusion);
        assertTrue(variants.size() > 4, "Repeated questions need varied decoys");
    }

    @Test
    void combatFeedbackFiresOnceAtImpactShieldBreakAndDestruction(){
        PinyinDefense game = new PinyinDefense(42, 1);
        game.deck.clear(); game.deck.add(TowerKind.rapid);
        Arrays.fill(game.towers, null);
        game.reserveResource(); game.answer(true, 0, TowerKind.rapid);
        game.towers[0].level = 50;
        Enemy enemy = game.spawn(EnemyKind.shield, 0);
        float closest = Float.MAX_VALUE;
        for(float progress = 0; progress < game.map.lengths[0]; progress += 4f){
            float distance = game.map.position(0, progress, new Vec2()).dst(game.map.pads[0]);
            if(distance < closest){ closest = distance; enemy.progress = progress; }
        }
        game.map.position(0, enemy.progress, enemy.position);
        enemy.health = 20f; enemy.shield = 1f;
        Set<Shot> impacts = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] events = new int[4];
        game.feedback = new Feedback(){
            @Override public void fired(Shot shot){ events[0]++; }
            @Override public void impacted(Shot shot){ assertTrue(impacts.add(shot), "Impact played twice"); }
            @Override public void destroyed(Enemy dead){ assertSame(enemy, dead); events[1]++; }
            @Override public void destroyed(Tower tower){ events[2]++; }
            @Override public void shieldBroken(Enemy hit){ assertSame(enemy, hit); events[3]++; }
        };
        for(int tick = 0; tick < 90; tick++) game.update(1f / 30f);
        assertTrue(events[0] > 0);
        assertFalse(impacts.isEmpty());
        assertEquals(1, events[1]);
        assertEquals(1, events[3]);
        assertEquals(game.kills, events[1]);
        game.towers[0].health = 0;
        for(int tick = 0; tick < 3; tick++) game.update(1f / 30f);
        assertEquals(1, events[2]);
    }

    @Test
    void droppedAnswersLandBeforeCollectionAndCanOnlyBeCollectedOnce(){
        PinyinDefense game = new PinyinDefense(31, 1);
        PinyinDefensePickup drops = new PinyinDefensePickup(game.map, 0, game.deck.first(), PinyinQuestions.all[0], new Rand(14), new Seq<>());
        int choice = 0;
        while(!drops.correct(choice)) choice++;
        Vec2 point = drops.positions[choice];
        assertEquals(-1, drops.collect(point.x, point.y, point.x, point.y), "Falling crates must not auto-answer");
        drops.age = PinyinDefensePickup.landingTime;
        assertTrue(game.reserveResource());
        int level = game.towers[0].level;
        int picked = drops.collect(point.x - 28f, point.y, point.x + 28f, point.y);
        assertEquals(choice, picked, "A dash through a crate must collect it");
        game.answer(drops.correct(picked), drops.pad, drops.kind);
        assertEquals(level + 1, game.towers[0].level);
        assertEquals(-1, drops.collect(point.x, point.y, point.x, point.y), "The second pilot cannot reuse a group");
        Vec2 other = drops.positions[(choice + 1) % 3];
        assertEquals(-1, drops.collect(other.x, other.y, other.x, other.y), "All alternatives disappear together");
    }

    @Test
    void wrongWorldPickupSpendsTheCrystalWithoutBuilding(){
        PinyinDefense game = new PinyinDefense(12, 1);
        PinyinDefensePickup drops = new PinyinDefensePickup(game.map, 2, game.deck.first(), PinyinQuestions.all[2], new Rand(9), new Seq<>());
        int choice = 0;
        while(drops.correct(choice)) choice++;
        Vec2 point = drops.positions[choice];
        drops.age = 1f;
        assertTrue(game.reserveResource());
        int picked = drops.collect(point.x, point.y, point.x, point.y);
        assertFalse(drops.correct(picked));
        game.answer(false, drops.pad, drops.kind);
        assertNull(game.towers[2]);
        assertEquals(1, game.wrong);
        assertEquals(1, game.resources);
    }

    @Test
    void worldAnswersStaySeparatedAndInsideAllMapEdges(){
        for(int seed = 0; seed < 120; seed++){
            PinyinDefense game = new PinyinDefense(seed, 1);
            for(int pad = 0; pad < game.towers.length; pad++){
                PinyinDefensePickup drops = new PinyinDefensePickup(game.map, pad, game.deck.first(), PinyinQuestions.all[0], new Rand(seed), Seq.with(game.map.pads[pad]));
                for(int i = 0; i < 3; i++){
                    Vec2 point = drops.positions[i];
                    assertTrue(point.x >= 24f && point.x <= PinyinDefenseMap.width * 8f - 24f, "Clipped pickup: " + point);
                    assertTrue(point.y >= 24f && point.y <= PinyinDefenseMap.height * 8f - 24f, "Clipped pickup: " + point);
                    for(int j = 0; j < i; j++) assertTrue(point.dst(drops.positions[j]) > 60f);
                }
            }
        }
    }

    @Test
    void generatedRoutesReachTheBaseAndSocketsStayBesideRoads(){
        Set<String> layouts = new HashSet<>();
        for(int seed = 0; seed < 120; seed++){
            PinyinDefenseMap map = new PinyinDefenseMap(seed, 1 + seed % 6);
            layouts.add(map.shape + ":" + map.routes.first()[0] + ":" + map.base);
            for(int r = 0; r < map.routes.size; r++){
                assertTrue(map.lengths[r] > 650f);
                assertEquals(map.base, map.position(r, map.lengths[r] + 1f, new Vec2()));
                for(float p = 0; p < map.lengths[r]; p += 8f){
                    Vec2 point = map.position(r, p, new Vec2());
                    assertTrue(point.x >= 0 && point.x < PinyinDefenseMap.width * 8);
                    assertTrue(point.y >= 0 && point.y < PinyinDefenseMap.height * 8);
                    assertTrue(map.roadDistance(point.x, point.y) < 0.01f);
                }
            }
            for(int i = 0; i < map.pads.length; i++){
                Vec2 pad = map.pads[i];
                assertTrue(map.roadDistance(pad.x, pad.y) >= 24f, "Socket on a road, seed " + seed);
                assertTrue(map.roadDistance(pad.x, pad.y) < 100f, "Socket cannot cover road, seed " + seed);
                for(int j = 0; j < i; j++) assertTrue(pad.dst(map.pads[j]) > 25f, "Overlapping sockets, seed " + seed);
            }
        }
        assertTrue(layouts.size() > 100);
    }

    @Test
    void crystalAndCorrectPronunciationAreBothRequiredForUpgrades(){
        PinyinDefense game = new PinyinDefense(42, 1);
        int level = game.towers[0].level;
        game.answer(true, 0, game.deck.first());
        assertEquals(level, game.towers[0].level, "No upgrade without a reserved crystal");
        assertTrue(game.reserveResource());
        game.answer(false, 0, game.deck.first());
        assertEquals(level, game.towers[0].level);
        assertTrue(game.reserveResource());
        game.answer(true, 0, game.deck.first());
        assertEquals(level + 1, game.towers[0].level);
        game.answer(true, 0, game.deck.first());
        assertEquals(level + 1, game.towers[0].level, "An answer cannot be collected twice");
        assertFalse(game.reserveResource());
        for(int i = 0; i < 151; i++) game.update(1f / 30f);
        assertEquals(1, game.resources);
        for(int i = 0; i < 1000; i++) game.update(1f / 30f);
        assertEquals(3, game.resources, "Idle time cannot bank unlimited upgrades");
    }

    @Test
    void continuousCorrectAnswersCanDefendTwentyWavesAcrossDifferentMaps(){
        for(int chapter : new int[]{1, 3, 6}) for(int seed = 0; seed < 12; seed++){
            PinyinDefense game = simulate(seed, chapter, 0, false);
            String details = "seed=" + seed + " chapter=" + chapter + " wave=" + game.wave + " base=" + game.baseHealth
                + " correct=" + game.correct + " kills=" + game.kills + " enemies=" + game.enemies.size;
            assertTrue(game.won, details);
            assertEquals(20, game.wave);
            assertTrue(game.kills > 120, "Monsters must die along the road");
            assertTrue(game.towerDamage > game.playerDamage * 15f, "Towers supply the primary damage");
        }
    }

    @Test
    void aSoloPilotCanTravelToSocketsAndCollectEnoughUpgradesToDefend(){
        for(int seed = 0; seed < 12; seed++){
            PinyinDefense game = new PinyinDefense(seed, 6);
            Vec2 pilot = game.map.pads[0].cpy(), movement = new Vec2();
            PinyinDefensePickup drops = null;
            int targetPad = 0;
            Rand random = new Rand(seed);
            float dt = 1f / 30f;
            for(int tick = 0; tick < 30 * 750 && !game.won && !game.lost; tick++){
                if(drops == null && game.resources > 0){
                    targetPad = game.recommendedPad();
                    Vec2 target = game.map.pads[targetPad];
                    pilot.add(movement.set(target).sub(pilot).limit(240f * dt));
                    if(pilot.dst(target) < 30f && game.reserveConstruction(0))
                        drops = new PinyinDefensePickup(game.map, targetPad, game.recommendedKind(targetPad), PinyinQuestions.all[seed], random, Seq.with(pilot));
                }else if(drops != null){
                    drops.age += dt;
                    // Allow time to read the short prompt, then physically walk into the right crate.
                    if(drops.age > 1.2f){
                        int right = 0;
                        while(!drops.correct(right)) right++;
                        float x = pilot.x, y = pilot.y;
                        pilot.add(movement.set(drops.positions[right]).sub(pilot).limit(240f * dt));
                        int collected = drops.collect(x, y, pilot.x, pilot.y);
                        if(collected >= 0){
                            assertTrue(drops.correct(collected), "A correct approach should not cross another crate");
                            game.answer(true, drops.pad, drops.kind);
                            drops = null;
                        }
                    }
                }
                game.update(dt);
                game.playerAttack(0, pilot.x, pilot.y, dt);
            }
            assertTrue(game.won, "Travel/pickup economy failed: seed=" + seed + " wave=" + game.wave + " upgrades=" + game.correct);
        }
    }

    @Test
    void neglectAndRepeatedMistakesLetRealEnemiesContinuouslyAttackTheBase(){
        PinyinDefense idle = simulate(12, 1, 0, true);
        assertTrue(idle.lost);
        assertTrue(idle.baseDamage >= 1000f);
        assertTrue(idle.leaked > 15);
        PinyinDefense mistakes = simulate(12, 3, 3, false);
        assertTrue(mistakes.baseDamage > 0f, "Wrong answers must create real breakthroughs");
        assertTrue(mistakes.leaked > 3, "Enemies stay at the base and attack repeatedly");
        PinyinDefense early = new PinyinDefense(1, 1), late = new PinyinDefense(1, 7);
        late.wave = 18;
        assertTrue(late.mistakeTolerance() < early.mistakeTolerance());
    }

    @Test
    void aFewConsecutiveMissesInLateWavesCauseSustainedBaseAttacks(){
        PinyinDefense game = new PinyinDefense(82, 5);
        int misses = 0;
        for(int tick = 0; tick < 30 * 650 && !game.won && !game.lost; tick++){
            if(game.resources > 0){
                int pad = game.recommendedPad();
                game.reserveResource();
                boolean right = game.wave < 13 || misses >= 3;
                if(!right) misses++;
                game.answer(right, pad, game.recommendedKind(pad));
            }
            game.update(1f / 30f);
        }
        assertEquals(3, misses);
        assertTrue(game.baseDamage >= 30f, "Three misses should create a visible breakthrough, got " + game.baseDamage);
        assertTrue(game.leaked >= 3, "Surviving attackers must keep hitting the base");
    }

    @Test
    void everyTowerFiresAndControlWeaponsApplyTheirDistinctEffects(){
        for(TowerKind kind : TowerKind.values()){
            PinyinDefense game = new PinyinDefense(kind.ordinal() + 100L, 1);
            game.deck.clear(); game.deck.add(kind);
            Arrays.fill(game.towers, null);
            game.reserveResource(); game.answer(true, 0, kind);
            Enemy enemy = game.spawn(EnemyKind.armor, 0);
            float closest = Float.MAX_VALUE;
            for(float progress = 0f; progress < game.map.lengths[0]; progress += 2f){
                Vec2 position = game.map.position(0, progress, new Vec2());
                float distance = position.dst(game.map.pads[0]);
                if(distance < closest){ closest = distance; enemy.progress = progress; }
            }
            game.map.position(0, enemy.progress, enemy.position);
            float before = enemy.health;
            for(int tick = 0; tick < 45; tick++) game.update(1f / 30f);
            assertTrue(game.towerDamage > 0f, kind + " never dealt damage");
            assertTrue(enemy.health < before);
            if(kind == TowerKind.frost || kind == TowerKind.vortex) assertTrue(enemy.slow > 0f, kind.name());
            if(kind == TowerKind.flame) assertTrue(enemy.burn > 0f);
            if(kind == TowerKind.poison) assertTrue(enemy.poison > 0f);
        }
    }

    @Test
    void generatedWorldHasAnActualCoreAndClearRoadsAcrossAllBiomes(){
        ApplicationTests.launchApplication();
        for(int chapter = 1; chapter <= 6; chapter++){
            mindustry.Vars.logic.reset();
            PinyinDefenseMap map = new PinyinDefenseMap(4000L + chapter, chapter);
            mindustry.Vars.world.loadGenerator(PinyinDefenseMap.width, PinyinDefenseMap.height, map::generate);
            assertNotNull(mindustry.game.Team.sharded.core());
            for(int r = 0; r < map.routes.size; r++) for(float p = 0f; p < map.lengths[r] - 40f; p += 4f){
                Vec2 point = map.position(r, p, new Vec2());
                assertFalse(mindustry.Vars.world.tileWorld(point.x, point.y).solid());
            }
        }
    }

    @Test
    void catalogAndAttackFamiliesVaryBetweenMaps(){
        Set<TowerKind> towers = EnumSet.noneOf(TowerKind.class);
        Set<EnemyKind> enemies = EnumSet.noneOf(EnemyKind.class);
        String previous = "";
        for(int chapter = 1; chapter <= 14; chapter++){
            PinyinDefense game = new PinyinDefense(chapter, chapter);
            Set<TowerKind> choices = EnumSet.noneOf(TowerKind.class);
            for(TowerKind kind : game.deck) choices.add(kind);
            assertEquals(6, choices.size());
            assertNotEquals(previous, game.deck.toString());
            previous = game.deck.toString();
            for(TowerKind kind : game.deck) towers.add(kind);
            for(int wave = 1; wave <= 20; wave++) for(int i = 0; i < 16; i++) enemies.add(game.roster(wave, i));
        }
        assertEquals(TowerKind.values().length, towers.size());
        assertEquals(EnemyKind.values().length - 1, enemies.size());
    }

    private PinyinDefense simulate(long seed, int chapter, int failEvery, boolean idle){
        PinyinDefense game = new PinyinDefense(seed, chapter);
        int attempts = 0;
        for(int tick = 0; tick < 30 * 750 && !game.won && !game.lost; tick++){
            if(!idle && game.resources > 0){
                int pad = game.recommendedPad();
                game.reserveResource();
                game.answer(failEvery == 0 || ++attempts % failEvery != 0, pad, game.recommendedKind(pad));
            }
            game.update(1f / 30f);
            for(int slot = 0; slot < 2; slot++) game.playerAttack(slot, game.map.base.x, game.map.base.y, 1f / 30f);
        }
        return game;
    }
}
