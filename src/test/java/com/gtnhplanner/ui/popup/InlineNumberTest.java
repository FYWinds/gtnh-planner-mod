package com.gtnhplanner.ui.popup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** A number typed into its box: what goes in (the text events of the numpad and an IME as much as keys), and where. */
class InlineNumberTest {

    @Test
    void typingReplacesTheNumberItStartsWith() {
        final InlineNumber box = new InlineNumber();
        box.start("rate", 12, v -> {});
        assertEquals("12", box.text());
        box.type("7");
        box.type("5");
        assertEquals("75", box.text());
    }

    @Test
    void keepsOnlyWhatANumberIsWrittenWith() {
        final InlineNumber box = new InlineNumber();
        box.start("rate", 0, v -> {});
        box.type("1a2中.5k§x/3");
        assertEquals("12.5k/3", box.text());
    }

    @Test
    void textWithNothingForANumberLeavesTheSelection() {
        final InlineNumber box = new InlineNumber();
        box.start("rate", 12, v -> {});
        box.type("个");
        assertEquals("12", box.text());
        box.type("4");
        assertEquals("4", box.text());
    }

    @Test
    void fullWidthDigitsAndSignsCountAsThePlainOnes() {
        final InlineNumber box = new InlineNumber();
        box.start("rate", 0, v -> {});
        box.type("１２。５Ｋ＊２");
        assertEquals("12.5K*2", box.text());
    }

    @Test
    void goesInAtTheCaret() {
        final InlineNumber box = new InlineNumber();
        box.start("rate", 0, v -> {});
        box.setText("10");
        box.type("0");
        assertEquals("100", box.text());
    }

    @Test
    void typesNothingWhileNotEditing() {
        final InlineNumber box = new InlineNumber();
        box.type("5");
        assertEquals("", box.text());
        assertFalse(box.active());
    }

    /** An empty box sets 0 (ModularUI's maths needs the game's libraries, so it is not read here). */
    @Test
    void typesNothingOnceSet() {
        final double[] set = { -1 };
        final InlineNumber box = new InlineNumber();
        box.start("rate", 3, v -> set[0] = v);
        box.setText("");
        box.commit();
        assertEquals(0, set[0]);
        assertFalse(box.active());
        box.type("5");
        assertEquals("", box.text());
    }

    @Test
    void typesNothingOnceLeft() {
        final InlineNumber box = new InlineNumber();
        box.start("rate", 3, v -> {});
        box.cancel();
        assertFalse(box.active());
        box.type("5");
        assertEquals("3", box.text());
    }
}
