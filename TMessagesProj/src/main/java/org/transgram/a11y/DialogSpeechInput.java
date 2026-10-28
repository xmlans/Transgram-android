package org.transgram.a11y;

public class DialogSpeechInput {
    public enum Type { PRIVATE, GROUP, CHANNEL, BOT }

    public String title;
    public Type type = Type.PRIVATE;
    public int unread;
    public boolean lastOwn;
    public String lastSender;
    public CharSequence lastText;
    public long dateMillis;
    public boolean pinned;
    public boolean muted;
    public boolean mentioned;
    public boolean online;
    public boolean verified;
    public boolean secret;
}
