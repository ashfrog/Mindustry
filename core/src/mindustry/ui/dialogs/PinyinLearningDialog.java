package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.style.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

/** A short, click-first pinyin game for young players. */
public class PinyinLearningDialog extends BaseDialog{
    private static final int ROUNDS = 10;
    private static final int MAP_WIDTH = 5;
    private static final int MAP_HEIGHT = 4;
    private static final int ENERGY_NEEDED = 3;
    private static final int[][] CHEST_POSITIONS = {{1, 2}, {2, 1}, {3, 3}, {1, 0}};
    private static final EscapeChest[] ESCAPE_CHESTS = {
        new EscapeChest("bā", "八", "爸", "拔"),
        new EscapeChest("māo", "猫", "马", "鸟"),
        new EscapeChest("yú", "鱼", "雨", "兔"),
        new EscapeChest("fēi", "飞", "非", "肥")
    };

    private static final Question[][] QUESTIONS = {
        {
            new Question("b", "八 bā", "米 mǐ", "猫 māo"),
            new Question("p", "葡萄 pú tao", "猫 māo", "八 bā"),
            new Question("m", "妈妈 mā ma", "飞机 fēi jī", "鱼 yú"),
            new Question("f", "飞机 fēi jī", "苹果 píng guǒ", "鸟 niǎo"),
            new Question("d", "大树 dà shù", "猫 māo", "花 huā"),
            new Question("t", "兔子 tù zi", "米 mǐ", "鹅 é"),
            new Question("n", "牛奶 niú nǎi", "书 shū", "鱼 yú"),
            new Question("l", "辣椒 là jiāo", "苹果 píng guǒ", "猫 māo"),
            new Question("g", "公鸡 gōng jī", "兔子 tù zi", "米 mǐ"),
            new Question("k", "可乐 kě lè", "花 huā", "鸟 niǎo")
        },
        {
            new Question("a", "大树 dà shù", "小鱼 xiǎo yú", "雨伞 yǔ sǎn"),
            new Question("o", "公鸡 gōng jī", "小猫 xiǎo māo", "苹果 píng guǒ"),
            new Question("e", "白鹅 bái é", "飞机 fēi jī", "兔子 tù zi"),
            new Question("i", "小鸡 xiǎo jī", "大树 dà shù", "河马 hé mǎ"),
            new Question("u", "小兔 xiǎo tù", "白鹅 bái é", "小鸡 xiǎo jī"),
            new Question("ü", "小鱼 xiǎo yú", "小猫 xiǎo māo", "大牛 dà niú"),
            new Question("ai", "爱心 ài xīn", "月亮 yuè liang", "书包 shū bāo"),
            new Question("ei", "飞机 fēi jī", "白鹅 bái é", "小鱼 xiǎo yú"),
            new Question("ui", "嘴巴 zuǐ ba", "花朵 huā duǒ", "小鸟 xiǎo niǎo"),
            new Question("ao", "小猫 xiǎo māo", "小鸡 xiǎo jī", "白鹅 bái é")
        },
        {
            new Question("māo", "猫", "马", "鸟"),
            new Question("mǎ", "马", "猫", "鱼"),
            new Question("yú", "鱼", "雨", "兔"),
            new Question("tù", "兔", "土", "图"),
            new Question("bā", "八", "爸", "拔"),
            new Question("dà", "大", "打", "搭"),
            new Question("huā", "花", "画", "华"),
            new Question("shū", "书", "树", "叔"),
            new Question("niú", "牛", "鸟", "妞"),
            new Question("fēi", "飞", "非", "肥")
        }
    };

    private int mode;
    private int round;
    private int score;
    private int streak;
    private boolean answered;
    private int lastChoice = -1;
    private int playerX, playerY, pursuerX, pursuerY;
    private int escapeEnergy;
    private int escapeMoves;
    private int activeChest = -1;

    public PinyinLearningDialog(){
        super("@pinyin.title");
        setTitleColor(Pal.accent);
        addCloseButton();
        closeOnBack();
        shown(this::showModes);
    }

    private String text(String key){
        return Core.bundle.get(key);
    }

    private String format(String key, Object... args){
        return Core.bundle.format(key, args);
    }

    private void showModes(){
        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::hide).size(200f, 58f);

        cont.defaults().growX().pad(6f);
        cont.image(Icon.star).size(44f).center();
        cont.row();
        cont.add(text("pinyin.subtitle")).color(Color.lightGray).wrap().center();
        cont.row();

