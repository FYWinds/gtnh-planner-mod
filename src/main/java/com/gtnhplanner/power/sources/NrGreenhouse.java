package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerModel.Stat;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * kubatech's Extreme Industrial Greenhouse (MTEExtremeIndustrialGreenhouse, GT 5.09.54.265), worked from its
 * checkProcessing and EIGNormalMode, the only mode this version has. One card is one greenhouse growing one seed type.
 * What a seed drops is the game's own: its bucket (EIGModes.Normal.tryCreateNewBucket, the factories the game uses),
 * read by power/game/NrGreenhouseGame.
 */
public final class NrGreenhouse {

    /** The source id, stored in plans: never rename it. */
    public static final String ID = "extreme-industrial-greenhouse";
    /** MetaTileEntityIDs.ExtremeIndustrialGreenhouseController, the card's picture. */
    public static final String MACHINE = "gregtech:gt.blockmachines@12792";

    /** The fertilizer select's "none". */
    public static final String NONE = "none";

    /** EIG_BALANCE_REGULAR_MODE_MIN_TIER (VoltageIndex.EV): EIGNormalMode.getSlotCount gives no slot below it. */
    static final int MIN_TIER = 4;
    /** EIGNormalMode.getSeedCapacityPerSlot. */
    static final int SEEDS_PER_SLOT = 64;
    /** checkProcessing: waterUsage = seedCount * 1000 (EIG_BALANCE_WATER_USAGE_PER_SEED). */
    static final int WATER_PER_SEED = 1_000;
    /** EIG_BALANCE_WEED_EX_USAGE_BEGINS_AT: below it no Weed-EX is used. */
    static final int WEED_EX_FROM = 1_000;
    /** EIGNormalMode.getWeedEXMultiplier: litres a seed a cycle from there on. */
    static final int WEED_EX_PER_SEED = 1;
    /** EIGNormalMode.getMaxFertilizerUsagePerSeed. */
    static final int FERTILIZER_PER_SEED = 2;
    /**
     * EIG_BALANCE_MAX_FERTILIZER_BOOST: the multiplier is 1 + used / most x 4, so full fertilizer is x5 (the mode's own
     * getFertilizerBoost, 2.0, is only in its tooltip).
     */
    static final double FERTILIZER_BOOST = 4.0;
    /** checkProcessing: lEUt = -(long) (V[tier] * 0.99). */
    static final double EU_SHARE = 0.99;
    /** The most seeds the setting takes: the slots of the game's last tier (15). */
    private static final int MOST_SEEDS = SEEDS_PER_SLOT << (15 - MIN_TIER);

    /** The greenhouse's seeds and what they grow, from the running game (or a test's own). */
    public interface Data {

        /**
         * The seeds it could take, keyed by {@link NrResources} item flow, labelled with the game's names, in order:
         * flowers, cacti and reeds it was seen to take, and every seed or stem item, which it may still refuse.
         */
        List<Option> seeds();

        /**
         * What one seed grows in a cycle at no fertilizer (its bucket's addProgress(1, table)), by item flow; null when
         * the greenhouse refuses the seed (no factory makes a valid bucket of it).
         */
        @Nullable
        Map<String, Double> drops(String seed);

        /** The fertilizers it takes (MTEExtremeIndustrialGreenhouse.FERTILIZER_ITEM_LIST), as item flows. */
        List<Option> fertilizers();

        /** Water's fluid flow. */
        String water();

        /** Weed-EX 9000's fluid flow. */
        String weedEx();
    }

    // region the game's maths

    /**
     * KubaTechGTMultiBlockBase.getVoltageTier: {@code (int) (ln(maxInputEu / 8) / ln 4 + 1e-8)}, so amps count: two
     * 2 A EV hatches (8,192 EU/t) run it at IV.
     */
    public static int voltageTier(final long maxInputEu) {
        return (int) (Math.log((double) maxInputEu / 8d) / Math.log(4d) + 1e-8d);
    }

    /** EIGNormalMode.getSlotCount: one seed type at EV, doubling each tier; none below EV. */
    public static int slots(final int tier) {
        final int above = tier - MIN_TIER;
        return above < 0 ? 0 : 1 << above;
    }

    /** checkProcessing: mMaxProgresstime = max(20, 100 / (tier - 3)), from EV up. */
    public static int cycleTicks(final int tier) {
        return Math.max(20, 100 / (tier - 3));
    }

    /** checkProcessing's draw at a tier. */
    public static long euPerTick(final int tier) {
        return (long) ((double) NrProcess.V[tier] * EU_SHARE);
    }

    /** checkProcessing: water a cycle, seedCount * 1000. */
    public static long waterPerCycle(final int seeds) {
        return (long) seeds * WATER_PER_SEED;
    }

    /** checkProcessing: Weed-EX a cycle, the seed count from 1,000 seeds up, else none. */
    public static long weedExPerCycle(final int seeds) {
        return seeds >= WEED_EX_FROM ? (long) seeds * WEED_EX_PER_SEED : 0;
    }

