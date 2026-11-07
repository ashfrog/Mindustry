import mindustry.game.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinPlayerMomentumTests{
    @Test
    void onlyTheAnsweringPlayerEarnsStreakRewardsEveryThreeCorrectAnswers(){
        PinyinPlayerMomentum first = new PinyinPlayerMomentum(), second = new PinyinPlayerMomentum();
        for(int i = 1; i <= 9; i++) assertEquals(i % 3 == 0, first.correct());
        assertEquals(9, first.streak());
        assertEquals(0, second.streak());
        first.wrong();
        assertEquals(0, first.streak());
        assertFalse(first.correct());
        assertFalse(first.correct());
        assertTrue(first.correct());
    }

    @Test
    void dashesCannotBeRepeatedUntilCooldownAndPauseFreezesBothTimers(){
        PinyinPlayerMomentum first = new PinyinPlayerMomentum(), second = new PinyinPlayerMomentum();
        assertTrue(first.dash());
        assertTrue(first.dashing());
        assertFalse(first.dash());
        assertTrue(second.dash(), "The other player has an independent dash");
        first.update(0f);
        assertEquals(4f, first.dashSeconds());
        assertTrue(first.dashing());
        first.update(PinyinPlayerMomentum.dashDuration);
        assertFalse(first.dashing());
        assertFalse(first.dash());
        first.update(PinyinPlayerMomentum.dashRecharge - PinyinPlayerMomentum.dashDuration);
        assertTrue(first.dash());
        first.reset();
        assertFalse(first.dashing());
        assertEquals(0, first.streak());
        assertTrue(first.dash());
    }
}
