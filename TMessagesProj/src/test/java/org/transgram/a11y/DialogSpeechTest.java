package org.transgram.a11y;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public class DialogSpeechTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");

    private static long at(int d, int h, int m) {
        Calendar c = Calendar.getInstance(TZ);
        c.clear();
        c.set(2026, 9, d, h, m);
        return c.getTimeInMillis();
    }

    @Test public void groupWithUnreadAndMention() {
        DialogSpeechInput in = new DialogSpeechInput();
        in.title = "读屏交流群";
        in.type = DialogSpeechInput.Type.GROUP;
        in.unread = 3;
        in.lastSender = "张三";
        in.lastText = "明天见";
        in.dateMillis = at(15, 15, 20);
        in.mentioned = true;
        in.muted = true;
        assertEquals("读屏交流群 群组，未读 3条，张三：明天见，下午3点20分，有人提到你，已静音",
                DialogSpeech.say(in, new SpeechFormatZh(), at(15, 18, 0), TZ));
    }

    @Test public void privateChatOwnLastMessage() {
        DialogSpeechInput in = new DialogSpeechInput();
        in.title = "李四";
        in.lastOwn = true;
        in.lastText = "好的";
        in.dateMillis = at(14, 9, 0);
        in.pinned = true;
        assertEquals("李四，你：好的，昨天 上午9点，已置顶", DialogSpeech.say(in, new SpeechFormatZh(), at(15, 18, 0), TZ));
    }

    @Test public void englishChannel() {
        DialogSpeechInput in = new DialogSpeechInput();
        in.title = "News";
        in.type = DialogSpeechInput.Type.CHANNEL;
        in.unread = 12;
        in.lastText = "Breaking";
        in.dateMillis = at(15, 15, 20);
        assertEquals("News channel, 12 unread, Breaking, 3:20 PM", DialogSpeech.say(in, new SpeechFormatEn(), at(15, 18, 0), TZ));
    }

    /** Order: title [type|secret], verified, online, unread, last message, time, mentioned, pinned, muted. */
    @Test public void secretOnlineVerifiedOrder() {
        DialogSpeechInput in = new DialogSpeechInput();
        in.title = "李四";
        in.secret = true;
        in.verified = true;
        in.online = true;
        in.unread = 2;
        in.lastText = "好";
        assertEquals("李四 加密聊天，已认证，在线，未读 2条，好",
                DialogSpeech.say(in, new SpeechFormatZh(), at(15, 18, 0), TZ));
    }

    @Test public void onlineOnly() {
        DialogSpeechInput in = new DialogSpeechInput();
        in.title = "李四";
        in.online = true;
        in.lastText = "好";
        assertEquals("李四，在线，好", DialogSpeech.say(in, new SpeechFormatZh(), at(15, 18, 0), TZ));
    }

    @Test public void englishStates() {
        DialogSpeechInput in = new DialogSpeechInput();
        in.title = "Ann";
        in.secret = true;
        in.online = true;
        in.verified = true;
        assertEquals("Ann secret chat, verified, online", DialogSpeech.say(in, new SpeechFormatEn(), at(15, 18, 0), TZ));
    }
}