    /** checkProcessing: the growth multiplier for the share of the most fertilizer it can use (0 to 1). */
    public static double multiplier(final double fertilizerShare) {
        return 1.d + fertilizerShare * FERTILIZER_BOOST;
    }

    /**
     * Thaumic Bases' BlockRainbowCactus.addDyeDropsToOutput: {@code 3 + round(nextDouble() * 4.91662)} dyes, each one
     * of
     * its allowedDyes at random. round(4.91662 u) averages {@code 5 - 12.5 / 4.91662}, so a cactus drops 5.4576 dyes a
     * cycle; this is one kind's share of them.
     */
    public static double rainbowDyesEach(final int dyeKinds) {
        final double count = 3 + 5 - 12.5 / 4.91662;
        return dyeKinds <= 0 ? 0 : count / dyeKinds;
    }

    // endregion

    public static PowerSource source(final Data data) {
        final List<Option> seeds = data.seeds();
        String seedDefault = seeds.get(0)
            .key();
        final String wheat = NrResources.item("minecraft:wheat_seeds");
        for (final Option seed : seeds) if (seed.key()
            .equals(wheat)) seedDefault = wheat;
        final List<Option> fertilizers = new ArrayList<>();
        fertilizers.add(new Option(NONE, "None"));
        fertilizers.addAll(data.fertilizers());
        return new PowerSource(
            ID,
            "Extreme Industrial Greenhouse",
            PowerGroup.OTHER,
            // A crafting-table recipe whose top part is a ZPM circuit (kubatech RecipeLoader).
            "ZPM",
            "Grows one seed type on water and power. Fertilizer makes it five times as much.",
            List.of(
                new PowerSetting.Select("seed", "Seed", seeds, seedDefault),
                new PowerSetting.Number("seeds", "Seeds planted (0 fills it)", 0, MOST_SEEDS, 1, 0, null, null),
                NrProcess.tierSetting("EV"),
                NrProcess.ampsSetting(),
                new PowerSetting.Select("fertilizer", "Fertilizer", fertilizers, NONE)),
            read -> compute(data, read));
    }

    /** One greenhouse a second, at its settings. */
    static PowerModel compute(final Data data, final SettingsReader read) {
        final int tier = voltageTier(NrProcess.maxInputEu(read));
        final List<Stat> stats = new ArrayList<>();
        stats.add(stat("Voltage tier", NrProcess.tierName(tier)));
        // checkMachine: the glass must be at least every energy hatch's tier.
        stats.add(stat("Glass tier", read.select(NrProcess.TIER)));
        if (tier < MIN_TIER) return idle(stats, "Below EV the greenhouse has no room for seeds, so it does nothing.");
        // GTValues.V has no tier past 15: the game would fail reading it.
        if (tier >= NrProcess.V.length)
            return idle(stats, "That much power is past the game's last voltage tier: the greenhouse cannot run.");
        final int capacity = slots(tier) * SEEDS_PER_SLOT;
        final int asked = (int) Math.floor(read.number("seeds"));
        final int seeds = asked <= 0 ? capacity : asked;
        final int ticks = cycleTicks(tier);
        stats.add(stat("Seed capacity", number(capacity)));
        stats.add(stat("Seeds planted", number(seeds)));
        stats.add(stat("Cycle", number(ticks / 20.0) + " s"));
        // checkProcessing: EIG_seedOverflow when the seeds are more than the slots hold.
        if (seeds > capacity)
            return idle(stats, "More seeds than the greenhouse holds at this voltage: it will not run.");
        final Map<String, Double> drops = data.drops(read.select("seed"));
        if (drops == null || drops.isEmpty()) return idle(stats, "The greenhouse does not take this seed.");
        final String fertilizer = read.select("fertilizer");
        final boolean fertilized = !NONE.equals(fertilizer);
        final double boost = multiplier(fertilized ? 1 : 0);
        stats.add(stat("Fertilizer boost", "x" + number(boost)));
        final double perSecond = 20.0 / ticks;
        final List<Flow> inputs = new ArrayList<>();
        inputs.add(liters(data.water(), waterPerCycle(seeds) * perSecond));
        final long weedEx = weedExPerCycle(seeds);
        if (weedEx > 0) inputs.add(liters(data.weedEx(), weedEx * perSecond));
        if (fertilized) inputs.add(items(fertilizer, (double) seeds * FERTILIZER_PER_SEED * perSecond));
        // Each bucket adds its table times the multiplier and its seed count (EIGSeedBucket.addProgress); the drop
        // tracker keeps the fractions, so over time the output is exact.
        final List<Flow> outputs = new ArrayList<>();
        for (final Map.Entry<String, Double> drop : drops.entrySet())
            outputs.add(items(drop.getKey(), drop.getValue() * boost * seeds * perSecond));
        outputs.sort(
            Comparator.comparingDouble(Flow::perSecond)
                .reversed());
        return new PowerModel(-euPerTick(tier), inputs, outputs, stats);
    }

    private static PowerModel idle(final List<Stat> stats, final String warning) {
        return new PowerModel(0, List.of(), List.of(), stats, List.of(warning));
    }

    private NrGreenhouse() {}
}
