package mindustry.game;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

/** Deterministic rescue-run map data. Legacy construction still exposes four regions for tests; the
 * live single-stage constructor builds one east-west Red Fortress route and regenerates after every Boss. */
public class PinyinAdventureMap{
    /** Larger east-west rooms make the route read like a classic rescue-and-boss map. */
    public static final int stages = 4, stageWidth = 100, width = 408, height = 176;
    public static final int chestsPerStage = 12, chestCount = stages * chestsPerStage, padsPerStage = 4;
    public final Point2[] chests = new Point2[chestCount];
    public final Point2[] entrances = new Point2[stages], exits = new Point2[stages];
    public final Point2[] camps = new Point2[stages], beacons = new Point2[stages];
    public final Point2[] turretPads = new Point2[stages * padsPerStage];
    public final Point2[] workshops = new Point2[stages], rescues = new Point2[stages * 2];
    public final Point2[] ships = new Point2[stages];
    public final Point2[] bossGates = new Point2[stages], bossArenas = new Point2[stages];
    public final Point2[] garages = new Point2[stages], docks = new Point2[stages], airfields = new Point2[stages];
    public final Point2[][] routes = new Point2[stages][];
    public final int[] patterns = new int[stages], themes = {0, 1, 2, 3}, bossRolls = new int[stages], elevations = new int[stages];
    private final boolean[] open = new boolean[width * height];
    private final boolean singleStage;
    private final int requestedStage;

    public PinyinAdventureMap(long seed){
        this(seed, -1, 1);
    }

