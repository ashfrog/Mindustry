package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.units.*;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.maps.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/** A real Mindustry map that turns exploration and pursuit into pinyin practice. */
public class PinyinEscapeScene{
    private static final int WIDTH = 96, HEIGHT = 64;
    private static final int ENERGY_NEEDED = 3;
    private static final int[][] CHESTS = {
        {12, 52}, {24, 42}, {36, 54}, {48, 44}, {60, 54}, {78, 46},
        {18, 28}, {34, 32}, {52, 28}, {68, 30}, {82, 22}, {42, 12}
    };
    private static final ChestQuestion[] QUESTION_BANK = {
        new ChestQuestion("爸", "bā", "声母 b", "bá", "bǎ"),
        new ChestQuestion("坡", "pō", "声母 p", "pó", "pǒ"),
        new ChestQuestion("妈", "mā", "声母 m", "má", "mǎ"),
        new ChestQuestion("花", "huā", "声母 h", "huá", "huǎ"),
        new ChestQuestion("大", "dà", "声母 d", "dá", "dǎ"),
        new ChestQuestion("可", "kě", "声母 k", "ké", "kē"),
        new ChestQuestion("牛", "niú", "声母 n", "niǔ", "niù"),
        new ChestQuestion("来", "lái", "声母 l", "lāi", "lǎi"),
        new ChestQuestion("爱", "ài", "韵母 ai", "āi", "ǎi"),
        new ChestQuestion("杯", "bēi", "韵母 ei", "béi", "běi"),
        new ChestQuestion("水", "shuǐ", "韵母 ui", "shuī", "shuì"),
        new ChestQuestion("猫", "māo", "韵母 ao", "máo", "mǎo"),
        new ChestQuestion("球", "qiú", "韵母 iu", "qiǔ", "qiù"),
        new ChestQuestion("月", "yuè", "韵母 üe", "yuě", "yuē"),
        new ChestQuestion("书", "shū", "拼读 sh", "shú", "shǔ"),
        new ChestQuestion("鱼", "yú", "拼读 y", "yǔ", "yù"),
        new ChestQuestion("飞", "fēi", "拼读 f", "féi", "fěi"),
        new ChestQuestion("兔", "tù", "拼读 t", "tǔ", "tū"),
        new ChestQuestion("鸟", "niǎo", "拼读 n", "niào", "niāo"),
        new ChestQuestion("光", "guāng", "拼读 gu", "guǎng", "guǎn"),
        new ChestQuestion("早", "zǎo", "声母 z", "zāo", "zào"),
        new ChestQuestion("吃", "chī", "声母 ch", "chí", "chǐ"),
        new ChestQuestion("热", "rè", "声母 r", "rě", "rē"),
        new ChestQuestion("星", "xīng", "声母 x", "xíng", "xǐng")
    };

    private final Map map = new Map(StringMap.of(
        "name", "pinyin-escape-maze",
        "description", "Pinyin Escape Maze"
    ));
    private final boolean[] collected = new boolean[CHESTS.length];
    private final ChestQuestion[] runQuestions = new ChestQuestion[CHESTS.length];
    private final Table hud = new Table();
    private final Seq<Unit> pursuers = new Seq<>();
    private Texture portraitTexture;
    private TextureRegion portraitRegion;
    private Unit partner;
    private UnitType combatUnitType;
    private Weapon combatWeapon;
    private BulletType combatBullet;
    private float baseBulletDamage, baseBulletSpeed, baseBulletWidth, baseBulletHeight, baseWeaponReload, baseWeaponInaccuracy;
    private int baseShots;
    private int combatPower;
    private boolean active, worldReady, chestDialogOpen, countered;
    private int energy, spawnWave;
    private float spawnTimer;

