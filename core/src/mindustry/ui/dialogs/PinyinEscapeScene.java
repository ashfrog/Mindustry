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
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import java.util.Arrays;

import static mindustry.Vars.*;

/** A real Mindustry map that turns exploration and pursuit into pinyin practice. */
public class PinyinEscapeScene{
    private final PinyinDefenseScene defense = new PinyinDefenseScene();
    private static final int WIDTH = PinyinAdventureMap.width, HEIGHT = PinyinAdventureMap.height;
    private static final int AIR_WIDTH = PinyinAirBattleMap.width, AIR_HEIGHT = PinyinAirBattleMap.height;
    private static final int ENERGY_NEEDED = 3;
    private static final int MAX_VISIBLE_PAYLOADS = 6;
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
    private final Table missionHud = new Table();
    private final Seq<Unit> pursuers = new Seq<>();
    private final Seq<Unit> allies = new Seq<>();
    private final Seq<SupplyDrop> drops = new Seq<>();
    private final Seq<PinyinRescuePayload> pinyinPayloads = new Seq<>();
    private final PinyinAdventureProgress adventure = new PinyinAdventureProgress();
    private final PinyinAdventureEvents sideEvent = new PinyinAdventureEvents();
    private final Vec2 sideEventPoint = new Vec2();
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
    private final float[] healthSnapshot = new float[2];
    private final float[] shieldSnapshot = new float[2];
    private final Vec2[] safePosition = {new Vec2(), new Vec2()};
    private final Vec2 zoneCenter = new Vec2();
    private final PinyinVehicleSystem.Mode[] vehicleModes = {PinyinVehicleSystem.Mode.land, PinyinVehicleSystem.Mode.land};
    private boolean active, worldReady, bossSpawned, campActive;
    private int spawnWave, supplies;
    private final int[] energy = new int[2], pulseCharge = new int[2];
    private final Seq<HelperRecord> carriedHelpers = new Seq<>();
    private final Seq<RecruitRecord> carriedRecruits = new Seq<>();
    private final boolean[] rescued = new boolean[PinyinAdventureMap.stages * 2];
    private final float[] rescueCharge = new float[PinyinAdventureMap.stages * 2];
    private final PinyinStageWorks stageWorks = new PinyinStageWorks();
    private final float[] chestCooldown = new float[2];
    private float bossSignal;
    private float sideEventCooldown;
    private float supplyTimer;
    private float zoneRadius, zoneDamageTimer;
    private float terrainRecoveryCooldown;
    private float previousCameraScale;
    private Unit guardian;
    private final PinyinPronunciation pronunciation = new PinyinPronunciation();
    /** The legacy route data is retained; the live game uses free-roaming survival. */
    private final boolean airMode = true;
    private PinyinAirBattleMap airLayout = new PinyinAirBattleMap(System.nanoTime(), 1);
    private final Seq<AirAnswerPickup> airAnswers = new Seq<>();
    private final AirQuestionBoard[] airBoards = new AirQuestionBoard[2];
    private final float[] questionCooldown = {60f, 300f};
    private float airWaveTimer;
    private int airWave;
    private boolean airBossWarning;
    private float survivalTime;
    private int survivalKills, survivalBosses;
    private final PinyinSurvival.Rounds rounds = new PinyinSurvival.Rounds();
    private final PinyinPlayerSkills skills = new PinyinPlayerSkills();
    private final PinyinFieldWorks fieldWorks = new PinyinFieldWorks();
    private final int[] selectedWorks = new int[2];
    private final float[] miningProgress = new float[2], celebration = new float[2];
    private final float[] lastCorrect = {-10000f, -10000f};
    private float coopCooldown, fieldServiceTimer;
    private String centerNotice = "";
    private Color centerNoticeColor = Pal.accent;
    private float centerNoticeTime;
    private boolean previousHudShown;
    private final Vec2 crowdPush = new Vec2();
    private final Seq<SupplyDrop> dropSnapshot = new Seq<>();

