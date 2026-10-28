package org.transgram.ocr;

import android.content.SharedPreferences;

import org.transgram.a11y.TransGramA11y;

/**
 * The offline text recognition (OCR) settings, stored in the same preferences file as the other TransGram
 * accessibility settings. Everything is off by default.
 */
public final class RecognitionSettings {
    /** OCR switch: the screen-reader action on photos only appears when this and TransGramA11y.isEnabled() are on. */
    private static final String K_ENABLED = "ocr_enabled";
    /** Speak the recognised text right after a photo's caption. */
    private static final String K_SPEAK_OCR = "ocr_in_speech";
    /** Preferred download source id; not exposed in the UI (the OCR pack has a single source). */
    private static final String K_SOURCE = "ocr_source";
    /** Ask before downloading on a metered network; not exposed in the UI. */
    private static final String K_WIFI_ONLY = "ocr_wifi_only";

    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_SPEAK_OCR = false;

    private RecognitionSettings() {}

    private static SharedPreferences prefs() {
        return TransGramA11y.prefs();
    }

    private static boolean bool(String key, boolean def) {
        try {
            return prefs().getBoolean(key, def);
        } catch (ClassCastException e) {
            return def;
        }
    }

    public static boolean ocrEnabled() {
        return bool(K_ENABLED, DEFAULT_ENABLED);
    }

    public static void setOcrEnabled(boolean v) {
        prefs().edit().putBoolean(K_ENABLED, v).apply();
        TransGramA11y.speechVersion++; // the screen-reader actions of photo cells change
    }

    public static boolean speakOcr() {
        return bool(K_SPEAK_OCR, DEFAULT_SPEAK_OCR);
    }

    public static void setSpeakOcr(boolean v) {
        prefs().edit().putBoolean(K_SPEAK_OCR, v).apply();
        TransGramA11y.speechVersion++; // cached cell texts include or drop the recognised text
    }

    /** "auto", "modelscope", "hf-mirror", "hf" or "github". */
    public static String source() {
        try {
            return prefs().getString(K_SOURCE, "auto");
        } catch (ClassCastException e) {
            return "auto";
        }
    }

    public static void setSource(String id) {
        prefs().edit().putString(K_SOURCE, id).apply();
    }

    public static boolean wifiOnly() {
        return bool(K_WIFI_ONLY, true);
    }

    public static void setWifiOnly(boolean v) {
        prefs().edit().putBoolean(K_WIFI_ONLY, v).apply();
    }
}
