package mindustry.game;

import arc.math.geom.*;
import arc.math.*;
import mindustry.gen.*;
import mindustry.core.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/** A captured pinyin syllable that follows its player until it reaches the rescue ship. */
public class PinyinRescuePayload{
    public final String character, pinyin;
    public int owner;
    public final PinyinTeamEffects.Reward reward;
    public final PinyinArsenal.Kind weapon;
    /** Delivery rewards wait until the captured syllable reaches the ship. */
    public final boolean rewardGranted;
    public final Vec2 position = new Vec2();
    private final Vec2 velocity = new Vec2();
    private float phase;
    private boolean carried;
    private boolean escaping = true;

    public PinyinRescuePayload(String character, String pinyin, int owner, float x, float y){
        this(character, pinyin, owner, x, y, PinyinTeamEffects.Reward.power, PinyinArsenal.Kind.rapid, false);
    }

    public PinyinRescuePayload(String character, String pinyin, int owner, float x, float y,
                               PinyinTeamEffects.Reward reward, PinyinArsenal.Kind weapon){
        this(character, pinyin, owner, x, y, reward, weapon, false);
    }

    public PinyinRescuePayload(String character, String pinyin, int owner, float x, float y,
                               PinyinTeamEffects.Reward reward, PinyinArsenal.Kind weapon, boolean rewardGranted){
        this.character = character;
        this.pinyin = pinyin;
        this.owner = owner;
        this.reward = reward;
        this.weapon = weapon;
        this.rewardGranted = rewardGranted;
        position.set(x, y);
        // A rescued syllable starts in the world.  It must be chased down before it
        // becomes a small follower, which keeps the rescue loop active instead of
        // handing the reward to the player immediately.
        carried = false;
    }

    public boolean carried(){ return carried; }
    public boolean escaping(){ return !carried && escaping; }

    public void drop(float x, float y){
        carried = false;
        escaping = false;
        position.set(x, y);
    }

    public void collect(int newOwner){
        owner = newOwner;
        carried = true;
        escaping = false;
    }

    /** Move away from the nearest player at a chaseable speed, sliding along walls. */
    public void updateEscape(float delta, Position hunter){
        if(carried || !escaping || delta <= 0f) return;
        phase += delta;
        float dx, dy;
        if(hunter != null){
            dx = position.x - hunter.getX();
            dy = position.y - hunter.getY();
            float length = Mathf.len(dx, dy);
            if(length > 0.01f){
                dx /= length;
                dy /= length;
            }else{
                float angle = phase * 2.7f;
                dx = Mathf.cos(angle);
                dy = Mathf.sin(angle);
            }
            // Deliberately below a normal player's top speed: the child can always
            // catch the answer by following it, while the wiggle avoids a straight line.
            float side = Mathf.sin(phase * 0.08f) * 0.28f;
            float sideX = -dy * side, sideY = dx * side;
            dx += sideX;
            dy += sideY;
        }else{
            float angle = phase * 1.8f;
            dx = Mathf.cos(angle);
            dy = Mathf.sin(angle);
        }
        float length = Mathf.len(dx, dy);
        if(length > 0.01f){ dx /= length; dy /= length; }
        float speed = tilesize * 0.045f;
        moveOnWalkableTile(dx * speed * delta, dy * speed * delta);
    }

    public void update(float delta, Position anchor, int order){
        if(!carried || delta <= 0f || anchor == null) return;
        phase += delta;
        float targetX = anchor.getX() - 22f - order * 12f;
        float targetY = anchor.getY() + Mathf.sin(phase * 0.08f + order) * 7f;
        position.lerp(targetX, targetY, Math.min(1f, delta * 0.08f));
        Tile tile = world.tileWorld(position.x, position.y);
        if(tile != null && tile.solid()) position.set(anchor.getX() - 20f - order * 10f, anchor.getY());
    }

    private void moveOnWalkableTile(float dx, float dy){
        velocity.set(position).add(dx, dy);
        Tile tile = world.tileWorld(velocity.x, velocity.y);
        if(tile != null && !tile.solid()){
            position.set(velocity);
            return;
        }
        velocity.set(position).add(dx, 0f);
        tile = world.tileWorld(velocity.x, velocity.y);
        if(tile != null && !tile.solid()){
            position.set(velocity);
            return;
        }
        velocity.set(position).add(0f, dy);
        tile = world.tileWorld(velocity.x, velocity.y);
        if(tile != null && !tile.solid()) position.set(velocity);
    }
}
