package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Condition;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * TwistSpaceTechnology's Basic Steam Generator, Large Solar Boiler and Lightning Spire (the ETO fork; the website has
 * none of them), worked from TST 0.8.1's source (common/machine/GeneratorMultis) and GregTech 5.09.54.265's.
 * docs/design/tst-machines.md describes them.
 */
public final class TstSteamGenerators {

    /** Source ids, stored in plans: never rename one. */
    public static final String BASIC_STEAM_GENERATOR = "tst-basic-steam-generator";
    public static final String LARGE_SOLAR_BOILER = "tst-large-solar-boiler";
    public static final String LIGHTNING_SPIRE = "tst-lightning-spire";

    /** The controller a source is drawn as (MachineLoader's MetaTileEntity ids), or null for another source. */
    @Nullable
    public static String controller(@Nullable final String sourceId) {
        if (BASIC_STEAM_GENERATOR.equals(sourceId)) return "gregtech:gt.blockmachines@19073";
        if (LARGE_SOLAR_BOILER.equals(sourceId)) return "gregtech:gt.blockmachines@19076";
        if (LIGHTNING_SPIRE.equals(sourceId)) return "gregtech:gt.blockmachines@19053";
        return null;
    }

    /** A dynamo voltage the card offers: GregTech's V[tier]. */
    private record DynamoTier(String name, long voltage) {}

    private static DynamoTier tier(final List<DynamoTier> tiers, final String name) {
        for (final DynamoTier tier : tiers) {
            if (tier.name()
                .equals(name)) return tier;
        }
        return tiers.get(tiers.size() - 1);
    }

    private static List<Option> tierOptions(final List<DynamoTier> tiers) {
        final List<Option> options = new ArrayList<>();
        for (final DynamoTier tier : tiers) options.add(new Option(tier.name(), tier.name()));
        return options;
    }

    // region Basic Steam Generator

    /**
     * TST_SteamBasicGenerator.getSteamPerSecond: STEAM_PER_SEC_PER_EUT_NUMERATOR / _DENOMINATOR, in longs, of the
     * dynamos' summed EU/t.
     */
    private static final long STEAM_PER_SEC_PER_EUT_NUMERATOR = 225;
    private static final long STEAM_PER_SEC_PER_EUT_DENOMINATOR = 8;
    /** checkProcessing: every run lasts 20 ticks and drains one second's steam at its start. */
    private static final int STEAM_RUN_TICKS = 20;
    /**
     * hasValidDynamos takes one to eight LV hatches (checkHatchMax 8, setDynamoTier(1, true)), each of at most 4 A
     * (GregTech's Dynamo element, MTEMultiBlockBase.addDynamoToMachineList: the 1 A hatch, GT++'s 4 A LV buffer
     * dynamo): 8 x 4.
     */
    private static final int STEAM_MAX_LV_AMPS = 32;
    /** Steam at 0.5 EU a litre (MTELargeTurbineSteam: flow x efficiency x 0.5): the tooltip's 142% is against it. */
    private static final double STEAM_EU_PER_LITRE = 0.5;

    /**
     * The dynamo builds hasValidDynamos allows, at GregTech's V[tier]: LV hatches (getTierForStructure 1) only, or one
     * single MV or HV hatch of exactly 1 A (maxAmperesOut 1).
     */
    private static final List<DynamoTier> STEAM_DYNAMOS = List
        .of(new DynamoTier("LV", 32), new DynamoTier("MV", 128), new DynamoTier("HV", 512));

    /** Steam drained a run (a second): the game's {@code eut * 225 / 8} in longs. */
    public static long steamPerSecond(final long euPerTick) {
        return euPerTick * STEAM_PER_SEC_PER_EUT_NUMERATOR / STEAM_PER_SEC_PER_EUT_DENOMINATOR;
    }

    private static PowerSource basicSteamGenerator() {
        return new PowerSource(
            BASIC_STEAM_GENERATOR,
            "Basic Steam Generator",
            PowerGroup.TURBINES,
            // GTCMMachineRecipes: an LV hull, LV steam turbines and an LV input hatch.
            "LV",
            "Turns steam into its dynamos' EU at 0.71 EU a litre.",
            List.of(
                new PowerSetting.Select(
                    "dynamoTier",
                    "Dynamo",
                    List.of(
                        new Option("LV", "LV, 1 to 8 hatches"),
                        new Option("MV", "MV, one 1 A hatch"),
                        new Option("HV", "HV, one 1 A hatch")),
                    "HV"),
                new PowerSetting.Number(
                    "amps",
                    "Dynamo amps",
                    1,
                    STEAM_MAX_LV_AMPS,
                    1,
                    1,
                    "A",
                    new Condition("dynamoTier", "LV"))),
            TstSteamGenerators::computeSteamGenerator);
    }

