import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinStageWorksTests{
    PinyinStageWorks works;
    Unit player;

    @BeforeAll
    static void init(){ ApplicationTests.launchApplication(); }

    @BeforeEach
    void setup(){
        logic.reset();
        world.loadGenerator(50, 50, tiles -> tiles.fill());
        state.rules.unitCap = 50;
        state.rules.unitCapVariable = false;
        Time.setDeltaProvider(() -> 1f);
        Time.delta = 1f;
        works = new PinyinStageWorks();
        player = UnitTypes.alpha.spawn(Team.sharded, 25 * tilesize, 25 * tilesize);
        player.isShooting = true;
    }

    void activate(int stage){
        works.reset(stage, 1, new Point2(25, 25));
        for(int i = 0; i < 60; i++) works.update(1f, 100, player, null);
        assertTrue(works.activated());
    }

    @Test
    void chapterRotationChangesFacilityForTheSameLesson(){
        for(int stage = 0; stage < 4; stage++){
            java.util.Set<PinyinStageWorks.Kind> kinds = new java.util.HashSet<>();
            for(int chapter = 1; chapter <= 4; chapter++){
                works.reset(stage, chapter, new Point2(25, 25));
                kinds.add(works.kind());
                assertFalse(works.activated());
                assertFalse(works.complete());
            }
            assertEquals(4, kinds.size());
        }
    }

    @Test
    void miningObjectiveUsesRealDrillConveyorsAndDepositedCopper(){
        activate(0);
        player.isShooting = false;
        assertEquals(Blocks.mechanicalDrill, world.tile(20, 28).block());
        assertEquals(Blocks.conveyor, world.tile(22, 28).block());
        assertEquals(Blocks.container, world.tile(27, 28).block());
        int received = 0;
        for(int tick = 0; tick < 4000 && !works.complete(); tick++){
            Time.update();
            Groups.build.update();
            received -= works.update(1f, 100, player, null);
        }
        assertTrue(received >= 12, "Copper must actually travel from the drill through the belt into the depot");
        assertTrue(works.complete());
        world.tile(23, 28).setBlock(Blocks.air);
        player.isShooting = true;
        works.update(1f, 0, player, null);
        assertEquals(Blocks.conveyor, world.tile(23, 28).block(), "Repair remains possible after losing supplies");
    }

    @Test
    void solarGridActuallyConnectsGeneratorAndBatteryAndStoresEnergy(){
        activate(2);
        player.isShooting = false;
        var solar = world.build(21, 28);
        var battery = world.build(25, 28);
        assertSame(solar.power.graph, battery.power.graph);
        state.rules.lighting = false;
        for(int tick = 0; tick < 1200 && !works.complete(); tick++){
            Time.update();
            Groups.build.update();
            battery.power.graph.update();
            works.update(1f, 100, player, null);
        }
        assertTrue(works.complete());
        assertTrue(battery.power.status > 0f);
    }

    @Test
    void fortressRequiresPresenceAndMissilesNeedEarnedAnswerEnergy(){
        activate(1);
        player.set(5, 5);
        works.update(1000f, 100, player, null);
        assertFalse(works.complete());
        player.set(25 * tilesize, 25 * tilesize);
        works.update(60f * 15f, 100, player, null);
        assertTrue(works.complete());

        logic.reset();
        world.loadGenerator(50, 50, tiles -> tiles.fill());
        state.rules.unitCap = 50;
        state.rules.unitCapVariable = false;
        player = UnitTypes.alpha.spawn(Team.sharded, 25 * tilesize, 25 * tilesize);
        player.isShooting = true;
        activate(3);
        assertFalse(works.complete());
        assertEquals(0, works.depositEnergy(player, 0));
        assertEquals(1, works.depositEnergy(player, 1));
        assertFalse(works.complete());
        assertEquals(1, works.depositEnergy(player, 1));
        assertTrue(works.complete());
        assertEquals(0, works.depositEnergy(player, 1));
    }
}
