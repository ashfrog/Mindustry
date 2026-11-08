package mindustry.game;

import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

/** Small vehicle roster used by the optional land/sea/air route switches. */
public class PinyinVehicleSystem{
    public enum Mode{land, sea, air}

    public static UnitType type(Mode mode){
        return switch(mode){
            case land -> UnitTypes.dagger;
            case sea -> UnitTypes.risso;
            case air -> UnitTypes.alpha;
        };
    }

    public static Mode mode(Unit unit){
        if(unit == null) return Mode.land;
        if(unit.type.naval) return Mode.sea;
        return unit.isFlying() ? Mode.air : Mode.land;
    }

    public static boolean canEnter(Mode mode, Tile tile){
        if(tile == null) return false;
        if(mode == Mode.air) return true;
        boolean liquid = tile.floor() != null && tile.floor().isLiquid;
        return mode == Mode.sea ? liquid : !liquid && !tile.solid();
    }

    public static Unit spawn(Mode mode, Team team, float x, float y){
        return type(mode).spawn(team, x, y);
    }
}
