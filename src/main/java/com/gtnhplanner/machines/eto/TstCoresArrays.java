package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.COIL;
import static com.gtnhplanner.machines.eto.TstControls.COIL_CONTROL;
import static com.gtnhplanner.machines.eto.TstControls.MAP_CONTROLS;
import static com.gtnhplanner.machines.eto.TstControls.VOLTAGES;
import static com.gtnhplanner.machines.eto.TstControls.choice;
import static com.gtnhplanner.machines.eto.TstControls.key;
import static com.gtnhplanner.machines.eto.TstCoresCards.count;
import static com.gtnhplanner.machines.eto.TstCoresCards.exact;
import static com.gtnhplanner.machines.eto.TstCoresCards.number;
import static com.gtnhplanner.machines.eto.TstCoresCards.putExact;
import static com.gtnhplanner.machines.eto.TstCoresCards.recipeEut;
import static com.gtnhplanner.machines.eto.TstCoresCards.recipeTicks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.gtnhplanner.machines.eto.TstCoresCards.Exact;
import com.gtnhplanner.machines.web.MachineTable.Behaviour;
import com.gtnhplanner.machines.web.MachineTable.Context;
import com.gtnhplanner.machines.web.RecipeRules;
import com.gtnhplanner.machines.web.Tiers;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's two arrays, which run the recipe map of the single-block GregTech machines put in them
 * (docs/design/tst-machines.md, "Cores, arrays and Thaumcraft machines"). Neither registers an NEI catalyst, so the
 * card
 * offers them on the maps {@code assets/gtnhplanner/machines/eto-tst-cores-arrays.json} lists
 * (machines/game/TstArrays);
 * the inserted machine's type is the card's map, its tier and count are settings. Source lines are the 0.8.1 tag's
 * {@code common/machine/TST_ProcessingArray.java} (PA), {@code common/machine/TST_BigBroArray.java} (BBA) and
 * {@code system/ProcessingArrayBackend/PAHelper.java}, and GregTech 5.09.54.265's. Pure: no game classes.
 */
final class TstCoresArrays {

    private TstCoresArrays() {}

    static final String PROCESSING_ARRAY = "TST Processing Array", MEGA_ARRAY = "MegaArray";

    // region Settings

    static final String TIER = "tstArrayMachineTier", MACHINES = "tstArrayMachines",
        PARALLELISM = "tstArrayParallelism", FRAME = "tstArrayFrame";

    /** The machine stack in the PA's controller slot, 1 to 64 (PA :237, :251); a full stack unless set. */
    static final Web.Control PA_MACHINES_CONTROL = count(MACHINES, "Inserted Machines", 1, 64, 64);

    /** BBA :1104-1112 at Parallelism Casing Mark 4, all four add-ons: what insertion takes at most (:496). */
    static final int MEGA_MACHINE_CAP = machineCap(5);

    /** The machines the screwdriver took from the input buses (BBA :1129-1170); a stack unless set. */
    static final Web.Control MEGA_MACHINES_CONTROL = count(MACHINES, "Inserted Machines", 1, MEGA_MACHINE_CAP, 64);

    /**
     * The lowest Parallelism Casing among the four add-ons (BBA :465-492): ParallelismCasing0 to 4 are metas 3 to 7 of
     * one block, tier meta - 2 (getParallelismCasingTier :983-988), so the tier is the position + 1.
     */
    static final Web.Control PARALLELISM_CONTROL = choice(
        PARALLELISM,
        "Parallelism Casing",
        "Parallelism Casing Mark 0",
        "Parallelism Casing Mark 1",
        "Parallelism Casing Mark 2",
        "Parallelism Casing Mark 3",
        "Parallelism Casing Mark 4");

    /**
     * The frame boxes of the core and add-ons, FRAMES (BBA :839-869) in order, tier the position + 1 (getFrameTier
     * :973-981). Quantum (6) takes every machine tier, so it is the default: the others only refuse.
     */
    static final Web.Control FRAME_CONTROL = choice(
        FRAME,
        "Frame Box",
        "Arcanite Frame Box",
        "Zeron-100 Frame Box",
        "Pikyonium 64B Frame Box",
        "Botmium Frame Box",
        "Abyssal Alloy Frame Box",
        "Quantum Frame Box");

