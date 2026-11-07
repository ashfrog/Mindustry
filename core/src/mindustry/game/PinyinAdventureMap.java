package mindustry.game;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

/** Four linked arenas with wide routes, side rooms and guaranteed connected objectives. */
public class PinyinAdventureMap{
    public static final int stages = 4, stageWidth = 80, width = 328, height = 160;
    public static final int chestsPerStage = 12, chestCount = stages * chestsPerStage, padsPerStage = 4;
    public final Point2[] chests = new Point2[chestCount];
    public final Point2[] entrances = new Point2[stages], exits = new Point2[stages];
    public final Point2[] camps = new Point2[stages], beacons = new Point2[stages];
    public final Point2[] turretPads = new Point2[stages * padsPerStage];
    public final int[] patterns = new int[stages], themes = {0, 1, 2, 3}, bossRolls = new int[stages], elevations = new int[stages];
    private final boolean[] open = new boolean[width * height];

    public PinyinAdventureMap(long seed){
        Rand random = new Rand(seed);
        for(int i = stages - 1; i > 0; i--){
            int other = random.random(i), theme = themes[i];
            themes[i] = themes[other];
            themes[other] = theme;
        }
        for(int stage = 0; stage < stages; stage++){
            bossRolls[stage] = random.random(2);
            elevations[stage] = random.random(3);
            int left = left(stage), entrySide = stage == 0 ? 0 : random.random(3);
            int pattern = patterns[stage] = (random.random(4) + stage) % 5;
            entrances[stage] = edge(left, entrySide, random);
            exits[stage] = edge(left, (entrySide + 2) % 4, random);
            camps[stage] = new Point2(entrances[stage].x + (entrySide == 0 ? 2 : 0), entrances[stage].y);
            beacons[stage] = new Point2(left + random.random(27, stageWidth - 27), random.random(44, height - 44));
            Seq<Point2> occupied = Seq.with(camps[stage], beacons[stage], exits[stage]);
            for(int i = 0; i < chestsPerStage; i++){
                Point2 chest = spacedPoint(left, occupied, random);
                chests[stage * chestsPerStage + i] = chest;
                occupied.add(chest);
            }
            Seq<Point2> anchors = new Seq<>();
            if(pattern == 0){
                for(int i = 0; i < 16; i++) anchors.add(new Point2(left + 40 + (int)Angles.trnsx(i * 22.5f, 25f),
                    height / 2 + (int)Angles.trnsy(i * 22.5f, 55f)));
                for(int i = 0; i < anchors.size; i++) connect(anchors.get(i), anchors.get((i + 1) % anchors.size), random);
            }else if(pattern == 2){
                for(int i = 0; i < 5; i++) anchors.add(new Point2(left + (i % 2 == 0 ? 20 : 60) + random.random(-4, 4), 22 + i * 29));
            }else if(pattern == 3 || pattern == 4){
                for(int i = 0; i < 5; i++){
                    Point2 center = new Point2(left + random.random(18, 62), random.random(22, height - 23));
                    anchors.add(center);
                    oval(center.x, center.y, random.random(10, 16), random.random(12, 22));
                }
            }else{
                anchors.add(entrances[stage]);
            }
            for(int i = 1; i < anchors.size; i++) connect(anchors.get(i - 1), anchors.get(i), random);
            for(Point2 point : occupied){
                Point2 nearest = anchors.first();
                for(Point2 anchor : anchors) if(distance(point, anchor) < distance(point, nearest)) nearest = anchor;
                connect(point, nearest, random);
                if(pattern == 1 || pattern == 4) anchors.add(point);
                if(pattern == 4) oval(point.x, point.y, random.random(6, 10), random.random(7, 12));
                else room(point.x, point.y, random.random(6, 9), random.random(7, 10));
            }
            connect(entrances[stage], camps[stage], random);
            connect(camps[stage], anchors.first(), random);
            for(int i = 0; i < 3; i++) connect(occupied.random(random), occupied.random(random), random);
            room(camps[stage].x, camps[stage].y, 9, 10);
            room(exits[stage].x, exits[stage].y, 8, 9);
            for(int i = 0; i < padsPerStage; i++){
                Point2 pad = i < 2 ? new Point2(camps[stage].x + 1, camps[stage].y + (i == 0 ? 4 : -4))
                    : spacedPoint(left, occupied, random);
                turretPads[stage * padsPerStage + i] = pad;
                if(i >= 2){
                    occupied.add(pad);
                    room(pad.x, pad.y, 6, 6);
                    connect(pad, beacons[stage], random);
                }
            }
        }
    }

    private Point2 edge(int left, int side, Rand random){
        return switch(side){
            case 0 -> new Point2(left + 9, random.random(28, height - 29));
            case 1 -> new Point2(left + random.random(22, stageWidth - 23), 13);
            case 2 -> new Point2(right((left - 4) / stageWidth) - 9, random.random(28, height - 29));
            default -> new Point2(left + random.random(22, stageWidth - 23), height - 14);
        };
    }

