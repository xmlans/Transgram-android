package org.transgram.a11y;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Speaks new messages of the open chat, merged by AnnounceThrottle. */
public final class Announcer {
    public enum Mode { ALL, PRIVATE_AND_MENTIONS, SOUND_ONLY, OFF }

    private static final long WINDOW_MS = 2000;
    private static final AnnounceThrottle throttle = new AnnounceThrottle(WINDOW_MS);
    private static Runnable pending;
    private static long windowDialog;
    private static long lastEarcon;

    private Announcer() {}

    public static void clearMode(long dialogId) {
        if (dialogId != 0) TransGramA11y.prefs().edit().remove("ann_" + dialogId).apply();
    }

    // ---- download progress (only files the user started with the cell's download button) ----
    private static final Map<String, long[]> watched = new LinkedHashMap<>(); // file name -> {last announced percent, watch start ms}
    private static final long WATCH_TTL_MS = 10 * 60 * 1000L;
    private static final int WATCH_MAX = 64;
    private static boolean downloadsObserved;
    private static final NotificationCenter.NotificationCenterDelegate downloadObserver = (id, account, args) -> {
        if (watched.isEmpty() || args.length == 0 || !(args[0] instanceof String)) return;
        String name = (String) args[0];
        long[] state = watched.get(name);
        if (state == null) return;
        int last = (int) state[0];
        if (id == NotificationCenter.fileLoadProgressChanged) {
            long done = (Long) args[1], total = (Long) args[2];
            if (total <= 0) return;
            int pct = (int) (done * 100 / total) / 25 * 25;
            if (pct > last && pct < 100) {
                state[0] = pct;
                speakDownload(pct);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            watched.remove(name);
            speakDownload(100);
            Feedback.play(Feedback.Event.DOWNLOAD_DONE);
        } else if (id == NotificationCenter.fileLoadFailed) {
            watched.remove(name);
            // upstream posts canceled == 1 for a user cancel; anything else is a real failure
            boolean cancelled = args.length > 1 && args[1] instanceof Integer && (Integer) args[1] == 1;
            if (!cancelled && TransGramA11y.isEnabled() && AndroidUtilities.isAccessibilityScreenReaderEnabled()
                    && TransGramA11y.prefs().getBoolean("ann_downloads", true)) {
                AndroidUtilities.makeAccessibilityAnnouncement(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.SgDownloadFailed));
            }
        }
    };

    private static void speakDownload(int pct) {
        if (!TransGramA11y.isEnabled() || !AndroidUtilities.isAccessibilityScreenReaderEnabled()) return;
        if (!TransGramA11y.prefs().getBoolean("ann_downloads", true)) return;
        AndroidUtilities.makeAccessibilityAnnouncement(TransGramA11y.format().label(SpeechFormat.Label.DOWNLOADED_PERCENT) + " " + pct + "%");
    }