    static {
        FRAME_CONTROL.defaultKey = key("Quantum Frame Box");
    }

    /** The PA has no coil: a map's own coil setting (the furnace's) is hidden with the maps' others. */
    private static final String[] PA_HIDDEN;

    static {
        PA_HIDDEN = Arrays.copyOf(MAP_CONTROLS, MAP_CONTROLS.length + 1);
        PA_HIDDEN[MAP_CONTROLS.length] = COIL;
    }

    // endregion

    /** The gate messages, English data. */
    static final String NO_TIER_GATE = "This machine does not come in this tier.",
        PA_VOLTAGE_GATE = "This recipe draws more EU/t than the inserted machines' voltage.",
        MEGA_TIER_GATE = "This recipe's voltage tier is above the inserted machines' tier.",
        FRAME_GATE = "These frame boxes do not take machines of this tier.";

    static void register(final Map<String, Behaviour> machines) {
        // TST_ProcessingArray: n machines of the stack are n parallels at the machine's voltage, amps n, no amperage
        // overclock (setProcessingLogicPower :196-201), the GT default 2/4 overclock and no modifiers.
        machines.put(
            PROCESSING_ARRAY,
            exact(TstCoresArrays::processingArray).handlerNumbers()
                .unlimitedTierSkip()
                .controls(controls(COMMON_TIERS, 1, false))
                .controlsFor(recipe -> controlsFor(recipe, false))
                .hidesControls(PA_HIDDEN));
        // TST_BigBroArray in processor mode: the hatches' power as GT gives it (MTEMultiBlockBase
        // .setProcessingLogicPower :1171-1176), n x 2^tier parallels, 0.66^tier duration and 0.9^coil EU (:498-499).
        putExact(
            machines,
            MEGA_ARRAY,
            exact(TstCoresArrays::megaArray).handlerNumbers()
                .unlimitedTierSkip()
                .controls(controls(MEGA_ALL_TIERS, 1, true))
                .controlsFor(recipe -> controlsFor(recipe, true)));
    }

    // region The machines in the arrays

    /** GTValues.V: the voltage of tier 1 (LV) to 14 (MAX). */
    static long voltage(final int tier) {
        return (long) Tiers.MAX_EUT[tier];
    }

    /** The tiers a machine type comes in, a bit per tier (1 LV to 14 MAX). */
    private static long tiers(final int lowest, final int highest) {
        return (1L << highest + 1) - (1L << lowest);
    }

    private static boolean has(final long tiers, final int tier) {
        return tier > 0 && tier < 63 && (tiers >> tier & 1) != 0;
    }

    private static int lowest(final long tiers) {
        return Long.numberOfTrailingZeros(tiers);
    }

    private static int highest(final long tiers) {
        return 63 - Long.numberOfLeadingZeros(tiers);
    }

    /** Most single blocks come LV to UMV (LoaderMetaTileEntities' builders, setTier 1 to 12). */
    private static final long COMMON_TIERS = tiers(1, 12);

    /** The Circuit Assembler also comes in UXV and MAX (ItemList :2107-2114, :2466-2471). */
    private static final long MEGA_ALL_TIERS = tiers(1, 14);

    /**
     * The other tier sets of the machines the PA takes, by their map's name: unpackager and printer LV to UV, GT++'s
     * Simple Washer LV to UV (GregtechSimpleWasher), Dehydrator MV to ZPM (GregtechDehydrator), Cold Trap and Reactor
     * Processing Unit IV and ZPM (GregtechLFTR).
     */
    private static final Map<String, Long> PA_TIERS = Map.of(
        "Circuit Assembler",
        MEGA_ALL_TIERS,
        "Unpackager",
        tiers(1, 8),
        "Printer",
        tiers(1, 8),
        "Simple Dust Washer",
        tiers(1, 8),
        "Dehydrator",
        tiers(2, 7),
        "Cold Trap",
        tiers(5, 5) | tiers(7, 7),
        "Reactor Processing Unit",
        tiers(5, 5) | tiers(7, 7));

