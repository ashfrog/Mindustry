import mindustry.game.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinCoopLivesTests{
    @Test
    void deathCostsOneLifeAndNeedsFiveFullSeconds(){
        PinyinCoopLives lives = new PinyinCoopLives();
        lives.update(false, true, 1f);
        assertEquals(4, lives.remaining());
        assertEquals(5, lives.secondsLeft(0));
        for(int tick = 0; tick < 299; tick++) lives.update(false, true, 1f);
        assertFalse(lives.ready(0));
        assertEquals(4, lives.remaining(), "Dead frames must not consume more lives");
        lives.update(false, true, 1f);
        assertTrue(lives.ready(0));
        lives.revived(0);
        lives.update(true, true, 1f);
        assertEquals(4, lives.remaining());
        assertEquals(0, lives.secondsLeft(0));
    }

    @Test
    void bothDeathsEndImmediatelyEvenWithLivesLeft(){
        PinyinCoopLives lives = new PinyinCoopLives();
        lives.update(false, true, 1f);
        lives.update(false, false, 120f);
        assertEquals(3, lives.remaining());
        assertTrue(lives.ended());
        assertFalse(lives.ready(0));
        assertFalse(lives.ready(1));
    }

    @Test
    void fifthDeathEndsEvenWhenTeammateSurvives(){
        PinyinCoopLives lives = new PinyinCoopLives();
        for(int death = 1; death <= 5; death++){
            int index = death % 2;
            lives.update(index != 0, index != 1, 1f);
            assertEquals(5 - death, lives.remaining());
            assertEquals(death == 5, lives.ended());
            lives.update(index != 0, index != 1, 300f);
            if(death < 5){
                assertTrue(lives.ready(index));
                lives.revived(index);
                lives.update(true, true, 1f);
            }else{
                assertFalse(lives.ready(index));
            }
        }
    }

    @Test
    void pausedTimeAndResetDoNotLeakIntoNextRound(){
        PinyinCoopLives lives = new PinyinCoopLives();
        lives.update(true, false, 1f);
        for(int tick = 0; tick < 600; tick++) lives.update(true, false, 0f);
        assertFalse(lives.ready(1));
        assertEquals(5, lives.secondsLeft(1));
        lives.reset();
        assertEquals(5, lives.remaining());
        assertFalse(lives.ended());
        lives.update(true, true, 300f);
        assertFalse(lives.ready(1));
        assertEquals(0, lives.secondsLeft(1));
    }
}
