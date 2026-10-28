package org.transgram.a11y;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/** One-time prompt on first launch telling the user that TransGram has accessibility features (they are off by default). */
public final class A11yPrompt {
    private static final String KEY_SHOWN = "a11y_prompt_shown";

    private A11yPrompt() {}

    public static void maybeShow(BaseFragment fragment) {
        if (fragment == null || TransGramA11y.isEnabled()) return;
        if (TransGramA11y.prefs().getBoolean(KEY_SHOWN, false)) return;
        Activity activity = fragment.getParentActivity();
        if (activity == null || activity.isFinishing()) return;
        // Mark first: whatever happens next (button, back, rotation), it is shown only once.
        TransGramA11y.prefs().edit().putBoolean(KEY_SHOWN, true).apply();

        boolean screenReader = AndroidUtilities.isAccessibilityScreenReaderEnabled();
        String text = getString(R.string.TgA11yPromptText);
        if (screenReader) text = getString(R.string.TgA11yPromptDetected) + " " + text;

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.TgA11yPromptTitle))
                .setMessage(text)
                .setPositiveButton(getString(R.string.TgA11yPromptEnable), (d, which) -> TransGramA11y.setEnabled(true))
                .setNegativeButton(getString(R.string.TgA11yPromptLater), null)
                .create();
        fragment.showDialog(dialog);
        if (screenReader) {
            // Put the screen reader on "Turn on" so the user can confirm with one double tap.
            AndroidUtilities.runOnUIThread(() -> {
                View b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (b != null && dialog.isShowing()) {
                    b.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                    b.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                }
            }, 600);
        }
    }
}
