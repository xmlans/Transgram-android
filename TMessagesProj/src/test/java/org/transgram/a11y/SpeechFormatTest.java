package org.transgram.a11y;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public class SpeechFormatTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");

    private static long at(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(TZ);
        c.clear();
        c.set(y, mo - 1, d, h, mi);
        return c.getTimeInMillis();
    }

    private final long now = at(2026, 10, 15, 18, 0);
    private final SpeechFormat zh = new SpeechFormatZh();
    private final SpeechFormat en = new SpeechFormatEn();

    @Test public void zhToday() { assertEquals("下午3点20分", zh.time(at(2026, 10, 15, 15, 20), now, TZ)); }
    @Test public void zhMorning() { assertEquals("上午9点05分", zh.time(at(2026, 10, 15, 9, 5), now, TZ)); }
    @Test public void zhExactHour() { assertEquals("晚上8点", zh.time(at(2026, 10, 14, 20, 0), at(2026, 10, 14, 21, 0), TZ)); }
    @Test public void zhYesterday() { assertEquals("昨天 下午3点20分", zh.time(at(2026, 10, 14, 15, 20), now, TZ)); }
    @Test public void zhThisYear() { assertEquals("10月12日 下午3点20分", zh.time(at(2026, 10, 12, 15, 20), now, TZ)); }
    @Test public void zhOtherYear() { assertEquals("2025年12月31日 晚上11点59分", zh.time(at(2025, 12, 31, 23, 59), now, TZ)); }
    @Test public void zhYesterdayAcrossYear() { assertEquals("昨天 晚上11点", zh.time(at(2025, 12, 31, 23, 0), at(2026, 1, 1, 8, 0), TZ)); }
    @Test public void zhMidnight() { assertEquals("凌晨0点30分", zh.time(at(2026, 10, 15, 0, 30), now, TZ)); }
    @Test public void zhNoon() { assertEquals("中午12点", zh.time(at(2026, 10, 15, 12, 0), now, TZ)); }

    @Test public void zhDuration() {
        assertEquals("0分12秒", zh.duration(12));
        assertEquals("3分05秒", zh.duration(185));
        assertEquals("1小时2分3秒", zh.duration(3723));
    }

    @Test public void zhSize() {
        assertEquals("512字节", zh.size(512));
        assertEquals("2.3MB", zh.size(2_411_725));
        assertEquals("1.5GB", zh.size(1_610_612_736L));
    }

    @Test public void enTimes() {
        assertEquals("3:20 PM", en.time(at(2026, 10, 15, 15, 20), now, TZ));
        assertEquals("yesterday 3:20 PM", en.time(at(2026, 10, 14, 15, 20), now, TZ));
        assertEquals("Oct 12, 3:20 PM", en.time(at(2026, 10, 12, 15, 20), now, TZ));
        assertEquals("Dec 31, 2025, 11:59 PM", en.time(at(2025, 12, 31, 23, 59), now, TZ));
    }

    @Test public void enDurationAndSize() {
        assertEquals("12 seconds", en.duration(12));
        assertEquals("3 minutes 5 seconds", en.duration(185));
        assertEquals("1 minute 1 second", en.duration(61));
        assertEquals("2.3 MB", en.size(2_411_725));
    }
}
