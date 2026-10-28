package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SettingsBackupTest {
    @Test
    public void roundTripsAllTypes() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", true);
        m.put("speech", "v1;STANDARD;SENDER+,TYPE-");
        m.put("fb_volume", 0.7f);
        m.put("n", 5);
        m.put("big", 1234567890123L);
        m.put("multi", "a\nb\\c\\n");
        m.put("empty", "");
        Map<String, Object> back = SettingsBackup.parse(SettingsBackup.export(m));
        assertEquals(m, back);
    }

    @Test
    public void startsWithHeader() {
        assertTrue(SettingsBackup.export(new HashMap<String, Object>()).startsWith("transgram-a11y-settings-v1\n"));
    }

    @Test
    public void rejectsForeignText() {
        assertNull(SettingsBackup.parse("hello"));
        assertNull(SettingsBackup.parse(null));
        assertNull(SettingsBackup.parse(""));
    }

    @Test
    public void skipsBadLinesAndAcceptsCrlf() {
        Map<String, Object> back = SettingsBackup.parse("transgram-a11y-settings-v1\r\nx=i:oops\r\ny=b:true\r\ngarbage\r\n");
        assertEquals(1, back.size());
        assertEquals(true, back.get("y"));
    }

    @Test
    public void keepsOcrSwitches() {
        Map<String, Object> m = new HashMap<>();
        m.put("ocr_enabled", true);
        m.put("ocr_in_speech", true);
        m.put("ocr_wifi_only", false);
        m.put("ocr_enabled_typo", true);
        Map<String, Object> out = clean(m);
        assertEquals(3, out.size());
        assertEquals(true, out.get("ocr_enabled"));
        assertEquals(false, out.get("ocr_wifi_only"));
    }

    private static Map<String, Object> clean(Map<String, Object> m) {
        return SettingsBackup.sanitize(m, java.util.Arrays.asList("SENT", "FAILED"),
                java.util.Arrays.asList("BOTH", "SOUND", "VIBRATE", "OFF"),
                java.util.Arrays.asList("ALL", "PRIVATE_AND_MENTIONS", "SOUND_ONLY", "OFF"));
    }

    @Test
    public void sanitizeKeepsValidValues() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", false);
        m.put("speech", "v1;STANDARD;SENDER+");
        m.put("fb_volume", 0.5f);
        m.put("fb_SENT", "OFF");
        m.put("ann_default", "SOUND_ONLY");
        m.put("ann_-1001", "ALL");
        m.put("ann_downloads", true);
        m.put("ui_lang", "zh");
        m.put("name_clean", false);
        m.put("call_group_joinleave", true);
        assertEquals(m, clean(m));
    }

    @Test
    public void sanitizeDropsWrongTypeUnknownKeyAndBadEnum() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", "x");
        m.put("fb_volume", "abc");
        m.put("speech", 5);
        m.put("unknown", "x");
        m.put("fb_NOPE", "OFF");
        m.put("fb_SENT", "LOUD");
        m.put("ann_default", "SHOUT");
        m.put("ann_abc", "ALL");
        m.put("ui_lang", "fr");
        m.put("name_clean", "yes");
        m.put("call_group_joinleave", 1);
        assertEquals(0, clean(m).size());
    }

    @Test
    public void sanitizeClampsVolume() {
        Map<String, Object> m = new HashMap<>();
        m.put("fb_volume", 7f);
        assertEquals(1f, (Float) clean(m).get("fb_volume"), 0f);
        m.put("fb_volume", -2f);
        assertEquals(0f, (Float) clean(m).get("fb_volume"), 0f);
    }
}
