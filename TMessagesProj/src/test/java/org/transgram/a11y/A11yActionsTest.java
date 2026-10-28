package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class A11yActionsTest {
    @Test public void textMessageFromOthers() {
        A11yActions.Caps c = new A11yActions.Caps();
        c.canReply = true; c.hasText = true; c.canForward = true; c.canReact = true;
        c.quickReactions = new String[]{"a", "b", "c", "d", "e", "f"};
        List<A11yAction> a = A11yActions.forMessage(c);
        assertEquals(A11yAction.REPLY, a.get(0));
        assertTrue(a.contains(A11yAction.COPY));
        assertTrue(a.indexOf(A11yAction.REACT_1) < a.indexOf(A11yAction.REACT_2));
        assertTrue(a.contains(A11yAction.REACT_6));
        assertTrue(a.indexOf(A11yAction.REACT_6) < a.indexOf(A11yAction.REACTIONS_PICKER));
        assertFalse(a.contains(A11yAction.WHO_REACTED));
        assertFalse(a.contains(A11yAction.EDIT));
        assertFalse(a.contains(A11yAction.DELETE));
        assertEquals(A11yAction.SELECT, a.get(a.size() - 1));
    }

    @Test public void voiceMessagePlaybackFirst() {
        A11yActions.Caps c = new A11yActions.Caps();
        c.canReply = true; c.canDelete = true; c.playable = true; c.canPin = true;
        List<A11yAction> a = A11yActions.forMessage(c);
        assertEquals(A11yAction.PLAY_PAUSE, a.get(0));
        assertTrue(a.indexOf(A11yAction.SEEK_FORWARD) < a.indexOf(A11yAction.REPLY));
        assertTrue(a.contains(A11yAction.SEEK_BACK));
        assertTrue(a.contains(A11yAction.SPEED));
        assertTrue(a.contains(A11yAction.DELETE));
        assertTrue(a.contains(A11yAction.PIN));
    }

    @Test public void whoReactedAndPartialQuick() {
        A11yActions.Caps c = new A11yActions.Caps();
        c.canReact = true; c.quickReactions = new String[]{"a", "b"}; c.hasReactions = true;
        List<A11yAction> a = A11yActions.forMessage(c);
        assertTrue(a.contains(A11yAction.REACT_2));
        assertFalse(a.contains(A11yAction.REACT_3));
        assertTrue(a.contains(A11yAction.REACTIONS_PICKER));
        assertTrue(a.contains(A11yAction.WHO_REACTED));
        c.canReact = false;
        assertFalse(A11yActions.forMessage(c).contains(A11yAction.REACTIONS_PICKER));
    }

    @Test public void pinnedShowsUnpin() {
        A11yActions.Caps c = new A11yActions.Caps();
        c.canPin = true; c.isPinned = true;
        List<A11yAction> a = A11yActions.forMessage(c);
        assertTrue(a.contains(A11yAction.UNPIN));
        assertFalse(a.contains(A11yAction.PIN));
    }

    @Test public void openSenderOnlyWhenAllowed() {
        A11yActions.Caps c = new A11yActions.Caps();
        assertFalse(A11yActions.forMessage(c).contains(A11yAction.OPEN_SENDER));
        c.canOpenSender = true;
        List<A11yAction> a = A11yActions.forMessage(c);
        assertTrue(a.contains(A11yAction.OPEN_SENDER));
        assertEquals(A11yAction.SELECT, a.get(a.size() - 1));
        assertEquals(A11yAction.OPEN_SENDER, a.get(a.size() - 2));
    }

    @Test public void replyJumpOnlyWhenReplying() {
        A11yActions.Caps c = new A11yActions.Caps();
        assertFalse(A11yActions.forMessage(c).contains(A11yAction.GO_TO_REPLIED));
        c.hasReplyTo = true;
        assertTrue(A11yActions.forMessage(c).contains(A11yAction.GO_TO_REPLIED));
    }
}
