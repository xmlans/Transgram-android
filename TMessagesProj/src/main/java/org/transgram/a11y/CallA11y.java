package org.transgram.a11y;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;

import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.messenger.voip.VoIPServiceState;
import org.telegram.messenger.voip.VoipAudioManager;
import org.telegram.tgnet.TLRPC;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Android side of call accessibility: announces call state, labels buttons, adds screen-reader actions. */
public final class CallA11y {
    private CallA11y() {}

    private static final CallSpeech.Strings STRINGS = CallA11y::str;
    private static final CallSpeech.Tracker tracker = new CallSpeech.Tracker();
    private static long establishedAt;
    private static WeakReference<VoIPService> currentService = new WeakReference<>(null);
    private static long groupCallId;
    private static CallSpeech.GroupSelf groupLast = CallSpeech.GroupSelf.OTHER;
    private static final List<String> joined = new ArrayList<>();
    private static final List<String> left = new ArrayList<>();
    private static Runnable flushRoster;
    private static final long ROSTER_WINDOW_MS = 1500;

    private static boolean active() {
        return TransGramA11y.isEnabled() && AndroidUtilities.isAccessibilityScreenReaderEnabled();
    }

    private static void announce(String s) {
        if (s != null && !s.isEmpty()) AndroidUtilities.makeAccessibilityAnnouncement(s);
    }

    static String str(CallSpeech.Key k) {
        int res;
        switch (k) {
            case INCOMING_VOICE: res = R.string.SgCallIncomingVoice; break;
            case INCOMING_VIDEO: res = R.string.SgCallIncomingVideo; break;
            case UNKNOWN_CALLER: res = R.string.SgCallUnknown; break;
            case CONNECTING: res = R.string.SgCallConnecting; break;
            case RINGING: res = R.string.SgCallRinging; break;
            case CONNECTED: res = R.string.SgCallConnected; break;
            case RECONNECTING: res = R.string.SgCallReconnecting; break;
            case ENDED: res = R.string.SgCallEnded; break;
            case ENDED_DURATION: res = R.string.SgCallEndedDuration; break;
            case FAILED: res = R.string.SgCallFailed; break;
            case BUSY: res = R.string.SgCallBusy; break;
            case MUTE: res = R.string.SgCallMute; break;
            case SPEAKER: res = R.string.SgCallSpeaker; break;
            case BLUETOOTH: res = R.string.SgCallBluetooth; break;
            case VIDEO: res = R.string.SgCallVideo; break;
            case FLIP_CAMERA: res = R.string.SgCallFlipCamera; break;
            case END_CALL: res = R.string.SgCallEndCall; break;
            case STATE_ON: res = R.string.SgCallStateOn; break;
            case STATE_OFF: res = R.string.SgCallStateOff; break;
            case DURATION_NOW: res = R.string.SgCallDurationNow; break;
            case DURATION_NONE: res = R.string.SgCallDurationNone; break;
            case NAME_SEP: res = R.string.SgCallNameSep; break;
            case CLAUSE_SEP: res = R.string.SgCallClauseSep; break;
            case JOINED: res = R.string.SgCallJoined; break;
            case LEFT: res = R.string.SgCallLeft; break;
            case JOINED_MANY: res = R.string.SgCallJoinedMany; break;
            case LEFT_MANY: res = R.string.SgCallLeftMany; break;
            case MY_MUTED: res = R.string.SgCallMyMuted; break;
            case MY_UNMUTED: res = R.string.SgCallMyUnmuted; break;
            case MY_FORBIDDEN: res = R.string.SgCallMyForbidden; break;
            default: res = R.string.SgCallMyCanSpeak; break;
        }
        return LocaleController.getString(res);
    }

    private static CallSpeech.Phase phaseOf(int state) {
        switch (state) {
            case VoIPService.STATE_WAITING_INCOMING: return CallSpeech.Phase.INCOMING;
            case VoIPService.STATE_WAIT_INIT:
            case VoIPService.STATE_WAIT_INIT_ACK:
            case VoIPService.STATE_EXCHANGING_KEYS:
            case VoIPService.STATE_WAITING:
            case VoIPService.STATE_REQUESTING:
            case VoIPService.STATE_CREATING: return CallSpeech.Phase.CONNECTING;
            case VoIPService.STATE_RINGING: return CallSpeech.Phase.RINGING;
            case VoIPService.STATE_ESTABLISHED: return CallSpeech.Phase.CONNECTED;
            case VoIPService.STATE_RECONNECTING: return CallSpeech.Phase.RECONNECTING;
            case VoIPService.STATE_ENDED: return CallSpeech.Phase.ENDED;
            case VoIPService.STATE_FAILED: return CallSpeech.Phase.FAILED;
            case VoIPService.STATE_BUSY: return CallSpeech.Phase.BUSY;
            default: return CallSpeech.Phase.NONE;
        }
    }

