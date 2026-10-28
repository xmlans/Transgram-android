package org.transgram.a11y;

import org.telegram.ui.ActionBar.Theme;

/** Foreground-only overlay. Never writes theme colors or changes bubbles, wallpapers or media. */
public final class LowVisionColors {
    private LowVisionColors() {}

    public static int adjust(int key, int color, Theme.ResourcesProvider provider) {
        if (!LowVisionSettings.get().highContrast) return color;
        int background;
        if (key == Theme.key_windowBackgroundWhiteBlackText
                || key == Theme.key_windowBackgroundWhiteGrayText
                || key == Theme.key_windowBackgroundWhiteGrayText2
                || key == Theme.key_windowBackgroundWhiteGrayText3
                || key == Theme.key_windowBackgroundWhiteGrayText4
                || key == Theme.key_windowBackgroundWhiteBlueText
                || key == Theme.key_windowBackgroundWhiteBlueText2
                || key == Theme.key_windowBackgroundWhiteBlueHeader
                || key == Theme.key_windowBackgroundWhiteValueText
                || key == Theme.key_windowBackgroundWhiteLinkText
                || key == Theme.key_windowBackgroundWhiteGrayIcon
                || key == Theme.key_chats_name || key == Theme.key_chats_secretName
                || key == Theme.key_chats_message || key == Theme.key_chats_date
                || key == Theme.key_chats_nameMessage || key == Theme.key_chats_message_threeLines) {
            background = Theme.key_windowBackgroundWhite;
        } else if (key == Theme.key_dialogTextBlack || key == Theme.key_dialogTextGray
                || key == Theme.key_dialogTextGray2 || key == Theme.key_dialogTextGray3
                || key == Theme.key_dialogTextBlue || key == Theme.key_dialogTextBlue2
                || key == Theme.key_dialogTextBlue4
                || key == Theme.key_dialogTextLink || key == Theme.key_dialogIcon) {
            background = Theme.key_dialogBackground;
        } else if (key == Theme.key_actionBarDefaultTitle || key == Theme.key_actionBarDefaultSubtitle
                || key == Theme.key_actionBarDefaultIcon) {
            background = Theme.key_actionBarDefault;
        } else {
            return color;
        }
        // The selected keys above never include a background, so this cannot recurse.
        return LowVisionPolicy.readableColor(color, Theme.getColor(background, provider));
    }
}