    /**
     * checkProcessing: each 20-tick run makes the dynamos' summed voltage x amps (getDynamoOutputEUt) at full
     * efficiency (getMaxEfficiency 10,000) and drains {@link #steamPerSecond} of plain Steam (MiscHelper.steam) at its
     * start. A finished run starts the next on the same tick (MTEMultiBlockBase.runMachine).
     */
    private static PowerModel computeSteamGenerator(final SettingsReader read) {
        final DynamoTier dynamo = tier(STEAM_DYNAMOS, read.select("dynamoTier"));
        final long amps = "LV".equals(dynamo.name()) ? (long) Math.floor(read.number("amps")) : 1;
        final long euPerTick = dynamo.voltage() * amps;
        final long steam = steamPerSecond(euPerTick);
        final double euPerLitre = (double) euPerTick * STEAM_RUN_TICKS / steam;
        return new PowerModel(
            euPerTick,
            List.of(liters("Steam", steam)),
            List.of(),
            List.of(
                stat("Burn", number(steam) + " L every " + STEAM_RUN_TICKS + " ticks"),
                stat("EU per L", formatAmount(euPerLitre)),
                stat("Efficiency", percent(euPerLitre / STEAM_EU_PER_LITRE))));
    }

    // endregion

    // region Large Solar Boiler

    /** steamProductionBronze and steamProductionSteel: L/s at full heat (machineTier 1 and 2). */
    private record BoilerTier(String key, String label, int steamPerSecond) {}

    private static final List<BoilerTier> BOILER_TIERS = List.of(
        new BoilerTier("bronze", "Bronze (4,800 L/s)", 4_800),
        new BoilerTier("steel", "Steel (14,400 L/s)", 14_400));

    /** GTValues.STEAM_PER_WATER. */
    private static final int STEAM_PER_WATER = 160;
    /** onPostTick: heat rises this much each sunny second, capped at 1. */
    private static final double HEAT_INCREASE_SPEED = 0.001;
    /** checkProcessing: calcification rises by this much a step, capped at 1, on plain water only. */
    private static final double CALCIFICATION_STEP = 0.01;
    /** calcificationFactor: full calcification divides the water (and steam) by three. */
    private static final int CALCIFICATION_FACTOR = 3;
    /** calcificationDelayTicks: no calcification until the run count passes 24 hours of runs. */
    private static final long CALCIFICATION_DELAY_TICKS = 20L * 60 * 60 * 24;
    /** calcificationTimeSeconds / 100: one step every 1,296 runs (seconds of run time) after the delay. */
    private static final long CALCIFICATION_STEP_RUNS = 60L * 60 * 36 / 100;
    private static final String NO_STEAM = "Too cold: under 1 L of water a run, so no steam.";
    private static final String CALCIFIES = "Regular water calcifies this boiler: full %s L/s for %s hours of run time, "
        + "then down to %s L/s by %s hours. Distilled water does not.";

    /** The heat a cold boiler has after this many sunny seconds: the game's sum of 0.001 steps, capped at 1. */
    public static double heatAfter(final int sunnySeconds) {
        double heat = 0;
        for (int i = 0; i < sunnySeconds; i++) {
            heat += HEAT_INCREASE_SPEED;
            if (heat > 1) heat = 1;
        }
        return heat;
    }

    /** The calcification after this many steps: the game's sum of 0.01 steps, capped at 1. */
    public static double calcificationAfter(final int steps) {
        double calcification = 0;
        for (int i = 0; i < steps; i++) calcification = Math.min(1, calcification + CALCIFICATION_STEP);
        return calcification;
    }

    /**
     * Water boiled a run: checkProcessing's
     * {@code (int) (min(hatch, steamProduction / STEAM_PER_WATER) * heat / (calcification * (factor - 1) + 1))}, the
     * hatch holding at least a run's water.
     */
    public static int waterPerRun(final int steamPerSecond, final double heat, final double calcification) {
        final int water = steamPerSecond / STEAM_PER_WATER;
        return (int) (water * heat / ((calcification * (CALCIFICATION_FACTOR - 1)) + 1));
    }

    /** The run whose end brings the first calcification step: past the delay, on a multiple of the step. */
    private static long firstCalcifyingRun() {
        return (CALCIFICATION_DELAY_TICKS / STEAM_RUN_TICKS / CALCIFICATION_STEP_RUNS + 1) * CALCIFICATION_STEP_RUNS;
    }

    /** Steps from clean to full calcification. */
    private static int stepsToFull() {
        int steps = 0;
        while (calcificationAfter(steps) < 1) steps++;
        return steps;
    }