    private Point2 spacedPoint(int left, Seq<Point2> occupied, Rand random){
        Point2 best = null;
        float bestDistance = -1f;
        for(int attempt = 0; attempt < 300; attempt++){
            Point2 point = new Point2(left + random.random(14, stageWidth - 15), random.random(18, height - 19));
            float closest = Float.MAX_VALUE;
            for(Point2 other : occupied) closest = Math.min(closest, distance(point, other));
            if(closest > bestDistance){ best = point; bestDistance = closest; }
            if(closest >= 16f * 16f) return point;
        }
        return best;
    }

    private float distance(Point2 first, Point2 second){
        return Mathf.dst2(first.x, first.y, second.x, second.y);
    }

    public static int left(int stage){
        return 4 + stage * stageWidth;
    }

    public static int right(int stage){ return left(stage) + stageWidth - 1; }

    private void oval(int x, int y, int rx, int ry){
        for(int dx = -rx; dx <= rx; dx++) for(int dy = -ry; dy <= ry; dy++){
            if(dx * dx / (float)(rx * rx) + dy * dy / (float)(ry * ry) <= 1f) room(x + dx, y + dy, 0, 0);
        }
    }

    public boolean open(int x, int y){
        return x > 1 && y > 1 && x < width - 2 && y < height - 2 && open[x + y * width];
    }

    private void room(int x, int y, int rx, int ry){
        for(int dx = -rx; dx <= rx; dx++){
            for(int dy = -ry; dy <= ry; dy++){
                int tx = x + dx, ty = y + dy;
                if(tx > 1 && ty > 1 && tx < width - 2 && ty < height - 2) open[tx + ty * width] = true;
            }
        }
    }

    private void connect(Point2 from, Point2 to, Rand random){
        boolean horizontalFirst = random.chance(0.5);
        int x = from.x, y = from.y;
        while(x != to.x || y != to.y){
            room(x, y, 3, 3);
            if((horizontalFirst && x != to.x) || y == to.y) x += Integer.signum(to.x - x);
            else y += Integer.signum(to.y - y);
        }
        room(to.x, to.y, 3, 3);
    }

    public void generate(Tiles tiles){
        tiles.fill();
        // The last two biomes deliberately read as a mine and a floating cloud shelf.
        Floor[] floors = {(Floor)Blocks.grass, (Floor)Blocks.sand, (Floor)Blocks.darksand, (Floor)Blocks.snow};
        Block[] walls = {Blocks.stoneWall, Blocks.sandWall, Blocks.stoneWall, Blocks.iceWall};
        tiles.eachTile(tile -> {
            int stage = Mathf.clamp((tile.x - 4) / stageWidth, 0, stages - 1);
            tile.setFloor(floors[themes[stage]]);
            if(!open(tile.x, tile.y)) tile.setBlock(walls[themes[stage]]);
        });
        tiles.getn(entrances[0].x - 4, entrances[0].y).setBlock(Blocks.coreShard, Team.sharded, 0);
        for(int stage = 0; stage < stages; stage++){
            Point2 camp = camps[stage], beacon = beacons[stage], exit = exits[stage];
            paint(tiles, camp, 4, (Floor)Blocks.metalFloor);
            paint(tiles, beacon, 4, (Floor)Blocks.metalFloor2);
            paint(tiles, exit, 3, (Floor)Blocks.metalFloor5);
            // Concentric floor terraces give the beacon and cave/cloud islands a readable height cue.
            if(themes[stage] == 2){
                paint(tiles, beacon, 11, (Floor)Blocks.basalt);
                paint(tiles, beacon, 7, (Floor)Blocks.metalFloor3);
                paint(tiles, beacon, 4, (Floor)Blocks.metalFloor2);
            }else if(themes[stage] == 3){
                paint(tiles, beacon, 12, (Floor)Blocks.snow);
                paint(tiles, beacon, 8, (Floor)Blocks.metalFloor5);
                paint(tiles, beacon, 4, (Floor)Blocks.metalFloor2);
            }
        }
        for(Point2 pad : turretPads) paint(tiles, pad, 2, (Floor)Blocks.metalFloor3);
        // Bunkers are drawn by the scene; floor markers do not block movement.
        for(Point2 chest : chests) paint(tiles, chest, 1, (Floor)Blocks.metalFloor4);
    }

    private void paint(Tiles tiles, Point2 center, int radius, Floor floor){
        for(int x = center.x - radius; x <= center.x + radius; x++){
            for(int y = center.y - radius; y <= center.y + radius; y++){
                if(open(x, y)) tiles.getn(x, y).setFloor(floor);
            }
        }
    }
}
