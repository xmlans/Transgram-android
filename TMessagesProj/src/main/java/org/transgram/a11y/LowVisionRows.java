package org.transgram.a11y;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.UItem;
import java.util.ArrayList;

/** Kept separate from speech/OCR settings so neither master switch can hide these rows. */
public final class LowVisionRows {
    private static final int MESSAGE = 400, UI = 401, CONTROLS = 402, FOCUS = 403, CONTRAST = 404;
    private static final int[] PERCENT = {100, 115, 130};
    private LowVisionRows() {}

    public static void fill(ArrayList<UItem> items) {
        LowVisionSettings s = LowVisionSettings.get();
        items.add(UItem.asHeader(getString(R.string.TgLowVisionHeader)));
        items.add(UItem.asButton(MESSAGE, getString(R.string.TgLowVisionMessageSize), "" + SharedConfig.fontSize));
        items.add(UItem.asButton(UI, getString(R.string.TgLowVisionUiSize), s.uiPercent + "%"));
        items.add(UItem.asCheck(CONTROLS, getString(R.string.TgLowVisionControls)).setChecked(s.largeControls));
        items.add(UItem.asCheck(FOCUS, getString(R.string.TgLowVisionFocus)).setChecked(s.focusOutline));
        items.add(UItem.asCheck(CONTRAST, getString(R.string.TgLowVisionContrast)).setChecked(s.highContrast));
        items.add(UItem.asShadow(getString(R.string.TgLowVisionInfo)));
    }

    public static boolean onClick(BaseFragment fragment, int id) {
        if (id < MESSAGE || id > CONTRAST) return false;
        if (fragment.getParentActivity() == null) return true;
        if (id == MESSAGE) {
            String[] sizes = new String[19];
            for (int i = 0; i < sizes.length; i++) sizes[i] = "" + (12 + i);
            fragment.showDialog(AlertsCreator.createSingleChoiceDialog(fragment.getParentActivity(), sizes,
                    getString(R.string.TgLowVisionMessageSize), SharedConfig.fontSize >= 12 && SharedConfig.fontSize <= 30 ? SharedConfig.fontSize - 12 : -1,
                    (dialog, which) -> {
                        LowVisionSettings.setMessageSize(12 + which);
                        rebuild(fragment);
                    }));
        } else if (id == UI) {
            String[] choices = {"100%", "115%", "130%"};
            int selected = -1;
            for (int i = 0; i < PERCENT.length; i++) if (PERCENT[i] == LowVisionSettings.get().uiPercent) selected = i;
            fragment.showDialog(AlertsCreator.createSingleChoiceDialog(fragment.getParentActivity(), choices,
                    getString(R.string.TgLowVisionUiSize), selected, (dialog, which) -> {
                        LowVisionSettings.setUiPercent(PERCENT[which]);
                        rebuild(fragment);
                    }));
        } else {
            LowVisionSettings s = LowVisionSettings.get();
            if (id == CONTROLS) LowVisionSettings.setLargeControls(!s.largeControls);
            if (id == FOCUS) LowVisionSettings.setFocusOutline(!s.focusOutline);
            if (id == CONTRAST) LowVisionSettings.setHighContrast(!s.highContrast);
            rebuild(fragment);
        }
        return true;
    }

    private static void rebuild(BaseFragment fragment) {
        // Let the choice dialog dismiss before recreating its owning fragment and cached paints.
        AndroidUtilities.runOnUIThread(() -> {
            Theme.createCommonMessageResources();
            Theme.createDialogsResources(ApplicationLoader.applicationContext);
            Theme.refreshThemeColors();
            if (fragment.getParentLayout() != null) fragment.getParentLayout().rebuildAllFragmentViews(true, true);
        });
    }
}