    public PinyinEscapeScene(){
        world.addMapLoader(map, this::generateMap);
        Events.on(WorldLoadEvent.class, event -> {
            if(active && state.map == map){
                worldReady = true;
                pursuers.clear();
                chestDialogOpen = false;
                countered = false;
                combatPower = 0;
                spawnWave = 0;
                spawnTimer = 60f * 12f;
                // Custom maps do not always trigger the normal campaign spawn handshake.
                // Request the player unit explicitly so the escape loop is immediately playable.
                if(player != null && player.unit() == null) player.checkSpawn();
                randomizeQuestions();
                spawnPartner();
                // Map loading rebuilds the scene tree; attach the adventure HUD again afterwards.
                ui.hudGroup.addChild(hud);
                hud.toFront();
            }
        });
        Events.on(StateChangeEvent.class, event -> {
            // playMap briefly touches the menu state while loading; only stop after
            // this scene has actually finished loading and is running in-game.
            if(event.to == GameState.State.menu && active && worldReady){
                stop();
            }
        });
        Events.run(Trigger.update, this::update);
        Events.run(Trigger.draw, this::drawPartnerPortrait);

        hud.setFillParent(true);
        hud.touchable = Touchable.childrenOnly;
        hud.visible(() -> active && state.isGame());
        ui.hudGroup.addChild(hud);
        try{
            portraitTexture = new Texture("sprites/pinyin-player.png");
            portraitRegion = new TextureRegion(portraitTexture);
        }catch(Throwable error){
            Log.warn("Unable to load pinyin player portrait", error);
        }
        buildHud();
    }

    public void start(){
        active = true;
        worldReady = false;
        energy = 0;
        pursuers.clear();
        partner = null;
        chestDialogOpen = false;
        countered = false;
        combatPower = 0;
        spawnWave = 0;
        spawnTimer = 60f * 12f;
        for(int i = 0; i < collected.length; i++) collected[i] = false;
        randomizeQuestions();

        ui.pinyin.hide();

        Rules rules = new Rules();
        rules.defaultTeam = Team.sharded;
        rules.waveTeam = Team.crux;
        rules.waves = false;
        rules.attackMode = false;
        rules.pvp = false;
        rules.fog = true;
        rules.staticFog = true;
        rules.lighting = true;
        rules.ambientLight.set(0.015f, 0.01f, 0.03f, 0.98f);
        rules.env = Env.terrestrial;
        rules.planet = Planets.serpulo;
        control.playMap(map, rules);
    }

    private void stop(){
        active = false;
        worldReady = false;
        pursuers.clear();
        chestDialogOpen = false;
        if(partner != null){
            partner.kill();
            partner = null;
        }
        restoreCombatStats();
    }

    private void buildHud(){
        hud.clearChildren();
        hud.top().left();
        hud.table(Styles.black6, info -> {
            info.defaults().left().pad(5f);
            info.table(avatar -> {
                avatar.image(Icon.play).size(42f).padRight(6f);
                avatar.add("@pinyin.world.player1").color(Color.white).row();
                if(portraitRegion != null){
                    avatar.image(portraitRegion).size(42f).padRight(6f);
                    avatar.add("@pinyin.world.player2").color(Color.white);
                }
            }).row();
            info.label(() -> Core.bundle.format("pinyin.world.status", energy, ENERGY_NEEDED, spawnWave, pursuerDistance())).color(Pal.accent).row();
            info.label(() -> Core.bundle.format("pinyin.world.power", combatPower)).color(combatPower < 0 ? Pal.remove : Pal.heal).row();
            info.add("@pinyin.world.help").color(Color.lightGray).wrap().width(330f).row();
            info.button("@pinyin.world.counter", Icon.commandAttack, this::counterattack)
                .visible(() -> active && energy >= ENERGY_NEEDED && !countered && !pursuers.isEmpty())
                .height(48f).fillX().row();
        }).pad(10f);
    }

