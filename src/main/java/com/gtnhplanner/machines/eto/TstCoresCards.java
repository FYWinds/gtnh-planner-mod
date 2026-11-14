package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.MAP_CONTROLS;
import static com.gtnhplanner.machines.web.MachineTable.machine;
import static com.gtnhplanner.machines.web.MachineTable.none;

import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.gtnhplanner.machines.web.MachineTable;
import com.gtnhplanner.machines.web.MachineTable.Behaviour;
import com.gtnhplanner.machines.web.MachineTable.Coefficient;
import com.gtnhplanner.machines.web.MachineTable.Context;

/**
 * How the cores, arrays and Thaumcraft machines (docs/design/tst-machines.md, "Cores, arrays and Thaumcraft machines")
 * reach a card: most work a whole run out the game's way ({@link TstCoresGt}, {@link TstCoresExecution}) and hand the
 * card its parallels, ticks and EU/t as they are ({@link #exact}); the rest are plain entries whose parallels GT's
 * ParallelHelper pays for ({@link #put}). Pure: no game classes.
 */
final class TstCoresCards {

    private TstCoresCards() {}

    /**
     * One run as the game makes it: the recipes it holds at once, the ticks it takes (a fraction for an average), the
     * EU/t it draws over them, and whether that EU comes from the wireless network (no hatches pay it); or why the game
     * refuses it ({@code refusal}, English data).
     */
    record Exact(double parallels, double ticks, double euT, boolean network, @Nullable String refusal) {

        static Exact of(final double parallels, final double ticks, final double euT) {
            return new Exact(parallels, ticks, euT, false, null);
        }

        static Exact network(final double parallels, final double ticks, final double euT) {
            return new Exact(parallels, ticks, euT, true, null);
        }

        static Exact refused(final String why) {
            return new Exact(1, 1, 0, false, why);
        }

        boolean runs() {
            return refusal == null;
        }

        /** The speed that turns the recipe's ticks into the run's. */
        double speed(final Context c) {
            return !runs() || c.recipe == null || !(ticks > 0) ? 1 : c.recipe.durationTicks / ticks;
        }

        /** The factor that turns the recipe's EU/t into the run's EU/t per parallel. */
        double power(final Context c) {
            final double eut = c.recipe != null ? Math.abs(c.recipe.eut) : 0;
            return !runs() || !(eut > 0) ? 1 : euT / parallels / eut;
        }
    }

    /**
     * An entry whose numbers are {@code run}'s, as they are: no overclock of the card's own (the run has its own), a
     * speed and EU factor that turn the recipe's ticks and EU/t into the run's, its parallels unpaid (the run paid
     * them), the duration kept to the millionth of a tick (an average need not be whole), the run's refusal as the
     * recipe gate, and a network run on fixed ticks with no hatches. A null run (no recipe yet) changes nothing.
     */
    static Behaviour exact(final Function<Context, Exact> run) {
        return machine().overclock(none())
            .speed(c -> {
                final Exact e = run.apply(c);
                return e == null ? 1 : e.speed(c);
            })
            .power(c -> {
                final Exact e = run.apply(c);
                return e == null ? 1 : e.power(c);
            })
            .parallels(c -> {
                final Exact e = run.apply(c);
                return e == null || !e.runs() ? 1 : Math.min(e.parallels(), Integer.MAX_VALUE);
            })
            .fixedTicks(c -> {
                final Exact e = run.apply(c);
                return e != null && e.runs() && e.network() ? e.ticks() : 0;
            })
            .quantiseDuration(d -> Math.round(d * 1e6) / 1e6)
            .recipeGate(c -> {
                final Exact e = run.apply(c);
                return e != null ? e.refusal() : null;
            });
    }

    /** An exact entry, the maps' own settings hidden. */
    static void putExact(final Map<String, Behaviour> machines, final String name, final Behaviour behaviour) {
        machines.put(name, behaviour.hidesControls(MAP_CONTROLS));
    }

    /**
     * A plain entry as TstMachines.put makes it: its structural parallels paid for as GT's ParallelHelper does
     * ({@link TstMachines#paid}), except on a wireless run (fixed ticks), whose parallels stand; the maps' own settings
     * hidden.
     */
    static void put(final Map<String, Behaviour> machines, final String name, final Behaviour behaviour) {
        final Coefficient structure = behaviour.parallels, power = behaviour.power, fixed = behaviour.fixedTicks;
        behaviour.parallels(c -> {
            final double structural = MachineTable.resolve(structure, c, 1);
            if (fixed != null && MachineTable.resolve(fixed, c, 0) > 0) return Math.min(structural, Integer.MAX_VALUE);
            return TstMachines.paid(c, structural, MachineTable.resolve(power, c, 1));
        });
        machines.put(name, behaviour.hidesControls(MAP_CONTROLS));
    }

    /** A count setting's number, 0 to Integer.MAX_VALUE. */
    static int number(final Context c, final String id) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, c.value(id)));
    }

    /** A count setting with its own default. */
    static com.gtnhplanner.machines.web.Web.Control count(final String id, final String label, final int min,
        @Nullable final Integer max, final int defaultValue) {
        final com.gtnhplanner.machines.web.Web.Control control = TstControls.count(id, label, min, max);
        control.defaultKey = String.valueOf(defaultValue);
        return control;
    }

    /** The recipe's EU/t and ticks as the game's ints and longs. */
    static long recipeEut(final Context c) {
        return c.recipe != null ? (long) Math.abs(c.recipe.eut) : 0;
    }

    static int recipeTicks(final Context c) {
        return c.recipe != null ? (int) Math.max(0, Math.min(Integer.MAX_VALUE, c.recipe.durationTicks)) : 0;
    }
}
