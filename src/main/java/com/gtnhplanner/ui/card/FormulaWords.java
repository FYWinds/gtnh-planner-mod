package com.gtnhplanner.ui.card;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gtnhplanner.machines.FormulaLine;
import com.gtnhplanner.ui.UiText;

/**
 * A machine's worked formulas in the player's language, as the settings sheet draws them. The machines write them in
 * English (machines/: the website's words, checked by their tests); here each line's label, answer and words are
 * translated: a piece with a number in it as a whole sentence ({@code UiText.tr} with the numbers as arguments, so the
 * words fall in the language's order), a piece of words alone as data ({@link UiText#data}). Numbers, units and the
 * maths themselves stay as they are.
 */
final class FormulaWords {

    private FormulaWords() {}

    /**
     * One kind of worded piece: the English it matches whole, and the key of its sentence, the matches its arguments.
     */
    private record Rule(Pattern english, String key) {}

    private static final String N = "(\\S+)";

    private static final List<Rule> RULES = List.of(
        rule(N + " \\(" + N + " perfect\\)", "mach.formula.overclocks_perfect"),
        rule(N + " K over", "mach.formula.k_over"),
        rule(N + "% power", "mach.formula.power_share"),
        rule(N + " logs", "mach.formula.logs"),
        rule(N + "/h; electric " + N + " EU/s", "mach.formula.wear_electric"),
        rule(N + " uses/h", "mach.formula.uses_per_hour"),
        rule("1 tool, 1 use ÷ " + N, "mach.formula.tool_wear_one"),
        rule(N + " tools, 1 use ÷ " + N, "mach.formula.tool_wear"),
        rule("voids " + N + " L", "mach.formula.voids"),
        rule(N + " pts per " + N, "mach.formula.points_per"),
        rule(N + " farm", "mach.formula.farm"),
        rule(N + " farms", "mach.formula.farms"),
        rule(N + " manager", "mach.formula.manager"),
        rule(N + " managers", "mach.formula.managers"),
        rule("ceil\\(" + N + " planted / " + N + " each\\)", "mach.formula.machines_for_planted"),
        rule(N + " / " + N + " \\* " + N + " planted", "mach.formula.output_planted"),
        rule(N + " EU \\* " + N + " picked / " + N, "mach.formula.eu_picked"),
        rule(N + " a cycle", "mach.formula.per_cycle"),
        rule(N + " cycles", "mach.formula.cycles"),
        rule("x" + N + " outputs", "mach.formula.outputs_times"),
        rule("machine · " + N + " A", "mach.formula.machine_amps"));

    private static Rule rule(final String english, final String key) {
        return new Rule(Pattern.compile(english), key);
    }

    /**
     * A word of two letters or more that is no tier, unit or hatch name: a piece without one (a number, a unit, a
     * variable, "≥ HV") needs no translation.
     */
    private static final Pattern WORD = Pattern
        .compile("\\b(?!(?:ULV|LV|MV|HV|EV|IV|LuV|ZPM|UV|UHV|UEV|UIV|UMV|UXV|OpV|MAX|ME|EU|Sv)\\b)[A-Za-z]{2,}");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    /** The lines, translated. */
    static List<FormulaLine> of(final List<FormulaLine> lines) {
        final List<FormulaLine> out = new ArrayList<>(lines.size());
        for (final FormulaLine l : lines) {
            final List<FormulaLine.Term> math = new ArrayList<>(
                l.math()
                    .size());
            for (final FormulaLine.Term t : l.math())
                math.add(t.sup() ? t : new FormulaLine.Term(text(t.text()), t.knob(), false));
            out.add(new FormulaLine(UiText.data(l.label()), math, text(l.result()), l.tone()));
        }
        return out;
    }

    /** A piece of a line in the player's language, the spaces around it kept. */
    static String text(final String english) {
        final String core = english.strip();
        if (!WORD.matcher(core)
            .find()) return english;
        final int start = english.indexOf(core);
        final String before = english.substring(0, start), after = english.substring(start + core.length());
        for (final Rule r : RULES) {
            final Matcher m = r.english()
                .matcher(core);
            if (!m.matches()) continue;
            final Object[] args = new Object[m.groupCount()];
            for (int i = 0; i < args.length; i++) args[i] = m.group(i + 1);
            return before + UiText.tr(r.key(), args) + after;
        }
        // Words alone are data; words around numbers that no rule knows are the maths' own (min, ceil, log4, vs).
        return DIGIT.matcher(core)
            .find() ? english : before + UiText.data(core) + after;
    }
}
