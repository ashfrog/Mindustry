package mindustry.game;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.world.*;

/** Seeded, rotated roads and build sockets. Players fly; only monsters follow the roads. */
public class PinyinDefenseMap{
    public static final int width = 112, height = 96, padCount = 12;
    public final long seed;
    public final int theme, shape;
    public final Seq<Vec2[]> routes = new Seq<>();
    public final Vec2[] pads = new Vec2[padCount];
    public final Vec2 base;
    public final float[] lengths;
    public final String name;

    public PinyinDefenseMap(long seed, int chapter){
        this.seed = seed;
        Rand random = new Rand(seed);
        theme = Math.floorMod(chapter - 1 + random.random(5), 6);
        shape = random.random(5);
        name = new String[]{"青森河谷", "赤沙峡湾", "冰湖回廊", "熔岩高地", "云端群岛", "水晶遗迹"}[theme];
        boolean flipX = random.chance(0.5), flipY = random.chance(0.5);
        int low = random.random(14, 24), high = random.random(68, 80), middle = random.random(40, 54);
        float[][] points = switch(shape){
            case 0 -> new float[][]{{7, low}, {30, low}, {30, high}, {60, high}, {60, middle}, {101, middle}};
            case 1 -> new float[][]{{7, high}, {24, high}, {24, low}, {52, low}, {52, high}, {82, high}, {82, middle}, {101, middle}};
            case 2 -> new float[][]{{7, middle}, {24, middle}, {24, high}, {82, high}, {82, low}, {46, low}, {46, middle}, {101, middle}};
            case 3 -> new float[][]{{7, low}, {42, low}, {42, middle}, {22, middle}, {22, high}, {76, high}, {76, middle}, {101, middle}};
            case 4 -> new float[][]{{7, middle}, {28, middle}, {28, high}, {67, high}, {67, middle}, {101, middle}};
            default -> new float[][]{{7, high}, {36, high}, {36, low}, {72, low}, {72, middle}, {101, middle}};
        };
        routes.add(transform(points, flipX, flipY));
        if(shape >= 4){
            routes.add(transform(new float[][]{{7, middle}, {18, middle}, {18, low}, {56, low}, {56, middle}, {101, middle}}, flipX, flipY));
        }
        base = routes.first()[routes.first().length - 1].cpy();
        lengths = new float[routes.size];
        for(int r = 0; r < routes.size; r++){
            Vec2[] route = routes.get(r);
            for(int i = 1; i < route.length; i++) lengths[r] += route[i - 1].dst(route[i]);
        }
        // Every socket lies beside a road, never on it. Spread them along both branches.
        for(int i = 0; i < pads.length; i++){
            int route = i % routes.size;
            Vec2 anchor = position(route, lengths[route] * (0.08f + i * 0.075f), new Vec2());
            Vec2 best = null;
            float score = -Float.MAX_VALUE;
            for(int attempt = 0; attempt < 180; attempt++){
                Vec2 candidate = new Vec2(Mathf.clamp(anchor.x + random.random(-100f, 100f), 48f, width * 8f - 48f),
                    Mathf.clamp(anchor.y + random.random(-100f, 100f), 48f, height * 8f - 48f));
                candidate.set(Math.round(candidate.x / 8f) * 8f, Math.round(candidate.y / 8f) * 8f);
                float road = roadDistance(candidate.x, candidate.y), spacing = 150f;
                for(int j = 0; j < i; j++) spacing = Math.min(spacing, candidate.dst(pads[j]));
                float value = Math.min(spacing, 95f) - Math.abs(road - 42f) * 3f - candidate.dst(anchor) * 0.12f;
                if(candidate.dst(base) < 50f || road < 25f) continue;
                if(value > score){ score = value; best = candidate; }
            }
            pads[i] = best == null ? anchor.cpy().add(0f, 40f) : best;
        }
    }

    private Vec2[] transform(float[][] points, boolean flipX, boolean flipY){
        Vec2[] result = new Vec2[points.length];
        for(int i = 0; i < points.length; i++) result[i] = new Vec2((flipX ? width - 1 - points[i][0] : points[i][0]) * 8f,
            (flipY ? height - 1 - points[i][1] : points[i][1]) * 8f);
        return result;
    }

    public Vec2 position(int routeIndex, float progress, Vec2 out){
        Vec2[] route = routes.get(Math.floorMod(routeIndex, routes.size));
        for(int i = 1; i < route.length; i++){
            float length = route[i - 1].dst(route[i]);
            if(progress <= length) return out.set(route[i - 1]).lerp(route[i], Mathf.clamp(progress / length));
            progress -= length;
        }
        return out.set(route[route.length - 1]);
    }

