package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.*;
import static com.gtnhplanner.machines.eto.TstCoresCards.count;
import static com.gtnhplanner.machines.eto.TstCoresCards.exact;
import static com.gtnhplanner.machines.eto.TstCoresCards.number;
import static com.gtnhplanner.machines.eto.TstCoresCards.put;
import static com.gtnhplanner.machines.eto.TstCoresCards.putExact;
import static com.gtnhplanner.machines.eto.TstCoresCards.recipeEut;
import static com.gtnhplanner.machines.eto.TstCoresCards.recipeTicks;
import static com.gtnhplanner.machines.eto.TstMachines.*;
import static com.gtnhplanner.machines.web.MachineTable.machine;
import static com.gtnhplanner.machines.web.MachineTable.none;
import static com.gtnhplanner.machines.web.MachineTable.normal;
import static com.gtnhplanner.machines.web.MachineTable.perfect;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnhplanner.machines.eto.TstCoresCards.Exact;
import com.gtnhplanner.machines.web.MachineTable;
import com.gtnhplanner.machines.web.MachineTable.Behaviour;
import com.gtnhplanner.machines.web.MachineTable.Context;
import com.gtnhplanner.machines.web.Overclock;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's execution-core machines (the DTPF Prototype MK-II, the Large Neutron Oscillator), its
 * Thaumcraft machines (the Industrial Magic Matrix and Alchemy Tower), the new Mega Digester, and the modes the first
 * entries left out (Hephaestus' Atelier's furnace and wireless coils, Ball Lightning's fusion), as machine table
 * entries
 * beside {@link TstMachines}'. The arrays are {@link TstCoresArrays}'. docs/design/tst-machines.md, "Cores, arrays and
 * Thaumcraft machines", has the source lines. Pure: no game classes.
 */
public final class TstMachinesCores {

    private TstMachinesCores() {}

    // region Settings

    static final String EXEC_OVERCLOCK = "tstExecOverclock", EXEC_SPEED = "tstExecSpeed", EXEC_POWER = "tstExecPower",
        EXEC_CORES = "tstExecCores", EXEC_ADVANCED = "tstExecAdvancedCores", EXEC_PERFECT = "tstExecPerfectCores",
        DTPF_HOURS = "tstDtpfHours", ESSENTIA_CELL = "tstEssentiaCell", CONTROLLER_SLOT = "tstControllerSlot",
        NODES = "tstEnergizedNodes", AER = "tstNodeAer", IGNIS = "tstNodeIgnis", TERRA = "tstNodeTerra",
        AQUA = "tstNodeAqua", ORDO = "tstNodeOrdo", PERDITIO = "tstNodePerditio", HEPHAESTUS_COIL = "tstHephaestusCoil";

    /** OverclockType by the controller installed: none (Normal), Low Speed Perfect, Perfect, Singularity Perfect. */
    static final Web.Control EXEC_OVERCLOCK_CONTROL = choice(
        EXEC_OVERCLOCK,
        "Overclock Controller",
        "No Overclock Controller",
        "Low Speed Perfect Overclock Controller Module",
        "Perfect Overclock Controller Module",
        "Singularity Perfect Overclock Controller Module");

    /** OverclockType's time reduction and power increase, by the setting's position (MachineLoader :1697-1722). */
    private static final int[][] EXEC_OVERCLOCKS = { { 2, 4 }, { 2, 2 }, { 4, 4 }, { 8, 4 } };

    /** One speed controller, static (2 to 256) or dynamic (1 to its max): its speed multiplier. */
    static final Web.Control EXEC_SPEED_CONTROL = count(EXEC_SPEED, "Speed Controller Multiplier", 1, 256, 1);

    static final Web.Control EXEC_POWER_CONTROL = choice(
        EXEC_POWER,
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

    /** PowerConsumptionMultiplierOfPowerConsumptionController (Config :482, the pack's cfg the same), as floats. */
    private static final float[] EXEC_POWER_MULTIPLIERS = { 1F, (float) 0.95d, (float) 0.9d, (float) 0.85d,
        (float) 0.8d, (float) 0.75d, (float) 0.7d, (float) 0.5d, (float) 0.25d };

    static final Web.Control EXEC_CORES_CONTROL = count(EXEC_CORES, "Execution Core Modules", 0, null, 0),
        EXEC_ADVANCED_CONTROL = count(EXEC_ADVANCED, "Advanced Execution Core Modules", 0, null, 0),
        EXEC_PERFECT_CONTROL = count(EXEC_PERFECT, "Perfect Execution Core Modules", 0, null, 0);

    /** How long the DTPF Prototype MK-II has run without a break: its catalyst discount (full after 24 hours). */
    static final Web.Control DTPF_HOURS_CONTROL = count(DTPF_HOURS, "Hours Running", 0, 24, 24);

    /** GoodGenerator's Essentia Diffusion Cell, meta 0 to 3: the cell tier 1 to 4. */
    static final Web.Control ESSENTIA_CELL_CONTROL = choice(
        ESSENTIA_CELL,
        "Essentia Diffusion Cell",
        "Novice Essentia Diffusion Cell",
        "Adept Essentia Diffusion Cell",
        "Master Essentia Diffusion Cell",
        "Grandmaster Essentia Diffusion Cell");

    static final Web.Control CONTROLLER_SLOT_CONTROL = choice(
        CONTROLLER_SLOT,
        "Controller Slot",
        "Empty",
        "Proof of Heroes");

    static final Web.Control MATRIX_NODES_CONTROL = count(NODES, "Energized Nodes", 0, 6, 0),
        TOWER_NODES_CONTROL = count(NODES, "Energized Nodes", 0, 4, 0), AER_CONTROL = count(AER, "Aer", 0, null, 0),
        IGNIS_CONTROL = count(IGNIS, "Ignis", 0, null, 0), TERRA_CONTROL = count(TERRA, "Terra", 0, null, 0),
        AQUA_CONTROL = count(AQUA, "Aqua", 0, null, 0), ORDO_CONTROL = count(ORDO, "Ordo", 0, null, 0),
        PERDITIO_CONTROL = count(PERDITIO, "Perditio", 0, null, 0);

    /** The ring block of Hephaestus' Atelier (getBlockTier :526-537): tier 1, 2 and 3. */
    static final Web.Control HEPHAESTUS_COIL_CONTROL = choice(
        HEPHAESTUS_COIL,
        "Coil",
        "Molecular Coil",
        "Ultimate Containment Field Generator",
        "Teleportation Casing");

    // endregion

    /** The gate messages, English data. */
    static final String POWER_GATE = "The hatches cannot pay one parallel of this recipe.",
        CORE_SHARE_GATE = "Each core's share of the hatches' EU cannot pay one parallel of this recipe.",
        FUSION_COIL_GATE = "This recipe needs a higher compact fusion coil tier.";

    /** Maps by the name the data gives them. */
    static final String FURNACE = "Furnace";

    public static void register(final Map<String, Behaviour> machines) {
        // MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2: the plasma forge on execution cores; a recipe
        // hotter than the coils is refused (:254-256); the Excited DTxC fuel discount grows over the run time
        // (:362-383).
        putExact(
            machines,
            "Dimensionally Transcendent Matter Plasma Forge Prototype MK-II",
            exact(TstMachinesCores::executionRun).inputMultiplier(TstMachinesCores::plasmaForgeFuel)
                .controls(
                    HOT_COIL_CONTROL,
                    EXEC_OVERCLOCK_CONTROL,
                    PARALLELS_CONTROL,
                    EXEC_SPEED_CONTROL,
                    EXEC_POWER_CONTROL,
                    EXEC_CORES_CONTROL,
                    EXEC_ADVANCED_CONTROL,
                    EXEC_PERFECT_CONTROL,
                    DTPF_HOURS_CONTROL));
        // MM_LargeNeutronOscillator: the electric neutron activator map on execution cores.
        putExact(
            machines,
            "Large Neutron Oscillator",
            exact(TstMachinesCores::executionRun).controls(
                EXEC_OVERCLOCK_CONTROL,
                PARALLELS_CONTROL,
                EXEC_SPEED_CONTROL,
                EXEC_POWER_CONTROL,
                EXEC_CORES_CONTROL,
                EXEC_ADVANCED_CONTROL,
                EXEC_PERFECT_CONTROL));
        // GT_TileEntity_IndustrialMagicMatrix: 8 parallels a cell tier, speed from six energized nodes, extra ticks
        // for a missing primal; a Primordial Pearl back each run.
        putExact(
            machines,
            "Industrial Magic Matrix",
            exact(TstMachinesCores::magicMatrix).inputMultiplier(TstMachinesCores::primordialPearl)
                .controls(
                    ESSENTIA_CELL_CONTROL,
                    CONTROLLER_SLOT_CONTROL,
                    MATRIX_NODES_CONTROL,
                    AER_CONTROL,
                    IGNIS_CONTROL,
                    TERRA_CONTROL,
                    AQUA_CONTROL,
                    ORDO_CONTROL,
                    PERDITIO_CONTROL));
        // TST_IndustrialAlchemyTower: cell tier ^ 5 parallels, speed from four energized nodes.
        putExact(
            machines,
            "Industrial Alchemy Tower",
            exact(TstMachinesCores::alchemyTower).controls(
                ESSENTIA_CELL_CONTROL,
                CONTROLLER_SLOT_CONTROL,
                TOWER_NODES_CONTROL,
                IGNIS_CONTROL,
                AER_CONTROL,
                ORDO_CONTROL,
                PERDITIO_CONTROL));
        // TST_MegaDigester (new in 0.8.1): 1F / (coil tier + 1) (:202-206), Parallel_MegaDigester 2147483640 parallels
        // and perfect overclocks (pack cfg :529-535), the recipe's heat no more than the coils' (:224-227).
        put(
            machines,
            "Mega Digester",
            machine().overclock(perfect())
                .speed(c -> speedOf(1F / (coil(c) + 1)))
                .parallels(2147483640)
                .controls(HOT_COIL_CONTROL));
        // TST_HephaestusAtelier: Molecular Coils on hatches (alloy smelting, perfect, unlimited; smelting 512 ticks at
        // 7 EU/t an item, as many items as 15/16 of the hatches pay for); the higher rings run wireless.
        put(
            machines,
            "Hephaestus' Atelier",
            machine().handlerNumbers()
                .overclock(perfect())
                .power(TstMachinesCores::hephaestusPower)
                .parallels(c -> {
                    if (hephaestusTier(c) > 1 || !on(c, FURNACE)) return Integer.MAX_VALUE;
                    final long maxEut = (long) (15d / 16 * totalEu(c));
                    return (int) Math.min(Integer.MAX_VALUE, maxEut / HEPHAESTUS_EUT_PER_ITEM);
                })
                .fixedTicks(c -> hephaestusTier(c) > 1 ? hephaestusWirelessTicks(c) : 0)
                .controls(HEPHAESTUS_COIL_CONTROL));
        // TST_BallLightning, with energy hatches: arc furnace mode 0, fusion mode 2 and star kernel mode 3.
        ballLightning(machines);
        TstCoresArrays.register(machines);
    }

    private static boolean on(final Context c, final String recipeMap) {
        return recipeMap.equals(c.recipeMap);
    }

    // region Execution cores

    /** PerfectExecutionCore.done (:66-86): every run lasts 20 ticks. */
    static final int PERFECT_CORE_TICKS = 20;

    /**
     * The steady run of an execution-core machine at the card's settings: Perfect Execution Cores take every input
     * (unlimited parallels, no overclock, the recipe's EU/t x ticks a parallel from the network, 20 ticks); else the
     * controller, its Execution Cores and its Advanced ones as {@link TstCoresExecution} runs them.
     */
    @Nullable
    static Exact executionRun(final Context c) {
        if (c.recipe == null || !(c.poolEuT > 0)) return null;
        final long eut = recipeEut(c);
        final int ticks = recipeTicks(c);
        if (number(c, EXEC_PERFECT) > 0) return Exact.network(
            Integer.MAX_VALUE,
            PERFECT_CORE_TICKS,
            (double) eut * ticks / PERFECT_CORE_TICKS * Integer.MAX_VALUE);
        final int[] overclock = EXEC_OVERCLOCKS[Math.min(c.tier(EXEC_OVERCLOCK), EXEC_OVERCLOCKS.length - 1)];
        final float speed = 1F * (1F / Math.max(1, number(c, EXEC_SPEED)));
        final float euModifier = 1F * EXEC_POWER_MULTIPLIERS[Math.min(c.tier(EXEC_POWER), 8)];
        final int advanced = number(c, EXEC_ADVANCED);
        final TstCoresExecution.Result r = TstCoresExecution.run(
            new TstCoresExecution.Setup(
                totalEu(c),
                eut,
                ticks,
                euModifier,
                speed,
                overclock[0],
                overclock[1],
                Math.max(1, number(c, PARALLELS)),
                number(c, EXEC_CORES),
                advanced));
        if (r == null) return Exact.refused(number(c, EXEC_CORES) > 0 ? CORE_SHARE_GATE : POWER_GATE);
        final double euT = r.hatchEuT() + r.networkEuT();
        return advanced > 0 ? Exact.network(r.parallels(), r.duration(), euT)
            : Exact.of(r.parallels(), r.duration(), euT);
    }

    /** The excited fuels the DTPF Prototype MK-II discounts (valid_fuels, :222-224), by their fluid ids. */
    private static final List<String> EXCITED_FUELS = List
        .of("exciteddtec", "exciteddtrc", "exciteddtpc", "exciteddtcc", "exciteddtsc");

    /** max_efficiency_time_in_ticks (:215): a day. */
    private static final double FULL_DISCOUNT_TICKS = 3600d * 24d * 20d;

    /**
     * recipeAfterDiscount (:362-383): the recipe's first excited fuel input becomes round(amount x multiplier), the
     * multiplier max(0.25, 1 - run time / a day x 0.75) (MaxFuelDiscount_DTMPFP 0.25, pack cfg :509-511).
     */
    static double plasmaForgeFuel(final Context c, final Web.Resource input) {
        if (c.recipe == null || !"fluid".equals(input.kind) || !(input.amount > 0)) return 1;
        String first = null;
        for (final Web.Resource in : c.recipe.inputs) if ("fluid".equals(in.kind) && EXCITED_FUELS.contains(in.id)) {
            first = in.id;
            break;
        }
        if (first == null || !first.equals(input.id)) return 1;
        final double runningTime = (long) (c.value(DTPF_HOURS) * 3600 * 20);
        final double maximumDiscount = 0.25d;
        final double multiplier = runningTime > FULL_DISCOUNT_TICKS ? maximumDiscount
            : Math.max(maximumDiscount, 1 - Math.min(runningTime / FULL_DISCOUNT_TICKS, 1.0d) * (1d - maximumDiscount));
        return (int) Math.round((int) input.amount * multiplier) / input.amount;
    }

    // endregion

    // region Thaumcraft

    /** The clamp both machines' speed stops at, where they overclock perfectly. */
    private static final double FASTEST = (double) 1 / 11.4514;

    /**
     * GT_TileEntity_IndustrialMagicMatrix (0.8.1): cell tier << 3 parallels (:389) or Integer.MAX_VALUE with a Proof
     * of Heroes (:431-436); the node speed (countSpeedBonus :601-644) as a float, perfect at the clamp (:447-450); the
     * run lasts the GT duration plus 20 ticks per primal no node brings (:562-566), one tick with a Proof of Heroes.
     */
    @Nullable
    static Exact magicMatrix(final Context c) {
        if (c.recipe == null || !(c.poolEuT > 0)) return null;
        final boolean proof = c.tier(CONTROLLER_SLOT) == 1;
        final int maxParallel = proof ? Integer.MAX_VALUE : (c.tier(ESSENTIA_CELL) + 1) << 3;
        final int[] amounts = { number(c, AER), number(c, IGNIS), number(c, TERRA), number(c, AQUA), number(c, ORDO),
            number(c, PERDITIO) };
        final double speed = matrixSpeed(number(c, NODES), amounts);
        final TstCoresGt.Run r = thaumRun(c, maxParallel, speed);
        if (r == null) return Exact.refused(POWER_GATE);
        return Exact.of(r.parallels(), proof ? 1 : r.duration() + matrixExtraTicks(number(c, NODES), amounts), r.eut());
    }

    /**
     * countSpeedBonus (:601-644): 1 without nodes, (6 - nodes) x 1.75 short of six, 1 when a primal is missing, else
     * 1 / (1 + (0.4 + (0.45e)^(-0.005 Var) + 0.15 log2(1 + e^-Var)) x Mean / 500), no less than 1 / 11.4514; Mean and
     * Var (population) of the six primals' amounts, Aer, Ignis, Terra, Aqua, Ordo, Perditio.
     */
    static double matrixSpeed(final int nodes, final int[] amounts) {
        if (nodes == 0) return 1;
        if (nodes < 6) return (6 - nodes) * 1.75;
        if (matrixExtraTicks(nodes, amounts) > 0) return 1;
        double sum = 0;
        for (final int a : amounts) sum += a;
        final double mean = sum / amounts.length;
        double squares = 0;
        for (final int a : amounts) squares += (a - mean) * (a - mean);
        final double variance = squares / amounts.length;
        final double e = Math.E;
        final double ln1 = Math.log(1 + Math.pow(e, -variance));
        final double ln2 = Math.log(2);
        final double bonus = 1 + ((0.4 + Math.pow(0.45 * e, -0.005 * variance) + 0.15 * (ln1 / ln2)) * mean / 500);
        return Math.max(1 / bonus, 1 / 11.4514);
    }

    /** ExtraTime (:625-628): 20 ticks for each primal no node brings, with all six nodes. */
    static int matrixExtraTicks(final int nodes, final int[] amounts) {
        if (nodes < 6) return 0;
        int penalize = 0;
        for (final int a : amounts) if (a <= 0) penalize++;
        return penalize * 20;
    }

    /**
     * TST_IndustrialAlchemyTower (0.8.1): cell tier ^ 5 parallels (:465-467) or Integer.MAX_VALUE with a Proof of
     * Heroes (:297-303); the node speed (countSpeedBonus :415-435) as a float, perfect at the clamp (:312-315).
     */
    @Nullable
    static Exact alchemyTower(final Context c) {
        if (c.recipe == null || !(c.poolEuT > 0)) return null;
        final int maxParallel = c.tier(CONTROLLER_SLOT) == 1 ? Integer.MAX_VALUE
            : (int) Math.pow(c.tier(ESSENTIA_CELL) + 1, 5);
        final double speed = towerSpeed(
            number(c, NODES),
            number(c, IGNIS),
            number(c, AER),
            number(c, ORDO),
            number(c, PERDITIO));
        final TstCoresGt.Run r = thaumRun(c, maxParallel, speed);
        return r == null ? Exact.refused(POWER_GATE) : Exact.of(r.parallels(), r.duration(), r.eut());
    }

    /**
     * countSpeedBonus (:415-435): 1 without nodes, 1 - 0.1 a node short of four; with four, from the least and most
     * of Ignis, Aer, Ordo, Perditio and 10: base 1/11.4514 + (0.7 - 1/11.4514)(1 - e^(min - 10)), plus (0.7 - base) x
     * (max - min) / max.
     */
    static double towerSpeed(final int nodes, final int ignis, final int aer, final int ordo, final int perditio) {
        if (nodes == 0) return 1.0;
        if (nodes < 4) return 1 - nodes * 0.1;
        final List<Integer> data = List.of(ignis, aer, ordo, perditio, 10);
        final int min = java.util.Collections.min(data);
        final int max = java.util.Collections.max(data);
        final double base = 1 / 11.4514 + (0.7 - 1 / 11.4514) * (1 - Math.pow(Math.E, min - 10));
        final double punish = (0.7 - base) * (max - min) / max;
        return base + punish;
    }

    /** GT's ProcessingLogic on the hatches (amperage overclocks), the speed as the float the machine hands it. */
    @Nullable
    private static TstCoresGt.Run thaumRun(final Context c, final int maxParallel, final double speed) {
        final boolean perfect = speed == FASTEST;
        return TstCoresGt.process(
            new TstCoresGt.Setup().voltage(totalEu(c))
                .maxParallel(maxParallel)
                .speedBoost((float) speed)
                .overclock(perfect ? 4 : 2, 4),
            recipeEut(c),
            recipeTicks(c));
    }

    /** Thaumcraft's Primordial Pearl (ItemEldritchObject meta 3). */
    private static final String PRIMORDIAL_PEARL = "thaumcraft:itemeldritchobject@3";

    /**
     * checkProcessing (:568-588): each run gives back one Primordial Pearl per pearl the infusion takes, whatever its
     * parallels, so a run of P parallels spends P - 1 of each P (not for Avaritia's big pearl).
     */
    static double primordialPearl(final Context c, final Web.Resource input) {
        if (!PRIMORDIAL_PEARL.equals(input.id) || c.recipe == null) return 1;
        if (c.recipe.outputs != null) for (final Web.Resource out : c.recipe.outputs)
            if (out.id != null && out.id.endsWith("big_pearl")) return 1;
        final Exact run = magicMatrix(c);
        if (run == null || !run.runs() || !(run.parallels() >= 1)) return 1;
        return (run.parallels() - 1) / run.parallels();
    }

    // endregion

    // region Hephaestus' Atelier

    /** ConsumeEutPerParallel and ConsumeEuPerSmelting (Config :324-326, pack cfg :258-264). */
    static final int HEPHAESTUS_EUT_PER_ITEM = 7, HEPHAESTUS_EU_PER_SMELT = 2048;

    private static int hephaestusTier(final Context c) {
        return c.tier(HEPHAESTUS_COIL) + 1;
    }

    /** DurationPerProcessing_T2Coil / T3Coil_Wireless (Config :327-328): 256 and 20 ticks. */
    private static int hephaestusWirelessTicks(final Context c) {
        return hephaestusTier(c) > 2 ? 20 : 256;
    }

    /**
     * The higher rings pay the network (isWirelessMode :321-323): smelting 2048 EU an item (:421-464), alloy smelting
     * EU/t x duration a parallel with no overclock (:278-281, :331-389), both over the fixed cycle.
     */
    private static double hephaestusPower(final Context c) {
        if (hephaestusTier(c) < 2) return 1;
        final double ticks = hephaestusWirelessTicks(c);
        if (on(c, FURNACE)) return HEPHAESTUS_EU_PER_SMELT / ticks / HEPHAESTUS_EUT_PER_ITEM;
        return recipeTicks(c) / ticks;
    }

    // endregion

    // region Ball Lightning

    /** TierEU.RECIPE_MAX: VP[14] = (Integer.MAX_VALUE - 7) x 30 / 32. */
    static final long RECIPE_MAX = (Integer.MAX_VALUE - 7L) * 30 / 32;

    /** Fusion parallels (getMaxParallelRecipes :445-450). */
    static final int FUSION_PARALLELS = 65536;

    private static void ballLightning(final Map<String, Behaviour> machines) {
        final Behaviour ball = machine()
            .overclock(c -> on(c, FUSION) ? none() : !on(c, STAR_KERNEL) && ballField(c) > 2 ? perfect() : normal())
            .speed(c -> {
                if (on(c, FUSION)) return ballFusion(c).speed(c);
                final int lead = ballTier(c) - (on(c, STAR_KERNEL) ? 3 : 0);
                return speedOf(lead > 1 ? (float) Math.pow(0.25, lead - 1) : 1);
            })
            .power(c -> {
                if (on(c, FUSION)) return ballFusion(c).power(c);
                return on(c, STAR_KERNEL) ? (float) (1 - 0.099 * (ballField(c) - 1)) : 1;
            })
            .parallels(
                c -> on(c, STAR_KERNEL) ? Integer.MAX_VALUE
                    : on(c, FUSION) ? ballFusion(c).parallels()
                        : (int) Math.pow(2, (c.tier(FUSION_COIL) + 1) * coil(c)))
            .quantiseDuration(d -> {
                // A fusion run's whole ticks come back through the speed as a double: snap that noise, then GT's rule.
                final double whole = Math.round(d);
                final boolean noise = Math.abs(d - whole) < 1e-9 * Math.max(1, whole);
                return Overclock.quantiseDurationToTicks(noise ? whole : d, true);
            })
            .controls(BALL_TIER_CONTROL, FUSION_COIL_CONTROL, INFINITY_COIL_CONTROL, BALL_FIELD_CONTROL, GLASS_CONTROL)
            .recipeGate(c -> {
                if (on(c, STAR_KERNEL) && ballTier(c) < 4) return BALL_TIER_GATE;
                if (on(c, FUSION)) {
                    if (ballTier(c) < 3) return BALL_TIER_GATE;
                    if (fusionRecipeTier(c) > c.tier(FUSION_COIL) + 1) return FUSION_COIL_GATE;
                    final String why = ballFusion(c).refusal();
                    if (why != null) return why;
                }
                return glassGate(c);
            });
        // A fusion run is worked out whole: its parallels are already paid.
        final MachineTable.Coefficient structure = ball.parallels, power = ball.power;
        ball.parallels(
            c -> on(c, FUSION) ? MachineTable.resolve(structure, c, 1)
                : paid(c, MachineTable.resolve(structure, c, 1), MachineTable.resolve(power, c, 1)));
        machines.put("BallLightning", ball.hidesControls(MAP_CONTROLS));
    }

    private static int ballTier(final Context c) {
        return c.tier(BALL_TIER) + 1;
    }

    private static int ballField(final Context c) {
        return c.tier(BALL_FIELD) + 1;
    }

    /**
     * A fusion run (machine mode 2, 0.8.1 :445-459, :510-531, :623-627): 65536 parallels, perfect overclocks, 0.25 at
     * machine tier 4; on FusionMaxEut = RECIPE_MAX x 4^(compact coil - 2) x 1.8^(field - 1) at 1 A without amperage
     * overclocks when the hatches carry more, else on the hatches.
     */
    static Exact ballFusion(final Context c) {
        if (c.recipe == null || !(c.poolEuT > 0)) return Exact.of(1, Math.max(1, recipeTicks(c)), recipeEut(c));
        final int compactCoil = c.tier(FUSION_COIL) + 1;
        final long fusionMaxEut = (long) (RECIPE_MAX
            * (Math.pow(4, (compactCoil - 2)) * Math.pow(1.8, ballField(c) - 1)));
        final int lead = ballTier(c) - 2;
        final float speed = lead > 1 ? (float) Math.pow(0.25, lead - 1) : 1;
        final long hatches = totalEu(c);
        final TstCoresGt.Setup setup = new TstCoresGt.Setup().maxParallel(FUSION_PARALLELS)
            .speedBoost(speed)
            .overclock(4, 4);
        if (fusionMaxEut < hatches) setup.voltage(fusionMaxEut)
            .amperageOC(false);
        else setup.voltage(hatches);
        final TstCoresGt.Run r = TstCoresGt.process(setup, recipeEut(c), recipeTicks(c));
        return r == null ? Exact.refused(POWER_GATE) : Exact.of(r.parallels(), r.duration(), r.eut());
    }

    /** getFusionRecipeTier (:613-621) of the recipe's special value and voltage tier. */
    static int fusionRecipeTier(final Context c) {
        final long startUp = recipeSpecial(c);
        final int recipeTier = gtTier(recipeEut(c));
        if (startUp < 160000000) return 1;
        if (startUp < 320000000) return 2;
        if (startUp < 640000000) return 3;
        if (startUp > 640000000 && recipeTier < 10) return 4;
        if (startUp > 640000000 && recipeTier == 10) return 5;
        if (recipeTier > 10) return 6;
        return 10;
    }

    // endregion
}
