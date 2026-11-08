import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinPlayerSkillsTests{
    PinyinPlayerSkills skills;
    Unit first, second;

    @BeforeAll
    static void init(){ ApplicationTests.launchApplication(); }

    @BeforeEach
    void setup(){
        logic.reset();
        world.loadGenerator(100, 100, tiles -> tiles.fill());
        state.rules.unitCap = 260;
        state.rules.unitCapVariable = false;
        Time.delta = 1f;
        first = UnitTypes.alpha.spawn(Team.sharded, 300f, 300f);
        second = UnitTypes.alpha.spawn(Team.sharded, 400f, 300f);
        skills = new PinyinPlayerSkills();
    }

    @Test
    void ultimateLastsTenSecondsAndCanOnlyRestartAtThirtySeconds(){
        assertTrue(skills.activateUltimate(0, first));
        assertFalse(skills.activateUltimate(0, first));
        skills.update(599f, first, second);
        first.update();
        second.update();
        assertEquals(1f, skills.ultimateTime(0));
        assertEquals(1.6f, first.speedMultiplier, 0.001f);
        assertEquals(2.2f, first.reloadMultiplier, 0.001f);
        assertEquals(2f, first.damageMultiplier, 0.001f);
        assertEquals(1f, second.damageMultiplier, 0.001f);
        skills.update(1f, first, second);
        first.update();
        assertEquals(0f, skills.ultimateTime(0));
        assertEquals(1f, first.speedMultiplier, 0.001f);
        assertEquals(1f, first.damageMultiplier, 0.001f);
        assertEquals(1f, first.reloadMultiplier, 0.001f);
        skills.update(1199f, first, second);
        assertFalse(skills.activateUltimate(0, first));
        skills.update(1f, first, second);
        assertTrue(skills.activateUltimate(0, first));
    }

    @Test
    void ultimateDoesNotCompoundOrEraseAnswerGrowthAndDeathKeepsCooldown(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        effects.enableEndlessGrowth();
        assertTrue(skills.activateUltimate(0, first));
        for(int tick = 0; tick < 100; tick++){
            effects.correct(0, false, first, PinyinTeamEffects.Reward.rapid);
            skills.update(1f, first, second);
            first.update();
            assertEquals(2f, first.damageMultiplier, 0.001f);
            assertEquals(2.2f, first.reloadMultiplier, 0.001f);
        }
        float upgraded = first.mounts[0].weapon.bullet.damage;
        first.dead = true;
        skills.update(1f, first, second);
        assertEquals(0f, skills.ultimateTime(0));
        first = UnitTypes.alpha.spawn(Team.sharded, 300f, 300f);
        effects.inherit(first);
        skills.update(1f, first, second);
        assertFalse(skills.activateUltimate(0, first));
        assertEquals(upgraded, first.mounts[0].weapon.bullet.damage);
        assertFalse(first.hasEffect(StatusEffects.dynamic));
        skills.reset();
        assertEquals(0f, skills.cooldown(0));
    }

    @Test
    void prankPullsSlowsOnlyVictimAndBoostsInitiatorWithRangeAndCooldown(){
        assertTrue(skills.activatePrank(0, first, second));
        assertFalse(skills.activatePrank(0, first, second));
        skills.update(1f, first, second);
        assertTrue(second.x < 400f && second.x > 398f);
        first.update();
        second.update();
        assertEquals(1.35f, first.speedMultiplier, 0.001f);
        assertEquals(0.65f, second.speedMultiplier, 0.001f);
        assertTrue(skills.activatePrank(1, second, first), "Both players may prank each other");
        skills.update(1f, first, second);
        first.update();
        second.update();
        assertEquals(1.35f * 0.65f, first.speedMultiplier, 0.001f);
        assertEquals(first.speedMultiplier, second.speedMultiplier, 0.001f);
        skills.update(180f, first, second);
        first.update();
        assertFalse(skills.pulling(0));
        assertFalse(skills.pulling(1));
        assertEquals(1f, first.speedMultiplier, 0.001f);
        assertFalse(skills.activatePrank(0, first, second));
        skills.update(180f, first, second);
        second.set(750f, 750f);
        assertFalse(skills.activatePrank(0, first, second));
        second.set(first.x + 100f, first.y);
        assertTrue(skills.activatePrank(0, first, second));
        second.set(750f, 750f);
        skills.update(1f, first, second);
        assertFalse(skills.pulling(0), "Escaping range breaks the tether");
        assertFalse(first.hasEffect(StatusEffects.dynamic));
    }
}
