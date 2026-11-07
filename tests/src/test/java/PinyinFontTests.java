import arc.files.*;
import arc.freetype.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PinyinFontTests{
    @Test
    void bundledFallbackCanRasterizeEveryPinyinToneVowel(){
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(new Fi("fonts/pinyin.ttf"));
        try{
            String vowels = "āáǎàēéěèīíǐìōóǒòūúǔùüǖǘǚǜĀÁǍÀĒÉĚÈĪÍǏÌŌÓǑÒŪÚǓÙÜǕǗǙǛ";
            for(char vowel : vowels.toCharArray()){
                var result = generator.generateGlyphAndBitmap(vowel, 48, false);
                assertNotNull(result, "Missing pinyin vowel U+" + Integer.toHexString(vowel));
                assertEquals(vowel, result.glyph.id);
                assertTrue(result.glyph.width > 0);
                assertTrue(result.glyph.height > 0);
            }
        }finally{
            generator.dispose();
        }
    }
}
