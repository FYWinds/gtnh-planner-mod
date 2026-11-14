package com.gtnhplanner;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether shown text holds what the player typed in one of the planner's own search boxes. In a pack with
 * NeverEnoughCharacters (the GTNH pinyin search, mod id nech) Chinese text also answers to its pinyin, as NEI's search
 * does there: "tyfdj" and "tongyong" both find 通用发电机. Without that mod it is a plain substring test.
 */
public final class TextMatch {

    /** nech's {@code Match.contains(String, CharSequence)}, or null without the mod. */
    private static final MethodHandle PINYIN = pinyin();

    private TextMatch() {}

    /** {@code text} and {@code typed} as the caller compares them (both lowered, where it ignores case). */
    public static boolean contains(final String text, final String typed) {
        if (text.contains(typed)) return true;
        if (PINYIN == null || typed.isEmpty()) return false;
        try {
            return (boolean) PINYIN.invoke(text, (CharSequence) typed);
        } catch (final Throwable t) {
            return false;
        }
    }

    private static MethodHandle pinyin() {
        try {
            return MethodHandles.publicLookup()
                .findStatic(
                    Class.forName("com.asdflj.nech.utils.Match"),
                    "contains",
                    MethodType.methodType(boolean.class, String.class, CharSequence.class));
        } catch (final ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
