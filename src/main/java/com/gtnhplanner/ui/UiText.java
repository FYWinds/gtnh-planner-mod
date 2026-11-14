package com.gtnhplanner.ui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.StatCollector;

/** Localized text for the planner's fixed interface copy. */
public final class UiText {

    private UiText() {}

    /**
     * The text of {@code gtnhplanner.ui.<key>} in the game's language. Where the game has no languages loaded (the
     * headless tests) it is the English of the mod's own en_US.lang.
     */
    public static String tr(final String key) {
        final String full = "gtnhplanner.ui." + key;
        final String out = StatCollector.translateToLocal(full);
        return out.equals(full) ? English.TEXT.getOrDefault(full, full) : out;
    }

    /** {@link #tr} with {@link String#format} arguments. */
    public static String tr(final String key, final Object... args) {
        return String.format(tr(key), args);
    }

    private static final Map<String, String> DATA_KEYS = new ConcurrentHashMap<>();

    /**
     * Text that is data: a machine's or a power source's name, a setting's label or choice, a description. It stays
     * English where it is kept, compared and sent (the website's tables, saved plans, tests), and is translated only
     * where it is drawn. The key is made from the English text ({@link #dataKey}); a language without that key shows
     * the English.
     */
    public static String data(final String english) {
        if (english == null || english.isEmpty()) return english;
        final String key = DATA_KEYS.computeIfAbsent(english, UiText::dataKey);
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : english;
    }

    /**
     * The lang key of a data text: {@code gtnhplanner.data.} and the text in lower case, every run of other characters
     * than a-z and 0-9 an underscore, none at the ends. Over 48 characters it is cut there and ends in the text's
     * {@link String#hashCode} as 8 hex digits. {@code tools/eto/data_key.py} makes the same key.
     */
    public static String dataKey(final String english) {
        final StringBuilder b = new StringBuilder();
        boolean gap = false;
        for (int i = 0; i < english.length(); i++) {
            char c = english.charAt(i);
            if (c >= 'A' && c <= 'Z') c = (char) (c + 32);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                if (gap && b.length() > 0) b.append('_');
                gap = false;
                b.append(c);
            } else gap = true;
        }
        if (b.length() > 48) {
            b.setLength(48);
            b.append('_')
                .append(String.format("%08x", english.hashCode()));
        }
        return "gtnhplanner.data." + b;
    }

    /** The mod's en_US.lang, read from the jar the first time a key has no translation. */
    private static final class English {

        static final Map<String, String> TEXT = load();

        private static Map<String, String> load() {
            final Map<String, String> out = new HashMap<>();
            try (InputStream in = UiText.class.getResourceAsStream("/assets/gtnhplanner/lang/en_US.lang")) {
                if (in == null) return out;
                final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    final int eq = line.indexOf('=');
                    if (eq > 0 && line.charAt(0) != '#') out.put(line.substring(0, eq), line.substring(eq + 1));
                }
            } catch (final java.io.IOException e) {
                return out;
            }
            return out;
        }
    }
}
