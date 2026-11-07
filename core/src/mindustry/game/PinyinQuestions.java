package mindustry.game;

/** Hanzi-to-pinyin lessons. The first option is always the checked pronunciation. */
public class PinyinQuestions{
    public static final Question[] all = {
        new Question("爸", "bà", "声母 b", "bā", "bǎ"),
        new Question("坡", "pō", "声母 p", "pó", "pǒ"),
        new Question("妈", "mā", "声母 m", "má", "mǎ"),
        new Question("花", "huā", "声母 h", "huá", "huǎ"),
        new Question("大", "dà", "声母 d", "dá", "dǎ"),
        new Question("可", "kě", "声母 k", "ké", "kē"),
        new Question("牛", "niú", "声母 n", "niǔ", "niù"),
        new Question("来", "lái", "声母 l", "lāi", "lǎi"),
        new Question("爱", "ài", "韵母 ai", "āi", "ǎi"),
        new Question("杯", "bēi", "韵母 ei", "béi", "běi"),
        new Question("水", "shuǐ", "韵母 ui", "shuī", "shuì"),
        new Question("猫", "māo", "韵母 ao", "mào", "mǎo"),
        new Question("球", "qiú", "韵母 iu", "qiǔ", "qiù"),
        new Question("月", "yuè", "韵母 üe", "yuě", "yuē"),
        new Question("书", "shū", "拼读 sh", "shú", "shǔ"),
        new Question("鱼", "yú", "拼读 y", "yǔ", "yù"),
        new Question("飞", "fēi", "拼读 f", "féi", "fěi"),
        new Question("兔", "tù", "拼读 t", "tǔ", "tū"),
        new Question("鸟", "niǎo", "拼读 n", "niào", "niāo"),
        new Question("光", "guāng", "拼读 gu", "guǎng", "guǎn"),
        new Question("早", "zǎo", "声母 z", "zāo", "zào"),
        new Question("吃", "chī", "声母 ch", "chí", "chǐ"),
        new Question("热", "rè", "声母 r", "rě", "rē"),
        new Question("星", "xīng", "声母 x", "xíng", "xǐng"),
        new Question("风", "fēng", "声母 f", "fěng", "fèng"),
        new Question("天", "tiān", "声母 t", "tián", "tiǎn"),
        new Question("哥", "gē", "声母 g", "gé", "gě"),
        new Question("鸡", "jī", "声母 j", "jí", "jǐ"),
        new Question("七", "qī", "声母 q", "qì", "qǐ"),
        new Question("猪", "zhū", "声母 zh", "zhú", "zhǔ"),
        new Question("草", "cǎo", "声母 c", "cāo", "cáo"),
        new Question("伞", "sǎn", "声母 s", "sān", "sàn"),
        new Question("鸭", "yā", "韵母 a", "yá", "yǎ"),
        new Question("鹅", "é", "韵母 e", "ē", "ě"),
        new Question("衣", "yī", "韵母 i", "yí", "yǐ"),
        new Question("乌", "wū", "韵母 u", "wú", "wǔ"),
        new Question("雨", "yǔ", "韵母 ü", "yú", "yū"),
        new Question("口", "kǒu", "韵母 ou", "kōu", "kòu"),
        new Question("山", "shān", "韵母 an", "shán", "shǎn"),
        new Question("人", "rén", "韵母 en", "rēn", "rěn"),
        new Question("云", "yún", "韵母 ün", "yūn", "yǔn"),
        new Question("灯", "dēng", "韵母 eng", "déng", "děng"),
        new Question("日", "rì", "拼读 r", "rí", "rǐ"),
        new Question("车", "chē", "拼读 ch", "ché", "chě"),
        new Question("石", "shí", "拼读 sh", "shī", "shǐ"),
        new Question("竹", "zhú", "拼读 zh", "zhū", "zhǔ"),
        new Question("绿", "lǜ", "拼读 lü", "lǘ", "lǚ"),
        new Question("雪", "xuě", "拼读 xüe", "xué", "xuè")
    };

    public static class Question{
        public final String character;
        public final String pinyin;
        public final String category;
        public final String[] choices;

        Question(String character, String pinyin, String category, String wrong1, String wrong2){
            this.character = character;
            this.pinyin = pinyin;
            this.category = category;
            choices = new String[]{pinyin, wrong1, wrong2};
        }
    }
}
