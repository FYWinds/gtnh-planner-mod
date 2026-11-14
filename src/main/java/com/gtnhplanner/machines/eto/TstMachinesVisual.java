package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.MAP_CONTROLS;
import static com.gtnhplanner.machines.eto.TstControls.choice;
import static com.gtnhplanner.machines.eto.TstControls.count;
import static com.gtnhplanner.machines.web.MachineTable.machine;
import static com.gtnhplanner.machines.web.MachineTable.none;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gtnhplanner.machines.web.MachineTable.Behaviour;
import com.gtnhplanner.machines.web.MachineTable.Context;
import com.gtnhplanner.machines.web.Tiers;
import com.gtnhplanner.machines.web.Upkeep;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's machines with their own recipe pages (docs/design/tst-machines.md, "Machines with their
 * own recipe pages"): maps whose NEI recipes show what a machine does while its controller works out each run itself.
 * Every entry makes a card's time, EU/t, parallels and per-port amounts come out as the controller's run, from the
 * recipe the card was made from and the card's settings. The maps and machines are in
 * {@code assets/gtnhplanner/machines/eto-tst-visual.json}; the Eco-Sphere Simulator is {@link TstVisualEcoSphere}.
 * Line numbers are TST 0.8.1's. Pure: no game classes.
 */
public final class TstMachinesVisual {

    private TstMachinesVisual() {}

    // region The pack's config (config/TwistSpaceTechnology.cfg; every value here is also TST's default)

    /** netherinterface: BasicEnergyCost, CycleTime, GenerateStackEveryProcessing, MaxParallel. */
    static final long NETHER_EU = 7680;
    static final int NETHER_TICKS = 1200, NETHER_STACKS = 3, NETHER_MAX_PARALLEL = 64;
    /** OutputHellishMetalPercent_NetherInterface. */
    static final double NETHER_HELLISH_CHANCE = 30 / 100.0;
    /** bottledchaos: BottledChaosChainedProcessingSeconds, BottledChaosMinimumOverclockSeconds. */
    static final int CHAOS_CHAINED_SECONDS = 20, CHAOS_MINIMUM_SECONDS = 5;
    /** modularizedmachinestuffs: PowerConsumptionMultiplierOfPowerConsumptionController, read as floats. */
    static final float[] POWER_CONTROLLER = { 0.95f, 0.9f, 0.85f, 0.8f, 0.75f, 0.7f, 0.5f, 0.25f };
    /** dsp: overloadSpeedUpMultiplier, overloadSpecialCalculationParameter, secondsOfEverySpaceWarper... */
    static final int DSP_OVERLOAD_SPEED = 30, DSP_OVERLOAD_PARAMETER = 1000, DSP_WARPER_SECONDS = 900;

    // endregion

    // region TST's hard-coded numbers

    /** OP_Values: OreProcessRecipeDuration, OreProcessRecipeEUt, ticksOfPerFluidConsuming, LubricantCost. */
    static final int ORE_TICKS = 128, ORE_EUT = 30, ORE_LUBRICANT_TICKS = 256, ORE_LUBRICANT = 3200;
    /** TST_SkypiercerTower: RECIPE_EUT (:325), the parallels a ring by mode (:461, :510, :564). */
    static final long SKY_EUT = 1920;
    /** Config (not read from the cfg, :114-140): the Strange Matter Aggregator's numbers by tier 1 to 3. */
    static final int[] SMA_RECIPE_TIME = { 2400, 800, 200 }, SMA_MAX_STREAK = { 24, 144, 384 },
        SMA_MAINTENANCE = { 576, 96, 16 }, SMA_ROD_FACTOR = { 256, 1024, 16384 }, SMA_FLUID_FACTOR = { 1000, 50, 1 };
    static final double[] SMA_AUXILIARY_RATE = { 1.0d, 0.99d, 0.90d };
    static final int SMA_BASE_INPUT = 256, SMA_BYPRODUCT = 144;
    /** PowerConsume_StrangeMatterAggregator: RECIPE_MAX x 256. */
    static final long SMA_POWER = 2_013_265_912L * 256L;

    // endregion

    // region GregTech's numbers

    /** TierEU.RECIPE_UMV: GTValues.VP[12]. */
    static final long RECIPE_UMV = 125_829_120L;

    // endregion

    // region Settings

    static final String ORE_POWER = "tstOrePower", ORE_BATCH = "tstOreBatch", XC_MAGNIFICATION = "tstXcMagnification",
        XC_CRAFTS = "tstXcCrafts", SKY_MODE = "tstSkyMode", SKY_RINGS = "tstSkyRings", SKY_TREE = "tstSkyTreeSeconds",
        SMA_OSCILLATOR = "tstSmaOscillatorTier", SMA_OSCILLATORS = "tstSmaOscillators",
        SMA_CONSTRAINTOR = "tstSmaConstraintorTier", SMA_CONSTRAINTORS = "tstSmaConstraintors",
        SMA_MERGER = "tstSmaMergerTier", SMA_MERGERS = "tstSmaMergers", SMA_CONSTRAINERS = "tstSmaConstrainers",
        SMA_STREAK = "tstSmaStreak", SMA_CORE = "tstSmaCoreElement", MODULE_SPEED = "tstModuleSpeed",
        MODULE_POWER = "tstModulePower", DSP_MOTOR = "tstDspMotor", DSP_OVERLOAD = "tstDspOverload",
        DSP_POWER = "tstDspPower", CHAOS_OVERCLOCK = "tstChaosOverclock", CHAOS_CORE = "tstChaosCore",
        CHAOS_ROUTE = "tstChaosRouteTicks", SKY_STACK = "tstSkyStack", SMA_POWER_INPUT = "tstSmaPower",
        CHAOS_POWER = "tstChaosPower", CHAOS_BATCHES = "tstChaosBatches", CHAOS_SHARE = "tstChaosShare",
        CHAOS_TOTAL_SHARE = "tstChaosTotalShare", CHAOS_UPKEEP_FLUID = "tstChaosUpkeepFluid",
        CHAOS_UPKEEP = "tstChaosUpkeepLitres", CHAOS_PHOTONS = "tstChaosPhotons";

