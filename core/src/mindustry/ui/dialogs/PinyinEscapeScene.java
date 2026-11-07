package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.units.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.maps.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import java.util.Arrays;

import static mindustry.Vars.*;

/** A real Mindustry map that turns exploration and pursuit into pinyin practice. */
public class PinyinEscapeScene{
    private static final int WIDTH = PinyinAdventureMap.width, HEIGHT = PinyinAdventureMap.height;
    private static final int ENERGY_NEEDED = 3;
    private static final Effect correctStars = new Effect(55f, 130f, e -> {
        Draw.color(Pal.accent, Color.white, e.fout());
        Draw.alpha(e.fout());
        Angles.randLenVectors(e.id, 12, 5f + e.finpow() * 40f, (x, y) -> {
            float size = 3f + e.fout() * 7f;
            Draw.rect(Icon.star.getRegion(), e.x + x, e.y + y, size, size, e.fin() * 120f);
        });
        Draw.reset();
    }).layer(Layer.flyingUnit + 3f);
    private static final Effect correctRing = new Effect(85f, 120f, e -> {
        Draw.color(Pal.heal);
        Draw.alpha(e.fout());
        Lines.stroke(1f + e.fout() * 3f);
        Lines.circle(e.x, e.y, 9f + e.fin() * 27f);
        Lines.circle(e.x, e.y, 7f + e.fin() * 16f);
        Draw.rect(Icon.ok.getRegion(), e.x, e.y + 12f + e.fin() * 24f, 18f, 18f);
        Drawf.light(e.x, e.y, 70f * e.fout(), Pal.heal, 0.8f);
        Draw.reset();
    }).layer(Layer.flyingUnit + 3f);
    private static final Effect wrongRing = new Effect(50f, 130f, e -> {
        Draw.color(Pal.remove);
        Draw.alpha(e.fout());
        Lines.stroke(1f + e.fout() * 3f);
        Lines.poly(e.x, e.y, 8, 8f + e.finpow() * 36f, e.fin() * 30f);
        Draw.rect(Icon.cancel.getRegion(), e.x, e.y + 12f + e.fin() * 12f, 18f, 18f);
        Draw.reset();
    }).layer(Layer.flyingUnit + 3f);
    private static final Effect wrongShards = new Effect(65f, 130f, e -> {
        Draw.color(Color.valueOf("bc76ed"), Pal.remove, e.fin());
        Draw.alpha(e.fout());
        Angles.randLenVectors(e.id + 71, 14, 6f + e.finpow() * 37f, (x, y) -> {
            Fill.poly(e.x + x, e.y + y - e.fin() * e.fin() * 20f, 3, 1f + e.fout() * 4f, e.fin() * 280f + x);
        });
        Draw.reset();
    }).layer(Layer.flyingUnit + 3f);
    private static final PinyinQuestions.Question[] QUESTION_BANK = PinyinQuestions.all;

    private final Map map = new Map(StringMap.of(
        "name", "pinyin-adventure",
        "description", "Pinyin Co-op Adventure"
    ));
    private final boolean[] collected = new boolean[PinyinAdventureMap.chestCount];
    private final PinyinTeamEffects.Reward[] bunkerRewards = new PinyinTeamEffects.Reward[PinyinAdventureMap.chestCount];
    private final PinyinArsenal.Kind[] bunkerWeapons = new PinyinArsenal.Kind[PinyinAdventureMap.chestCount];
    private final float[] chestRecharge = new float[PinyinAdventureMap.chestCount];
    private final PinyinQuestions.Question[] runQuestions = new PinyinQuestions.Question[PinyinAdventureMap.chestCount];
    private final Table hud = new Table();
    private final Seq<Unit> pursuers = new Seq<>();
    private final Seq<Unit> allies = new Seq<>();
    private final Seq<SupplyDrop> drops = new Seq<>();
    private final PinyinAdventureProgress adventure = new PinyinAdventureProgress();
    private PinyinAdventureMap layout = new PinyinAdventureMap(System.nanoTime());
    private Texture portraitTexture;
    private TextureRegion portraitRegion;
    private Unit firstPlayer, partner;
    private final PinyinCoopLives lives = new PinyinCoopLives();
    private final PinyinTeamEffects[] playerEffects = {new PinyinTeamEffects(), new PinyinTeamEffects()};
    private final PinyinPlayerMomentum[] momentum = {new PinyinPlayerMomentum(), new PinyinPlayerMomentum()};
    private final Seq<PickupFeedback> feedback = new Seq<>();
    /** One independent question board per player; both can be active at once. */
    private final PinyinWorldQuestion[] worldQuestions = {new PinyinWorldQuestion(), new PinyinWorldQuestion()};
    private final boolean[] reservedChests = new boolean[PinyinAdventureMap.chestCount];
    private final float[] reviveProtection = new float[2];
    private final float[] contactCooldown = new float[2];
    private final Vec2[] safePosition = {new Vec2(), new Vec2()};
    private boolean active, worldReady, bossSpawned, campActive;
    private int spawnWave, supplies;
    private final int[] energy = new int[2], pulseCharge = new int[2];
    private int carriedDrones;
    private float spawnTimer;
    private final float[] chestCooldown = new float[2];
    private float supplyTimer;
    private float previousCameraScale;
    private Unit guardian;
    private final PinyinPronunciation pronunciation = new PinyinPronunciation();

