package mindustry.desktop;

import com.sun.jna.*;
import mindustry.input.*;

/** Read-only Windows gamepad polling. WinMM also supports Betop pads in DirectInput mode. */
public class WindowsPinyinGamepad implements PinyinGamepad.Reader, AutoCloseable{
    private final NativeLibrary xinput, winmm;
    private final Function xstate, joyState, joyCaps;
    private final Memory xbox = new Memory(16), joystick = new Memory(52), caps = new Memory(728);
    private int xboxSlot = -1, joySlot = -1;
    private long rescanAt;
    private final int[] minima = new int[6], maxima = new int[6];
    private boolean hasU;
    private String name;

    public WindowsPinyinGamepad(){
        NativeLibrary found = null;
        for(String library : new String[]{"XInput1_4", "XInput1_3", "XInput9_1_0"}){
            try{ found = NativeLibrary.getInstance(library); break; }
            catch(UnsatisfiedLinkError ignored){ }
        }
        xinput = found;
        xstate = found == null ? null : found.getFunction("XInputGetState", Function.ALT_CONVENTION);
        winmm = NativeLibrary.getInstance("winmm");
        joyState = winmm.getFunction("joyGetPosEx", Function.ALT_CONVENTION);
        joyCaps = winmm.getFunction("joyGetDevCapsW", Function.ALT_CONVENTION);
    }

    @Override
    public boolean poll(PinyinGamepad.State state){
        if(xboxSlot >= 0){
            if(readXbox(xboxSlot, state)) return true;
            xboxSlot = -1;
            rescanAt = 0;
        }
        if(joySlot >= 0){
            if(readJoystick(joySlot, state)) return true;
            joySlot = -1;
            rescanAt = 0;
        }
        if(System.nanoTime() < rescanAt) return false;
        rescanAt = System.nanoTime() + 1_000_000_000L;
        if(xstate != null){
            for(int i = 0; i < 4; i++) if(readXbox(i, state)){ xboxSlot = i; return true; }
        }
        for(int i = 0; i < 16; i++){
            joystick.setInt(0, 52);
            joystick.setInt(4, 255);
            if(joyState.invokeInt(new Object[]{i, joystick}) != 0) continue;
            caps.clear();
            if(joyCaps.invokeInt(new Object[]{new Pointer(i), caps, 728}) != 0) continue;
            for(int axis = 0; axis < 6; axis++){
                int offset = axis < 3 ? 68 + axis * 8 : 104 + (axis - 3) * 8;
                minima[axis] = caps.getInt(offset);
                maxima[axis] = caps.getInt(offset + 4);
            }
            hasU = (caps.getInt(128) & 4) != 0;
            name = caps.getWideString(144).toUpperCase().contains("VID_20BC") ? "BETOP (DirectInput)" : "USB (DirectInput)";
            joySlot = i;
            return readJoystick(i, state);
        }
        return false;
    }

    private boolean readXbox(int slot, PinyinGamepad.State state){
        if(xstate.invokeInt(new Object[]{slot, xbox}) != 0) return false;
        int buttons = Short.toUnsignedInt(xbox.getShort(4));
        state.move.set(signed(xbox.getShort(8)), signed(xbox.getShort(10)));
        state.aim.set(signed(xbox.getShort(12)), signed(xbox.getShort(14)));
        if((buttons & 15) != 0) state.move.set(((buttons & 8) != 0 ? 1 : 0) - ((buttons & 4) != 0 ? 1 : 0),
            ((buttons & 1) != 0 ? 1 : 0) - ((buttons & 2) != 0 ? 1 : 0));
        state.fire = (buttons & (0x1000 | 0x4000 | 0x200)) != 0 || Byte.toUnsignedInt(xbox.getByte(7)) > 40;
        state.dash = (buttons & (0x2000 | 0x100)) != 0;
        state.name = "XInput";
        return true;
    }

    private boolean readJoystick(int slot, PinyinGamepad.State state){
        joystick.setInt(0, 52);
        joystick.setInt(4, 255);
        if(joyState.invokeInt(new Object[]{slot, joystick}) != 0) return false;
        state.move.set(axis(0), -axis(1));
        // Four-axis Betop: Z/R is the right stick. Five-axis Xbox DInput: R/U.
        state.aim.set(axis(hasU ? 3 : 2), -axis(hasU ? 4 : 3));
        int pov = joystick.getInt(40);
        if(pov >= 0 && pov < 36000){
            double angle = Math.toRadians(pov / 100d);
            state.move.set((float)Math.sin(angle), (float)Math.cos(angle));
        }
        int buttons = joystick.getInt(32);
        state.fire = (buttons & (1 | 4 | 32 | 128)) != 0;
        state.dash = (buttons & (2 | 8 | 16)) != 0;
        state.name = name;
        return true;
    }

    private float axis(int axis){
        int range = maxima[axis] - minima[axis];
        return range <= 0 ? 0f : Math.max(-1f, Math.min(1f, (joystick.getInt(8 + axis * 4) - minima[axis]) * 2f / range - 1f));
    }

    private float signed(short value){ return value < 0 ? value / 32768f : value / 32767f; }

    @Override
    public void close(){
        xbox.close();
        joystick.close();
        caps.close();
        if(xinput != null) xinput.close();
        winmm.close();
    }
}
