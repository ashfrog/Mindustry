package mindustry.ui.dialogs;

import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.entities.units.*;
import mindustry.entities.Effect;
import mindustry.entities.bullet.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.game.PinyinDefense.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.maps.Map;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/** Local co-op presentation. All combat and resource rules live in PinyinDefense. */
public class PinyinDefenseScene{
    private static final Color atmosphere = Color.valueOf("132536"), pedestalDark = Color.valueOf("242e3b"),
        pedestalRim = Color.valueOf("647588"), pedestalTop = Color.valueOf("303d4c"), capsule = Color.valueOf("202d3c");
    private static final Color friendInk = Color.valueOf("55516d"), friendCream = Color.valueOf("fff9ed"),
        friendBlush = Color.valueOf("f68eaa"), friendShade = Color.valueOf("c299c6");
    private static final Color[] friendColors = {Color.valueOf("ffbfd3"), Color.valueOf("a8ead3"), Color.valueOf("d0c2ff")};
    private static final Effect friendPickup = new Effect(32f, e -> {
        Draw.color(e.color, Color.white, e.fin());
        for(int i = 0; i < 7; i++){
            float angle = i * 360f / 7f + e.id % 40;
            float distance = 5f + e.fin(Interp.pow2Out) * 27f;
            pickupStar(e.x + Angles.trnsx(angle, distance), e.y + Angles.trnsy(angle, distance), 3.5f * e.fout());
        }
    }).layer(Layer.playerName + 2f);
    private final Map map = new Map(StringMap.of("name", "pinyin-defense", "description", "Pinyin Tower Defense"));
    private final Table hud = new Table();
    private final Unit[] pilots = new Unit[2];
    private final PinyinDefensePickup[] boards = new PinyinDefensePickup[2];
    private final Vec2[] previousPosition = {new Vec2(), new Vec2()};
    private final float[] assembly = new float[PinyinDefenseMap.padCount];
    private final float[] recoil = new float[PinyinDefenseMap.padCount], answerResult = new float[PinyinDefenseMap.padCount];
    private final boolean[] answerFailed = new boolean[PinyinDefenseMap.padCount];
    private final TowerKind[] resultKind = new TowerKind[PinyinDefenseMap.padCount];
    private final float[] fireSoundCooldown = new float[16];
    private final PinyinPronunciation pronunciation = new PinyinPronunciation();
    private float voiceTime, impactSoundTime, deathSoundTime, baseFlash;
    private final Rand lootRandom = new Rand();
    private final int[] selected = new int[2], focus = {-1, -1};
    private final boolean[] customKind = new boolean[2];
    private final float[] dash = new float[2];
    private final Seq<PinyinQuestions.Question> questionBag = new Seq<>();
    private PinyinDefense game;
    private boolean active, ready, previousHud;
    private float previousScale, previousMinZoom, messageTime, endTime;
    private String message = "";
    private int chapter = 1, lastWave;
    private Color messageColor = Pal.accent;

    public PinyinDefenseScene(){
        world.addMapLoader(map, () -> {
            state.map = map;
            world.loadGenerator(PinyinDefenseMap.width, PinyinDefenseMap.height, game.map::generate);
        });
        Events.on(WorldLoadEvent.class, event -> {
            if(!active || state.map != map) return;
            ready = true;
            for(int slot = 0; slot < 2; slot++){
                Vec2 start = game.map.pads[slot];
                Unit unit = UnitTypes.alpha.spawn(Team.sharded, start.x, start.y + 30f);
                // Personal fire is modeled at two damage per shot, independent of native weapon buffs.
                unit.mounts = new mindustry.entities.units.WeaponMount[0];
                unit.elevation = 1f;
                pilots[slot] = unit;
                previousPosition[slot].set(unit.x, unit.y);
                if(slot == 0){ player.unit(unit); player.set(unit); }
                else unit.controller(new AIController(){
                    @Override public void updateTargeting(){}
                    @Override public void updateMovement(){ movePilot(1); }
                });
            }
            ui.hudfrag.shown = false;
            buildHud();
            ui.hudGroup.addChild(hud);
            announce("靠近炮位：P1 按 J / P2 按手柄 A 打开补给，移动拾取正确拼音组装炮台", Pal.accent, 8f);
        });
        Events.on(StateChangeEvent.class, event -> {
            if(active && ready && event.to == GameState.State.menu) stop();
            if(active && event.to == GameState.State.paused){
                pronunciation.stop(); voiceTime = 0f;
                for(PinyinDefensePickup board : boards) if(board != null) board.spoken = false;
            }
        });
        Events.run(Trigger.update, this::update);
        Events.run(Trigger.draw, this::draw);
        Events.run(Trigger.preDraw, () -> {
            if(!isActive()) return;
            fitCamera();
            // Fitting the battlefield resets the camera; preserve vanilla shake after that reset.
            float shake = renderer.shakeIntensity * Core.settings.getInt("screenshake", 4) / 4f * 0.75f;
            if(renderer.shakeTime > 0f && state.isPlaying()) Core.camera.position.add(Mathf.range(shake), Mathf.range(shake));
        });
    }

    public boolean isActive(){ return active && ready && state.isGame(); }

    public void start(){
        if(!active){ previousHud = ui.hudfrag.shown; previousScale = renderer.getScale(); previousMinZoom = renderer.minZoom; }
        active = true;
        chapter = 1;
        ui.pinyin.hide();
        loadMap();
    }

    private void loadMap(){
        ready = false;
        pronunciation.stop();
        PinyinDefense previous = game;
        long seed = System.nanoTime();
        do{
            game = new PinyinDefense(seed++, chapter);
        }while(previous != null && (game.map.theme == previous.map.theme || game.deck.first() == previous.deck.first()));
        game.feedback = new Feedback(){
            @Override public void fired(Shot shot){ shotFired(shot); }
            @Override public void impacted(Shot shot){ shotImpact(shot); }
            @Override public void destroyed(Enemy enemy){ enemyDestroyed(enemy); }
            @Override public void destroyed(Tower tower){ towerDestroyed(tower); }
            @Override public void shieldBroken(Enemy enemy){
                Fx.shieldBreak.at(enemy.position.x, enemy.position.y, 17f, Pal.lancerLaser);
                play(Sounds.shieldBreakSmall, enemy.position, 0.8f);
            }
        };
        boards[0] = boards[1] = null;
        pilots[0] = pilots[1] = null;
        focus[0] = focus[1] = -1;
        selected[0] = selected[1] = 0;
        customKind[0] = customKind[1] = false;
        dash[0] = dash[1] = 0f;
        java.util.Arrays.fill(assembly, 0f);
        java.util.Arrays.fill(recoil, 0f);
        java.util.Arrays.fill(answerResult, 0f);
        java.util.Arrays.fill(fireSoundCooldown, 0f);
        voiceTime = impactSoundTime = deathSoundTime = baseFlash = 0f;
        endTime = 0f;
        messageTime = 0f;
        message = "";
        lastWave = 0;
        Rules rules = new Rules();
        rules.defaultTeam = Team.sharded;
        rules.waveTeam = Team.crux;
        rules.waves = rules.attackMode = rules.canGameOver = rules.fog = false;
        rules.unitCap = 10;
        rules.unitCapVariable = false;
        rules.lighting = false;
        rules.env = Env.terrestrial;
        rules.planet = Planets.serpulo;
        control.playMap(map, rules);
    }

