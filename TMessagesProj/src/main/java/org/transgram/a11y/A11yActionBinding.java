package org.transgram.a11y;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public final class A11yActionBinding {
    private static final int[][] TABLE = {
            {R.id.sg_act_play_pause, R.string.SgActPlayPause},
            {R.id.sg_act_seek_back, R.string.SgActSeekBack},
            {R.id.sg_act_seek_forward, R.string.SgActSeekForward},
            {R.id.sg_act_speed, R.string.SgActSpeed},
            {R.id.sg_act_reply, R.string.SgActReply},
            {R.id.sg_act_copy, R.string.SgActCopy},
            {R.id.sg_act_forward, R.string.SgActForward},
            {R.id.sg_act_react_1, R.string.SgActReactWith},
            {R.id.sg_act_react_2, R.string.SgActReactWith},
            {R.id.sg_act_react_3, R.string.SgActReactWith},
            {R.id.sg_act_react_4, R.string.SgActReactWith},
            {R.id.sg_act_react_5, R.string.SgActReactWith},
            {R.id.sg_act_react_6, R.string.SgActReactWith},
            {R.id.sg_act_reactions_picker, R.string.SgActReactionsPicker},
            {R.id.sg_act_who_reacted, R.string.SgActWhoReacted},
            {R.id.sg_act_reply_private, R.string.SgActReplyPrivate},
            {R.id.sg_act_edit, R.string.SgActEdit},
            {R.id.sg_act_delete, R.string.SgActDelete},
            {R.id.sg_act_pin, R.string.SgActPin},
            {R.id.sg_act_unpin, R.string.SgActUnpin},
            {R.id.sg_act_save, R.string.SgActSave},
            {R.id.sg_act_share, R.string.SgActShare},
            {R.id.sg_act_go_replied, R.string.SgActGoReplied},
            {R.id.sg_act_open_sender, R.string.SgActOpenSender},
            {R.id.sg_act_ocr, R.string.SgActOcr},
            {R.id.sg_act_select, R.string.SgActSelect},
    };

    static {
        if (TABLE.length != A11yAction.values().length) {
            throw new IllegalStateException("A11yActionBinding.TABLE must have one row per A11yAction, in order");
        }
    }

    private A11yActionBinding() {}

    /** Rows follow A11yAction declaration order. */
    public static int id(A11yAction a) {
        return TABLE[a.ordinal()][0];
    }

    public static String label(A11yAction a) {
        return LocaleController.getString(TABLE[a.ordinal()][1]);
    }

    /** Label with dynamic parts (the six quick reactions carry their emoji). */
    public static String label(A11yAction a, A11yActions.Caps caps) {
        final int i = a.ordinal() - A11yAction.REACT_1.ordinal();
        if (i >= 0 && i < 6 && caps != null && caps.quickReactions != null && i < caps.quickReactions.length) {
            return LocaleController.formatString(TABLE[a.ordinal()][1], caps.quickReactions[i]);
        }
        return label(a);
    }

    public static A11yAction fromId(int id) {
        for (A11yAction a : A11yAction.values()) if (TABLE[a.ordinal()][0] == id) return a;
        return null;
    }
}
