package mindustry.game;

import arc.util.*;
import mindustry.gen.*;
import mindustry.type.*;

/** Local survival boss: a shared damage budget protects against same-frame burst kills. */
public class PinyinSurvivalBoss extends UnitEntity{
    private float damageBudget;

    public static PinyinSurvivalBoss spawn(UnitType type, Team team, float x, float y){
        PinyinSurvivalBoss boss = new PinyinSurvivalBoss();
        boss.team = team;
        boss.setType(type);
        for(var ability : boss.abilities) ability.created(boss);
        boss.elevation = 1f;
        boss.heal();
        boss.set(x, y);
        boss.add();
        return boss;
    }

    /** Call after equipping instance health; one opening burst can remove at most 4%. */
    public void prepareArmor(){ damageBudget = maxHealth * 0.04f; }

    @Override
    public void update(){
        // Sustained damage can remove up to 5%/second, without accumulating idle-time burst credit.
        damageBudget = Math.min(maxHealth * 0.04f, damageBudget + maxHealth * 0.05f * Time.delta / 60f);
        super.update();
    }

    @Override
    public void rawDamage(float amount){
        if(!(amount > 0f)) return;
        float accepted = Math.min(amount, damageBudget);
        damageBudget -= accepted;
        if(accepted > 0f) super.rawDamage(accepted);
    }
}
