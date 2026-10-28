package org.transgram.a11y;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;

/** Earcons and haptics. Each event can be sound, vibration, both or off. */
public final class Feedback {
    public enum Event { SENT, FAILED, INCOMING, RECORD_START, RECORD_STOP, DOWNLOAD_DONE, EDGE, ACTION_OK, INCOMING_CALL }
    public enum Mode { BOTH, SOUND, VIBRATE, OFF }

    private static final int[] SOUND_RES = {
            R.raw.sg_sent, R.raw.sg_failed, R.raw.sg_incoming, R.raw.sg_rec_start, R.raw.sg_rec_stop, R.raw.sg_download, 0, 0, 0 // order must match Event; INCOMING_CALL has no sound (the system ringtone plays)
    };
    private static SoundPool pool;
    private static final int[] loaded = new int[Event.values().length];

    private Feedback() {}

    private static synchronized void ensurePool() {
        if (pool != null) return;
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        Context c = ApplicationLoader.applicationContext;
        for (Event e : Event.values()) {
            if (SOUND_RES[e.ordinal()] != 0) loaded[e.ordinal()] = pool.load(c, SOUND_RES[e.ordinal()], 1);
        }
    }

    public static Mode mode(Event e) {
        Mode def = e == Event.EDGE || e == Event.ACTION_OK || e == Event.INCOMING_CALL ? Mode.VIBRATE : Mode.BOTH;
        try {
            return Mode.valueOf(TransGramA11y.prefs().getString("fb_" + e.name(), def.name()));
        } catch (IllegalArgumentException | ClassCastException ex) {
            return def;
        }
    }

    public static void setMode(Event e, Mode m) {
        TransGramA11y.prefs().edit().putString("fb_" + e.name(), m.name()).apply();
    }

    public static float volume() {
        try {
            return TransGramA11y.prefs().getFloat("fb_volume", 0.7f);
        } catch (ClassCastException e) {
            return 0.7f;
        }
    }

    public static void setVolume(float v) {
        TransGramA11y.prefs().edit().putFloat("fb_volume", v).apply();
    }

    public static void play(Event e) {
        if (!TransGramA11y.isEnabled()) return;
        Mode m = mode(e);
        if (m == Mode.OFF) return;
        AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
        boolean silentRinger = am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
        boolean wantSound = (m == Mode.BOTH || m == Mode.SOUND) && !silentRinger && SOUND_RES[e.ordinal()] != 0;
        boolean wantVibrate = m == Mode.BOTH || m == Mode.VIBRATE || silentRinger;
        if (wantSound) {
            ensurePool();
            float v = volume();
            MediaController mc = MediaController.getInstance();
            if (mc.getPlayingMessageObject() != null && !mc.isMessagePaused()) v *= 0.4f;
            pool.play(loaded[e.ordinal()], v, v, 1, 0, 1f);
        }
        if (wantVibrate) {
            if (e == Event.INCOMING_CALL) vibratePattern(CALL_PATTERN);
            else vibrate(e == Event.FAILED ? 120 : 25);
        }
    }

    /** Long-short-long-short buzz, distinct from message feedback. */
    private static final long[] CALL_PATTERN = {0, 400, 150, 150, 150, 400};

    private static void vibratePattern(long[] pattern) {
        Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        else v.vibrate(pattern, -1);
    }

    private static void vibrate(long ms) {
        Vibrator v = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(ms);
    }
}