    private void stop(){
        active = ready = false;
        pronunciation.stop();
        hud.remove();
        ui.hudfrag.shown = previousHud;
        renderer.minZoom = previousMinZoom;
        renderer.minZoomInGame = previousMinZoom / Core.settings.getFloat("minzoomingamemultiplier", 1f);
        renderer.setScale(previousScale);
    }

    private boolean enabled(){
        return isActive() && state.isPlaying() && !Core.scene.hasDialog() && !Core.scene.hasKeyboard()
            && !ui.chatfrag.shown() && !ui.consolefrag.shown() && !game.lost && !game.won;
    }

    public void updateDesktopControls(){
        if(!isActive()) return;
        movePilot(0);
        player.shooting = false;
        player.boosting = false;
        fitCamera();
    }

    private void fitCamera(){
        // Fit the entire battlefield; roads, incoming enemies and the base remain visible.
        float fit = Math.min(Core.graphics.getWidth() / (PinyinDefenseMap.width * 8f + 70f),
            Math.max(120f, Core.graphics.getHeight() - Scl.scl(170f)) / (PinyinDefenseMap.height * 8f + 35f));
        renderer.landTime = 0f;
        renderer.minZoom = Math.min(previousMinZoom, fit / Scl.scl(1f));
        renderer.minZoomInGame = renderer.minZoom;
        renderer.setScale(fit);
        renderer.camerascale = fit;
        Core.camera.width = Core.graphics.getWidth() / fit;
        Core.camera.height = Core.graphics.getHeight() / fit;
        Core.camera.position.set(PinyinDefenseMap.width * 4f, PinyinDefenseMap.height * 4f - Scl.scl(8f) / fit);
    }

    private void movePilot(int slot){
        Unit pilot = pilots[slot];
        if(pilot == null || !pilot.isAdded()) return;
        PinyinPlayerInput.updateSurvival(pilot, slot == 1, null, enabled());
        pilot.elevation = 1f;
        if(enabled() && dash[slot] > 2.5f) pilot.moveAt(Tmp.v1.set(pilot.vel).scl(2.5f));
        pilot.x = Mathf.clamp(pilot.x, 18f, PinyinDefenseMap.width * 8f - 18f);
        pilot.y = Mathf.clamp(pilot.y, 18f, PinyinDefenseMap.height * 8f - 18f);
    }

    private void buildHud(){
        hud.clearChildren();
        hud.setFillParent(true);
        hud.touchable = Touchable.childrenOnly;
        hud.top();
        hud.table(Styles.black6, top -> {
            top.defaults().pad(3f);
            top.label(() -> "拼音塔防 · 第" + chapter + "关 · " + game.map.name + " · " + (game.map.routes.size == 2 ? "双线进攻" : "曲折防线"))
                .color(Pal.accent).row();
            top.label(() -> "基地 " + (int)game.baseHealth + "/1000  |  波次 " + game.wave + "/20  |  怪物 " + game.enemies.size
                + "  |  资源晶体 " + game.resources + "/3（" + (int)Math.ceil(PinyinDefense.resourceInterval - game.resourceTimer) + "秒）").row();
            var notice = top.label(() -> messageTime > 0f ? message : "击杀 " + game.kills + "  ·  "
                + (game.wave < 20 ? "下一波 " + Math.max(0, (int)Math.ceil(game.nextWave)) + "秒" : "清除剩余敌人") + "  ·  强化炮台守住防线")
                .wrap().width(860f).get();
            notice.update(() -> notice.setColor(messageTime > 0f ? messageColor : Pal.accent));
        }).growX();
        hud.row();
        hud.add().grow();
        hud.row();
        hud.table(Styles.black6, footer -> {
            footer.defaults().pad(3f);
            footer.table(loadouts -> {
                for(int slot = 0; slot < 2; slot++){
                    final int owner = slot;
                    loadouts.label(() -> "P" + (owner + 1) + "  " + plannedKind(owner).label)
                        .color(slot == 0 ? Pal.accent : Pal.lancerLaser).width(200f).padRight(12f);
                    var build = loadouts.button("", () -> openQuestion(owner)).width(190f).height(32f).padRight(16f).get();
                    build.update(() -> {
                        String key = owner == 0 ? "J" : "手柄 A";
                        float cooldown = game.constructionCooldown[owner];
                        build.setText(boards[owner] != null ? "重听 [" + key + "]" : cooldown > 0f ? "冷却 " + (int)Math.ceil(cooldown) + "s [" + key + "]" : "建造 / 升级 [" + key + "]");
                        build.setDisabled(!enabled() || boards[owner] == null && cooldown > 0f);
                    });
                }
            }).row();
            footer.add("P1  WASD · J建造 · K冲刺 · L换塔    |    P2  左摇杆 · A建造 · B冲刺 · X换塔    |    移动拾取答案")
                .color(Color.lightGray).center().row();
            footer.table(actions -> {
                actions.button("重新开始", () -> { chapter = 1; loadMap(); }).width(140f).height(32f).padRight(12f);
                actions.button("返回乐园", () -> { stop(); state.set(GameState.State.menu); ui.pinyin.show(); }).width(140f).height(32f);
            });
        }).growX();
    }

    private TowerKind plannedKind(int slot){
        PinyinDefensePickup board = boards[slot];
        if(board != null) return game.towers[board.pad] == null ? board.kind : game.towers[board.pad].kind;
        int pad = focus[slot] < 0 ? game.recommendedPad() : focus[slot];
        return game.towers[pad] != null ? game.towers[pad].kind : customKind[slot] ? game.deck.get(selected[slot]) : game.recommendedKind(pad);
    }

