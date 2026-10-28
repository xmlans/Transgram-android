package org.transgram.a11y;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

/** Global switches and persisted settings for the TransGram accessibility layer. */
public final class TransGramA11y {
    private static final String PREFS = "transgram_a11y";
    private static SpeechSettings speech;
    /** Bumped whenever speech settings, the enabled switch or the language change; cached cell texts compare against it. */
    public static volatile int speechVersion;
    private static final SpeechFormat ZH = new SpeechFormatZh();
    private static final SpeechFormat EN = new SpeechFormatEn();

    private TransGramA11y() {}

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Master switch of the whole accessibility layer. Off by default: TransGram behaves like upstream until the user opts in. */
    public static final boolean DEFAULT_ENABLED = false;
    private static volatile Boolean enabledCache;

    /** Cached: it is checked on every cell bind. */
    public static boolean isEnabled() {
        Boolean c = enabledCache;
        if (c == null) {
            boolean v;
            try {
                v = prefs().getBoolean("enabled", DEFAULT_ENABLED);
            } catch (ClassCastException e) {
                v = DEFAULT_ENABLED;
            }
            enabledCache = c = v;
        }
        return c;
    }

    /** Whether decorations (symbols, stacked marks, emoji runs) are filtered out of spoken names. */
    public static boolean cleanNames() {
        try {
            return prefs().getBoolean("name_clean", true);
        } catch (ClassCastException e) {
            return true;
        }
    }

    public static void setCleanNames(boolean on) {
        prefs().edit().putBoolean("name_clean", on).apply();
        speechVersion++;
    }

    /** Announce "X joined / left" in group calls (default off). */
    public static boolean callJoinLeave() {
        try {
            return prefs().getBoolean("call_group_joinleave", false);
        } catch (ClassCastException e) {
            return false;
        }
    }

    /** Name as it should be spoken. */
    public static String spokenName(String name) {
        return cleanNames() ? NameCleaner.clean(name) : name;
    }

    public static synchronized SpeechSettings speech() {
        if (speech == null) {
            String raw;
            try {
                raw = prefs().getString("speech", null);
            } catch (ClassCastException e) {
                raw = null;
            }
            speech = SpeechSettings.parse(raw);
        }
        return speech;
    }

    public static synchronized void setSpeech(SpeechSettings s) {
        speech = s;
        speechVersion++;
        prefs().edit().putString("speech", s.serialize()).apply();
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean("enabled", enabled).apply();
        enabledCache = enabled;
        speechVersion++;
    }

    /** Drops the cached speech settings (after an import) and invalidates cached cell texts. */
    public static synchronized void reload() {
        speech = null;
        enabledCache = null;
        speechVersion++;
    }

    /** Removes every TransGram key (before an import), then reloads. */
    public static synchronized void clearAll() {
        prefs().edit().clear().apply();
        reload();
    }

    /** Called after the interface language changed. */
    public static void onLanguageChanged() {
        speechVersion++;
    }

    public static SpeechFormat format() {
        String lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
        return "zh".equals(lang) ? ZH : EN;
    }
}
