import mindustry.game.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinAdventureEventsTests{
    @Test
    void huntAndRhythmEventsNeedTheirOwnActions(){
        PinyinAdventureEvents events = new PinyinAdventureEvents();
        events.start(PinyinAdventureEvents.Kind.hunt, 10);
        for(int i = 0; i < events.target() - 1; i++) events.defeated();
        assertTrue(events.active());
        events.defeated();
        assertTrue(events.consumeCompleted());
        assertFalse(events.active());

        events.start(PinyinAdventureEvents.Kind.rhythm, 1);
        events.answered();
        assertTrue(events.active());
        events.answered();
        events.answered();
        assertTrue(events.consumeCompleted());
    }

    @Test
    void rescueProgressNeedsEightSecondsAndTimeoutFails(){
        PinyinAdventureEvents events = new PinyinAdventureEvents();
        events.start(PinyinAdventureEvents.Kind.rescue, 1);
        events.update(60f * 4f, true);
        assertTrue(events.active());
        events.update(60f * 4f, true);
        assertTrue(events.consumeCompleted());

        events.start(PinyinAdventureEvents.Kind.survive, 1);
        events.update(PinyinAdventureEvents.duration + 100f, false);
        assertFalse(events.active());
        assertTrue(events.consumeCompleted() || events.failed());
    }
}