    private void update(){
        if(!isActive() || !state.isPlaying()) return;
        ui.hudfrag.shown = false;
        fitCamera();
        if(Core.scene.hasDialog()) return;
        float dt = Math.min(Time.delta / 60f, 0.1f);
        messageTime = Math.max(0f, messageTime - dt);
        voiceTime = Math.max(0f, voiceTime - dt);
        impactSoundTime = Math.max(0f, impactSoundTime - dt);
        deathSoundTime = Math.max(0f, deathSoundTime - dt);
        baseFlash = Math.max(0f, baseFlash - dt);
        for(int i = 0; i < fireSoundCooldown.length; i++) fireSoundCooldown[i] = Math.max(0f, fireSoundCooldown[i] - dt);
        for(int i = 0; i < 2; i++) dash[i] = Math.max(0f, dash[i] - dt);
        for(int i = 0; i < assembly.length; i++){
            assembly[i] = Math.max(0f, assembly[i] - dt);
            recoil[i] = Math.max(0f, recoil[i] - dt * 5f);
            answerResult[i] = Math.max(0f, answerResult[i] - dt);
        }
        game.update(dt);
        if(game.wave != lastWave){
            lastWave = game.wave;
            announce("第" + game.wave + "/20波抵达" + (game.wave % 5 == 0 ? " · 首领率队来袭！" : " · 补给已就绪，强化你的防线"), Pal.remove, 3f);
        }
        if(game.won || game.lost){
            if(endTime == 0f){
                pronunciation.stop();
                if(game.lost){
                    Fx.coreExplosion.at(game.map.base.x, game.map.base.y);
                    play(Sounds.explosionCore, game.map.base, 1.3f);
                    renderer.shake(12f, 40f);
                }
            }
            boards[0] = boards[1] = null;
            endTime += dt;
            if(game.won && endTime >= 6f){ chapter++; loadMap(); }
            return;
        }
        if(!enabled()){
            for(int slot = 0; slot < 2; slot++) previousPosition[slot].set(pilots[slot].x, pilots[slot].y);
            return;
        }
        for(int slot = 0; slot < 2; slot++){
            Unit pilot = pilots[slot];
            game.playerAttack(slot, pilot.x, pilot.y, dt);
            if(slot == 0 ? Core.input.keyTap(KeyCode.k) : PinyinGamepad.state.connected && PinyinGamepad.state.dashTap){
                if(dash[slot] <= 0f){ dash[slot] = 2.8f; Fx.shockwave.at(pilot); }
            }
            if(slot == 0 ? Core.input.keyTap(KeyCode.l) : PinyinGamepad.state.connected && PinyinGamepad.state.prankTap){
                selected[slot] = (selected[slot] + 1) % game.deck.size;
                customKind[slot] = true;
                announce("P" + (slot + 1) + "已装备：" + game.deck.get(selected[slot]).label + " · " + game.deck.get(selected[slot]).description, Pal.accent, 2f);
            }
            focus[slot] = nearestPad(pilot);
            if(slot == 0 ? Core.input.keyTap(KeyCode.j) : PinyinGamepad.state.connected && PinyinGamepad.state.fireTap) openQuestion(slot);
        }
        for(int owner = 0; owner < 2; owner++){
            PinyinDefensePickup board = boards[owner];
            if(board == null) continue;
            float lastAge = board.age;
            board.age += dt;
            for(int slot = 0; slot < 2; slot++){
                Unit pilot = pilots[slot];
                Vec2 from = previousPosition[slot];
                int choice = board.collect(lastAge < PinyinDefensePickup.landingTime ? pilot.x : from.x,
                    lastAge < PinyinDefensePickup.landingTime ? pilot.y : from.y, pilot.x, pilot.y);
                if(choice >= 0){ answer(owner, choice); break; }
            }
        }
        if(voiceTime <= 0f) for(PinyinDefensePickup board : boards) if(board != null && !board.spoken){
            pronunciation.speak(board.question.character);
            board.spoken = true;
            voiceTime = 2.5f;
            break;
        }
        for(int slot = 0; slot < 2; slot++) previousPosition[slot].set(pilots[slot].x, pilots[slot].y);
    }

    private int nearestPad(Unit pilot){
        int pad = -1;
        float nearest = 78f;
        for(int i = 0; i < game.map.pads.length; i++){
            float distance = game.map.pads[i].dst(pilot.x, pilot.y);
            if(distance < nearest){ nearest = distance; pad = i; }
        }
        return pad;
    }

    private void openQuestion(int slot){
        if(!enabled()) return;
        if(boards[slot] != null){
            if(voiceTime <= 0f) boards[slot].spoken = false;
            return;
        }
        if(game.constructionCooldown[slot] > 0f){
            announce("P" + (slot + 1) + " 建造补给冷却 " + (int)Math.ceil(game.constructionCooldown[slot]) + "秒", Pal.accent, 1.2f);
            return;
        }
        int pad = nearestPad(pilots[slot]);
        if(pad < 0){ announce("靠近带 + 标记的炮位或已有炮台，再打开建造补给", Pal.accent, 2f); return; }
        if(boards[1 - slot] != null && boards[1 - slot].pad == pad){
            announce("这个炮位的补给已经展开，走过去拾取即可", Pal.accent, 2f); return;
        }
        if(game.resources <= 0){ announce("补给充能中 · 每5秒恢复一颗建造晶体", Pal.accent, 2f); return; }
        focus[slot] = pad;
        if(!game.reserveConstruction(slot)) return;
        if(questionBag.isEmpty()){
            questionBag.addAll(PinyinQuestions.all);
            questionBag.shuffle();
        }
        Seq<Vec2> occupied = new Seq<>();
        for(PinyinDefensePickup other : boards) if(other != null) occupied.addAll(other.positions);
        for(Unit pilot : pilots) occupied.add(new Vec2(pilot.x, pilot.y));
        PinyinDefensePickup board = new PinyinDefensePickup(game.map, pad, plannedKind(slot), questionBag.pop(), lootRandom, occupied);
        boards[slot] = board;
        Fx.unitSpawn.at(game.map.pads[pad].x, game.map.pads[pad].y);
        play(Sounds.payloadDrop1, game.map.pads[pad], 0.7f);
    }

    private void answer(int slot, int choice){
        if(!enabled() || boards[slot] == null) return;
        PinyinDefensePickup board = boards[slot];
        boards[slot] = null;
        boolean right = board.correct(choice);
        game.answer(right, board.pad, board.kind);
        answerResult[board.pad] = right ? 1.8f : 2.2f;
        answerFailed[board.pad] = !right;
        resultKind[board.pad] = board.kind;
        Vec2 place = game.map.pads[board.pad];
        if(right){
            Vec2 picked = board.positions[choice];
            friendPickup.at(picked.x, picked.y + 8f, 0f, friendColors[friendStyle(board, choice)]);
            Fx.upgradeCore.at(place.x, place.y);
            Fx.placeBlock.at(place.x, place.y, 3f, towerColor(board.kind));
            play(Sounds.blockPlace2, place, 0.9f);
            assembly[board.pad] = 0.85f;
            announce(game.towers[board.pad].kind.label + " Lv." + game.towers[board.pad].level + " · 组装完成，防线火力提升", Pal.heal, 2f);
            Sounds.uiUnlock.play();
        }else{
            Fx.blastExplosion.at(place.x, place.y);
            Fx.breakBlock.at(place.x, place.y, 3f, Pal.remove);
            play(Sounds.blockBreak2, place, 0.8f);
            renderer.shake(2f, 9f);
            announce("补给损坏 · “" + board.question.character + "”选 " + board.question.pinyin + " · 小心怪物突破！", Pal.remove, 3f);
            Sounds.uiBack.play();
        }
    }

