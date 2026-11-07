import arc.*;
import arc.input.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.input.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinPlayerInputTests{
    Input previousInput;
    TestInput input;
    Unit first, second;

    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
    }

    @BeforeEach
    void setup(){
        previousInput = Core.input;
        Core.input = input = new TestInput();
        Time.delta = 1f;
        first = UnitTypes.alpha.create(Team.sharded);
        second = UnitTypes.alpha.create(Team.sharded);
    }

    @AfterEach
    void restoreInput(){
        Core.input = previousInput;
    }

    void update(boolean enabled){
        PinyinPlayerInput.update(first, false, null, enabled);
        PinyinPlayerInput.update(second, true, null, enabled);
    }

    @Test
    void fireKeysAreIndependentAndReleaseStopsFire(){
        input.pressed.add(KeyCode.j);
        update(true);
        assertTrue(first.isShooting);
        assertFalse(second.isShooting);

        input.pressed.clear();
        input.pressed.add(KeyCode.mouseLeft);
        update(true);
        assertFalse(first.isShooting);
        assertTrue(second.isShooting);

        input.pressed.clear();
        update(true);
        for(Unit unit : new Unit[]{first, second}){
            assertFalse(unit.isShooting);
            for(var mount : unit.mounts) assertFalse(mount.shoot);
        }
    }

    @Test
    void holdingBothFireKeysRepeatsAcrossReloads(){
        input.pressed.addAll(KeyCode.j, KeyCode.mouseLeft);
        for(int tick = 0; tick < 120; tick++){
            update(true);
            for(Unit unit : new Unit[]{first, second}){
                for(var mount : unit.mounts) mount.weapon.update(unit, mount);
            }
        }
        for(Unit unit : new Unit[]{first, second}){
            assertTrue(shots(unit) > 4, "Holding fire must shoot through several reload cycles");
        }

        input.pressed.clear();
        int firstShots = shots(first), secondShots = shots(second);
        for(int tick = 0; tick < 60; tick++){
            update(true);
            for(Unit unit : new Unit[]{first, second}){
                for(var mount : unit.mounts) mount.weapon.update(unit, mount);
            }
        }
        assertEquals(firstShots, shots(first));
        assertEquals(secondShots, shots(second));
    }

    @Test
    void movementKeysDoNotMoveTheOtherPlayer(){
        input.pressed.addAll(KeyCode.w, KeyCode.d);
        update(true);
        assertTrue(first.vel.x > 0f && first.vel.y > 0f);
        assertTrue(second.vel.isZero());
        assertTrue(first.vel.len() <= first.speed());

        first.vel.setZero();
        input.pressed.clear();
        input.pressed.add(KeyCode.left);
        update(true);
        assertTrue(first.vel.isZero());
        assertTrue(second.vel.x < 0f);
    }

    @Test
    void disabledControlsStopFireEvenWhileKeysRemainHeld(){
        input.pressed.addAll(KeyCode.j, KeyCode.mouseLeft);
        update(true);
        update(false);
        assertFalse(first.isShooting);
        assertFalse(second.isShooting);
        for(Unit unit : new Unit[]{first, second}){
            for(var mount : unit.mounts) assertFalse(mount.shoot);
        }
    }

    int shots(Unit unit){
        int total = 0;
        for(var mount : unit.mounts) total += mount.totalShots;
        return total;
    }

    static class TestInput extends MockInput{
        final ObjectSet<KeyCode> pressed = new ObjectSet<>();

        @Override
        public boolean keyDown(KeyCode key){
            return pressed.contains(key);
        }

        @Override
        public float mouseWorldX(){
            return 300f;
        }

        @Override
        public float mouseWorldY(){
            return 0f;
        }
    }
}