    public PinyinEscapeScene(){
        world.addMapLoader(map, this::generateMap);
        Events.on(WorldLoadEvent.class, event -> {
            if(active && state.map == map){
                worldReady = true;
                pursuers.clear();
                for(PinyinWorldQuestion question : worldQuestions) question.clear();
                reviveProtection[0] = reviveProtection[1] = 0f;
                contactCooldown[0] = contactCooldown[1] = 0f;
                spawnTimer = 60f * 12f;
                Point2 entry = layout.entrances[adventure.stage()];
                firstPlayer = spawnPlayer(false, entry.x * tilesize, entry.y * tilesize);
                partner = spawnPlayer(true, (entry.x + 2) * tilesize, entry.y * tilesize);
                for(int i = 0; i < carriedDrones; i++) spawnDrone();
                carriedDrones = 0;
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
        Events.run(Trigger.draw, this::drawAdventure);

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
        previousCameraScale = renderer.getScale();
        active = true;
        worldReady = false;
        energy[0] = energy[1] = 0;
        supplies = 40;
        pulseCharge[0] = pulseCharge[1] = 0;
        carriedDrones = 0;
        adventure.reset();
        pursuers.clear();
        firstPlayer = partner = null;
        for(PinyinWorldQuestion question : worldQuestions) question.clear();
        resetStage();
        lives.reset();
        for(PinyinTeamEffects effects : playerEffects) effects.reset();
        for(PinyinPlayerMomentum progress : momentum) progress.reset();
        supplyTimer = 60f * 20f;
        feedback.clear();
        spawnWave = 0;
        spawnTimer = 60f * 12f;
        ui.pinyin.hide();
        loadChapter();
    }

    private void loadChapter(){
        worldReady = false;
        carriedDrones = allies.count(this::alive);
        allies.clear();
        layout = new PinyinAdventureMap(System.nanoTime());
        for(int i = 0; i < collected.length; i++){ collected[i] = false; chestRecharge[i] = 0f; }
        Arrays.fill(reservedChests, false);
        Arrays.fill(chestCooldown, 0f);
        randomizeQuestions();
        planBunkerRewards();

        Rules rules = new Rules();
        rules.defaultTeam = Team.sharded;
        rules.waveTeam = Team.crux;
        rules.waves = false;
        rules.attackMode = false;
        rules.pvp = false;
        rules.canGameOver = false;
        rules.unitCap = 120;
        rules.unitCapVariable = false;
        rules.fog = false;
        rules.staticFog = false;
        rules.lighting = true;
        rules.ambientLight.set(0.08f, 0.10f, 0.16f, 0.82f);
        rules.env = Env.terrestrial;
        rules.planet = Planets.serpulo;
        control.playMap(map, rules);
    }

    private void stop(){
        pronunciation.stop();
        renderer.setScale(previousCameraScale);
        active = false;
        worldReady = false;
        pursuers.clear();
        allies.clear();
        drops.clear();
        for(PinyinWorldQuestion question : worldQuestions) question.clear();
        if(alive(firstPlayer)) firstPlayer.remove();
        if(alive(partner)) partner.remove();
        firstPlayer = partner = null;
        for(PinyinTeamEffects effects : playerEffects) effects.reset();
        feedback.clear();
    }

    private void buildHud(){
        hud.clearChildren();
        hud.top().left();
        hud.table(Styles.black6, info -> {
            info.defaults().left().pad(5f);
            info.table(avatar -> {
                avatar.image(Icon.play).size(26f).padRight(6f);
                avatar.add("@pinyin.world.player1").color(Color.white).row();
                if(portraitRegion != null){
                    avatar.image(portraitRegion).size(26f).padRight(6f);
                    avatar.add("@pinyin.world.player2").color(Color.white);
                }
            }).row();
            info.label(() -> Core.bundle.format("pinyin.adventure.stage", adventure.chapter(), adventure.stage() + 1, stageName())).color(Pal.accent).row();
            info.label(() -> PinyinGamepad.state.connected ? Core.bundle.format("pinyin.gamepad.connected", PinyinGamepad.state.name)
                : Core.bundle.get("pinyin.gamepad.waiting")).wrap().width(330f).color(Pal.accent).row();
            info.label(this::objectiveText).wrap().width(330f).color(Color.white).row();
            info.label(() -> Core.bundle.format("pinyin.adventure.supplies", supplies, pursuers.size)).color(Pal.accent).row();
            info.label(() -> powerText(0)).wrap().width(330f).color(Pal.heal).row();
            info.label(() -> powerText(1)).wrap().width(330f).color(Pal.heal).row();
            info.label(() -> Core.bundle.format("pinyin.adventure.personalGoals", momentum[0].streak(), momentum[1].streak(),
                (int)momentum[0].dashSeconds(), (int)momentum[1].dashSeconds())).wrap().width(330f).color(Pal.accent).row();
            info.label(() -> Core.bundle.format("pinyin.adventure.supplySoon", Math.max(0, (int)Math.ceil(supplyTimer / 60f)))).color(Color.lightGray).row();
            info.collapser(t -> t.label(this::questionText).wrap().width(330f).color(Pal.accent), this::anyQuestionActive).row();
            info.label(() -> Core.bundle.format("pinyin.world.lives", lives.remaining(), PinyinCoopLives.startingLives,
                (int)(lives.lifeProgress() * 100f))).color(Pal.accent).row();
            info.collapser(t -> t.label(() -> respawnText(0)).color(Pal.remove), () -> !alive(firstPlayer)).row();
            info.collapser(t -> t.label(() -> respawnText(1)).color(Pal.remove), () -> !alive(partner)).row();
            info.add("@pinyin.world.help").color(Color.lightGray).wrap().width(330f).row();
            for(int i = 0; i < 2; i++){
                int index = i;
                info.button(Core.bundle.format("pinyin.world.counter", index + 1), Icon.commandAttack, () -> counterattack(index))
                    .disabled(button -> pulseCharge[index] < ENERGY_NEEDED || pursuers.isEmpty()
                        || !alive(index == 0 ? firstPlayer : partner))
                    .height(36f).fillX().row();
            }
            info.collapser(camp -> {
                camp.defaults().growX().height(36f).pad(2f);
                camp.add("@pinyin.adventure.camp").wrap().row();
                camp.button("@pinyin.adventure.buyRepair", this::repairTeam).disabled(button -> supplies < 25).row();
                camp.button("@pinyin.adventure.buyDrone", this::buyDrone).disabled(button -> supplies < 60 || allies.size >= 2);
            }, this::nearCamp).fillX().row();
            info.collapser(t -> t.label(() -> Core.bundle.format("pinyin.adventure.bossHealth", bossName(), alive(guardian) ? (int)guardian.health : 0))
                .color(Pal.remove), () -> alive(guardian)).row();
        }).pad(10f);
    }

    private String stageName(){
        return Core.bundle.get("pinyin.adventure.theme" + layout.themes[adventure.stage()]) + " · "
            + Core.bundle.get("pinyin.adventure.lesson" + adventure.stage());
    }

    private String questionText(){
        if(!anyQuestionActive()) return "";
        StringBuilder text = new StringBuilder();
        for(int slot = 0; slot < 2; slot++){
            PinyinWorldQuestion board = worldQuestions[slot];
            if(!board.active()) continue;
            PinyinQuestions.Question question = questionFor(board.chestIndex());
            if(text.length() > 0) text.append("\n");
            text.append("P").append(slot + 1).append(" ")
                .append(Core.bundle.format("pinyin.world.pickQuestion", question.character, question.category, Strings.fixed(board.secondsLeft(), 1)));
        }
        return text.toString();
    }

    private boolean anyQuestionActive(){
        return worldQuestions[0].active() || worldQuestions[1].active();
    }

    private boolean[] unavailableChests(){
        boolean[] unavailable = Arrays.copyOf(collected, collected.length);
        for(int i = 0; i < unavailable.length; i++) unavailable[i] |= reservedChests[i];
        return unavailable;
    }

    private String powerText(int index){
        PinyinTeamEffects effects = playerEffects[index];
        return Core.bundle.format("pinyin.world.power", index + 1, effects.power(), Strings.autoFixed(effects.damageMultiplier(), 2), effects.bulletsPerShot(), pulseCharge[index]) + "\n"
            + Core.bundle.format("pinyin.world.weapon", Core.bundle.get("pinyin.weapon." + effects.weaponKind().name())) + " · "
            + (effects.secondsLeft() == 0 ? Core.bundle.get("pinyin.world.boostNone")
                : Core.bundle.format("pinyin.world.boostTimer", effects.secondsLeft()));
    }

    private String objectiveText(){
        if(adventure.ready()) return Core.bundle.get("pinyin.adventure.exitReady");
        String answers = Core.bundle.format("pinyin.adventure.answers", adventure.answers(), adventure.requiredAnswers());
        return answers + "\n" + switch(adventure.stage()){
            case 0, 1 -> Core.bundle.format("pinyin.adventure.kills", adventure.kills(), adventure.requiredKills());
            case 2 -> Core.bundle.format("pinyin.adventure.charge", adventure.chargeSeconds(), 25);
            default -> Core.bundle.get(adventure.bossDefeated() ? "pinyin.adventure.bossDone" : "pinyin.adventure.bossGoal");
        };
    }

    private void generateMap(){
        state.map = map;
        world.loadGenerator(WIDTH, HEIGHT, layout::generate);
    }

    private void randomizeQuestions(){
        for(int stage = 0; stage < PinyinAdventureMap.stages; stage++){
            Seq<PinyinQuestions.Question> pool = new Seq<>();
            for(PinyinQuestions.Question question : QUESTION_BANK){
                if(stage == 3 || question.category.startsWith(stage == 0 ? "声母" : stage == 1 ? "韵母" : "拼读")) pool.add(question);
            }
            pool.shuffle();
            for(int i = 0; i < PinyinAdventureMap.chestsPerStage; i++){
                runQuestions[stage * PinyinAdventureMap.chestsPerStage + i] = pool.get(i % pool.size);
            }
        }
    }

    private void planBunkerRewards(){
        PinyinTeamEffects.Reward[] rewards = PinyinTeamEffects.Reward.values();
        PinyinArsenal.Kind[] weapons = PinyinArsenal.Kind.values();
        for(int i = 0; i < bunkerRewards.length; i++){
            int stage = i / PinyinAdventureMap.chestsPerStage;
            bunkerRewards[i] = rewards[Math.floorMod(i * 3 + layout.themes[stage], rewards.length)];
            bunkerWeapons[i] = weapons[1 + Math.floorMod(i * 5 + layout.themes[stage], weapons.length - 1)];
        }
    }

    private void refreshQuestion(int index){
        int stage = index / PinyinAdventureMap.chestsPerStage;
        PinyinQuestions.Question previous = questionFor(index);
        Seq<PinyinQuestions.Question> pool = new Seq<>();
        for(PinyinQuestions.Question question : QUESTION_BANK){
            if(question != previous && (stage == 3 || question.category.startsWith(stage == 0 ? "声母" : stage == 1 ? "韵母" : "拼读"))) pool.add(question);
        }
        runQuestions[index] = pool.random();
    }

    private String bossName(){
        return guardian != null && guardian.controller() instanceof PinyinEnemyController ai
            ? Core.bundle.get("pinyin.boss." + ai.kind.name()) : "";
    }

    private PinyinQuestions.Question questionFor(int index){
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
        playerEffects[second ? 1 : 0].inherit(unit);
        safePosition[second ? 1 : 0].set(unit);
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
                    int stageLeft = PinyinAdventureMap.left(adventure.stage());
                    if(tile == null || tile.x < stageLeft + 2 || tile.x > PinyinAdventureMap.right(adventure.stage()) - 2 || tile.y < 3 || tile.y >= HEIGHT - 3 || tile.solid()) continue;
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

    private void drawAdventure(){
        if(!isActive()) return;
        int stage = adventure.stage(), start = stage * PinyinAdventureMap.chestsPerStage;
        // Learning text and navigation must remain readable above the night lighting.
        Draw.z(Layer.playerName + 1f);
        for(int i = start; i < start + PinyinAdventureMap.chestsPerStage; i++){
            Point2 chest = layout.chests[i];
            if(collected[i]){
                Fonts.outline.draw(Core.bundle.format("pinyin.adventure.recharge", (int)Math.ceil(chestRecharge[i] / 60f)),
                    chest.x * tilesize, chest.y * tilesize + 16f, Color.gray, 0.35f, false, Align.center);
                continue;
            }
            Color typeColor = bunkerColor(bunkerRewards[i]);
            Draw.color(typeColor, 0.2f);
            Fill.circle(chest.x * tilesize, chest.y * tilesize, 15f + Mathf.absin(7f, 2f));
            Draw.color(typeColor);
            Draw.rect(Blocks.vault.fullIcon, chest.x * tilesize, chest.y * tilesize, 21f, 21f);
            float progress = Math.max(worldQuestions[0].chargeProgress(i), worldQuestions[1].chargeProgress(i));
            Color chargeColor = progress > 0f ? Pal.heal : typeColor;
            if(reservedChests[i]) chargeColor = Color.gray;
            Draw.color(chargeColor);
            Lines.stroke(1f);
            Lines.circle(chest.x * tilesize, chest.y * tilesize, PinyinWorldQuestion.chargeRadius);
            Lines.square(chest.x * tilesize, chest.y * tilesize, 14f + Mathf.absin(8f, 2f), 45f);
            if(progress > 0f){
                Draw.color(Color.darkGray);
                Fill.rect(chest.x * tilesize, chest.y * tilesize - 16f, 32f, 4f);
                Draw.color(Pal.heal);
                Fill.rect(chest.x * tilesize - 16f + 16f * progress, chest.y * tilesize - 16f, 32f * progress, 4f);
            }
            String chargeText = reservedChests[i] ? Core.bundle.get("pinyin.adventure.chestWaiting")
                : progress > 0f ? Core.bundle.format("pinyin.adventure.chestProgress", Mathf.round(progress * 100f))
                : Core.bundle.get("pinyin.adventure.chargeChest");
            Fonts.outline.draw(chargeText, chest.x * tilesize, chest.y * tilesize - 30f, chargeColor, 0.3f, false, Align.center);
            Fonts.outline.draw(questionFor(i).character, chest.x * tilesize, chest.y * tilesize + 23f, typeColor, 0.5f, false, Align.center);
            Fonts.outline.draw(Core.bundle.get("pinyin.reward." + bunkerRewards[i].name()) + " · "
                + Core.bundle.get("pinyin.weapon." + bunkerWeapons[i].name()), chest.x * tilesize, chest.y * tilesize + 35f,
                typeColor, 0.28f, false, Align.center);
        }
        Point2 exit = layout.exits[stage], camp = layout.camps[stage], beacon = layout.beacons[stage];
        drawLayeredMarker(beacon, stage == 2 ? Color.valueOf("bb7a4a") : stage == 3 ? Color.valueOf("d8f3ff") : Pal.accent, 11f);
        drawLayeredMarker(camp, Pal.heal, 6f);
        drawLayeredMarker(exit, Pal.remove, 4f);
        Color exitColor = adventure.ready() ? Pal.heal : Pal.remove;
        Draw.color(exitColor);
        Lines.stroke(2f);
        Lines.circle(exit.x * tilesize, exit.y * tilesize, tilesize * 3f + Mathf.absin(8f, 2f));
        Fonts.outline.draw(Core.bundle.get(adventure.ready() ? "pinyin.adventure.portalOpen" : "pinyin.adventure.portalLocked"),
            exit.x * tilesize, exit.y * tilesize + 35f, exitColor, 0.35f, false, Align.center);
        Drawf.light(exit.x * tilesize, exit.y * tilesize, tilesize * 10f, exitColor, 0.6f);
        Draw.color(Pal.heal);
        Lines.dashCircle(camp.x * tilesize, camp.y * tilesize, tilesize * 6f);
        Fonts.outline.draw(Core.bundle.get(campActive ? "pinyin.adventure.campActiveMarker" : "pinyin.adventure.campMarker"),
            camp.x * tilesize, camp.y * tilesize + 42f, Pal.heal, 0.35f, false, Align.center);
        if(stage == 2){
            Draw.color(Pal.accent);
            Lines.dashCircle(beacon.x * tilesize, beacon.y * tilesize, tilesize * 11f);
            Fonts.outline.draw(Core.bundle.format("pinyin.adventure.chargeMarker", adventure.chargeSeconds()),
                beacon.x * tilesize, beacon.y * tilesize + 12f, Pal.accent, 0.4f, false, Align.center);
            Draw.color(Color.darkGray);
            Fill.rect(beacon.x * tilesize, beacon.y * tilesize - 18f, 48f, 5f);
            Draw.color(Pal.accent);
            Fill.rect(beacon.x * tilesize - 24f + 24f * adventure.chargeProgress(), beacon.y * tilesize - 18f,
                48f * adventure.chargeProgress(), 5f);
        }
        for(SupplyDrop drop : drops){
            Draw.color(drop.kind < 4 ? Pal.accent : drop.kind == 4 ? Pal.heal
                : drop.kind == 5 ? Pal.lancerLaser : drop.kind == 6 ? Pal.accent : drop.kind == 7 ? Pal.remove : Pal.surge);
            Fill.square(drop.x, drop.y, 3f + Mathf.absin(8f, 1f), 45f);
            Drawf.light(drop.x, drop.y, 28f, Draw.getColor(), 0.7f);
            if(drop.kind < 4){
                Draw.color();
                Draw.rect(Items.copper.fullIcon, drop.x, drop.y, 10f, 10f);
            }else{
                Fonts.outline.draw(drop.kind == 4 ? "♥" : drop.kind == 5 ? "◆" : drop.kind == 6 ? "+15s" : drop.kind == 7 ? "+1" : "⚡",
                    drop.x, drop.y + 5f, Color.white, 0.4f, false, Align.center);
            }
            if(drop.limited) Fonts.outline.draw((int)Math.ceil(drop.time / 60f) + "s", drop.x, drop.y + 12f,
                Pal.accent, 0.4f, false, Align.center);
        }
        for(Unit enemy : pursuers){
            if(alive(enemy) && enemy.controller() instanceof PinyinEnemyController ai) ai.drawWarning();
        }
        drawWorldQuestion();
        drawTurretPads();
        Draw.z(Layer.playerName + 4f);
        for(int i = 0; i < 2; i++){
            Unit unit = i == 0 ? firstPlayer : partner;
            if(!alive(unit)) continue;
            Color color = i == 0 ? Pal.accent : Pal.lancerLaser;
            Draw.color(color);
            Lines.stroke(1f);
            Lines.circle(unit.x, unit.y, unit.hitSize * 0.65f + 2f);
            drawHealthBar(unit, color, 34f);
            Fonts.outline.draw(Core.bundle.format("pinyin.adventure.playerMarker", i + 1, energy[i]),
                unit.x, unit.y + unit.hitSize + 10f, color, 0.4f, false, Align.center);
        }
        for(Unit enemy : pursuers) if(alive(enemy)) drawHealthBar(enemy, PinyinEnemyController.boss(enemy.controller() instanceof PinyinEnemyController ai ? ai.kind : PinyinEnemyController.Kind.gunner) ? Pal.remove : Pal.lightOrange, 32f);
        for(PickupFeedback popup : feedback){
            Fonts.outline.draw(popup.text, popup.x, popup.y + (75f - popup.time) * 0.35f,
                Tmp.c1.set(popup.color).a(Math.min(1f, popup.time / 20f)), 0.6f, false, Align.center);
        }
        // A world-space arrow points toward the next useful objective, including off-camera chests.
        Unit leader = alive(firstPlayer) ? firstPlayer : alive(partner) ? partner : null;
        if(leader != null && !anyQuestionActive()){
            Point2 goal = adventure.ready() ? exit : stage == 2 && adventure.answers() >= adventure.requiredAnswers() ? beacon : exit;
            if(adventure.answers() < adventure.requiredAnswers()){
                float nearest = Float.MAX_VALUE;
                for(int i = start; i < start + PinyinAdventureMap.chestsPerStage; i++){
                    Point2 chest = layout.chests[i];
                    float distance = leader.dst2(chest.x * tilesize, chest.y * tilesize);
                    if(!collected[i] && distance < nearest){ nearest = distance; goal = chest; }
                }
            }
            float gx = alive(guardian) ? guardian.x : goal.x * tilesize, gy = alive(guardian) ? guardian.y : goal.y * tilesize;
            Vec2 direction = Tmp.v1.set(gx, gy).sub(leader).setLength(42f);
            Drawf.arrow(leader.x, leader.y, leader.x + direction.x, leader.y + direction.y, 12f, 5f, Pal.accent);
        }
        Draw.reset();
    }

    private Color bunkerColor(PinyinTeamEffects.Reward reward){
        return switch(reward){
            case power -> Pal.heal;
            case multishot -> Color.valueOf("ffd166");
            case rapid -> Pal.lancerLaser;
            case giant -> Color.valueOf("ff8a65");
            case arsenal -> Color.valueOf("c792ea");
            case shield -> Color.valueOf("63d8ff");
            case rainbow -> Color.valueOf("ff72c6");
            case wobble -> Color.valueOf("b48aff");
        };
    }

    /** Small offset rings make the objective platforms read as raised layers in the top-down view. */
    private void drawLayeredMarker(Point2 point, Color color, float radius){
        Draw.z(Layer.floor + 1f);
        for(int level = 3; level >= 1; level--){
            Draw.color(color, 0.12f + level * 0.04f);
            Lines.stroke(1.5f);
            Lines.circle(point.x * tilesize, point.y * tilesize - level * 2.5f, radius * tilesize + level * 2f);
        }
        Draw.reset();
    }

    private void drawHealthBar(Unit unit, Color color, float width){
        float ratio = Mathf.clamp(unit.health / Math.max(1f, unit.maxHealth));
        float shield = Mathf.clamp(unit.shield / Math.max(1f, unit.maxHealth));
        float y = unit.y + unit.hitSize * 0.7f + 7f;
        Draw.z(Layer.playerName + 6f);
        Draw.color(Color.black, 0.75f);
        Fill.rect(unit.x, y, width, 3f);
        Draw.color(color);
        Fill.rect(unit.x - width / 2f + width * ratio / 2f, y, width * ratio, 3f);
        if(shield > 0f){
            Draw.color(Pal.lancerLaser);
            Fill.rect(unit.x - width / 2f + width * shield / 2f, y + 3f, width * shield, 1.5f);
        }
        Draw.reset();
    }

    private void resetStage(){
        pronunciation.stop();
        for(PinyinWorldQuestion question : worldQuestions) question.clear();
        bossSpawned = campActive = false;
        guardian = null;
        chestCooldown[0] = chestCooldown[1] = 60f * 2f;
        spawnTimer = 60f * 8f;
    }

    private void drawWorldQuestion(){
        for(int slot = 0; slot < 2; slot++){
            PinyinWorldQuestion board = worldQuestions[slot];
            if(!board.active()) continue;
            PinyinQuestions.Question question = questionFor(board.chestIndex());
            Point2 chest = layout.chests[board.chestIndex()];
            Color playerColor = slot == 0 ? Pal.accent : Pal.lancerLaser;
            Color timerColor = board.secondsLeft() <= 2f ? Pal.remove : playerColor;
            Fonts.outline.draw("P" + (slot + 1) + "  " + question.character + " · " + Strings.fixed(board.secondsLeft(), 1) + "s",
                chest.x * tilesize, chest.y * tilesize + 42f, timerColor, 0.7f, false, Align.center);
            for(int i = 0; i < 3; i++){
                Vec2 option = board.positions[i];
                boolean hint = board.secondsLeft() <= 2.5f && board.choices[i] == 0;
                Color optionColor = hint ? Pal.heal : playerColor;
                Draw.z(Layer.playerName + 3f);
                Draw.color(optionColor, hint ? 0.48f : 0.35f);
                Fill.circle(option.x, option.y, 11f);
                Draw.color(optionColor);
                Lines.stroke(2f);
                Lines.circle(option.x, option.y, 12f + Mathf.absin(5f, hint ? 3f : 1.5f));
                Fonts.outline.draw(question.choices[board.choices[i]], option.x, option.y + 8f,
                    Color.white, 0.65f, false, Align.center);
                if(hint) Fill.circle(option.x, option.y + 16f, 2.2f + Mathf.absin(4f, 1f));
                Drawf.light(option.x, option.y, hint ? 65f : 45f, optionColor, hint ? 0.9f : 0.7f);
            }
        }
        Draw.reset();
    }

    private void drawTurretPads(){
        int start = adventure.stage() * PinyinAdventureMap.padsPerStage;
        for(int i = start; i < start + PinyinAdventureMap.padsPerStage; i++){
            Point2 pad = layout.turretPads[i];
            Tile tile = world.tile(pad.x, pad.y);
            boolean built = PinyinTurretPads.built(tile);
            boolean loaded = PinyinTurretPads.loaded(tile);
            if(loaded) continue;
            Color color = supplies >= (built ? 20 : 40) ? Pal.heal : Color.gray;
            Draw.z(Layer.playerName + 2f);
            Draw.color(color);
            Lines.dashCircle(pad.x * tilesize, pad.y * tilesize, 3f * tilesize);
            Draw.alpha(0.5f);
            Draw.rect(PinyinTurretPads.turret(i).fullIcon, pad.x * tilesize, pad.y * tilesize, 16f, 16f);
            Fonts.outline.draw(Core.bundle.format(built ? "pinyin.adventure.padReload" : "pinyin.adventure.padBuild", PinyinTurretPads.turret(i).localizedName),
                pad.x * tilesize, pad.y * tilesize + 30f, color, 0.3f, false, Align.center);
        }
        Draw.reset();
    }

    private void advanceStage(){
        for(Unit enemy : pursuers) if(enemy.isAdded()) enemy.remove();
        pursuers.clear();
        Groups.bullet.each(Bullet::remove);
        drops.clear();
        supplies += 35;
        for(Unit unit : new Unit[]{firstPlayer, partner}){
            unit.heal(35f);
            unit.shield += 40f;
        }
        boolean nextChapter = adventure.advance();
        resetStage();
        if(nextChapter){
            loadChapter();
        }else{
            Point2 entry = layout.entrances[adventure.stage()];
            firstPlayer.set(entry.x * tilesize, entry.y * tilesize);
            partner.set((entry.x + 2) * tilesize, entry.y * tilesize);
            safePosition[0].set(firstPlayer);
            safePosition[1].set(partner);
            player.set(firstPlayer);
            for(int i = 0; i < 2; i++){
                Unit unit = i == 0 ? firstPlayer : partner;
                unit.vel.setZero();
                unit.apply(StatusEffects.invincible, 120f);
                reviveProtection[i] = 120f;
            }
            for(Unit ally : allies) ally.set(firstPlayer.x, firstPlayer.y + 3 * tilesize);
            ui.showInfoToast(Core.bundle.format("pinyin.adventure.nextStage", stageName()), 3f);
        }
    }

    private boolean nearCamp(){
        return isActive() && (nearPoint(firstPlayer, layout.camps[adventure.stage()], 8f)
            || nearPoint(partner, layout.camps[adventure.stage()], 8f));
    }

    private void updateTurretPads(){
        int start = adventure.stage() * PinyinAdventureMap.padsPerStage;
        for(int i = start; i < start + PinyinAdventureMap.padsPerStage; i++){
            Point2 pad = layout.turretPads[i];
            Tile tile = world.tile(pad.x, pad.y);
            int cost = PinyinTurretPads.activate(tile, i, supplies, firstPlayer, partner);
            if(cost == 0) continue;
            supplies -= cost;
            campActive = true;
            if(alive(firstPlayer)) safePosition[0].set(firstPlayer);
            if(alive(partner)) safePosition[1].set(partner);
            Fx.upgradeCore.at(tile.worldx(), tile.worldy());
            floating(tile.worldx(), tile.worldy(), Core.bundle.get("pinyin.adventure.turretBuilt"), Pal.heal);
            ui.showInfoToast("@pinyin.adventure.turretsReady", 2f);
        }
    }

    private void repairTeam(){
        if(!nearCamp() || supplies < 25 || !state.isPlaying()) return;
        supplies -= 25;
        for(Unit unit : new Unit[]{firstPlayer, partner}){
            if(alive(unit)){
                unit.heal(60f);
                unit.shield += 50f;
                Fx.heal.at(unit);
            }
        }
        ui.showInfoToast("@pinyin.adventure.repaired", 2f);
    }

    private void buyDrone(){
        if(!nearCamp() || supplies < 60 || allies.size >= 2 || !state.isPlaying()) return;
        supplies -= 60;
        spawnDrone();
        ui.showInfoToast("@pinyin.adventure.droneReady", 2f);
    }

    private void spawnDrone(){
        Unit anchor = alive(firstPlayer) ? firstPlayer : partner;
        Unit drone = UnitTypes.poly.spawn(Team.sharded, anchor.x, anchor.y + 3 * tilesize);
        drone.controller(new DroneController());
        allies.add(drone);
    }

    private void updateDrops(){
        for(int i = drops.size - 1; i >= 0; i--){
            SupplyDrop drop = drops.get(i);
            drop.time -= Time.delta;
            Unit collector = alive(firstPlayer) && firstPlayer.within(drop.x, drop.y, 4f * tilesize) ? firstPlayer
                : alive(partner) && partner.within(drop.x, drop.y, 4f * tilesize) ? partner : null;
            if(collector != null){
                if(drop.kind < 4){
                    supplies += drop.amount;
                }else if(drop.kind == 4){
                    collector.heal(25f);
                }else if(drop.kind == 5){
                    collector.shield += 30f;
                }else if(drop.kind == 6){
                    int extended = playerEffects[collector == firstPlayer ? 0 : 1].extendBoosts(60f * 15f);
                    if(extended == 0) collector.heal(20f);
                }else if(drop.kind == 7){
                    lives.addLife();
                }else{
                    collector.apply(StatusEffects.fast, 60f * 8f);
                }
                Fx.pickup.at(drop.x, drop.y);
                String pickup = drop.kind < 4 ? "+" + drop.amount + " " + Items.copper.localizedName
                    : drop.kind == 4 ? Core.bundle.get("pinyin.adventure.healPickup")
                    : drop.kind == 5 ? Core.bundle.get("pinyin.adventure.shieldPickup")
                    : drop.kind == 6 ? "答题增益延长15秒"
                    : drop.kind == 7 ? "额外生命 +1" : "疾跑能量 +8秒";
                floating(drop.x, drop.y, pickup, drop.kind == 7 ? Pal.remove : Pal.heal);
                Sounds.uiUnlock.play();
                drops.remove(i);
            }else if(drop.time <= 0f){
                drops.remove(i);
            }
        }
    }

    public boolean isActive(){
        return active && worldReady && state.isGame();
    }

    private boolean controlsEnabled(){
        return isActive() && state.isPlaying()
            && !Core.scene.hasDialog() && !Core.scene.hasKeyboard()
            && !ui.chatfrag.shown() && !ui.consolefrag.shown();
    }

    public void updateDesktopControls(){
        constrainPlayer(firstPlayer, 0);
        constrainPlayer(partner, 1);
        if(alive(firstPlayer)){
            PinyinPlayerInput.update(firstPlayer, false, nearestPursuer(firstPlayer), controlsEnabled());
            dashMovement(firstPlayer, 0);
            player.shooting = firstPlayer.isShooting;
            player.boosting = false;
            player.mouseX = firstPlayer.aimX;
            player.mouseY = firstPlayer.aimY;
        }else{
            player.shooting = false;
        }
        if(!controlsEnabled() && alive(partner)) partner.controlWeapons(false, false);
        if(alive(firstPlayer) && alive(partner)){
            float fit = Math.min(Core.graphics.getWidth() / (Math.abs(firstPlayer.x - partner.x) + 200f),
                Core.graphics.getHeight() / (Math.abs(firstPlayer.y - partner.y) + 180f));
            renderer.setScale(Math.min(Scl.scl(2.5f), fit));
            Core.camera.position.lerpDelta((firstPlayer.x + partner.x) / 2f, (firstPlayer.y + partner.y) / 2f, 0.08f);
        }else if(alive(firstPlayer) || alive(partner)){
            renderer.setScale(Scl.scl(2.5f));
            Core.camera.position.lerpDelta(alive(firstPlayer) ? firstPlayer : partner, 0.08f);
        }
    }

    private void constrainPlayer(Unit unit, int index){
        if(!alive(unit)) return;
        Vec2 previous = safePosition[index];
        int left = PinyinAdventureMap.left(adventure.stage());
        unit.x = Mathf.clamp(unit.x, (left + 2) * tilesize, (PinyinAdventureMap.right(adventure.stage()) - 2) * tilesize);
        unit.y = Mathf.clamp(unit.y, 3 * tilesize, (HEIGHT - 4) * tilesize);
        // Alpha is a flying unit: enforce the adventure walls for the two human players.
        Tile tile = world.tileWorld(unit.x, unit.y);
        if(tile == null || tile.solid()){
            Tile horizontal = world.tileWorld(unit.x, previous.y);
            Tile vertical = world.tileWorld(previous.x, unit.y);
            if(horizontal != null && !horizontal.solid()) unit.y = previous.y;
            else if(vertical != null && !vertical.solid()) unit.x = previous.x;
            else unit.set(previous);
            unit.vel.setZero();
        }
        previous.set(unit);
    }

    private void update(){
        if(!active || !worldReady || !state.isPlaying() || player == null) return;
        var core = Team.sharded.core();
        if(core != null) core.items.set(Items.copper, supplies);
        for(int i = 0; i < playerEffects.length; i++){
            Unit unit = i == 0 ? firstPlayer : partner;
            int beforePower = playerEffects[i].power();
            playerEffects[i].update(Time.delta, unit);
            if(alive(unit) && playerEffects[i].power() < beforePower) floating(unit.x, unit.y, Core.bundle.get("pinyin.world.boostExpired"), Pal.accent);
        }
        for(int i = 0; i < chestRecharge.length; i++){
            if(collected[i] && (chestRecharge[i] = Math.max(0f, chestRecharge[i] - Time.delta)) == 0f){
                collected[i] = false;
                refreshQuestion(i);
            }
        }
        updateFunEvents();
        for(int i = 0; i < chestCooldown.length; i++) chestCooldown[i] = Math.max(0f, chestCooldown[i] - Time.delta);
        for(int i = 0; i < 2; i++){
            reviveProtection[i] = Math.max(0f, reviveProtection[i] - Time.delta);
            contactCooldown[i] = Math.max(0f, contactCooldown[i] - Time.delta);
            Unit unit = i == 0 ? firstPlayer : partner;
            Unit nearest = alive(unit) ? nearestPursuer(unit) : null;
            if(nearest != null && reviveProtection[i] <= 0f && contactCooldown[i] <= 0f
                && unit.within(nearest, Math.max(tilesize * 1.4f, (unit.hitSize + nearest.hitSize) * 0.45f))){
                unit.damage(20f * (1f + (adventure.difficulty() - 1) * 0.12f));
                contactCooldown[i] = 60f;
                unit.impulse(Tmp.v1.set(unit).sub(nearest).setLength(3f));
            }
        }
        int before = lives.remaining();
        lives.update(alive(firstPlayer), alive(partner), Time.delta);
        if(lives.ended()){
            finish(Core.bundle.get(lives.remaining() == 0 ? "pinyin.world.noLives" : "pinyin.world.teamLost"));
            return;
        }
        if(before != lives.remaining()){
            String key = lives.remaining() > before ? "pinyin.world.lifeGained" : "pinyin.world.lifeLost";
            ui.showInfoToast(Core.bundle.format(key, lives.remaining()), 2f);
        }
        for(int i = 0; i < 2; i++){
            if(lives.ready(i)) revive(i);
        }

        boolean wasReady = adventure.ready();
        for(int i = pursuers.size - 1; i >= 0; i--){
            Unit enemy = pursuers.get(i);
            if(!alive(enemy)){
                if(!(enemy.controller() instanceof PinyinEnemyController ai) || !ai.selfDestructed){
                    adventure.defeat(enemy == guardian);
                    floating(enemy.x, enemy.y, Core.bundle.get("pinyin.adventure.defeated"), Pal.accent);
                    if(drops.size < 100) drops.add(new SupplyDrop(enemy.x, enemy.y, enemy == guardian ? 7 : Mathf.random(8), enemy == guardian ? 60 : 8));
                }
                pursuers.remove(i);
            }
        }
        allies.removeAll(unit -> !alive(unit));
        updateDrops();
        updateTurretPads();
        for(int slot = 0; slot < 2; slot++){
            PinyinWorldQuestion board = worldQuestions[slot];
            Unit owner = slot == 0 ? firstPlayer : partner;
            board.updateHint(Time.delta, alive(owner) ? owner : null);
            int result = board.update(Time.delta, alive(owner) ? owner : null, null);
            if(result == PinyinWorldQuestion.pending) continue;
            int chest = board.chestIndex();
            reservedChests[chest] = false;
            if(result == PinyinWorldQuestion.timeout){
                PinyinQuestions.Question question = questionFor(chest);
                ui.showInfoToast(Core.bundle.format("pinyin.world.timeout", question.character, question.pinyin), 3f);
            }else{
                resolveChest(chest, result, slot);
            }
            chestCooldown[slot] = 60f * 3f;
        }
        adventure.update(Time.delta, nearPoint(firstPlayer, layout.beacons[adventure.stage()], 14f)
            || nearPoint(partner, layout.beacons[adventure.stage()], 14f));
        if(adventure.stage() == 3 && adventure.answers() >= adventure.requiredAnswers() && !bossSpawned){
            guardian = spawnEnemy(PinyinEnemyController.bossKind(layout.themes[adventure.stage()], layout.bossRolls[adventure.stage()]), true);
            bossSpawned = guardian != null;
            if(bossSpawned) ui.showInfoToast(Core.bundle.format("pinyin.adventure.bossArrived", bossName()), 3f);
        }
        if(!wasReady && adventure.ready()) ui.showInfoToast("@pinyin.adventure.exitReady", 3f);
        spawnTimer -= Time.delta;
        if(spawnTimer <= 0f && !adventure.ready()) spawnMonster();

        for(int slot = 0; slot < 2; slot++){
            PinyinWorldQuestion board = worldQuestions[slot];
            Unit owner = slot == 0 ? firstPlayer : partner;
            int chargedChest = board.updateCharge(Time.delta, alive(owner) ? owner : null, null,
                layout, adventure.stage(), unavailableChests(), chestCooldown[slot] <= 0f);
            if(chargedChest >= 0){
                reservedChests[chargedChest] = true;
                openChest(chargedChest, owner);
            }
        }

        if(!anyQuestionActive() && adventure.ready() && (atExit(firstPlayer) || atExit(partner)) && alive(firstPlayer) && alive(partner)){
            advanceStage();
        }
    }

    private boolean atExit(Unit unit){
        return nearPoint(unit, layout.exits[adventure.stage()], 3f);
    }

    private boolean nearPoint(Unit unit, Point2 point, float radius){
        return alive(unit) && unit.within(point.x * tilesize, point.y * tilesize, radius * tilesize);
    }

    private void openChest(int index, Unit opener){
        if(opener == null) return;
        Unit teammate = opener == firstPlayer ? partner : firstPlayer;
        int slot = opener == firstPlayer ? 0 : 1;
        Vec2[] positions = PinyinWorldQuestion.placeOptions(world.tiles, adventure.stage(), opener, alive(teammate) ? teammate : null,
            worldQuestions[1 - slot].active() ? worldQuestions[1 - slot].positions : null);
        if(positions == null){ reservedChests[index] = false; chestCooldown[slot] = 60f; return; }
        PinyinQuestions.Question question = questionFor(index);
        pronunciation.speak(question.character);
        int[] order = {0, 1, 2};
        for(int i = order.length - 1; i > 0; i--){
            int swap = Mathf.random(i), previous = order[i];
            order[i] = order[swap];
            order[swap] = previous;
        }
        worldQuestions[slot].begin(index, positions, order);
    }

    private void resolveChest(int index, int choice, int collector){
        PinyinQuestions.Question question = questionFor(index);
        Unit picker = collector == 0 ? firstPlayer : partner;
        showAnswerEffects(index, choice == 0, collector);
        if(choice == 0){
            collected[index] = true;
            chestRecharge[index] = 60f * 20f;
            energy[collector]++;
            pulseCharge[collector] = Math.min(ENERGY_NEEDED, pulseCharge[collector] + 1);
            adventure.answer();
            PinyinTeamEffects.Reward reward = bunkerRewards[index];
            PinyinArsenal.Kind weapon = bunkerWeapons[index];
            playerEffects[collector].correct(energy[collector], reward == PinyinTeamEffects.Reward.shield || Mathf.chance(0.35f),
                alive(picker) ? picker : null, reward, weapon);
            floating(picker.x, picker.y, Core.bundle.get("pinyin.reward." + reward.name()) + " "
                + Core.bundle.get("pinyin.weapon." + playerEffects[collector].weaponKind().name()), Pal.heal);
            boolean combo = momentum[collector].correct();
            if(combo){
                playerEffects[collector].combo(picker);
                floating(picker.x, picker.y + 24f, Core.bundle.format("pinyin.adventure.combo", momentum[collector].streak()), Pal.accent);
                correctStars.at(picker);
                Fx.shockwave.at(picker);
            }
            pushPursuerBack(picker);
            Sounds.uiUnlock.play();
            ui.showInfoToast(Core.bundle.format("pinyin.world.correct", collector + 1, question.character, playerEffects[collector].bulletsPerShot(), Core.bundle.get("pinyin.reward." + reward.name())), 3f);
        }else{
            playerEffects[collector].wrong(alive(picker) ? picker : null);
            momentum[collector].wrong();
            floating(picker.x, picker.y, Core.bundle.get("pinyin.adventure.tryAgain"), Pal.accent);
            Sounds.uiBack.play();
            ui.showInfoToast(Core.bundle.format("pinyin.world.wrong", collector + 1, question.character, question.pinyin), 3f);
            pronunciation.speak(question.character);
        }
    }

    private void showAnswerEffects(int index, boolean correct, int collector){
        Effect burst = correct ? correctStars : wrongShards;
        Effect ring = correct ? correctRing : wrongRing;
        Point2 chest = layout.chests[index];
        burst.at(chest.x * tilesize, chest.y * tilesize);
        ring.at(chest.x * tilesize, chest.y * tilesize);
        for(int playerIndex = 0; playerIndex < 2; playerIndex++){
            Unit unit = playerIndex == 0 ? firstPlayer : partner;
            if(alive(unit) && playerIndex == collector){
                burst.at(unit, true);
                ring.at(unit, true);
            }
        }
    }

    private void spawnMonster(){
        if(!active || (!alive(firstPlayer) && !alive(partner))) return;

        int count = adventure.waveSize();
        for(int i = 0; i < count && pursuers.size < adventure.enemyLimit(); i++){
            var kind = PinyinEnemyController.regularKind(spawnWave * count + i + layout.patterns[adventure.stage()], adventure.difficulty(), layout.themes[adventure.stage()]);
            spawnEnemy(kind, false);
        }
        spawnWave++;
        spawnTimer = adventure.waveInterval();
        Sounds.uiNotify.play();
    }

    private Unit spawnEnemy(PinyinEnemyController.Kind kind, boolean boss){
        Unit anchor = alive(firstPlayer) && alive(partner) ? (Mathf.chance(0.5) ? firstPlayer : partner) : alive(firstPlayer) ? firstPlayer : partner;
        Point2 exit = layout.exits[adventure.stage()];
        int left = PinyinAdventureMap.left(adventure.stage());
        for(int attempt = 0; attempt < 100; attempt++){
            float angle = Mathf.random(360f), range = Mathf.random(16f, 24f) * tilesize;
            int x = boss ? exit.x + Mathf.random(-5, 0) : World.toTile(anchor.x + Angles.trnsx(angle, range));
            int y = boss ? exit.y + Mathf.random(-6, 6) : World.toTile(anchor.y + Angles.trnsy(angle, range));
            if(x < left + 4 || x > PinyinAdventureMap.right(adventure.stage()) - 4 || y < 5 || y > HEIGHT - 6) continue;
            boolean clear = true;
            for(int dx = -1; dx <= 1; dx++){
                for(int dy = -1; dy <= 1; dy++){
                    if(world.tile(x + dx, y + dy).solid()) clear = false;
                }
            }
            if(!clear || (!boss && (nearPoint(firstPlayer, new Point2(x, y), 9f) || nearPoint(partner, new Point2(x, y), 9f)))) continue;
            Unit monster = PinyinEnemyController.type(kind).spawn(Team.crux, x * tilesize, y * tilesize);
            PinyinEnemyController.equip(monster, kind, adventure.difficulty());
            monster.controller(new PinyinEnemyController(kind, this::nearestPlayer, adventure.difficulty()));
            pursuers.add(monster);
            Fx.spawn.at(monster);
            return monster;
        }
        return null;
    }

    private void pushPursuerBack(Unit picker){
        for(Unit pursuer : pursuers){
            if(!alive(pursuer) || !alive(picker) || !pursuer.within(picker, tilesize * 9f)) continue;
            pursuer.apply(StatusEffects.slow, 60f * 2f);
            pursuer.impulse(Tmp.v1.set(pursuer).sub(picker).setLength(5f));
        }
    }

    private void counterattack(int index){
        Unit owner = index == 0 ? firstPlayer : partner;
        if(!alive(owner) || !state.isPlaying() || pulseCharge[index] < ENERGY_NEEDED || pursuers.isEmpty()) return;
        pulseCharge[index] = 0;
        for(Unit pursuer : pursuers){
            if(alive(pursuer) && pursuer.within(owner, tilesize * 18f)) pursuer.damage(120f);
        }
        Fx.shockwave.at(owner);
        ui.showInfoToast(Core.bundle.format("pinyin.world.countered", index + 1), 3f);
        Sounds.explosion.play();
    }

    private void finish(String message){
        stop();
        Core.app.post(() -> {
            state.set(GameState.State.menu);
            ui.showInfoFade(message);
        });
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

    private void floating(float x, float y, String text, Color color){
        if(feedback.size >= 64) feedback.remove(0);
        feedback.add(new PickupFeedback(x, y + 12f, text, color));
    }

    private void dashMovement(Unit unit, int index){
        if(!controlsEnabled() || !momentum[index].dashing()) return;
        Vec2 direction = Tmp.v1.set(unit.vel);
        if(direction.isZero()) direction.trns(unit.rotation, 1f);
        unit.movePref(direction.setLength(unit.speed() * 2.4f));
    }

    private void updateFunEvents(){
        feedback.removeAll(popup -> (popup.time -= Time.delta) <= 0f);
        for(int i = 0; i < 2; i++){
            momentum[i].update(Time.delta);
            Unit unit = i == 0 ? firstPlayer : partner;
            if(alive(unit) && controlsEnabled() && (Core.input.keyTap(i == 0 ? KeyCode.space : KeyCode.shiftRight) || i == 1 && PinyinGamepad.state.dashTap) && momentum[i].dash()){
                unit.apply(StatusEffects.invincible, 30f);
                reviveProtection[i] = Math.max(reviveProtection[i], 30f);
                Fx.shockwave.at(unit);
                floating(unit.x, unit.y, Core.bundle.get("pinyin.adventure.dash"), Pal.lancerLaser);
            }
        }
        supplyTimer -= Time.delta;
        if(supplyTimer > 0f || anyQuestionActive()) return;
        supplyTimer = 60f * Mathf.random(25f, 35f);
        for(Unit unit : new Unit[]{firstPlayer, partner}){
            if(!alive(unit)) continue;
            Vec2[] places = PinyinWorldQuestion.placeOptions(world.tiles, adventure.stage(), unit, null);
            if(places == null) continue;
            for(int i = 0; i < places.length; i++){
                SupplyDrop drop = new SupplyDrop(places[i].x, places[i].y, i == 0 ? 0 : i + 3, 15);
                drop.time = 60f * 12f;
                drop.limited = true;
                drops.add(drop);
                Fx.spawn.at(drop.x, drop.y);
            }
            floating(unit.x, unit.y + 30f, Core.bundle.get("pinyin.adventure.supplyArrived"), Pal.accent);
        }
        Sounds.uiUnlock.play();
    }

    private class PartnerController extends AIController{
        @Override
        public void updateTargeting(){
            // Player 2 aims and fires from the mouse button instead of auto-targeting.
        }

        @Override
        public void updateMovement(){
            if(unit == null) return;
            PinyinPlayerInput.update(unit, true, nearestPursuer(unit), controlsEnabled(), !Core.scene.hasMouse());
            dashMovement(unit, 1);
        }
    }

    private class DroneController extends AIController{
        @Override
        public void updateTargeting(){
            Unit enemy = nearestPursuer(unit);
            Unit anchor = nearestPlayer(unit);
            Unit target = enemy != null && unit.within(enemy, unit.range()) ? enemy : anchor;
            boolean shoot = target != null && unit.within(target, unit.range()) && (target == enemy || target.damaged());
            if(target != null) unit.aimLook(target);
            unit.controlWeapons(shoot, shoot);
        }

        @Override
        public void updateMovement(){
            Unit anchor = nearestPlayer(unit);
            if(anchor != null) moveTo(anchor, tilesize * 4f);
        }
    }

    private static class SupplyDrop{
        final float x, y;
        final int kind, amount;
        float time = 60f * 45f;
        boolean limited;

        SupplyDrop(float x, float y, int kind, int amount){
            this.x = x;
            this.y = y;
            this.kind = kind;
            this.amount = amount;
        }
    }

    private static class PickupFeedback{
        final float x, y;
        final String text;
        final Color color;
        float time = 75f;

        PickupFeedback(float x, float y, String text, Color color){
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
        }
    }


}

