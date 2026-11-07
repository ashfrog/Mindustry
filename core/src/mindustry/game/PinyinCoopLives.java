package mindustry.game;

/** Shared lives and simulation-time respawns for the two escape players. */
public class PinyinCoopLives{
    public static final int startingLives = 5;
    public static final float respawnDelay = 60f * 5f;
    public static final float lifeInterval = 60f * 60f;
    private final boolean[] alive = new boolean[2];
    private final float[] countdown = new float[2];
    private int remaining;
    private boolean ended;
    private float lifeClock;

    public PinyinCoopLives(){
        reset();
    }

    public void reset(){
        remaining = startingLives;
        ended = false;
        lifeClock = 0f;
        for(int i = 0; i < 2; i++){
            alive[i] = true;
            countdown[i] = -1f;
        }
    }

    public void update(boolean firstAlive, boolean secondAlive, float delta){
        // A wiped team may still spend its remaining lives on the two respawn timers.
        // Only zero lives is a terminal game-over state.
        if(ended && remaining == 0) return;
        if(delta > 0f && (firstAlive || secondAlive)){
            lifeClock += delta;
            while(lifeClock >= lifeInterval){
                lifeClock -= lifeInterval;
                remaining++;
            }
        }
        for(int i = 0; i < 2; i++){
            boolean present = i == 0 ? firstAlive : secondAlive;
            if(alive[i] && !present){
                remaining = Math.max(0, remaining - 1);
                countdown[i] = respawnDelay;
            }else if(!present && countdown[i] >= 0f){
                countdown[i] = Math.max(0f, countdown[i] - delta);
            }
            alive[i] = present;
        }
        ended = remaining == 0 || (!firstAlive && !secondAlive);
    }

    public boolean ready(int index){
        return !ended && !alive[index] && alive[1 - index] && countdown[index] == 0f;
    }

    public boolean teamDown(){
        return ended && remaining > 0 && !alive[0] && !alive[1];
    }

    public boolean respawnReady(int index){
        return teamDown() && !alive[index] && countdown[index] == 0f;
    }

    public void revived(int index){
        if(!ready(index) && !respawnReady(index)) return;
        alive[index] = true;
        countdown[index] = -1f;
        ended = false;
    }

    public int remaining(){
        return remaining;
    }

    public int secondsLeft(int index){
        return alive[index] ? 0 : (int)Math.ceil(Math.max(0f, countdown[index]) / 60f);
    }

    public boolean ended(){
        return ended;
    }

    /** Progress toward the next automatically earned life, useful for a small HUD meter. */
    public float lifeProgress(){
        return Math.min(1f, lifeClock / lifeInterval);
    }

    /** A rare pickup can grant a life without bypassing the game-over state. */
    public void addLife(){
        if(!ended) remaining++;
    }
}
