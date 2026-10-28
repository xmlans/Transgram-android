package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NameCleanerTest {
    private static void same(String s) {
        assertEquals(s, NameCleaner.clean(s));
    }

    private static void is(String expected, String in) {
        assertEquals(expected, NameCleaner.clean(in));
    }

    @Test public void ordinaryNamesUnchanged() {
        same("张三");
        same("John Smith");
        same("José");
        same("Müller");
        same("李 小龙");
        same("กี่"); // Thai with two stacked marks
        same("Nguyễn"); // decomposed Vietnamese
        same("彡");
        same("阿彡");
    }

    @Test public void nullAndEmpty() {
        assertNull(NameCleaner.clean(null));
        assertEquals("", NameCleaner.clean(""));
    }

    @Test public void javaneseAndTibetanOrnaments() {
        is("小明", "꧁༺小明༻꧂");
    }

    @Test public void dingbatsAndMiscSymbols() {
        is("小红", "✿小红✿");
        is("小红", "❥小红");
        is("阿杰", "☆彡阿杰"); // star + 彡 prefix
        is("a", "✦a✦");
    }

    @Test public void decorationBetweenWordsLeavesSpace() {
        is("Anna Lee", "Anna★Lee");
        is("小明 小红", "小明✿小红");
        is("Anna Lee", "Anna ★ Lee");
        is("小明", "★小明★");
    }

    @Test public void genderSignsKept() {
        same("Bob♂");
        same("Alice♀");
        same("Sam⚧");
    }

    @Test public void boxBlockAndGeometric() {
        is("小明", "░▒小明▒░");
        is("小明", "◆小明◆");
        is("小明", "╔═小明═╗");
    }

    @Test public void zeroWidthAndBidi() {
        is("小明", "小​明‎");
        is("alice", "‮alice‬");
        is("Bob", "﻿Bob");
        is("小明", "小‍明"); // orphan ZWJ
        is("小明", "⁦小明⁩");
    }

    @Test public void stackedCombiningMarks() {
        is("小́明", "小́̂̃明"); // CJK: one mark
        is("é̂", "é̂̃̄̅"); // Latin keeps two (decomposed Vietnamese)
        same("é"); // single accent untouched
    }

    @Test public void consecutiveEmojiCollapsed() {
        is("🌸小明", "🌸🌸🌸小明");
        is("小明🌸", "小明🌸🌸");
        same("🌸小明🌸"); // separated by text: kept
        is("🌸小明", "🌸✿🌸小明"); // decoration between emoji
    }

    @Test public void zwjSequencesAndSkinTones() {
        String family = "👨‍👩‍👧";
        same(family + "小明");
        is(family + "小明", family + family + "小明");
        String thumb = "👍🏽";
        is(thumb + "小明", thumb + thumb + "小明");
        String dev = "🧑🏽‍💻";
        same(dev + " dev");
        String woman = "🤷‍♀️";
        same(woman + "小明");
    }

    @Test public void flagsAndHeart() {
        is("🇨🇳小明", "🇨🇳🇺🇸小明");
        same("❤️小明");
        same("✨小明");
    }

    @Test public void pairedBracketsOnlyAroundWholeName() {
        is("小明", "【小明】");
        is("小明", "『小明』");
        is("小明", "〖小明〗");
        is("小明", "꧁『小明』꧂");
        same("【VIP】小明");
        same("【小明』"); // mismatched
        same("小明】");
    }

    @Test public void whitespaceCollapsed() {
        is("小 明", "  小   明 ");
        is("小明", "　小明　");
        is("小明", "✿ 小明 ✿");
    }

    @Test public void allDecorationFallsBackToOriginal() {
        same("✿✿✿");
        same("꧁꧂");
        same("★☆★");
        same("​​");
    }

    @Test public void tooShortResultFallsBackToOriginal() {
        same("✿✿✿✿✿✿✿✿✿✿a"); // 1 of 11 chars left
        is("a", "✿a✿"); // 1 of 3 is >= 20%
    }
}
