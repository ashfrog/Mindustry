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
        PinyinGamepad.reader = null;
        PinyinGamepad.update();
        first = UnitTypes.alpha.create(Team.sharded);
        second = UnitTypes.alpha.create(Team.sharded);
    }

    @AfterEach
    void restoreInput(){
        Core.input = previousInput;
        PinyinGamepad.reader = null;
        PinyinGamepad.update();
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
    void upgradedHeldFireCreatesEveryProjectileForBothPlayers(){
        mindustry.Vars.logic.reset();
        mindustry.Vars.world.loadGenerator(40, 40, tiles -> tiles.fill());
        mindustry.Vars.state.rules.unitCap = 20;
        mindustry.Vars.state.rules.unitCapVariable = false;
        first = UnitTypes.alpha.spawn(Team.sharded, 100, 100);
        second = UnitTypes.alpha.spawn(Team.sharded, 120, 100);
        PinyinTeamEffects effects = new PinyinTeamEffects();
        PinyinTeamEffects other = new PinyinTeamEffects();
        for(int i = 1; i <= 20; i++){
            effects.correct(i, false, first, PinyinTeamEffects.Reward.multishot);
            other.correct(i, false, second, PinyinTeamEffects.Reward.multishot);
        }
        int pellets = PinyinCombatBalance.maxShots;
        assertEquals(pellets, effects.bulletsPerShot());
        input.pressed.addAll(KeyCode.j, KeyCode.mouseLeft);
        int bulletsBefore = Groups.bullet.size();
        for(int tick = 0; tick < 30; tick++){
            update(true);
            for(Unit unit : new Unit[]{first, second}){
                for(var mount : unit.mounts) mount.weapon.update(unit, mount);
            }
        }
        assertTrue(shots(first) > pellets && shots(second) > pellets);
        assertEquals(0, shots(first) % pellets);
        assertEquals(0, shots(second) % pellets);
        assertEquals(shots(first) + shots(second), Groups.bullet.size() - bulletsBefore,
            "Every upgraded pellet must become a real bullet in the world");
        mindustry.Vars.logic.reset();
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
    void survivalMovesEquallyInEveryDirectionAndBothPlayersAutomaticallyFire(){
        Unit target = UnitTypes.flare.create(Team.crux);
        target.set(300f, 300f);
        float speed = -1f;
        for(KeyCode key : new KeyCode[]{KeyCode.w, KeyCode.s, KeyCode.a, KeyCode.d}){
            input.pressed.clear();
            input.pressed.add(key);
            first.vel.setZero();
            first.rotation = 37f;
            for(int tick = 0; tick < 120; tick++) PinyinPlayerInput.updateSurvival(first, false, target, true);
            if(speed < 0f) speed = first.vel.len();
            assertEquals(speed, first.vel.len(), 0.001f, "Aim direction cannot penalize strafing");
            assertTrue(first.isShooting);
            assertEquals(target.x, first.aimX, 0.01f);
        }
        assertEquals(first.type.speed * 2f, speed, 0.001f);
        for(KeyCode key : new KeyCode[]{KeyCode.up, KeyCode.down, KeyCode.left, KeyCode.right}){
            input.pressed.clear();
            input.pressed.add(key);
            second.vel.setZero();
            for(int tick = 0; tick < 120; tick++) PinyinPlayerInput.updateSurvival(second, true, target, true);
            assertEquals(speed, second.vel.len(), 0.001f);
            assertTrue(second.isShooting);
        }
        input.pressed.clear();
        first.vel.setZero();
        first.set(100f, 150f);
        for(int tick = 0; tick < 120; tick++) PinyinPlayerInput.updateSurvival(first, false, target, true);
        assertTrue(first.vel.isZero());
        assertEquals(150f, first.y, "No automatic upward movement");
        PinyinPlayerInput.updateSurvival(first, false, target, false);
        assertFalse(first.isShooting, "Pause must stop automatic fire");
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

    @Test
    void gamepadMovesAimsAndContinuouslyFiresOnlyPlayerTwo(){
        PinyinGamepad.reader = pad -> {
            pad.move.set(1f, 0f);
            pad.aim.set(0f, 1f);
            pad.fire = true;
            return true;
        };
        for(int tick = 0; tick < 120; tick++){
            PinyinGamepad.update();
            update(true);
            for(var mount : second.mounts) mount.weapon.update(second, mount);
        }
        assertTrue(first.vel.isZero());
        assertFalse(first.isShooting);
        assertTrue(second.vel.x > 0f);
        assertEquals(second.x, second.aimX, 0.01f);
        assertTrue(second.aimY > second.y);
        assertTrue(shots(second) > 4);
        PinyinGamepad.reader = pad -> false;
        PinyinGamepad.update();
        second.vel.setZero();
        update(true);
        assertFalse(second.isShooting, "Disconnect must immediately release fire");
        assertTrue(second.vel.isZero());
        input.pressed.addAll(KeyCode.left, KeyCode.mouseLeft);
        update(true);
        assertTrue(second.isShooting, "Keyboard/mouse must still work after unplugging");
        assertTrue(second.vel.x < 0f);
    }

    @Test
    void padDeadzoneDashEdgesAndPausedInputsAreSafe(){
        PinyinGamepad.reader = pad -> {
            pad.move.set(0.1f, -0.1f);
            pad.aim.set(-0.05f, 0.05f);
            pad.dash = true;
            pad.fire = true;
            return true;
        };
        PinyinGamepad.update();
        assertTrue(PinyinGamepad.state.move.isZero());
        assertTrue(PinyinGamepad.state.aim.isZero());
        assertTrue(PinyinGamepad.state.dashTap);
        assertTrue(PinyinGamepad.state.fireTap);
        PinyinGamepad.update();
        assertFalse(PinyinGamepad.state.dashTap, "Holding dash must not retrigger every frame");
        assertFalse(PinyinGamepad.state.fireTap, "Holding mining/fire must not cycle buildings every frame");
        update(false);
        assertFalse(second.isShooting);
        assertTrue(second.vel.isZero());
        PinyinGamepad.reader = pad -> { pad.move.set(0.6f, 0f); return true; };
        PinyinGamepad.update();
        update(true);
        assertTrue(second.vel.len() > 0f && second.vel.len() < second.speed(), "Analog movement must retain partial speed");
    }

    @Test
    void gamepadPrankAndUltimateHaveIndependentEdgesAndReleaseOnDisconnect(){
        PinyinGamepad.reader = pad -> { pad.prank = true; pad.ultimate = true; return true; };
        PinyinGamepad.update();
        assertTrue(PinyinGamepad.state.prankTap);
        assertTrue(PinyinGamepad.state.ultimateTap);
        assertFalse(PinyinGamepad.state.fire);
        assertFalse(PinyinGamepad.state.dash);
        PinyinGamepad.update();
        assertFalse(PinyinGamepad.state.prankTap);
        assertFalse(PinyinGamepad.state.ultimateTap);
        PinyinGamepad.reader = pad -> false;
        PinyinGamepad.update();
        assertFalse(PinyinGamepad.state.prank);
        assertFalse(PinyinGamepad.state.ultimate);
        PinyinGamepad.reader = pad -> { pad.ultimate = true; return true; };
        PinyinGamepad.update();
        assertTrue(PinyinGamepad.state.ultimateTap);
        assertFalse(PinyinGamepad.state.prankTap);
    }

    @Test
    void mouseOverHudCannotFireButControllerStillCan(){
        input.pressed.add(KeyCode.mouseLeft);
        PinyinPlayerInput.update(second, true, null, true, false);
        assertFalse(second.isShooting);
        PinyinGamepad.reader = pad -> { pad.fire = true; return true; };
        PinyinGamepad.update();
        PinyinPlayerInput.update(second, true, null, true, false);
        assertTrue(second.isShooting);
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
