package mindustry.game;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import java.text.*;
import mindustry.game.PinyinDefense.*;

/** A construction crate opens into three physical pickups; either pilot can resolve it once. */
public class PinyinDefensePickup{
    public static final float landingTime = 0.8f, pickupRadius = 13f;
    public final PinyinQuestions.Question question;
    public final int pad;
    public final TowerKind kind;
    public final String[] choices;
    public final Vec2[] positions = {new Vec2(), new Vec2(), new Vec2()};
    public float age;
    public boolean spoken;
    private boolean consumed;

    public PinyinDefensePickup(PinyinDefenseMap map, int pad, TowerKind kind, PinyinQuestions.Question question, Rand random, Seq<Vec2> occupied){
        this.pad = pad;
        this.kind = kind;
        this.question = question;
        choices = new String[]{question.pinyin, null, null};
        choices[1] = random.chance(0.35) ? question.choices[random.random(1, 2)] : distractor(question.pinyin, null, random);
        choices[2] = distractor(question.pinyin, choices[1], random);
        for(int i = 2; i > 0; i--){
            int other = random.random(i);
            String value = choices[i]; choices[i] = choices[other]; choices[other] = value;
        }
        Vec2 center = map.pads[pad];
        float best = -Float.MAX_VALUE, rotation = random.random(360f);
        // Fan the crates into open space, away from the other player's active drops.
        for(int attempt = 0; attempt < 24; attempt++){
            float angle = attempt * 15f, score = 200f;
            for(int i = 0; i < 3; i++){
                float x = center.x + Angles.trnsx(angle + (i - 1) * 60f, 64f);
                float y = center.y + Angles.trnsy(angle + (i - 1) * 60f, 64f);
                float edge = Math.min(Math.min(x - 24f, map.width * 8f - 24f - x), Math.min(y - 24f, map.height * 8f - 24f - y));
                score = Math.min(score, edge < 0f ? edge * 100f : 70f + edge * 0.1f);
                for(Vec2 point : occupied) score = Math.min(score, Mathf.dst(x, y, point.x, point.y));
            }
            if(score > best){ best = score; rotation = angle; }
        }
        for(int i = 0; i < 3; i++) positions[i].trns(rotation + (i - 1) * 60f, 64f).add(center);
    }

    private static String distractor(String correct, String other, Rand random){
        String plain = plain(correct), excluded = other == null ? "" : plain(other), best = null;
        float score = Float.MAX_VALUE;
        // Prefer similar real syllables, mixing initial/final confusions with tone mistakes.
        for(PinyinQuestions.Question candidate : PinyinQuestions.all){
            String value = plain(candidate.pinyin);
            if(value.equals(plain) || value.equals(excluded)) continue;
            float distance = Strings.levenshtein(plain, value) * 2f + random.random(2.8f);
            if(distance < score){ score = distance; best = candidate.pinyin; }
        }
        return best;
    }

    private static String plain(String value){ return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", ""); }

    /** Swept collision also catches a crate crossed during a dash. No collection while falling. */
    public int collect(float fromX, float fromY, float toX, float toY){
        if(consumed || age < landingTime) return -1;
        float dx = toX - fromX, dy = toY - fromY, length2 = dx * dx + dy * dy;
        int first = -1;
        float earliest = Float.MAX_VALUE;
        for(int i = 0; i < positions.length; i++){
            Vec2 point = positions[i];
            float t = length2 == 0f ? 0f : Mathf.clamp(((point.x - fromX) * dx + (point.y - fromY) * dy) / length2);
            if(Mathf.dst2(point.x, point.y, fromX + dx * t, fromY + dy * t) <= pickupRadius * pickupRadius && t < earliest){
                first = i; earliest = t;
            }
        }
        if(first >= 0) consumed = true;
        return first;
    }

    public boolean correct(int choice){ return choices[choice].equals(question.pinyin); }
}
