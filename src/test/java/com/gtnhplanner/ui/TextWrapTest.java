package com.gtnhplanner.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Lines break at spaces in English and between characters in Chinese, with a character one unit wide. */
class TextWrapTest {

    private static List<String> wrap(final String text, final int width) {
        return TextWrap.wrap(text, width, s -> s.length());
    }

    @Test
    void englishBreaksAtSpacesAndDropsThem() {
        assertEquals(List.of("set a rate", "on what", "you want"), wrap("set a rate on what you want", 10));
    }

    @Test
    void chineseBreaksBetweenCharacters() {
        assertEquals(List.of("给想要的产", "物设一个速", "率"), wrap("给想要的产物设一个速率", 5));
    }

    @Test
    void closingPunctuationStaysWithItsLine() {
        assertEquals(List.of("设一个速", "率，其余自", "动算出"), wrap("设一个速率，其余自动算出", 5));
    }

    @Test
    void mixedTextBreaksAroundChineseButNotInsideAWord() {
        assertEquals(List.of("用EBF", "冶炼"), wrap("用EBF冶炼", 4));
    }

    @Test
    void aWordWiderThanTheLineIsCut() {
        assertEquals(List.of("abcd", "efgh", "ij"), wrap("abcdefghij", 4));
    }

    @Test
    void newlinesAndEmptyTextKeepTheirLines() {
        assertEquals(List.of("a", "", "b"), wrap("a\n\nb", 10));
        assertEquals(List.of(""), wrap("", 10));
    }

    @Test
    void formattingCodesAreNotSplit() {
        assertEquals(List.of("ab", "\u00a7ac", "d"), wrap("ab\u00a7acd", 3));
    }
}
