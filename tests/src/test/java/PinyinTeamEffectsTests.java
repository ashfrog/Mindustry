import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinTeamEffectsTests{
    @BeforeAll
    static void init(){
        ApplicationTests.launchApplication();
    }

    Unit player(){
        return UnitTypes.alpha.create(Team.sharded);
    }

    @Test
    void bothPlayersAndAllGunsGainPowerWithoutChangingBaseContent(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit first = player(), second = player();
        float baseDamage = UnitTypes.alpha.weapons.first().bullet.damage;
        float baseReload = UnitTypes.alpha.weapons.first().reload;
        int baseShots = UnitTypes.alpha.weapons.first().shoot.shots;
        for(int energy = 1; energy <= 4; energy++) effects.correct(energy, true, first, second);
        for(Unit unit : new Unit[]{first, second}){
            for(var mount : unit.mounts){
                assertTrue(mount.weapon.bullet.damage > baseDamage);
                assertTrue(mount.weapon.reload < baseReload);
                assertTrue(mount.weapon.shoot instanceof ShootSpread);
                assertTrue(mount.weapon.shoot.shots > baseShots);
                assertTrue(((BasicBulletType)mount.weapon.bullet).width > ((BasicBulletType)UnitTypes.alpha.weapons.first().bullet).width);
            }
            assertEquals(160f, unit.shield);
            assertTrue(unit.getDuration(StatusEffects.fast) > 0f);
            assertTrue(unit.getDuration(StatusEffects.overclock) > 0f);
            assertTrue(unit.getDuration(StatusEffects.shielded) > 0f);
        }
        assertEquals(baseDamage, UnitTypes.alpha.weapons.first().bullet.damage);
        assertEquals(baseReload, UnitTypes.alpha.weapons.first().reload);
        assertEquals(baseShots, UnitTypes.alpha.weapons.first().shoot.shots);
        Unit respawn = player();
        effects.inherit(respawn);
        assertEquals(first.mounts[0].weapon.bullet.damage, respawn.mounts[0].weapon.bullet.damage);
        assertNotSame(first.mounts[0].weapon, second.mounts[0].weapon);
    }

    @Test
    void wrongAnswerHurtsSlowsAndWeakensBothPlayers(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit first = player(), second = player();
        float health = first.health;
        effects.wrong(first, second);
        assertEquals(-1, effects.power());
        for(Unit unit : new Unit[]{first, second}){
            assertEquals(health - 12f, unit.health);
            assertEquals(240f, unit.getDuration(StatusEffects.slow));
            for(var mount : unit.mounts){
                assertTrue(mount.weapon.bullet.damage < UnitTypes.alpha.weapons.first().bullet.damage);
                assertTrue(mount.weapon.reload > UnitTypes.alpha.weapons.first().reload);
            }
        }
    }

    @Test
    void RespawnInheritsOnlyRemainingTeamEffectsAndCurrentPower(){
        PinyinTeamEffects effects = new PinyinTeamEffects();
        Unit survivor = player();
        effects.correct(1, true, survivor, null);
        effects.wrong(survivor, null);
        effects.update(300f);
        Unit respawn = player();
        effects.inherit(respawn);
        assertEquals(900f, respawn.getDuration(StatusEffects.fast));
        assertEquals(60f, respawn.getDuration(StatusEffects.shielded));
        assertEquals(0f, respawn.getDuration(StatusEffects.slow));
        assertEquals(survivor.mounts[0].weapon.bullet.damage, respawn.mounts[0].weapon.bullet.damage);
        effects.update(1200f);
        Unit later = player();
        effects.inherit(later);
        assertEquals(0f, later.getDuration(StatusEffects.fast));
        assertEquals(0f, later.getDuration(StatusEffects.shielded));
        effects.reset();
        assertEquals(0, effects.power());
    }
}