        addEscapeMode();
        addMode(0, "pinyin.mode.initial", "pinyin.mode.initial.desc", Pal.accent);
        addMode(1, "pinyin.mode.final", "pinyin.mode.final.desc", Pal.heal);
        addMode(2, "pinyin.mode.spelling", "pinyin.mode.spelling.desc", Pal.lancerLaser);
        cont.row();
        cont.add(format("pinyin.best", Core.settings.getInt("pinyin-best", 0))).color(Pal.accent).center();
    }

    private void addEscapeMode(){
        Table card = new Table(Styles.grayPanel);
        card.defaults().pad(4f);
        card.image(Icon.warning).size(28f).color(Pal.remove);
        card.table(info -> {
            info.add(text("pinyin.mode.escape")).color(Pal.remove).left().row();
            info.add(text("pinyin.mode.escape.desc")).color(Color.lightGray).wrap().left();
        }).growX();
        card.button(Icon.play, Styles.clearNonei, this::startEscape).size(54f);
        cont.add(card).height(78f).row();
    }

    private void addMode(int value, String title, String description, Color color){
        Table card = new Table(Styles.grayPanel);
        card.defaults().pad(4f);
        card.image(Icon.star).size(28f).color(color);
        card.table(info -> {
            info.add(text(title)).color(color).left().row();
            info.add(text(description)).color(Color.lightGray).wrap().left();
        }).growX();
        card.button(Icon.play, Styles.clearNonei, () -> start(value)).size(54f);
        cont.add(card).height(78f).row();
    }

    private void start(int selectedMode){
        mode = selectedMode;
        round = 0;
        score = 0;
        streak = 0;
        renderQuestion();
    }

    private void renderQuestion(){
        answered = false;
        lastChoice = -1;
        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::showModes).size(200f, 58f);

        Question question = QUESTIONS[mode][round];
        cont.defaults().growX().pad(5f);
        cont.add(format("pinyin.round", round + 1, ROUNDS)).color(Pal.accent).center().row();
        cont.image(Icon.star).size(52f).center().row();
        cont.add(prompt(question)).color(Color.white).wrap().center().row();
        cont.add(format("pinyin.score", score)).color(Pal.accent).center().row();
        cont.row();

        int offset = (round + mode) % 3;
        for(int displayIndex = 0; displayIndex < 3; displayIndex++){
            int choice = (displayIndex + offset) % 3;
            cont.button(question.choices[choice], Styles.flatTogglet, () -> answer(question, choice))
                .height(62f).pad(5f).row();
        }
    }

    private String prompt(Question question){
        String key = mode == 0 ? "pinyin.prompt.initial" : mode == 1 ? "pinyin.prompt.final" : "pinyin.prompt.spelling";
        return format(key, question.target);
    }

    private void answer(Question question, int choice){
        if(answered) return;

        answered = true;
        lastChoice = choice;
        boolean correct = choice == 0;
        if(correct){
            score++;
            streak++;
        }else{
            streak = 0;
        }

        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::showModes).size(200f, 58f);
        cont.defaults().growX().pad(7f);
        cont.image(correct ? Icon.star : Icon.info).size(64f).center().row();
        cont.add(correct ? text("pinyin.correct") : format("pinyin.wrong", question.choices[0]))
            .color(correct ? Pal.heal : Pal.accent).wrap().center().row();
        if(correct && streak >= 2){
            cont.add(format("pinyin.correct.streak", streak)).color(Pal.accent).center().row();
        }
        cont.add(format("pinyin.progress", round + 1, ROUNDS, score)).color(Color.lightGray).center().row();
        cont.row();
        cont.button(text("pinyin.next"), Icon.right, () -> {
            if(round + 1 >= ROUNDS){
                finish();
            }else{
                round++;
                renderQuestion();
            }
        }).height(64f).row();
    }

    private void finish(){
        int best = Math.max(score, Core.settings.getInt("pinyin-best", 0));
        Core.settings.put("pinyin-best", best);

        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::hide).size(200f, 58f);
        cont.defaults().growX().pad(7f);
        cont.image(Icon.star).size(76f).center().row();
        cont.add(text("pinyin.result")).color(Pal.accent).center().row();
        cont.add(format("pinyin.result.score", score)).color(Color.white).center().row();
        cont.add(format("pinyin.result.best", best)).color(Color.lightGray).center().row();
        cont.add(score >= 8 ? text("pinyin.result.great") : text("pinyin.result.encourage"))
            .wrap().center().color(score >= 8 ? Pal.heal : Color.lightGray).row();
        cont.row();
        cont.button(text("pinyin.result.again"), Icon.refresh, () -> start(mode)).height(60f).row();
        cont.button(text("pinyin.result.modes"), Icon.menu, this::showModes).height(60f).row();
    }

    private void startEscape(){
        hide();
        ui.pinyinEscape.start();
    }

    private void renderEscapeMap(){
        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::showModes).size(200f, 58f);

        cont.defaults().growX().pad(4f);
        cont.add(text("pinyin.escape.title")).color(Pal.accent).center().row();
        cont.add(text("pinyin.escape.help")).color(Color.lightGray).wrap().center().row();
        cont.add(format("pinyin.escape.status", escapeEnergy, ENERGY_NEEDED, pursuerDistance(), escapeMoves))
            .color(pursuerDistance() <= 2 ? Pal.remove : Pal.accent).center().row();

        Table map = new Table(Styles.grayPanel);
        for(int y = 0; y < MAP_HEIGHT; y++){
            for(int x = 0; x < MAP_WIDTH; x++){
                int cellX = x, cellY = y;
                TextureRegionDrawable icon = Icon.none;
                if(x == playerX && y == playerY) icon = Icon.play;
                else if(x == pursuerX && y == pursuerY) icon = Icon.warning;
                else if(x == MAP_WIDTH - 1 && y == 0) icon = Icon.home;
                else{
                    int chest = chestAt(x, y);
                    if(chest >= 0) icon = ESCAPE_CHESTS[chest].collected ? Icon.ok : Icon.box;
                }

                map.button(icon, Styles.graySquarei, 34f, () -> moveTo(cellX, cellY)).size(58f).pad(2f);
            }
            map.row();
        }
        cont.add(map).center().row();
        cont.table(move -> {
            move.defaults().size(52f).pad(2f);
            move.add();
            move.button(Icon.up, Styles.clearNonei, () -> moveTo(playerX, playerY - 1));
            move.add();
            move.row();
            move.button(Icon.left, Styles.clearNonei, () -> moveTo(playerX - 1, playerY));
            move.button(Icon.home, Styles.clearNonei, () -> moveTo(playerX, playerY));
            move.button(Icon.right, Styles.clearNonei, () -> moveTo(playerX + 1, playerY));
            move.row();
            move.add();
            move.button(Icon.down, Styles.clearNonei, () -> moveTo(playerX, playerY + 1));
            move.add();
        }).center().row();

        if(escapeEnergy >= ENERGY_NEEDED){
            cont.button(text("pinyin.escape.counter"), Icon.commandAttack, this::counterattack)
                .height(62f).color(Pal.remove).row();
        }else{
            cont.add(format("pinyin.escape.need", ENERGY_NEEDED - escapeEnergy)).color(Color.lightGray).center().row();
        }
    }

    private void moveTo(int x, int y){
        if(mode != 3 || activeChest >= 0 || x < 0 || x >= MAP_WIDTH || y < 0 || y >= MAP_HEIGHT) return;
        if(Math.abs(x - playerX) + Math.abs(y - playerY) != 1) return;

        playerX = x;
        playerY = y;
        escapeMoves++;
        int chest = chestAt(x, y);
        if(chest >= 0 && !ESCAPE_CHESTS[chest].collected){
            activeChest = chest;
            renderEscapeChest();
            return;
        }

        chase();
        if(pursuerDistance() <= 0){
            escapeCaught();
        }else if(playerX == MAP_WIDTH - 1 && playerY == 0 && escapeEnergy >= ENERGY_NEEDED){
            escapeWin(false);
        }else{
            renderEscapeMap();
        }
    }

    private void renderEscapeChest(){
        EscapeChest chest = ESCAPE_CHESTS[activeChest];
        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::showModes).size(200f, 58f);
        cont.defaults().growX().pad(7f);
        cont.image(Icon.box).size(72f).center().row();
        cont.add(format("pinyin.escape.chest", chest.pinyin)).color(Pal.accent).wrap().center().row();
        cont.add(text("pinyin.escape.choose")).color(Color.white).wrap().center().row();
        for(int i = 0; i < chest.choices.length; i++){
            int choice = i;
            cont.button(chest.choices[i], Styles.flatTogglet, () -> answerEscape(chest, choice)).height(62f).row();
        }
        cont.add(format("pinyin.escape.status", escapeEnergy, ENERGY_NEEDED, pursuerDistance(), escapeMoves))
            .color(Color.lightGray).center().row();
    }

    private void answerEscape(EscapeChest chest, int choice){
        boolean correct = choice == 0;
        chest.collected = correct;
        if(correct){
            escapeEnergy++;
            pushPursuerBack();
        }else{
            chase();
        }
        activeChest = -1;

        if(pursuerDistance() <= 0){
            escapeCaught();
        }else{
            renderEscapeResult(correct, chest);
        }
    }

    private void renderEscapeResult(boolean correct, EscapeChest chest){
        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::showModes).size(200f, 58f);
        cont.defaults().growX().pad(7f);
        cont.image(correct ? Icon.star : Icon.warning).size(72f).center().row();
        cont.add(correct ? format("pinyin.escape.buff", chest.pinyin) : format("pinyin.escape.miss", chest.choices[0]))
            .color(correct ? Pal.heal : Pal.remove).wrap().center().row();
        cont.add(format("pinyin.escape.status", escapeEnergy, ENERGY_NEEDED, pursuerDistance(), escapeMoves))
            .color(Color.lightGray).center().row();
        cont.button(text("pinyin.escape.continue"), Icon.right, this::renderEscapeMap).height(62f).row();
    }

    private void chase(){
        if(pursuerX != playerX){
            pursuerX += Integer.compare(playerX, pursuerX);
        }else if(pursuerY != playerY){
            pursuerY += Integer.compare(playerY, pursuerY);
        }
    }

    private void pushPursuerBack(){
        if(pursuerX == playerX && pursuerY == playerY){
            if(pursuerX < MAP_WIDTH - 1) pursuerX++;
            else if(pursuerX > 0) pursuerX--;
            else if(pursuerY < MAP_HEIGHT - 1) pursuerY++;
            else if(pursuerY > 0) pursuerY--;
        }else if(pursuerX != playerX){
            pursuerX = Math.max(0, Math.min(MAP_WIDTH - 1, pursuerX + Integer.compare(pursuerX, playerX)));
        }else if(pursuerY != playerY){
            pursuerY = Math.max(0, Math.min(MAP_HEIGHT - 1, pursuerY + Integer.compare(pursuerY, playerY)));
        }
    }

    private int pursuerDistance(){
        return Math.abs(playerX - pursuerX) + Math.abs(playerY - pursuerY);
    }

    private int chestAt(int x, int y){
        for(int i = 0; i < CHEST_POSITIONS.length; i++){
            if(CHEST_POSITIONS[i][0] == x && CHEST_POSITIONS[i][1] == y) return i;
        }
        return -1;
    }

    private void counterattack(){
        if(escapeEnergy >= ENERGY_NEEDED) escapeWin(true);
    }

    private void escapeCaught(){
        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::showModes).size(200f, 58f);
        cont.defaults().growX().pad(7f);
        cont.image(Icon.warning).size(76f).center().row();
        cont.add(text("pinyin.escape.caught")).color(Pal.remove).wrap().center().row();
        cont.add(text("pinyin.escape.caught.help")).color(Color.lightGray).wrap().center().row();
        cont.button(text("pinyin.escape.retry"), Icon.refresh, this::startEscape).height(62f).row();
    }

    private void escapeWin(boolean countered){
        cont.clear();
        buttons.clear();
        buttons.button(text("pinyin.back"), Icon.left, this::hide).size(200f, 58f);
        cont.defaults().growX().pad(7f);
        cont.image(countered ? Icon.commandAttack : Icon.home).size(76f).center().row();
        cont.add(countered ? text("pinyin.escape.counter.win") : text("pinyin.escape.win"))
            .color(Pal.heal).wrap().center().row();
        cont.add(format("pinyin.escape.final", escapeEnergy, escapeMoves)).color(Color.white).center().row();
        cont.button(text("pinyin.escape.retry"), Icon.refresh, this::startEscape).height(62f).row();
        cont.button(text("pinyin.result.modes"), Icon.menu, this::showModes).height(62f).row();
    }

    private static class Question{
        final String target;
        final String[] choices;

        Question(String target, String correct, String wrong1, String wrong2){
            this.target = target;
            choices = new String[]{correct, wrong1, wrong2};
        }
    }

    private static class EscapeChest{
        final String pinyin;
        final String[] choices;
        boolean collected;

        EscapeChest(String pinyin, String correct, String wrong1, String wrong2){
            this.pinyin = pinyin;
            choices = new String[]{correct, wrong1, wrong2};
        }
    }
}
