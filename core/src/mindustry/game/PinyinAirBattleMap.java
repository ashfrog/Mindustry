package mindustry.game;

import arc.math.*;
import arc.math.geom.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

/**
 * A deliberately open vertical arena for the air-run mode.  There are no solid
 * walls or land/water rules here: the scrolling field is only a visual frame for
 * the two aircraft, enemy waves and falling pickups.
 */
public class PinyinAirBattleMap{
    public static final int width = 176, height = 408;

    private final int theme;
    private final int seed;
    private final Rand random;
    private final boolean[] clouds = new boolean[width * height];

    public PinyinAirBattleMap(long seed, int chapter){
        random = new Rand(seed);
        this.seed = (int)(seed ^ (seed >>> 32));
        theme = Math.floorMod(chapter - 1, 4);
        for(int i = 0; i < clouds.length; i++) clouds[i] = random.chance(0.035f);
    }

    public int theme(){ return theme; }
    public int entryX(){ return width / 2; }
    public int entryY(){ return 26; }
    public int bossX(){ return width / 2; }
    public int bossY(){ return height - 34; }

    public void generate(Tiles tiles){
        tiles.fill();
        Floor[][] palettes = {
            {(Floor)Blocks.space, (Floor)Blocks.empty, (Floor)Blocks.snow, (Floor)Blocks.ice,
                (Floor)Blocks.metalFloor5},
            {(Floor)Blocks.space, (Floor)Blocks.darksand, (Floor)Blocks.basalt, (Floor)Blocks.craters,
                (Floor)Blocks.metalFloor3},
            {(Floor)Blocks.space, (Floor)Blocks.metalFloor, (Floor)Blocks.metalFloor2, (Floor)Blocks.metalFloor5,
                (Floor)Blocks.metalFloor4},
            {(Floor)Blocks.space, (Floor)Blocks.snow, (Floor)Blocks.ice, (Floor)Blocks.salt,
                (Floor)Blocks.metalFloor5}
        };
        Floor[] floors = palettes[Math.floorMod(theme, palettes.length)];
        tiles.eachTile(tile -> {
            int x = tile.x, y = tile.y;
            // Two-scale simplex noise creates broad biome patches plus small
            // terrain grain instead of visible horizontal floor bands.
            float macro = (float)Simplex.noise2d(seed, 4, 0.58f, 1f / 70f, x, y);
            float detail = (float)Simplex.noise2d(seed + 17, 3, 0.55f, 1f / 20f, x, y);
            float ridge = Math.abs((float)Simplex.noise2d(seed + 31, 2, 0.5f, 1f / 38f, x + y * 0.35f, y));
            int band = Math.floorMod((int)((macro + 1f) * 2.1f + (detail + 1f) * 1.4f), floors.length);
            if(ridge < 0.08f) band = Math.floorMod(band + 2, floors.length);
            tile.setFloor(floors[band]);
            tile.setBlock(Blocks.air);
            tile.setOverlay(Blocks.air);

            // Overlays are lightweight scenery: they add texture and landmarks
            // without creating walls that can trap an aircraft or block a route.
            if(detail > 0.56f && macro > -0.25f) tile.setOverlay(Blocks.pebbles);
            else if(detail < -0.62f && macro < 0.45f) tile.setOverlay(Blocks.tendrils);
            else if(ridge > 0.84f && (x + y) % 3 == 0) tile.setOverlay(Blocks.oreCopper);
        });
        // A core gives the local team a valid anchor while remaining outside the
        // flight corridor; it does not impose a route or a win condition.
        tiles.getn(entryX(), entryY()).setBlock(Blocks.coreShard, Team.sharded, 0);
        for(int i = 0; i < 6; i++){
            int x = 12 + random.random(width - 24), y = 40 + random.random(height - 80);
            for(int dx = -3; dx <= 3; dx++) for(int dy = -1; dy <= 1; dy++){
                clouds[Math.floorMod(x + dx, width) + Math.floorMod(y + dy, height) * width] = true;
            }
        }
        paintLandmarks(tiles);
    }

    /** Adds seeded cloud banks, meteor scars and abandoned air-lane islands. */
    private void paintLandmarks(Tiles tiles){
        int count = 12 + Math.floorMod(seed, 7);
        for(int i = 0; i < count; i++){
            int cx = 14 + random.random(width - 28), cy = 42 + random.random(height - 84);
            if(Mathf.dst(cx, cy, entryX(), entryY()) < 26f || Mathf.dst(cx, cy, bossX(), bossY()) < 26f) continue;
            int rx = 4 + random.random(10), ry = 2 + random.random(8);
            for(int x = cx - rx; x <= cx + rx; x++) for(int y = cy - ry; y <= cy + ry; y++){
                if(!tiles.in(x, y)) continue;
                float ellipse = (x - cx) * (x - cx) / (float)(rx * rx) + (y - cy) * (y - cy) / (float)(ry * ry);
                if(ellipse > 1f) continue;
                Tile tile = tiles.getn(x, y);
                tile.setFloor((Floor)landmarkFloor(i));
                if((x + y + i) % 4 == 0) tile.setOverlay(Blocks.pebbles);
                else if((x * 3 + y + i) % 7 == 0) tile.setOverlay(Blocks.tendrils);
            }
            // A thin bright rim gives each generated island a readable edge.
            for(int x = cx - rx - 1; x <= cx + rx + 1; x++) for(int y = cy - ry - 1; y <= cy + ry + 1; y++){
                if(!tiles.in(x, y)) continue;
                float outer = (x - cx) * (x - cx) / (float)((rx + 1) * (rx + 1))
                    + (y - cy) * (y - cy) / (float)((ry + 1) * (ry + 1));
                float inner = (x - cx) * (x - cx) / (float)(Math.max(1, rx - 1) * Math.max(1, rx - 1))
                    + (y - cy) * (y - cy) / (float)(Math.max(1, ry - 1) * Math.max(1, ry - 1));
                if(outer <= 1f && inner > 1f) tiles.getn(x, y).setOverlay(Blocks.oreCopper);
            }
        }
    }

    private Block landmarkFloor(int index){
        return switch(Math.floorMod(theme + index, 4)){
            case 0 -> Blocks.metalFloor3;
            case 1 -> Blocks.basalt;
            case 2 -> Blocks.snow;
            default -> Blocks.metalFloor5;
        };
    }

    public boolean cloud(int x, int y){
        return x >= 0 && y >= 0 && x < width && y < height && clouds[x + y * width];
    }
}
