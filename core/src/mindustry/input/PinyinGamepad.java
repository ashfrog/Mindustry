package mindustry.input;

import arc.math.geom.*;

/** The desktop backend feeds one controller exclusively to local player 2. */
public class PinyinGamepad{
    public static final State state = new State();
    public static Reader reader;
    public static final float deadzone = 0.22f;

    public interface Reader{
        boolean poll(State state);
    }

    public static void update(){
        boolean wasDash = state.dash;
        state.clear();
        state.connected = reader != null && reader.poll(state);
        if(!state.connected){ state.clear(); return; }
        applyDeadzone(state.move);
        applyDeadzone(state.aim);
        state.dashTap = state.dash && !wasDash;
    }

    private static void applyDeadzone(Vec2 axis){
        float length = axis.len();
        if(length <= deadzone) axis.setZero();
        else axis.scl((Math.min(1f, length) - deadzone) / (1f - deadzone) / length);
    }

    public static class State{
        public final Vec2 move = new Vec2(), aim = new Vec2();
        public boolean connected, fire, dash, dashTap;
        public String name = "";

        public void clear(){
            move.setZero();
            aim.setZero();
            fire = dash = dashTap = false;
        }
    }
}