    /** Call when the user taps a download button; the given objects are the document and/or photo size being fetched. */
    public static void watchDownload(TLObject... files) {
        if (!TransGramA11y.isEnabled() || !AndroidUtilities.isAccessibilityScreenReaderEnabled()) return;
        if (!downloadsObserved) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter nc = NotificationCenter.getInstance(a);
                nc.addObserver(downloadObserver, NotificationCenter.fileLoadProgressChanged);
                nc.addObserver(downloadObserver, NotificationCenter.fileLoaded);
                nc.addObserver(downloadObserver, NotificationCenter.fileLoadFailed);
            }
            downloadsObserved = true;
        }
        long now = System.currentTimeMillis();
        for (Iterator<long[]> it = watched.values().iterator(); it.hasNext(); ) {
            if (now - it.next()[1] > WATCH_TTL_MS) it.remove();
        }
        for (TLObject f : files) {
            if (f == null) continue;
            String name = FileLoader.getAttachFileName(f);
            if (name == null) continue;
            watched.remove(name);
            watched.put(name, new long[]{0, now});
            while (watched.size() > WATCH_MAX) {
                Iterator<String> it = watched.keySet().iterator();
                it.next();
                it.remove();
            }
        }
    }

    public static Mode mode(long dialogId) {
        String key = TransGramA11y.prefs().contains("ann_" + dialogId) ? "ann_" + dialogId : "ann_default";
        try {
            return Mode.valueOf(TransGramA11y.prefs().getString(key, Mode.ALL.name()));
        } catch (IllegalArgumentException | ClassCastException e) {
            return Mode.ALL;
        }
    }

    public static void setMode(long dialogId, Mode m) {
        TransGramA11y.prefs().edit().putString(dialogId == 0 ? "ann_default" : "ann_" + dialogId, m.name()).apply();
    }

    /** Drops any buffered announcement (chat paused/closed/switched). */
    public static void cancel() {
        if (pending != null) {
            AndroidUtilities.cancelRunOnUIThread(pending);
            pending = null;
        }
        throttle.reset();
    }

    public static void onIncoming(long dialogId, boolean isPrivate, boolean mentionsMe, String sender, MessageObject m) {
        if (!TransGramA11y.isEnabled() || !AndroidUtilities.isAccessibilityScreenReaderEnabled()) return;
        Mode mode = mode(dialogId);
        if (mode == Mode.OFF) return;
        long now = System.currentTimeMillis();
        if (mode == Mode.SOUND_ONLY) {
            if (now - lastEarcon >= WINDOW_MS) {
                lastEarcon = now;
                Feedback.play(Feedback.Event.INCOMING);
            }
            return;
        }
        if (mode == Mode.PRIVATE_AND_MENTIONS && !isPrivate && !mentionsMe) return;
        if (pending != null && windowDialog != dialogId) cancel();
        if (pending == null) {
            windowDialog = dialogId;
            Feedback.play(Feedback.Event.INCOMING);
            pending = () -> {
                pending = null;
                String line = throttle.flushIfDue(System.currentTimeMillis(), TransGramA11y.format());
                if (line != null) AndroidUtilities.makeAccessibilityAnnouncement(line);
            };
            AndroidUtilities.runOnUIThread(pending, WINDOW_MS + 50);
        }
        throttle.add(TransGramA11y.spokenName(sender), textOf(m), now);
    }

    static String textOf(MessageObject m) {
        CharSequence t = m.messageText;
        if (t != null && t.toString().trim().length() > 0) return t.toString();
        SpeechFormat f = TransGramA11y.format();
        SpeechInput in = MessageSpeechAdapter.toInput(m, null, null);
        SpeechFormat.Label l;
        switch (in.kind) {
            case PHOTO: l = SpeechFormat.Label.PHOTO; break;
            case VIDEO: l = SpeechFormat.Label.VIDEO; break;
            case VOICE: l = SpeechFormat.Label.VOICE; break;
            case ROUND_VIDEO: l = SpeechFormat.Label.ROUND_VIDEO; break;
            case MUSIC: l = SpeechFormat.Label.MUSIC; break;
            case GIF: l = SpeechFormat.Label.GIF; break;
            case STICKER: l = SpeechFormat.Label.STICKER; break;
            case FILE: l = SpeechFormat.Label.FILE; break;
            case POLL: l = SpeechFormat.Label.POLL; break;
            case LOCATION: l = SpeechFormat.Label.LOCATION; break;
            case CONTACT: l = SpeechFormat.Label.CONTACT; break;
            default: l = SpeechFormat.Label.MEDIA; break;
        }
        String s = f.label(l);
        if (in.kind == SpeechInput.Kind.STICKER && in.stickerEmoji != null && !in.stickerEmoji.isEmpty()) s += " " + in.stickerEmoji;
        if (in.body != null && in.body.toString().trim().length() > 0) s += f.colon() + in.body; // media caption / voice transcription
        return s;
    }
}
