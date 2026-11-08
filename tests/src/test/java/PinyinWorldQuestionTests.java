import arc.math.geom.*;
import mindustry.core.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.game.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinWorldQuestionTests{
    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
    }

    PinyinWorldQuestion question(){
        PinyinWorldQuestion question = new PinyinWorldQuestion();
        question.begin(7, new Vec2[]{new Vec2(40, 0), new Vec2(80, 0), new Vec2(120, 0)}, new int[]{2, 0, 1});
        return question;
    }

    @Test
    void eitherPlayerCanChooseAndAQuestionResolvesOnlyOnce(){
        PinyinWorldQuestion question = question();
        assertEquals(PinyinWorldQuestion.pending, question.update(14f, new Vec2(80, 0), null));
        assertEquals(0, question.update(1f, new Vec2(80, 0), null));
        assertEquals(0, question.collector());
        assertFalse(question.active());
        assertEquals(7, question.chestIndex());
        assertEquals(PinyinWorldQuestion.pending, question.update(1f, new Vec2(40, 0), null));
        question = question();
        assertEquals(2, question.update(15f, null, new Vec2(40, 0)));
        assertEquals(1, question.collector());
        question = question();
        assertEquals(0, question.update(15f, null, new Vec2(80, 0)));
        assertEquals(1, question.collector());
    }

    @Test
    void fleeingQuestionHasNoFiveSecondTimeout(){
        PinyinWorldQuestion question = question();
        assertEquals(PinyinWorldQuestion.pending, question.update(0f, new Vec2(80, 0), null));
        assertEquals(-1f, question.secondsLeft());
        assertEquals(PinyinWorldQuestion.pending, question.update(60f * 60f, null, null));
        assertTrue(question.active());
        assertEquals(0, question.update(15f, new Vec2(80, 0), null));
        assertEquals(0, question.collector());
        question = question();
        question.clear();
        assertEquals(-1, question.collector());
        assertEquals(PinyinWorldQuestion.pending, question.update(15f, new Vec2(80, 0), null));
    }

    @Test
    void everyChestCanDropThreeReachableSeparatedOptionsOnBuiltMaps(){
        for(int seed = 0; seed < 12; seed++){
            logic.reset();
            PinyinAdventureMap map = new PinyinAdventureMap(seed);
            world.loadGenerator(PinyinAdventureMap.width, PinyinAdventureMap.height, map::generate);
            for(int i = 0; i < map.turretPads.length; i++){
                Point2 pad = map.turretPads[i];
                world.tile(pad.x, pad.y).setBlock(PinyinTurretPads.turret(i), Team.sharded, 0);
            }
            for(int i = 0; i < map.chests.length; i++){
                Point2 chest = map.chests[i];
                Tile start = world.tile(chest.x + 2, chest.y);
                assertFalse(start.solid());
                Vec2 opener = new Vec2(start.worldx(), start.worldy());
                Vec2[] options = PinyinWorldQuestion.placeOptions(world.tiles, i / PinyinAdventureMap.chestsPerStage, opener, opener);
                assertNotNull(options, "Missing options, seed " + seed + ", chest " + i);
                int[] distance = distances(start);
                for(int slot = 0; slot < 3; slot++){
                    Vec2 option = options[slot];
                    Tile tile = world.tileWorld(option.x, option.y);
                    assertFalse(tile.solid());
                    assertTrue(distance[tile.x + tile.y * world.width()] >= 3);
                    assertTrue(distance[tile.x + tile.y * world.width()] <= 10);
                    assertFalse(option.within(opener, tilesize * 2.5f));
                    for(int other = 0; other < slot; other++) assertTrue(option.dst(options[other]) >= tilesize * 3f);
                }
            }
        }
    }

    @Test
    void standingInsideChargesForTwoSecondsAndBothPlayersDoNotDoubleTheSpeed(){
        var map = new PinyinAdventureMap(10);
        var question = new PinyinWorldQuestion();
        Point2 chest = map.chests[0];
        Vec2 first = new Vec2(chest.x * tilesize, chest.y * tilesize), second = first.cpy();
        boolean[] unavailable = new boolean[map.chests.length];
        assertEquals(-1, question.updateCharge(60f, first, second, map, 0, unavailable, true));
        assertEquals(0.5f, question.chargeProgress(0));
        assertEquals(-1, question.updateCharge(59f, first, second, map, 0, unavailable, true));
        assertEquals(0, question.updateCharge(1f, first, second, map, 0, unavailable, true));
        assertEquals(0, question.chargeOpener());
        question.begin(0, new Vec2[]{first, first, first}, new int[]{0, 1, 2});
        assertEquals(0f, question.chargeProgress(0));
        assertEquals(-1, question.updateCharge(120f, first, second, map, 0, unavailable, true), "An active question must prevent another opening");
    }

    @Test
    void leavingResetsChargePauseFreezesItAndPlayerTwoCanOpenAlone(){
        var map = new PinyinAdventureMap(10);
        var question = new PinyinWorldQuestion();
        Point2 chest = map.chests[0];
        Vec2 second = new Vec2(chest.x * tilesize, chest.y * tilesize);
        boolean[] unavailable = new boolean[map.chests.length];
        question.updateCharge(60f, null, second, map, 0, unavailable, true);
        question.updateCharge(0f, null, null, map, 0, unavailable, true);
        assertEquals(0.5f, question.chargeProgress(0), "Pause freezes progress");
        second.x += PinyinWorldQuestion.chargeRadius + 1f;
        question.updateCharge(1f, null, second, map, 0, unavailable, true);
        assertEquals(0f, question.chargeProgress(0), "Leaving the circle resets progress");
        second.x = chest.x * tilesize;
        assertEquals(-1, question.updateCharge(119f, null, second, map, 0, unavailable, true));
        assertEquals(0, question.updateCharge(1f, null, second, map, 0, unavailable, true));
        assertEquals(1, question.chargeOpener());
        question.clear();
        assertEquals(0f, question.chargeProgress(0));
    }

    @Test
    void usedBunkersOtherStagesAndCooldownCannotCharge(){
        var map = new PinyinAdventureMap(10);
        var question = new PinyinWorldQuestion();
        Point2 chest = map.chests[0];
        Vec2 player = new Vec2(chest.x * tilesize, chest.y * tilesize);
        boolean[] unavailable = new boolean[map.chests.length];
        unavailable[0] = true;
        assertEquals(-1, question.updateCharge(120f, player, null, map, 0, unavailable, true));
        unavailable[0] = false;
        assertEquals(-1, question.updateCharge(120f, player, null, map, 1, unavailable, true));
        assertEquals(-1, question.updateCharge(120f, player, null, map, 0, unavailable, false));
        assertEquals(0f, question.chargeProgress(0));
        assertEquals(-1, question.updateCharge(120f, null, null, map, 0, unavailable, true), "No proximity means no question, regardless of firing");
        assertEquals(0, question.updateCharge(120f, player, null, map, 0, unavailable, true));
    }

    int[] distances(Tile origin){
        int[] distance = new int[world.width() * world.height()];
        Arrays.fill(distance, -1);
        ArrayDeque<Tile> queue = new ArrayDeque<>();
        queue.add(origin);
        distance[origin.x + origin.y * world.width()] = 0;
        while(!queue.isEmpty()){
            Tile tile = queue.remove();
            int steps = distance[tile.x + tile.y * world.width()];
            if(steps == 10) continue;
            for(Point2 d : Geometry.d4){
                Tile next = world.tile(tile.x + d.x, tile.y + d.y);
                if(next == null || next.solid() || distance[next.x + next.y * world.width()] >= 0) continue;
                distance[next.x + next.y * world.width()] = steps + 1;
                queue.add(next);
            }
        }
        return distance;
    }
}
