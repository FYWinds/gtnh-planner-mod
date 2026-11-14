package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * bartworks' Void Miners I, II and III (bwcrossmod.galacticgreg.MTEVoidMinerBase, MTEVoidMiners; GregTech
 * 5.09.54.265): ores out of nothing, drawn from the dimension's weighted table. The dimension, the noble gas and the
 * ore filter are settings; the table is the game's (NrData, read from the running game). docs/design/nr-sources.md.
 */
public final class NrMiners {

    /** Source ids, stored in plans: never rename one. */
    public static final String VOID_MINER_1 = "nr-void-miner-1", VOID_MINER_2 = "nr-void-miner-2",
        VOID_MINER_3 = "nr-void-miner-3";

    /** MTEVoidMinerBase.RECIPE_DURATION_TICKS: a run, one ore drawn, every 10 ticks. */
    static final int RECIPE_DURATION_TICKS = 10;
    /** MTEVoidMinerBase.MIN_TIER_OFFSET: a miner draws VP[TIER_MULTIPLIER + 5]. */
    static final int MIN_TIER_OFFSET = 5;
    /** consumeNobleGas: a run takes batchMultiplier L, one litre for each ore it draws. */
    static final int GAS_LITRES_PER_ORE = 1;

    public static final String NO_GAS = "none", ALL_ORES = "all";

    /** A filter ore this dimension never draws: the miner voids every ore it draws. */
    public static final String FILTER_EMPTY = "The ore filter keeps an ore this dimension does not have: "
        + "everything is voided.";

    /**
     * One miner: MTEVoidMiners registers VMLUV, VMZPM and VMUV with tier 1, 2 and 3 (bartworks ItemRegistry, MTE ids
     * 12741, 12740, 12739), TIER_MULTIPLIER being that tier.
     */
    record Miner(String id, String name, int tierMultiplier, int meta) {

        /** getMinTier, the tier of its draw and of the energy hatch it needs. */
        int minTier() {
            return tierMultiplier + MIN_TIER_OFFSET;
        }
    }

    static final List<Miner> MINERS = List.of(
        new Miner(VOID_MINER_1, "Void Miner I", 1, 12741),
        new Miner(VOID_MINER_2, "Void Miner II", 2, 12740),
        new Miner(VOID_MINER_3, "Void Miner III", 3, 12739));

    /** GTValues.V, ULV to MAX. */
    static final long[] V = { 8L, 32L, 128L, 512L, 2_048L, 8_192L, 32_768L, 131_072L, 524_288L, 2_097_152L, 8_388_608L,
        33_554_432L, 134_217_728L, 536_870_912L, Integer.MAX_VALUE - 7 };

    /** GTValues.VP: V x 30 / 32, rounded down. */
    static long vp(final int tier) {
        return V[tier] * 30 / 32;
    }

    /** The machine item a miner is drawn as. */
    @Nullable
    static String icon(final String sourceId) {
        for (final Miner miner : MINERS) if (miner.id()
            .equals(sourceId)) return "gregtech:gt.blockmachines@" + miner.meta();
        return null;
    }

    /** The three miners on these tables; none without a dimension to mine. */
    static List<PowerSource> sources(final NrData data) {
        if (data.dimensions()
            .isEmpty()) return List.of();
        final List<PowerSource> out = new ArrayList<>();
        for (final Miner miner : MINERS) out.add(source(miner, data));
        return out;
    }

    static PowerSource source(final Miner miner, final NrData data) {
        final List<Option> dims = new ArrayList<>();
        for (final NrData.Dimension dim : data.dimensions()) dims.add(new Option(dim.key(), dim.label()));
        final List<Option> gases = new ArrayList<>();
        gases.add(new Option(NO_GAS, "None"));
        for (final NrData.NobleGas gas : data.nobleGases()) gases.add(new Option(gas.key(), gas.label()));
        return new PowerSource(
            miner.id(),
            miner.name(),
            PowerGroup.OTHER,
            com.gtnhplanner.machines.web.Tiers.NAMES[miner.minTier()],
            "Ores out of nothing, drawn from its dimension's veins. Noble gases multiply them.",
            List.of(
                new PowerSetting.Select("dimension", "Dimension", dims, defaultDimension(data)),
                new PowerSetting.Select("nobleGas", "Noble gas", gases, NO_GAS),
                new PowerSetting.Select("filter", "Ore filter", filterOptions(data), ALL_ORES)),
            read -> compute(miner, data, read));
    }