    private void announce(String text, Color color, float seconds){ message = text; messageColor = color; messageTime = seconds; }

    private BulletType ammunition(TowerKind kind){
        return switch(kind){
            case rail -> ((ItemTurret)Blocks.foreshadow).ammoTypes.get(Items.surgeAlloy);
            case boomerang -> ((ItemTurret)Blocks.ripple).ammoTypes.get(Items.plastanium);
            case pulse -> ((ItemTurret)Blocks.spectre).ammoTypes.get(Items.thorium);
            default -> PinyinArsenal.source(switch(kind){
                case scatter -> PinyinArsenal.Kind.scatter;
                case artillery -> PinyinArsenal.Kind.artillery;
                case missile -> PinyinArsenal.Kind.missile;
                case laser -> PinyinArsenal.Kind.laser;
                case lightning, vortex -> PinyinArsenal.Kind.lightning;
                case frost -> PinyinArsenal.Kind.ice;
                case flame -> PinyinArsenal.Kind.flame;
                case poison -> PinyinArsenal.Kind.slag;
                case fragment -> PinyinArsenal.Kind.fragment;
                default -> PinyinArsenal.Kind.rapid;
            });
        };
    }

    private void play(Sound sound, Vec2 point, float volume){
        sound.at(point.x, point.y, Mathf.random(0.94f, 1.06f), volume * (voiceTime > 0f ? 0.45f : 1f));
    }

    private void shotFired(Shot shot){
        if(!isActive()) return;
        float angle = shot.from.angleTo(shot.to);
        int channel = shot.hostile() ? 14 : shot.enemyPad == -3 ? 15 : shot.kind.ordinal();
        Sound sound;
        if(shot.hostile()){
            Fx.shootBig.at(shot.from.x, shot.from.y, angle, Pal.remove);
            UnitType unit = enemyIcon(shot.attacker.kind);
            sound = unit.weapons.isEmpty() ? Sounds.shootAlpha : unit.weapons.first().shootSound;
        }else{
            BulletType bullet = ammunition(shot.kind);
            float x = shot.from.x + Angles.trnsx(angle, 17f), y = shot.from.y + 6f + Angles.trnsy(angle, 17f);
            bullet.shootEffect.at(x, y, angle, towerColor(shot.kind));
            bullet.smokeEffect.at(x, y, angle, towerColor(shot.kind));
            if(shot.enemyPad != -3){
                for(int pad = 0; pad < game.map.pads.length; pad++) if(game.map.pads[pad].within(shot.from, 1f)) recoil[pad] = 1f;
                if(shot.kind == TowerKind.rapid || shot.kind == TowerKind.scatter) Fx.casing1.at(shot.from.x, shot.from.y, angle);
                if(shot.kind == TowerKind.rail) renderer.shake(2.5f, 8f);
                if(shot.kind == TowerKind.artillery) renderer.shake(1.1f, 5f);
            }
            Block block = towerBlock(shot.kind);
            sound = shot.enemyPad == -3 ? Sounds.shootAlpha : shot.kind == TowerKind.vortex ? Sounds.shootSap
                : shot.kind == TowerKind.pulse ? Sounds.shockwaveTower : block instanceof Turret turret ? turret.shootSound : Sounds.shootArc;
            if(sound == Sounds.none) sound = Sounds.shootAtrax;
        }
        if(fireSoundCooldown[channel] <= 0f){
            play(sound, shot.from, shot.enemyPad == -3 ? 0.22f : 0.65f);
            fireSoundCooldown[channel] = 0.07f;
        }
    }

    private void shotImpact(Shot shot){
        if(!isActive()) return;
        Sound sound;
        float volume = 0.5f;
        if(shot.hostile()){
            boolean heavy = shot.attacker != null && (shot.attacker.kind == EnemyKind.boss || shot.attacker.kind == EnemyKind.siege || shot.attacker.kind == EnemyKind.bomber);
            (heavy ? Fx.blastExplosion : Fx.hitBulletBig).at(shot.to.x, shot.to.y, 0f, Pal.remove);
            sound = heavy ? Sounds.explosionArtillery : Sounds.shieldHit;
            if(shot.enemyPad == -1){
                baseFlash = 0.35f;
                Fx.shockwave.at(shot.to.x, shot.to.y);
                renderer.shake(heavy ? 5f : 2.5f, heavy ? 16f : 9f);
            }else renderer.shake(heavy ? 2f : 0.6f, 6f);
        }else{
            BulletType bullet = ammunition(shot.kind);
            bullet.hitEffect.at(shot.to.x, shot.to.y, shot.from.angleTo(shot.to), towerColor(shot.kind));
            sound = bullet.hitSound;
            if(sound == Sounds.none){ sound = Sounds.shieldHit; volume = 0.16f; }
            switch(shot.kind){
                case artillery, missile, fragment -> { Fx.blastExplosion.at(shot.to.x, shot.to.y); renderer.shake(1.8f, 7f); }
                case rail -> { Fx.instHit.at(shot.to.x, shot.to.y, shot.from.angleTo(shot.to), Pal.lancerLaser); renderer.shake(3.5f, 11f); }
                case frost -> Fx.freezing.at(shot.to.x, shot.to.y);
                case flame -> Fx.fireHit.at(shot.to.x, shot.to.y);
                case poison -> Fx.greenCloud.at(shot.to.x, shot.to.y);
                case vortex, pulse -> { Fx.shockwave.at(shot.to.x, shot.to.y); renderer.shake(1.3f, 7f); }
                default -> {}
            }
        }
        if(impactSoundTime <= 0f){ play(sound, shot.to, volume); impactSoundTime = 0.035f; }
    }

    private void enemyDestroyed(Enemy enemy){
        if(!isActive()) return;
        UnitType type = enemyIcon(enemy.kind);
        boolean boss = enemy.kind == EnemyKind.boss;
        type.deathExplosionEffect.at(enemy.position.x, enemy.position.y, boss ? 2.8f : enemy.kind == EnemyKind.armor ? 1.4f : 0.7f);
        Fx.unitWreck.at(enemy.position.x, enemy.position.y, enemy.angle, Pal.rubble, type.fullIcon);
        if(boss){ Fx.massiveExplosion.at(enemy.position.x, enemy.position.y); Fx.bigShockwave.at(enemy.position.x, enemy.position.y); }
        if(enemy.kind == EnemyKind.bomber) Fx.blastExplosion.at(enemy.position.x, enemy.position.y);
        renderer.shake(boss ? 10f : enemy.kind == EnemyKind.armor || enemy.kind == EnemyKind.bomber ? 3.5f : 1.3f, boss ? 30f : 8f);
        if(boss || deathSoundTime <= 0f){
            play(boss ? Sounds.unitExplode3 : type.deathSound, enemy.position, boss ? 1.2f : 0.7f);
            deathSoundTime = 0.06f;
        }
    }