    private static PowerSource largeSolarBoiler() {
        final List<Option> tiers = new ArrayList<>();
        for (final BoilerTier tier : BOILER_TIERS) tiers.add(new Option(tier.key(), tier.label()));
        return new PowerSource(
            LARGE_SOLAR_BOILER,
            "Large Solar Boiler",
            PowerGroup.STEAM,
            // GTCMMachineRecipes: a Simple Solar Boiler, reinforced glass (an alloy smelter at 4 EU/t), bronze parts.
            "ULV",
            "Up to 14,400 L/s of steam from the sun. Calcifies on regular water.",
            List.of(
                new PowerSetting.Select("model", "Boiler", tiers, "bronze"),
                new PowerSetting.Select(
                    "waterKind",
                    "Water supply",
                    List.of(new Option("Water", "Water"), new Option("Distilled Water", "Distilled Water")),
                    "Water"),
                new PowerSetting.Number("heat", "Heat", 0, 100, 0.1, 100, "%", null),
                new PowerSetting.Number(
                    "calcification",
                    "Calcification",
                    0,
                    100,
                    1,
                    0,
                    "%",
                    new Condition("waterKind", "Water"))),
            TstSteamGenerators::computeSolarBoiler);
    }

    /**
     * checkProcessing: each 20-tick run boils {@link #waterPerRun} of plain or distilled water into 160 L of Steam a
     * litre, by night as by day: only the heat scales it. Heat is a setting, read as the heat a cold boiler has after
     * that many tenths of a percent of sunny seconds (onPostTick); calcification, which only plain water raises, is a
     * setting of whole 1% steps.
     */
    private static PowerModel computeSolarBoiler(final SettingsReader read) {
        BoilerTier tier = BOILER_TIERS.get(0);
        for (final BoilerTier row : BOILER_TIERS) {
            if (row.key()
                .equals(read.select("model"))) tier = row;
        }
        final String waterKind = read.select("waterKind");
        final boolean onWater = "Water".equals(waterKind);
        final double heat = heatAfter((int) Math.round(read.number("heat") * 10));
        final double calcification = onWater ? calcificationAfter((int) Math.floor(read.number("calcification"))) : 0;
        final int water = waterPerRun(tier.steamPerSecond(), heat, calcification);
        final long steam = (long) water * STEAM_PER_WATER;
        final List<Flow> inputs = new ArrayList<>();
        final List<Flow> outputs = new ArrayList<>();
        if (water > 0) {
            inputs.add(liters(waterKind, water));
            outputs.add(liters("Steam", steam));
        }
        final List<String> warnings = new ArrayList<>();
        if (water <= 0) {
            warnings.add(NO_STEAM);
        } else if (onWater) {
            // Run k (from 0) boils at the calcification before its own step, taken when k x 20 ticks is past the
            // delay and k is a multiple of 1,296: the first step ends run 86,832, the last 99 steps later.
            final long fullRuns = firstCalcifyingRun() + 1;
            final long minRuns = firstCalcifyingRun() + (stepsToFull() - 1) * CALCIFICATION_STEP_RUNS + 1;
            final long fullSteam = (long) waterPerRun(tier.steamPerSecond(), heat, 0) * STEAM_PER_WATER;
            final long minSteam = (long) waterPerRun(tier.steamPerSecond(), heat, 1) * STEAM_PER_WATER;
            warnings.add(
                String.format(
                    CALCIFIES,
                    number(fullSteam),
                    formatAmount(fullRuns / 3600.0),
                    number(minSteam),
                    formatAmount(minRuns / 3600.0)));
        }
        return new PowerModel(
            0,
            inputs,
            outputs,
            List.of(
                stat("Steam", formatAmount(steam / (double) STEAM_RUN_TICKS) + " L/t"),
                stat("Needs", "Open sky"),
                stat("Dry hatch", "Refilling above 50% heat explodes")),
            warnings);
    }

    // endregion

    // region Lightning Spire

    /** GTCM_LightningSpire.MAXRODS: HV Lightning Rods stored. */
    private static final int MAX_RODS = 512;
    /** checkInputRods: tProductEU, EU a strike per rod. */
    private static final long EU_PER_ROD_STRIKE = 28_000_000L;
    /** checkInputRods: tMaxStoredEU per rod. */
    private static final long STORE_PER_ROD = 280_000_000L;
    /** checkProcessing: CRYOTHEUM_CONSUMPTION, litres a strike. */
    private static final int CRYOTHEUM_PER_STRIKE = 128;
    /** checkProcessing: moltenIronConsumption = tRods * 72, litres a strike. */
    private static final int MOLTEN_IRON_PER_ROD = 72;
    /** checkProcessing: mMaxProgresstime, ticks a strike. */
    private static final int STRIKE_TICKS = 256;
    /** Over the sum of every hatch: laser source hatches reach 16,777,216 A, which carries any spire at ULV. */
    private static final int MAX_SPIRE_AMPS = 16_777_216;
    private static final String DYNAMOS_CARRY = "The dynamos carry %s EU/t of the %s EU/t the strikes make; "
        + "the rest is lost.";

