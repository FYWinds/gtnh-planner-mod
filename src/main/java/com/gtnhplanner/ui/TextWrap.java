package com.gtnhplanner.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Text broken into lines no wider than a given width, for any language. A line may end at a space (which is dropped)
 * and between two characters when one of them is Chinese, Japanese or Korean, where words are not set apart by
 * spaces; never before closing punctuation or after opening punctuation, and never inside a formatting code. A
 * newline in the text always ends a line. A stretch with no place to break that is wider than the width is cut where
 * it stops fitting.
 */
public final class TextWrap {

    private static final String NO_BREAK_BEFORE = "，。、；：！？）】》〉」』”’…—·,.;:!?)]}%";
    private static final String NO_BREAK_AFTER = "（【《〈「『“‘([{";

    private TextWrap() {}

    /** {@code text} in lines at most {@code maxWidth} wide as {@code width} measures them; at least one line. */
    public static List<String> wrap(final String text, final double maxWidth, final ToDoubleFunction<String> width) {
        final List<String> lines = new ArrayList<>();
        for (final String paragraph : text.split("\n", -1)) wrapParagraph(paragraph, maxWidth, width, lines);
        return lines;
    }

    private static void wrapParagraph(final String text, final double maxWidth, final ToDoubleFunction<String> width,
        final List<String> lines) {
        final int first = lines.size();
        int start = skipSpaces(text, 0);
        while (start < text.length()) {
            if (width.applyAsDouble(text.substring(start)) <= maxWidth) {
                lines.add(text.substring(start));
                return;
            }
            // The last place to break that still fits; failing that, the last character that fits.
            int best = -1, cut = insideCode(text, start + 1) && start + 2 <= text.length() ? start + 2 : start + 1;
            for (int i = start + 1; i < text.length(); i++) {
                if (width.applyAsDouble(text.substring(start, i)) > maxWidth) break;
                if (!insideCode(text, i)) cut = i;
                if (text.charAt(i) == ' ' || breakable(text, i)) best = i;
            }
            final int end = best > start ? best : cut;
            lines.add(text.substring(start, end));
            start = skipSpaces(text, end);
        }
        if (lines.size() == first) lines.add("");
    }

    private static int skipSpaces(final String text, final int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) == ' ') i++;
        return i;
    }

    /** Whether a line may end before the character at {@code i} with no space there. */
    private static boolean breakable(final String text, final int i) {
        final char before = text.charAt(i - 1), after = text.charAt(i);
        if (insideCode(text, i)) return false;
        if (!wide(before) && !wide(after)) return false;
        return NO_BREAK_BEFORE.indexOf(after) < 0 && NO_BREAK_AFTER.indexOf(before) < 0;
    }

    /** Whether {@code i} falls between a formatting code's section sign and its letter. */
    private static boolean insideCode(final String text, final int i) {
        return i > 0 && text.charAt(i - 1) == '\u00a7';
    }

    /** A character of a script written without spaces between words, or its full-width punctuation. */
    static boolean wide(final char c) {
        return c >= 0x2E80 && c <= 0x9FFF || c >= 0xAC00 && c <= 0xD7AF
            || c >= 0xF900 && c <= 0xFAFF
            || c >= 0xFF00 && c <= 0xFFEF
            || c >= 0x3000 && c <= 0x303F;
    }
}
