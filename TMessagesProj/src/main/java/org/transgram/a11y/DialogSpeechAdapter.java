package org.transgram.a11y;

import java.util.TimeZone;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

public final class DialogSpeechAdapter {
    /** The chat list only previews the last message; long texts are cut like upstream does. */
    private static final int LAST_TEXT_CHARS = 120;

    private DialogSpeechAdapter() {}

    public static String build(CharSequence title, TLRPC.Chat chat, TLRPC.User user, MessageObject last,
                               int unread, int mentions, boolean pinned, boolean muted,
                               boolean online, boolean verified, boolean secret) {
        DialogSpeechInput in = new DialogSpeechInput();
        in.title = title == null ? null : TransGramA11y.spokenName(title.toString());
        boolean channel = chat != null && ChatObject.isChannelAndNotMegaGroup(chat);
        if (chat != null) in.type = channel ? DialogSpeechInput.Type.CHANNEL : DialogSpeechInput.Type.GROUP;
        else if (user != null && user.bot) in.type = DialogSpeechInput.Type.BOT;
        in.unread = unread;
        in.mentioned = mentions > 0;
        in.pinned = pinned;
        in.muted = muted;
        in.online = online;
        in.verified = verified;
        in.secret = secret;
        if (last != null) {
            in.dateMillis = last.messageOwner.date * 1000L;
            in.lastOwn = last.isOutOwner();
            if (!in.lastOwn && chat != null && !channel && last.messageOwner.from_id != null && last.messageOwner.from_id.user_id != 0) {
                TLRPC.User from = MessagesController.getInstance(last.currentAccount).getUser(last.messageOwner.from_id.user_id);
                in.lastSender = from != null ? TransGramA11y.spokenName(UserObject.getFirstName(from)) : null;
            }
            CharSequence text = last.messageText;
            if (text != null && text.length() > LAST_TEXT_CHARS) {
                text = text.subSequence(0, LAST_TEXT_CHARS) + "…";
            }
            in.lastText = text;
        }
        return DialogSpeech.say(in, TransGramA11y.format(), System.currentTimeMillis(), TimeZone.getDefault());
    }
}
