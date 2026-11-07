import mindustry.game.*;
import java.nio.charset.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinQuestionsTests{
    @Test
    void dadHasFourthToneAndEachPromptHasExactlyOneCorrectOption(){
        var dad = Arrays.stream(PinyinQuestions.all).filter(q -> q.character.equals("爸")).findFirst().orElseThrow();
        assertEquals("bà", dad.pinyin);
        assertEquals("bà", dad.choices[0]);
        for(var question : PinyinQuestions.all){
            assertEquals(question.pinyin, question.choices[0]);
            assertEquals(3, new HashSet<>(Arrays.asList(question.choices)).size());
            for(String choice : question.choices){
                long accents = choice.codePoints().filter(c -> "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ".indexOf(c) >= 0).count();
                assertEquals(1, accents, question.character + ": " + choice + " must contain one clear tone mark");
                assertEquals(java.text.Normalizer.normalize(choice, java.text.Normalizer.Form.NFC), choice);
            }
        }
    }

    @Test
    void speechContainsOnlyTheHanziAndUnicodeSurvivesWindowsCommandEncoding(){
        for(var question : PinyinQuestions.all){
            String script = new String(Base64.getDecoder().decode(PinyinPronunciation.encodedCommand(question.character)), StandardCharsets.UTF_16LE);
            assertTrue(script.contains("$voice.Speak('" + question.character + "')"));
            assertFalse(script.contains(question.pinyin), "Speech must not read Latin pinyin as English letters");
            assertFalse(script.contains("拼音"));
            assertTrue(script.contains("zh-CN"));
        }
    }
}