    /** The BBA's machine types (PROCESSING_MACHINE_LIST :156-171) all come LV to UMV, the Circuit Assembler to MAX. */
    private static long tiersOn(@Nullable final String map, final boolean mega) {
        if ("Circuit Assembler".equals(map)) return MEGA_ALL_TIERS;
        return mega || map == null ? COMMON_TIERS : PA_TIERS.getOrDefault(map, COMMON_TIERS);
    }

    /**
     * The PA's voltage, PAHelper.getVoltageFromMTE (:77-80): V[tier] x the machine's amps, which its map gives
     * (RecipeMaps :167, the thermal centrifuge's 2 A) unless the machine sets its own (the arc furnace's 3 A,
     * LoaderMetaTileEntities :7252-7417); 1 A otherwise. The machine's EU/t multiplier is not read: the PA runs the
     * recipe's own EU/t.
     */
    static int paAmperage(@Nullable final String map) {
        if ("Thermal Centrifuge".equals(map)) return 2;
        if ("Arc Furnace".equals(map)) return 3;
        return 1;
    }

    /** BBA validateRecipe (:686-689): mEUt <= 8 x 4^machineTier. */
    private static boolean megaRuns(final long eut, final int tier) {
        return eut <= 8 * Math.pow(4, tier);
    }

    /** The machine tier the card picked: the ladder's position + 1. */
    static int machineTier(final Context c) {
        return c.tier(TIER) + 1;
    }

    // endregion

    // region TST Processing Array

    /**
     * One check of the PA: no machine of the tier, or a recipe over the machines' voltage (validateRecipe :186-191),
     * is refused; otherwise ProcessingLogic at voltage V[tier] x A, amperage n, no amperage overclock, n parallels
     * (:193, :197-201). The CompressionTierKey refusal (:187-189) is not modelled: the card's recipe has no such data.
     */
    @Nullable
    static Exact processingArray(final Context c) {
        if (c.recipe == null) return null;
        final int tier = machineTier(c);
        if (!has(tiersOn(c.recipeMap, false), tier)) return Exact.refused(NO_TIER_GATE);
        final long voltage = voltage(tier) * paAmperage(c.recipeMap);
        final long eut = recipeEut(c);
        if (eut > voltage) return Exact.refused(PA_VOLTAGE_GATE);
        final int machines = Math.max(1, Math.min(64, number(c, MACHINES)));
        final TstCoresGt.Run r = TstCoresGt.process(
            new TstCoresGt.Setup().voltage(voltage)
                .amperage(machines)
                .amperageOC(false)
                .maxParallel(machines),
            eut,
            recipeTicks(c));
        return r == null ? Exact.refused(TstMachinesCores.POWER_GATE) : Exact.of(r.parallels(), r.duration(), r.eut());
    }

    // endregion

    // region MegaArray

    /**
     * BBA's casingMultiplier (:495): the parallelism tier, + 6 from Mark 3 (tier 4) up. It is set after the max
     * parallel is worked out (:494), so the max reads the previous check's; on a formed machine that is the same.
     */
    static int casingMultiplier(final int parallelism) {
        return parallelism > 3 ? parallelism + 6 : parallelism;
    }

    /**
     * calculateMaxParallelismByAddonTier (:1104-1112) with four add-ons, the only build that forms: each add-on is
     * checked into the shared error list (:468-486) and GregTech forms a machine only when that list is empty
     * (MTEMultiBlockBase.checkStructure :583-584, StructureChecker :34-42).
     */
    static long maxParallelism(final int parallelism) {
        final int addons = 4;
        if (parallelism >= 5) return ((2147483647L << casingMultiplier(parallelism)) / 5) * (1 + addons);
        return (64L << (parallelism * 2 + (parallelism > 3 ? 6 : 0))) * (1 + addons);
    }

    /** machineCountForMaxParallelism (:496): the machines one screwdriver click takes at most. */
    static int machineCap(final int parallelism) {
        return (int) (maxParallelism(parallelism) >> casingMultiplier(parallelism));
    }