    /** TST_OreProcessingFactory.checkMachine (:190-200): which hatches feed it. */
    static final Web.Control ORE_POWER_CONTROL = choice(
        ORE_POWER,
        "Energy Input",
        "One Energy Hatch",
        "Several or Exotic Hatches",
        "Wireless");
    /** Wireless takes every ore waiting at the run's start: how many that is. */
    static final Web.Control ORE_BATCH_CONTROL = withDefault(count(ORE_BATCH, "Ores a Run (Wireless)", 1, null), 64);

    static final Web.Control XC_MAGNIFICATION_CONTROL = count(XC_MAGNIFICATION, "Pattern Magnification", 1, null);
    static final Web.Control XC_CRAFTS_CONTROL = count(XC_CRAFTS, "Pattern Crafts a Cycle", 1, null);

    /**
     * machineMode 0 to 2 (:338-389). The card starts in Crystal Essence Mode, the one whose inputs and outputs are the
     * page's crystals; Essentia Mode runs the same numbers on essentia, Passive Mode on the tree's primals.
     */
    static final Web.Control SKY_MODE_CONTROL = withDefault(
        choice(SKY_MODE, "Mode", "Passive Mode", "Crystal Essence Mode", "Essentia Mode"),
        "crystal-essence-mode");
    static final Web.Control SKY_RINGS_CONTROL = count(SKY_RINGS, "Rings", 0, null);
    /** Crystal Essence Mode's parallels stop at the smaller of the two crystal stacks it finds (:510-511). */
    static final Web.Control SKY_STACK_CONTROL = withDefault(
        count(SKY_STACK, "Crystals a Stack (Crystal Essence Mode)", 1, null),
        64);

    private static final List<String> SMA_TIERS = List.of("T1", "T2", "T3");
    static final Web.Control SMA_OSCILLATOR_CONTROL = choice(
        SMA_OSCILLATOR,
        "SpaceTime Oscillator Tier",
        SMA_TIERS,
        null);
    static final Web.Control SMA_OSCILLATORS_CONTROL = count(SMA_OSCILLATORS, "SpaceTime Oscillators", 1, null);
    static final Web.Control SMA_CONSTRAINTOR_CONTROL = choice(
        SMA_CONSTRAINTOR,
        "SpaceTime Constraintor Tier",
        SMA_TIERS,
        null);
    static final Web.Control SMA_CONSTRAINTORS_CONTROL = count(SMA_CONSTRAINTORS, "SpaceTime Constraintors", 1, null);
    static final Web.Control SMA_MERGER_CONTROL = choice(SMA_MERGER, "SpaceTime Merger Tier", SMA_TIERS, null);
    static final Web.Control SMA_MERGERS_CONTROL = count(SMA_MERGERS, "SpaceTime Mergers", 1, null);
    static final Web.Control SMA_CONSTRAINERS_CONTROL = count(
        SMA_CONSTRAINERS,
        "Annihilation Constrainers a Run",
        1,
        null);
    /** The streak of runs (consecutivePoint), capped by the merger tier; the steady state is the cap. */
    static final Web.Control SMA_STREAK_CONTROL = withDefault(count(SMA_STREAK, "Consecutive Runs", 0, null), 384);
    static final Web.Control SMA_CORE_CONTROL = choice(SMA_CORE, "Core Element", "Supplied", "Not Supplied");

    /** A speed controller module's multiplier (static T1-T8 2 to 256, or a dynamic one's setting); 1 without. */
    static final Web.Control MODULE_SPEED_CONTROL = count(MODULE_SPEED, "Speed Controller Multiplier", 1, 256);
    static final Web.Control MODULE_POWER_CONTROL = choice(
        MODULE_POWER,
        "Power Consumption Controller",
        "No Power Consumption Controller",
        "Static Power Consumption Controller Module T1",
        "Static Power Consumption Controller Module T2",
        "Static Power Consumption Controller Module T3",
        "Static Power Consumption Controller Module T4",
        "Static Power Consumption Controller Module T5",
        "Static Power Consumption Controller Module T6",
        "Static Power Consumption Controller Module T7",
        "Static Power Consumption Controller Module T8");

    static final Web.Control DSP_MOTOR_CONTROL = choice(DSP_MOTOR, "Motor Tier", "T1", "T2", "T3", "T4", "T5");
    static final Web.Control DSP_OVERLOAD_CONTROL = count(DSP_OVERLOAD, "Overload Seconds Left", 0, null);
    static final Web.Control DSP_POWER_CONTROL = choice(DSP_POWER, "Energy Input", "Energy Hatches", "Wireless");