    private void towerDestroyed(Tower tower){
        if(!isActive()) return;
        Vec2 point = game.map.pads[tower.pad];
        Fx.massiveExplosion.at(point.x, point.y);
        Fx.breakBlock.at(point.x, point.y, 3f, towerColor(tower.kind));
        play(Sounds.blockExplode2, point, 0.9f);
        renderer.shake(5f, 17f);
    }

    private Color towerColor(TowerKind kind){
        return switch(kind){
            case frost -> Color.sky;
            case flame, artillery -> Pal.lightOrange;
            case poison, vortex -> Pal.heal;
            case laser, lightning, rail -> Pal.lancerLaser;
            default -> Pal.accent;
        };
    }

    private Block towerBlock(TowerKind kind){
        return switch(kind){
            case rapid -> Blocks.duo;
            case scatter -> Blocks.scatter;
            case artillery -> Blocks.hail;
            case missile -> Blocks.swarmer;
            case laser -> Blocks.lancer;
            case lightning -> Blocks.arc;
            case frost -> Blocks.wave;
            case flame -> Blocks.scorch;
            case poison -> Blocks.tsunami;
            case rail -> Blocks.foreshadow;
            case fragment -> Blocks.fuse;
            case boomerang -> Blocks.ripple;
            case vortex -> Blocks.parallax;
            case pulse -> Blocks.spectre;
        };
    }

    private UnitType enemyIcon(EnemyKind kind){
        return switch(kind){
            case grunt -> UnitTypes.dagger;
            case runner -> UnitTypes.mace;
            case swarm -> UnitTypes.flare;
            case armor -> UnitTypes.fortress;
            case shield -> UnitTypes.quasar;
            case regen -> UnitTypes.atrax;
            case medic -> UnitTypes.poly;
            case splitter -> UnitTypes.spiroct;
            case bomber -> UnitTypes.crawler;
            case siege -> UnitTypes.scepter;
            case jammer -> UnitTypes.nova;
            case burrow -> UnitTypes.arkyid;
            case flyer -> UnitTypes.horizon;
            case leech -> UnitTypes.vela;
            case boss -> UnitTypes.reign;
        };
    }