    /** The Overworld, where there is one; else the first dimension. */
    private static String defaultDimension(final NrData data) {
        for (final NrData.Dimension dim : data.dimensions()) if ("Overworld".equals(dim.key())) return dim.key();
        return data.dimensions()
            .get(0)
            .key();
    }

    /**
     * Keep every ore, or only one (the whitelist with one ore in it; handleOutputs voids what the filter does not
     * keep): every ore of every dimension, by name; two ores of one name are told apart by their ids.
     */
    private static List<Option> filterOptions(final NrData data) {
        final Map<String, String> ores = new LinkedHashMap<>();
        final Map<String, Integer> names = new HashMap<>();
        for (final NrData.Dimension dim : data.dimensions()) for (final NrData.Ore ore : dim.ores())
            if (ores.putIfAbsent(ore.flow(), ore.label()) == null) names.merge(ore.label(), 1, Integer::sum);
        final List<Option> sorted = new ArrayList<>();
        for (final Map.Entry<String, String> ore : ores.entrySet()) {
            final String label = names.get(ore.getValue()) > 1 ? ore.getValue() + " ("
                + ore.getKey()
                    .substring(
                        ore.getKey()
                            .lastIndexOf(':') + 1)
                + ")" : ore.getValue();
            sorted.add(new Option(ore.getKey(), label));
        }
        sorted.sort(Comparator.comparing(Option::label));
        final List<Option> out = new ArrayList<>();
        out.add(new Option(ALL_ORES, "All ores"));
        out.addAll(sorted);
        return out;
    }

    /**
     * checkProcessing and handleOutputs: every run of RECIPE_DURATION_TICKS draws one ore (batch mode draws 16 in a
     * run 16 times as long, the same rate) at the table's weight over its whole weight (DropMap.isDistributionCached,
     * DiscreteDistribution), as a stack of {@code multiplier}: TIER_MULTIPLIER, times the gas's multiplier while a run
     * finds and takes its litres (getNobleGasInputAndSetMultiplier, consumeNobleGas). The draw is VP[tier + 5]
     * (setElectricityStats) at full efficiency, whatever the hatch.
     */
    static PowerModel compute(final Miner miner, final NrData data, final SettingsReader read) {
        final NrData.Dimension dim = dimension(data, read.select("dimension"));
        final NrData.NobleGas gas = gas(data, read.select("nobleGas"));
        final String filter = read.select("filter");
        final int multiplier = multiplier(miner.tierMultiplier(), gas);
        final double drawsPerSecond = 20.0 / RECIPE_DURATION_TICKS;
        final double total = dim.totalWeight();
        final List<Flow> outputs = new ArrayList<>();
        for (final NrData.Ore ore : dim.ores()) {
            if (!ALL_ORES.equals(filter) && !filter.equals(ore.flow())) continue;
            outputs.add(items(ore.flow(), orePerSecond(drawsPerSecond, multiplier, ore.weight(), total)));
        }
        final List<Flow> inputs = gas == null ? List.of()
            : List.of(liters(gas.flow(), drawsPerSecond * GAS_LITRES_PER_ORE));
        final List<String> warnings = !ALL_ORES.equals(filter) && outputs.isEmpty() ? List.of(FILTER_EMPTY) : List.of();
        return new PowerModel(
            -vp(miner.minTier()),
            inputs,
            outputs,
            List.of(
                stat("Ores a second", number(drawsPerSecond * multiplier)),
                stat("Ores a draw", number(multiplier)),
                stat(
                    "Kinds of ore",
                    number(
                        dim.ores()
                            .size()))),
            warnings);
    }

    /** The output stack size: TIER_MULTIPLIER, times NOBLE_GASSES_MULTIPLIER with a gas. */
    static int multiplier(final int tierMultiplier, @Nullable final NrData.NobleGas gas) {
        return gas == null ? tierMultiplier : tierMultiplier * gas.multiplier();
    }

    /** One ore's expected rate: draws a second x stack size x its share of the table. */
    static double orePerSecond(final double drawsPerSecond, final int multiplier, final double weight,
        final double totalWeight) {
        return totalWeight > 0 ? drawsPerSecond * multiplier * weight / totalWeight : 0;
    }

    private static NrData.Dimension dimension(final NrData data, final String key) {
        for (final NrData.Dimension dim : data.dimensions()) if (dim.key()
            .equals(key)) return dim;
        return data.dimensions()
            .get(0);
    }

    @Nullable
    private static NrData.NobleGas gas(final NrData data, final String key) {
        for (final NrData.NobleGas gas : data.nobleGases()) if (gas.key()
            .equals(key)) return gas;
        return null;
    }

    private NrMiners() {}
}
