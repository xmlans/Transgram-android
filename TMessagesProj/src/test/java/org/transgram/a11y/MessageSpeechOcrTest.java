package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.TimeZone;

import org.junit.Test;

public class MessageSpeechOcrTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");
    private static final SpeechFormat ZH = new SpeechFormatZh();
    private static final SpeechFormat EN = new SpeechFormatEn();
    private static final SpeechSettings STD = SpeechSettings.forPreset(SpeechPreset.STANDARD);

    private static String say(SpeechInput in, SpeechFormat f) {
        return MessageSpeech.join(MessageSpeech.build(in, STD, f, 0L, TZ), f);
    }

    private static SpeechInput photo(String caption, String ocr) {
        SpeechInput in = new SpeechInput();
        in.kind = SpeechInput.Kind.PHOTO;
        in.body = caption;
        in.ocrText = ocr;
        return in;
    }

    @Test public void ocrTextIsSpokenAfterTheCaption() {
        String s = say(photo("看这个", "今日特价 18 元"), ZH);
        assertTrue(s, s.contains("看这个"));
        assertTrue(s, s.contains("图中文字：今日特价 18 元"));
        assertTrue(s, s.indexOf("看这个") < s.indexOf("图中文字"));
    }

    @Test public void ocrTextWorksWithoutCaptionAndInEnglish() {
        assertTrue(say(photo(null, "OPEN"), ZH).contains("图中文字：OPEN"));
        assertTrue(say(photo(null, "OPEN"), EN).contains("Text in image: OPEN"));
    }

    @Test public void ocrTextIsCutAt200CharactersWithEllipsis() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('字');
        String s = say(photo(null, sb.toString()), ZH);
        int start = s.indexOf("图中文字：") + "图中文字：".length();
        String spoken = s.substring(start);
        int end = spoken.indexOf('…');
        assertEquals(200, end);
        assertEquals(end + 1, spoken.length());
    }

    @Test public void blankOrNullOcrTextIsIgnored() {
        assertFalse(say(photo("hi", null), ZH).contains("图中文字"));
        assertFalse(say(photo("hi", "  \n "), ZH).contains("图中文字"));
    }

    @Test public void newlinesInOcrTextBecomeSpaces() {
        assertTrue(say(photo(null, "第一行\n第二行"), ZH).contains("图中文字：第一行 第二行"));
    }
}
