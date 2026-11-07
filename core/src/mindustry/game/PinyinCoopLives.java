package mindustry.game;

/** Shared lives and simulation-time respawns for the two escape players. */
public class PinyinCoopLives{
    public static final int startingLives = 5;
    public static final float respawnDelay = 60f * 5f;
    private final boolean[] alive = new boolean[2];
    private final float[] countdown = new float[2];
    private int remaining;
    private boolean ended;

    public PinyinCoopLives(){
        reset();
    }

    public void reset(){
        remaining = startingLives;
        ended = false;
        for(int i = 0; i < 2; i++){
            alive[i] = true;
            countdown[i] = -1f;
        }
    }

    public void update(boolean firstAlive, boolean secondAlive, float delta){
        if(ended) return;
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

    public void revived(int index){
        if(!ready(index)) return;
        alive[index] = true;
        countdown[index] = -1f;
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
}
