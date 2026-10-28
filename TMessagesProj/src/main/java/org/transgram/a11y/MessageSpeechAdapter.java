package org.transgram.a11y;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

/** Android side: MessageObject -> SpeechInput -> spoken text (links kept as spans). */
public final class MessageSpeechAdapter {
    private MessageSpeechAdapter() {}

    public static SpannableStringBuilder build(MessageObject m, String senderName, CharSequence adminTitle) {
        SpeechFormat f = TransGramA11y.format();
        SpeechInput input = toInput(m, senderName, adminTitle);
        // Empty text body (e.g. rich-layout messages): let the caller keep the upstream text.
        if (input.kind == SpeechInput.Kind.TEXT && TextUtils.isEmpty(input.body)) return null;
        List<CharSequence> parts = MessageSpeech.build(input, TransGramA11y.speech(), f,
                System.currentTimeMillis(), TimeZone.getDefault());
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (m.isHiddenSensitive()) {
            // Media covered by the 18+ / sensitive mask: say so, the user can double tap to reveal it.
            sb.append(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.SgMediaHidden));
        }
        for (CharSequence p : parts) {
            if (sb.length() > 0) sb.append(f.separator());
            sb.append(p);
        }
        return sb;
    }

    static SpeechInput toInput(MessageObject m, String senderName, CharSequence adminTitle) {
        SpeechInput in = new SpeechInput();
        in.own = m.isOutOwner();
        in.senderName = TransGramA11y.spokenName(senderName);
        in.adminTitle = TextUtils.isEmpty(adminTitle) ? null : adminTitle.toString();
        in.dateMillis = m.messageOwner.date * 1000L;
        in.edited = m.isEdited();
        in.pinned = m.messageOwner.pinned;
        in.silent = m.messageOwner.silent;
        int ttl = m.messageOwner.ttl;
        in.selfDestructSec = ttl > 0 && ttl != 0x7FFFFFFF ? ttl : 0; // 0x7FFFFFFF = view-once sentinel
        kind(m, in);
        if ((in.kind == SpeechInput.Kind.VOICE || in.kind == SpeechInput.Kind.ROUND_VIDEO)
                && m.isVoiceTranscriptionOpen() && !TextUtils.isEmpty(m.messageOwner.voiceTranscription)) {
            in.body = m.messageOwner.voiceTranscription;
        } else if (!TextUtils.isEmpty(m.caption)) in.body = m.caption;
        else if (in.kind == SpeechInput.Kind.TEXT) in.body = m.messageText;
        // TransGram: cached OCR text of a photo / image file, when "read text in images with the message" is on
        if (in.kind == SpeechInput.Kind.PHOTO || in.kind == SpeechInput.Kind.FILE) {
            in.ocrText = org.transgram.ocr.OcrUi.textForSpeech(m);
        }
        if (m.isForwarded()) in.forwardFrom = m.getForwardedName();
        if (m.replyMessageObject != null) {
            in.replyToName = TransGramA11y.spokenName(senderNameOf(m.replyMessageObject));
            CharSequence t = m.replyMessageObject.messageText;
            in.replyToText = t == null ? null : t.toString();
        }
        if (in.own) {
            if (m.isSendError()) in.status = SpeechInput.Status.FAILED;
            else if (m.isSending()) in.status = SpeechInput.Status.SENDING;
            else in.status = m.isUnread() ? SpeechInput.Status.UNREAD : SpeechInput.Status.READ;
        }
        in.reactions = reactions(m);
        in.views = m.messageOwner.views;
        in.comments = m.messageOwner.replies != null ? m.messageOwner.replies.replies : 0;
        return in;
    }

    private static void kind(MessageObject m, SpeechInput in) {
        if (m.isRoundVideo()) {
            in.kind = SpeechInput.Kind.ROUND_VIDEO;
            in.durationSec = (int) m.getDuration();
        } else if (m.isVoice()) {
            in.kind = SpeechInput.Kind.VOICE;
            in.durationSec = (int) m.getDuration();
        } else if (m.isMusic()) {
            in.kind = SpeechInput.Kind.MUSIC;
            in.durationSec = (int) m.getDuration();
            String author = m.getMusicAuthor(false);
            String title = m.getMusicTitle();
            StringBuilder mt = new StringBuilder();
            if (!TextUtils.isEmpty(author)) mt.append(author);
            if (!TextUtils.isEmpty(title)) {
                if (mt.length() > 0) mt.append(' ');
                mt.append(title);
            }
            in.musicTitle = mt.length() > 0 ? mt.toString() : null;
        } else if (m.isAnyKindOfSticker()) {
            in.kind = SpeechInput.Kind.STICKER;
            in.stickerEmoji = m.getStickerEmoji();
        } else if (m.isGif()) {
            in.kind = SpeechInput.Kind.GIF;
        } else if (m.isVideo()) {
            in.kind = SpeechInput.Kind.VIDEO;
            in.durationSec = (int) m.getDuration();
            in.fileSize = m.getSize();
        } else if (m.isPhoto()) {
            in.kind = SpeechInput.Kind.PHOTO;
        } else if (m.type == MessageObject.TYPE_POLL) {
            in.kind = SpeechInput.Kind.POLL;
            TLRPC.MessageMedia media = MessageObject.getMedia(m.messageOwner);
            if (media instanceof TLRPC.TL_messageMediaPoll) {
                TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) media).poll;
                if (poll != null && poll.question != null) in.pollQuestion = poll.question.text;
            }
        } else if (m.type == MessageObject.TYPE_GEO) {
            in.kind = SpeechInput.Kind.LOCATION;
        } else if (m.type == MessageObject.TYPE_CONTACT) {
            in.kind = SpeechInput.Kind.CONTACT;
        } else if (m.getDocument() != null) {
            in.kind = SpeechInput.Kind.FILE;
            in.fileName = m.getDocumentName();
            in.fileSize = m.getSize();
        } else if (!m.isMediaEmpty()) {
            in.kind = SpeechInput.Kind.OTHER_MEDIA;
        }
        if (m.loadedFileSize > 0 && in.fileSize > 0) {
            in.downloadPercent = (int) Math.min(100, m.loadedFileSize * 100 / in.fileSize);
        }
    }

    private static String senderNameOf(MessageObject m) {
        if (m.isOutOwner()) return TransGramA11y.format().label(SpeechFormat.Label.YOU);
        TLRPC.User u = m.messageOwner.from_id != null && m.messageOwner.from_id.user_id != 0
                ? MessagesController.getInstance(m.currentAccount).getUser(m.messageOwner.from_id.user_id) : null;
        return u != null ? UserObject.getUserName(u) : m.getForwardedName();
    }

    private static List<SpeechInput.Reaction> reactions(MessageObject m) {
        List<SpeechInput.Reaction> out = new ArrayList<>();
        if (m.messageOwner.reactions == null || m.messageOwner.reactions.results == null) return out;
        for (TLRPC.ReactionCount rc : m.messageOwner.reactions.results) {
            String emoji;
            if (rc.reaction instanceof TLRPC.TL_reactionEmoji) {
                emoji = ((TLRPC.TL_reactionEmoji) rc.reaction).emoticon;
            } else if (rc.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                TLRPC.Document d = AnimatedEmojiDrawable.findDocument(m.currentAccount, ((TLRPC.TL_reactionCustomEmoji) rc.reaction).document_id);
                emoji = MessageObject.findAnimatedEmojiEmoticon(d, "⭐");
            } else if (rc.reaction instanceof TLRPC.TL_reactionPaid) {
                emoji = "⭐";
            } else {
                continue;
            }
            out.add(new SpeechInput.Reaction(emoji, rc.count, rc.chosen));
        }
        return out;
    }
}
