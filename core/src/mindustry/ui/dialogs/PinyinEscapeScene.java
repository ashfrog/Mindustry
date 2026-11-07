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
    private Unit firstPlayer, partner;
    private final PinyinCoopLives lives = new PinyinCoopLives();
    private final PinyinTeamEffects teamEffects = new PinyinTeamEffects();
    private final float[] reviveProtection = new float[2];
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
                lives.reset();
                teamEffects.reset();
                reviveProtection[0] = reviveProtection[1] = 0f;
                spawnWave = 0;
                spawnTimer = 60f * 12f;
                randomizeQuestions();
                firstPlayer = spawnPlayer(false, 9 * tilesize, 32 * tilesize);
                partner = spawnPlayer(true, 11 * tilesize, 32 * tilesize);
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
        firstPlayer = partner = null;
        chestDialogOpen = false;
        countered = false;
        lives.reset();
        teamEffects.reset();
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
        rules.canGameOver = false;
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
        if(alive(firstPlayer)) firstPlayer.remove();
        if(alive(partner)) partner.remove();
        firstPlayer = partner = null;
        teamEffects.reset();
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
            info.label(() -> Core.bundle.format("pinyin.world.power", teamEffects.power())).color(Pal.heal).row();
            info.label(() -> Core.bundle.format("pinyin.world.lives", lives.remaining(), PinyinCoopLives.startingLives)).color(Pal.accent).row();
            info.label(() -> respawnText(0)).color(Pal.remove).visible(() -> !alive(firstPlayer)).row();
            info.label(() -> respawnText(1)).color(Pal.remove).visible(() -> !alive(partner)).row();
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

    private Unit spawnPlayer(boolean second, float x, float y){
        Unit unit = UnitTypes.alpha.spawn(player.team(), x, y);
        if(second){
            unit.controller(new PartnerController());
        }else{
            player.unit(unit);
            player.set(unit);
            player.shooting = false;
        }
        unit.aim(x + 1f, y, true);
        teamEffects.inherit(unit);
        return unit;
    }

    private boolean alive(Unit unit){
        return unit != null && !unit.dead && unit.isAdded();
    }

    private String respawnText(int index){
        return Core.bundle.format("pinyin.world.respawning", index + 1, lives.secondsLeft(index));
    }

    private void revive(int index){
        Unit survivor = index == 0 ? partner : firstPlayer;
        Tile destination = null;
        // Search a close, clear tile; keep respawns away from walls, chests and pursuers.
        for(int radius = 1; radius <= 5 && destination == null; radius++){
            for(int dx = -radius; dx <= radius && destination == null; dx++){
                for(int dy = -radius; dy <= radius; dy++){
                    if(Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    Tile tile = world.tile(survivor.tileX() + dx, survivor.tileY() + dy);
                    if(tile == null || tile.x < 2 || tile.y < 2 || tile.x >= WIDTH - 2 || tile.y >= HEIGHT - 2 || tile.solid()) continue;
                    boolean safe = true;
                    for(Unit pursuer : pursuers){
                        if(alive(pursuer) && pursuer.within(tile.worldx(), tile.worldy(), tilesize * 2f)){
                            safe = false;
                            break;
                        }
                    }
                    if(safe){
                        destination = tile;
                        break;
                    }
                }
            }
        }
        Unit unit = spawnPlayer(index == 1, destination == null ? survivor.x : destination.worldx(),
            destination == null ? survivor.y : destination.worldy());
        if(index == 0) firstPlayer = unit;
        else partner = unit;
        lives.revived(index);
        reviveProtection[index] = 60f * 2f;
        unit.apply(StatusEffects.invincible, reviveProtection[index]);
        Fx.spawn.at(unit);
        ui.showInfoToast(Core.bundle.format("pinyin.world.revived", index + 1), 2f);
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
        if(alive(firstPlayer)){
            PinyinPlayerInput.update(firstPlayer, false, nearestPursuer(firstPlayer), controlsEnabled());
            player.shooting = firstPlayer.isShooting;
            player.boosting = false;
            player.mouseX = firstPlayer.aimX;
            player.mouseY = firstPlayer.aimY;
        }else{
            player.shooting = false;
        }
        if(!controlsEnabled() && alive(partner)) partner.controlWeapons(false, false);
        if(alive(firstPlayer) && alive(partner)){
            Core.camera.position.lerpDelta((firstPlayer.x + partner.x) / 2f, (firstPlayer.y + partner.y) / 2f, 0.08f);
        }else if(alive(firstPlayer) || alive(partner)){
            Core.camera.position.lerpDelta(alive(firstPlayer) ? firstPlayer : partner, 0.08f);
        }
    }

    private String combatBoostText(){
        return teamEffects.power() >= 4 ? Core.bundle.get("pinyin.world.boost.scatter")
            : teamEffects.power() >= 3 ? Core.bundle.get("pinyin.world.boost.dense")
            : teamEffects.power() >= 2 ? Core.bundle.get("pinyin.world.boost.thick")
            : Core.bundle.get("pinyin.world.boost.damage");
    }

    private String combatWeaknessText(){
        return teamEffects.power() <= -3 ? Core.bundle.get("pinyin.world.weak.broken")
            : teamEffects.power() <= -2 ? Core.bundle.get("pinyin.world.weak.slow")
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
        teamEffects.update(Time.delta);
        for(int i = 0; i < 2; i++){
            reviveProtection[i] = Math.max(0f, reviveProtection[i] - Time.delta);
            Unit unit = i == 0 ? firstPlayer : partner;
            Unit nearest = alive(unit) ? nearestPursuer(unit) : null;
            if(nearest != null && reviveProtection[i] <= 0f && unit.within(nearest, tilesize * 1.4f)){
                unit.kill();
            }
        }
        int before = lives.remaining();
        lives.update(alive(firstPlayer), alive(partner), Time.delta);
        if(lives.ended()){
            finish(Core.bundle.get(lives.remaining() == 0 ? "pinyin.world.noLives" : "pinyin.world.teamLost"));
            return;
        }
        if(before != lives.remaining()){
            ui.showInfoToast(Core.bundle.format("pinyin.world.lifeLost", lives.remaining()), 2f);
        }
        for(int i = 0; i < 2; i++){
            if(lives.ready(i)) revive(i);
        }

        pursuers.removeAll(unit -> unit == null || unit.dead() || !unit.isAdded());
        spawnTimer -= Time.delta;
        if(spawnTimer <= 0f) spawnMonster();

        if(!chestDialogOpen){
            for(int i = 0; i < CHESTS.length; i++){
                if(!collected[i] && (atChest(firstPlayer, i) || atChest(partner, i))){
                    openChest(i);
                    break;
                }
            }
        }

        if(countered && (atExit(firstPlayer) || atExit(partner))){
            escaped();
        }
    }

    private boolean atChest(Unit unit, int index){
        return alive(unit) && unit.within(CHESTS[index][0] * tilesize, CHESTS[index][1] * tilesize, tilesize * 2.2f);
    }

    private boolean atExit(Unit unit){
        return alive(unit) && unit.within((WIDTH - 7) * tilesize, 6 * tilesize, tilesize * 3f);
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
            teamEffects.correct(energy, Mathf.chance(0.35f), alive(firstPlayer) ? firstPlayer : null, alive(partner) ? partner : null);
            pushPursuerBack();
            grantReward();
            Sounds.uiUnlock.play();
            ui.showInfoToast(Core.bundle.format("pinyin.world.correct", question.character, energy, ENERGY_NEEDED, combatBoostText()), 3f);
        }else{
            teamEffects.wrong(alive(firstPlayer) ? firstPlayer : null, alive(partner) ? partner : null);
            spawnMonster();
            Sounds.explosion.play();
            ui.showInfoToast(Core.bundle.format("pinyin.world.wrong", question.choices[0], combatWeaknessText()), 3f);
        }
    }

    private void grantReward(){
        var core = player.team().core();
        if(core != null) core.items.add(Items.copper, 45 + energy * 25);
    }

    private void spawnMonster(){
        if(!active || (!alive(firstPlayer) && !alive(partner))) return;

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
        for(Unit pursuer : pursuers){
            if(pursuer == null || pursuer.dead()) continue;
            Unit target = nearestPlayer(pursuer);
            if(target == null) continue;
            Vec2 away = Tmp.v1.set(pursuer).sub(target).nor();
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

    private void escaped(){
        finish(Core.bundle.format("pinyin.world.escaped", energy));
    }

    private void finish(String message){
        stop();
        Core.app.post(() -> {
            state.set(GameState.State.menu);
            ui.showInfoFade(message);
        });
    }

    private int pursuerDistance(){
        float distance = Float.MAX_VALUE;
        for(Unit unit : pursuers){
            Unit target = alive(unit) ? nearestPlayer(unit) : null;
            if(target != null) distance = Math.min(distance, unit.dst(target));
        }
        return distance == Float.MAX_VALUE ? 0 : (int)(distance / tilesize);
    }

    private Unit nearestPlayer(Unit from){
        if(!alive(firstPlayer)) return alive(partner) ? partner : null;
        if(!alive(partner)) return firstPlayer;
        return from.dst2(firstPlayer) <= from.dst2(partner) ? firstPlayer : partner;
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
            if(unit == null) return;
            Unit target = nearestPlayer(unit);
            if(target == null) return;
            unit.aimLook(target);
            float chaseSpeed = Math.min(unit.speed() * 0.38f, target.speed() * 0.60f);
            unit.movePref(vec.set(target).sub(unit).setLength(chaseSpeed));
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