    /** Builds one full-width stage for the live adventure. */
    public PinyinAdventureMap(long seed, int stageIndex, int chapter){
        singleStage = stageIndex >= 0;
        requestedStage = Math.floorMod(stageIndex, stages);
        Rand random = new Rand(seed);
        if(singleStage){
            themes[0] = requestedStage;
            for(int i = 1; i < themes.length; i++) themes[i] = Math.floorMod(requestedStage + i + chapter - 1, themes.length);
        }
        for(int i = stages - 1; i > 0 && !singleStage; i--){
            int other = random.random(i), theme = themes[i];
            themes[i] = themes[other];
            themes[other] = theme;
        }
        if(singleStage){
            buildRedFortressStage(random, requestedStage, chapter);
            return;
        }
        int count = stages;
        for(int stage = 0; stage < count; stage++){
            bossRolls[stage] = random.random(2);
            elevations[stage] = random.random(3);
            int left = localLeft(stage), entrySide = stage == 0 ? 0 : random.random(3);
            int pattern = patterns[stage] = (random.random(4) + stage) % 5;
            entrances[stage] = edge(left, entrySide, random);
            exits[stage] = edge(left, (entrySide + 2) % 4, random);
            camps[stage] = new Point2(entrances[stage].x + (entrySide == 0 ? 2 : 0), entrances[stage].y);
            beacons[stage] = new Point2(left + random.random(27, localWidth() - 27), random.random(44, height - 44));
            Seq<Point2> occupied = Seq.with(camps[stage], beacons[stage], exits[stage]);
            workshops[stage] = spacedPoint(left, occupied, random);
            occupied.add(workshops[stage]);
            for(int i = 0; i < 2; i++){
                rescues[stage * 2 + i] = spacedPoint(left, occupied, random);
                occupied.add(rescues[stage * 2 + i]);
            }
            // The ship sits at the far end of the route; pinyin is delivered here after it is chased down.
            ships[stage] = new Point2(exits[stage].x - 5, exits[stage].y);
            occupied.add(ships[stage]);
            bossGates[stage] = new Point2(left + localWidth() - 34, random.random(38, height - 39));
            bossArenas[stage] = new Point2(left + localWidth() - 12, bossGates[stage].y);
            occupied.add(bossGates[stage]);
            occupied.add(bossArenas[stage]);
            garages[stage] = spacedPoint(left, occupied, random);
            occupied.add(garages[stage]);
            docks[stage] = spacedPoint(left, occupied, random);
            occupied.add(docks[stage]);
            airfields[stage] = spacedPoint(left, occupied, random);
            occupied.add(airfields[stage]);
            routes[stage] = new Point2[]{entrances[stage], camps[stage], rescues[stage * 2], workshops[stage],
                beacons[stage], rescues[stage * 2 + 1], garages[stage], docks[stage], airfields[stage],
                bossGates[stage], bossArenas[stage]};
            for(int i = 0; i < chestsPerStage; i++){
                Point2 chest = spacedPoint(left, occupied, random);
                chests[stage * chestsPerStage + i] = chest;
                occupied.add(chest);
            }
            Seq<Point2> anchors = new Seq<>();
            if(pattern == 0){
                for(int i = 0; i < 16; i++) anchors.add(new Point2(left + localWidth() / 2 + (int)Angles.trnsx(i * 22.5f, 25f),
                    height / 2 + (int)Angles.trnsy(i * 22.5f, 55f)));
                for(int i = 0; i < anchors.size; i++) connect(anchors.get(i), anchors.get((i + 1) % anchors.size), random);
            }else if(pattern == 2){
                for(int i = 0; i < 5; i++) anchors.add(new Point2(left + (i % 2 == 0 ? localWidth() / 4 : localWidth() * 3 / 4) + random.random(-4, 4), 22 + i * 29));
            }else if(pattern == 3 || pattern == 4){
                for(int i = 0; i < 5; i++){
                    Point2 center = new Point2(left + random.random(18, localWidth() - 19), random.random(22, height - 23));
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
            room(workshops[stage].x, workshops[stage].y, 9, 9);
            room(ships[stage].x, ships[stage].y, 5, 5);
            connect(ships[stage], exits[stage], random);
            room(bossGates[stage].x, bossGates[stage].y, 7, 8);
            room(bossArenas[stage].x, bossArenas[stage].y, 12, 14);
            connect(bossGates[stage], bossArenas[stage], random);
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

    /** Builds the live map as a side-scrolling rescue route instead of a collection of rooms. */
    private void buildRedFortressStage(Rand random, int theme, int chapter){
        int left = 4, center = height / 2;
        int[] x = {10, 28, 68, 112, 158, 204, 248, 286, 322, 360, 394};
        Point2[] spine = new Point2[x.length];
        int y = center + random.random(-18, 18);
        for(int i = 0; i < x.length; i++){
            if(i > 0 && i < x.length - 1) y = Mathf.clamp(y + random.random(-20, 20), 28, height - 29);
            spine[i] = new Point2(x[i], y);
        }
        patterns[0] = random.random(4);
        bossRolls[0] = random.random(2);
        elevations[0] = random.random(3);
        entrances[0] = spine[0];
        camps[0] = spine[1];
        exits[0] = spine[10];
        bossGates[0] = spine[9];
        bossArenas[0] = spine[10];
        ships[0] = spine[7];
        beacons[0] = new Point2(204, Mathf.clamp(spine[5].y - 28, 24, height - 25));
        workshops[0] = new Point2(112, Mathf.clamp(spine[3].y + 30, 22, height - 23));
        rescues[0] = new Point2(70, Mathf.clamp(spine[2].y - 30, 22, height - 23));
        rescues[1] = new Point2(250, Mathf.clamp(spine[6].y + 30, 22, height - 23));
        garages[0] = new Point2(145, Mathf.clamp(spine[4].y - 28, 22, height - 23));
        docks[0] = new Point2(194, theme == 0 ? center - 42 : Mathf.clamp(spine[5].y + 30, 22, height - 23));
        airfields[0] = new Point2(300, Mathf.clamp(spine[7].y - 30, 22, height - 23));
        routes[0] = spine;

        Seq<Point2> occupied = Seq.with(camps[0], beacons[0], workshops[0], rescues[0], rescues[1],
            ships[0], garages[0], docks[0], airfields[0], bossGates[0], bossArenas[0]);
        for(Point2 point : occupied){
            Point2 nearest = spine[0];
            for(Point2 anchor : spine) if(distance(point, anchor) < distance(point, nearest)) nearest = anchor;
            connect(point, nearest, random);
            room(point.x, point.y, point == docks[0] && theme == 0 ? 5 : 7, 7);
        }
        for(int i = 1; i < spine.length; i++) connect(spine[i - 1], spine[i], random);
        for(int i = 0; i < 3; i++) connect(spine[random.random(1, spine.length - 2)], spine[random.random(1, spine.length - 2)], random);
        room(camps[0].x, camps[0].y, 10, 10);
        room(bossGates[0].x, bossGates[0].y, 8, 9);
        room(bossArenas[0].x, bossArenas[0].y, 14, 15);

        for(int i = 0; i < chestsPerStage; i++){
            int segment = 1 + (i * 8 / chestsPerStage);
            Point2 anchor = spine[Mathf.clamp(segment, 1, spine.length - 2)];
            Point2 chest = new Point2(Mathf.clamp(anchor.x + random.random(-10, 10), left + 12, width - 14),
                Mathf.clamp(anchor.y + random.random(-32, 32), 18, height - 19));
            chests[i] = chest;
            room(chest.x, chest.y, 5, 5);
            connect(chest, anchor, random);
        }
        for(int i = 0; i < padsPerStage; i++){
            Point2 anchor = spine[1 + i * 2];
            Point2 pad = i < 2 ? new Point2(camps[0].x + 1, camps[0].y + (i == 0 ? 4 : -4))
                : new Point2(anchor.x, Mathf.clamp(anchor.y + (i == 2 ? -25 : 25), 18, height - 19));
            turretPads[i] = pad;
            room(pad.x, pad.y, 5, 5);
            connect(pad, anchor, random);
        }
    }

    private Point2 edge(int left, int side, Rand random){
        return switch(side){
            case 0 -> new Point2(left + 9, random.random(28, height - 29));
            case 1 -> new Point2(left + random.random(22, localWidth() - 23), 13);
            case 2 -> new Point2(left + localWidth() - 10, random.random(28, height - 29));
            default -> new Point2(left + random.random(22, localWidth() - 23), height - 14);
        };
    }

    private Point2 spacedPoint(int left, Seq<Point2> occupied, Rand random){
        Point2 best = null;
        float bestDistance = -1f;
        for(int attempt = 0; attempt < 300; attempt++){
            Point2 point = new Point2(left + random.random(14, localWidth() - 15), random.random(18, height - 19));
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

    public boolean singleStage(){ return singleStage; }
    public int stageIndex(){ return 0; }
    public int leftBoundary(){ return localLeft(0); }
    public int rightBoundary(){ return localLeft(0) + localWidth() - 1; }

    private int localWidth(){ return singleStage ? width - 8 : stageWidth; }
    private int localLeft(int stage){ return singleStage ? 4 : left(stage); }

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
            // Nine tiles wide roads make the long rescue route readable and leave
            // enough room for two players, vehicles and dodge lanes side by side.
            room(x, y, 4, 4);
            if((horizontalFirst && x != to.x) || y == to.y) x += Integer.signum(to.x - x);
            else y += Integer.signum(to.y - y);
        }
        room(to.x, to.y, 4, 4);
    }

    public void generate(Tiles tiles){
        tiles.fill();
        // The last two biomes deliberately read as a mine and a floating cloud shelf.
        Floor[] floors = {(Floor)Blocks.grass, (Floor)Blocks.sand, (Floor)Blocks.darksand, (Floor)Blocks.snow};
        Block[] walls = {Blocks.stoneWall, Blocks.sandWall, Blocks.stoneWall, Blocks.iceWall};
        tiles.eachTile(tile -> {
            int stage = singleStage ? 0 : Mathf.clamp((tile.x - 4) / stageWidth, 0, stages - 1);
            tile.setFloor(floors[themes[stage]]);
            if(isWaterLane(tile.x, tile.y, stage) && !nearRoute(tile.x, tile.y, routes[stage], 6f)){
                tile.setFloor(themes[stage] == 2 ? (Floor)Blocks.deepwater : (Floor)Blocks.water);
            }
            if(!open(tile.x, tile.y)) tile.setBlock(walls[themes[stage]]);
        });
        tiles.getn(entrances[0].x - 4, entrances[0].y).setBlock(Blocks.coreShard, Team.sharded, 0);
        int count = singleStage ? 1 : stages;
        for(int stage = 0; stage < count; stage++){
            paintScenery(tiles, stage);
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
        for(Point2 pad : turretPads) if(pad != null) paint(tiles, pad, 2, (Floor)Blocks.metalFloor3);
        // Bunkers are drawn by the scene; floor markers do not block movement.
        for(Point2 chest : chests) if(chest != null) paint(tiles, chest, 1, (Floor)Blocks.metalFloor4);
        for(Point2 workshop : workshops) if(workshop != null) paint(tiles, workshop, 7, (Floor)Blocks.metalFloor3);
        for(Point2 rescue : rescues) if(rescue != null) paint(tiles, rescue, 2, (Floor)Blocks.metalFloor5);
        for(Point2 ship : ships) if(ship != null) paint(tiles, ship, 4, (Floor)Blocks.metalFloor2);
        // A dock is a real water-side switch point on river and harbor chapters.
        for(int i = 0; i < stages; i++) if(docks[i] != null && (themes[i] == 0 || themes[i] == 2)){
            paint(tiles, docks[i], 4, themes[i] == 2 ? (Floor)Blocks.deepwater : (Floor)Blocks.water);
        }
    }

    /** Paints broad, deterministic biome pockets so a run reads as a place instead of a tiled box. */
    private void paintScenery(Tiles tiles, int stage){
        Rand decor = new Rand(0x51f15eL + patterns[stage] * 911L + themes[stage] * 131L + stage * 17L);
        Floor[][] palettes = {
            {(Floor)Blocks.grass, (Floor)Blocks.moss, (Floor)Blocks.dirt, (Floor)Blocks.sand},
            {(Floor)Blocks.sand, (Floor)Blocks.darksand, (Floor)Blocks.mud, (Floor)Blocks.craters},
            {(Floor)Blocks.darksand, (Floor)Blocks.sand, (Floor)Blocks.basalt, (Floor)Blocks.craters},
            {(Floor)Blocks.snow, (Floor)Blocks.ice, (Floor)Blocks.metalFloor5, (Floor)Blocks.salt}
        };
        Floor[] palette = palettes[Math.floorMod(themes[stage], palettes.length)];
        Block[] props = sceneryProps(themes[stage]);
        int left = localLeft(stage), right = left + localWidth() - 1;
        for(int i = 0; i < 22; i++){
            int x = decor.random(left + 12, right - 12), y = decor.random(14, height - 15);
            if(nearRoute(x, y, routes[stage], 10f)) continue;
            int rx = decor.random(5, 17), ry = decor.random(4, 13);
            for(int dx = -rx; dx <= rx; dx++) for(int dy = -ry; dy <= ry; dy++){
                if(dx * dx / (float)(rx * rx) + dy * dy / (float)(ry * ry) > 1f) continue;
                int tx = x + dx, ty = y + dy;
                if(open(tx, ty) && !nearRoute(tx, ty, routes[stage], 6f)) tiles.getn(tx, ty).setFloor(palette[(i + dx + dy + 8) & 3]);
            }
        }
        // Give each biome a visual vocabulary of trees, rocks, crystals and shoreline
        // life.  These props stay well away from the route spine, so the composition
        // reads as a landscape without turning scenery into invisible path blockers.
        for(int i = 0; i < 46; i++){
            int x = decor.random(left + 10, right - 10), y = decor.random(10, height - 11);
            Tile tile = tiles.getn(x, y);
            if(!open(x, y) || nearRoute(x, y, routes[stage], 11f) || nearFeature(x, y, stage, 6f) || tile.block() != Blocks.air) continue;
            boolean liquid = tile.floor() != null && tile.floor().isLiquid;
            Block prop = props[Math.floorMod(i + decor.random(props.length), props.length)];
            if(liquid && themes[stage] != 2) prop = Blocks.pebbles;
            if(!liquid && (prop == Blocks.redweed || prop == Blocks.yellowCoral)) prop = Blocks.boulder;
            tile.setBlock(prop);
        }
    }

    private Block[] sceneryProps(int theme){
        return switch(Math.floorMod(theme, 4)){
            case 0 -> new Block[]{Blocks.pine, Blocks.boulder, Blocks.shrubs, Blocks.sporeCluster};
            case 1 -> new Block[]{Blocks.sandBoulder, Blocks.boulder, Blocks.shrubs, Blocks.pebbles};
            case 2 -> new Block[]{Blocks.basaltBoulder, Blocks.redweed, Blocks.yellowCoral, Blocks.shaleBoulder};
            default -> new Block[]{Blocks.snowPine, Blocks.snowBoulder, Blocks.crystalCluster, Blocks.pebbles};
        };
    }

    private boolean nearFeature(int x, int y, int stage, float radius){
        Point2[] fixed = {camps[stage], beacons[stage], workshops[stage], ships[stage],
            bossGates[stage], bossArenas[stage], garages[stage], docks[stage], airfields[stage],
            rescues[stage * 2], rescues[stage * 2 + 1]};
        for(Point2 point : fixed) if(point != null && Mathf.dst(x, y, point.x, point.y) <= radius) return true;
        int start = stage * chestsPerStage;
        for(int i = start; i < start + chestsPerStage; i++){
            Point2 point = chests[i];
            if(point != null && Mathf.dst(x, y, point.x, point.y) <= radius) return true;
        }
        return false;
    }

    private void paint(Tiles tiles, Point2 center, int radius, Floor floor){
        for(int x = center.x - radius; x <= center.x + radius; x++){
            for(int y = center.y - radius; y <= center.y + radius; y++){
                if(open(x, y)) tiles.getn(x, y).setFloor(floor);
            }
        }
    }

    private boolean isWaterLane(int x, int y, int stage){
        int left = localLeft(stage), right = left + localWidth() - 1;
        if(singleStage && themes[stage] == 0){
            // The river valley is crossed by a long bridge on the main spine;
            // the water remains a meaningful optional boat route around it.
            return x > left + 176 && x < left + 207 && y > 12 && y < height - 13;
        }
        if(themes[stage] == 0) return x > left + 14 && x < right - 8 && y > height / 2 - 11 && y < height / 2 + 11;
        if(themes[stage] == 2) return x > left + 18 && x < right - 4 && y > height - 52;
        return false;
    }

    private boolean nearRoute(int x, int y, Point2[] route, float radius){
        if(route == null) return false;
        for(int i = 1; i < route.length; i++){
            Point2 a = route[i - 1], b = route[i];
            float dx = b.x - a.x, dy = b.y - a.y;
            float length = dx * dx + dy * dy;
            float t = length == 0f ? 0f : Mathf.clamp(((x - a.x) * dx + (y - a.y) * dy) / length);
            float px = a.x + dx * t, py = a.y + dy * t;
            if(Mathf.dst(x, y, px, py) <= radius) return true;
        }
        return false;
    }
}
