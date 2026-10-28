package org.transgram.a11y;

import java.util.ArrayList;
import java.util.List;

public final class A11yActions {
    private A11yActions() {}

    /** What the current user can do with a message; filled by ChatActivity. */
    public static final class Caps {
        public boolean playable;
        public boolean canReply;
        public boolean hasText;
        public boolean canForward;
        public boolean canReact;
        /** Up to 6 emoji offered as one-tap reactions; null/empty = none. */
        public String[] quickReactions;
        public boolean hasReactions;
        public boolean canReplyPrivately;
        public boolean canEdit;
        public boolean canDelete;
        public boolean canPin;
        public boolean isPinned;
        public boolean canSave;
        public boolean canShare;
        public boolean hasReplyTo;
        public boolean canOpenSender;
        /** Photo or image file whose text can be read offline (OCR on and supported). */
        public boolean canOcr;
    }

    public static List<A11yAction> forMessage(Caps c) {
        List<A11yAction> out = new ArrayList<>();
        if (c.playable) {
            out.add(A11yAction.PLAY_PAUSE);
            out.add(A11yAction.SEEK_BACK);
            out.add(A11yAction.SEEK_FORWARD);
            out.add(A11yAction.SPEED);
        }
        if (c.canOcr) out.add(A11yAction.OCR);
        if (c.canReply) out.add(A11yAction.REPLY);
        if (c.hasText) out.add(A11yAction.COPY);
        if (c.canForward) out.add(A11yAction.FORWARD);
        if (c.canReact) {
            final A11yAction[] all = A11yAction.values();
            final int first = A11yAction.REACT_1.ordinal();
            final int n = c.quickReactions == null ? 0 : Math.min(6, c.quickReactions.length);
            for (int i = 0; i < n; i++) out.add(all[first + i]);
            out.add(A11yAction.REACTIONS_PICKER);
        }
        if (c.hasReactions) out.add(A11yAction.WHO_REACTED);
        if (c.canReplyPrivately) out.add(A11yAction.REPLY_PRIVATELY);
        if (c.canEdit) out.add(A11yAction.EDIT);
        if (c.canDelete) out.add(A11yAction.DELETE);
        if (c.canPin) out.add(c.isPinned ? A11yAction.UNPIN : A11yAction.PIN);
        if (c.canSave) out.add(A11yAction.SAVE);
        if (c.canShare) out.add(A11yAction.SHARE);
        if (c.hasReplyTo) out.add(A11yAction.GO_TO_REPLIED);
        if (c.canOpenSender) out.add(A11yAction.OPEN_SENDER);
        out.add(A11yAction.SELECT);
        return out;
    }
}