    /**
     * The max parallel supplier (:691-693): (int) min(n x 2^tier, maxParallelism), as doubles; past Integer.MAX_VALUE
     * the cast holds it there.
     */
    static int megaParallels(final int machines, final int parallelism) {
        return (int) Math.min(machines * Math.pow(2, parallelism), (double) maxParallelism(parallelism));
    }

    /**
     * One check of the MegaArray: a machine tier the frames refuse at insertion (:1127, :1151) or one the type lacks
     * is refused, as is a recipe over the machines' tier (:686-689); otherwise ProcessingLogic on the hatches with the
     * structure's speed bonus and EU modifier as floats (:498-499) and the GT default 2/4 overclock.
     */
    @Nullable
    static Exact megaArray(final Context c) {
        if (c.recipe == null) return null;
        final int tier = machineTier(c);
        if (!has(tiersOn(c.recipeMap, true), tier)) return Exact.refused(NO_TIER_GATE);
        final int frame = c.tier(FRAME) + 1;
        if (frame < 6 && tier > frame + 4) return Exact.refused(FRAME_GATE);
        final long eut = recipeEut(c);
        if (!megaRuns(eut, tier)) return Exact.refused(MEGA_TIER_GATE);
        final int parallelism = c.tier(PARALLELISM) + 1;
        final int machines = Math.max(1, Math.min(MEGA_MACHINE_CAP, number(c, MACHINES)));
        final TstCoresGt.Run r = TstCoresGt.process(
            new TstCoresGt.Setup().voltage(TstMachines.totalEu(c))
                .maxParallel(megaParallels(machines, parallelism))
                .euModifier((float) Math.pow(0.9, Math.max(0, TstMachines.coil(c))))
                .speedBoost((float) Math.pow(0.66, parallelism)),
            eut,
            recipeTicks(c));
        return r == null ? Exact.refused(TstMachinesCores.POWER_GATE) : Exact.of(r.parallels(), r.duration(), r.eut());
    }

    // endregion

    // region The settings a map shows

    /** Built once per map and default tier: every card asks for them on every computation. */
    private static final Map<String, List<Web.Control>[]> PA_BY_MAP = new ConcurrentHashMap<>(),
        MEGA_BY_MAP = new ConcurrentHashMap<>();

    /**
     * The settings on the recipe's map: its machine ladder, LV to the type's top, its lowest tier the minimum and the
     * lowest that runs the recipe the default, as the plan button picks a single block.
     */
    private static List<Web.Control> controlsFor(final Web.Recipe recipe, final boolean mega) {
        final String map = RecipeRules.recipeMapName(recipe);
        final long tiers = tiersOn(map, mega);
        final long eut = (long) Math.abs(recipe.eut);
        int tier = highest(tiers);
        for (int t = lowest(tiers); t <= highest(tiers); t++)
            if (has(tiers, t) && (mega ? megaRuns(eut, t) : eut <= voltage(t) * paAmperage(map))) {
                tier = t;
                break;
            }
        final List<Web.Control>[] byTier = (mega ? MEGA_BY_MAP : PA_BY_MAP)
            .computeIfAbsent(String.valueOf(map), k -> newLists());
        List<Web.Control> out = byTier[tier];
        if (out == null) byTier[tier] = out = controls(tiers, tier, mega);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Web.Control>[] newLists() {
        return (List<Web.Control>[]) new List<?>[VOLTAGES.size()];
    }

    private static List<Web.Control> controls(final long tiers, final int defaultTier, final boolean mega) {
        final Web.Control ladder = choice(TIER, "Machine Tier", VOLTAGES.subList(1, highest(tiers) + 1), null);
        ladder.minimumKey = key(VOLTAGES.get(lowest(tiers)));
        ladder.defaultKey = key(VOLTAGES.get(defaultTier));
        return mega ? List.of(ladder, MEGA_MACHINES_CONTROL, PARALLELISM_CONTROL, COIL_CONTROL, FRAME_CONTROL)
            : List.of(ladder, PA_MACHINES_CONTROL);
    }

    // endregion
}