    private void draw(){
        if(!isActive()) return;
        Draw.z(Layer.blockOver + 1f);
        // A light atmospheric tint ties native terrain tiles into a single landscape.
        Draw.color(atmosphere, game.map.theme == 2 ? 0.17f : 0.08f);
        Fill.rect(PinyinDefenseMap.width * 4f - 4f, PinyinDefenseMap.height * 4f - 4f, PinyinDefenseMap.width * 8f, PinyinDefenseMap.height * 8f);
        for(int r = 0; r < game.map.routes.size; r++){
            Color color = r == 0 ? Pal.lightOrange : Pal.lancerLaser;
            Vec2[] route = game.map.routes.get(r);
            Draw.color(color, 0.16f); Lines.stroke(1f);
            for(int i = 1; i < route.length; i++){
                Lines.line(route[i - 1].x, route[i - 1].y, route[i].x, route[i].y);
                Vec2 mid = Tmp.v1.set(route[i - 1]).lerp(route[i], 0.5f);
                Draw.color(color, 0.5f);
                Draw.rect(Icon.right.getRegion(), mid.x, mid.y, 9f, 9f, route[i - 1].angleTo(route[i]));
                float angle = route[i - 1].angleTo(route[i]);
                for(float distance = 35f; distance < route[i - 1].dst(route[i]) - 20f; distance += 96f){
                    float x = route[i - 1].x + Angles.trnsx(angle, distance) + Angles.trnsx(angle + 90f, 22f);
                    float y = route[i - 1].y + Angles.trnsy(angle, distance) + Angles.trnsy(angle + 90f, 22f);
                    Draw.color(Color.black, 0.35f); Fill.circle(x + 2f, y - 2f, 3f);
                    Draw.color(Color.darkGray); Fill.square(x, y, 2.5f);
                    Draw.color(color, 0.8f); Fill.circle(x, y + 1f, 1.3f);
                }
                Draw.color(color, 0.16f);
            }
            Draw.color(color, 0.16f); Fill.circle(route[0].x, route[0].y, 25f);
            Draw.color(color, 0.8f); Lines.stroke(2f);
            Lines.arc(route[0].x, route[0].y, 23f, 0.75f, game.elapsed * 18f);
            Lines.poly(route[0].x, route[0].y, 6, 18f, -game.elapsed * 12f);
            Fonts.outline.draw("裂隙 " + (r + 1), route[0].x, route[0].y + 34f, color, 0.48f, false, Align.center);
        }
        Draw.z(Layer.turret + 1f);
        for(int i = 0; i < game.towers.length; i++){
            Vec2 point = game.map.pads[i];
            Tower tower = game.towers[i];
            boolean result = answerResult[i] > 0f;
            Color color = result ? answerFailed[i] ? Pal.remove : Pal.heal : tower == null ? Color.gray : towerColor(tower.kind);
            boolean focused = focus[0] == i || focus[1] == i || boards[0] != null && boards[0].pad == i || boards[1] != null && boards[1].pad == i;
            Draw.color(Color.black, 0.4f); Fill.poly(point.x + 5f, point.y - 5f, 8, 24f, 22.5f);
            Draw.color(pedestalDark); Fill.poly(point.x, point.y - 2f, 8, 23f, 22.5f);
            Draw.color(pedestalRim); Fill.poly(point.x, point.y + 1f, 8, 22f, 22.5f);
            Draw.color(pedestalTop); Fill.poly(point.x, point.y + 2f, 8, 18.5f, 22.5f);
            Draw.color(color, focused ? 0.8f : 0.35f); Lines.stroke(1.2f); Lines.poly(point.x, point.y + 2f, 8, 20f, 22.5f);
            if(focused){ Draw.alpha(0.22f); Lines.dashCircle(point.x, point.y, tower == null ? plannedKind(focus[0] == i ? 0 : 1).range : tower.kind.range); }
            if(tower == null){
                Draw.color(Pal.accent, focused ? 1f : 0.4f);
                Draw.rect(Icon.add.getRegion(), point.x, point.y + 3f, 13f, 13f);
                if(result && answerFailed[i]){
                    TextureRegion broken = towerBlock(resultKind[i]).region;
                    Draw.color(Pal.remove, 0.6f);
                    Draw.rect(broken, point.x, point.y + 6f, 32f, 32f * broken.height / broken.width);
                }
                if(!focused) Fonts.outline.draw("" + (i + 1), point.x, point.y - 11f, Color.lightGray, 0.32f, false, Align.center);
            }else{
                TextureRegion top = towerBlock(tower.kind).region;
                float built = 1f - assembly[i] / 0.85f, size = 32f * (0.65f + built * 0.35f) * (1f + Math.min(tower.level - 1, 15) * 0.012f);
                float height = 6f + (1f - built) * 22f;
                float kick = recoil[i] * (tower.kind == TowerKind.rail ? 6f : 3f);
                float topX = point.x - Angles.trnsx(tower.angle, kick), topY = point.y + height - Angles.trnsy(tower.angle, kick);
                if(result && answerFailed[i]) topX += Mathf.sin((2.2f - answerResult[i]) * 70f) * 2f;
                Draw.color(Color.black, 0.4f);
                Draw.rect(top, point.x + 6f, point.y - 3f, size, size * top.height / top.width, tower.angle - 90f);
                Draw.color(Color.white);
                if(result) Draw.mixcol(color, answerFailed[i] ? 0.65f + Mathf.absin(2f, 0.3f) : Math.min(answerResult[i], 1f) * 0.7f);
                Draw.rect(top, topX, topY, size, size * top.height / top.width, tower.angle - 90f);
                Draw.mixcol();
                Draw.color(color);
                // Visible upgrade modules change the turret silhouette as levels rise.
                for(int module = 0; module < Math.min(tower.level - 1, 8); module++){
                    float a = 45f + module * 45f;
                    Fill.square(point.x + Angles.trnsx(a, 20f), point.y + 3f + Angles.trnsy(a, 20f), 2.2f, 45f);
                }
                Draw.color(color, 0.7f); Fill.circle(point.x, point.y + 3f, 2.5f);
                if(assembly[i] > 0f){
                    Draw.color(color, 0.45f); Lines.stroke(1f);
                    Lines.poly(point.x, point.y + height, 8, 25f, 22.5f);
                    Lines.line(point.x - 18f, point.y + height, point.x + 18f, point.y + height);
                }
                if(focused || tower.health < tower.maxHealth()) bar(point.x, point.y + 30f, 32f, tower.health / tower.maxHealth(), Pal.heal);
                Fonts.outline.draw(tower.jam > 0f ? "干扰" : "L" + tower.level, point.x, point.y - 15f, color, 0.35f, false, Align.center);
            }
            if(result){
                Draw.color(color); Lines.stroke(2.4f);
                if(answerFailed[i]){
                    Lines.line(point.x - 10f, point.y - 5f, point.x + 10f, point.y + 15f);
                    Lines.line(point.x - 10f, point.y + 15f, point.x + 10f, point.y - 5f);
                    for(int shard = 0; shard < 4; shard++){
                        float a = shard * 90f + 45f, spread = 23f + (2.2f - answerResult[i]) * 8f;
                        Drawf.tri(point.x + Angles.trnsx(a, spread), point.y + Angles.trnsy(a, spread), 4f, 7f, a);
                    }
                }else{
                    Lines.line(point.x - 10f, point.y + 3f, point.x - 3f, point.y - 3f);
                    Lines.line(point.x - 3f, point.y - 3f, point.x + 11f, point.y + 13f);
                    Lines.poly(point.x, point.y + 3f, 8, 25f + (1.8f - answerResult[i]) * 8f, 22.5f);
                }
            }
            boolean opened = boards[0] != null && boards[0].pad == i || boards[1] != null && boards[1].pad == i;
            if(focused && !opened){
                int owner = focus[0] == i ? 0 : 1;
                float cooldown = game.constructionCooldown[owner];
                if(focus[0] == i && focus[1] == i) cooldown = Math.min(cooldown, game.constructionCooldown[1]);
                String text = cooldown > 0f ? "冷却 " + (int)Math.ceil(cooldown) + "s" : (focus[0] == i ? focus[1] == i ? "J / A" : "J" : "A") + (tower == null ? " 建造" : " 升级");
                Fonts.outline.draw(text, point.x, point.y - 32f, cooldown > 0f ? Color.lightGray : Pal.accent, 0.48f, false, Align.center);
            }
        }
        Draw.z(Layer.flyingUnitLow + 1f);
        for(Enemy enemy : game.enemies){
            Color color = enemy.poison > 0f ? Pal.heal : enemy.slow > 0f ? Color.sky : enemy.kind == EnemyKind.boss ? Pal.remove : Pal.lightOrange;
            float size = enemy.kind == EnemyKind.boss ? 40f : enemy.kind == EnemyKind.armor ? 27f : 20f;
            Draw.color(color, 0.18f); Fill.circle(enemy.position.x, enemy.position.y, size * 0.7f);
            Draw.color(Color.white);
            if(enemy.hitFlash > 0f) Draw.mixcol(Color.white, enemy.hitFlash / 0.12f);
            Draw.rect(enemyIcon(enemy.kind).fullIcon, enemy.position.x, enemy.position.y, size, size, enemy.angle - 90f);
            Draw.mixcol();
            if(enemy.shield > 0f){ Draw.color(Pal.lancerLaser); Lines.stroke(1.5f); Lines.poly(enemy.position.x, enemy.position.y, 6, size * 0.8f); }
            if(enemy.burn > 0f){ Draw.color(Pal.lightOrange); Fill.circle(enemy.position.x + Mathf.sin(Time.time) * 5f, enemy.position.y + 9f, 4f); }
            if(enemy.kind == EnemyKind.medic){ Draw.color(Pal.heal, 0.4f); Lines.stroke(1f); Lines.circle(enemy.position.x, enemy.position.y, 28f + Mathf.absin(10f, 8f)); }
            if(enemy.kind == EnemyKind.jammer){
                Draw.color(Pal.remove, 0.65f); Lines.stroke(1.2f);
                for(Tower tower : game.towers) if(tower != null && tower.jam > 0f && game.map.pads[tower.pad].dst(enemy.position) < 120f)
                    Lines.line(enemy.position.x, enemy.position.y, game.map.pads[tower.pad].x, game.map.pads[tower.pad].y);
            }
            if(enemy.kind == EnemyKind.burrow){ Draw.color(Pal.lightOrange); Lines.dashCircle(enemy.position.x, enemy.position.y, size); }
            bar(enemy.position.x, enemy.position.y + size * 0.65f, size, enemy.health / enemy.maxHealth, color);
            if(enemy.kind == EnemyKind.boss || enemy.kind == EnemyKind.medic || enemy.kind == EnemyKind.jammer || enemy.kind == EnemyKind.siege)
                Fonts.outline.draw(enemy.kind.label, enemy.position.x, enemy.position.y - size, color, 0.45f, false, Align.center);
        }
        Draw.z(Layer.bullet + 1f);
        for(Shot shot : game.shots) drawShot(shot);
        Draw.z(Layer.playerName + 1f);
        for(int slot = 0; slot < 2; slot++) if(boards[slot] != null) drawPickups(slot, boards[slot]);
        Vec2 base = game.map.base;
        if(baseFlash > 0f){
            Draw.color(Pal.remove, baseFlash * 1.7f); Lines.stroke(3f); Lines.poly(base.x, base.y, 8, 35f, 22.5f);
        }
        bar(base.x, base.y + 30f, 70f, game.baseHealth / PinyinDefense.baseMax, game.baseHealth < 300f ? Pal.remove : Pal.heal);
        Fonts.outline.draw("基地 " + (int)game.baseHealth, base.x, base.y + 48f, Pal.accent, 0.38f, false, Align.center);
        for(int slot = 0; slot < 2; slot++){
            Unit pilot = pilots[slot];
            if(pilot == null) continue;
            Color color = slot == 0 ? Pal.accent : Pal.lancerLaser;
            Draw.color(color); Lines.stroke(1.5f); Lines.circle(pilot.x, pilot.y, 15f);
            Fonts.outline.draw("P" + (slot + 1), pilot.x, pilot.y + 26f, color, 0.38f, false, Align.center);
        }
        if(game.won || game.lost){
            float x = Core.camera.position.x, y = Core.camera.position.y;
            Draw.color(Color.black, 0.8f); Fill.rect(x, y, 600f, 120f);
            Fonts.outline.draw(game.won ? "20波全部守住！" : "基地失守", x, y + 28f, game.won ? Pal.heal : Pal.remove, 0.9f, false, Align.center);
            Fonts.outline.draw(game.won ? (6 - (int)endTime) + "秒后生成下一关新地图、新炮塔组合" : "答对 " + game.correct + "题 · 守到第" + game.wave + "波 · 点击重新开始再挑战",
                x, y - 20f, Color.white, 0.42f, false, Align.center);
        }
        Draw.reset();
    }

