package com.gtnhplanner.machines.eto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.gtnhplanner.machines.BacterialVat;
import com.gtnhplanner.machines.FormulaLine;
import com.gtnhplanner.machines.FormulaLine.Tone;
import com.gtnhplanner.machines.web.MachineTable.Context;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's Biosphere III (TST_BiosphereIII, MTE 19025) as a bacterial vat: its machine modes 0
 * (Bacterial Vat) and 1 (Bacterial Vat Automation) on bartworks' map. What a petri dish stack gives in parallels, the
 * speed, the multiplier on the first fluid in and out, and the parallels void protection lets the one output hatch
 * take.
 * Its brewing and fermenting modes are TstMachines' entry alone. The card keeps the bartworks vat's settings
 * (BacterialVat, under the website's keys: glass, output hatch, fill, void protection, radio hatch); the mode and the
 * dish count are this machine's own. Line numbers are TST 0.8.1's common/machine/TST_BiosphereIII.java. Pure: no game
 * classes.
 */
public final class TstCoresVat {

    private TstCoresVat() {}

    /** The controller, as a card's machine item names it (WebCards.machineStack, GameIds.itemId). */
    public static final String CONTROLLER = "gregtech:gt.blockmachines@19025";

    /**
     * Recipe metadata: a vat recipe's glass tier (GTRecipeConstants.GLASS; GTProvider reads 3 where it has none), as
     * WebCards.recipe writes it.
     */
    public static final String RECIPE_GLASS = "glassTier";

    /** bartworks' bacterialVatRecipes, by the name the data gives the map (a formula's Context.recipeMap). */
    static final String MAP = "Bacterial Vat";

    public static final String MODE = "tstBioMode", DISHES = "tstBioDishes";

    /** machineMode 0 and 1 (getRecipeMap :211-217, getMachineModeName :244-263). */
    public static final int NORMAL = 0, AUTOMATION = 1;

    /**
     * The petri dishes the controller slot holds: one stack. bartworks' ItemLabParts never sets a max stack size, so
     * Minecraft's 64.
     */
    public static final int MAX_DISHES = 64;

    /** chainAllGlasses (:137): HV 3 to UXV 13, as TstControls.GLASS_CONTROL. */
    public static final int MAX_GLASS = 13;

    /** Configuration.Multiblocks.bioVatMaxParallelBonus, @Config.Ignore: the pack cannot change it. */
    private static final int MAX_BONUS = 1000;

    static final Web.Control MODE_CONTROL = TstControls
        .choice(MODE, "Mode", "Bacterial Vat Mode", "Bacterial Vat Automation Mode");

    static final Web.Control DISHES_CONTROL = TstControls.count(DISHES, "Petri Dishes", 1, MAX_DISHES);

    /** A vat card's own settings; the glass is the vat's (BacterialVat.GLASS), not TstControls.GLASS_CONTROL. */
    static final List<Web.Control> CONTROLS = List.of(MODE_CONTROL, DISHES_CONTROL);

    // region The card's context

    /** Whether the card runs the bacterial vat map. */
    static boolean on(final Context c) {
        return MAP.equals(c.recipeMap);
    }

    /** The card's machine mode: 0 unless it picks the automation mode. */
    public static int mode(final Context c) {
        return c.tier(MODE) == AUTOMATION ? AUTOMATION : NORMAL;
    }

    /** The petri dishes in the controller, 1 to a stack. */
    public static int dishes(final Context c) {
        return (int) Math.max(1, Math.min(MAX_DISHES, c.value(DISHES)));
    }

    /** The recipe's glass tier, from WebCards' metadata; HV where it says none (GTProvider's default). */
    static int recipeGlass(final Context c) {
        final JsonElement glass = c.recipe != null && c.recipe.metadata != null ? c.recipe.metadata.get(RECIPE_GLASS)
            : null;
        return glass != null && glass.isJsonPrimitive() ? glass.getAsInt() : 3;
    }

    /** The first fluid among a recipe's resources, its litres; 0 when it has none. */
    static long firstFluid(@Nullable final List<Web.Resource> resources) {
        if (resources != null)
            for (final Web.Resource r : resources) if ("fluid".equals(r.kind)) return (long) r.amount;
        return 0;
    }

    /** The run as the card's context reads it: mode, dishes, the vat's settings and the recipe's first fluid out. */
    public static Run run(final Context c, final BacterialVat.Needs needs,
        final List<BacterialVat.Material> materials) {
        return run(
            mode(c),
            dishes(c),
            c.settings != null ? c.settings : Map.of(),
            needs,
            c.recipe != null ? firstFluid(c.recipe.outputs) : 0,
            materials);
    }

    /** The speed bonus the entry reads. */
    static float speedBonus(final Context c) {
        return speedBonus(mode(c));
    }

    /** The structural parallels the entry reads: the dishes' parallels, as many as the output hatch takes. */
    static double parallels(final Context c) {
        return run(c, new BacterialVat.Needs(recipeGlass(c), 0, false), List.of()).parallels();
    }

    // endregion

    // region The game's numbers

    /** getSpeedBonus (:275-282), a duration multiplier: 0.5 in mode 0, 1 in mode 1. */
    public static float speedBonus(final int mode) {
        return mode == NORMAL ? 0.5f : 1;
    }

    /** getMaxParallelRecipes (:266-273): 4 parallels a petri dish in mode 0, 1 in mode 1. */
    public static int structuralParallels(final int mode, final int dishes) {
        return mode == NORMAL ? dishes * 4 : dishes;
    }

    /**
     * recipeAfterEfficiencyCalculation in mode 1 (:363-364): (int) (((glass - recipe glass) * 600 + 1601.0) / 1000 *
     * bioVatMaxParallelBonus), in the game's doubles (1601 + 600 a tier over, exactly, from the recipe's tier up).
     */
    public static int automationMultiplier(final int glass, final int recipeGlass) {
        return (int) (((glass - recipeGlass) * 600 + 1601.0) / 1000 * MAX_BONUS);
    }

    /**
     * The multiplier on the first fluid in and out (:357-378), the inputs never short: mode 0 reads the output hatch's
     * fill (getExpectedMultiplier :380-393, calcMod + 1 with Math.pow: the same doubles as bartworks' at every litre of
     * every hatch the card offers, so BacterialVat.multiplierAt), mode 1 the glass; at least 1 (:373).
     */
    public static int multiplier(final int mode, final BacterialVat.Setup setup, final int recipeGlass) {
        final int m = mode == NORMAL ? BacterialVat.multiplierAt(
            BacterialVat.stored(setup),
            setup.hatch()
                .capacity())
            : automationMultiplier(setup.glass(), recipeGlass);
        return Math.max(1, m);
    }

    /** The litres the output hatch has room for: unbounded for the ME output hatch, which keeps nothing. */
    public static long room(final BacterialVat.Setup setup) {
        final long capacity = setup.hatch()
            .capacity();
        return capacity > 0 ? capacity - BacterialVat.stored(setup) : Long.MAX_VALUE;
    }

    /**
     * The parallels whose output the hatch takes (void protection: VoidProtectionHelper.build :207-215,
     * FluidEjectionHelper.ejectFluids :153-162): the room over one parallel's scaled output, rounded down; unbounded on
     * the ME output hatch (canDumpFluidToME) or without a fluid output.
     */
    public static long fit(final BacterialVat.Setup setup, final int multiplier, final long fluidOut) {
        final long room = room(setup);
        if (room == Long.MAX_VALUE || fluidOut <= 0) return Long.MAX_VALUE;
        return Math.max(0, room) / ((long) multiplier * fluidOut);
    }

    /**
     * The hatch a card starts with: in mode 0 the smallest that holds every parallel's x1001 run at half full (the
     * bartworks vat's rule, times the parallels); in mode 1, whose multiplier never reads the hatch, the ME output
     * hatch,
     * which never runs out of room.
     */
    public static BacterialVat.Hatch defaultHatch(final int mode, final int structural, final long fluidOut) {
        if (mode == AUTOMATION) return BacterialVat.hatch("me");
        return BacterialVat.defaultHatch((double) structural * (MAX_BONUS + 1) * fluidOut);
    }

    /**
     * One check: the mode, the parallels the dishes give, the card's vat settings, the fluids' multiplier and the
     * parallels the hatch takes.
     */
    public record Run(int mode, int structural, BacterialVat.Setup setup, int multiplier, long fit) {

        /**
         * The parallels the machine offers ParallelHelper (before the power's cut): void protection lowers them to
         * what fits, 0 when not one does; voiding runs them all.
         */
        public int parallels() {
            return setup.voidExcess() ? structural : (int) Math.min(structural, fit);
        }

        /** Void protection holds the machine: not one parallel fits (CheckRecipeResultRegistry.FLUID_OUTPUT_FULL). */
        public boolean full() {
            return !setup.voidExcess() && fit < 1;
        }
    }

    /** The run of {@code dishes} in {@code mode} on the card's vat settings, for a recipe's first fluid output. */
    public static Run run(final int mode, final int dishes, final Map<String, ?> settings,
        final BacterialVat.Needs needs, final long fluidOut, final List<BacterialVat.Material> materials) {
        final int structural = structuralParallels(mode, dishes);
        final BacterialVat.Setup setup = BacterialVat
            .setup(settings, needs, materials, defaultHatch(mode, structural, fluidOut));
        final int multiplier = multiplier(mode, setup, needs.glass());
        return new Run(mode, structural, setup, multiplier, fit(setup, multiplier, fluidOut));
    }

    /**
     * The first fluid output's multiplier per parallel when {@code parallels} run: the fluids' multiplier, less what a
     * voiding machine's hatch cannot take.
     */
    public static double outputMultiplier(final Run run, final long fluidOut, final double parallels) {
        if (fluidOut <= 0 || !(parallels > 0)) return run.multiplier();
        final double made = parallels * run.multiplier() * fluidOut;
        return Math.min(made, room(run.setup())) / (parallels * fluidOut);
    }

    /** The gate message, English data. */
    static final String FULL_GATE = "Not one parallel fits in the output hatch: void protection holds the machine.";

    /**
     * Why the recipe cannot run: the vat's own glass and sievert gates (validateRecipe :306-326, as bartworks'; no
     * output litres, so not its fit), then an output hatch with no room for one parallel.
     */
    @Nullable
    public static String gate(final Run run, final BacterialVat.Needs needs) {
        final String vat = BacterialVat.gate(needs, run.setup(), 0);
        if (vat != null) return vat;
        return run.full() ? FULL_GATE : null;
    }

    // endregion

    // region The card's working

    /**
     * The vat's lines on a Biosphere III card: the fluids' multiplier, a run's fluids for {@code parallels}, the
     * hatch's
     * room, the rate kept, and the glass and radiation; the power and time lines are the machine maths' own.
     */
    public static List<FormulaLine> formulas(final Run run, final BacterialVat.Needs needs, final long fluidIn,
        final long fluidOut, final double parallels, final double runTicks) {
        final List<FormulaLine> lines = new ArrayList<>();
        final BacterialVat.Setup setup = run.setup();
        final int m = run.multiplier();
        if (run.mode() == NORMAL) lines.add(BacterialVat.fillLine(setup));
        else lines.add(
            FormulaLine.of(
                "multiplier",
                FormulaLine.number(m) + "x",
                "1601+600·(",
                FormulaLine.knob(setup.glass(), BacterialVat.GLASS),
                "−" + needs.glass() + ")"));
        final String times = "·" + FormulaLine.number(m) + "·" + FormulaLine.number(parallels);
        if (fluidIn > 0) lines.add(
            FormulaLine.of(
                "in",
                FormulaLine.number((double) fluidIn * m * parallels) + " L",
                FormulaLine.number(fluidIn) + " L" + times));
        final long capacity = setup.hatch()
            .capacity();
        final double made = (double) fluidOut * m * parallels;
        final double kept = Math.min(made, room(setup));
        if (fluidOut > 0) {
            lines.add(
                FormulaLine.of("out", FormulaLine.number(made) + " L", FormulaLine.number(fluidOut) + " L" + times));
            if (capacity > 0 && !setup.voidExcess()) lines.add(
                FormulaLine.of(
                    "parallels that fit",
                    FormulaLine.number(run.fit()),
                    run.fit() >= run.structural() ? Tone.GOOD : Tone.BAD,
                    "⌊(",
                    FormulaLine.knob(FormulaLine.number(capacity), BacterialVat.OUTPUT_HATCH),
                    "−" + FormulaLine.number(BacterialVat.stored(setup))
                        + ") ÷ "
                        + FormulaLine.number((double) fluidOut * m)
                        + "⌋"));
            else if (capacity > 0) lines.add(
                FormulaLine.of(
                    "room",
                    made <= kept ? "fits" : "voids " + FormulaLine.number(made - kept) + " L",
                    made <= kept ? Tone.GOOD : Tone.BAD,
                    FormulaLine.number(made) + " ≤ ",
                    FormulaLine.knob(FormulaLine.number(capacity), BacterialVat.OUTPUT_HATCH),
                    "−" + FormulaLine.number(BacterialVat.stored(setup))));
            final double seconds = runTicks / 20.0;
            lines.add(
                FormulaLine.of(
                    "rate",
                    FormulaLine.number(seconds > 0 ? kept / seconds : 0) + " L/s",
                    FormulaLine.number(kept) + " L ÷ " + FormulaLine.number(seconds) + "s"));
        }
        lines.addAll(BacterialVat.requirements(needs, setup));
        return lines;
    }

    // endregion
}
