import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinFieldWorksTests{
    @BeforeAll
    static void init(){ ApplicationTests.launchApplication(); }

    @BeforeEach
    void setup(){
        logic.reset();
        world.loadGenerator(80, 80, tiles -> tiles.fill());
        state.rules.unitCap = 260;
        Time.delta = 1f;
    }

    @Test
    void buildingSpendsSharedSuppliesAndProducesRealArmedDestructibleTurrets(){
        PinyinFieldWorks works = new PinyinFieldWorks();
        Unit pilot = UnitTypes.alpha.spawn(Team.sharded, 300f, 300f);
        assertEquals(0, works.build(PinyinFieldWorks.Kind.flak, pilot, 0, 49));
        assertTrue(works.sites.isEmpty());
        assertEquals(50, works.build(PinyinFieldWorks.Kind.flak, pilot, 0, 50));
        var site = works.sites.first();
        assertEquals(Blocks.scatter, site.tile.block());
        assertEquals(Team.sharded, site.tile.team());
        assertTrue(((ItemTurret.ItemTurretBuild)site.tile.build).totalAmmo > 0);
        site.tile.build.damage(10f);
        assertTrue(site.tile.build.health < site.tile.build.maxHealth);
        site.tile.setBlock(Blocks.air);
        assertFalse(site.alive());
        works.update(60f, pilot, null, new Seq<>());
        assertTrue(works.sites.isEmpty());
    }

    @Test
    void miningRequiresOreProducesIncomeAndTwoPilotsDoubleItsYield(){
        PinyinFieldWorks works = new PinyinFieldWorks();
        Unit first = UnitTypes.alpha.spawn(Team.sharded, 300f, 300f);
        Unit second = UnitTypes.alpha.spawn(Team.sharded, 305f, 300f);
        assertEquals(0, works.build(PinyinFieldWorks.Kind.drill, first, 0, 100));
        world.tiles.eachTile(tile -> tile.setOverlay(Blocks.oreCopper));
        assertEquals(40, works.build(PinyinFieldWorks.Kind.drill, first, 0, 100));
        assertEquals(Blocks.mechanicalDrill, works.sites.first().tile.block());
        assertEquals(4, works.update(60f, first, null, new Seq<>()));
        assertEquals(8, works.update(60f, first, second, new Seq<>()));
    }

    @Test
    void repairStationSupportsBothPilotsAndEnemyCrowdingDamagesTheFortification(){
        PinyinFieldWorks works = new PinyinFieldWorks();
        Unit first = UnitTypes.alpha.spawn(Team.sharded, 300f, 300f);
        Unit second = UnitTypes.alpha.spawn(Team.sharded, 305f, 300f);
        first.health = second.health = 30f;
        assertEquals(55, works.build(PinyinFieldWorks.Kind.clinic, first, 1, 100));
        var site = works.sites.first();
        float stationHealth = site.tile.build.health;
        Unit enemy = UnitTypes.flare.spawn(Team.crux, site.tile.worldx(), site.tile.worldy());
        works.update(60f, first, second, Seq.with(enemy));
        assertTrue(first.health > 30f && second.health > 30f);
        assertTrue(first.shield > 0f && second.shield > 0f);
        assertTrue(site.tile.build.health < stationHealth);
        assertEquals(1, site.owner);
    }

    @Test
    void barrierAbsorbsHostileBulletsButLeavesFriendlyShotsAndLosesHealth(){
        PinyinFieldWorks works = new PinyinFieldWorks();
        Unit pilot = UnitTypes.alpha.spawn(Team.sharded, 300f, 300f);
        works.build(PinyinFieldWorks.Kind.barrier, pilot, 0, 100);
        var site = works.sites.first();
        float health = site.tile.build.health;
        var ammo = PinyinArsenal.create(PinyinArsenal.Kind.rapid, 10f, true);
        Bullet hostile = ammo.create(null, Team.crux, site.tile.worldx() + 10f, site.tile.worldy(), 0f);
        Bullet friendly = ammo.create(null, Team.sharded, site.tile.worldx() + 12f, site.tile.worldy(), 0f);
        Groups.bullet.updatePhysics();
        works.update(1f, pilot, null, new Seq<>());
        assertFalse(hostile.isAdded());
        assertTrue(friendly.isAdded());
        assertTrue(site.tile.build.health < health);
    }
}
