package mindustry.game;

/** Personal streak rewards and a short, reusable escape dash. Times use game ticks. */
public class PinyinPlayerMomentum{
    public static final float dashDuration = 15f, dashRecharge = 60f * 4f;
    private int streak;
    private float recharge, dashTime;

    public int streak(){ return streak; }
    public boolean dashing(){ return dashTime > 0f; }
    public float dashSeconds(){ return (float)Math.ceil(recharge / 60f); }

    public boolean correct(){ return ++streak % 3 == 0; }
    public void wrong(){ streak = 0; }

    public void update(float delta){
        recharge = Math.max(0f, recharge - delta);
        dashTime = Math.max(0f, dashTime - delta);
    }

    public boolean dash(){
        if(recharge > 0f) return false;
        recharge = dashRecharge;
        dashTime = dashDuration;
        return true;
    }

    public void reset(){
        streak = 0;
        recharge = dashTime = 0f;
    }
}