    /**
     * ModularizedMachineSupportAllModuleBase.overclockType (:24-37): NormalOverclock without a controller module, else
     * the module's OverclockType (:9-13).
     */
    static final Web.Control CHAOS_OVERCLOCK_CONTROL = choice(
        CHAOS_OVERCLOCK,
        "Overclock Controller",
        "No Overclock Controller",
        "Low Speed Perfect Overclock Controller Module",
        "Perfect Overclock Controller Module",
        "Singularity Perfect Overclock Controller Module");
    /** {time reduction, power increase} by the overclock setting's position. */
    static final double[][] CHAOS_OVERCLOCKS = { { 2, 4 }, { 2, 2 }, { 4, 4 }, { 8, 4 } };
    /** generatorLevel 0 to 4: route EU/t up to V[UHV + level], V[MAX] at 4 (MM_BottledChaos :2060-2062). */
    static final Web.Control CHAOS_CORE_CONTROL = choice(CHAOS_CORE, "Core Tier", "UHV", "UEV", "UIV", "UMV", "MAX");
    /** The modular machines' wireless form: no energy hatches at all. */
    static final Web.Control SMA_POWER_CONTROL = choice(SMA_POWER_INPUT, "Energy Input", "Energy Hatches", "Wireless");
    static final Web.Control CHAOS_POWER_CONTROL = choice(CHAOS_POWER, "Energy Input", "Energy Hatches", "Wireless");
    /** Wireless batches grow with the inputs waiting (maximizeWireless, no time budget): how many that is. */
    static final Web.Control CHAOS_BATCHES_CONTROL = count(CHAOS_BATCHES, "Batches a Run (Wireless)", 1, null);
    /** proportionalBudgets: a group's EU/t is the hatches' times its credential frames' capacity over all groups'. */
    static final Web.Control CHAOS_SHARE_CONTROL = count(CHAOS_SHARE, "This Group's Credential Capacity", 1, null),
        CHAOS_TOTAL_SHARE_CONTROL = count(CHAOS_TOTAL_SHARE, "All Groups' Credential Capacity", 1, null);
    /** The installed source machines' upkeep (BottledChaosMachineParameters :562-710): fluid and litres a second. */
    static final Web.Control CHAOS_UPKEEP_FLUID_CONTROL = choice(
        CHAOS_UPKEEP_FLUID,
        "Source Upkeep Fluid",
        "None",
        "Gelid Cryotheum",
        "Blazing Pyrotheum",
        "Molten Magnetohydrodynamically Constrained Star Matter",
        "Super Coolant",
        "Molten SpaceTime",
        "Molten Eternity",
        "Biocatalyzed Propulsion Fluid");
    /** Forge fluid names by the upkeep setting's position past None. */
    static final String[] CHAOS_UPKEEP_FLUIDS = { "fluid:cryotheum", "fluid:pyrotheum",
        "fluid:molten.magnetohydrodynamicallyconstrainedstarmatter", "fluid:supercoolant", "fluid:molten.spacetime",
        "fluid:molten.eternity", "fluid:biocatalyzedpropulsionfluid" };
    static final Web.Control CHAOS_UPKEEP_CONTROL = count(CHAOS_UPKEEP, "Source Upkeep (L/s)", 0, null);
    /** Energy-only sources take Critical Photons once a run (roundItemDemands, :669-681). */
    static final Web.Control CHAOS_PHOTONS_CONTROL = count(CHAOS_PHOTONS, "Critical Photons a Run", 0, null);
    static final Web.Control[] CHAOS_CONTROLS = { MODULE_SPEED_CONTROL, MODULE_POWER_CONTROL, CHAOS_OVERCLOCK_CONTROL,
        CHAOS_CORE_CONTROL, CHAOS_POWER_CONTROL, CHAOS_BATCHES_CONTROL, CHAOS_SHARE_CONTROL, CHAOS_TOTAL_SHARE_CONTROL,
        CHAOS_UPKEEP_FLUID_CONTROL, CHAOS_UPKEEP_CONTROL, CHAOS_PHOTONS_CONTROL };

    // endregion

    /** The gate messages, English data. */
    static final String NETHER_POWER_GATE = "Needs at least 23040 EU/t of hatches: 2 A of IV upkeep and 1 A a parallel.",
        WATER_POWER_GATE = "Needs at least 125829120 EU/t of hatches, one parallel's.",
        ORE_POWER_GATE = "Needs at least 30 EU/t of usable hatch power.",
        SKY_POWER_GATE = "Needs at least 1920 EU/t of hatches.",
        SMA_POWER_GATE = "The hatches must carry the run's EU/t and at least 515396073472 EU/t.",
        SMA_PARALLEL_GATE = "Too few Annihilation Constrainers for one parallel.",
        DSP_POWER_GATE = "The hatches cannot carry this launch's EU/t.",
        CHAOS_POWER_GATE = "The hatches cannot carry this route's EU/t.",
        CHAOS_CORE_GATE = "Recipe voltage exceeds the structure upgrade limit.";