    private void generateMap(){
        state.map = map;
        world.loadGenerator(WIDTH, HEIGHT, tiles -> {
            tiles.fill();
            tiles.eachTile(tile -> {
                tile.setFloor((Floor)Blocks.grass);
                if(tile.x == 0 || tile.y == 0 || tile.x == WIDTH - 1 || tile.y == HEIGHT - 1){
                    tile.setBlock(Blocks.stoneWall, Team.derelict, 0);
                }
            });

            // Player spawn and a bright exit core.
            tiles.getn(6, 32).setBlock(Blocks.coreShard, Team.sharded, 0);
            tiles.getn(WIDTH - 7, 6).setBlock(Blocks.coreShard, Team.sharded, 0);

            for(int i = 0; i < CHESTS.length; i++){
                int x = CHESTS[i][0], y = CHESTS[i][1];
                tiles.getn(x, y).setBlock(Blocks.vault, Team.derelict, 0);
                tiles.getn(x + 3, y).setBlock(Blocks.message, Team.derelict, 0);
                if(tiles.getn(x + 3, y).build instanceof MessageBlock.MessageBuild message){
                    message.message.setLength(0);
                    message.message.append("Pinyin chest ").append(questionFor(i).pinyin);
                }
            }

            // Random alternating walls create a new maze layout on every run.
            // Each wall keeps a one-to-three tile opening so the route remains solvable.
            for(int x = 4; x < WIDTH - 4; x += 6){
                int gap = Mathf.random(3, HEIGHT - 4);
                for(int y = 2; y < HEIGHT - 2; y++){
                    if(Math.abs(y - gap) <= 1 || protectedTile(x, y)) continue;
                    if(Mathf.chance(0.82f)) tiles.getn(x, y).setBlock(Blocks.stoneWall, Team.derelict, 0);
                }
            }
            for(int y = 5; y < HEIGHT - 5; y += 6){
                int gap = Mathf.random(3, WIDTH - 4);
                for(int x = 2; x < WIDTH - 2; x++){
                    if(Math.abs(x - gap) <= 1 || protectedTile(x, y)) continue;
                    if(Mathf.chance(0.78f)) tiles.getn(x, y).setBlock(Blocks.stoneWall, Team.derelict, 0);
                }
            }
        });
    }

    private void randomizeQuestions(){
        Seq<ChestQuestion> pool = new Seq<>();
        pool.addAll(QUESTION_BANK);
        pool.shuffle();
        for(int i = 0; i < runQuestions.length; i++) runQuestions[i] = pool.get(i % pool.size);
    }

    private ChestQuestion questionFor(int index){
        return runQuestions[index] == null ? QUESTION_BANK[index % QUESTION_BANK.length] : runQuestions[index];
    }

    private void spawnPartner(){
        if(!active || player == null || player.unit() == null || partner != null) return;
        partner = UnitTypes.alpha.spawn(player.team(), 10 * tilesize, 32 * tilesize);
        partner.controller(new PartnerController());
        partner.aim(partner.x + 1f, partner.y, true);
    }

    private void drawPartnerPortrait(){
        if(!active || !state.isGame() || partner == null || !partner.isAdded() || portraitRegion == null) return;
        Draw.z(Layer.flyingUnit + 1f);
        Draw.color(Color.white);
        float size = Math.max(partner.hitSize * 1.35f, 12f);
        Draw.rect(portraitRegion, partner.x, partner.y, size, size, partner.rotation - 90f);
        Draw.reset();
    }

    public boolean isActive(){
        return active && worldReady && state.isGame();
    }

    private boolean controlsEnabled(){
        return isActive() && state.isPlaying() && !chestDialogOpen
            && !Core.scene.hasDialog() && !Core.scene.hasKeyboard()
            && !ui.chatfrag.shown() && !ui.consolefrag.shown();
    }

    public void updateDesktopControls(){
        Unit unit = player == null ? null : player.unit();
        if(unit == null || player.dead()) return;
        captureCombatStats(unit);
        PinyinPlayerInput.update(unit, false, nearestPursuer(unit), controlsEnabled());
        player.shooting = unit.isShooting;
        player.boosting = false;
        player.mouseX = unit.aimX;
        player.mouseY = unit.aimY;
        if(!controlsEnabled() && partner != null) partner.controlWeapons(false, false);
        if(partner != null && partner.isValid()){
            Core.camera.position.lerpDelta((unit.x + partner.x) / 2f, (unit.y + partner.y) / 2f, 0.08f);
        }else{
            Core.camera.position.lerpDelta(unit, 0.08f);
        }
    }

