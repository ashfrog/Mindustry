import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinTurretPadsTests{
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
    void eitherPlayerCanBuildAllFourTurretsAndHoldFireWithoutRepeatedCost(){
        for(int i = 0; i < 4; i++){
            Tile tile = world.tile(6 + i * 8, 20);
            Unit unit = UnitTypes.alpha.spawn(Team.sharded, tile.worldx(), tile.worldy());
            unit.isShooting = true;
            assertEquals(40, PinyinTurretPads.activate(tile, i, 40, i % 2 == 0 ? unit : null, i % 2 == 1 ? unit : null));
            assertEquals(PinyinTurretPads.turret(i), tile.block());
            assertTrue(PinyinTurretPads.loaded(tile));
            assertFalse(world.tileWorld(unit.x, unit.y).solid(), "Building cannot trap the player inside a turret");
            ItemTurret.ItemTurretBuild turret = (ItemTurret.ItemTurretBuild)tile.build;
            assertEquals(PinyinTurretPads.ammo(i), ((ItemTurret.ItemEntry)turret.ammo.first()).item);
            assertEquals(0, PinyinTurretPads.activate(tile, i, 100, unit, null));
            turret.ammo.clear();
            turret.totalAmmo = 0;
            assertEquals(20, PinyinTurretPads.activate(tile, i, 20, unit, null));
            assertTrue(PinyinTurretPads.loaded(tile));
        }
    }

    @Test
    void walkingWithoutFiringOrFiringFarAwayCannotSpendSupplies(){
        Tile tile = world.tile(20, 20);
        Unit unit = UnitTypes.alpha.spawn(Team.sharded, tile.worldx(), tile.worldy());
        assertEquals(0, PinyinTurretPads.activate(tile, 0, 100, unit, null));
        unit.isShooting = true;
        assertEquals(0, PinyinTurretPads.activate(tile, 0, 39, unit, null));
        unit.set(0, 0);
        assertEquals(0, PinyinTurretPads.activate(tile, 0, 100, unit, null));
        assertEquals(Blocks.air, tile.block());
    }

    @Test
    void builtTurretsReallyFireTheirLoadedAmmunition(){
        for(int i = 0; i < 4; i++){
            Tile tile = world.tile(20, 20);
            Unit player = UnitTypes.alpha.spawn(Team.sharded, tile.worldx(), tile.worldy() - 2f * tilesize);
            player.isShooting = true;
            assertEquals(40, PinyinTurretPads.activate(tile, i, 40, player, null));
            ItemTurret.ItemTurretBuild turret = (ItemTurret.ItemTurretBuild)tile.build;
            Unit target = (i == 1 ? UnitTypes.flare : UnitTypes.dagger).spawn(Team.crux, tile.worldx() + 10f * tilesize, tile.worldy());
            state.teams.updateTeamStats();
            int ammo = turret.totalAmmo;
            for(int tick = 0; tick < 360; tick++){
                turret.efficiency = turret.potentialEfficiency = 1f;
                turret.target = target;
                turret.updateTile();
            }
            assertTrue(turret.totalAmmo < ammo, PinyinTurretPads.turret(i).name + " must fire its real ammunition");
            assertTrue(Groups.bullet.size() > 0);
            tile.setBlock(Blocks.air);
            target.remove();
            player.remove();
            Groups.bullet.clear();
        }
    }
}
