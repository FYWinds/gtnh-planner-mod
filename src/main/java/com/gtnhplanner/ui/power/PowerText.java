package com.gtnhplanner.ui.power;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.StatCollector;

import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.ui.UiText;

/**
 * The power sources' words as the player reads them. The model keeps them English (the website's tables, saved plans,
 * the tests): names, descriptions, settings and their choices, readings and warnings. Fixed words are
 * {@link UiText#data}; a reading or warning built from numbers and names is matched against its English shape and
 * said again through a {@code power.reading_*} or {@code power.warning_*} key, its numbers carried over and its names
 * translated.
 */
public final class PowerText {

    private PowerText() {}

    public static String name(final PowerSource source) {
        return UiText.data(source.name());
    }

    public static String blurb(final PowerSource source) {
        return UiText.data(source.blurb());
    }

    public static String group(final PowerGroup group) {
        return UiText.data(group.title);
    }

    public static String label(final PowerSetting setting) {
        return UiText.data(setting.label());
    }

    /** A setting's caption: its label, with its unit for a number that has one. */
    public static String caption(final PowerSetting setting) {
        return setting instanceof final PowerSetting.Number n && n.unit() != null ? withUnit(setting.label(), n.unit())
            : label(setting);
    }

    /** An English label with a unit after it, "Rate (L/s)". */
    public static String withUnit(final String label, final String unit) {
        return UiText.tr("power.label_unit", UiText.data(label), UiText.data(unit));
    }

    public static String option(final PowerSetting.Option option) {
        return UiText.data(option.label());
    }

    /** A reading's label; a flow the game has no item or fluid for is a reading named for it. */
    public static String stat(final String label) {
        return UiText.data(label);
    }

    /** A reading's value: numbers and units as they are, the words around them translated. */
    public static String reading(final String value) {
        return said(READINGS, value);
    }

    public static String warning(final String text) {
        return said(WARNINGS, text);
    }

    /**
     * An English shape, the key that says it, and which of its groups are names (bit {@code i} for group
     * {@code i + 1}); the rest are numbers, carried over as written.
     */
    private record Shape(Pattern english, String key, int names) {

        Shape(final String english, final String key) {
            this(english, key, 0);
        }

        Shape(final String english, final String key, final int names) {
            this(Pattern.compile(english), key, names);
        }
    }

    /** The readings the sources build (power/sources), most specific first. */
    private static final List<Shape> READINGS = List.of(
        new Shape("(.+) median / (.+) max", "power.reading_median_max"),
        new Shape("(.+) a minute", "power.reading_a_minute"),
        new Shape("(.+) per hour", "power.reading_per_hour"),
        new Shape("(.+) L/t superheated", "power.reading_superheated"),
        new Shape("(.+) L/t ([A-Za-z].*)", "power.reading_steam_grade", 0b10),
        new Shape("(.+) s min, (.+) s avg", "power.reading_seconds_min_avg"),
        new Shape("(.+)/s avg, (.+)/s max", "power.reading_avg_max"),
        new Shape("(.+) L every (.+) ticks", "power.reading_every_ticks"),
        new Shape("(.+) real days", "power.reading_real_days"),
        new Shape("(.+) real day", "power.reading_real_day"),
        new Shape("Single fuel: (.+)", "power.reading_single_fuel"),
        new Shape("Dual fuel: (.+)", "power.reading_dual_fuel"),
        new Shape("(.+) L, kept", "power.reading_kept"),
        // A flow with no port, listed with the readings (PowerPorts.unported).
        new Shape("(.+/s) in", "power.reading_flow_in"),
        new Shape("(.+/s) out", "power.reading_flow_out"));

    /** The warnings the sources build (power/sources). */
    private static final List<Shape> WARNINGS = List.of(
        new Shape(
            "Rotor too weak for this fuel: it burns (.+) L/t for one rotor's (.+) EU/t\\.",
            "power.warning_rotor_weak_xl"),
        new Shape("Rotor too weak for this fuel: it burns (.+) L/t for (.+) EU/t\\.", "power.warning_rotor_weak"),
        new Shape("Flow is capped at (.+); the turbine will not take more\\.", "power.warning_flow_capped"),
        new Shape(
            "(.+) bursts: the hottest cell takes (.+) heat a second and holds (.+)\\. Pick a bigger cell\\.",
            "power.warning_cell_bursts",
            0b1),
        new Shape(
            "Core temp (.+)% multiplies the output by (.+)\\. The reactor melts at (.+)%\\.",
            "power.warning_core_temp"),
        new Shape("(.+) needs at least mark (.+)\\.", "power.warning_needs_mark", 0b1),
        new Shape("Intake is capped at (.+) L/s for this fluid\\.", "power.warning_intake_capped"),
        new Shape(
            "Burns at most (.+) item per (.+) ticks, so it runs at (.+) EU/t, not (.+)\\.",
            "power.warning_item_capped"),
        new Shape(
            "Burns at most (.+) L per (.+) ticks \\(the tank\\), so it runs at (.+) EU/t, not (.+)\\.",
            "power.warning_tank_capped"),
        new Shape(
            "Regular water calcifies this boiler: full (.+) L/s for (.+) hours of run time, then down to (.+) L/s by (.+) hours\\. Distilled water does not\\.",
            "power.warning_calcifies"),
        new Shape("(.+) is over (.+) EU/L and needs the oxygen boost\\.", "power.warning_needs_oxygen", 0b1),
        new Shape(
            "(.+) lasts under a tick at (.+) EU/t: each 1,000 L burns for no EU\\.",
            "power.warning_under_a_tick",
            0b1));

    /** The text in the player's language: its shape's key with its parts, else its own translation. */
    private static String said(final List<Shape> shapes, final String english) {
        if (english == null || english.isEmpty()) return english;
        // A text with a translation of its own (a fixed sentence, a word) needs no shape. Looked up uncached: most
        // readings are numbers that change with every setting.
        final String own = UiText.dataKey(english);
        if (StatCollector.canTranslate(own)) return StatCollector.translateToLocal(own);
        for (final Shape shape : shapes) {
            final Matcher m = shape.english()
                .matcher(english);
            if (!m.matches()) continue;
            final Object[] parts = new Object[m.groupCount()];
            for (int i = 0; i < parts.length; i++) {
                final String part = m.group(i + 1);
                parts[i] = (shape.names() & 1 << i) != 0 ? UiText.data(part) : part;
            }
            return UiText.tr(shape.key(), parts);
        }
        return english;
    }
}
