package org.transgram.a11y;

import android.os.SystemClock;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

/** Subscribes Feedback to upstream notifications for every account. */
public final class FeedbackHooks implements NotificationCenter.NotificationCenterDelegate {
    private static boolean installed;
    private long lastSentAt;

    public static void install() {
        if (installed) return;
        installed = true;
        A11ySettingsActivity.applySavedLanguage();
        FeedbackHooks h = new FeedbackHooks();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(h, NotificationCenter.messageReceivedByServer);
            nc.addObserver(h, NotificationCenter.messageSendError);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messageReceivedByServer) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastSentAt < 300) return; // albums report every part
            lastSentAt = now;
            Feedback.play(Feedback.Event.SENT);
        } else if (id == NotificationCenter.messageSendError) {
            Feedback.play(Feedback.Event.FAILED);
        }
    }
}
