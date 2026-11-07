package mindustry.game;

/**
 * Small repeatable side challenges for the escape map. Events are world objectives,
 * so they never pause the action or open a blocking dialog.
 */
public class PinyinAdventureEvents{
    public enum Kind{hunt, rescue, rhythm, survive}
    public static final float duration = 60f * 32f;

    private Kind kind;
    private float remaining, progress;
    private int target, count;
    private boolean active, completed, failed;

    public void reset(){
        kind = null;
        remaining = progress = 0f;
        target = count = 0;
        active = completed = failed = false;
    }

    public void start(Kind event, int difficulty){
        kind = event;
        remaining = duration + Math.min(60f * 12f, difficulty * 30f);
        progress = count = 0;
        target = switch(event){
            case hunt -> Math.min(12, 3 + difficulty / 2);
            case rhythm -> 3 + Math.min(3, difficulty / 8);
            case rescue -> 1;
            case survive -> 1;
        };
        active = true;
        completed = failed = false;
    }

    public void update(float delta, boolean insideZone){
        if(!active || delta <= 0f) return;
        remaining = Math.max(0f, remaining - delta);
        if(kind == Kind.rescue && insideZone) progress = Math.min(60f * 8f, progress + delta);
        if(kind == Kind.survive && remaining <= 0f) complete();
        if(kind == Kind.rescue && progress >= 60f * 8f) complete();
        if(remaining <= 0f && active) fail();
    }

    public void defeated(){
        if(active && kind == Kind.hunt && ++count >= target) complete();
    }

    public void answered(){
        if(active && kind == Kind.rhythm && ++count >= target) complete();
    }

    private void complete(){ active = false; completed = true; }
    private void fail(){ active = false; failed = true; }

    public boolean active(){ return active; }
    public boolean completed(){ return completed; }
    public boolean failed(){ return failed; }
    public boolean consumeCompleted(){
        if(!completed) return false;
        completed = false;
        return true;
    }
    public Kind kind(){ return kind; }
    public float secondsLeft(){ return (int)Math.ceil(remaining / 60f); }
    public float progress(){
        if(kind == Kind.rescue) return progress / (60f * 8f);
        return target == 0 ? 0f : Math.min(1f, count / (float)target);
    }
    public int count(){ return count; }
    public int target(){ return target; }
}
