package mindustry.game;

import arc.math.*;

/** Shared bounds for a readable two-player arena, including late endless chapters. */
public class PinyinCombatBalance{
    public static final int maxBoosts = 12, maxPenalties = 4, maxShots = 9, maxEnemies = 48, maxHelpers = 4;
    public static final float maxDamageMultiplier = 6f, maxShield = 240f;

    public static int difficulty(int value){ return Mathf.clamp(value, 1, 12); }
    public static float enemyDamageScale(int difficulty){ return 1f + (difficulty(difficulty) - 1) * 0.065f; }
    public static float enemyHealthScale(int difficulty){ return 1f + (difficulty(difficulty) - 1) * 0.22f; }
    public static float contactDamage(int difficulty){ return 16f * enemyDamageScale(difficulty); }
    public static int shots(PinyinArsenal.Kind kind, int ordinaryShots){
        return Math.min(ordinaryShots, switch(kind){
            case laser, lightning, scatter -> 3;
            case fragment, artillery -> 5;
            default -> maxShots;
        });
    }
}