    private void captureCombatStats(Unit unit){
        if(unit.type == combatUnitType && combatWeapon != null) return;
        restoreCombatStats();
        if(unit.type.weapons.isEmpty()) return;
        combatUnitType = unit.type;
        combatWeapon = unit.type.weapons.first();
        combatBullet = combatWeapon.bullet;
        baseBulletDamage = combatBullet.damage;
        baseBulletSpeed = combatBullet.speed;
        baseBulletWidth = combatBullet instanceof BasicBulletType basic ? basic.width : 0f;
        baseBulletHeight = combatBullet instanceof BasicBulletType basic ? basic.height : 0f;
        baseWeaponReload = combatWeapon.reload;
        baseWeaponInaccuracy = combatWeapon.inaccuracy;
        baseShots = combatWeapon.shoot.shots;
        refreshCombatStats();
    }

    private void refreshCombatStats(){
        if(combatWeapon == null || combatBullet == null) return;
        int level = combatPower;
        float damageScale = Math.max(0.35f, 1f + level * 0.3f);
        float reloadScale = level >= 0 ? 1f / (1f + level * 0.12f) : 1f + (-level * 0.2f);
        combatBullet.damage = baseBulletDamage * damageScale;
        combatBullet.speed = baseBulletSpeed * Math.max(0.7f, 1f + level * 0.08f);
        combatWeapon.reload = baseWeaponReload * reloadScale;
        combatWeapon.inaccuracy = baseWeaponInaccuracy + Math.max(0, level - 2) * 3f;
        combatWeapon.shoot.shots = baseShots + Math.max(0, level - 1);
        if(combatBullet instanceof BasicBulletType basic){
            float widthScale = Math.max(0.55f, 1f + level * 0.22f);
            basic.width = baseBulletWidth * widthScale;
            basic.height = baseBulletHeight * widthScale;
        }
    }

    private void restoreCombatStats(){
        if(combatWeapon != null && combatBullet != null){
            combatBullet.damage = baseBulletDamage;
            combatBullet.speed = baseBulletSpeed;
            if(combatBullet instanceof BasicBulletType basic){
                basic.width = baseBulletWidth;
                basic.height = baseBulletHeight;
            }
            combatWeapon.reload = baseWeaponReload;
            combatWeapon.inaccuracy = baseWeaponInaccuracy;
            combatWeapon.shoot.shots = baseShots;
        }
        combatUnitType = null;
        combatWeapon = null;
        combatBullet = null;
    }

    private String combatBoostText(){
        return combatPower >= 4 ? Core.bundle.get("pinyin.world.boost.scatter")
            : combatPower >= 3 ? Core.bundle.get("pinyin.world.boost.dense")
            : combatPower >= 2 ? Core.bundle.get("pinyin.world.boost.thick")
            : Core.bundle.get("pinyin.world.boost.damage");
    }

    private String combatWeaknessText(){
        return combatPower <= -3 ? Core.bundle.get("pinyin.world.weak.broken")
            : combatPower <= -2 ? Core.bundle.get("pinyin.world.weak.slow")
            : Core.bundle.get("pinyin.world.weak.power");
    }

    private boolean protectedTile(int x, int y){
        if((Math.abs(x - 6) <= 3 && Math.abs(y - 32) <= 3)
            || (Math.abs(x - (WIDTH - 7)) <= 3 && Math.abs(y - 6) <= 3)) return true;
        for(int[] chest : CHESTS){
            if(Math.abs(x - chest[0]) <= 2 && Math.abs(y - chest[1]) <= 2) return true;
        }
        return false;
    }