    public static void register(final Map<String, Behaviour> machines) {
        TstVisualEcoSphere.register(machines);
        // TST_NetherInterface.checkProcessing (:204-263): no recipe lookup. p = min(64, EU / 7680 - 2) parallels
        // (water allowing), 1200 ticks, 7680 x (2 + p) EU/t; 16000 L distilled water in and Poor Nether Waste out a
        // parallel; three weighted picks of the five item stacks (initStatics :265-286, weights 1/49/30/10/10, the
        // NEI chances), each times p; 288 L of molten Hellish Metal a parallel on 30% of runs.
        put(
            machines,
            "Nether Interface",
            machine().overclock(none())
                .fixedTicks(c -> NETHER_TICKS)
                .parallels(c -> Math.max(1, netherParallels(c)))
                .power(c -> {
                    final int p = Math.max(1, netherParallels(c));
                    return c.recipe != null && c.recipe.eut != 0
                        ? (double) NETHER_EU * (2 + p) / p / Math.abs(c.recipe.eut)
                        : 1;
                })
                .outputMultiplier(TstMachinesVisual::netherOutput)
                .recipeGate(c -> netherParallels(c) < 1 ? NETHER_POWER_GATE : null));
        // TST_SuperWaterPurifier.process (:196-287): no recipe lookup. p = min(2,000,000, EU / RECIPE_UMV), 1200 ticks,
        // RECIPE_UMV x p EU/t; 1000 L of UU-Matter (or 1 L of the concentrated one) a parallel; three (six on the
        // concentrated) weighted picks of 1000 L (initStatics :291-306: each grade 12, Stable Baryonic Matter 4 of
        // 100).
        put(
            machines,
            "Super Water Purifier",
            machine().overclock(none())
                .fixedTicks(c -> 1200)
                .parallels(c -> Math.max(1, waterParallels(c)))
                .outputMultiplier(TstMachinesVisual::waterOutput)
                .recipeGate(c -> waterParallels(c) < 1 ? WATER_POWER_GATE : null));
        // TST_OreProcessingFactory (:183-431) with OP_Logic: no recipe lookup and no overclock. Each run takes as
        // many ores as the usable power pays 30 EU/t for (one hatch: V x 15/16; more or exotic: V x A x 31/32;
        // wireless: every ore waiting), 128 ticks, the page's outputs an ore; 3200 L of Lubricant every 256 running
        // ticks (onRunningTick :446-452), so 1600 L a run whatever its ores.
        put(
            machines,
            "General Ore Processing Factory TST",
            machine().overclock(none())
                .fixedTicks(c -> ORE_TICKS)
                .parallels(c -> Math.max(1, oreParallels(c)))
                .inputMultiplier(TstMachinesVisual::oreInput)
                .controls(ORE_POWER_CONTROL, ORE_BATCH_CONTROL)
                .recipeGate(c -> oreParallels(c) < 1 ? ORE_POWER_GATE : null));
        // TST_MegaCraftingCenter (:291-334): runs the AE2 patterns pushed into it, no EU, every 20 ticks
        // (TickEveryProcess_MegaCraftingCenter); a pattern's crafts times the magnification (GUI, default 1).
        put(
            machines,
            "Extreme Crafting Center",
            machine().overclock(none())
                .fixedTicks(c -> 20)
                .parallels(
                    c -> Math.min(
                        Integer.MAX_VALUE,
                        Math.max(1, number(c, XC_MAGNIFICATION)) * (double) Math.max(1, number(c, XC_CRAFTS))))
                .controls(XC_MAGNIFICATION_CONTROL, XC_CRAFTS_CONTROL));
        // TST_SkypiercerTower.checkProcessing (:367-692): no recipe lookup. Parallels 6 a ring (16 in Essentia Mode),
        // the time 20 x T over 1.2^rings (Essentia: not), GT's overclock at 1920 EU/t on the hatches' whole EU with
        // one amp (Essentia: 4/4), EU/t never times the parallels. Passive Mode makes p of the aspect from the
        // primals of one.
        put(
            machines,
            "Skypiercer Tower",
            machine().overclock(none())
                .fixedTicks(c -> skypiercerTicks(c))
                .parallels(c -> skypiercerParallels(c))
                .power(c -> {
                    final long eut = SKY_EUT << (2 * skypiercerOverclocks(TstMachines.totalEu(c)));
                    return c.recipe != null && c.recipe.eut != 0
                        ? (double) eut / skypiercerParallels(c) / Math.abs(c.recipe.eut)
                        : 1;
                })
                .inputMultiplier((c, in) -> c.tier(SKY_MODE) == 0 ? 1.0 / skypiercerParallels(c) : 1)
                .controls(SKY_MODE_CONTROL, SKY_RINGS_CONTROL, SKY_STACK_CONTROL)
                .controlsFor(r -> List.of(SKY_MODE_CONTROL, SKY_RINGS_CONTROL, SKY_STACK_CONTROL, treeControl(r)))
                .recipeGate(c -> TstMachines.totalEu(c) < SKY_EUT ? SKY_POWER_GATE : null));
        // TST_StrangeMatterAggregator.checkProcessingMM (:922-1236), calculateParameters (:784-866), setRunning
        // (:908-919): no recipe lookup; one run whose parallel is the constrainers' (the other inputs supplied), the
        // amounts each its own rounding, a fixed EU/t from the mergers and a time from the oscillator and constraintor.
        put(
            machines,
            "Strange Matter Aggregator",
            machine().overclock(none())
                .fixedTicks(c -> smaTicks(c))
                .parallels(1)
                .power(c -> c.recipe != null && c.recipe.eut != 0 ? smaRunEut(c) / Math.abs(c.recipe.eut) : 1)
                .inputMultiplier(TstMachinesVisual::smaInput)
                .outputMultiplier(TstMachinesVisual::smaOutput)
                .upkeep(c -> smaCore(c))
                .controls(
                    SMA_OSCILLATOR_CONTROL,
                    SMA_OSCILLATORS_CONTROL,
                    SMA_CONSTRAINTOR_CONTROL,
                    SMA_CONSTRAINTORS_CONTROL,
                    SMA_MERGER_CONTROL,
                    SMA_MERGERS_CONTROL,
                    SMA_CONSTRAINERS_CONTROL,
                    SMA_STREAK_CONTROL,
                    SMA_CORE_CONTROL,
                    MODULE_SPEED_CONTROL,
                    MODULE_POWER_CONTROL,
                    SMA_POWER_CONTROL)
                .recipeGate(c -> {
                    if (smaParallel(c) < 1) return SMA_PARALLEL_GATE;
                    if (c.tier(SMA_POWER_INPUT) == 1) return null;
                    return TstMachines.totalEu(c) < Math.max(SMA_POWER, smaEut(c)) ? SMA_POWER_GATE : null;
                }));
        // TST_DSPLauncher.checkProcessing (:340-395): one Solar Sail or Small Launch Vehicle a run at the page's EU/t,
        // the page's time over the motor tier (or over the overload's speed-up while Space Warpers last); wireless
        // pays the page's whole EU up front. A Space Warper buys 900 s of overload.
        put(
            machines,
            "Dyson Sphere Module Launch Site",
            machine().overclock(none())
                .fixedTicks(c -> dspTicks(c))
                .parallels(1)
                .power(c -> c.tier(DSP_POWER) == 1 && c.recipe != null ? c.recipe.durationTicks / dspTicks(c) : 1)
                .upkeep(
                    c -> number(c, DSP_OVERLOAD) > 0 ? List.of(Upkeep.perSecond(SPACE_WARPER, 1.0 / DSP_WARPER_SECONDS))
                        : List.of())
                .controls(DSP_MOTOR_CONTROL, DSP_OVERLOAD_CONTROL, DSP_POWER_CONTROL)
                .recipeGate(
                    c -> c.tier(DSP_POWER) == 0 && c.recipe != null && TstMachines.totalEu(c) < Math.abs(c.recipe.eut)
                        ? DSP_POWER_GATE
                        : null));
        // MM_BottledChaos with BottledChaosProcessingLogic.processWired (:120-199) and BottledChaosOverclock: the route
        // replayed in as many batches as fit 20 s of its fastest time, then one overclock to at least 5 s; wireless
        // (processWireless :202-259) takes every batch waiting, 100 t, billed once. The installed sources' upkeep and
        // Critical Photons are upkeep ports. The credential and structure pages are crafts made at once in the
        // controller's workbench.
        put(
            machines,
            "Bottled Chaos",
            machine().overclock(none())
                .fixedTicks(c -> chaosRoute(c) ? chaosRun(c).ticks() : 1)
                .parallels(c -> chaosRoute(c) ? chaosRun(c).batches() : 1)
                .power(c -> {
                    if (!chaosRoute(c) || c.recipe == null || c.recipe.eut == 0) return 0;
                    final ChaosRun run = chaosRun(c);
                    return run.eut() / run.batches() / Math.abs(c.recipe.eut);
                })
                .upkeep(c -> chaosRoute(c) ? chaosUpkeep(c) : List.of())
                .controls(CHAOS_CONTROLS)
                .controlsFor(r -> {
                    if (!CHAOS_MAP.equals(r.source != null ? r.source.recipeMap : null)) return List.of();
                    final List<Web.Control> list = new ArrayList<>(List.of(CHAOS_CONTROLS));
                    list.add(routeControl(r));
                    return list;
                })
                .recipeGate(c -> chaosRoute(c) ? chaosGate(c) : null));
    }

