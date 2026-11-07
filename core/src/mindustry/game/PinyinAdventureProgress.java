package mindustry.game;

/** Objectives advance through four stages, then continue into another randomized chapter. */
public class PinyinAdventureProgress{
    public static final float chargeNeeded = 60f * 25f;
    private int stage, chapter, answers, kills;
    private float charge;
    private boolean bossDefeated;

    public void reset(){
        stage = 0;
        chapter = 1;
        clearObjectives();
    }

    private void clearObjectives(){
        answers = kills = 0;
        charge = 0f;
        bossDefeated = false;
    }

    public int stage(){ return stage; }
    public int chapter(){ return chapter; }
    public int difficulty(){ return (chapter - 1) * PinyinAdventureMap.stages + stage + 1; }
    public int waveSize(){ return Math.min(40, 4 + difficulty()); }
    public int enemyLimit(){ return Math.min(300, 36 + difficulty() * 8); }
    public float waveInterval(){ return 60f * Math.max(2.5f, 6f / (1f + (difficulty() - 1) * 0.14f)); }
    public int answers(){ return answers; }
    public int kills(){ return kills; }
    public int requiredAnswers(){ return stage == 0 ? 3 : 4; }
    public int requiredKills(){ return stage == 0 ? 8 : stage == 1 ? 12 : 0; }
    public int chargeSeconds(){ return (int)(charge / 60f); }
    public float chargeProgress(){ return charge / chargeNeeded; }
    public boolean bossDefeated(){ return bossDefeated; }
    public void answer(){ answers++; }
    public void defeat(boolean boss){ kills++; bossDefeated |= boss; }

    public void update(float delta, boolean nearBeacon){
        if(delta <= 0f) return;
        if(stage == 2 && answers >= requiredAnswers() && nearBeacon){
            charge = Math.min(chargeNeeded, charge + delta);
        }
    }

    public boolean ready(){
        return answers >= requiredAnswers() && kills >= requiredKills()
            && (stage != 2 || charge >= chargeNeeded) && (stage != 3 || bossDefeated);
    }

    /** Returns true only when a newly randomized map is needed. */
    public boolean advance(){
        if(!ready()) return false;
        stage++;
        boolean nextChapter = stage == PinyinAdventureMap.stages;
        if(nextChapter){
            stage = 0;
            chapter++;
        }
        clearObjectives();
        return nextChapter;
    }
}
