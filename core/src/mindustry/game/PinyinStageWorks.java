package mindustry.game;

import arc.math.geom.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

/** Four working vanilla facilities, repaired by holding fire near the floor marker. */
public class PinyinStageWorks{
    public enum Kind{mine, fortress, grid, siege}
    private Kind kind = Kind.mine;
    private final Point2 point = new Point2();
    private boolean activated, complete;
    private float held, guardTime;
    private int mined, energy;

    public void reset(int stage, int chapter, Point2 location){
        kind = Kind.values()[Math.floorMod(stage + chapter - 1, Kind.values().length)];
        point.set(location);
        activated = complete = false;
        held = guardTime = 0f;
        mined = energy = 0;
    }

    public Kind kind(){ return kind; }
    public Point2 point(){ return point; }
    public boolean activated(){ return activated; }
    public boolean complete(){ return complete; }
    public int cost(){ return kind == Kind.fortress ? 20 : kind == Kind.grid ? 15 : 0; }
    public float progress(){
        return Math.min(1f, switch(kind){
            case mine -> mined / 12f;
            case fortress -> guardTime / (60f * 15f);
            case grid -> batteryCharge() / 120f;
            case siege -> energy / 2f;
        });
    }

    /** Supply cost is paid once; broken parts can subsequently be repaired without a soft lock. */
    public int update(float delta, int supplies, Unit first, Unit second){
        if(delta <= 0f) return 0;
        boolean repairing = firing(first) || firing(second);
        int spent = 0;
        if(!activated){
            held = repairing ? held + delta : 0f;
            if(held < 60f || supplies < cost()) return 0;
            activated = true;
            spent = cost();
            repair();
        }else if(repairing){
            repair();
        }
        if(kind == Kind.mine){
            Building depot = build(2, 3);
            if(depot != null && depot.items != null){
                int amount = depot.items.get(Items.copper);
                depot.items.remove(Items.copper, amount);
                mined += amount;
                // Actual items arriving over the conveyor are returned to the team supply stock.
                spent -= amount;
            }
            complete |= mined >= 12;
        }else if(kind == Kind.fortress){
            if(near(first, 10f) || near(second, 10f)){
                if(loaded(-4, 3) && loaded(4, 3)) guardTime += delta;
            }
            complete |= guardTime >= 60f * 15f;
            if(repairing){ load(-4, 3, Items.copper); load(4, 3, Items.lead); }
        }else if(kind == Kind.grid){
            complete |= batteryCharge() >= 120f;
        }
        return spent;
    }

    /** The missile outpost accepts earned answer energy, rather than adding free player layers. */
    public int depositEnergy(Unit player, int available){
        if(!activated || kind != Kind.siege || !near(player, 6f) || !player.isShooting || available <= 0) return 0;
        if(energy >= 2) return 0;
        energy++;
        if(energy >= 2){
            load(-4, 3, Items.pyratite);
            load(4, 3, Items.pyratite);
            complete = true;
        }
        return 1;
    }

    public boolean near(Unit unit, float radius){
        return unit != null && !unit.dead && unit.isAdded() && unit.within(point.x * tilesize, point.y * tilesize, radius * tilesize);
    }

    private boolean firing(Unit unit){ return near(unit, 7f) && unit.isShooting; }
    private Building build(int x, int y){ return world.build(point.x + x, point.y + y); }

    private void ensure(int x, int y, Block block){
        Tile tile = world.tile(point.x + x, point.y + y);
        if(tile == null) return;
        if(tile.block() != block || tile.team() != Team.sharded) tile.setNet(block, Team.sharded, 0);
        if(tile.build != null) tile.build.heal();
    }

    private void repair(){
        switch(kind){
            case mine -> {
                for(int x = -5; x <= -4; x++) for(int y = 3; y <= 4; y++){
                    world.tile(point.x + x, point.y + y).setOverlay(Blocks.oreCopper);
                }
                ensure(-5, 3, Blocks.mechanicalDrill);
                for(int x = -3; x <= 1; x++) ensure(x, 3, Blocks.conveyor);
                ensure(2, 3, Blocks.container);
            }
            case fortress -> {
                ensure(-4, 3, Blocks.duo);
                ensure(4, 3, Blocks.scatter);
                load(-4, 3, Items.copper);
                load(4, 3, Items.lead);
            }
            case grid -> {
                ensure(-4, 3, Blocks.largeSolarPanel);
                ensure(-1, 3, Blocks.powerNode);
                ensure(0, 3, Blocks.battery);
                Building node = build(-1, 3), solar = build(-4, 3);
                if(node != null && solar != null && !node.power.links.contains(solar.pos())) node.configureAny(solar.pos());
            }
            case siege -> {
                ensure(-4, 3, Blocks.swarmer);
                ensure(4, 3, Blocks.swarmer);
                if(complete){ load(-4, 3, Items.pyratite); load(4, 3, Items.pyratite); }
            }
        }
    }

    private boolean loaded(int x, int y){
        Building building = build(x, y);
        return building instanceof ItemTurret.ItemTurretBuild turret && turret.totalAmmo > 0;
    }

    private void load(int x, int y, Item item){
        if(build(x, y) instanceof ItemTurret.ItemTurretBuild turret){
            turret.handleStack(item, turret.acceptStack(item, 40, turret), turret);
        }
    }

    private float batteryCharge(){
        Building battery = build(0, 3);
        return activated && battery != null && battery.block == Blocks.battery && battery.power != null
            ? battery.power.status * battery.block.consPower.capacity : 0f;
    }
}