    /** An entry, the maps' settings hidden: these machines bill their own parallels, never ParallelHelper's. */
    private static void put(final Map<String, Behaviour> machines, final String name, final Behaviour behaviour) {
        machines.put(name, behaviour.hidesControls(MAP_CONTROLS));
    }

    // region Shared pieces

    /** A count's default other than its minimum. */
    static Web.Control withDefault(final Web.Control control, final int value) {
        control.defaultKey = String.valueOf(value);
        return control;
    }

    static Web.Control withDefault(final Web.Control control, final String key) {
        control.defaultKey = key;
        return control;
    }

    static int number(final Context c, final String id) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, c.value(id)));
    }

    /**
     * The resource's place among the list's of its kind (items first, then fluids, as NEI lists them); -1 if absent.
     */
    static int place(final List<Web.Resource> list, final Web.Resource resource) {
        if (list == null) return -1;
        int index = 0;
        for (final Web.Resource r : list) {
            if (r == resource) return index;
            if (r.kind != null && r.kind.equals(resource.kind)) index++;
        }
        return -1;
    }

    static int outputPlace(final Context c, final Web.Resource output) {
        return c.recipe != null ? place(c.recipe.outputs, output) : -1;
    }

    static int inputPlace(final Context c, final Web.Resource input) {
        return c.recipe != null ? place(c.recipe.inputs, input) : -1;
    }

    /** GTUtility.log4 on a long: floor(log4 a), 0 at 1 or less. */
    static long log4(final long a) {
        return a <= 1 ? 0 : (63 - Long.numberOfLeadingZeros(a)) >> 1;
    }

    /** GTUtility.log4ceil on a long: ceil(log4 a), 0 at 1 or less. */
    static long log4ceil(final long a) {
        return a <= 1 ? 0 : (65 - Long.numberOfLeadingZeros(a - 1)) >> 1;
    }

    /** The modular machines' speed bonus (SpeedControllerBase :42-43): 1F / the controller's multiplier. */
    static float speedBonus(final Context c) {
        return 1F / Math.max(1, Math.min(256, number(c, MODULE_SPEED)));
    }

    /** The modular machines' EU modifier: the power consumption controller's float, 1 without. */
    static float euModifier(final Context c) {
        final int position = c.tier(MODULE_POWER);
        return position <= 0 || position > POWER_CONTROLLER.length ? 1F : POWER_CONTROLLER[position - 1];
    }

    // endregion

    // region Nether Interface and Super Water Purifier

    /** min(64, getMaxInputEu / 7680 - 2); 0 below 3 x 7680 EU/t, where it refuses to start. */
    static int netherParallels(final Context c) {
        final long eu = TstMachines.totalEu(c);
        if (eu < NETHER_EU * 3) return 0;
        return (int) Math.min(NETHER_MAX_PARALLEL, eu / NETHER_EU - 2);
    }

    /** Items: three picks where NEI shows one; Hellish Metal (the second fluid): 30% of runs where NEI shows all. */
    private static double netherOutput(final Context c, final Web.Resource output) {
        if ("item".equals(output.kind)) return NETHER_STACKS;
        return outputPlace(c, output) == 1 ? NETHER_HELLISH_CHANCE : 1;
    }

    /** min(2,000,000, getMaxInputEu / RECIPE_UMV). */
    static int waterParallels(final Context c) {
        return (int) Math.min(2_000_000, TstMachines.totalEu(c) / RECIPE_UMV);
    }

    /**
     * NEI lists 1000 L of every product; a parallel makes three picks (six on Concentrated UU-Matter, 1 L a parallel)
     * of 1000 L: 12% each grade, 4% Stable Baryonic Matter (the ninth).
     */
    private static double waterOutput(final Context c, final Web.Resource output) {
        final int picks = concentrated(c) ? 6 : 3;
        return picks * (outputPlace(c, output) == 8 ? 0.04 : 0.12);
    }

    /** The recipe that burns Concentrated UU-Matter: 1 L where UU-Matter's takes 1000. */
    private static boolean concentrated(final Context c) {
        if (c.recipe == null || c.recipe.inputs == null) return false;
        for (final Web.Resource in : c.recipe.inputs) if ("fluid".equals(in.kind) && in.amount < 1000) return true;
        return false;
    }

    // endregion

    // region General Ore Processing Factory TST

    /** The usable EU/t: one normal hatch V x 15/16, several or exotic getMaxInputEu x 31/32 (:190-200). */
    static long oreUsableEu(final Context c) {
        final long eu = TstMachines.totalEu(c);
        if (c.tier(ORE_POWER) == 0) return hatchVoltage(eu) * 15L / 16L;
        return eu * 31L / 32L;
    }

    /** A single hatch's voltage: the highest tier's at or under its V x A (a normal hatch works at most 2 A). */
    static long hatchVoltage(final long eu) {
        long v = 0;
        for (final double tier : Tiers.MAX_EUT) if (tier <= eu) v = (long) tier;
        return v;
    }

    /** Ores a run: what the usable power pays 30 EU/t each for (:365), or the wireless batch. */
    static int oreParallels(final Context c) {
        if (c.tier(ORE_POWER) == 2) return Math.max(1, number(c, ORE_BATCH));
        return (int) Math.min(Integer.MAX_VALUE, oreUsableEu(c) / ORE_EUT);
    }

    /** The 1 L of Lubricant NEI lists is 1600 L a run (3200 L every 256 ticks), shared by the run's ores. */
    private static double oreInput(final Context c, final Web.Resource input) {
        if (!"fluid".equals(input.kind) || input.amount <= 0) return 1;
        final double perRun = (double) ORE_LUBRICANT * ORE_TICKS / ORE_LUBRICANT_TICKS;
        return perRun / input.amount / Math.max(1, oreParallels(c));
    }

    // endregion

    // region Skypiercer Tower

    /**
     * Position of the mode setting: Passive 0, Crystal Essence 1, Essentia 2 (machineMode). Crystal Essence Mode stops
     * at
     * the smaller crystal stack (:510-511).
     */
    static int skypiercerParallels(final Context c) {
        final long rings = number(c, SKY_RINGS);
        final int mode = c.tier(SKY_MODE);
        final long perRing = mode == 2 ? 16 : 6;
        long parallels = Math.max(1, Math.min(Integer.MAX_VALUE, rings * perRing));
        if (mode == 1) parallels = Math.min(parallels, Math.max(1, number(c, SKY_STACK)));
        return (int) parallels;
    }

    /**
     * OverclockCalculator (GT :385-436) with recipe EU/t 1920, machine voltage the hatches' whole EU, one amp and no
     * amperage overclocks: steps the power pays for, capped by the voltage tiers above EV's.
     */
    static int skypiercerOverclocks(final long eu) {
        if (eu < SKY_EUT) return 0;
        final long tiersAbove = log4(Math.round(eu * 100d) / Math.max(Math.round(SKY_EUT * 100d), 32L * 100));
        final int machineTier = (int) Math.max(log4ceil(eu / 8), 1);
        final int recipeTier = (int) Math.max(log4ceil(SKY_EUT / 8), 1);
        return (int) Math.max(Math.min(tiersAbove, machineTier - recipeTier), 0);
    }

    /** The run's ticks: the mode's base time, then the overclocks (2x, 4x in Essentia Mode), truncated, at least 1. */
    static double skypiercerTicks(final Context c) {
        if (c.recipe == null) return 1;
        final int rings = number(c, SKY_RINGS);
        final double divisor = rings == 0 ? 1 : Math.pow(1.2, rings);
        final int mode = c.tier(SKY_MODE);
        final int base;
        if (mode == 2) base = (int) c.recipe.durationTicks;
        else if (mode == 1) base = (int) ((int) c.recipe.durationTicks / divisor);
        else base = (int) Math.ceil(20 * treeSeconds(c) / divisor);
        double duration = base;
        final int steps = skypiercerOverclocks(TstMachines.totalEu(c));
        for (int i = 0; i < steps; i++) duration /= mode == 2 ? 4 : 2;
        return (int) Math.max(duration, 1);
    }

    /** Passive Mode's time: the synthesis seconds of every compound aspect in the tree of one (RECIPE_DURATION). */
    static int treeSeconds(final Context c) {
        final int own = c.recipe != null ? (int) (c.recipe.durationTicks / 20) : 1;
        return Math.max(own, number(c, SKY_TREE));
    }

    /** Passive Mode's tree time, from the recipe's own aspect (its page's ticks / 20) up. */
    private static Web.Control treeControl(final Web.Recipe recipe) {
        final int own = Math.max(1, (int) (recipe.durationTicks / 20));
        return withDefault(count(SKY_TREE, "Synthesis Seconds of the Tree (Passive)", own, null), own);
    }

    // endregion

    // region Strange Matter Aggregator

    private static int smaTier(final Context c, final String id) {
        return Math.max(1, Math.min(3, c.tier(id) + 1));
    }

    private static int smaRings(final Context c, final String id) {
        return Math.max(1, number(c, id));
    }

    /** The maintenance fluid tier the card's recipe is for: its second fluid, 576 / 96 / 16 L. */
    static int smaFluidTier(final Context c) {
        if (c.recipe != null && c.recipe.inputs != null) for (final Web.Resource in : c.recipe.inputs) {
            if (!"fluid".equals(in.kind)) continue;
            for (int t = 0; t < SMA_MAINTENANCE.length; t++) if (in.amount == SMA_MAINTENANCE[t]) return t + 1;
        }
        return 1;
    }

    /** consecutivePoint at the run: the setting, capped by the merger's maximum. */
    static int smaStreak(final Context c) {
        return Math.min(number(c, SMA_STREAK), SMA_MAX_STREAK[smaTier(c, SMA_MERGER) - 1]);
    }

    /** inputFactor (:811): 1 + (256 - 1) / oscillators^0.5. */
    static double smaInputFactor(final Context c) {
        return 1d + (SMA_BASE_INPUT - 1) / Math.pow(smaRings(c, SMA_OSCILLATORS), 0.5);
    }

    /** constrainerFactor (:846-851). */
    static long smaRodFactor(final Context c) {
        return (long) smaTier(c, SMA_OSCILLATOR) * smaRings(c, SMA_OSCILLATORS)
            * smaTier(c, SMA_CONSTRAINTOR)
            * smaRings(c, SMA_CONSTRAINTORS)
            * smaTier(c, SMA_MERGER)
            * smaRings(c, SMA_MERGERS)
            * SMA_ROD_FACTOR[smaFluidTier(c) - 1];
    }

    /** The parallel (:1020): the constrainers' rods over the rods a recipe makes; the other inputs are supplied. */
    static double smaParallel(final Context c) {
        final long rods = (long) Math.max(1, number(c, SMA_CONSTRAINERS)) * smaRodFactor(c);
        return (double) rods / (1 + smaStreak(c));
    }

    /** outputRodAmount (:1041). */
    static long smaRods(final Context c) {
        return (long) (smaParallel(c) * (1 + smaStreak(c)));
    }

    /** Annihilation Constrainers used (:1057-1058); as many Core Elements with them supplied. */
    static long smaConstrainersUsed(final Context c) {
        return (long) Math.max(1, Math.ceil((double) smaRods(c) / smaRodFactor(c)));
    }

    /** The run's ticks (:917): constraintor tier x the oscillator's time, times the speed bonus as a float. */
    static double smaTicks(final Context c) {
        final int recipeTime = smaTier(c, SMA_CONSTRAINTOR) * SMA_RECIPE_TIME[smaTier(c, SMA_OSCILLATOR) - 1];
        return (int) Math.max(1, recipeTime * speedBonus(c));
    }

    /** The run's EU/t (:814-815, :915): merger tier x mergers^0.7 x RECIPE_MAX x 256, times the EU modifier. */
    static long smaEut(final Context c) {
        final long power = (long) (smaTier(c, SMA_MERGER) * Math.pow(smaRings(c, SMA_MERGERS), 0.7d) * SMA_POWER);
        return (long) (euModifier(c) * power);
    }

    /**
     * The card's EU/t: wired, the run's draw; wireless (no energy hatches, :787), setRunning (:909-913) hands the
     * network (long) (EU modifier x draw x the unsped run time, :816-818) once a run, a gain, so the EU/t is negative.
     */
    static double smaRunEut(final Context c) {
        if (c.tier(SMA_POWER_INPUT) != 1) return smaEut(c);
        final long power = (long) (smaTier(c, SMA_MERGER) * Math.pow(smaRings(c, SMA_MERGERS), 0.7d) * SMA_POWER);
        final int recipeTime = smaTier(c, SMA_CONSTRAINTOR) * SMA_RECIPE_TIME[smaTier(c, SMA_OSCILLATOR) - 1];
        final long total = power * recipeTime;
        return -(double) (long) (euModifier(c) * total) / smaTicks(c);
    }

    /** Each input's amount a run over the page's (:1044, :1072, :1085, :1098, :837-838). */
    private static double smaInput(final Context c, final Web.Resource input) {
        final int place = inputPlace(c, input);
        if (input.amount <= 0 || place < 0) return 1;
        final int fluidTier = smaFluidTier(c);
        final double parallel = smaParallel(c), inputFactor = smaInputFactor(c);
        final double auxiliary = SMA_AUXILIARY_RATE[smaTier(c, SMA_CONSTRAINTOR) - 1];
        final double amount;
        if ("item".equals(input.kind)) amount = switch (place) {
            case 0 -> Math.max(1, (long) (parallel * inputFactor));
            case 1 -> smaConstrainersUsed(c);
            case 2 -> (long) Math.max(1, parallel * auxiliary / SMA_ROD_FACTOR[fluidTier - 1]);
            default -> (long) Math.max(1, auxiliary * smaRods(c));
        };
        else if (place == 0) amount = (long) Math.max(1, parallel * (inputFactor * SMA_FLUID_FACTOR[fluidTier - 1]));
        else amount = (int) Math.max(
            1d,
            SMA_MAINTENANCE[fluidTier - 1] * Math.pow(smaTier(c, SMA_OSCILLATOR) * smaRings(c, SMA_OSCILLATORS), 0.8d)
                / smaRings(c, SMA_CONSTRAINTORS));
        return amount / input.amount;
    }

    /** Rods: all of the run's as the one the Core Elements decide (:1115-1196); byproducts after the first run. */
    private static double smaOutput(final Context c, final Web.Resource output) {
        if (output.amount <= 0) return 1;
        final boolean core = c.tier(SMA_CORE) == 0;
        if ("item".equals(output.kind)) {
            final boolean strange = outputPlace(c, output) == 1;
            return strange == core ? (double) smaRods(c) / output.amount : 0;
        }
        final int streak = smaStreak(c);
        final long byproduct = (long) smaRings(c, SMA_MERGERS) * SMA_BYPRODUCT * streak * (core ? 2 : 1);
        return (double) byproduct / output.amount;
    }

    /** TST's MetaItem01, by its registry name. */
    static final String CORE_ELEMENT = "item:TwistSpaceTechnology:MetaItem01@28",
        SPACE_WARPER = "item:TwistSpaceTechnology:MetaItem01@1";

    /** The Core Elements a run uses: one a constrainer used (:1121-1123), from the special slot NEI shows. */
    private static List<Upkeep> smaCore(final Context c) {
        if (c.tier(SMA_CORE) != 0) return List.of();
        return List.of(Upkeep.perRun(CORE_ELEMENT, smaConstrainersUsed(c)));
    }

    // endregion

    // region Dyson Sphere Module Launch Site

    /** The run's ticks: the page's over the motor tier, or over the overload speed-up (:386-392, :499-506). */
    static double dspTicks(final Context c) {
        final int base = c.recipe != null ? (int) c.recipe.durationTicks : 1;
        final int motor = c.tier(DSP_MOTOR) + 1;
        final int seconds = number(c, DSP_OVERLOAD);
        if (seconds <= 0) return Math.max(1, base / motor);
        final double speed;
        if (seconds <= 1) speed = DSP_OVERLOAD_SPEED;
        else {
            final double i = 1d / (5d + 900d * DSP_OVERLOAD_PARAMETER / seconds);
            speed = (1 + Math.pow(seconds, i)) * DSP_OVERLOAD_SPEED * motor;
        }
        return (int) Math.max(1, base / speed);
    }

    // endregion

    // region Bottled Chaos

    /** The main page's map, by the name the data gives it; its credential and structure pages are crafts. */
    static final String CHAOS_MAP = "Bottled Chaos";

    private static boolean chaosRoute(final Context c) {
        return CHAOS_MAP.equals(c.recipeMap);
    }

    /** The route's time a batch: the page's reference time unless the card says otherwise. */
    private static Web.Control routeControl(final Web.Recipe recipe) {
        final int ticks = Math.max(1, (int) recipe.durationTicks);
        return withDefault(count(CHAOS_ROUTE, "Route Time a Batch (Ticks)", 1, null), ticks);
    }

    /** One run: batches, ticks and EU/t (a wireless run's whole bill over its ticks). */
    record ChaosRun(int batches, int ticks, double eut) {}

    static ChaosRun chaosRun(final Context c) {
        return c.tier(CHAOS_POWER) == 1 ? chaosWireless(c) : chaosWired(c);
    }

    /** The group's EU/t: powerForShare (BottledChaosPowerAllocator :6-10) of the hatches' by credential capacity. */
    static long chaosBudget(final Context c) {
        final long power = TstMachines.totalEu(c);
        final int share = Math.max(1, number(c, CHAOS_SHARE));
        final int total = Math.max(share, number(c, CHAOS_TOTAL_SHARE));
        if (power <= 0) return 0;
        return power / total * share + (power % total) * share / total;
    }

    /**
     * processWired (:120-199) with the inputs supplied: batches = floor(budget / route time) (one if a batch alone is
     * longer), the budget 20 s x the fastest speed over the speed bonus (batchDurationLimit :316-332); then
     * BottledChaosOverclock.calculate (:137-154) on the whole work.
     */
    static ChaosRun chaosWired(final Context c) {
        final long available = chaosBudget(c);
        final double[] clock = CHAOS_OVERCLOCKS[Math
            .max(0, Math.min(CHAOS_OVERCLOCKS.length - 1, c.tier(CHAOS_OVERCLOCK)))];
        final double timeReduction = clock[0], powerIncrease = clock[1];
        final double speedBoost = speedBonus(c), euModifier = euModifier(c);
        final double basePower = (c.recipe != null ? Math.abs(c.recipe.eut) : 0) * euModifier;
        final double route = Math.max(1, number(c, CHAOS_ROUTE));
        if (!(basePower > 0) || Math.ceil(basePower) > available) return new ChaosRun(1, (int) route, 0);
        final double maximumSpeed = chaosMaximumSpeed(basePower, available, timeReduction, powerIncrease);
        final double limit = Math.min(Double.MAX_VALUE, CHAOS_CHAINED_SECONDS * 20.0 * maximumSpeed / speedBoost);
        final long batches = route <= limit ? (long) Math.min(Integer.MAX_VALUE, limit / route) : 1;
        final double workTicks = batches * route * speedBoost;
        final int minimumTicks = CHAOS_MINIMUM_SECONDS * 20;
        final double targetSpeed = Math.max(1, workTicks / minimumTicks);
        final double speed = Math.min(maximumSpeed, targetSpeed);
        double power = Math.ceil(basePower);
        if (timeReduction > 1 && powerIncrease > 1)
            power *= Math.pow(speed, Math.log(powerIncrease) / Math.log(timeReduction));
        final long eut = (long) Math.min(available, Math.ceil(power));
        final double ticks = workTicks > minimumTicks && maximumSpeed >= targetSpeed ? minimumTicks
            : Math.ceil(workTicks / speed);
        return new ChaosRun(
            (int) Math.max(1, batches),
            ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, ticks),
            eut);
    }

    /**
     * processWireless (:202-259): every batch waiting, folded at V[MAX] (MM_BottledChaos.parameterVoltage :718-720),
     * work = batches x route x (EU/t / V[MAX]) x speed bonus, billed once (BottledChaosOverclock.wirelessCost :28-41):
     * ceil(V[MAX] x EU modifier x work x speed^(ln(power / time) / ln time)), speed max(1, work / 100); 100 t.
     */
    static ChaosRun chaosWireless(final Context c) {
        final double[] clock = CHAOS_OVERCLOCKS[Math
            .max(0, Math.min(CHAOS_OVERCLOCKS.length - 1, c.tier(CHAOS_OVERCLOCK)))];
        final double timeReduction = clock[0], powerIncrease = clock[1];
        final long voltage = (long) Tiers.MAX_EUT[14];
        final int batches = Math.max(1, number(c, CHAOS_BATCHES));
        final double eut = c.recipe != null ? Math.abs(c.recipe.eut) : 0;
        final double route = Math.max(1, number(c, CHAOS_ROUTE));
        final double power = voltage * (double) euModifier(c);
        final double work = batches * route * (eut / voltage) * speedBonus(c);
        final int ticks = CHAOS_MINIMUM_SECONDS * 20;
        final double speed = Math.max(1, work / ticks);
        final double multiplier = timeReduction > 1
            ? Math.pow(speed, Math.log(powerIncrease / timeReduction) / Math.log(timeReduction))
            : 1;
        return new ChaosRun(batches, ticks, Math.ceil(power * work * multiplier) / ticks);
    }

    /** TST's MetaItem01 Critical Photon, by its registry name. */
    static final String CRITICAL_PHOTON = "item:TwistSpaceTechnology:MetaItem01@13";

    /** The sources' upkeep fluid a second while running (BottledChaosUpkeep) and the Critical Photons a run. */
    private static List<Upkeep> chaosUpkeep(final Context c) {
        final List<Upkeep> out = new ArrayList<>();
        final int fluid = c.tier(CHAOS_UPKEEP_FLUID);
        final int litres = number(c, CHAOS_UPKEEP);
        if (fluid > 0 && fluid <= CHAOS_UPKEEP_FLUIDS.length && litres > 0)
            out.add(Upkeep.perSecond(CHAOS_UPKEEP_FLUIDS[fluid - 1], litres));
        final int photons = number(c, CHAOS_PHOTONS);
        if (photons > 0) out.add(Upkeep.perRun(CRITICAL_PHOTON, photons));
        return out;
    }

    /** BottledChaosOverclock.maximumSpeed (:157-168) through overclockSpeed (:181-201) with a fractional last step. */
    static double chaosMaximumSpeed(final double basePower, final long availablePower, final double timeReduction,
        final double powerIncrease) {
        if (timeReduction == 1) return 1;
        if (powerIncrease == 1) return Double.MAX_VALUE;
        double power = Math.ceil(basePower);
        double speed = 1;
        while (true) {
            if (power > availablePower / powerIncrease) {
                speed *= Math
                    .pow(Math.max(1, availablePower / power), Math.log(timeReduction) / Math.log(powerIncrease));
                break;
            }
            power *= powerIncrease;
            speed *= timeReduction;
        }
        return Double.isFinite(speed) ? speed : Double.MAX_VALUE;
    }

    /**
     * Wired only (processWireless checks no voltage or power): validateLimits (:301-309) on the core tier,
     * validatePlan (:286-299) on the group's EU/t.
     */
    private static String chaosGate(final Context c) {
        if (c.tier(CHAOS_POWER) == 1) return null;
        final int level = Math.max(0, Math.min(4, c.tier(CHAOS_CORE)));
        final double limit = Tiers.MAX_EUT[level >= 4 ? 14 : 9 + level];
        if (c.recipe != null && Math.abs(c.recipe.eut) > limit) return CHAOS_CORE_GATE;
        final double basePower = (c.recipe != null ? Math.abs(c.recipe.eut) : 0) * euModifier(c);
        return Math.ceil(basePower) > chaosBudget(c) ? CHAOS_POWER_GATE : null;
    }

    // endregion
}
