import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ai.types.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinAdventureTests{
    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
    }

    @Test
    void randomMapsKeepEveryObjectiveConnectedWithinItsStage(){
        for(int seed = 0; seed < 100; seed++){
            PinyinAdventureMap map = new PinyinAdventureMap(seed);
            for(int stage = 0; stage < 4; stage++){
                boolean[] visited = reachable(map, stage, false);
                assertTrue(visited[index(map.exits[stage])], "Unreachable exit, seed " + seed);
                assertTrue(visited[index(map.beacons[stage])], "Unreachable beacon, seed " + seed);
                assertTrue(visited[index(map.workshops[stage])], "Unreachable workshop, seed " + seed);
                assertTrue(visited[index(map.ships[stage])], "Unreachable rescue ship, seed " + seed);
                assertTrue(map.routes[stage].length >= 7, "Each stage needs a readable route spine");
                for(int i = stage * 2; i < stage * 2 + 2; i++) assertTrue(visited[index(map.rescues[i])], "Unreachable helper rescue");
                int start = stage * PinyinAdventureMap.chestsPerStage;
                for(int i = start; i < start + PinyinAdventureMap.chestsPerStage; i++) assertTrue(visited[index(map.chests[i])], "Unreachable chest, seed " + seed);
                for(int i = stage * PinyinAdventureMap.padsPerStage; i < (stage + 1) * PinyinAdventureMap.padsPerStage; i++){
                    assertTrue(visited[index(map.turretPads[i])]);
                }
            }
        }
    }

    @Test
    void ActualBuildingsDoNotBlockRoutesOrChestInteraction(){
        for(int seed = 0; seed < 12; seed++){
            logic.reset();
            PinyinAdventureMap map = new PinyinAdventureMap(seed);
            world.loadGenerator(PinyinAdventureMap.width, PinyinAdventureMap.height, map::generate);
            state.rules.unitCap = 50;
            state.rules.unitCapVariable = false;
            assertNotNull(Team.sharded.core());
            for(int i = 0; i < map.turretPads.length; i++){
                Point2 pad = map.turretPads[i];
                assertEquals(Blocks.air, world.tile(pad.x, pad.y).block());
                world.tile(pad.x, pad.y).setBlock(PinyinTurretPads.turret(i), Team.sharded, 0);
            }
            for(int stage = 0; stage < 4; stage++){
                Point2 workshop = map.workshops[stage];
                var operator = UnitTypes.alpha.spawn(Team.sharded, workshop.x * tilesize, workshop.y * tilesize);
                operator.isShooting = true;
                PinyinStageWorks works = new PinyinStageWorks();
                works.reset(stage, 1, workshop);
                works.update(60f, 100, operator, null);
                assertTrue(works.activated());
                operator.remove();
            }
            for(int stage = 0; stage < 4; stage++){
                boolean[] visited = reachable(map, stage, true);
                assertTrue(visited[index(map.exits[stage])]);
                assertTrue(visited[index(map.beacons[stage])]);
                assertTrue(visited[index(map.workshops[stage])], "Workshop entrance must remain open after construction");
                assertTrue(visited[index(map.ships[stage])], "Ship entrance must remain open after construction");
                for(int i = stage * 2; i < stage * 2 + 2; i++) assertTrue(visited[index(map.rescues[i])]);
                int start = stage * PinyinAdventureMap.chestsPerStage;
                for(int i = start; i < start + PinyinAdventureMap.chestsPerStage; i++){
                    Point2 chest = map.chests[i];
                    assertEquals(Blocks.air, world.tile(chest.x, chest.y).block());
                    assertTrue(visited[index(chest)], "Players must walk straight through bunkers");
                    boolean accessible = false;
                    for(Point2 direction : Geometry.d4){
                        if(visited[chest.x + direction.x * 2 + (chest.y + direction.y * 2) * PinyinAdventureMap.width]) accessible = true;
                    }
                    assertTrue(accessible, "Chest is inaccessible after placing turrets");
                }
            }
        }
    }

    @Test
    void seedsChangeRoutesAndChestLocationsReproducibly(){
        PinyinAdventureMap first = new PinyinAdventureMap(25), same = new PinyinAdventureMap(25), other = new PinyinAdventureMap(26);
        assertArrayEquals(first.chests, same.chests);
        assertArrayEquals(first.patterns, same.patterns);
        assertArrayEquals(first.themes, same.themes);
        assertArrayEquals(first.bossRolls, same.bossRolls);
        assertFalse(Arrays.equals(first.chests, other.chests));
        assertFalse(Arrays.equals(first.exits, other.exits));
    }

    @Test
    void seedsUseEveryLayoutAndDifferentEntranceSides(){
        Set<Integer> patterns = new HashSet<>(), entrySides = new HashSet<>();
        for(int seed = 0; seed < 40; seed++){
            PinyinAdventureMap map = new PinyinAdventureMap(seed);
            assertEquals(48, map.chests.length);
            for(int stage = 1; stage < 4; stage++){
                patterns.add(map.patterns[stage]);
                Point2 entry = map.entrances[stage];
                entrySides.add(entry.x == PinyinAdventureMap.left(stage) + 9 ? 0
                    : entry.y == 13 ? 1 : entry.x == PinyinAdventureMap.right(stage) - 9 ? 2 : 3);
            }
        }
        assertEquals(5, patterns.size());
        assertEquals(4, entrySides.size());
    }

    @Test
    void progressionRequiresOnlyBossThenLoopsEndlessly(){
        PinyinAdventureProgress progress = new PinyinAdventureProgress();
        progress.reset();
        for(int chapter = 1; chapter <= 3; chapter++){
            for(int stage = 0; stage < 4; stage++){
                assertEquals(stage, progress.stage());
                assertEquals(chapter, progress.chapter());
                assertEquals((chapter - 1) * 4 + stage + 1, progress.difficulty());
                assertFalse(progress.ready());
                assertFalse(progress.advance());
                // Answers, facilities, kills and beacon charging are optional side content.
                progress.answer();
                progress.defeat(false);
                assertFalse(progress.ready(), "Normal enemies cannot clear a stage");
                progress.defeat(true);
                assertTrue(progress.ready());
                assertEquals(stage == 3, progress.advance());
                assertEquals(0, progress.answers());
                assertEquals(0, progress.kills());
            }
        }
        assertEquals(4, progress.chapter());
        assertEquals(0, progress.stage());
    }

    @Test
    void laterStagesAndChaptersIncreaseEnemyPressure(){
        PinyinAdventureProgress progress = new PinyinAdventureProgress();
        progress.reset();
        int firstSize = progress.waveSize(), firstLimit = progress.enemyLimit();
        float firstInterval = progress.waveInterval();
        for(int i = 0; i < 40; i++){
            int size = progress.waveSize(), limit = progress.enemyLimit();
            float interval = progress.waveInterval();
            for(int j = 0; j < progress.requiredAnswers(); j++) progress.answer();
            for(int j = 0; j < progress.requiredKills(); j++) progress.defeat(false);
            progress.defeat(true);
            progress.update(PinyinAdventureProgress.chargeNeeded, true);
            progress.advance();
            assertTrue(progress.waveSize() >= size);
            assertTrue(progress.enemyLimit() >= limit);
            assertTrue(progress.waveInterval() <= interval);
            assertTrue(progress.waveSize() <= 10);
            assertTrue(progress.enemyLimit() <= PinyinCombatBalance.maxEnemies);
            assertTrue(progress.waveInterval() >= 60f * 5f);
        }
        assertTrue(progress.waveSize() > firstSize);
        assertTrue(progress.enemyLimit() > firstLimit);
        assertTrue(progress.waveInterval() < firstInterval);
    }

    @Test
    void liveStagesUseOneWideMapWithBossGateAndVehicleRoutes(){
        for(int chapterStage = 0; chapterStage < PinyinAdventureMap.stages; chapterStage++){
            PinyinAdventureMap map = new PinyinAdventureMap(1000L + chapterStage, chapterStage, 2);
            assertTrue(map.singleStage());
            assertEquals(4, map.leftBoundary());
            assertEquals(PinyinAdventureMap.width - 5, map.rightBoundary());
            assertNotNull(map.bossGates[0]);
            assertNotNull(map.bossArenas[0]);
            assertNotNull(map.garages[0]);
            assertNotNull(map.docks[0]);
            assertNotNull(map.airfields[0]);
            assertTrue(reachableSingle(map)[index(map.bossArenas[0])], "Boss arena must be reachable");
        }
    }

    @Test
    void liveStageUsesAnEastWestRescueSpineWithBossAtTheFarEnd(){
        for(int stage = 0; stage < PinyinAdventureMap.stages; stage++){
            PinyinAdventureMap map = new PinyinAdventureMap(8800L + stage, stage, 4);
            Point2[] route = map.routes[0];
            assertTrue(route.length >= 10);
            assertTrue(route[0].x < route[route.length - 1].x);
            for(int i = 1; i < route.length; i++) assertTrue(route[i].x >= route[i - 1].x,
                "The main route must keep moving toward the fortress");
            assertTrue(map.bossGates[0].x > map.entrances[0].x + PinyinAdventureMap.width / 2);
        }
    }

    @Test
    void vehicleModesRespectTerrainAndKeepVanillaRoster(){
        assertEquals(UnitTypes.dagger, PinyinVehicleSystem.type(PinyinVehicleSystem.Mode.land));
        assertEquals(UnitTypes.risso, PinyinVehicleSystem.type(PinyinVehicleSystem.Mode.sea));
        assertEquals(UnitTypes.alpha, PinyinVehicleSystem.type(PinyinVehicleSystem.Mode.air));
        assertFalse(PinyinVehicleSystem.canEnter(PinyinVehicleSystem.Mode.air, null));
    }

    @Test
    void verticalAirBattleMapIsOpenAndBossIsAhead(){
        logic.reset();
        PinyinAirBattleMap map = new PinyinAirBattleMap(42L, 3);
        world.loadGenerator(PinyinAirBattleMap.width, PinyinAirBattleMap.height, map::generate);
        assertEquals(PinyinAirBattleMap.width, world.width());
        assertEquals(PinyinAirBattleMap.height, world.height());
        assertTrue(map.entryY() < map.bossY(), "Aircraft must progress upward toward the boss");
        assertTrue(map.entryX() > 0 && map.entryX() < PinyinAirBattleMap.width);
        assertNotNull(world.tile(map.entryX(), map.entryY()));
        assertNotNull(world.tile(map.bossX(), map.bossY()));
        world.tiles.eachTile(tile -> assertTrue(tile.block() == Blocks.coreShard || tile.block() == Blocks.air,
            "Air arenas cannot add blocking walls"));
    }

    @Test
    void airTerrainUsesSeededNoiseAndDecorativeOverlays(){
        logic.reset();
        PinyinAirBattleMap first = new PinyinAirBattleMap(101L, 1);
        world.loadGenerator(PinyinAirBattleMap.width, PinyinAirBattleMap.height, first::generate);
        Set<Short> firstFloors = new HashSet<>();
        int[] firstOverlays = {0};
        int[] firstSignature = {0};
        world.tiles.eachTile(tile -> {
            firstFloors.add(tile.floorID());
            if(tile.overlay() != Blocks.air) firstOverlays[0]++;
            firstSignature[0] = firstSignature[0] * 31 + tile.floorID();
        });
        logic.reset();
        PinyinAirBattleMap second = new PinyinAirBattleMap(202L, 1);
        world.loadGenerator(PinyinAirBattleMap.width, PinyinAirBattleMap.height, second::generate);
        int[] secondSignature = {0};
        world.tiles.eachTile(tile -> {
            secondSignature[0] = secondSignature[0] * 31 + tile.floorID();
        });
        assertTrue(firstFloors.size() >= 3, "Noise terrain should contain several biome materials");
        assertTrue(firstOverlays[0] > 20, "Generated air terrain should have decorative detail");
        assertNotEquals(firstSignature[0], secondSignature[0], "Different seeds should produce different terrain layouts");
    }

    @Test
    void everyEnemySilhouetteCanBeEquippedAndRecruitedSafely(){
        for(PinyinEnemyController.Kind kind : PinyinEnemyController.Kind.values()){
            Unit enemy = PinyinEnemyController.visualType(kind).spawn(Team.crux, 0f, 0f);
            assertDoesNotThrow(() -> PinyinEnemyController.equip(enemy, kind, 3), kind.name());
            enemy.remove();
            Unit recruit = PinyinEnemyController.recruitType(kind).spawn(Team.sharded, 0f, 0f);
            assertDoesNotThrow(() -> PinyinEnemyController.equip(recruit, kind, 3), "recruit " + kind.name());
            recruit.remove();
            assertTrue(PinyinEnemyController.recruitType(kind).hitSize <= 16f, "Recruit carrier too large: " + kind.name());
        }
    }

    @Test
    void liveStagesUseMultipleBiomeFloorsInsteadOfOneFlatMaterial(){
        for(int stage = 0; stage < PinyinAdventureMap.stages; stage++){
            logic.reset();
            PinyinAdventureMap map = new PinyinAdventureMap(3000L + stage, stage, 3);
            world.loadGenerator(PinyinAdventureMap.width, PinyinAdventureMap.height, map::generate);
            Set<Floor> floors = new HashSet<>();
            world.tiles.eachTile(tile -> { if(map.open(tile.x, tile.y)) floors.add(tile.floor()); });
            assertTrue(floors.size() >= 3, "Each theme should have readable biome variation");
        }
    }

    boolean[] reachable(PinyinAdventureMap map, int stage, boolean actual){
        int width = PinyinAdventureMap.width, height = PinyinAdventureMap.height;
        boolean[] visited = new boolean[width * height];
        ArrayDeque<Point2> queue = new ArrayDeque<>();
        queue.add(map.entrances[stage]);
        visited[index(map.entrances[stage])] = true;
        while(!queue.isEmpty()){
            Point2 point = queue.remove();
            for(Point2 direction : Geometry.d4){
                int x = point.x + direction.x, y = point.y + direction.y;
                if(x < PinyinAdventureMap.left(stage) + 2 || x > PinyinAdventureMap.right(stage) - 2 || y < 3 || y >= height - 3) continue;
                if(!visited[x + y * width] && map.open(x, y) && (!actual || !world.tile(x, y).solid())){
                    visited[x + y * width] = true;
                    queue.add(new Point2(x, y));
                }
            }
        }
        return visited;
    }

    boolean[] reachableSingle(PinyinAdventureMap map){
        boolean[] visited = new boolean[PinyinAdventureMap.width * PinyinAdventureMap.height];
        ArrayDeque<Point2> queue = new ArrayDeque<>();
        queue.add(map.entrances[0]);
        visited[index(map.entrances[0])] = true;
        while(!queue.isEmpty()){
            Point2 point = queue.remove();
            for(Point2 direction : Geometry.d4){
                int x = point.x + direction.x, y = point.y + direction.y;
                if(x < map.leftBoundary() + 2 || x > map.rightBoundary() - 2 || y < 3 || y >= PinyinAdventureMap.height - 3) continue;
                if(!visited[x + y * PinyinAdventureMap.width] && map.open(x, y)){
                    visited[x + y * PinyinAdventureMap.width] = true;
                    queue.add(new Point2(x, y));
                }
            }
        }
        return visited;
    }

    int index(Point2 point){
        return point.x + point.y * PinyinAdventureMap.width;
    }
}