    public PinyinEscapeScene(){
        world.addMapLoader(map, this::generateMap);
        Events.on(WorldLoadEvent.class, event -> {
            if(active && state.map == map){
                worldReady = true;
                pursuers.clear();
                for(PinyinWorldQuestion question : worldQuestions) question.clear();
                reviveProtection[0] = reviveProtection[1] = 0f;
                contactCooldown[0] = contactCooldown[1] = 0f;
                if(airMode){
                    vehicleModes[0] = vehicleModes[1] = PinyinVehicleSystem.Mode.air;
                    firstPlayer = spawnPlayer(false, AIR_WIDTH * tilesize * 0.5f - 30f, AIR_HEIGHT * tilesize * 0.5f);
                    partner = spawnPlayer(true, AIR_WIDTH * tilesize * 0.5f + 30f, AIR_HEIGHT * tilesize * 0.5f);
                    healthSnapshot[0] = firstPlayer.health;
                    healthSnapshot[1] = partner.health;
                    Arrays.fill(airBoards, null);
                    airAnswers.clear();
                    questionCooldown[0] = 60f;
                    questionCooldown[1] = 300f;
                    airWave = 0;
                    rounds.reset();
                    airWaveTimer = PinyinSurvival.interval(1);
                    airBossWarning = false;
                    spawnAirWave();
                    ui.hudfrag.shown = false;
                    prepareMiningGround();
                    notice("P1：K牵引 / L大招 · R切换 / E建造 / F采矿\nP2：逗号/X牵引 / 句号/Y大招 · Enter建造 / 右Ctrl采矿", Pal.accent, 12f);
                    ui.hudGroup.addChild(hud);
                    hud.toFront();
                    ui.hudGroup.addChild(missionHud);
                    missionHud.toFront();
                    return;
                }
                int mapStage = mapStage();
                Point2 entry = layout.entrances[mapStage];
                firstPlayer = spawnPlayer(false, entry.x * tilesize, entry.y * tilesize);
                partner = spawnPlayer(true, (entry.x + 2) * tilesize, entry.y * tilesize);
                healthSnapshot[0] = firstPlayer.health;
                healthSnapshot[1] = partner.health;
                shieldSnapshot[0] = firstPlayer.shield;
                shieldSnapshot[1] = partner.shield;
                zoneCenter.set((layout.leftBoundary() + layout.rightBoundary()) * 0.5f * tilesize, HEIGHT * 0.5f * tilesize);
                zoneRadius = Math.max(layout.rightBoundary() - layout.leftBoundary(), HEIGHT) * tilesize * 0.58f;
                zoneDamageTimer = 60f;
                spawnFixedEnemies();
                stageWorks.reset(mapStage, adventure.chapter(), layout.workshops[mapStage]);
                vehicleModes[0] = vehicleModes[1] = PinyinVehicleSystem.Mode.land;
                for(HelperRecord record : carriedHelpers) spawnHelper(record.kind, record.owner);
                for(RecruitRecord record : carriedRecruits) spawnRecruit(record.kind, record.owner);
                carriedHelpers.clear();
                carriedRecruits.clear();
                // Map loading rebuilds the scene tree; attach the adventure HUD again afterwards.
                ui.hudGroup.addChild(hud);
                hud.toFront();
                ui.hudGroup.addChild(missionHud);
                missionHud.toFront();
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
        missionHud.setFillParent(true);
        missionHud.touchable = Touchable.disabled;
        missionHud.visible(() -> active && state.isGame());
        ui.hudGroup.addChild(missionHud);
        try{
            portraitTexture = new Texture("sprites/pinyin-player.png");
            portraitRegion = new TextureRegion(portraitTexture);
        }catch(Throwable error){
            Log.warn("Unable to load pinyin player portrait", error);
        }
        buildHud();
    }

    public void start(){
        defense.start();
    }

    private void loadChapter(){
        worldReady = false;
        bossSpawned = false;
        guardian = null;
        campActive = false;
        spawnWave = 0;
        bossSignal = 0f;
        carriedHelpers.clear();
        for(Unit helper : allies) if(alive(helper) && helper.controller() instanceof DroneController ai) carriedHelpers.add(new HelperRecord(ai.kind, ai.owner));
        for(Unit helper : allies) if(alive(helper) && helper.controller() instanceof RecruitController ai) carriedRecruits.add(new RecruitRecord(ai.kind, ai.owner));
        allies.clear();
        if(airMode){
            airLayout = new PinyinAirBattleMap(System.nanoTime() + adventure.stage() * 7919L, adventure.chapter());
            airAnswers.clear();
            Arrays.fill(airBoards, null);
            airWave = 0;
            airWaveTimer = PinyinSurvival.interval(1);
            airBossWarning = false;
        }
        layout = new PinyinAdventureMap(System.nanoTime(), adventure.stage(), adventure.chapter());
        for(int i = 0; i < collected.length; i++){ collected[i] = false; chestRecharge[i] = 0f; }
        Arrays.fill(reservedChests, false);
        Arrays.fill(rescued, false);
        Arrays.fill(rescueCharge, 0f);
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
        rules.unitCap = airMode ? PinyinSurvival.maxEnemies + 16 : 120;
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
        skills.reset();
        pronunciation.stop();
        ui.hudfrag.shown = previousHudShown;
        renderer.setScale(previousCameraScale);
        active = false;
        worldReady = false;
        pursuers.clear();
        allies.clear();
        drops.clear();
        pinyinPayloads.clear();
        airAnswers.clear();
        Arrays.fill(airBoards, null);
        for(PinyinWorldQuestion question : worldQuestions) question.clear();
        if(alive(firstPlayer)) firstPlayer.remove();
        if(alive(partner)) partner.remove();
        firstPlayer = partner = null;
        for(PinyinTeamEffects effects : playerEffects) effects.reset();
        feedback.clear();
    }

    private int mapStage(){ return layout.stageIndex(); }

    private void buildHud(){
        missionHud.clearChildren();
        hud.clearChildren();
        if(airMode) return;
        missionHud.top();
        missionHud.table(Styles.black6, banner -> {
            banner.defaults().pad(3f);
            banner.label(this::modeTitle).color(Pal.accent).row();
            banner.label(this::objectiveText).wrap().width(540f).color(Color.white);
        }).padTop(8f);
        hud.clearChildren();
        hud.top().left();
        hud.table(Styles.black6, info -> {
            info.defaults().left().pad(5f);
            info.table(avatar -> {
                avatar.image(Icon.play).size(26f).padRight(6f);
                avatar.add(airMode ? "玩家1：WASD移动，自动开火，空格冲刺" : Core.bundle.get("pinyin.world.player1")).color(Color.white).row();
                if(portraitRegion != null){
                    avatar.image(portraitRegion).size(26f).padRight(6f);
                    avatar.add(airMode ? "玩家2：方向键/手柄移动，自动开火，右Shift冲刺" : Core.bundle.get("pinyin.world.player2")).color(Color.white);
                }
            }).row();
            info.label(this::modeTitle).color(Pal.accent).row();
            info.label(() -> PinyinGamepad.state.connected ? Core.bundle.format("pinyin.gamepad.connected", PinyinGamepad.state.name)
                : Core.bundle.get("pinyin.gamepad.waiting")).wrap().width(330f).color(Pal.accent).row();
            info.label(this::objectiveText).wrap().width(330f).color(Color.white).row();
            info.label(() -> Core.bundle.format("pinyin.adventure.supplies", supplies, pursuers.size)).color(Pal.accent).row();
            info.label(() -> powerText(0)).wrap().width(330f).color(Pal.heal).row();
            info.label(() -> powerText(1)).wrap().width(330f).color(Pal.heal).row();
            info.label(() -> Core.bundle.format("pinyin.adventure.personalGoals", momentum[0].streak(), momentum[1].streak(),
                (int)momentum[0].dashSeconds(), (int)momentum[1].dashSeconds())).wrap().width(330f).color(Pal.accent).row();
            info.label(() -> airMode ? "追截队：肉盾挡路 · 快攻追尾 · 两翼包夹"
                : Core.bundle.format("pinyin.adventure.supplySoon", Math.max(0, (int)Math.ceil(supplyTimer / 60f)))).color(Color.lightGray).row();
            info.collapser(t -> t.label(this::questionText).wrap().width(330f).color(Pal.accent), this::anyQuestionActive).row();
            info.label(() -> Core.bundle.format("pinyin.world.lives", lives.remaining(), PinyinCoopLives.startingLives,
                (int)(lives.lifeProgress() * 100f))).color(Pal.accent).row();
            info.collapser(t -> t.label(() -> respawnText(0)).color(Pal.remove), () -> !alive(firstPlayer)).row();
            info.collapser(t -> t.label(() -> respawnText(1)).color(Pal.remove), () -> !alive(partner)).row();
            info.label(() -> airMode ? "P1 WASD · P2 方向键/手柄；自动瞄准开火\n空格 / 右Shift / 手柄冲刺突破包围；拾取拼音升级"
                : Core.bundle.get("pinyin.world.help")).color(Color.lightGray).wrap().width(330f).row();
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
                for(PinyinHelpers.Kind kind : PinyinHelpers.Kind.values()){
                    camp.button(Core.bundle.get("pinyin.helper.short." + kind.name()) + " · 60 " + Items.copper.localizedName,
                        () -> buyHelper(kind)).tooltip(Core.bundle.get("pinyin.helper." + kind.name()))
                        .disabled(button -> supplies < 60 || allies.size >= PinyinCombatBalance.maxHelpers).row();
                }
            }, this::nearCamp).fillX().row();
            info.collapser(t -> t.label(() -> Core.bundle.format("pinyin.adventure.bossHealth", bossName(), alive(guardian) ? (int)guardian.health : 0))
                .color(Pal.remove), () -> alive(guardian)).row();
        }).pad(10f);
    }

    private String stageName(){
        if(airMode) return switch(airLayout.theme()){
            case 0 -> "云海航线";
            case 1 -> "雷暴空域";
            case 2 -> "星港空战";
            default -> "彩虹风暴";
        };
        return Core.bundle.get("pinyin.adventure.theme" + layout.themes[mapStage()]) + " · "
            + Core.bundle.get("pinyin.adventure.lesson" + adventure.stage());
    }

    private String modeTitle(){
        return airMode ? "拼音无尽割草 · " + stageName()
            : Core.bundle.format("pinyin.adventure.stage", adventure.chapter(), adventure.stage() + 1, stageName());
    }

    private String questionText(){
        if(airMode) return "";
        if(!anyQuestionActive()) return "";
        StringBuilder text = new StringBuilder();
        for(int slot = 0; slot < 2; slot++){
            PinyinWorldQuestion board = worldQuestions[slot];
            if(!board.active()) continue;
            PinyinQuestions.Question question = questionFor(board.chestIndex());
            if(text.length() > 0) text.append("\n");
            text.append("P").append(slot + 1).append(" ")
                .append(question.character).append(" · 追踪中 · ").append(question.category);
        }
        return text.toString();
    }

    private boolean anyQuestionActive(){
        if(airMode) return airBoards[0] != null || airBoards[1] != null;
        return worldQuestions[0].active() || worldQuestions[1].active();
    }

    private boolean[] unavailableChests(){
        boolean[] unavailable = Arrays.copyOf(collected, collected.length);
        for(int i = 0; i < unavailable.length; i++) unavailable[i] |= reservedChests[i];
        return unavailable;
    }

    private String powerText(int index){
        PinyinTeamEffects effects = playerEffects[index];
        if(airMode) return "P" + (index + 1) + " 武器 Lv." + effects.power() + " · 威力 ×"
            + Strings.autoFixed(effects.damageMultiplier(), 2) + " · " + Core.bundle.get("pinyin.weapon." + effects.weaponKind().name())
            + "\n个人升级持续累积 · 冲刺 " + (int)momentum[index].dashSeconds() + "秒";
        return Core.bundle.format("pinyin.world.power", index + 1, effects.power(), Strings.autoFixed(effects.damageMultiplier(), 2), effects.bulletsPerShot(), pulseCharge[index]) + "\n"
            + Core.bundle.format("pinyin.world.weapon", Core.bundle.get("pinyin.weapon." + effects.weaponKind().name())) + " · "
            + (effects.positiveBoostsPermanent() ? "救援成长永久保留"
                : effects.secondsLeft() == 0 ? Core.bundle.get("pinyin.world.boostNone")
                : Core.bundle.format("pinyin.world.boostTimer", effects.secondsLeft()));
    }

    private String worksText(){
        return Core.bundle.format("pinyin.works." + stageWorks.kind().name(), (int)(stageWorks.progress() * 100f))
            + " · " + Core.bundle.get(stageWorks.complete() ? "pinyin.works.done" : stageWorks.activated() ? "pinyin.works.active" : "pinyin.works.repair");
    }

    private String objectiveText(){
        if(airMode){
            return "生存 " + (int)(survivalTime / 60f) + "秒 · 第 " + airWave + " 波 · 击杀 " + survivalKills
                + " · 怪物 " + pursuers.size + "/" + PinyinSurvival.enemyLimit(airWave)
                + "\n" + (alive(guardian) ? "精英追截队长：" + bossName()
                    : "下一波 " + (int)Math.ceil(airWaveTimer / 60f) + "秒") + " · 已击败 " + survivalBosses + " 名队长";
        }
        String boss = adventure.bossDefeated() ? "Boss 已击破，准备进入下一关"
            : bossSpawned ? "Boss 已出现：" + bossName() : "沿主路线前进，前往末端 Boss 堡垒";
        String base = boss + "\n携带拼音：" + pinyinPayloads.size
            + " · P1载具 " + vehicleName(vehicleModes[0]) + " · P2载具 " + vehicleName(vehicleModes[1])
            + "\n路线：" + routeProgress()
            + " · 安全区半径：" + Math.max(1, (int)(zoneRadius / tilesize)) + "格";
        String optional = "\n支线：房屋救援、飞船投递、助手和设施均可选";
        return sideEvent.active() ? base + optional + "\n" + eventText() : base + optional;
    }

    private String vehicleName(PinyinVehicleSystem.Mode mode){
        return switch(mode){
            case land -> "陆地";
            case sea -> "海上";
            case air -> "空中";
        };
    }

    private String routeProgress(){
        Point2[] route = layout.routes[mapStage()];
        Unit leader = alive(firstPlayer) ? firstPlayer : partner;
        if(route == null || leader == null) return "入口";
        int nearest = 0;
        float distance = Float.MAX_VALUE;
        for(int i = 0; i < route.length; i++){
            float current = leader.dst2(route[i].x * tilesize, route[i].y * tilesize);
            if(current < distance){ distance = current; nearest = i; }
        }
        return (nearest + 1) + "/" + route.length;
    }

    private String eventText(){
        if(!sideEvent.active()) return "";
        return switch(sideEvent.kind()){
            case hunt -> Core.bundle.format("pinyin.event.hunt", sideEvent.count(), sideEvent.target(), sideEvent.secondsLeft());
            case rescue -> Core.bundle.format("pinyin.event.rescue", (int)(sideEvent.progress() * 100f), sideEvent.secondsLeft());
            case rhythm -> Core.bundle.format("pinyin.event.rhythm", sideEvent.count(), sideEvent.target(), sideEvent.secondsLeft());
            case survive -> Core.bundle.format("pinyin.event.survive", sideEvent.secondsLeft());
            case convoy -> "补给车队：" + sideEvent.count() + "/" + sideEvent.target() + " · " + sideEvent.secondsLeft() + "秒";
        };
    }

    private void generateMap(){
        state.map = map;
        if(airMode) world.loadGenerator(AIR_WIDTH, AIR_HEIGHT, airLayout::generate);
        else world.loadGenerator(WIDTH, HEIGHT, layout::generate);
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
        int slot = second ? 1 : 0;
        Unit unit = PinyinVehicleSystem.spawn(vehicleModes[slot], player.team(), x, y);
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

    private void drawAirBattle(){
        float cx = Core.camera.position.x, cy = Core.camera.position.y;
        Draw.z(Layer.floor + 2f);
        // Render seeded cloud banks as a second depth layer over the generated
        // floors. They drift slightly so the terrain feels alive while scrolling.
        int minX = Math.max(2, World.toTile(cx - Core.camera.width * 0.55f));
        int maxX = Math.min(AIR_WIDTH - 3, World.toTile(cx + Core.camera.width * 0.55f));
        int minY = Math.max(2, World.toTile(cy - Core.camera.height * 0.55f));
        int maxY = Math.min(AIR_HEIGHT - 3, World.toTile(cy + Core.camera.height * 0.55f));
        Draw.color(Color.white, 0.08f);
        for(int x = minX; x <= maxX; x += 3) for(int y = minY; y <= maxY; y += 3){
            if(!airLayout.cloud(x, y)) continue;
            float px = x * tilesize + Mathf.sin(Time.time * 0.012f + y) * 4f;
            float py = y * tilesize;
            Fill.circle(px, py, tilesize * (1.6f + Mathf.absin(x + y, 0.5f)));
        }

        Draw.z(Layer.playerName + 12f);
        for(int slot = 0; slot < 2; slot++){
            AirQuestionBoard board = airBoards[slot];
            if(board == null) continue;
            Color color = slot == 0 ? Pal.accent : Pal.lancerLaser;
            float qx = cx + (slot == 0 ? -1 : 1) * Core.camera.width * 0.15f;
            float qy = cy + Core.camera.height * 0.28f;
            Draw.color(color, 0.18f);
            Fill.circle(qx, qy - 8f, 32f + Mathf.absin(5f, 3f));
            Fonts.outline.draw((slot == 0 ? "金色题目" : "蓝色题目"), qx, qy + 32f, color, 0.42f, false, Align.center);
            Fonts.outline.draw(board.question.character, qx, qy + 10f, Color.white, 1.1f, false, Align.center);
            Fonts.outline.draw("谁碰到谁作答 · 拾取即消失",
                qx, qy - 32f, color, 0.32f, false, Align.center);
        }
        for(AirAnswerPickup answer : airAnswers){
            AirQuestionBoard board = answer.board;
            Color color = board.access.owner == 0 ? Pal.accent : Pal.lancerLaser;
            float pulse = 1f + Mathf.absin(6f, 2f);
            Draw.color(color, 0.22f);
            Fill.circle(answer.x, answer.y, 19f + pulse);
            Draw.color(color);
            Lines.stroke(2.5f);
            Lines.circle(answer.x, answer.y, 22f + pulse);
            Lines.circle(answer.x, answer.y, 27f + pulse * 0.5f);
            Fonts.outline.draw(answer.text, answer.x, answer.y + 8f, Color.white, 0.7f, false, Align.center);
            Fonts.outline.draw("题目 · " + board.question.character, answer.x, answer.y + 38f, color, 0.35f, false, Align.center);
            Fonts.outline.draw("任意玩家可拾取",
                answer.x, answer.y - 21f, color, 0.28f, false, Align.center);
            if(answer.correct && board.access.age > 480f) Draw.rect(Icon.star.getRegion(), answer.x + 27f, answer.y + 18f, 10f, 10f, Time.time);
            Drawf.light(answer.x, answer.y, 80f, color, 0.9f);
        }
        for(SupplyDrop drop : drops){
            Color color = dropColor(drop.kind);
            Draw.color(color, 0.25f);
            Fill.circle(drop.x, drop.y, 9f + Mathf.absin(5f, 2f));
            Draw.color(color);
            Lines.circle(drop.x, drop.y, 10f + Mathf.absin(5f, 2f));
            Fonts.outline.draw(dropIcon(drop.kind), drop.x, drop.y + 6f, Color.white, 0.55f, false, Align.center);
        }
        for(Unit enemy : pursuers){
            if(alive(enemy) && enemy.controller() instanceof PinyinEnemyController ai) ai.drawWarning();
            if(alive(enemy) && enemy.controller() instanceof PinyinSurvivalController ai
                && (ai.role == PinyinSurvival.Role.tank || ai.role == PinyinSurvival.Role.runner || ai.role == PinyinSurvival.Role.blocker)){
                Fonts.outline.draw(ai.role.label, enemy.x, enemy.y - enemy.hitSize - 3f,
                    ai.role == PinyinSurvival.Role.runner ? Pal.lancerLaser : Pal.lightOrange, 0.27f, false, Align.center);
            }
        }
        if(alive(guardian)) drawBossPresence();
        for(int i = 0; i < 2; i++){
            Unit unit = i == 0 ? firstPlayer : partner;
            if(!alive(unit)) continue;
            Color color = i == 0 ? Pal.accent : Pal.lancerLaser;
            Draw.color(color);
            Lines.circle(unit.x, unit.y, unit.hitSize * 0.9f + 4f + Mathf.absin(7f, 2f));
            drawHealthBar(unit, color, 40f);
            Fonts.outline.draw("P" + (i + 1), unit.x, unit.y + unit.hitSize + 11f, color, 0.45f, false, Align.center);
            if(skills.ultimateTime(i) > 0f){
                Draw.color(Pal.lancerLaser);
                Lines.stroke(3f);
                Lines.poly(unit.x, unit.y, 8, 28f + Mathf.absin(3f, 4f), -Time.time * 3f);
                Drawf.light(unit.x, unit.y, 130f, Pal.lancerLaser, 0.8f);
            }
            if(skills.pulling(i)){
                Unit other = i == 0 ? partner : firstPlayer;
                if(alive(other)){
                    Draw.color(Pal.lightOrange);
                    Lines.stroke(2f + Mathf.absin(3f, 1f));
                    Lines.line(unit.x, unit.y, other.x, other.y);
                    Lines.poly(other.x, other.y, 3, 16f, Time.time * 4f);
                }
            }
            String skillLabel = skills.ultimateTime(i) > 0f ? "爆发 " + (int)Math.ceil(skills.ultimateTime(i) / 60f) + "秒"
                : skills.cooldown(i) > 0f ? "大招 " + (int)Math.ceil(skills.cooldown(i) / 60f) + "秒" : (i == 0 ? "L 大招就绪" : ". / Y 大招就绪");
            Fonts.outline.draw(skillLabel, unit.x, unit.y - 29f, Pal.lancerLaser, 0.29f, false, Align.center);
            if(celebration[i] > 0f){
                float glow = Math.min(1f, celebration[i] / 35f);
                Draw.color(Pal.accent, glow * 0.2f);
                Fill.circle(unit.x, unit.y, 32f + Mathf.absin(4f, 6f));
                Draw.color(Color.white, glow * 0.85f);
                if(i == 1 && portraitRegion != null) Draw.rect(portraitRegion, unit.x, unit.y, 38f, 38f);
                else Draw.rect(unit.type.fullIcon, unit.x, unit.y, 42f, 42f, unit.rotation - 90f);
                Draw.color(Pal.accent);
                Lines.stroke(2f);
                Lines.poly(unit.x, unit.y, 6, 31f + Mathf.absin(3f, 5f), Time.time * 2f);
                Drawf.light(unit.x, unit.y, 150f, Pal.accent, glow * 0.85f);
                Fonts.outline.draw("★ P" + (i + 1) + " 太棒啦！Lv." + playerEffects[i].power() + " ★", unit.x, unit.y + 53f, Pal.accent, 0.65f, false, Align.center);
            }
        }
        drawFieldWorks();
        if(alive(firstPlayer) && alive(partner) && firstPlayer.within(partner, 100f)){
            Draw.color(Pal.heal, 0.5f);
            Lines.stroke(1.5f);
            Lines.line(firstPlayer.x, firstPlayer.y, partner.x, partner.y);
        }
        for(Unit enemy : pursuers) if(alive(enemy)) drawHealthBar(enemy,
            enemy == guardian ? Pal.remove : Pal.lightOrange, enemy == guardian ? 70f : 30f);
        Fonts.outline.draw("第" + rounds.round() + "轮 · " + rounds.wave() + "/10波 · "
            + (rounds.bossActive() ? "击败Boss进入下一轮" : "下一波 " + (int)Math.ceil(airWaveTimer / 60f) + "秒")
            + " · 铜 " + supplies + " · 生命 " + lives.remaining(),
            cx, cy + Core.camera.height * 0.46f, Pal.accent, 0.40f, false, Align.center);
        if(centerNoticeTime > 0f) Fonts.outline.draw(centerNotice, cx, cy + Core.camera.height * 0.39f,
            centerNoticeColor, 0.48f, false, Align.center);
        if(airBossWarning && !bossSpawned)
            Fonts.outline.draw("⚠ 精英追截队正在接近 ⚠", cx, cy + Core.camera.height * 0.37f,
                Pal.remove, 0.55f + Mathf.absin(5f, 0.08f), false, Align.center);
        for(PickupFeedback popup : feedback){
            Fonts.outline.draw(popup.text, popup.x, popup.y + (75f - popup.time) * 0.35f,
                Tmp.c1.set(popup.color).a(Math.min(1f, popup.time / 20f)), 0.6f, false, Align.center);
        }
        Draw.reset();
    }

    private boolean alive(Unit unit){
        return unit != null && !unit.dead && unit.isAdded();
    }

    private String respawnText(int index){
        return Core.bundle.format("pinyin.world.respawning", index + 1, lives.secondsLeft(index));
    }

    private void revive(int index){
        if(airMode){
            Unit survivor = index == 0 ? partner : firstPlayer;
            float x = alive(survivor) ? survivor.x + (index == 0 ? -18f : 18f) : safePosition[index].x;
            float y = alive(survivor) ? survivor.y : safePosition[index].y;
            Unit unit = spawnPlayer(index == 1, Mathf.clamp(x, 16f, AIR_WIDTH * tilesize - 16f),
                Mathf.clamp(y, 16f, AIR_HEIGHT * tilesize - 16f));
            if(index == 0) firstPlayer = unit;
            else partner = unit;
            healthSnapshot[index] = unit.health;
            shieldSnapshot[index] = unit.shield;
            lives.revived(index);
            reviveProtection[index] = 60f * 2f;
            unit.apply(StatusEffects.invincible, reviveProtection[index]);
            Fx.spawn.at(unit);
            floating(unit.x, unit.y, "P" + (index + 1) + " 空中复活", Pal.heal);
            return;
        }
        Unit survivor = index == 0 ? partner : firstPlayer;
        Tile destination = null;
        // Search a close, clear tile; keep respawns away from walls, chests and pursuers.
        int originX = survivor == null ? World.toTile(safePosition[index == 0 ? 1 : 0].x) : survivor.tileX();
        int originY = survivor == null ? World.toTile(safePosition[index == 0 ? 1 : 0].y) : survivor.tileY();
        if(originX <= 0){
            Point2 entry = layout.entrances[mapStage()];
            originX = entry.x;
            originY = entry.y;
        }
        for(int radius = 1; radius <= 5 && destination == null; radius++){
            for(int dx = -radius; dx <= radius && destination == null; dx++){
                for(int dy = -radius; dy <= radius; dy++){
                    if(Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    Tile tile = world.tile(originX + dx, originY + dy);
                    int stageLeft = layout.leftBoundary();
                    if(tile == null || tile.x < stageLeft + 2 || tile.x > layout.rightBoundary() - 2 || tile.y < 3 || tile.y >= HEIGHT - 3
                        || !PinyinVehicleSystem.canEnter(vehicleModes[index], tile)) continue;
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
        float fallbackX = survivor == null ? safePosition[index == 0 ? 1 : 0].x : survivor.x;
        float fallbackY = survivor == null ? safePosition[index == 0 ? 1 : 0].y : survivor.y;
        Unit unit = spawnPlayer(index == 1, destination == null ? fallbackX : destination.worldx(),
            destination == null ? fallbackY : destination.worldy());
        if(index == 0) firstPlayer = unit;
        else partner = unit;
        healthSnapshot[index] = unit.health;
        shieldSnapshot[index] = unit.shield;
        lives.revived(index);
        reviveProtection[index] = 60f * 2f;
        unit.apply(StatusEffects.invincible, reviveProtection[index]);
        Fx.spawn.at(unit);
        if(airMode) notice(Core.bundle.format("pinyin.world.revived", index + 1), Pal.heal, 3f);
        else ui.showInfoToast(Core.bundle.format("pinyin.world.revived", index + 1), 2f);
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
        if(!active || !worldReady || !state.isGame()) return;
        if(airMode){
            drawAirBattle();
            return;
        }
        int stage = mapStage(), start = stage * PinyinAdventureMap.chestsPerStage;
        // Learning text and navigation must remain readable above the night lighting.
        Draw.z(Layer.playerName + 1f);
        Draw.color(Color.valueOf("71d7ff"), 0.7f);
        Lines.stroke(3f + Mathf.absin(8f, 1f));
        Lines.circle(zoneCenter.x, zoneCenter.y, zoneRadius);
        Fonts.outline.draw("安全区", zoneCenter.x, zoneCenter.y - zoneRadius - 10f, Color.valueOf("9fe8ff"), 0.38f, false, Align.center);
        drawSideEvent();
        drawStageFeatures();
        drawRoute();
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
        Point2 camp = layout.camps[stage], beacon = layout.beacons[stage], ship = layout.ships[stage];
        Point2 bossGate = layout.bossGates[stage], bossArena = layout.bossArenas[stage];
        Point2 garage = layout.garages[stage], dock = layout.docks[stage], airfield = layout.airfields[stage];
        drawLayeredMarker(beacon, Pal.accent, 8f);
        drawLayeredMarker(camp, Pal.heal, 6f);
        drawLayeredMarker(ship, Pal.accent, 5f);
        drawLayeredMarker(garage, Pal.heal, 4f);
        drawLayeredMarker(dock, Pal.accent, 4f);
        drawLayeredMarker(airfield, Pal.lancerLaser, 4f);
        Fonts.outline.draw("车库", garage.x * tilesize, garage.y * tilesize + 28f, Pal.heal, 0.35f, false, Align.center);
        Fonts.outline.draw("船坞", dock.x * tilesize, dock.y * tilesize + 28f, Pal.accent, 0.35f, false, Align.center);
        Fonts.outline.draw("机场", airfield.x * tilesize, airfield.y * tilesize + 28f, Pal.lancerLaser, 0.35f, false, Align.center);
        Draw.rect(Blocks.launchPad.fullIcon, ship.x * tilesize, ship.y * tilesize, 28f, 28f);
        Fonts.outline.draw("飞船 · 投递拼音 " + adventure.delivered() + "/" + adventure.requiredDeliveries(),
            ship.x * tilesize, ship.y * tilesize + 34f, Pal.accent, 0.4f, false, Align.center);
        drawLayeredMarker(bossGate, Pal.remove, 7f);
        drawLayeredMarker(bossArena, Pal.remove, 12f);
        Fonts.outline.draw(bossSpawned ? "⚠ BOSS 战场" : "前方 Boss 堡垒", bossGate.x * tilesize,
            bossGate.y * tilesize + 32f, Pal.remove, 0.45f, false, Align.center);
        Draw.color(Pal.heal);
        Lines.dashCircle(camp.x * tilesize, camp.y * tilesize, tilesize * 6f);
        Fonts.outline.draw(Core.bundle.get(campActive ? "pinyin.adventure.campActiveMarker" : "pinyin.adventure.campMarker"),
            camp.x * tilesize, camp.y * tilesize + 42f, Pal.heal, 0.35f, false, Align.center);
        for(SupplyDrop drop : drops){
            Draw.color(dropColor(drop.kind));
            Fill.square(drop.x, drop.y, 3f + Mathf.absin(8f, 1f), 45f);
            Drawf.light(drop.x, drop.y, 28f, Draw.getColor(), 0.7f);
            if(drop.kind < 4){
                Draw.color();
                Draw.rect(Items.copper.fullIcon, drop.x, drop.y, 10f, 10f);
            }else{
                Fonts.outline.draw(dropIcon(drop.kind),
                    drop.x, drop.y + 5f, Color.white, 0.4f, false, Align.center);
            }
            if(drop.limited) Fonts.outline.draw((int)Math.ceil(drop.time / 60f) + "s", drop.x, drop.y + 12f,
                Pal.accent, 0.4f, false, Align.center);
        }
        int visiblePayloads = 0;
        for(PinyinRescuePayload payload : pinyinPayloads){
            if(visiblePayloads++ >= MAX_VISIBLE_PAYLOADS) continue;
            Draw.color(Pal.heal);
            Fill.circle(payload.position.x, payload.position.y, 8f + Mathf.absin(6f, 2f));
            Draw.color(Color.white);
            Fonts.outline.draw(payload.character + "  " + payload.pinyin, payload.position.x,
                payload.position.y + 18f, Color.white, 0.5f, false, Align.center);
        }
        for(Unit enemy : pursuers){
            if(alive(enemy) && enemy.controller() instanceof PinyinEnemyController ai) ai.drawWarning();
        }
        if(alive(guardian)) drawBossPresence();
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
            Point2 goal = bossSpawned ? bossArena : !pinyinPayloads.isEmpty() ? ship : bossGate;
            if(!bossSpawned && adventure.answers() == 0){
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

    private Color dropColor(int kind){
        return kind < 4 ? Pal.accent : switch(kind){
            case 4, 9, 16 -> Pal.heal;
            case 5, 12 -> Pal.lancerLaser;
            case 6, 10, 18, 19 -> Pal.accent;
            case 7, 15 -> Pal.remove;
            case 8, 17 -> Pal.surge;
            case 11 -> Color.valueOf("ffd166");
            case 13 -> Color.valueOf("c792ea");
            case 14 -> Color.valueOf("9fe8ff");
            case 20 -> Color.valueOf("ff8a65");
            case 21 -> Color.valueOf("8dffef");
            case 22, 23 -> Pal.heal;
            default -> Color.valueOf("ff9f68");
        };
    }

    private String dropIcon(int kind){
        if(kind < 4) return "";
        return switch(kind){
            case 4 -> "♥";
            case 5 -> "◆";
            case 6 -> "+15s";
            case 7 -> "+1";
            case 8 -> "⚡";
            case 9 -> "⚡+";
            case 10 -> "✈";
            case 11 -> "⛵";
            case 12 -> "☁";
            case 13 -> "☄";
            case 14 -> "✦";
            case 15 -> "❄";
            case 16 -> "✚";
            case 17 -> "★";
            case 18 -> "◎";
            case 19 -> "✹";
            case 20 -> "⬆";
            case 21 -> "✧";
            case 22 -> "✈+";
            case 23 -> "全对";
            default -> "⚙";
        };
    }

    private String dropName(int kind){
        return switch(kind){
            case 4 -> Core.bundle.get("pinyin.adventure.healPickup");
            case 5 -> Core.bundle.get("pinyin.adventure.shieldPickup");
            case 6 -> "答题增益延长15秒";
            case 7 -> "额外生命 +1";
            case 8 -> "疾跑能量 +8秒";
            case 9 -> "超速燃料 +18秒";
            case 10 -> airMode ? "急速火力升级" : "空中载具";
            case 11 -> airMode ? "随机支援僚机" : "小皮艇";
            case 12 -> "烟雾护身 +5秒";
            case 13 -> "火力超频 +12秒";
            case 14 -> "磁力搜刮 +25";
            case 15 -> "冰冻脉冲";
            case 16 -> "团队维修";
            case 17 -> "电弧诱饵";
            case 18 -> "补给箱 +60";
            case 19 -> "原版武器箱";
            case 20 -> "巨型机体";
            case 21 -> "迷你机体";
            case 22 -> "僚机加入";
            case 23 -> "全对增益";
            default -> "特殊道具";
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

    private void drawSideEvent(){
        if(!sideEvent.active()) return;
        float x = sideEventPoint.x, y = sideEventPoint.y;
        Color color = sideEvent.kind() == PinyinAdventureEvents.Kind.hunt ? Pal.remove
            : sideEvent.kind() == PinyinAdventureEvents.Kind.rescue ? Pal.heal
            : sideEvent.kind() == PinyinAdventureEvents.Kind.rhythm ? Pal.accent
            : sideEvent.kind() == PinyinAdventureEvents.Kind.convoy ? Color.valueOf("ffd166") : Pal.lancerLaser;
        Draw.z(Layer.playerName + 14f);
        Draw.color(color, 0.14f);
        Fill.circle(x, y, tilesize * (sideEvent.kind() == PinyinAdventureEvents.Kind.rescue ? 9f
            : sideEvent.kind() == PinyinAdventureEvents.Kind.convoy ? 7f : 5f));
        Draw.color(color);
        Lines.stroke(2.5f);
        Lines.circle(x, y, tilesize * (sideEvent.kind() == PinyinAdventureEvents.Kind.rescue ? 8f
            : sideEvent.kind() == PinyinAdventureEvents.Kind.convoy ? 6f : 4f) + Mathf.absin(6f, 2f));
        Lines.circle(x, y, tilesize * 10f + Mathf.absin(8f, 3f));
        String icon = sideEvent.kind() == PinyinAdventureEvents.Kind.hunt ? "⚔" : sideEvent.kind() == PinyinAdventureEvents.Kind.rescue ? "♥"
            : sideEvent.kind() == PinyinAdventureEvents.Kind.rhythm ? "♪" : sideEvent.kind() == PinyinAdventureEvents.Kind.convoy ? "▣" : "⚡";
        Fonts.outline.draw(icon + " " + eventText(), x, y + 12f * tilesize, color, 0.45f, false, Align.center);
        Drawf.light(x, y, tilesize * 12f, color, 0.7f);
        Draw.reset();
    }

    private PinyinHelpers.Kind rescueKind(int index){
        return PinyinHelpers.Kind.values()[Math.floorMod(index + adventure.chapter() - 1, PinyinHelpers.Kind.values().length)];
    }

    private void drawStageFeatures(){
        Point2 workshop = stageWorks.point();
        Draw.z(Layer.playerName + 8f);
        Color color = stageWorks.complete() ? Pal.heal : Pal.lancerLaser;
        Draw.color(color);
        Lines.stroke(2f);
        Lines.dashCircle(workshop.x * tilesize, workshop.y * tilesize, 7f * tilesize);
        Fonts.outline.draw(worksText(), workshop.x * tilesize, workshop.y * tilesize + 70f, color, 0.4f, false, Align.center);
        if(!stageWorks.activated()){
            Draw.alpha(0.55f);
            Draw.rect(switch(stageWorks.kind()){
                case mine -> Blocks.mechanicalDrill.fullIcon;
                case fortress -> Blocks.duo.fullIcon;
                case grid -> Blocks.largeSolarPanel.fullIcon;
                case siege -> Blocks.swarmer.fullIcon;
            }, workshop.x * tilesize, workshop.y * tilesize + 3f * tilesize, 28f, 28f);
        }
        for(int i = mapStage() * 2; i < mapStage() * 2 + 2; i++){
            if(rescued[i]) continue;
            Point2 site = layout.rescues[i];
            PinyinHelpers.Kind kind = rescueKind(i);
            Draw.color(Pal.heal, 0.7f);
            Draw.rect(Blocks.container.fullIcon, site.x * tilesize, site.y * tilesize, 28f, 28f);
            Draw.color(Color.white);
            Draw.rect(PinyinHelpers.type(kind).fullIcon, site.x * tilesize, site.y * tilesize + 5f, 15f, 15f);
            Lines.square(site.x * tilesize, site.y * tilesize, 15f, 0f);
            Lines.dashCircle(site.x * tilesize, site.y * tilesize, 4f * tilesize);
            Fonts.outline.draw(Core.bundle.get("pinyin.helper." + kind.name()) + " · "
                + Core.bundle.get(allies.size >= PinyinCombatBalance.maxHelpers ? "pinyin.helper.full" : "pinyin.helper.rescue")
                + " " + (int)(rescueCharge[i] / 120f * 100f) + "%",
                site.x * tilesize, site.y * tilesize + 35f, Pal.heal, 0.35f, false, Align.center);
        }
        for(Unit helper : allies){
            if(!alive(helper)) continue;
            if(helper.controller() instanceof DroneController ai){
                Fonts.outline.draw("P" + (ai.owner + 1) + " · " + Core.bundle.get("pinyin.helper.short." + ai.kind.name()),
                    helper.x, helper.y + helper.hitSize + 8f, Pal.heal, 0.3f, false, Align.center);
            }else if(helper.controller() instanceof RecruitController ai){
                Fonts.outline.draw("P" + (ai.owner + 1) + " · 收编 " + ai.kind.name(),
                    helper.x, helper.y + helper.hitSize + 8f, Pal.accent, 0.3f, false, Align.center);
            }
        }
        Draw.reset();
    }

    /** Switches a player at an optional garage, dock or airfield without resetting their run. */
    private boolean switchVehicle(int slot, PinyinVehicleSystem.Mode mode){
        return switchVehicle(slot, mode, true);
    }

    private boolean switchVehicle(int slot, PinyinVehicleSystem.Mode mode, boolean requireFacility){
        Unit old = slot == 0 ? firstPlayer : partner;
        if(!alive(old) || vehicleModes[slot] == mode) return false;
        Point2 marker = requireFacility ? switch(mode){
            case land -> layout.garages[mapStage()];
            case sea -> layout.docks[mapStage()];
            case air -> layout.airfields[mapStage()];
        } : new Point2(World.toTile(old.x), World.toTile(old.y));
        Tile markerTile = world.tile(marker.x, marker.y);
        if(requireFacility && !nearPoint(old, marker, 7f)) return false;
        if(!PinyinVehicleSystem.canEnter(mode, markerTile) && mode == PinyinVehicleSystem.Mode.sea && !requireFacility){
            for(int radius = 1; radius <= 14; radius++){
                boolean found = false;
                for(int dx = -radius; dx <= radius && !found; dx++) for(int dy = -radius; dy <= radius; dy++){
                    Tile candidate = world.tile(marker.x + dx, marker.y + dy);
                    if(PinyinVehicleSystem.canEnter(mode, candidate)){
                        marker = new Point2(candidate.x, candidate.y);
                        markerTile = candidate;
                        found = true;
                        break;
                    }
                }
                if(found) break;
            }
        }
        if(!PinyinVehicleSystem.canEnter(mode, markerTile)) return false;
        float health = old.health / Math.max(1f, old.maxHealth);
        float shield = old.shield / Math.max(1f, old.maxHealth);
        float spawnX = marker.x * tilesize, spawnY = marker.y * tilesize;
        if(mode != PinyinVehicleSystem.Mode.air && !PinyinVehicleSystem.canEnter(mode, world.tileWorld(old.x, old.y))){
            // A dock may be just off the road; place the new hull on the nearest valid tile.
            float nearest = Float.MAX_VALUE;
            for(int radius = 0; radius <= 8; radius++) for(int dx = -radius; dx <= radius; dx++) for(int dy = -radius; dy <= radius; dy++){
                if(Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                Tile candidate = world.tile(marker.x + dx, marker.y + dy);
                if(!PinyinVehicleSystem.canEnter(mode, candidate)) continue;
                float distance = Mathf.dst2(old.x, old.y, candidate.worldx(), candidate.worldy());
                if(distance < nearest){ nearest = distance; spawnX = candidate.worldx(); spawnY = candidate.worldy(); }
            }
        }
        Unit replacement = PinyinVehicleSystem.spawn(mode, Team.sharded, spawnX, spawnY);
        if(replacement == null) return false;
        replacement.rotation = old.rotation;
        replacement.vel.set(old.vel);
        replacement.health = replacement.maxHealth * health;
        replacement.shield = Math.min(PinyinCombatBalance.maxShield, replacement.maxHealth * shield);
        if(slot == 0){
            player.unit(replacement);
            player.set(replacement);
            player.shooting = false;
            firstPlayer = replacement;
        }else{
            replacement.controller(new PartnerController());
            partner = replacement;
        }
        playerEffects[slot].inherit(replacement);
        safePosition[slot].set(replacement);
        old.remove();
        vehicleModes[slot] = mode;
        healthSnapshot[slot] = replacement.health;
        shieldSnapshot[slot] = replacement.shield;
        floating(replacement.x, replacement.y, "切换载具：" + mode.name(), Pal.accent);
        Fx.spawn.at(replacement);
        return true;
    }

    private void updateVehicles(){
        for(int slot = 0; slot < 2; slot++){
            Unit unit = slot == 0 ? firstPlayer : partner;
            if(!alive(unit)) continue;
            if(nearPoint(unit, layout.garages[mapStage()], 6f)) switchVehicle(slot, PinyinVehicleSystem.Mode.land);
            if(nearPoint(unit, layout.docks[mapStage()], 6f)) switchVehicle(slot, PinyinVehicleSystem.Mode.sea);
            if(nearPoint(unit, layout.airfields[mapStage()], 6f)) switchVehicle(slot, PinyinVehicleSystem.Mode.air);
        }
    }

    /** A hit knocks one carried syllable loose; the glowing pickup can be recovered by either player. */
    private void trackPlayerDamage(){
        for(int slot = 0; slot < 2; slot++){
            Unit unit = slot == 0 ? firstPlayer : partner;
            if(!alive(unit)){
                if(healthSnapshot[slot] > 0f) dropPinyin(slot);
                healthSnapshot[slot] = 0f;
                shieldSnapshot[slot] = 0f;
                continue;
            }
            if((healthSnapshot[slot] > 0f && unit.health + 0.5f < healthSnapshot[slot])
                || (shieldSnapshot[slot] > 0f && unit.shield + 0.5f < shieldSnapshot[slot])) dropPinyin(slot);
            healthSnapshot[slot] = unit.health;
            shieldSnapshot[slot] = unit.shield;
        }
    }

    private void updateSafeZone(){
        if(zoneRadius <= 0f) return;
        zoneRadius = Math.max(18f * tilesize, zoneRadius - Time.delta * 0.08f);
        zoneDamageTimer -= Time.delta;
        if(zoneDamageTimer > 0f) return;
        zoneDamageTimer = 60f;
        for(Unit unit : new Unit[]{firstPlayer, partner}) if(alive(unit) && unit.dst(zoneCenter) > zoneRadius){
            unit.damage(4f + PinyinCombatBalance.difficulty(adventure.difficulty()) * 0.6f);
            floating(unit.x, unit.y, "离开安全区", Pal.remove);
        }
    }

    private void dropPinyin(int owner){
        Unit unit = owner == 0 ? firstPlayer : partner;
        float x = unit == null ? safePosition[owner].x : unit.x;
        float y = unit == null ? safePosition[owner].y : unit.y;
        for(int i = pinyinPayloads.size - 1; i >= 0; i--){
            PinyinRescuePayload payload = pinyinPayloads.get(i);
            if(payload.carried() && payload.owner == owner){
                payload.drop(x + Mathf.random(-10f, 10f), y + Mathf.random(-10f, 10f));
                floating(x, y, "拼音掉落：" + payload.character + "，快捡回来！", Pal.remove);
                Fx.blastExplosion.at(x, y);
                return;
            }
        }
    }

    /** Bright breadcrumb route, like a run-and-gun stage map, without blocking movement. */
    private void drawRoute(){
        Point2[] route = layout.routes[mapStage()];
        if(route == null) return;
        Draw.z(Layer.playerName + 3f);
        Draw.color(Pal.accent, 0.38f);
        Lines.stroke(1.5f);
        for(int i = 1; i < route.length; i++){
            Point2 from = route[i - 1], to = route[i];
            Lines.line(from.x * tilesize, from.y * tilesize, to.x * tilesize, to.y * tilesize);
            Fill.circle(to.x * tilesize, to.y * tilesize, 3f + Mathf.absin(6f, 1f));
            if(i == 1 || i == 3 || i == 5 || i == 7 || i == route.length - 1){
                String label = i == 1 ? "营地" : i == 3 ? "救援村" : i == 5 ? "桥梁战区"
                    : i == 7 ? "前线补给" : "Boss堡垒";
                Fonts.outline.draw(label, to.x * tilesize, to.y * tilesize - 12f, Pal.accent, 0.34f, false, Align.center);
            }
        }
        Draw.reset();
    }

    private void updateStageFeatures(){
        boolean completeBefore = stageWorks.complete();
        supplies -= stageWorks.update(Time.delta, supplies, firstPlayer, partner);
        for(int slot = 0; slot < 2; slot++){
            Unit unit = slot == 0 ? firstPlayer : partner;
            if(alive(unit)) pulseCharge[slot] -= stageWorks.depositEnergy(unit, pulseCharge[slot]);
        }
        if(!completeBefore && stageWorks.complete()){
            Point2 point = stageWorks.point();
            floating(point.x * tilesize, point.y * tilesize, Core.bundle.get("pinyin.works.done"), Pal.heal);
            supplies += 25;
        }
        for(int i = mapStage() * 2; i < mapStage() * 2 + 2; i++){
            if(rescued[i]) continue;
            Point2 site = layout.rescues[i];
            // Rescue is a proximity interaction.  Firing is reserved for weapons and
            // must never be required to open a house, otherwise a child can stand on
            // the marker yet fail to start the learning encounter.
            int owner = nearPoint(firstPlayer, site, 4f) ? 0
                : nearPoint(partner, site, 4f) ? 1 : -1;
            rescueCharge[i] = owner >= 0 ? rescueCharge[i] + Time.delta : 0f;
            if(rescueCharge[i] >= 120f){
                rescued[i] = true;
                Point2 house = site;
                int questionIndex = mapStage() * PinyinAdventureMap.chestsPerStage
                    + Math.floorMod(i * 3 + adventure.chapter(), PinyinAdventureMap.chestsPerStage);
                PinyinQuestions.Question question = questionFor(questionIndex);
                PinyinTeamEffects.Reward reward = bunkerRewards[questionIndex];
                PinyinArsenal.Kind weapon = bunkerWeapons[questionIndex];
                Unit rescuer = owner == 0 ? firstPlayer : partner;
                if(alive(rescuer)){
                    pinyinPayloads.add(new PinyinRescuePayload(question.character, question.pinyin, owner,
                        house.x * tilesize, house.y * tilesize, reward, weapon, false));
                    adventure.answer();
                    pronunciation.speak(question.character);
                    floating(house.x * tilesize, house.y * tilesize, "房屋救出拼音：" + question.character + "，快追上它！", Pal.accent);
                }
                if(allies.size < PinyinCombatBalance.maxHelpers) spawnHelper(rescueKind(i), owner);
                Fx.spawn.at(site.x * tilesize, site.y * tilesize);
            }
        }
    }

    private void drawBossPresence(){
        float pulse = 1f + Mathf.absin(7f, 3f);
        Draw.z(Layer.playerName + 16f);
        Draw.color(Pal.remove, 0.15f);
        Fill.circle(guardian.x, guardian.y, guardian.hitSize * 1.5f + pulse * 3f);
        Draw.color(Pal.remove);
        Lines.stroke(3f);
        Lines.circle(guardian.x, guardian.y, guardian.hitSize * 1.45f + pulse * 3f);
        Lines.circle(guardian.x, guardian.y, guardian.hitSize * 1.9f + pulse * 4f);
        Fonts.outline.draw("⚠ BOSS · " + bossName() + " · " + (int)guardian.health + "/" + (int)guardian.maxHealth,
            guardian.x, guardian.y + guardian.hitSize + 26f, Pal.remove, 0.5f, false, Align.center);
        Drawf.light(guardian.x, guardian.y, guardian.hitSize * 4f, Pal.remove, 0.65f);
        if(bossSignal > 0f){
            Draw.color(Pal.remove, bossSignal / (60f * 4f));
            Lines.circle(guardian.x, guardian.y, guardian.hitSize * (2.2f + bossSignal / 45f));
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
        stageWorks.reset(adventure.stage(), adventure.chapter(), layout.workshops[mapStage()]);
        bossSignal = 0f;
        sideEvent.reset();
        sideEventCooldown = 60f * 12f;
        chestCooldown[0] = chestCooldown[1] = 60f * 2f;
    }

    private void drawWorldQuestion(){
        for(int slot = 0; slot < 2; slot++){
            PinyinWorldQuestion board = worldQuestions[slot];
            if(!board.active()) continue;
            PinyinQuestions.Question question = questionFor(board.chestIndex());
            Point2 chest = layout.chests[board.chestIndex()];
            Color playerColor = slot == 0 ? Pal.accent : Pal.lancerLaser;
            Color timerColor = board.hintActive() ? Pal.heal : playerColor;
            Fonts.outline.draw("P" + (slot + 1) + "  " + question.character + " · 追踪中",
                chest.x * tilesize, chest.y * tilesize + 42f, timerColor, 0.7f, false, Align.center);
            for(int i = 0; i < 3; i++){
                Vec2 option = board.positions[i];
                boolean hint = board.hintActive() && board.choices[i] == 0;
                Color optionColor = hint ? Pal.heal : playerColor;
                Draw.z(Layer.playerName + 18f);
                float blink = 0.72f + Mathf.absin(6f, 0.28f);
                Draw.color(optionColor, (hint ? 0.55f : 0.32f) * blink);
                Fill.circle(option.x, option.y, 13f + Mathf.absin(5f, 2f));
                Draw.color(optionColor);
                Lines.stroke(2.5f);
                Lines.circle(option.x, option.y, 15f + Mathf.absin(5f, hint ? 4f : 2.5f));
                Lines.circle(option.x, option.y, 20f + Mathf.absin(4f, 2f));
                Fonts.outline.draw(question.choices[board.choices[i]], option.x, option.y + 8f,
                    Color.white, 0.65f, false, Align.center);
                if(hint) Fill.circle(option.x, option.y + 16f, 2.2f + Mathf.absin(4f, 1f));
                Drawf.light(option.x, option.y, hint ? 80f : 62f, optionColor, hint ? 1f : 0.85f);
            }
        }
        Draw.reset();
    }

    private void drawTurretPads(){
        int start = mapStage() * PinyinAdventureMap.padsPerStage;
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
        for(Unit unit : new Unit[]{firstPlayer, partner}) if(alive(unit)){
            unit.heal(35f);
            unit.shield = Math.min(PinyinCombatBalance.maxShield, unit.shield + 40f);
        }
        adventure.advance();
        // Boss defeat always regenerates one complete oversized map.
        ui.showInfoToast("Boss 已击破，下一关地图生成中", 2f);
        loadChapter();
    }

    private boolean nearCamp(){
        if(airMode) return false;
        return isActive() && (nearPoint(firstPlayer, layout.camps[mapStage()], 8f)
            || nearPoint(partner, layout.camps[mapStage()], 8f));
    }

    private void updateTurretPads(){
        int start = mapStage() * PinyinAdventureMap.padsPerStage;
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
                unit.shield = Math.min(PinyinCombatBalance.maxShield, unit.shield + 50f);
                Fx.heal.at(unit);
            }
        }
        ui.showInfoToast("@pinyin.adventure.repaired", 2f);
    }

    private void buyHelper(PinyinHelpers.Kind kind){
        if(!nearCamp() || supplies < 60 || allies.size >= PinyinCombatBalance.maxHelpers || !state.isPlaying()) return;
        supplies -= 60;
        spawnHelper(kind, alive(firstPlayer) && nearPoint(firstPlayer, layout.camps[mapStage()], 8f) ? 0 : 1);
    }

    private void spawnHelper(PinyinHelpers.Kind kind, int owner){
        if(allies.size >= PinyinCombatBalance.maxHelpers) return;
        Unit anchor = owner == 0 ? firstPlayer : partner;
        if(!alive(anchor)) anchor = alive(firstPlayer) ? firstPlayer : alive(partner) ? partner : null;
        if(anchor == null) return;
        Unit helper = PinyinHelpers.type(kind).spawn(Team.sharded, anchor.x, anchor.y + 3 * tilesize);
        PinyinHelpers.equip(helper, kind);
        helper.controller(new DroneController(kind, owner));
        allies.add(helper);
        floating(helper.x, helper.y, Core.bundle.get("pinyin.helper." + kind.name()), Pal.heal);
    }

    private void spawnRecruit(PinyinEnemyController.Kind kind, int owner){
        if(allies.size >= PinyinCombatBalance.maxHelpers) return;
        Unit anchor = owner == 0 ? firstPlayer : partner;
        if(!alive(anchor)) anchor = alive(firstPlayer) ? firstPlayer : partner;
        if(!alive(anchor)) return;
        Unit recruit = PinyinEnemyController.recruitType(kind).spawn(Team.sharded, anchor.x, anchor.y + 2f * tilesize);
        if(recruit == null) return;
        PinyinEnemyController.equip(recruit, kind, adventure.difficulty());
        // Recruits are helpers, not a second boss: compact hitboxes and bounded
        // stats keep the pair readable and prevent giant vanilla units from
        // overwhelming the world or the renderer.
        recruit.hitSize = Math.min(recruit.hitSize, 10f);
        recruit.maxHealth = Math.min(recruit.maxHealth, 180f);
        recruit.health = recruit.maxHealth;
        recruit.controller(new RecruitController(kind, owner));
        allies.add(recruit);
        floating(recruit.x, recruit.y, "收编小弟：" + kind.name(), Pal.accent);
        Fx.spawn.at(recruit);
    }

    private void updateDrops(){
        // Iterate stable references; magnets and helper collectors can remove
        // live drops without invalidating the cursor or processing one twice.
        dropSnapshot.clear();
        dropSnapshot.addAll(drops);
        for(SupplyDrop drop : dropSnapshot){
            if(!drops.contains(drop, true)) continue;
            drop.time -= Time.delta;
            if(drop.vy != 0f) drop.y += drop.vy * Time.delta;
            Unit collector = alive(firstPlayer) && firstPlayer.within(drop.x, drop.y, 4f * tilesize) ? firstPlayer
                : alive(partner) && partner.within(drop.x, drop.y, 4f * tilesize) ? partner : null;
            if(collector != null){
                boolean consumed = true;
                if(drop.kind < 4){
                    supplies += drop.amount;
                }else if(drop.kind == 4){
                    collector.heal(25f);
                }else if(drop.kind == 5){
                    collector.shield = Math.min(PinyinCombatBalance.maxShield, collector.shield + 30f);
                }else if(drop.kind == 6){
                    int extended = playerEffects[collector == firstPlayer ? 0 : 1].extendBoosts(60f * 15f);
                    if(extended == 0) collector.heal(20f);
                }else if(drop.kind == 7){
                    lives.addLife();
            }else if(drop.kind == 8){
                    collector.apply(StatusEffects.fast, 60f * 8f);
            }else if(drop.kind == 9){
                collector.apply(StatusEffects.fast, 60f * 18f);
            }else if(drop.kind == 10){
                if(airMode){
                    playerEffects[collector == firstPlayer ? 0 : 1].correct(0, false, collector, PinyinTeamEffects.Reward.rapid);
                }else consumed = switchVehicle(collector == firstPlayer ? 0 : 1, PinyinVehicleSystem.Mode.air, false);
            }else if(drop.kind == 11){
                if(airMode) spawnHelper(PinyinHelpers.Kind.values()[Mathf.random(3)], collector == firstPlayer ? 0 : 1);
                else consumed = switchVehicle(collector == firstPlayer ? 0 : 1, PinyinVehicleSystem.Mode.sea, false);
            }else if(drop.kind == 12){
                collector.apply(StatusEffects.invincible, 60f * 5f);
            }else if(drop.kind == 13){
                collector.apply(StatusEffects.overclock, 60f * 12f);
            }else if(drop.kind == 14){
                for(int j = drops.size - 1; j >= 0; j--){
                    SupplyDrop nearby = drops.get(j);
                    if(nearby != drop && !nearby.eventDrop && collector.within(nearby.x, nearby.y, 12f * tilesize)) drops.remove(j);
                }
                supplies += 25;
            }else if(drop.kind == 15){
                for(Unit enemy : pursuers) if(alive(enemy) && enemy.within(collector, 16f * tilesize))
                    enemy.apply(StatusEffects.freezing, 60f * 5f);
                Fx.freezing.at(collector.x, collector.y);
            }else if(drop.kind == 16){
                for(Unit unit : new Unit[]{firstPlayer, partner}) if(alive(unit)){
                    unit.heal(45f);
                    unit.shield = Math.min(PinyinCombatBalance.maxShield, unit.shield + 35f);
                    Fx.heal.at(unit);
                }
            }else if(drop.kind == 17){
                collector.apply(StatusEffects.overclock, 60f * 10f);
                for(Unit enemy : pursuers) if(alive(enemy) && enemy.within(collector, 18f * tilesize))
                    enemy.apply(StatusEffects.sapped, 60f * 6f);
                Fx.lightning.at(collector.x, collector.y);
            }else if(drop.kind == 18){
                supplies += 60;
            }else if(drop.kind == 19){
                int slot = collector == firstPlayer ? 0 : 1;
                PinyinArsenal.Kind weapon = PinyinArsenal.reward(Mathf.random(PinyinArsenal.Kind.values().length - 1));
                playerEffects[slot].correct(0, false, collector, PinyinTeamEffects.Reward.arsenal, weapon);
                floating(collector.x, collector.y, "原版武器箱：" + Core.bundle.get("pinyin.weapon." + weapon.name()), Pal.accent);
            }else if(drop.kind == 20){
                collector.hitSize = Math.min(24f, collector.hitSize * 1.35f);
                collector.heal(18f);
                floating(collector.x, collector.y, "巨型机体 · 子弹更醒目", Pal.accent);
            }else if(drop.kind == 21){
                collector.hitSize = Math.max(7f, collector.hitSize * 0.72f);
                collector.apply(StatusEffects.fast, 60f * 4f);
                floating(collector.x, collector.y, "迷你机体 · 灵活闪避", Pal.lancerLaser);
            }else if(drop.kind == 22){
                int slot = collector == firstPlayer ? 0 : 1;
                spawnHelper(PinyinHelpers.Kind.gunner, slot);
                floating(collector.x, collector.y, "僚机加入！", Pal.heal);
            }else if(drop.kind == 23){
                int slot = collector == firstPlayer ? 0 : 1;
                playerEffects[slot].correct(1, true, collector, PinyinTeamEffects.Reward.power, null);
                playerEffects[slot].makePositiveBoostsPermanent();
                floating(collector.x, collector.y, "全对增益 · 当前成长保留", Pal.heal);
                }
                if(!consumed) continue;
                if(drop.eventDrop) sideEvent.collected();
                Fx.pickup.at(drop.x, drop.y);
                String pickup = drop.kind < 4 ? "+" + drop.amount + " " + Items.copper.localizedName : dropName(drop.kind);
                floating(drop.x, drop.y, pickup, drop.kind == 7 ? Pal.remove : Pal.heal);
                Sounds.uiUnlock.play();
                drops.remove(drop);
            }else if(drop.time <= 0f){
                drops.remove(drop);
            }
        }
        dropSnapshot.clear();
    }

    private void updatePinyinPayloads(){
        Point2 ship = layout.ships[mapStage()];
        Unit nearest = alive(firstPlayer) ? firstPlayer : alive(partner) ? partner : null;
        for(int i = pinyinPayloads.size - 1; i >= 0; i--){
            PinyinRescuePayload payload = pinyinPayloads.get(i);
            if(!payload.carried()){
                payload.updateEscape(Time.delta, nearest);
                for(int slot = 0; slot < 2; slot++){
                    Unit picker = slot == 0 ? firstPlayer : partner;
                    if(alive(picker) && picker.within(payload.position.x, payload.position.y, tilesize * 1.8f)){
                        payload.collect(slot);
                        floating(picker.x, picker.y, "捡回拼音：" + payload.character, Pal.heal);
                        Fx.pickup.at(payload.position.x, payload.position.y);
                        break;
                    }
                }
                continue;
            }
            Unit owner = payload.owner == 0 ? firstPlayer : partner;
            if(!alive(owner)){
                Vec2 safe = safePosition[payload.owner == 0 ? 0 : 1];
                payload.drop(safe.x, safe.y);
                continue;
            }
            payload.update(Time.delta, owner, i);
            if(owner == null || !nearPoint(owner, ship, 6f)) continue;
            int index = payload.owner == 0 ? 0 : 1;
            energy[index]++;
            pulseCharge[index] = Math.min(ENERGY_NEEDED, pulseCharge[index] + 1);
            // Delivery is the moment the learning reward is banked.  Rescue itself
            // only releases the syllable, so children must chase it and escort it to
            // the ship instead of receiving a free power increase at the house.
            if(!payload.rewardGranted){
                PinyinTeamEffects.Reward reward = payload.reward;
                PinyinArsenal.Kind weapon = payload.weapon;
                playerEffects[index].correct(energy[index], reward == PinyinTeamEffects.Reward.shield || Mathf.chance(0.35f),
                    owner, reward, weapon);
                // Rescued syllables are the long-term progression of this mode:
                // once a child escorts one to the ship, that personal growth stays
                // for the rest of the run instead of fading while exploring.
                playerEffects[index].makePositiveBoostsPermanent();
                playerEffects[index].refreshActiveBoosts();
            }
            adventure.deliver();
            floating(owner.x, owner.y, "飞船收到了 " + payload.character + " · " + payload.pinyin, Pal.heal);
            Fx.spawn.at(owner);
            Sounds.uiUnlock.play();
            pinyinPayloads.remove(i);
        }
    }

    public boolean isActive(){
        return defense.isActive() || active && worldReady && state.isGame();
    }

    private boolean controlsEnabled(){
        return isActive() && state.isPlaying()
            && !Core.scene.hasDialog() && !Core.scene.hasKeyboard()
            && !ui.chatfrag.shown() && !ui.consolefrag.shown();
    }

    public void updateDesktopControls(){
        if(defense.isActive()){
            defense.updateDesktopControls();
            return;
        }
        if(airMode){
            constrainAirPlayer(firstPlayer);
            constrainAirPlayer(partner);
            if(alive(firstPlayer)){
                PinyinPlayerInput.updateSurvival(firstPlayer, false, nearestPursuer(firstPlayer), controlsEnabled());
                dashMovement(firstPlayer, 0);
                player.shooting = firstPlayer.isShooting;
                player.boosting = false;
                player.mouseX = firstPlayer.aimX;
                player.mouseY = firstPlayer.aimY;
            }else player.shooting = false;
            if(!controlsEnabled()){
                if(alive(firstPlayer)) firstPlayer.controlWeapons(false, false);
                if(alive(partner)) partner.controlWeapons(false, false);
            }
            Unit lead = alive(firstPlayer) ? firstPlayer : partner;
            if(alive(lead)){
                float cx = lead.x, cy = lead.y, scale = Scl.scl(2.5f);
                if(alive(firstPlayer) && alive(partner)){
                    cx = (firstPlayer.x + partner.x) * 0.5f;
                    cy = (firstPlayer.y + partner.y) * 0.5f;
                    scale = Math.min(scale, Math.min(Core.graphics.getWidth() / (Math.abs(firstPlayer.x - partner.x) + 280f),
                        Core.graphics.getHeight() / (Math.abs(firstPlayer.y - partner.y) + 230f)));
                }
                renderer.setScale(scale);
                Core.camera.position.lerpDelta(cx, cy, 0.12f);
            }
            return;
        }
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

    private void constrainAirPlayer(Unit unit){
        if(!alive(unit)) return;
        float margin = Math.max(10f, unit.hitSize * 0.65f);
        unit.x = Mathf.clamp(unit.x, margin, AIR_WIDTH * tilesize - margin);
        unit.y = Mathf.clamp(unit.y, margin, AIR_HEIGHT * tilesize - margin);
    }

    private Tile nearestEnterableTile(int originX, int originY, PinyinVehicleSystem.Mode mode, int maxRadius){
        Tile origin = world.tile(originX, originY);
        if(PinyinVehicleSystem.canEnter(mode, origin)) return origin;
        for(int radius = 1; radius <= maxRadius; radius++){
            for(int dx = -radius; dx <= radius; dx++){
                for(int dy = -radius; dy <= radius; dy++){
                    if(Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                    Tile candidate = world.tile(originX + dx, originY + dy);
                    if(PinyinVehicleSystem.canEnter(mode, candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private void constrainPlayer(Unit unit, int index){
        if(!alive(unit)) return;
        Vec2 previous = safePosition[index];
        int left = layout.leftBoundary();
        unit.x = Mathf.clamp(unit.x, (left + 2) * tilesize, (layout.rightBoundary() - 2) * tilesize);
        unit.y = Mathf.clamp(unit.y, 3 * tilesize, (HEIGHT - 4) * tilesize);
        PinyinVehicleSystem.Mode mode = vehicleModes[index];
        Tile tile = world.tileWorld(unit.x, unit.y);
        if(!PinyinVehicleSystem.canEnter(mode, tile)){
            // A large impulse can move a unit across a whole tile in one frame,
            // so axis-by-axis rollback is insufficient near rivers.  Search from
            // the actual position and snap to the nearest tile valid for the
            // current hull, guaranteeing that land players never remain stranded
            // in water and boats never remain on dry ground.
            boolean boostEscape = index == 0 ? Core.input.keyDown(KeyCode.space)
                : Core.input.keyDown(KeyCode.shiftRight) || PinyinGamepad.state.dashTap;
            Tile previousTile = world.tileWorld(previous.x, previous.y);
            Tile recovery = boostEscape && PinyinVehicleSystem.canEnter(mode, previousTile) ? previousTile
                : nearestEnterableTile(World.toTile(unit.x), World.toTile(unit.y), mode, 10);
            if(recovery != null){
                unit.set(recovery.worldx(), recovery.worldy());
                if(boostEscape){
                    unit.apply(StatusEffects.fast, 24f);
                    Fx.shockwave.at(unit);
                }
                if(terrainRecoveryCooldown <= 0f){
                    floating(unit.x, unit.y, boostEscape ? "加速脱困！" : mode == PinyinVehicleSystem.Mode.sea ? "小皮艇回到水道" : "脱离水域", Pal.accent);
                    terrainRecoveryCooldown = 60f;
                }
            }else{
                Tile horizontal = world.tileWorld(unit.x, previous.y);
                Tile vertical = world.tileWorld(previous.x, unit.y);
                if(PinyinVehicleSystem.canEnter(mode, horizontal)) unit.y = previous.y;
                else if(PinyinVehicleSystem.canEnter(mode, vertical)) unit.x = previous.x;
                else if(PinyinVehicleSystem.canEnter(mode, world.tileWorld(previous.x, previous.y))) unit.set(previous);
            }
            unit.vel.setZero();
        }
        if(PinyinVehicleSystem.canEnter(mode, world.tileWorld(unit.x, unit.y))) previous.set(unit);
    }

    private void update(){
        if(!active || !worldReady || !state.isPlaying() || player == null) return;
        if(airMode){
            updateAirBattle();
            return;
        }
        bossSignal = Math.max(0f, bossSignal - Time.delta);
        terrainRecoveryCooldown = Math.max(0f, terrainRecoveryCooldown - Time.delta);
        var core = Team.sharded.core();
        if(core != null) core.items.set(Items.copper, supplies);
        for(int i = 0; i < playerEffects.length; i++){
            Unit unit = i == 0 ? firstPlayer : partner;
            int beforePower = playerEffects[i].power();
            // Rescue gains stay available until an explicit wrong answer removes one.
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
                unit.damage(PinyinCombatBalance.contactDamage(adventure.difficulty()));
                contactCooldown[i] = 60f;
                unit.impulse(Tmp.v1.set(unit).sub(nearest).setLength(3f));
            }
        }
        int before = lives.remaining();
        lives.update(alive(firstPlayer), alive(partner), Time.delta);
        if(lives.ended() && lives.remaining() == 0){
            finish(Core.bundle.get(lives.remaining() == 0 ? "pinyin.world.noLives" : "pinyin.world.teamLost"));
            return;
        }
        if(before != lives.remaining()){
            String key = lives.remaining() > before ? "pinyin.world.lifeGained" : "pinyin.world.lifeLost";
            ui.showInfoToast(Core.bundle.format(key, lives.remaining()), 2f);
        }
        updateVehicles();
        updateSafeZone();
        trackPlayerDamage();
        for(int i = 0; i < 2; i++){
            if(lives.ready(i) || lives.respawnReady(i)) revive(i);
        }

        boolean bossDefeatedThisFrame = false;
        for(int i = pursuers.size - 1; i >= 0; i--){
            Unit enemy = pursuers.get(i);
            if(!alive(enemy)){
                if(!(enemy.controller() instanceof PinyinEnemyController ai) || !ai.selfDestructed){
                    boolean defeatedBoss = enemy == guardian;
                    adventure.defeat(defeatedBoss);
                    bossDefeatedThisFrame |= defeatedBoss;
                    if(!defeatedBoss && enemy.controller() instanceof PinyinEnemyController ai
                        && allies.size < PinyinCombatBalance.maxHelpers && Mathf.chance(0.28f)){
                        Unit owner = nearestPlayer(enemy);
                        spawnRecruit(ai.kind, owner == partner ? 1 : 0);
                    }
                    sideEvent.defeated();
                    floating(enemy.x, enemy.y, Core.bundle.get("pinyin.adventure.defeated"), Pal.accent);
                    if(drops.size < 100) drops.add(new SupplyDrop(enemy.x, enemy.y, enemy == guardian ? 7 : Mathf.random(19), enemy == guardian ? 60 : 8));
                }
                pursuers.remove(i);
            }
        }
        if(bossDefeatedThisFrame){
            advanceStage();
            return;
        }
        allies.removeAll(unit -> !alive(unit));
        updateDrops();
        updateTurretPads();
        updateStageFeatures();
        updatePinyinPayloads();
        updateSideEvent();
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
        adventure.update(Time.delta, nearPoint(firstPlayer, layout.beacons[mapStage()], 14f)
            || nearPoint(partner, layout.beacons[mapStage()], 14f));
        // The only hard progression trigger is reaching the far-end Boss gate.
        if(!bossSpawned && (nearPoint(firstPlayer, layout.bossGates[mapStage()], 11f)
            || nearPoint(partner, layout.bossGates[mapStage()], 11f))){
            guardian = spawnEnemy(PinyinEnemyController.bossKind(layout.themes[mapStage()], layout.bossRolls[mapStage()]), true);
            bossSpawned = guardian != null;
            if(bossSpawned){
                bossSignal = 60f * 4f;
                Fx.spawn.at(guardian);
                Fx.blastExplosion.at(guardian);
                ui.showInfoToast(Core.bundle.format("pinyin.adventure.bossArrived", bossName()), 3f);
            }
        }
        for(int slot = 0; slot < 2; slot++){
            PinyinWorldQuestion board = worldQuestions[slot];
            Unit owner = slot == 0 ? firstPlayer : partner;
            int chargedChest = board.updateCharge(Time.delta, alive(owner) ? owner : null, null,
                layout, mapStage(), unavailableChests(), chestCooldown[slot] <= 0f);
            if(chargedChest >= 0){
                reservedChests[chargedChest] = true;
                openChest(chargedChest, owner);
            }
        }

    }

    /** Free-roaming survival: no forced displacement, scrolling or map changes. */
    private void updateAirBattle(){
        ui.hudfrag.shown = false;
        bossSignal = Math.max(0f, bossSignal - Time.delta);
        int beforeLives = lives.remaining();
        lives.update(alive(firstPlayer), alive(partner), Time.delta);
        if(lives.ended() && lives.remaining() == 0){
            finish(Core.bundle.get("pinyin.world.noLives"));
            return;
        }
        if(beforeLives != lives.remaining()) notice(Core.bundle.format(
            lives.remaining() > beforeLives ? "pinyin.world.lifeGained" : "pinyin.world.lifeLost", lives.remaining()), Pal.accent, 3f);
        for(int i = 0; i < 2; i++) if(lives.ready(i) || lives.respawnReady(i)) revive(i);
        Unit leader = alive(firstPlayer) ? firstPlayer : alive(partner) ? partner : null;
        if(leader == null) return;
        survivalTime += Time.delta;
        updateFunEvents();
        allies.removeAll(unit -> !alive(unit));
        for(int slot = 0; slot < 2; slot++){
            Unit unit = slot == 0 ? firstPlayer : partner;
            if(!alive(unit)) continue;
            constrainAirPlayer(unit);
            safePosition[slot].set(unit);
        }

        centerNoticeTime = Math.max(0f, centerNoticeTime - Time.delta);
        coopCooldown = Math.max(0f, coopCooldown - Time.delta);
        for(int slot = 0; slot < 2; slot++){
            celebration[slot] = Math.max(0f, celebration[slot] - Time.delta);
            Unit pilot = slot == 0 ? firstPlayer : partner;
            if(celebration[slot] > 0f && alive(pilot) && Mathf.chanceDelta(0.12f)){
                correctStars.at(pilot.x + Mathf.range(48f), pilot.y + Mathf.range(40f));
            }
        }
        if(!rounds.bossActive()){
            airWaveTimer = Math.max(0f, airWaveTimer - Time.delta);
            if(rounds.wave() == 9 && airWaveTimer <= 240f && !airBossWarning){
                airBossWarning = true;
                notice("⚠ 第10波Boss即将抵达！靠近队友，准备合力迎战 ⚠", Pal.remove, 4f);
            }
            if(airWaveTimer <= 0f){
                spawnAirWave();
                airWaveTimer = PinyinSurvival.interval(airWave);
            }
        }
        updatePlayerSkills();
        updateAirQuestions();
        updateFieldWorks();

        for(int slot = 0; slot < 2; slot++){
            Unit playerUnit = slot == 0 ? firstPlayer : partner;
            if(alive(playerUnit)) applyCrowdPressure(playerUnit, slot);
            reviveProtection[slot] = Math.max(0f, reviveProtection[slot] - Time.delta);
            contactCooldown[slot] = Math.max(0f, contactCooldown[slot] - Time.delta);
            playerEffects[slot].update(Time.delta, playerUnit);
        }

        boolean bossDefeated = false;
        for(int i = pursuers.size - 1; i >= 0; i--){
            Unit enemy = pursuers.get(i);
            if(!alive(enemy)){
                boolean defeatedBoss = enemy == guardian;
                if(defeatedBoss) bossDefeated = true;
                survivalKills++;
                if(drops.size < 100){
                    int kind = defeatedBoss ? 20 + Mathf.random(3) : Mathf.chance(0.28f) ? 0 : 4 + Mathf.random(19);
                    SupplyDrop drop = new SupplyDrop(enemy.x, enemy.y, kind, defeatedBoss ? 30 : 8);
                    drop.vy = 0f;
                    drops.add(drop);
                }
                if(defeatedBoss) correctStars.at(enemy);
                pursuers.remove(i);
            }
        }
        if(bossDefeated && rounds.defeatBoss()){
            survivalBosses++;
            bossSpawned = airBossWarning = false;
            guardian = null;
            // A boss victory is a clean round boundary, never another timed wave.
            for(Unit enemy : pursuers) if(alive(enemy)) enemy.remove();
            pursuers.clear();
            Seq<Bullet> dangerous = new Seq<>();
            for(Bullet bullet : Groups.bullet) if(bullet.team != Team.sharded) dangerous.add(bullet);
            for(Bullet bullet : dangerous) bullet.remove();
            lives.addLife();
            supplies += 100;
            airWaveTimer = 60f * 5f;
            for(int slot = 0; slot < 2; slot++){
                Unit pilot = slot == 0 ? firstPlayer : partner;
                if(alive(pilot)){ pilot.heal(60f); celebrate(pilot, slot); }
            }
            notice("Boss击破！进入第" + rounds.round() + "轮 · 5秒后出发 · 全队生命+1 / 铜+100", Pal.heal, 7f);
        }
        airWaveTimer = PinyinSurvival.afterClear(airWaveTimer, pursuers.contains(this::alive), rounds.bossActive());
        updateDrops();
        if(rounds.bossActive() && !bossSpawned) spawnRoundBoss();
    }

    private void updatePlayerSkills(){
        // Timers advance only with the playing scene; paused dialogs cannot activate skills.
        skills.update(Time.delta, firstPlayer, partner);
        if(!controlsEnabled()) return;
        for(int i = 0; i < 2; i++){
            Unit owner = i == 0 ? firstPlayer : partner, other = i == 0 ? partner : firstPlayer;
            if((Core.input.keyTap(i == 0 ? KeyCode.k : KeyCode.comma) || i == 1 && PinyinGamepad.state.prankTap)
                && skills.activatePrank(i, owner, other)){
                notice("P" + (i + 1) + "发动恶搞牵引！自己加速，队友被拉慢 · 3秒", Pal.lightOrange, 3f);
                Fx.shockwave.at(owner);
            }
            if((Core.input.keyTap(i == 0 ? KeyCode.l : KeyCode.period) || i == 1 && PinyinGamepad.state.ultimateTap)
                && skills.activateUltimate(i, owner)){
                notice("★ P" + (i + 1) + "超能爆发！移速×1.6 / 射速×2.2 / 伤害×2 · 10秒 ★", Pal.lancerLaser, 4f);
                celebrate(owner, i);
                Fx.shockwave.at(owner);
            }
        }
    }

    private void updateAirQuestions(){
        for(int owner = 0; owner < 2; owner++){
            AirQuestionBoard board = airBoards[owner];
            if(board == null){
                questionCooldown[owner] -= Time.delta;
                if(questionCooldown[owner] <= 0f && alive(owner == 0 ? firstPlayer : partner)) spawnAirQuestion(owner);
                continue;
            }
            board.access.age += Time.delta;
            for(AirAnswerPickup answer : airAnswers){
                if(board.access.complete()) break;
                if(answer.board != board) continue;
                for(int slot = 0; slot < 2; slot++){
                    Unit pilot = slot == 0 ? firstPlayer : partner;
                    if(alive(pilot) && board.access.canAnswer(slot)
                        && pilot.within(answer.x, answer.y, Math.max(14f, pilot.hitSize * 0.8f))
                        && board.access.collect(slot)){
                        collectAirAnswer(answer, slot, pilot);
                        break;
                    }
                }
            }
            boolean nearby = false;
            for(AirAnswerPickup answer : airAnswers) if(answer.board == board
                && (nearAirAnswer(firstPlayer, answer) || nearAirAnswer(partner, answer))) nearby = true;
            if(board.access.complete() || !nearby){
                airAnswers.removeAll(answer -> answer.board == board);
                airBoards[owner] = null;
                questionCooldown[owner] = owner == 0 ? 180f : 300f;
            }
        }
    }

    private void spawnRoundBoss(){
        guardian = spawnAirEnemy(PinyinEnemyController.bossKind(airLayout.theme(), (rounds.round() - 1) % 3), true);
        bossSpawned = guardian != null;
        if(bossSpawned){
            bossSignal = 240f;
            notice("第" + rounds.round() + "轮 · 第10波 BOSS：" + bossName() + "！过载装甲抵御瞬间爆发，持续攻击破甲", Pal.remove, 6f);
            Fx.blastExplosion.at(guardian);
            Sounds.uiNotify.play();
        }
    }

    private boolean nearAirAnswer(Unit playerUnit, AirAnswerPickup answer){
        return alive(playerUnit) && playerUnit.within(answer.x, answer.y, 550f);
    }

    private void notice(String text, Color color, float seconds){
        centerNotice = text;
        centerNoticeColor = color;
        centerNoticeTime = seconds * 60f;
    }

    private void prepareMiningGround(){
        // Guaranteed visible deposits at camp and throughout the large map.
        for(int x = 12; x < AIR_WIDTH - 12; x += 24) for(int y = 12; y < AIR_HEIGHT - 12; y += 28){
            for(int dx = -2; dx <= 2; dx++) for(int dy = -2; dy <= 2; dy++){
                Tile tile = world.tile(x + dx, y + dy);
                if(tile.block() == Blocks.air){
                    tile.setFloor(Blocks.metalFloor3.asFloor());
                    tile.setOverlay(Blocks.oreCopper);
                }
            }
        }
        int cx = AIR_WIDTH / 2, cy = AIR_HEIGHT / 2;
        for(int x = cx - 8; x <= cx + 8; x++) for(int y = cy - 3; y <= cy + 3; y++){
            Tile tile = world.tile(x, y);
            if(tile.block() == Blocks.air){ tile.setFloor(Blocks.metalFloor3.asFloor()); tile.setOverlay(Blocks.oreCopper); }
        }
    }

    private Tile nearbyOre(Unit pilot){
        int x = World.toTile(pilot.x), y = World.toTile(pilot.y);
        for(int dx = -3; dx <= 3; dx++) for(int dy = -3; dy <= 3; dy++){
            Tile tile = world.tile(x + dx, y + dy);
            if(tile != null && tile.drop() != null && tile.block() == Blocks.air) return tile;
        }
        return null;
    }

    private void updateFieldWorks(){
        supplies += fieldWorks.update(Time.delta, firstPlayer, partner, pursuers);
        for(int slot = 0; slot < 2; slot++){
            Unit pilot = slot == 0 ? firstPlayer : partner;
            if(!alive(pilot) || !controlsEnabled()) continue;
            boolean cycle = Core.input.keyTap(slot == 0 ? KeyCode.r : KeyCode.backspace)
                || slot == 1 && PinyinGamepad.state.fireTap && !PinyinGamepad.state.dash;
            boolean build = Core.input.keyTap(slot == 0 ? KeyCode.e : KeyCode.enter)
                || slot == 1 && PinyinGamepad.state.fire && PinyinGamepad.state.dashTap;
            if(Core.input.keyTap(slot == 0 ? KeyCode.q : KeyCode.slash)) counterattack(slot);
            if(cycle){
                selectedWorks[slot] = (selectedWorks[slot] + 1) % PinyinFieldWorks.Kind.values().length;
                PinyinFieldWorks.Kind kind = PinyinFieldWorks.Kind.values()[selectedWorks[slot]];
                notice("P" + (slot + 1) + "选择：" + kind.label + " · " + kind.cost + "铜 · " + (slot == 0 ? "E" : "Enter/手柄开火+冲刺") + "建造", slot == 0 ? Pal.accent : Pal.lancerLaser, 4f);
            }
            if(build){
                PinyinFieldWorks.Kind kind = PinyinFieldWorks.Kind.values()[selectedWorks[slot]];
                if(kind == PinyinFieldWorks.Kind.hangar && allies.size >= PinyinCombatBalance.maxHelpers){
                    notice("僚机已满，先保护现有僚机", Pal.accent, 3f);
                }else{
                    int cost = fieldWorks.build(kind, pilot, slot, supplies);
                    supplies -= cost;
                    if(cost > 0){
                        Fx.placeBlock.at(pilot);
                        if(kind == PinyinFieldWorks.Kind.hangar) spawnHelper(PinyinHelpers.Kind.values()[(rounds.round() + slot) % 4], slot);
                    }
                    notice("P" + (slot + 1) + "：" + fieldWorks.message, cost > 0 ? Pal.heal : Pal.remove, 4f);
                }
            }
            boolean mining = Core.input.keyDown(slot == 0 ? KeyCode.f : KeyCode.controlRight)
                || slot == 1 && PinyinGamepad.state.fire && !PinyinGamepad.state.dash;
            Tile ore = mining ? nearbyOre(pilot) : null;
            if(ore != null){
                boolean together = alive(firstPlayer) && alive(partner) && firstPlayer.within(partner, 90f);
                miningProgress[slot] += Time.delta * (together ? 2f : 1f);
                if(miningProgress[slot] >= 60f){
                    miningProgress[slot] %= 60f;
                    supplies += 4;
                    Fx.mine.at(ore.worldx(), ore.worldy(), Items.copper.color);
                }
            }else miningProgress[slot] = 0f;
        }
        fieldServiceTimer += Time.delta;
        if(fieldServiceTimer >= 60f){
            fieldServiceTimer %= 60f;
            if(alive(firstPlayer) && alive(partner) && firstPlayer.within(partner, 100f)){
                firstPlayer.shield = Math.min(PinyinCombatBalance.maxShield, firstPlayer.shield + 1.5f);
                partner.shield = Math.min(PinyinCombatBalance.maxShield, partner.shield + 1.5f);
            }
        }
    }

    private void drawFieldWorks(){
        for(PinyinFieldWorks.Site site : fieldWorks.sites){
            if(!site.alive()) continue;
            Color color = site.kind == PinyinFieldWorks.Kind.drill ? Pal.accent : Pal.heal;
            boolean together = PinyinFieldWorks.near(firstPlayer, site) && PinyinFieldWorks.near(partner, site);
            Draw.color(color, together ? 0.75f : 0.35f);
            Lines.stroke(together ? 2f : 1f);
            Lines.circle(site.tile.worldx(), site.tile.worldy(), site.kind == PinyinFieldWorks.Kind.barrier ? 30f : 22f);
            // Labels sit on facilities, never in a left sidebar.
            Fonts.outline.draw(site.kind.label + (together ? " ★协作" : ""), site.tile.worldx(), site.tile.worldy() + 22f, color, 0.28f, false, Align.center);
        }
        for(int slot = 0; slot < 2; slot++){
            Unit pilot = slot == 0 ? firstPlayer : partner;
            if(!alive(pilot)) continue;
            PinyinFieldWorks.Kind kind = PinyinFieldWorks.Kind.values()[selectedWorks[slot]];
            Draw.color(slot == 0 ? Pal.accent : Pal.lancerLaser, 0.8f);
            Draw.rect(kind.block().fullIcon, pilot.x - 22f, pilot.y - 22f, 12f, 12f);
            if(miningProgress[slot] > 0f){
                Lines.stroke(3f);
                Lines.arc(pilot.x, pilot.y, 20f, miningProgress[slot] / 60f);
            }
        }
    }

    private void applyCrowdPressure(Unit playerUnit, int slot){
        if(reviveProtection[slot] > 0f || momentum[slot].dashing()) return;
        crowdPush.setZero();
        int touching = 0;
        for(Unit enemy : pursuers){
            if(!alive(enemy)) continue;
            float radius = (enemy.hitSize + playerUnit.hitSize) * 0.52f + 5f;
            if(!enemy.within(playerUnit, radius)) continue;
            touching++;
            float distance = enemy.dst(playerUnit);
            Tmp.v1.set(playerUnit).sub(enemy);
            if(distance < 0.1f) Tmp.v1.trns(enemy.id * 137.5f, 1f);
            float weight = enemy.controller() instanceof PinyinSurvivalController ai && ai.role == PinyinSurvival.Role.tank ? 1.4f : 0.65f;
            crowdPush.add(Tmp.v1.setLength(weight * (1f - Math.min(0.95f, distance / radius))));
        }
        if(touching > 0){
            // Contact nudges the player and narrows escape lanes, but a dash
            // bypasses crowd pressure instead of letting the mob permanently pin them.
            crowdPush.limit(playerUnit.type.speed * 0.65f).scl(Time.delta);
            playerUnit.move(crowdPush);
            constrainAirPlayer(playerUnit);
            if(contactCooldown[slot] <= 0f){
                playerUnit.damage(7f * PinyinSurvival.damageScale(airWave));
                contactCooldown[slot] = 55f;
                if(touching >= 3) floating(playerUnit.x, playerUnit.y, "被围堵！冲刺突围", Pal.remove);
            }
        }
    }

    private void spawnAirWave(){
        if(!rounds.nextWave()) return;
        airWave = rounds.totalWave();
        if(rounds.bossActive()){
            spawnRoundBoss();
            return;
        }
        notice("第" + rounds.round() + "轮 · 第" + rounds.wave() + "波追截队抵达", Pal.accent, 3f);
        int count = PinyinSurvival.waveSize(airWave);
        for(int i = 0; i < count && pursuers.size < PinyinSurvival.enemyLimit(airWave); i++){
            Unit target = i % 2 == 0 && alive(partner) ? partner : alive(firstPlayer) ? firstPlayer : partner;
            if(!alive(target)) break;
            // Squads approach from different sides, leaving a moving escape gap.
            float angle = airWave * 67f + i * 290f / count;
            float radius = Math.max(230f, Math.min(480f, Math.max(Core.camera.width, Core.camera.height) * 0.6f));
            Vec2 place = PinyinSurvival.spawnPoint(new Vec2(), target, angle, radius,
                AIR_WIDTH * tilesize, AIR_HEIGHT * tilesize);
            if(nearPlayerSpawn(place)) continue;
            spawnAirEnemy(PinyinSurvival.kind(airWave, i), false, place.x, place.y, i);
        }
    }

    private boolean nearPlayerSpawn(Vec2 place){
        return alive(firstPlayer) && firstPlayer.within(place, 140f) || alive(partner) && partner.within(place, 140f);
    }

    private Unit spawnAirEnemy(PinyinEnemyController.Kind kind, boolean boss){
        Unit leader = alive(firstPlayer) ? firstPlayer : partner;
        if(!alive(leader)) return null;
        Vec2 place = PinyinSurvival.spawnPoint(new Vec2(), leader, airWave * 67f, 300f,
            AIR_WIDTH * tilesize, AIR_HEIGHT * tilesize);
        if(nearPlayerSpawn(place)) return null;
        return spawnAirEnemy(kind, boss, place.x, place.y, 0);
    }

    private Unit spawnAirEnemy(PinyinEnemyController.Kind kind, boolean boss, float x, float y, int slot){
        PinyinSurvival.Role role = boss ? PinyinSurvival.Role.tank : PinyinSurvival.role(airWave, slot);
        UnitType type = PinyinSurvival.carrier(role, boss);
        Unit enemy = boss ? PinyinSurvivalBoss.spawn(type, Team.crux, x, y) : type.spawn(Team.crux, x, y);
        if(enemy == null) return null;
        PinyinSurvivalController.equip(enemy, kind, role, airWave, boss);
        enemy.controller(new PinyinSurvivalController(kind, role, PinyinSurvival.pattern(airWave, slot),
            airWave, airWave * 8 + slot / 8, slot % 8, boss, this::nearestPlayer));
        pursuers.add(enemy);
        Fx.spawn.at(enemy);
        return enemy;
    }

    private void spawnAirQuestion(int owner){
        PinyinQuestions.Question question = QUESTION_BANK[Mathf.random(QUESTION_BANK.length - 1)];
        AirQuestionBoard board = new AirQuestionBoard(question, owner);
        airBoards[owner] = board;
        pronunciation.speak(question.character);
        int correct = Mathf.random(1);
        Unit pilot = owner == 0 ? firstPlayer : partner;
        float centerX = Mathf.clamp(pilot.x + (owner == 0 ? -55f : 55f), 80f, AIR_WIDTH * tilesize - 80f);
        float y = Mathf.clamp(pilot.y + (owner == 0 ? 85f : -85f), 55f, AIR_HEIGHT * tilesize - 55f);
        String[] options = PinyinSurvival.answerChoices(question, correct);
        for(int i = 0; i < 2; i++) airAnswers.add(new AirAnswerPickup(board, options[i], i == correct,
            centerX + (i == 0 ? -48f : 48f), y));
    }

    private void collectAirAnswer(AirAnswerPickup answer, int slot, Unit owner){
        PinyinQuestions.Question question = answer.board.question;
        if(answer.correct){
            PinyinTeamEffects.Reward reward = PinyinTeamEffects.Reward.values()[Mathf.random(PinyinTeamEffects.Reward.values().length - 1)];
            playerEffects[slot].correct(0, reward == PinyinTeamEffects.Reward.shield, owner, reward, null);
            pulseCharge[slot] = Math.min(ENERGY_NEEDED, pulseCharge[slot] + 1);
            momentum[slot].correct();
            owner.apply(StatusEffects.fast, 480f);
            celebrate(owner, slot);
            notice("★ P" + (slot + 1) + "答对 " + question.pinyin + "！武器升级 Lv." + playerEffects[slot].power() + " ★", Pal.accent, 3f);
            Sounds.uiUnlock.play();
            lastCorrect[slot] = survivalTime;
            if(coopCooldown <= 0f && survivalTime - lastCorrect[1 - slot] <= 480f){
                coopCooldown = 600f;
                supplies += 30;
                for(Unit pilot : new Unit[]{firstPlayer, partner}) if(alive(pilot)){
                    pilot.shield = Math.min(PinyinCombatBalance.maxShield, pilot.shield + 35f);
                    pilot.heal(15f);
                    correctStars.at(pilot);
                }
                notice("★★ 双人接力成功！团队护盾 +35 / 铜 +30 ★★", Pal.heal, 4f);
            }
        }else{
            playerEffects[slot].wrong(owner);
            momentum[slot].wrong();
            owner.apply(StatusEffects.slow, 360f);
            wrongRing.at(owner);
            wrongShards.at(owner);
            notice("P" + (slot + 1) + "再试试看！正确读音：" + question.pinyin, Pal.remove, 3f);
            Sounds.uiBack.play();
        }
    }

    private void celebrate(Unit owner, int slot){
        celebration[slot] = 210f;
        correctRing.at(owner);
        correctStars.at(owner);
        for(int i = 0; i < 7; i++){
            float angle = i * 360f / 7f;
            correctStars.at(owner.x + Angles.trnsx(angle, 45f), owner.y + Angles.trnsy(angle, 45f));
        }
    }

    private boolean nearPoint(Unit unit, Point2 point, float radius){
        return alive(unit) && unit.within(point.x * tilesize, point.y * tilesize, radius * tilesize);
    }

    private void openChest(int index, Unit opener){
        if(opener == null) return;
        Unit teammate = opener == firstPlayer ? partner : firstPlayer;
        int slot = opener == firstPlayer ? 0 : 1;
        Vec2[] positions = PinyinWorldQuestion.placeOptions(world.tiles, mapStage(), opener, alive(teammate) ? teammate : null,
            worldQuestions[1 - slot].active() ? worldQuestions[1 - slot].positions : null,
            layout.leftBoundary(), layout.rightBoundary());
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
            adventure.answer();
            sideEvent.answered();
            PinyinTeamEffects.Reward reward = bunkerRewards[index];
            PinyinArsenal.Kind weapon = bunkerWeapons[index];
            if(alive(picker)){
                // Answer bunkers reward learning with supplies; only rescued people create
                // the collectible pinyin companions that can be carried to a ship.
                if(drops.size < 100) drops.add(new SupplyDrop(picker.x, picker.y, 0, 12));
                floating(picker.x, picker.y, "答对！获得补给，继续寻找被困的人", Pal.heal);
            }
            boolean combo = momentum[collector].correct();
            if(combo){
                playerEffects[collector].combo(picker);
                floating(picker.x, picker.y + 24f, Core.bundle.format("pinyin.adventure.combo", momentum[collector].streak()), Pal.accent);
                correctStars.at(picker);
                Fx.shockwave.at(picker);
            }
            pushPursuerBack(picker);
            Sounds.uiUnlock.play();
            ui.showInfoToast(Core.bundle.format("pinyin.world.correct", collector + 1, question.character,
                playerEffects[collector].bulletsPerShot(),
                "补给奖励：" + Core.bundle.get("pinyin.reward." + reward.name())), 3f);
        }else{
            playerEffects[collector].wrong(alive(picker) ? picker : null);
            momentum[collector].wrong();
            if(alive(picker)){
                healthSnapshot[collector] = picker.health;
                shieldSnapshot[collector] = picker.shield;
            }
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

    /** Each map has a fixed roster; defeated regular enemies never respawn on a timer. */
    private void spawnFixedEnemies(){
        int target = Math.max(1, adventure.enemyLimit() - 1), attempts = 0;
        Point2[] spine = layout.routes[mapStage()];
        while(pursuers.size < target && attempts++ < target * 6){
            var kind = PinyinEnemyController.regularKind(attempts + layout.patterns[mapStage()], adventure.difficulty(), layout.themes[mapStage()]);
            Point2 anchor = spine == null || spine.length < 3 ? null
                : spine[1 + Math.floorMod(attempts * 7, spine.length - 2)];
            spawnEnemy(kind, false, anchor);
        }
    }

    private Unit spawnEnemy(PinyinEnemyController.Kind kind, boolean boss){
        return spawnEnemy(kind, boss, null);
    }

    /** Spawn regular units around route nodes, making each stretch read as a fixed enemy outpost. */
    private Unit spawnEnemy(PinyinEnemyController.Kind kind, boolean boss, Point2 routeAnchor){
        Unit anchor = alive(firstPlayer) && alive(partner) ? (Mathf.chance(0.5) ? firstPlayer : partner) : alive(firstPlayer) ? firstPlayer : partner;
        Point2 spawnPoint = boss ? layout.bossArenas[mapStage()] : null;
        int left = layout.leftBoundary();
        UnitType visual = PinyinEnemyController.visualType(kind);
        for(int attempt = 0; attempt < 100; attempt++){
            int x = boss ? spawnPoint.x + Mathf.random(-5, 5)
                : routeAnchor == null ? Mathf.random(left + 6, layout.rightBoundary() - 6) : routeAnchor.x + Mathf.random(-10, 10);
            int y = boss ? spawnPoint.y + Mathf.random(-6, 6)
                : routeAnchor == null ? Mathf.random(6, HEIGHT - 7) : routeAnchor.y + Mathf.random(-14, 14);
            if(!boss && visual.naval && routeAnchor == null){
                if(layout.themes[mapStage()] == 0) x = left + Mathf.random(178, 205);
                if(layout.themes[mapStage()] == 2) y = HEIGHT - Mathf.random(16, 49);
            }
            if(x < left + 4 || x > layout.rightBoundary() - 4 || y < 5 || y > HEIGHT - 6) continue;
            boolean clear = true;
            for(int dx = -1; dx <= 1; dx++){
                for(int dy = -1; dy <= 1; dy++){
                    if(world.tile(x + dx, y + dy).solid()) clear = false;
                }
            }
            Tile spawnTile = world.tile(x, y);
            boolean liquid = spawnTile.floor() != null && spawnTile.floor().isLiquid;
            if(!boss && ((liquid && !visual.naval && !visual.flying) || (!liquid && visual.naval))) continue;
            if(!clear || (!boss && (nearPoint(firstPlayer, new Point2(x, y), 9f) || nearPoint(partner, new Point2(x, y), 9f)))) continue;
            Unit monster = PinyinEnemyController.visualType(kind).spawn(Team.crux, x * tilesize, y * tilesize);
            if(monster == null) continue;
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
        if(airMode) notice(Core.bundle.format("pinyin.world.countered", index + 1), Pal.lancerLaser, 3f);
        else ui.showInfoToast(Core.bundle.format("pinyin.world.countered", index + 1), 3f);
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
        if(airMode){
            if(centerNoticeTime <= 0f) notice(text, color, 2.5f);
            return;
        }
        if(feedback.size >= 64) feedback.remove(0);
        feedback.add(new PickupFeedback(x, y + 12f, text, color));
    }

    private void dashMovement(Unit unit, int index){
        if(!controlsEnabled() || !momentum[index].dashing()) return;
        Vec2 direction = Tmp.v1.set(unit.vel);
        if(direction.isZero()) direction.trns(unit.rotation, 1f);
        float speed = airMode ? unit.type.speed * unit.speedMultiplier * 2f : unit.speed();
        unit.moveAt(direction.setLength(speed * 2.4f));
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
        if(airMode) return;
        supplyTimer -= Time.delta;
        if(supplyTimer > 0f || anyQuestionActive()) return;
        supplyTimer = 60f * Mathf.random(25f, 35f);
        for(Unit unit : new Unit[]{firstPlayer, partner}){
            if(!alive(unit)) continue;
            Vec2[] places = PinyinWorldQuestion.placeOptions(world.tiles, mapStage(), unit, null, null,
                layout.leftBoundary(), layout.rightBoundary());
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

    private void updateSideEvent(){
        sideEventCooldown = Math.max(0f, sideEventCooldown - Time.delta);
        Unit leader = alive(firstPlayer) ? firstPlayer : partner;
        float radius = tilesize * (sideEvent.kind() == PinyinAdventureEvents.Kind.rescue ? 8f : 5f);
        boolean inside = alive(firstPlayer) && firstPlayer.within(sideEventPoint.x, sideEventPoint.y, radius)
            || alive(partner) && partner.within(sideEventPoint.x, sideEventPoint.y, radius);
        sideEvent.update(Time.delta, inside);
        if(sideEvent.consumeCompleted()){
            clearEventDrops();
            supplies += 30 + PinyinCombatBalance.difficulty(adventure.difficulty()) * 3;
            if(sideEvent.kind() == PinyinAdventureEvents.Kind.rescue && allies.size < PinyinCombatBalance.maxHelpers){
                PinyinHelpers.Kind kind = PinyinHelpers.Kind.values()[Math.floorMod(spawnWave + adventure.stage(), 4)];
                spawnHelper(kind, alive(firstPlayer) && firstPlayer.within(sideEventPoint.x, sideEventPoint.y, radius) ? 0 : 1);
            }
            for(int i = 0; i < 2; i++){
                Unit unit = i == 0 ? firstPlayer : partner;
                if(alive(unit)){
                    unit.heal(35f);
                    unit.shield = Math.min(PinyinCombatBalance.maxShield, unit.shield + 45f);
                    playerEffects[i].refreshActiveBoosts();
                    Fx.heal.at(unit);
                }
            }
            if(sideEvent.kind() == PinyinAdventureEvents.Kind.rhythm) for(PinyinTeamEffects effects : playerEffects) effects.refreshActiveBoosts();
            floating(sideEventPoint.x, sideEventPoint.y, Core.bundle.get("pinyin.event.complete"), Pal.heal);
            Fx.spawn.at(sideEventPoint.x, sideEventPoint.y);
            Sounds.uiUnlock.play();
            sideEventCooldown = 60f * (18f + Mathf.random(12f));
        }else if(sideEvent.failed()){
            clearEventDrops();
            floating(sideEventPoint.x, sideEventPoint.y, Core.bundle.get("pinyin.event.failed"), Pal.remove);
            alertNearbyEnemies();
            sideEvent.reset();
            sideEventCooldown = 60f * 10f;
        }else if(!sideEvent.active() && sideEventCooldown <= 0f && leader != null){
            PinyinAdventureEvents.Kind kind = PinyinAdventureEvents.Kind.values()[Mathf.random(PinyinAdventureEvents.Kind.values().length - 1)];
            sideEvent.start(kind, adventure.difficulty());
            Point2 point = switch(kind){
                case rescue -> layout.beacons[mapStage()];
                case rhythm -> layout.chests[mapStage() * PinyinAdventureMap.chestsPerStage + Mathf.random(PinyinAdventureMap.chestsPerStage - 1)];
                case convoy -> layout.workshops[mapStage()];
                default -> layout.camps[mapStage()];
            };
            sideEventPoint.set(point.x * tilesize, point.y * tilesize);
            if(kind == PinyinAdventureEvents.Kind.convoy) spawnEventDrops();
            floating(sideEventPoint.x, sideEventPoint.y, Core.bundle.get("pinyin.event.new") + " " + eventText(), Pal.accent);
            Sounds.uiNotify.play();
        }
    }

    private void spawnEventDrops(){
        clearEventDrops();
        for(int i = 0; i < 3; i++){
            float angle = i * 120f + 20f;
            float distance = tilesize * (4f + i * 1.5f);
            float x = sideEventPoint.x + Angles.trnsx(angle, distance);
            float y = sideEventPoint.y + Angles.trnsy(angle, distance);
            Tile tile = world.tileWorld(x, y);
            if(tile == null || tile.solid()){
                x = sideEventPoint.x;
                y = sideEventPoint.y;
            }
            SupplyDrop drop = new SupplyDrop(x, y, 18, 10, true);
            drop.time = 60f * 35f;
            drops.add(drop);
            Fx.spawn.at(x, y);
        }
    }

    private void clearEventDrops(){
        drops.removeAll(drop -> drop.eventDrop);
    }

    private void alertNearbyEnemies(){
        Unit leader = alive(firstPlayer) ? firstPlayer : partner;
        if(!alive(leader)) return;
        for(Unit enemy : pursuers) if(alive(enemy) && enemy.within(leader, 30f * tilesize))
            enemy.apply(StatusEffects.overclock, 60f * 3f);
    }

    private class PartnerController extends AIController{
        @Override
        public void updateTargeting(){
            // Player 2 aims and fires from the mouse button instead of auto-targeting.
        }

        @Override
        public void updateMovement(){
            if(unit == null) return;
            if(airMode) PinyinPlayerInput.updateSurvival(unit, true, nearestPursuer(unit), controlsEnabled());
            else PinyinPlayerInput.update(unit, true, nearestPursuer(unit), controlsEnabled(), !Core.scene.hasMouse());
            dashMovement(unit, 1);
        }
    }

    private class RecruitController extends AIController{
        final PinyinEnemyController.Kind kind;
        final int owner;

        RecruitController(PinyinEnemyController.Kind kind, int owner){ this.kind = kind; this.owner = owner; }

        private Unit anchor(){
            Unit preferred = owner == 0 ? firstPlayer : partner;
            return alive(preferred) ? preferred : nearestPlayer(unit);
        }

        @Override
        public void updateTargeting(){
            Unit enemy = nearestPursuer(unit);
            boolean shoot = enemy != null && unit.within(enemy, 170f);
            if(shoot) unit.aimLook(enemy);
            unit.controlWeapons(shoot, shoot);
        }

        @Override
        public void updateMovement(){
            Unit target = anchor();
            if(target == null) return;
            Vec2 formation = Tmp.v2.trns(unit.id * 97.3f, 38f).add(target);
            moveTo(formation, 10f);
        }
    }

    private class DroneController extends AIController{
        final PinyinHelpers.Kind kind;
        final int owner;
        private float service;

        DroneController(PinyinHelpers.Kind kind, int owner){ this.kind = kind; this.owner = owner; }

        private Unit anchor(){
            Unit preferred = owner == 0 ? firstPlayer : partner;
            return alive(preferred) ? preferred : nearestPlayer(unit);
        }

        @Override
        public void updateTargeting(){
            Unit enemy = kind == PinyinHelpers.Kind.gunner ? nearestPursuer(unit) : null;
            boolean shoot = enemy != null && unit.within(enemy, 150f);
            if(shoot) unit.aimLook(enemy);
            unit.controlWeapons(shoot, shoot);
        }

        @Override
        public void updateMovement(){
            Unit anchor = anchor();
            if(anchor == null) return;
            service = Math.max(0f, service - Time.delta);
            if(kind == PinyinHelpers.Kind.collector){
                SupplyDrop nearest = null;
                for(SupplyDrop drop : drops){
                    if(drop.kind < 4 && unit.within(drop.x, drop.y, 160f)
                        && (nearest == null || unit.dst2(drop.x, drop.y) < unit.dst2(nearest.x, nearest.y))) nearest = drop;
                }
                if(nearest != null){
                    moveTo(new Vec2(nearest.x, nearest.y), 5f);
                    if(unit.within(nearest.x, nearest.y, 12f)){
                        supplies += nearest.amount;
                        Fx.pickup.at(nearest.x, nearest.y);
                        drops.remove(nearest);
                    }
                    return;
                }
            }
            if(service <= 0f){
                service = 120f;
                if(kind == PinyinHelpers.Kind.medic && unit.within(anchor, 100f)){
                    for(Unit pilot : new Unit[]{firstPlayer, partner}) if(alive(pilot) && unit.within(pilot, 100f)){
                        pilot.heal(12f);
                        Fx.heal.at(pilot);
                    }
                }else if(kind == PinyinHelpers.Kind.engineer && airMode){
                    anchor.shield = Math.min(PinyinCombatBalance.maxShield, anchor.shield + 10f);
                    for(PinyinFieldWorks.Site site : fieldWorks.sites) if(site.alive() && unit.within(site.tile, 130f)){
                        site.tile.build.heal(24f);
                        Fx.healBlockFull.at(site.tile.worldx(), site.tile.worldy(), site.tile.block().size, Pal.heal);
                    }
                }else if(kind == PinyinHelpers.Kind.collector && airMode && nearbyOre(anchor) != null){
                    supplies += 3;
                }else if(kind == PinyinHelpers.Kind.engineer){
                    int start = mapStage() * PinyinAdventureMap.padsPerStage;
                    for(int i = start; i < start + PinyinAdventureMap.padsPerStage; i++){
                        Point2 pad = layout.turretPads[i];
                        Tile tile = world.tile(pad.x, pad.y);
                        if(!PinyinTurretPads.built(tile) || !unit.within(tile, 150f)) continue;
                        tile.build.heal(35f);
                        if(!PinyinTurretPads.loaded(tile) && supplies >= 3){
                            var turret = (mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild)tile.build;
                            var ammo = PinyinTurretPads.ammo(i);
                            turret.handleStack(ammo, turret.acceptStack(ammo, 20, turret), turret);
                            supplies -= 3;
                        }
                        break;
                    }
                }
            }
            Vec2 formation = Tmp.v2.trns(unit.id * 137.5f, 32f).add(anchor);
            moveTo(formation, 8f);
        }
    }

    private static class HelperRecord{
        final PinyinHelpers.Kind kind;
        final int owner;
        HelperRecord(PinyinHelpers.Kind kind, int owner){ this.kind = kind; this.owner = owner; }
    }

    private static class RecruitRecord{
        final PinyinEnemyController.Kind kind;
        final int owner;
        RecruitRecord(PinyinEnemyController.Kind kind, int owner){ this.kind = kind; this.owner = owner; }
    }

    private static class SupplyDrop{
        final float x;
        float y;
        final int kind, amount;
        final boolean eventDrop;
        float vy;
        float time = 60f * 45f;
        boolean limited;

        SupplyDrop(float x, float y, int kind, int amount){
            this(x, y, kind, amount, false);
        }

        SupplyDrop(float x, float y, int kind, int amount, boolean eventDrop){
            this.x = x;
            this.y = y;
            this.kind = kind;
            this.amount = amount;
            this.eventDrop = eventDrop;
        }
    }

    private static class AirQuestionBoard{
        final PinyinQuestions.Question question;
        final PinyinSurvival.AnswerAccess access;
        AirQuestionBoard(PinyinQuestions.Question question, int owner){
            this.question = question;
            access = new PinyinSurvival.AnswerAccess(owner);
        }
    }

    private static class AirAnswerPickup{
        final AirQuestionBoard board;
        final String text;
        final boolean correct;
        final float x;
        float y;

        AirAnswerPickup(AirQuestionBoard board, String text, boolean correct, float x, float y){
            this.board = board;
            this.text = text;
            this.correct = correct;
            this.x = x;
            this.y = y;
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

