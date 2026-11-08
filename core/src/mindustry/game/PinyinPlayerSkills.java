package mindustry.game;

import arc.math.geom.*;
import mindustry.content.*;
import mindustry.gen.*;

/** Personal temporary skills, separate from permanent answer weapon growth. Times are ticks. */
public class PinyinPlayerSkills{
    public static final float ultimateDuration = 600f, ultimateCooldown = 1800f;
    public static final float prankDuration = 180f, prankCooldown = 360f, prankRange = 220f;
    private final float[] ultimate = new float[2], cooldown = new float[2], prank = new float[2], prankWait = new float[2];
    private final Unit[] affected = new Unit[2];
    private final Vec2 pull = new Vec2();

    public float ultimateTime(int player){ return ultimate[player]; }
    public float cooldown(int player){ return cooldown[player]; }
    public boolean pulling(int player){ return prank[player] > 0f; }

    public boolean activateUltimate(int player, Unit unit){
        if(!alive(unit) || cooldown[player] > 0f) return false;
        ultimate[player] = ultimateDuration;
        cooldown[player] = ultimateCooldown;
        return true;
    }

    public boolean activatePrank(int player, Unit owner, Unit other){
        if(!alive(owner) || !alive(other) || prankWait[player] > 0f || !owner.within(other, prankRange)) return false;
        prank[player] = prankDuration;
        prankWait[player] = prankCooldown;
        return true;
    }

    public void update(float delta, Unit first, Unit second){
        for(int i = 0; i < 2; i++){
            Unit owner = i == 0 ? first : second, other = i == 0 ? second : first;
            cooldown[i] = Math.max(0f, cooldown[i] - delta);
            prankWait[i] = Math.max(0f, prankWait[i] - delta);
            ultimate[i] = alive(owner) ? Math.max(0f, ultimate[i] - delta) : 0f;
            prank[i] = alive(owner) && alive(other) && owner.within(other, prankRange + 40f)
                ? Math.max(0f, prank[i] - delta) : 0f;
        }
        for(int i = 0; i < 2; i++){
            Unit owner = i == 0 ? first : second, other = i == 0 ? second : first;
            if(affected[i] != owner && affected[i] != null) affected[i].unapply(StatusEffects.dynamic);
            affected[i] = owner;
            if(!alive(owner)){
                if(owner != null) owner.unapply(StatusEffects.dynamic);
                continue;
            }
            if(ultimate[i] > 0f || prank[i] > 0f || prank[1 - i] > 0f){
                var status = owner.applyDynamicStatus();
                status.speedMultiplier = (ultimate[i] > 0f ? 1.6f : 1f)
                    * (prank[i] > 0f ? 1.35f : 1f) * (prank[1 - i] > 0f ? 0.65f : 1f);
                status.damageMultiplier = ultimate[i] > 0f ? 2f : 1f;
                status.reloadMultiplier = ultimate[i] > 0f ? 2.2f : 1f;
            }else{
                owner.unapply(StatusEffects.dynamic);
            }
            if(prank[1 - i] > 0f && owner.dst(other) > 22f){
                // Small positional pull cannot teleport or pin a player. Dash can break range.
                owner.move(pull.set(other).sub(owner).limit(0.9f * delta));
            }
        }
    }

    public void reset(){
        for(int i = 0; i < 2; i++){
            if(affected[i] != null) affected[i].unapply(StatusEffects.dynamic);
            affected[i] = null;
            ultimate[i] = cooldown[i] = prank[i] = prankWait[i] = 0f;
        }
    }

    private boolean alive(Unit unit){ return unit != null && !unit.dead && unit.isAdded(); }
}
