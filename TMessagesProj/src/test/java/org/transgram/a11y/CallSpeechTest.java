package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class CallSpeechTest {
    private static final SpeechFormat ZH_FMT = new SpeechFormatZh();
    private static final SpeechFormat EN_FMT = new SpeechFormatEn();

    private static final Map<CallSpeech.Key, String> ZH = new EnumMap<>(CallSpeech.Key.class);
    private static final Map<CallSpeech.Key, String> EN = new EnumMap<>(CallSpeech.Key.class);

    static {
        ZH.put(CallSpeech.Key.INCOMING_VOICE, "%1$s 来电");
        ZH.put(CallSpeech.Key.INCOMING_VIDEO, "%1$s 视频来电");
        ZH.put(CallSpeech.Key.UNKNOWN_CALLER, "未知号码");
        ZH.put(CallSpeech.Key.CONNECTING, "正在连接");
        ZH.put(CallSpeech.Key.RINGING, "对方响铃中");
        ZH.put(CallSpeech.Key.CONNECTED, "已接通");
        ZH.put(CallSpeech.Key.RECONNECTING, "重新连接中");
        ZH.put(CallSpeech.Key.ENDED, "通话结束");
        ZH.put(CallSpeech.Key.ENDED_DURATION, "通话结束，%1$s");
        ZH.put(CallSpeech.Key.FAILED, "通话失败");
        ZH.put(CallSpeech.Key.BUSY, "对方忙线");
        ZH.put(CallSpeech.Key.MUTE, "静音");
        ZH.put(CallSpeech.Key.SPEAKER, "扬声器");
        ZH.put(CallSpeech.Key.BLUETOOTH, "蓝牙");
        ZH.put(CallSpeech.Key.VIDEO, "视频");
        ZH.put(CallSpeech.Key.FLIP_CAMERA, "切换摄像头");
        ZH.put(CallSpeech.Key.END_CALL, "挂断");
        ZH.put(CallSpeech.Key.STATE_ON, "%1$s：开");
        ZH.put(CallSpeech.Key.STATE_OFF, "%1$s：关");
        ZH.put(CallSpeech.Key.DURATION_NOW, "通话时长 %1$s");
        ZH.put(CallSpeech.Key.DURATION_NONE, "尚未接通");
        ZH.put(CallSpeech.Key.NAME_SEP, "、");
        ZH.put(CallSpeech.Key.CLAUSE_SEP, "，");
        ZH.put(CallSpeech.Key.JOINED, "%1$s 加入了");
        ZH.put(CallSpeech.Key.LEFT, "%1$s 离开了");
        ZH.put(CallSpeech.Key.JOINED_MANY, "%1$d 人加入了");
        ZH.put(CallSpeech.Key.LEFT_MANY, "%1$d 人离开了");
        ZH.put(CallSpeech.Key.MY_MUTED, "麦克风已关闭");
        ZH.put(CallSpeech.Key.MY_UNMUTED, "麦克风已开启");
        ZH.put(CallSpeech.Key.MY_FORBIDDEN, "你已被静音，不能发言");
        ZH.put(CallSpeech.Key.MY_CAN_SPEAK, "你可以发言");

        EN.put(CallSpeech.Key.INCOMING_VOICE, "%1$s incoming call");
        EN.put(CallSpeech.Key.INCOMING_VIDEO, "%1$s incoming video call");
        EN.put(CallSpeech.Key.UNKNOWN_CALLER, "Unknown");
        EN.put(CallSpeech.Key.ENDED, "Call ended");
        EN.put(CallSpeech.Key.ENDED_DURATION, "Call ended, %1$s");
        EN.put(CallSpeech.Key.MUTE, "Mute");
        EN.put(CallSpeech.Key.STATE_ON, "%1$s: on");
        EN.put(CallSpeech.Key.STATE_OFF, "%1$s: off");
    }

    private static CallSpeech.Strings strings(Map<CallSpeech.Key, String> m) {
        return k -> m.get(k);
    }

    private static final CallSpeech.Strings S = strings(ZH);

    @Test public void incomingVoiceAndVideo() {
        assertEquals("张三 来电", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.INCOMING, "张三", false, 0));
        assertEquals("张三 视频来电", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.INCOMING, "张三", true, 0));
        assertEquals("Ann incoming video call", CallSpeech.status(strings(EN), EN_FMT, CallSpeech.Phase.INCOMING, "Ann", true, 0));
    }

    @Test public void incomingWithoutNameUsesUnknown() {
        assertEquals("未知号码 来电", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.INCOMING, null, false, 0));
        assertEquals("未知号码 来电", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.INCOMING, "  ", false, 0));
    }

    @Test public void simplePhases() {
        assertEquals("正在连接", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.CONNECTING, "x", false, 0));
        assertEquals("对方响铃中", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.RINGING, "x", false, 0));
        assertEquals("已接通", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.CONNECTED, "x", false, 0));
        assertEquals("重新连接中", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.RECONNECTING, "x", false, 0));
        assertEquals("通话失败", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.FAILED, "x", false, 0));
        assertEquals("对方忙线", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.BUSY, "x", false, 0));
        assertNull(CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.NONE, "x", false, 0));
    }

    @Test public void endedWithAndWithoutDuration() {
        assertEquals("通话结束，3分12秒", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.ENDED, "x", false, 192));
        assertEquals("通话结束", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.ENDED, "x", false, 0));
        assertEquals("通话结束，1小时0分5秒", CallSpeech.status(S, ZH_FMT, CallSpeech.Phase.ENDED, "x", false, 3605));
        assertEquals("Call ended, 3 minutes 12 seconds",
                CallSpeech.status(strings(EN), EN_FMT, CallSpeech.Phase.ENDED, "x", false, 192));
    }

    @Test public void buttonLabelsCarryState() {
        assertEquals("静音：开", CallSpeech.button(S, CallSpeech.Button.MUTE, true));
        assertEquals("静音：关", CallSpeech.button(S, CallSpeech.Button.MUTE, false));
        assertEquals("扬声器：开", CallSpeech.button(S, CallSpeech.Button.SPEAKER, true));
        assertEquals("视频：关", CallSpeech.button(S, CallSpeech.Button.VIDEO, false));
        assertEquals("Mute: on", CallSpeech.button(strings(EN), CallSpeech.Button.MUTE, true));
    }

    @Test public void statelessButtonsIgnoreState() {
        assertEquals("切换摄像头", CallSpeech.button(S, CallSpeech.Button.FLIP_CAMERA, true));
        assertEquals("挂断", CallSpeech.button(S, CallSpeech.Button.END_CALL, false));
        assertEquals("蓝牙", CallSpeech.button(S, CallSpeech.Button.BLUETOOTH, true));
    }

    @Test public void durationReading() {
        assertEquals("通话时长 1分05秒", CallSpeech.duration(S, ZH_FMT, 65));
        assertEquals("尚未接通", CallSpeech.duration(S, ZH_FMT, 0));
        assertEquals("尚未接通", CallSpeech.duration(S, ZH_FMT, -1));
    }

    @Test public void trackerDoesNotRepeatAPhase() {
        CallSpeech.Tracker t = new CallSpeech.Tracker();
        assertEquals("张三 来电", t.next(S, ZH_FMT, CallSpeech.Phase.INCOMING, "张三", false, 0));
        assertNull(t.next(S, ZH_FMT, CallSpeech.Phase.INCOMING, "张三", false, 0));
        assertEquals("已接通", t.next(S, ZH_FMT, CallSpeech.Phase.CONNECTED, "张三", false, 0));
        assertNull(t.next(S, ZH_FMT, CallSpeech.Phase.CONNECTED, "张三", false, 5));
        assertEquals("重新连接中", t.next(S, ZH_FMT, CallSpeech.Phase.RECONNECTING, "张三", false, 6));
        assertEquals("已接通", t.next(S, ZH_FMT, CallSpeech.Phase.CONNECTED, "张三", false, 8));
        assertEquals("通话结束，0分12秒", t.next(S, ZH_FMT, CallSpeech.Phase.ENDED, "张三", false, 12));
        assertNull(t.next(S, ZH_FMT, CallSpeech.Phase.ENDED, "张三", false, 12));
    }

    @Test public void trackerIgnoresNoneAndSpeaksNextCallAfterEnd() {
        CallSpeech.Tracker t = new CallSpeech.Tracker();
        assertNull(t.next(S, ZH_FMT, CallSpeech.Phase.NONE, "x", false, 0));
        assertEquals("正在连接", t.next(S, ZH_FMT, CallSpeech.Phase.CONNECTING, "x", false, 0));
        assertEquals("通话结束", t.next(S, ZH_FMT, CallSpeech.Phase.ENDED, "x", false, 0));
        assertEquals("正在连接", t.next(S, ZH_FMT, CallSpeech.Phase.CONNECTING, "x", false, 0));
        t.reset();
        assertEquals("正在连接", t.next(S, ZH_FMT, CallSpeech.Phase.CONNECTING, "x", false, 0));
    }

    @Test public void groupSelfTransitions() {
        CallSpeech.GroupSelf M = CallSpeech.GroupSelf.MUTED, T = CallSpeech.GroupSelf.TALKING,
                F = CallSpeech.GroupSelf.FORBIDDEN, O = CallSpeech.GroupSelf.OTHER;
        assertEquals("麦克风已开启", CallSpeech.groupSelf(S, M, T));
        assertEquals("麦克风已关闭", CallSpeech.groupSelf(S, T, M));
        assertEquals("你已被静音，不能发言", CallSpeech.groupSelf(S, T, F));
        assertEquals("你已被静音，不能发言", CallSpeech.groupSelf(S, O, F));
        assertEquals("你可以发言", CallSpeech.groupSelf(S, F, M));
        assertEquals("你可以发言", CallSpeech.groupSelf(S, F, T));
        assertNull(CallSpeech.groupSelf(S, M, M));
        assertNull(CallSpeech.groupSelf(S, O, M)); // initial connect is silent
        assertNull(CallSpeech.groupSelf(S, M, O));
        assertNull(CallSpeech.groupSelf(S, F, F));
    }

    @Test public void joinLeaveText() {
        assertEquals("小明 加入了", CallSpeech.joinLeave(S, Arrays.asList("小明"), Collections.<String>emptyList()));
        assertEquals("小明 离开了", CallSpeech.joinLeave(S, Collections.<String>emptyList(), Arrays.asList("小明")));
        assertEquals("小明、小红 加入了", CallSpeech.joinLeave(S, Arrays.asList("小明", "小红"), Collections.<String>emptyList()));
        assertEquals("3 人加入了", CallSpeech.joinLeave(S, Arrays.asList("a", "b", "c"), Collections.<String>emptyList()));
        assertEquals("小明 加入了，小红 离开了", CallSpeech.joinLeave(S, Arrays.asList("小明"), Arrays.asList("小红")));
        assertNull(CallSpeech.joinLeave(S, Collections.<String>emptyList(), Collections.<String>emptyList()));
    }
}