    private void drawPickups(int owner, PinyinDefensePickup board){
        Vec2 center = game.map.pads[board.pad];
        Color color = owner == 0 ? Pal.accent : Pal.lancerLaser;
        Draw.color(color, 0.18f);
        TextureRegion ghost = towerBlock(board.kind).region;
        Draw.rect(ghost, center.x, center.y + 9f, 34f, 34f * ghost.height / ghost.width);
        // The question is engraved into a cap fitted to the turret, rather than a separate card.
        int sides = switch(board.kind){ case laser, lightning, rail, vortex, pulse -> 6; case scatter, frost, poison -> 4; default -> 8; };
        float rotation = sides == 4 ? 45f : 22.5f;
        Draw.color(capsule, 0.95f); Fill.poly(center.x, center.y + 7f, sides, 16f, rotation);
        Draw.color(towerColor(board.kind)); Lines.stroke(1.8f); Lines.poly(center.x, center.y + 7f, sides, 17f, rotation);
        Fonts.outline.draw(board.question.character, center.x, center.y + 8f, Color.white, 1.15f, false, Align.center);
        Fonts.outline.draw("P" + (owner + 1), center.x - 19f, center.y + 24f, color, 0.35f, false, Align.center);
        for(int i = 0; i < 3; i++){
            Vec2 point = board.positions[i];
            float t = Mathf.clamp(board.age / 0.65f);
            float x = Mathf.lerp(center.x, point.x, Interp.pow2Out.apply(t));
            float y = Mathf.lerp(center.y, point.y, Interp.pow2Out.apply(t));
            int style = friendStyle(board, i);
            Color skin = friendColors[style];
            float phase = board.age * 3f + i * 2f;
            float lift = (1f - t) * 35f + Mathf.sin(t * Mathf.PI) * 25f + 7f + Mathf.sin(phase) * 2f;
            float squash = board.age > 0.65f && board.age < 1f ? Mathf.sin((board.age - 0.65f) / 0.35f * Mathf.PI) : 0f;
            Draw.color(friendInk, 0.20f); pill(x + 2f, y - 2f, 25f - lift * 0.1f, 7f);
            Draw.color(skin, 0.20f); Fill.circle(point.x, point.y, 17f);
            Draw.color(color, 0.55f); Lines.stroke(1f); Lines.circle(point.x, point.y, PinyinDefensePickup.pickupRadius);
            for(int petal = 0; petal < 6; petal++){
                float angle = petal * 60f;
                Draw.color(skin, 0.65f);
                Fill.circle(point.x + Angles.trnsx(angle, 16f), point.y + Angles.trnsy(angle, 16f), 1.8f);
            }
            drawFriend(x, y + lift, style, skin, phase, squash, Mathf.mod(board.age + i * 0.7f, 4.4f) > 4.2f);

            // Keep the whole label readable even for drops along the upper map edge.
            float labelY = y + lift + (y + lift > PinyinDefenseMap.height * 8f - 58f ? -29f : 37f);
            Draw.color(friendInk, 0.20f); pill(x + 1f, labelY - 2f, 65f, 24f);
            Draw.color(skin); pill(x, labelY, 65f, 24f);
            Draw.color(friendCream); pill(x, labelY + 1f, 61f, 20f);
            Draw.color(Color.white, 0.8f); Lines.stroke(1f); Lines.line(x - 20f, labelY + 8f, x + 13f, labelY + 8f);
            Fonts.outline.draw(board.choices[i], x, labelY + 8f, friendInk, 0.86f, false, Align.center);
            Draw.color(skin, 0.65f);
            pickupStar(x + 20f + Mathf.sin(phase) * 2f, y + lift + 13f, 2.8f + Mathf.sin(phase + 1f) * 0.6f);
            Drawf.light(x, y + lift, 30f, skin, 0.18f);
        }
    }

    private static int friendStyle(PinyinDefensePickup board, int choice){
        // Appearance follows the shuffled slot, independently of whether the answer is right.
        return Math.floorMod(board.question.character.hashCode() + board.pad + choice, friendColors.length);
    }