    private void update(){
        if(!active || !worldReady || !state.isPlaying() || player == null) return;
        if(player.unit() == null){
            player.checkSpawn();
            return;
        }
        if(partner == null) spawnPartner();

        pursuers.removeAll(unit -> unit == null || unit.dead() || !unit.isAdded());
        spawnTimer -= Time.delta;
        if(spawnTimer <= 0f) spawnMonster();

        Unit playerUnit = player.unit();
        if(!chestDialogOpen){
            for(int i = 0; i < CHESTS.length; i++){
                if(!collected[i] && playerUnit.within(CHESTS[i][0] * tilesize, CHESTS[i][1] * tilesize, tilesize * 2.2f)){
                    openChest(i);
                    break;
                }
            }
        }

        Unit nearest = nearestPursuer(playerUnit);
        if(nearest != null && playerUnit.within(nearest, tilesize * 1.4f)){
            caught();
        }

        if(countered && playerUnit.within((WIDTH - 7) * tilesize, 6 * tilesize, tilesize * 3f)){
            escaped();
        }
    }

    private void openChest(int index){
        chestDialogOpen = true;
        state.set(GameState.State.paused);
        ChestQuestion question = questionFor(index);
        // Give the child the pronunciation hint as soon as the choices appear.
        speakPinyin(question.pinyin, question.character);
        BaseDialog dialog = new BaseDialog("@pinyin.world.chest");
        dialog.cont.defaults().growX().pad(7f);
        dialog.cont.image(Icon.box).size(60f).center().row();
        dialog.cont.add(Core.bundle.format("pinyin.world.question", question.character, question.category)).wrap().center().row();
        for(int i = 0; i < question.choices.length; i++){
            int choice = i;
            dialog.cont.button(question.choices[i], Styles.flatTogglet, () -> {
                resolveChest(index, choice);
                dialog.hide();
            }).height(58f).row();
        }
        dialog.hidden(() -> {
            chestDialogOpen = false;
            if(active && state.isPaused()) state.set(GameState.State.playing);
        });
        dialog.closeOnBack();
        dialog.show();
    }

    private void resolveChest(int index, int choice){
        ChestQuestion question = questionFor(index);
        if(choice == 0){
            collected[index] = true;
            energy++;
            combatPower = Math.min(5, combatPower + 1);
            applyBoost();
            pushPursuerBack();
            grantReward();
            Sounds.uiUnlock.play();
            ui.showInfoToast(Core.bundle.format("pinyin.world.correct", question.character, energy, ENERGY_NEEDED, combatBoostText()), 3f);
        }else{
            combatPower = Math.max(-3, combatPower - 1);
            refreshCombatStats();
            if(player.unit() != null){
                player.unit().damage(12f);
                player.unit().apply(StatusEffects.slow, 60f * 4f);
            }
            spawnMonster();
            Sounds.explosion.play();
            ui.showInfoToast(Core.bundle.format("pinyin.world.wrong", question.choices[0], combatWeaknessText()), 3f);
        }
    }

    private void applyBoost(){
        Unit unit = player.unit();
        if(unit == null) return;
        if(energy == 1) unit.apply(StatusEffects.fast, 60f * 20f);
        if(energy == 2) unit.apply(StatusEffects.overclock, 60f * 20f);
        if(energy >= ENERGY_NEEDED) unit.shield += 80f;
        refreshCombatStats();
    }

    private void grantReward(){
        var core = player.team().core();
        if(core != null) core.items.add(Items.copper, 45 + energy * 25);
        if(Mathf.chance(0.35f) && player.unit() != null){
            player.unit().apply(StatusEffects.shielded, 60f * 6f);
        }
    }

    private void spawnMonster(){
        if(!active || player == null || player.unit() == null) return;

        int side = Mathf.random(3);
        int x = side == 0 ? 3 : side == 1 ? WIDTH - 4 : Mathf.random(3, WIDTH - 4);
        int y = side == 2 ? 3 : side == 3 ? HEIGHT - 4 : Mathf.random(3, HEIGHT - 4);
        UnitType type = switch(spawnWave % 4){
            case 1 -> UnitTypes.flare;
            case 2 -> UnitTypes.crawler;
            case 3 -> UnitTypes.mace;
            default -> UnitTypes.dagger;
        };
        Unit monster = type.spawn(Team.crux, x * tilesize, y * tilesize);
        monster.controller(new PursuerAI());
        pursuers.add(monster);
        spawnWave++;
        spawnTimer = Math.max(60f * 4f, 60f * (10f - spawnWave * 0.35f));
        Sounds.uiNotify.play();
    }