    public float roadDistance(float x, float y){
        float nearest = Float.MAX_VALUE;
        for(Vec2[] route : routes) for(int i = 1; i < route.length; i++){
            Vec2 a = route[i - 1], b = route[i];
            float dx = b.x - a.x, dy = b.y - a.y;
            float t = Mathf.clamp(((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy));
            nearest = Math.min(nearest, Mathf.dst(x, y, a.x + dx * t, a.y + dy * t));
        }
        return nearest;
    }

    public void generate(Tiles tiles){
        tiles.fill();
        Block[][] biomes = {
            {Blocks.deepwater, Blocks.water, Blocks.sandWater, Blocks.sand, Blocks.grass, Blocks.moss, Blocks.stone, Blocks.dirt, Blocks.pine, Blocks.boulder, Blocks.stoneWall},
            {Blocks.deepwater, Blocks.water, Blocks.darksandWater, Blocks.darksand, Blocks.sand, Blocks.darksand, Blocks.redStone, Blocks.darksand, Blocks.shrubs, Blocks.sandBoulder, Blocks.duneWall},
            {Blocks.deepwater, Blocks.water, Blocks.ice, Blocks.iceSnow, Blocks.snow, Blocks.iceSnow, Blocks.dacite, Blocks.iceSnow, Blocks.snowPine, Blocks.snowBoulder, Blocks.snowWall},
            {Blocks.slag, Blocks.slag, Blocks.magmarock, Blocks.hotrock, Blocks.basalt, Blocks.darksand, Blocks.shale, Blocks.basalt, Blocks.basaltBoulder, Blocks.basaltBoulder, Blocks.shaleWall},
            {Blocks.space, Blocks.space, Blocks.salt, Blocks.salt, Blocks.stone, Blocks.snow, Blocks.dacite, Blocks.stone, Blocks.whiteTree, Blocks.daciteBoulder, Blocks.daciteWall},
            {Blocks.deepwater, Blocks.water, Blocks.darksandWater, Blocks.salt, Blocks.crystallineStone, Blocks.crystalFloor, Blocks.denseRedStone, Blocks.crystallineStone, Blocks.crystalCluster, Blocks.vibrantCrystalCluster, Blocks.crystallineStoneWall}
        };
        Block[] biome = biomes[theme];
        Rand decor = new Rand(seed ^ 0x4ac731L);
        tiles.eachTile(tile -> {
            float elevation = (float)Simplex.noise2d((int)seed, 3, 0.5, 0.015, tile.x, tile.y);
            float detail = (float)Simplex.noise2d((int)seed + 63, 2, 0.5, 0.065, tile.x, tile.y);
            float moisture = (float)Simplex.noise2d((int)seed + 117, 2, 0.5, 0.023, tile.x, tile.y);
            // Wide, connected landforms with shallow-water and shore transitions.
            int terrain = elevation < 0.33f ? 0 : elevation < 0.40f ? 1 : elevation < 0.44f ? 2 : elevation < 0.48f ? 3
                : elevation > 0.68f ? 6 : moisture > 0.56f ? 5 : 4;
            tile.setFloor(biome[terrain].asFloor());
            float road = roadDistance(tile.worldx(), tile.worldy());
            if(road < 20f){
                boolean bridge = tile.floor().isLiquid || terrain < 3 || theme == 4 && terrain < 4;
                tile.setFloor((bridge ? road < 14f ? Blocks.metalFloor2 : Blocks.metalFloor3 : road < 14f ? biome[7] : biome[6]).asFloor());
            }else if(terrain >= 3 && road > 34f){
                if(elevation > 0.72f && detail > 0.52f && road > 65f) tile.setBlock(biome[10]);
                else if(moisture > 0.55f && detail > 0.52f && decor.chance(theme == 5 ? 0.045 : 0.09)) tile.setBlock(biome[8]);
                else if(detail > 0.64f && decor.chance(0.10)) tile.setBlock(biome[9]);
            }
        });
        for(Vec2 pad : pads) for(int dx = -2; dx <= 2; dx++) for(int dy = -2; dy <= 2; dy++){
            Tile tile = tiles.getn(Math.round(pad.x / 8f) + dx, Math.round(pad.y / 8f) + dy);
            tile.setBlock(Blocks.air);
            tile.setFloor((Math.abs(dx) == 2 || Math.abs(dy) == 2 ? Blocks.metalFloor3 : Blocks.metalFloor2).asFloor());
        }
        for(int dx = -4; dx <= 4; dx++) for(int dy = -4; dy <= 4; dy++){
            Tile tile = tiles.getn(Math.round(base.x / 8f) + dx, Math.round(base.y / 8f) + dy);
            tile.setBlock(Blocks.air);
            tile.setFloor((Math.abs(dx) == 4 || Math.abs(dy) == 4 ? Blocks.metalFloor3 : Blocks.metalFloor2).asFloor());
        }
        tiles.getn(Math.round(base.x / 8f), Math.round(base.y / 8f)).setBlock(Blocks.coreShard, Team.sharded, 0);
    }
}