    private void drawFriend(float x, float y, int style, Color skin, float phase, float squash, boolean blink){
        float sway = Mathf.sin(phase) * 1.3f;
        if(style == 0){
            for(int side : Mathf.signs){
                Draw.color(friendInk); pill(x + side * (6f + sway * 0.4f), y + 14f, 7f, 18f);
                Draw.color(skin); pill(x + side * (6f + sway * 0.4f), y + 14f, 5f, 16f);
                Draw.color(friendBlush, 0.65f); pill(x + side * (6f + sway * 0.4f), y + 16f, 2f, 8f);
            }
        }else if(style == 1){
            Draw.color(friendInk);
            Fill.tri(x - 13f, y + 6f, x - 12f, y + 21f, x - 2f, y + 10f);
            Fill.tri(x + 13f, y + 6f, x + 12f, y + 21f, x + 2f, y + 10f);
            Draw.color(skin);
            Fill.tri(x - 11f, y + 7f, x - 11f, y + 18f, x - 3f, y + 10f);
            Fill.tri(x + 11f, y + 7f, x + 11f, y + 18f, x + 3f, y + 10f);
            Draw.color(friendBlush, 0.7f);
            Fill.tri(x - 10f, y + 11f, x - 10f, y + 16f, x - 6f, y + 11f);
            Fill.tri(x + 10f, y + 11f, x + 10f, y + 16f, x + 6f, y + 11f);
        }else{
            Draw.color(friendCream); pill(x - 14f, y + sway, 10f, 6f); pill(x + 14f, y - sway, 10f, 6f);
            Draw.color(skin); Lines.stroke(1.4f); Lines.line(x, y + 9f, x + sway, y + 18f);
            Draw.color(friendCream); pickupStar(x + sway, y + 20f, 5f);
        }
        float width = 26f + squash * 4f, height = 23f - squash * 4f;
        Draw.color(friendInk); pill(x, y, width + 2f, height + 2f);
        Draw.color(skin, friendShade, 0.25f); pill(x, y - 1f, width, height);
        Draw.color(skin); pill(x, y + 1.5f, width - 1f, height - 2f);
        Draw.color(Color.white, 0.55f); pill(x - 5f, y + 7f, 7f, 2.5f);
        Draw.color(friendBlush, 0.75f); pill(x - 8f, y - 2.5f, 5f, 2.5f); pill(x + 8f, y - 2.5f, 5f, 2.5f);
        Draw.color(friendInk);
        for(int side : Mathf.signs){
            if(blink){ Lines.stroke(1.4f); Lines.line(x + side * 5f - 1.7f, y + 1f, x + side * 5f + 1.7f, y + 1f); }
            else{
                pill(x + side * 5f, y + 2f, 3f, 4.3f);
                Draw.color(Color.white); Fill.circle(x + side * 5f - 0.4f, y + 3f, 0.75f); Draw.color(friendInk);
            }
        }
        Lines.stroke(1.1f); Lines.arc(x, y + 0.5f, 2.5f, 0.32f, 212f);
        Draw.color(skin); pill(x - 8f, y - height / 2f, 7f, 4f); pill(x + 8f, y - height / 2f, 7f, 4f);
    }

    private static void pill(float x, float y, float width, float height){
        float radius = Math.min(width, height) / 2f;
        if(width >= height){
            Fill.rect(x, y, width - height, height);
            Fill.circle(x - width / 2f + radius, y, radius); Fill.circle(x + width / 2f - radius, y, radius);
        }else{
            Fill.rect(x, y, width, height - width);
            Fill.circle(x, y - height / 2f + radius, radius); Fill.circle(x, y + height / 2f - radius, radius);
        }
    }

    private static void pickupStar(float x, float y, float radius){
        for(int side = 0; side < 4; side++) Drawf.tri(x, y, radius * 0.8f, radius, side * 90f);
    }

    private void drawShot(Shot shot){
        Color color = shot.hostile() ? Pal.remove : towerColor(shot.kind);
        float t = Mathf.clamp(shot.age / shot.duration);
        Draw.color(color);
        if(shot.kind == TowerKind.laser || shot.kind == TowerKind.rail || shot.kind == TowerKind.lightning){
            Lines.stroke((1f - t) * 3f + 0.8f);
            if(shot.kind == TowerKind.lightning){
                float dx = shot.to.x - shot.from.x, dy = shot.to.y - shot.from.y;
                float mx = (shot.from.x + shot.to.x) / 2f - dy * 0.08f, my = (shot.from.y + shot.to.y) / 2f + dx * 0.08f;
                Lines.line(shot.from.x, shot.from.y, mx, my); Lines.line(mx, my, shot.to.x, shot.to.y);
            }else Lines.line(shot.from.x, shot.from.y, shot.to.x, shot.to.y);
        }else{
            float x = Mathf.lerp(shot.from.x, shot.to.x, t), y = Mathf.lerp(shot.from.y, shot.to.y, t);
            float angle = shot.from.angleTo(shot.to);
            if(shot.kind == TowerKind.artillery || shot.attacker != null && shot.attacker.kind == EnemyKind.siege) y += Mathf.sin(t * Mathf.PI) * 45f;
            if(shot.hostile()){
                Fill.circle(x, y, shot.attacker != null && shot.attacker.kind == EnemyKind.boss ? 6f : 3.5f);
                Lines.stroke(1.5f); Lines.lineAngle(x, y, angle + 180f, 12f);
                if(shot.attacker != null && shot.attacker.kind == EnemyKind.bomber) Lines.circle(x, y, 7f + t * 12f);
            }else switch(shot.kind){
                case boomerang -> { Lines.stroke(2f); Lines.arc(x, y, 6f, 0.75f, shot.age * 8f); }
                case missile -> {
                    Fill.tri(x + Angles.trnsx(angle, 6f), y + Angles.trnsy(angle, 6f),
                        x + Angles.trnsx(angle + 130f, 5f), y + Angles.trnsy(angle + 130f, 5f),
                        x + Angles.trnsx(angle - 130f, 5f), y + Angles.trnsy(angle - 130f, 5f));
                    Draw.color(Pal.lightOrange); Lines.stroke(2f); Lines.lineAngle(x, y, angle + 180f, 15f);
                }
                case frost -> { Lines.stroke(1.5f); Lines.poly(x, y, 4, 6f, 45f); Lines.lineAngleCenter(x, y, 0f, 12f); Lines.lineAngleCenter(x, y, 90f, 12f); }
                case flame -> { Draw.alpha(0.7f); Fill.circle(x, y, 7f); Fill.circle(x - Angles.trnsx(angle, 8f), y - Angles.trnsy(angle, 8f), 5f); }
                case poison -> { Draw.alpha(0.55f); Fill.circle(x, y, 8f); Fill.circle(x + 5f, y - 5f, 5f); }
                case vortex -> { Lines.stroke(1.5f); Lines.arc(x, y, 9f, 0.7f, shot.age * 7f); Lines.arc(x, y, 5f, 0.7f, -shot.age * 9f); }
                case pulse -> { Lines.stroke(2f); Lines.circle(x, y, 5f + t * 9f); }
                case fragment -> { Fill.poly(x, y, 4, 5f, shot.age * 250f); }
                default -> { Fill.circle(x, y, 2.8f); Lines.stroke(1.5f); Lines.lineAngle(x, y, angle + 180f, 8f); }
            }
            Drawf.light(x, y, 18f, color, 0.5f);
        }
        if(shot.resolved){
            float fade = 1f - Mathf.clamp((shot.age - shot.duration) / 0.2f);
            Draw.color(color, fade);
            Lines.stroke(2f * fade);
            Lines.circle(shot.to.x, shot.to.y, 5f + (1f - fade) * (shot.kind == TowerKind.artillery || shot.kind == TowerKind.pulse ? 55f : 14f));
        }
    }

    private void bar(float x, float y, float width, float fraction, Color color){
        Draw.color(Color.black, 0.8f); Fill.rect(x, y, width, 3f);
        float fill = Mathf.clamp(fraction) * width;
        Draw.color(color); Fill.rect(x - width / 2f + fill / 2f, y, fill, 3f);
    }

}
