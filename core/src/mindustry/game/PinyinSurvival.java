package mindustry.game;

import arc.math.*;
import arc.math.geom.*;
import mindustry.ai.types.PinyinEnemyController.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;

/** Wave pacing and pursuit formations for free-roaming survival on the existing map. */
public class PinyinSurvival{
    public enum Role{
        chaser("追捕", 0.70f, 1f, 12f), tank("重装肉盾", 0.46f, 4.2f, 22f),
        runner("疾速追兵", 1.05f, 0.65f, 10f), blocker("拦截路障", 0.78f, 2.1f, 17f),
        flanker("侧翼包抄", 0.92f, 1.15f, 12f), artillery("远程火力", 0.50f, 0.9f, 13f);

        public final String label;
        public final float speed, health, size;
        Role(String label, float speed, float health, float size){
            this.label = label;
            this.speed = speed;
            this.health = health;
            this.size = size;
        }
    }

    public enum Pattern{aimed, fan, burst, ring, spiral, pincer, wave, mines, beam, crossfire, sweep, ambush, flower}
    public static final int maxEnemies = 220, maxEnemyBullets = 950;
    private static final Kind[] roster = {Kind.gunner, Kind.orbit, Kind.crawler, Kind.flame, Kind.artillery,
        Kind.spread, Kind.bomber, Kind.sniper, Kind.ice, Kind.burst, Kind.beam, Kind.electric,
        Kind.reign, Kind.eclipse, Kind.vela, Kind.mono, Kind.poly, Kind.mega, Kind.risso,
        Kind.retusa, Kind.sei, Kind.omura, Kind.navanax};

    public static int waveSize(int wave){ return Math.min(180, 8 + Math.max(1, wave) * 2); }
    public static int enemyLimit(int wave){ return Math.min(maxEnemies, 26 + Math.max(1, wave) * 3); }
    public static float interval(int wave){ return 60f * Math.max(20f, 28f - Math.max(1, wave) * 0.15f); }

    /** Clearing enemies shortens every rest to at most five seconds; a live boss remains a gate. */
    public static float afterClear(float timer, boolean enemiesAlive, boolean bossActive){
        return enemiesAlive || bossActive ? timer : Math.min(timer, 300f);
    }

    /** Wave ten is a gate: time alone can never start the next round. */
    public static class Rounds{
        private int round = 1, wave;
        private boolean boss;
        public int round(){ return round; }
        public int wave(){ return wave; }
        public int totalWave(){ return (round - 1) * 10 + wave; }
        public boolean bossActive(){ return boss; }
        public boolean nextWave(){
            if(boss) return false;
            wave++;
            boss = wave == 10;
            return true;
        }
        public boolean defeatBoss(){
            if(!boss) return false;
            round++;
            wave = 0;
            boss = false;
            return true;
        }
        public void reset(){ round = 1; wave = 0; boss = false; }
    }

    /** Shared single-use question: the first player touching either answer consumes it. */
    public static class AnswerAccess{
        public final int owner;
        public float age;
        private boolean consumed;
        public AnswerAccess(int owner){ this.owner = owner; }
        public boolean canAnswer(int player){ return !consumed; }
        public boolean collect(int player){
            if(!canAnswer(player)) return false;
            consumed = true;
            return true;
        }
        public boolean complete(){ return consumed; }
    }
    public static float healthScale(int wave){ return 1f + Math.max(0, wave - 1) * 0.22f + (float)Math.sqrt(Math.max(0, wave - 1)) * 0.4f; }
    public static float damageScale(int wave){ return 1f + (float)Math.log1p(Math.max(0, wave - 1)) * 0.24f; }
    public static Role role(int wave, int slot){ return Role.values()[Math.floorMod(slot + wave, Role.values().length)]; }
    public static Kind kind(int wave, int slot){
        return roster[Math.floorMod(wave * 3 + slot, Math.min(roster.length, 8 + Math.max(1, wave)))];
    }
    public static Pattern pattern(int wave, int slot){ return Pattern.values()[Math.floorMod(wave + slot, Pattern.values().length)]; }

    public static String[] answerChoices(PinyinQuestions.Question question, int correctSlot){
        String[] choices = new String[2];
        choices[Math.floorMod(correctSlot, 2)] = question.pinyin;
        choices[1 - Math.floorMod(correctSlot, 2)] = question.choices[1];
        return choices;
    }

    /** All carriers fly, so players and pursuers share collision elevation over every biome. */
    public static UnitType carrier(Role role, boolean boss){
        if(boss) return UnitTypes.antumbra;
        return switch(role){
            case tank -> UnitTypes.zenith;
            case blocker, artillery -> UnitTypes.horizon;
            case runner, chaser, flanker -> UnitTypes.flare;
        };
    }

    /** Eight-member squads share a heading but take different ring slots. */
    public static Vec2 destination(Vec2 out, Unit target, Role role, int squad, int slot, float phase){
        float heading = target.vel.isZero(0.15f) ? squad * 83f : target.vel.angle();
        float radius = switch(role){
            case chaser, runner -> 0f;
            case tank -> 14f;
            case blocker -> 52f;
            case flanker -> 40f;
            case artillery -> 135f;
        };
        float angle = heading + (role == Role.blocker ? (slot % 3 - 1) * 25f
            : role == Role.flanker ? (slot % 2 == 0 ? 85f : -85f) : slot * 45f);
        // Squads gather outside the player, then converge in a coordinated pincer.
        if((role == Role.blocker || role == Role.flanker) && phase % 420f > 290f) radius = 12f;
        return out.trns(angle, radius).add(target.x, target.y)
            .add(target.vel.x * (role == Role.blocker ? 18f : 5f), target.vel.y * (role == Role.blocker ? 18f : 5f));
    }

    /** Spawn on a ring outside the view; clamp at borders without spawning on a player. */
    public static Vec2 spawnPoint(Vec2 out, Unit target, float angle, float radius, float mapWidth, float mapHeight){
        out.trns(angle, radius).add(target);
        out.x = Mathf.clamp(out.x, 26f, mapWidth - 26f);
        out.y = Mathf.clamp(out.y, 26f, mapHeight - 26f);
        if(out.dst(target) < 140f){
            out.trns(angle + 180f, radius).add(target);
            out.x = Mathf.clamp(out.x, 26f, mapWidth - 26f);
            out.y = Mathf.clamp(out.y, 26f, mapHeight - 26f);
        }
        return out;
    }
}
