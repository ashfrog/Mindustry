import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinCombatBalanceTests{
    @BeforeAll
    static void init(){ ApplicationTests.launchApplication(); }

    @Test
    void farFutureEnemiesStopEscalatingWhileEarlyChaptersStillGrow(){
        for(var kind : PinyinEnemyController.Kind.values()){
            Unit maximum = PinyinEnemyController.type(kind).create(Team.crux);
            Unit future = PinyinEnemyController.type(kind).create(Team.crux);
            PinyinEnemyController.equip(maximum, kind, 12);
            PinyinEnemyController.equip(future, kind, 100000);
            assertEquals(maximum.maxHealth, future.maxHealth);
            assertEquals(maximum.mounts[0].weapon.bullet.damage, future.mounts[0].weapon.bullet.damage);
            assertEquals(maximum.mounts[0].weapon.reload, future.mounts[0].weapon.reload);
            assertTrue(future.mounts[0].weapon.reload >= 25f);
        }
        assertTrue(PinyinCombatBalance.contactDamage(100000) < 30f);
    }

    @Test
    void eachNativeAmmoFamilyHasABoundedProjectileBudget(){
        for(var kind : PinyinArsenal.Kind.values()){
            PinyinTeamEffects effects = new PinyinTeamEffects();
            Unit player = UnitTypes.alpha.create(Team.sharded);
            for(int i = 0; i < 40; i++) effects.correct(i, false, player, PinyinTeamEffects.Reward.multishot);
            effects.correct(41, false, player, PinyinTeamEffects.Reward.arsenal, kind);
            assertEquals(PinyinCombatBalance.shots(kind, 9), player.mounts[0].weapon.shoot.shots);
            assertTrue(player.shield <= PinyinCombatBalance.maxShield);
        }
    }

    @Test
    void helpersHaveDistinctTypesAndOnlyGunnerAddsBoundedAttackFire(){
        for(var kind : PinyinHelpers.Kind.values()){
            Unit helper = PinyinHelpers.type(kind).create(Team.sharded);
            int nativeGuns = helper.type.weapons.size;
            PinyinHelpers.equip(helper, kind);
            assertEquals(kind == PinyinHelpers.Kind.gunner ? 1 : 0, helper.mounts.length);
            if(helper.mounts.length > 0){
                assertEquals(1, helper.mounts[0].weapon.shoot.shots);
                assertEquals(45f, helper.mounts[0].weapon.reload);
            }
            assertEquals(nativeGuns, helper.type.weapons.size);
        }
    }
}
