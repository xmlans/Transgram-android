package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public class MessageSpeechTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");
    private static final SpeechFormat ZH = new SpeechFormatZh();
    private static final SpeechFormat EN = new SpeechFormatEn();
    private static final SpeechSettings STD = SpeechSettings.forPreset(SpeechPreset.STANDARD);

    private static long at(int d, int h, int m) {
        Calendar c = Calendar.getInstance(TZ);
        c.clear();
        c.set(2026, 9, d, h, m);
        return c.getTimeInMillis();
    }

    private static final long NOW = at(15, 18, 0);

    private static String say(SpeechInput in, SpeechSettings s, SpeechFormat f) {
        return MessageSpeech.join(MessageSpeech.build(in, s, f, NOW, TZ), f);
    }

    private static SpeechInput text(String body) {
        SpeechInput in = new SpeechInput();
        in.body = body;
        in.dateMillis = at(15, 15, 20);
        return in;
    }

    @Test public void incomingGroupText() {
        SpeechInput in = text("晚上一起吃饭吗");
        in.senderName = "张三";
        assertEquals("张三，晚上一起吃饭吗，下午3点20分", say(in, STD, ZH));
    }

    @Test public void adminTitleFollowsName() {
        SpeechInput in = text("hi");
        in.senderName = "张三";
        in.adminTitle = "管理员";
        assertEquals("张三 管理员，hi，下午3点20分", say(in, STD, ZH));
    }

    @Test public void ownMessageSaysYouAndStatus() {
        SpeechInput in = text("好的");
        in.own = true;
        in.status = SpeechInput.Status.READ;
        assertEquals("你，好的，下午3点20分，已读", say(in, STD, ZH));
    }

    @Test public void voiceMessage() {
        SpeechInput in = text(null);
        in.kind = SpeechInput.Kind.VOICE;
        in.durationSec = 12;
        in.senderName = "李四";
        assertEquals("李四，语音 0分12秒，下午3点20分", say(in, STD, ZH));
    }

    @Test public void stickerWithEmoji() {
        SpeechInput in = text(null);
        in.kind = SpeechInput.Kind.STICKER;
        in.stickerEmoji = "😂";
        assertEquals("贴纸 😂，下午3点20分", say(in, STD, ZH));
    }

    @Test public void fileWithNameAndSize() {
        SpeechInput in = text(null);
        in.kind = SpeechInput.Kind.FILE;
        in.fileName = "报告.pdf";
        in.fileSize = 2_411_725;
        assertEquals("文件 报告.pdf 2.3MB，下午3点20分", say(in, STD, ZH));
    }

    @Test public void forwardAndReplyTruncatedTo30() {
        SpeechInput in = text("看这个");
        in.forwardFrom = "科技频道";
        in.replyToName = "王五";
        in.replyToText = "这是一段很长很长的原文，用来测试截断到三十个字符的效果是否正确呢";
        assertEquals("转发自 科技频道，回复 王五：这是一段很长很长的原文，用来测试截断到三十个字符的效果是否正…，看这个，下午3点20分",
                say(in, STD, ZH));
    }

    @Test public void extrasAndOldDate() {
        SpeechInput in = text("hi");
        in.edited = true;
        in.pinned = true;
        in.dateMillis = at(12, 15, 20);
        assertEquals("hi，已编辑，已置顶，10月12日 下午3点20分", say(in, STD, ZH));
    }

    @Test public void reactionsIncludingMine() {
        SpeechInput in = text("hi");
        in.reactions = Arrays.asList(new SpeechInput.Reaction("❤️", 3, true), new SpeechInput.Reaction("👍", 1, false));
        assertEquals("hi，下午3点20分，回应：❤️ 3，👍 1，你回应了 ❤️", say(in, STD, ZH));
    }

    @Test public void statsOnlyInVerbose() {
        SpeechInput in = text("news");
        in.views = 1200;
        in.comments = 4;
        assertEquals("news，下午3点20分", say(in, STD, ZH));
        assertEquals("news，下午3点20分，浏览 1200，评论 4", say(in, SpeechSettings.forPreset(SpeechPreset.VERBOSE), ZH));
    }

    @Test public void briefDropsTime() {
        SpeechInput in = text("hi");
        in.senderName = "张三";
        assertEquals("张三，hi", say(in, SpeechSettings.forPreset(SpeechPreset.BRIEF), ZH));
    }

    @Test public void customOrderBodyFirst() {
        SpeechInput in = text("hi");
        in.senderName = "张三";
        assertEquals("hi，张三，下午3点20分", say(in, STD.withOrder(Arrays.asList(SpeechField.BODY)), ZH));
    }

    @Test public void verboseAddsSizeAndDownloadProgress() {
        SpeechInput in = text(null);
        in.kind = SpeechInput.Kind.VIDEO;
        in.durationSec = 65;
        in.fileSize = 10_485_760;
        in.downloadPercent = 40;
        assertEquals("视频 1分05秒 10.0MB 已下载 40%，下午3点20分", say(in, SpeechSettings.forPreset(SpeechPreset.VERBOSE), ZH));
        assertEquals("视频 1分05秒，下午3点20分", say(in, STD, ZH));
    }

    @Test public void english() {
        SpeechInput in = text("hello");
        in.own = true;
        in.status = SpeechInput.Status.FAILED;
        assertEquals("You, hello, 3:20 PM, failed to send", say(in, STD, EN));
    }

    @Test public void englishReply() {
        SpeechInput in = text("ok");
        in.replyToName = "Ann";
        in.replyToText = "lunch?";
        assertEquals("Reply to Ann: lunch?, ok, 3:20 PM", say(in, STD, EN));
    }

    @Test public void bodyKeepsOriginalCharSequenceObject() {
        StringBuilder body = new StringBuilder("link text");
        SpeechInput in = text(null);
        in.body = body;
        boolean found = false;
        for (CharSequence part : MessageSpeech.build(in, STD, ZH, NOW, TZ)) found |= part == body;
        assertTrue("spans (links) must survive for TalkBack's links menu", found);
    }
}
