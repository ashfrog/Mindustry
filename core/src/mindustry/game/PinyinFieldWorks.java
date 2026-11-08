package mindustry.game;

import arc.struct.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

/** Shared supply economy backed by real, destructible Mindustry buildings. */
public class PinyinFieldWorks{
    public enum Kind{
        cannon("机枪塔", 35), flak("防空炮", 50), barrier("拦弹护墙", 25),
        drill("采矿站", 40), clinic("维修站", 55), hangar("僚机机库", 70);
        public final String label;
        public final int cost;
        Kind(String label, int cost){ this.label = label; this.cost = cost; }
        public Block block(){ return switch(this){
            case cannon -> Blocks.duo;
            case flak -> Blocks.scatter;
            case barrier -> Blocks.copperWall;
            case drill -> Blocks.mechanicalDrill;
            case clinic -> Blocks.mender;
            case hangar -> Blocks.airFactory;
        }; }
    }
    public static class Site{
        public final Tile tile;
        public final Kind kind;
        public final int owner;
        private final Building building;
        private float timer;
        Site(Tile tile, Kind kind, int owner){
            this.tile = tile; this.kind = kind; this.owner = owner; building = tile.build;
        }
        public boolean alive(){ return tile.build == building && building != null && !building.dead; }
    }
    public static final int maxSites = 24;
    public final Seq<Site> sites = new Seq<>();
    private final Seq<Bullet> intercepted = new Seq<>();
    public String message = "";

    public void reset(){ sites.clear(); intercepted.clear(); }

    public int build(Kind kind, Unit player, int owner, int supplies){
        sites.removeAll(site -> !site.alive());
        if(supplies < kind.cost){ message = "铜不足：需要 " + kind.cost; return 0; }
        if(sites.size >= maxSites){ message = "工事已满：保护现有阵地"; return 0; }
        Tile chosen = null;
        int px = World.toTile(player.x), py = World.toTile(player.y);
        for(int radius = 2; radius <= 8 && chosen == null; radius++){
            for(int dx = -radius; dx <= radius && chosen == null; dx++) for(int dy = -radius; dy <= radius; dy++){
                if(Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                Tile tile = world.tile(px + dx, py + dy);
                if(tile == null || kind == Kind.drill && tile.drop() == null) continue;
                if(clear(tile, kind.block().size)){ chosen = tile; break; }
            }
        }
        if(chosen == null){ message = kind == Kind.drill ? "附近没有矿石：寻找铜色矿脉" : "这里太拥挤，换个位置建造"; return 0; }
        int size = kind.block().size, offset = -(size - 1) / 2;
        for(int dx = offset; dx < offset + size; dx++) for(int dy = offset; dy < offset + size; dy++){
            Tile tile = world.tile(chosen.x + dx, chosen.y + dy);
            tile.setFloor(Blocks.metalFloor.asFloor());
        }
        chosen.setBlock(kind.block(), Team.sharded, 0);
        Site site = new Site(chosen, kind, owner);
        sites.add(site);
        reload(site);
        message = kind.label + "建成 · 队友靠近可协作增强";
        return kind.cost;
    }

    private boolean clear(Tile center, int size){
        int offset = -(size - 1) / 2;
        for(int dx = offset - 1; dx <= offset + size; dx++) for(int dy = offset - 1; dy <= offset + size; dy++){
            Tile tile = world.tile(center.x + dx, center.y + dy);
            if(tile == null || tile.block() != Blocks.air) return false;
        }
        return true;
    }

    private void reload(Site site){
        if(site.tile.build instanceof ItemTurret.ItemTurretBuild turret){
            Item ammo = site.kind == Kind.flak ? Items.lead : Items.copper;
            turret.handleStack(ammo, turret.acceptStack(ammo, 80, turret), turret);
        }
    }

    public static boolean near(Unit player, Site site){
        return player != null && !player.dead && player.isAdded() && player.within(site.tile, 90f);
    }

    /** Mining and field service tick once a second. Two nearby pilots double mining. */
    public int update(float delta, Unit first, Unit second, Seq<Unit> enemies){
        int income = 0;
        sites.removeAll(site -> !site.alive());
        for(Site site : sites){
            boolean p1 = near(first, site), p2 = near(second, site), together = p1 && p2;
            site.timer += delta;
            if(site.kind == Kind.barrier){
                intercepted.clear();
                Groups.bullet.intersect(site.tile.worldx() - 42f, site.tile.worldy() - 42f, 84f, 84f, bullet -> {
                    if(bullet.team != Team.sharded && bullet.type.absorbable && bullet.within(site.tile, together ? 42f : 30f)) intercepted.add(bullet);
                });
                for(Bullet bullet : intercepted){
                    if(!site.alive()) break;
                    site.building.damage(Math.min(20f, Math.max(1f, bullet.damage * 0.2f)));
                    bullet.absorb();
                }
            }
            if(site.timer < 60f) continue;
            site.timer %= 60f;
            if(site.kind == Kind.drill) income += together ? 8 : 4;
            if(site.kind == Kind.cannon || site.kind == Kind.flak){
                reload(site);
                if(together) site.building.applyBoost(1.6f, 65f);
            }
            if(site.kind == Kind.clinic){
                for(Unit pilot : new Unit[]{first, second}) if(near(pilot, site)){
                    pilot.heal(together ? 7f : 4f);
                    pilot.shield = Math.min(PinyinCombatBalance.maxShield, pilot.shield + (together ? 5f : 2f));
                }
                for(Site other : sites) if(other.alive() && other.tile.within(site.tile, 80f)) other.building.heal(4f);
            }
            for(Unit enemy : enemies) if(enemy != null && !enemy.dead && enemy.within(site.tile, 40f)){
                site.building.damage(4f * PinyinSurvival.damageScale(1));
            }
        }
        return income;
    }
}
