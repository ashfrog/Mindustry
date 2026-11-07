package mindustry.game;

import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

/** A marked world position builds or reloads a real turret when either nearby player fires. */
public class PinyinTurretPads{
    public static Block turret(int index){
        return switch(index % PinyinAdventureMap.padsPerStage){
            case 0 -> Blocks.duo;
            case 1 -> Blocks.scatter;
            case 2 -> Blocks.salvo;
            default -> Blocks.hail;
        };
    }

    public static Item ammo(int index){
        return index % PinyinAdventureMap.padsPerStage == 1 ? Items.lead
            : index % PinyinAdventureMap.padsPerStage == 0 ? Items.copper : Items.graphite;
    }

    public static boolean built(Tile tile){
        return tile.build instanceof ItemTurret.ItemTurretBuild && tile.team() == Team.sharded;
    }

    public static boolean loaded(Tile tile){
        return built(tile) && ((ItemTurret.ItemTurretBuild)tile.build).totalAmmo > 0;
    }

    /** Returns the supply cost, or zero when no activation took place. */
    public static int activate(Tile tile, int index, int supplies, Unit first, Unit second){
        if(tile == null || loaded(tile) || (!firing(first, tile) && !firing(second, tile))) return 0;
        int cost = built(tile) ? 20 : 40;
        if(supplies < cost) return 0;
        if(!built(tile)) tile.setNet(turret(index), Team.sharded, 0);
        if(!(tile.build instanceof ItemTurret.ItemTurretBuild turret)) return 0;
        Item ammo = ammo(index);
        turret.handleStack(ammo, turret.acceptStack(ammo, 100, turret), turret);
        // A player may be standing on the hologram when it becomes a solid building.
        moveOutside(first, tile);
        moveOutside(second, tile);
        return cost;
    }

    private static boolean firing(Unit unit, Tile tile){
        return unit != null && !unit.dead && unit.isAdded() && unit.isShooting && unit.within(tile, 3f * tilesize);
    }

    private static void moveOutside(Unit unit, Tile pad){
        if(unit == null || unit.dead || !unit.isAdded()) return;
        Tile current = world.tileWorld(unit.x, unit.y);
        if(current == null || !current.solid() || !unit.within(pad, 4f * tilesize)) return;
        for(int radius = 1; radius <= 6; radius++){
            for(int dx = -radius; dx <= radius; dx++) for(int dy = -radius; dy <= radius; dy++){
                if(Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                Tile clear = world.tile(current.x + dx, current.y + dy);
                if(clear != null && !clear.solid()){
                    unit.set(clear.worldx(), clear.worldy());
                    unit.vel.setZero();
                    return;
                }
            }
        }
    }
}
