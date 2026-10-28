package org.transgram.a11y;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/** Independent of TalkBack and the TransGram speech master switch. All options default off. */
public final class LowVisionSettings {
    private static volatile LowVisionSettings cached;
    public final int uiPercent;
    public final boolean largeControls, focusOutline, highContrast;

    private LowVisionSettings(int percent, boolean controls, boolean focus, boolean contrast) {
        uiPercent = Math.max(100, Math.min(130, percent));
        largeControls = controls;
        focusOutline = focus;
        highContrast = contrast;
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("transgram_lowvision", Context.MODE_PRIVATE);
    }

    public static LowVisionSettings get() {
        LowVisionSettings settings = cached;
        if (settings != null) return settings;
        if (ApplicationLoader.applicationContext == null) return new LowVisionSettings(100, false, false, false);
        SharedPreferences p = preferences();
        // Read each key independently so one malformed preference does not disable the others.
        int percent = 100;
        try { percent = p.getInt("ui_percent", 100); } catch (ClassCastException ignored) {}
        return cached = new LowVisionSettings(percent, flag(p, "large_controls"), flag(p, "focus_outline"), flag(p, "high_contrast"));
    }

    private static boolean flag(SharedPreferences p, String key) {
        try { return p.getBoolean(key, false); } catch (ClassCastException ignored) { return false; }
    }

    public static float uiText(float baseDp) {
        return baseDp * LowVisionPolicy.uiScale(get().uiPercent);
    }

    /** Fixed-height app bars and canvas labels have less vertical room than settings rows. */
    public static float compactUiText(float baseDp) {
        return LowVisionPolicy.compactText(baseDp, get().uiPercent);
    }

    public static int rowHeight(int baseDp) {
        LowVisionSettings s = get();
        return LowVisionPolicy.rowHeight(baseDp, s.uiPercent, s.largeControls);
    }

    public static void setUiPercent(int percent) {
        preferences().edit().putInt("ui_percent", Math.max(100, Math.min(130, percent))).apply();
        cached = null;
    }

    public static void setLargeControls(boolean enabled) { setFlag("large_controls", enabled); }
    public static void setFocusOutline(boolean enabled) { setFlag("focus_outline", enabled); }
    public static void setHighContrast(boolean enabled) { setFlag("high_contrast", enabled); }

    private static void setFlag(String key, boolean value) {
        preferences().edit().putBoolean(key, value).apply();
        cached = null;
    }

    public static void setMessageSize(int size) {
        SharedConfig.fontSize = LowVisionPolicy.messageSize(size);
        SharedConfig.fontSizeIsDefault = false;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                .edit().putInt("fons_size", SharedConfig.fontSize).apply();
        Theme.createCommonMessageResources();
    }
}
