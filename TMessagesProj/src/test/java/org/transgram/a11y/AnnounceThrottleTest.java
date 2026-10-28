package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AnnounceThrottleTest {
    private final SpeechFormat zh = new SpeechFormatZh();

    @Test public void singleMessageSpokenAfterWindow() {
        AnnounceThrottle t = new AnnounceThrottle(2000);
        t.add("张三", "你好", 0);
        assertNull(t.flushIfDue(1000, zh));
        assertEquals("张三：你好", t.flushIfDue(2000, zh));
        assertNull(t.flushIfDue(5000, zh));
    }

    @Test public void burstIsMerged() {
        AnnounceThrottle t = new AnnounceThrottle(2000);
        t.add("张三", "a", 0);
        t.add("李四", "b", 500);
        t.add("张三", "c", 900);
        t.add("王五", "d", 1200);
        t.add("张三", "e", 1500);
        assertEquals("张三等 3 人发来 5条新消息", t.flushIfDue(2000, zh));
    }

    @Test public void sameSenderBurst() {
        AnnounceThrottle t = new AnnounceThrottle(2000);
        t.add("张三", "a", 0);
        t.add("张三", "b", 100);
        assertEquals("张三发来 2条新消息", t.flushIfDue(2000, zh));
    }

    @Test public void longTextTruncatedTo80() {
        AnnounceThrottle t = new AnnounceThrottle(2000);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 100; i++) s.append('字');
        t.add("张三", s.toString(), 0);
        assertEquals(3 + 80 + 1, t.flushIfDue(2000, zh).length()); // "张三：" + 80 + "…"
    }

    @Test public void englishMerge() {
        AnnounceThrottle t = new AnnounceThrottle(2000);
        t.add("Ann", "a", 0);
        t.add("Bob", "b", 100);
        assertEquals("Ann and 1 other sent 2 new messages", t.flushIfDue(2000, new SpeechFormatEn()));
    }

    @Test public void resetDropsPending() {
        AnnounceThrottle t = new AnnounceThrottle(2000);
        t.add("张三", "a", 0);
        t.reset();
        assertNull(t.flushIfDue(5000, zh));
        t.add("李四", "b", 6000);
        assertEquals("李四：b", t.flushIfDue(8000, zh));
    }
}