    /** Called whenever the one-to-one call screen refreshes; the same phase is only spoken once. */
    public static void onCallState(int state, TLRPC.User user, boolean video) {
        if (!active()) return;
        CallSpeech.Phase p = phaseOf(state);
        long now = SystemClock.elapsedRealtime();
        VoIPService svc = VoIPService.getSharedInstance();
        if (svc != null && svc != currentService.get()) { // a new call (each call has its own service): start fresh
            currentService = new WeakReference<>(svc);
            tracker.reset();
            establishedAt = 0;
        }
        if (p == CallSpeech.Phase.CONNECTED && establishedAt == 0) {
            // the service knows when the call really started, even if this screen was reopened later
            long d = svc == null ? 0 : svc.getCallDuration();
            establishedAt = now - Math.max(0, d);
        }
        int sec = p == CallSpeech.Phase.ENDED && establishedAt > 0 ? (int) ((now - establishedAt) / 1000) : 0;
        String name = user == null ? null : TransGramA11y.spokenName(ContactsController.formatName(user.first_name, user.last_name));
        String text = tracker.next(STRINGS, TransGramA11y.format(), p, name, video, sec);
        if (p == CallSpeech.Phase.ENDED) establishedAt = 0;
        if (text == null) return;
        if (p == CallSpeech.Phase.INCOMING) Feedback.play(Feedback.Event.INCOMING_CALL);
        announce(text);
    }

    /** Content description for a call button, e.g. "Mute: on". type is VoIpSwitchLayout.Type.name(). */
    public static String switchLabel(String type, boolean selected, String fallback) {
        if (!TransGramA11y.isEnabled()) return fallback;
        switch (type) {
            case "MICRO": return CallSpeech.button(STRINGS, CallSpeech.Button.MUTE, selected);
            case "SPEAKER": return CallSpeech.button(STRINGS, CallSpeech.Button.SPEAKER, selected);
            case "VIDEO": return CallSpeech.button(STRINGS, CallSpeech.Button.VIDEO, !selected); // selected = camera off
            case "BLUETOOTH": return CallSpeech.button(STRINGS, CallSpeech.Button.BLUETOOTH, true);
            case "CAMERA": return CallSpeech.button(STRINGS, CallSpeech.Button.FLIP_CAMERA, false);
            default: return fallback;
        }
    }

    public static String endCallLabel(String fallback) {
        return TransGramA11y.isEnabled() ? CallSpeech.button(STRINGS, CallSpeech.Button.END_CALL, false) : fallback;
    }

    /** Adds "read call duration" and "switch audio output" to the focusable summary node of the call screen. */
    public static void attachActions(View host, Activity activity) {
        if (!TransGramA11y.isEnabled()) return;
        ViewCompat.addAccessibilityAction(host, LocaleController.getString(R.string.SgCallActReadDuration), (v, args) -> {
            VoIPServiceState st = VoIPService.getSharedState();
            int sec = st == null ? 0 : (int) (st.getCallDuration() / 1000);
            announce(CallSpeech.duration(STRINGS, TransGramA11y.format(), sec));
            return true;
        });
        ViewCompat.addAccessibilityAction(host, LocaleController.getString(R.string.SgCallActAudioOutput), (v, args) -> {
            switchAudio(activity);
            return true;
        });
    }

    private static void switchAudio(Activity activity) {
        VoIPService s = VoIPService.getSharedInstance();
        if (s == null) return;
        VoipAudioManager vam = VoipAudioManager.get();
        int selected = s.isBluetoothOn() ? 2 : vam.isSpeakerphoneOn() ? 0 : 1; // same positions as the call screen's own button
        boolean sheet = s.isBluetoothHeadsetConnected() && s.hasEarpiece();
        s.toggleSpeakerphoneOrShowRouteSheet(activity, false, selected);
        if (!sheet) {
            AndroidUtilities.runOnUIThread(() -> {
                VoIPService cur = VoIPService.getSharedInstance();
                if (cur == null) return;
                announce(cur.isBluetoothOn()
                        ? CallSpeech.button(STRINGS, CallSpeech.Button.BLUETOOTH, true)
                        : CallSpeech.button(STRINGS, CallSpeech.Button.SPEAKER, VoipAudioManager.get().isSpeakerphoneOn()));
            }, 600);
        }
    }

    // ---- group calls ----

    /** My own state in a group call; selfState: 0 = other, 1 = muted, 2 = talking, 3 = forbidden. */
    public static void onGroupSelf(long callId, int selfState) {
        if (!active()) return;
        CallSpeech.GroupSelf cur = CallSpeech.GroupSelf.values()[Math.max(0, Math.min(3, selfState))];
        if (callId != groupCallId) {
            groupCallId = callId;
            groupLast = CallSpeech.GroupSelf.OTHER;
        }
        if (cur == CallSpeech.GroupSelf.OTHER) return;
        String text = CallSpeech.groupSelf(STRINGS, groupLast, cur);
        groupLast = cur;
        announce(text);
    }

    public static void onGroupParticipant(ChatObject.Call call, int account, long peerId, boolean isJoin) {
        if (!active() || !TransGramA11y.callJoinLeave()) return;
        VoIPService s = VoIPService.getSharedInstance();
        if (s == null || s.groupCall != call) return;
        String name;
        if (peerId > 0) {
            TLRPC.User u = MessagesController.getInstance(account).getUser(peerId);
            name = u == null ? null : UserObject.getUserName(u);
        } else {
            TLRPC.Chat c = MessagesController.getInstance(account).getChat(-peerId);
            name = c == null ? null : c.title;
        }
        if (name == null || name.isEmpty()) return;
        (isJoin ? joined : left).add(TransGramA11y.spokenName(name));
        if (flushRoster == null) {
            flushRoster = () -> {
                flushRoster = null;
                List<String> j = new ArrayList<>(joined), l = new ArrayList<>(left);
                joined.clear();
                left.clear();
                announce(CallSpeech.joinLeave(STRINGS, j, l));
            };
            AndroidUtilities.runOnUIThread(flushRoster, ROSTER_WINDOW_MS);
        }
    }
}