    private void pushPursuerBack(){
        if(player.unit() == null) return;
        for(Unit pursuer : pursuers){
            if(pursuer == null || pursuer.dead()) continue;
            Vec2 away = Tmp.v1.set(pursuer).sub(player.unit()).nor();
            pursuer.set(pursuer.x + away.x * tilesize * 4f, pursuer.y + away.y * tilesize * 4f);
        }
    }

    private void counterattack(){
        if(energy < ENERGY_NEEDED || countered || pursuers.isEmpty()) return;
        countered = true;
        for(Unit pursuer : pursuers){
            if(pursuer != null && !pursuer.dead()) pursuer.kill();
        }
        ui.showInfoToast("@pinyin.world.countered", 3f);
        Sounds.explosion.play();
    }

    private void caught(){
        if(!active) return;
        active = false;
        ui.showInfoToast("@pinyin.world.caught", 3f);
        Core.app.post(() -> state.set(GameState.State.menu));
    }

    private void escaped(){
        if(!active) return;
        active = false;
        ui.showInfoToast(Core.bundle.format("pinyin.world.escaped", energy), 3f);
        Core.app.post(() -> state.set(GameState.State.menu));
    }

    private int pursuerDistance(){
        Unit nearest = nearestPursuer(player == null ? null : player.unit());
        return nearest == null ? 0 : (int)(nearest.dst(player.unit()) / tilesize);
    }

    private Unit nearestPursuer(Unit target){
        if(target == null || pursuers.isEmpty()) return null;
        Unit nearest = null;
        float distance = Float.MAX_VALUE;
        for(Unit pursuer : pursuers){
            if(pursuer == null || pursuer.dead()) continue;
            float current = pursuer.dst(target);
            if(current < distance){
                distance = current;
                nearest = pursuer;
            }
        }
        return nearest;
    }

    private void speakPinyin(String pinyin, String character){
        if(headless) return;
        String text = (character + ", 拼音 " + pinyin).replace("'", "''");
        new Thread(() -> {
            try{
                String script = "Add-Type -AssemblyName System.Speech; "
                    + "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$s.SelectVoice('Microsoft Huihui Desktop'); "
                    + "$s.Speak('" + text + "');";
                new ProcessBuilder("powershell", "-NoProfile", "-Command", script).start();
            }catch(Throwable ignored){
                // Windows TTS is optional; the visual prompt and UI sound remain available.
            }
        }, "pinyin-voice").start();
    }

    private class PartnerController extends AIController{
        @Override
        public void updateTargeting(){
            // Player 2 aims and fires from the mouse button instead of auto-targeting.
        }

        @Override
        public void updateMovement(){
            if(unit == null) return;
            PinyinPlayerInput.update(unit, true, null, controlsEnabled() && !Core.scene.hasMouse());
        }
    }

    private class PursuerAI extends AIController{
        @Override
        public void updateMovement(){
            if(unit == null || player == null || player.unit() == null || player.unit().dead()) return;
            unit.aimLook(player.unit());
            float chaseSpeed = Math.min(unit.speed() * 0.38f, player.unit().speed() * 0.60f);
            unit.movePref(vec.set(player.unit()).sub(unit).setLength(chaseSpeed));
        }
    }

    private static class ChestQuestion{
        final String character;
        final String pinyin;
        final String category;
        final String[] choices;

        ChestQuestion(String character, String pinyin, String category, String wrong1, String wrong2){
            this.character = character;
            this.pinyin = pinyin;
            this.category = category;
            choices = new String[]{pinyin, wrong1, wrong2};
        }
    }
}
