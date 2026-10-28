package org.transgram.a11y;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class SpeechFormatZh implements SpeechFormat {

    @Override
    public String time(long dateMillis, long nowMillis, TimeZone tz) {
        Calendar d = Calendar.getInstance(tz);
        d.setTimeInMillis(dateMillis);
        Calendar n = Calendar.getInstance(tz);
        n.setTimeInMillis(nowMillis);
        String clock = clock(d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE));
        int dayDiff = SpeechDates.dayDiff(d, n);
        if (dayDiff == 0) return clock;
        if (dayDiff == 1) return "昨天 " + clock;
        String date = (d.get(Calendar.MONTH) + 1) + "月" + d.get(Calendar.DAY_OF_MONTH) + "日";
        if (d.get(Calendar.YEAR) != n.get(Calendar.YEAR)) date = d.get(Calendar.YEAR) + "年" + date;
        return date + " " + clock;
    }

    private static String clock(int h, int m) {
        String part;
        int h12;
        if (h < 6) { part = "凌晨"; h12 = h; }
        else if (h < 12) { part = "上午"; h12 = h; }
        else if (h < 13) { part = "中午"; h12 = h; }
        else if (h < 18) { part = "下午"; h12 = h - 12; }
        else { part = "晚上"; h12 = h - 12; }
        return part + h12 + "点" + (m == 0 ? "" : String.format(Locale.US, "%02d分", m));
    }

    @Override
    public String duration(int seconds) {
        int h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        if (h > 0) return h + "小时" + m + "分" + s + "秒";
        return m + "分" + String.format(Locale.US, "%02d秒", s);
    }

    @Override public String size(long bytes) { return SpeechDates.size(bytes, "字节", ""); }
    @Override public String count(int n) { return n + "条"; }
    @Override public String separator() { return "，"; }
    @Override public String colon() { return "："; }

    @Override
    public String label(Label label) {
        switch (label) {
            case YOU: return "你";
            case PHOTO: return "照片";
            case OCR_TEXT: return "图中文字";
            case VIDEO: return "视频";
            case VOICE: return "语音";
            case ROUND_VIDEO: return "圆形视频";
            case MUSIC: return "音乐";
            case GIF: return "GIF";
            case STICKER: return "贴纸";
            case FILE: return "文件";
            case POLL: return "投票";
            case LOCATION: return "位置";
            case CONTACT: return "联系人";
            case MEDIA: return "媒体";
            case FORWARDED_FROM: return "转发自";
            case REPLY_TO: return "回复";
            case EDITED: return "已编辑";
            case PINNED: return "已置顶";
            case SILENT: return "静默发送";
            case SELF_DESTRUCT: return "阅后即焚";
            case PRICE_STARS: return "星星";
            case READ: return "已读";
            case UNREAD: return "未读";
            case SENDING: return "发送中";
            case FAILED: return "发送失败";
            case REACTIONS: return "回应";
            case YOU_REACTED: return "你回应了";
            case VIEWS: return "浏览";
            case COMMENTS: return "评论";
            case DOWNLOADED_PERCENT: return "已下载";
            case UNREAD_COUNT: return "未读";
            case MENTIONED: return "有人提到你";
            case MUTED: return "已静音";
            case PINNED_CHAT: return "已置顶";
            case GROUP: return "群组";
            case CHANNEL: return "频道";
            case BOT: return "机器人";
            case ONLINE: return "在线";
            case VERIFIED: return "已认证";
            case SECRET_CHAT: return "加密聊天";
            default: return label.name();
        }
    }
}