    /**
     * Every dynamo the structure takes: TST_GeneratorBase.addDynamoToMachineList takes any MTEHatchDynamo and puts the
     * multi-amp and laser source ones (MTEHatchDynamoMulti) with the exotic hatches; GregTech's hatches run ULV to UXV
     * (GTValues.V).
     */
    private static final List<DynamoTier> SPIRE_DYNAMOS = List.of(
        new DynamoTier("ULV", 8L),
        new DynamoTier("LV", 32L),
        new DynamoTier("MV", 128L),
        new DynamoTier("HV", 512L),
        new DynamoTier("EV", 2_048L),
        new DynamoTier("IV", 8_192L),
        new DynamoTier("LuV", 32_768L),
        new DynamoTier("ZPM", 131_072L),
        new DynamoTier("UV", 524_288L),
        new DynamoTier("UHV", 2_097_152L),
        new DynamoTier("UEV", 8_388_608L),
        new DynamoTier("UIV", 33_554_432L),
        new DynamoTier("UMV", 134_217_728L),
        new DynamoTier("UXV", 536_870_912L));

    private static PowerSource lightningSpire() {
        return new PowerSource(
            LIGHTNING_SPIRE,
            "Lightning Spire",
            PowerGroup.ENGINES,
            // GTCMMachineRecipes: an assembler recipe at IV.
            "IV",
            "Lightning rods turn cryotheum and molten iron into 28M EU a strike each.",
            List.of(
                new PowerSetting.Number("rods", "Lightning rods", 1, MAX_RODS, 1, MAX_RODS),
                new PowerSetting.Select("dynamoTier", "Dynamo tier", tierOptions(SPIRE_DYNAMOS), "UV"),
                new PowerSetting.Number("amps", "Dynamo amps", 1, MAX_SPIRE_AMPS, 1, 256, "A", null)),
            TstSteamGenerators::computeSpire);
    }

    /**
     * checkProcessing: a strike every 256 ticks drains exactly 128 L of Cryotheum and 72 L of Molten Iron a rod (the
     * hatches must hold exactly that, or the spire stops) and adds rods x 28M EU to a store of rods x 280M EU
     * (MoreSafetyPowerGeneration_LightningSpire is false in the pack: at once). onRunningTick tops the dynamos up from
     * the store every tick, so they carry at most their voltage x amps; what they cannot carry fills the store and is
     * lost.
     */
    private static PowerModel computeSpire(final SettingsReader read) {
        final long rods = (long) Math.floor(read.number("rods"));
        final DynamoTier dynamo = tier(SPIRE_DYNAMOS, read.select("dynamoTier"));
        final long amps = (long) Math.floor(read.number("amps"));
        final long perStrike = rods * EU_PER_ROD_STRIKE;
        // 28,000,000 / 256 = 109,375 EU/t a rod, exact.
        final long made = perStrike / STRIKE_TICKS;
        final long carried = dynamo.voltage() * amps;
        final long molten = rods * MOLTEN_IRON_PER_ROD;
        return new PowerModel(
            Math.min(made, carried),
            List.of(
                liters("Cryotheum", CRYOTHEUM_PER_STRIKE * 20.0 / STRIKE_TICKS),
                liters("Molten Iron", molten * 20.0 / STRIKE_TICKS)),
            List.of(),
            List.of(
                stat("Per strike", formatAmount(perStrike) + " EU"),
                stat("Strike every", number(STRIKE_TICKS / 20.0) + " s"),
                stat("Cryotheum per strike", number(CRYOTHEUM_PER_STRIKE) + " L"),
                stat("Molten Iron per strike", number(molten) + " L"),
                stat("Store", formatAmount(rods * STORE_PER_ROD) + " EU"),
                stat("Fluids", "Exact amounts, or it stops")),
            carried < made ? List.of(String.format(DYNAMOS_CARRY, number(carried), number(made))) : List.of());
    }

    // endregion

    private static List<PowerSource> sources;

    /** The Basic Steam Generator (Turbines), the Large Solar Boiler (Boilers) and the Lightning Spire (Engines). */
    public static synchronized List<PowerSource> sources() {
        if (sources == null) sources = List.of(basicSteamGenerator(), largeSolarBoiler(), lightningSpire());
        return sources;
    }

    private TstSteamGenerators() {}
}
