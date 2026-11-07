package mindustry.game;

import arc.math.geom.*;
import arc.math.*;
import arc.struct.*;
import mindustry.world.*;
import mindustry.core.*;
import java.util.*;

import static mindustry.Vars.*;

/** A single shared question, answered once by walking onto a world pickup. */
public class PinyinWorldQuestion{
    public static final float duration = 60f * 5f, pickupRadius = tilesize * 1.1f;
    public static final int pending = -2, timeout = -1;
    public final Vec2[] positions = {new Vec2(), new Vec2(), new Vec2()};
    private final Vec2[] hintOrigins = {new Vec2(), new Vec2(), new Vec2()};
    public final int[] choices = new int[3];
    public static final float chargeDuration = 60f * 2f, chargeRadius = tilesize * 3f;
    private final float[] chestCharge = new float[PinyinAdventureMap.chestCount];
    private int chargeOpener = -1;
    private int chestIndex;
    private int collector = -1;
    private float remaining;
    private boolean active;
    private float hintPhase;

    public boolean active(){ return active; }
    public int chestIndex(){ return chestIndex; }
    /** Player slot that picked the option, or -1 for a timeout/no selection. */
    public int collector(){ return collector; }
    public float secondsLeft(){ return Math.max(0f, remaining / 60f); }
    public void clear(){ active = false; collector = -1; clearCharge(); }
    public float chargeProgress(int index){ return chestCharge[index] / chargeDuration; }
    public int chargeOpener(){ return chargeOpener; }

    private void clearCharge(){ Arrays.fill(chestCharge, 0f); chargeOpener = -1; }

    public void begin(int chest, Vec2[] locations, int[] order){
        clearCharge();
        chestIndex = chest;
        collector = -1;
        remaining = duration;
        active = true;
        hintPhase = 0f;
        for(int i = 0; i < 3; i++){
            positions[i].set(locations[i]);
            hintOrigins[i].set(locations[i]);
            choices[i] = order[i];
        }
    }

    /**
     * Once the gentle answer hint appears, the correct pickup becomes a living target:
     * it backs away from its owner and wiggles inside a small radius around its spawn point.
     */
    public void updateHint(float delta, Position hunter){
        if(!active || delta <= 0f || secondsLeft() > 2.5f || hunter == null) return;
        hintPhase += delta;
        for(int i = 0; i < 3; i++){
            if(choices[i] != 0) continue;
            Vec2 position = positions[i], origin = hintOrigins[i];
            float dx = position.x - hunter.getX(), dy = position.y - hunter.getY();
            float length = Mathf.len(dx, dy);
            if(length < tilesize * 9f && length > 0.01f){
                float speed = tilesize * 0.055f;
                position.x += dx / length * speed * delta;
                position.y += dy / length * speed * delta;
            }else{
                float angle = hintPhase * 3f + i * 120f;
                position.x += Mathf.cos(angle) * tilesize * 0.012f * delta;
                position.y += Mathf.sin(angle) * tilesize * 0.012f * delta;
            }
            // Keep the moving answer close enough to chase and always inside its local arena.
            float max = tilesize * 4f;
            float ox = position.x - origin.x, oy = position.y - origin.y, offset = Mathf.len(ox, oy);
            if(offset > max) position.set(origin.x + ox / offset * max, origin.y + oy / offset * max);
        }
    }

    public int update(float delta, Position first, Position second){
        if(!active || delta <= 0f) return pending;
        remaining = Math.max(0f, remaining - delta);
        int result = pending;
        if(remaining <= 0f){
            result = timeout;
        }else if(duration - remaining >= 15f){
            for(int i = 0; i < 3; i++){
                boolean firstHere = first != null && positions[i].within(first, pickupRadius);
                boolean secondHere = second != null && positions[i].within(second, pickupRadius);
                if(firstHere || secondHere){
                    result = choices[i];
                    collector = firstHere ? 0 : 1;
                    break;
                }
            }
        }
        if(result != pending) active = false;
        return result;
    }

    /** A continuous human presence charges each bunker once, even when both players stand inside. */
    public int updateCharge(float delta, Position first, Position second, PinyinAdventureMap map, int stage, boolean[] unavailable, boolean enabled){
        if(delta <= 0f) return -1;
        if(active || !enabled){ clearCharge(); return -1; }
        int ready = -1;
        for(int i = 0; i < chestCharge.length; i++){
            Point2 chest = map.chests[i];
            boolean available = i / PinyinAdventureMap.chestsPerStage == stage && !unavailable[i];
            boolean firstHere = available && first != null && first.within(chest.x * tilesize, chest.y * tilesize, chargeRadius);
            boolean secondHere = available && second != null && second.within(chest.x * tilesize, chest.y * tilesize, chargeRadius);
            if(!firstHere && !secondHere){ chestCharge[i] = 0f; continue; }
            chestCharge[i] = Math.min(chargeDuration, chestCharge[i] + delta);
            if(chestCharge[i] >= chargeDuration && ready < 0){ ready = i; chargeOpener = firstHere ? 0 : 1; }
        }
        return ready;
    }

    /** Breadth-first search keeps every option within ten walkable tiles of the opener. */
    public static Vec2[] placeOptions(Tiles tiles, int stage, Position opener, Position teammate){
        return placeOptions(tiles, stage, opener, teammate, null);
    }

    /** Place a second player's answer set away from an already active answer set. */
    public static Vec2[] placeOptions(Tiles tiles, int stage, Position opener, Position teammate, Vec2[] excluded){
        int[] distances = new int[tiles.width * tiles.height];
        Arrays.fill(distances, -1);
        ArrayDeque<Tile> queue = new ArrayDeque<>();
        Tile origin = tiles.get(World.toTile(opener.getX()), World.toTile(opener.getY()));
        if(origin == null || origin.solid()) return null;
        queue.add(origin);
        distances[origin.x + origin.y * tiles.width] = 0;
        Seq<Vec2> candidates = new Seq<>();
        while(!queue.isEmpty()){
            Tile tile = queue.remove();
            int steps = distances[tile.x + tile.y * tiles.width];
            Vec2 position = new Vec2(tile.worldx(), tile.worldy());
            boolean blocked = steps < 3 || position.within(opener, tilesize * 2.5f)
                || (teammate != null && position.within(teammate, tilesize * 2.5f));
            if(!blocked && excluded != null){
                for(Vec2 other : excluded) if(other != null && position.within(other, tilesize * 2.5f)){ blocked = true; break; }
            }
            if(!blocked) candidates.add(position);
            if(steps >= 10) continue;
            for(Point2 direction : Geometry.d4){
                int x = tile.x + direction.x, y = tile.y + direction.y;
                Tile next = tiles.get(x, y);
                if(next == null || x < PinyinAdventureMap.left(stage) + 2 || x > PinyinAdventureMap.right(stage) - 2
                    || y < 3 || y >= tiles.height - 3 || next.solid() || distances[x + y * tiles.width] >= 0) continue;
                distances[x + y * tiles.width] = steps + 1;
                queue.add(next);
            }
        }
        candidates.shuffle();
        Seq<Vec2> selected = new Seq<>();
        for(Vec2 candidate : candidates){
            boolean separated = true;
            for(Vec2 previous : selected) if(previous.within(candidate, tilesize * 3f)) separated = false;
            if(separated) selected.add(candidate);
            if(selected.size == 3) return selected.toArray(Vec2.class);
        }
        return null;
    }
}
