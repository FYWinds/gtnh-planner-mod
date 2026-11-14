package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerModel.Stat;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * GoodGenerator's Large Essentia Smeltery (MTELargeEssentiaSmeltery, MetaTileEntity 32024, GT 5.09.54.265), worked
 * from its checkMachine and checkProcessing_EM: each run smelts up to its parallel in items (or buckets of a fluid)
 * into their Thaumcraft aspects, in a time set by the essentia it makes, perfectly overclocked from 480 EU/t.
 * <p>
 * Like the fluid tank it smelts what is wired to it: a port dropped on it, or its socket dragged to one, makes it take
 * that item or fluid (power/Holders), kept under {@link #INPUT} as the resource's key; with nothing wired it lets go.
 * Essentia is no item or fluid, and the planner has no port for it: each aspect is a flow named for the aspect, which
 * the card lists with its readings (PowerPorts.unported), per second, as it does any flow it has no port for.
 */
public final class NrEssentiaSmeltery {

    /** The source id, stored in plans: never rename it. */
    public static final String ID = "large-essentia-smeltery";
    /** MetaTileEntityIDs.LargeEssentiaSmeltery, the card's picture. */
    public static final String MACHINE = "gregtech:gt.blockmachines@32024";
    /**
     * The setting holding what it smelts: the resource key ("item:mod:name:meta" or "fluid:name"), absent while empty.
     */
    public static final String INPUT = "input";

    /** RECIPE_DURATION: ticks per point of essentia before overclocks. */
    static final int RECIPE_DURATION = 32;
    /** RECIPE_EUT. */
    static final long RECIPE_EUT = 480;
    /** NODE_COST_MULTIPLIER, a float in the game. */
    static final float NODE_COST_MULTIPLIER = 1.15f;
    /** getPollutionPerSecond: 22 x (100 - purification efficiency). */
    static final int POLLUTION_PER_POINT = 22;
    /** onPostTick: drainVis(ENTROPY, 125), every 5 ticks. */
    static final int MAX_PERDITIO = 125;

    /** What things smelt to, from the running game (or a test's own). */
    public interface Data {

        /**
         * The aspects one item, or 1,000 L of a fluid, smelts to (getEssentia: Thaumcraft's object tags and bonus tags,
         * one Perditio for an item with none; a fluid's are its block's, none without a block), aspect name to amount,
         * in the game's order. {@code input} is an {@link NrResources} flow; null when the game has no such thing.
         */
        @Nullable
        Map<String, Integer> aspects(String input);

        /** The essentia diffusion cells, tiers 1 to 4 keyed "0" to "3" (their block meta), with the game's names. */
        List<Option> cells();
    }

    // region holding what it smelts

    public static boolean is(@Nullable final Node node) {
        return node != null && ID.equals(node.powerSource);
    }

    /** What it smelts, a resource key; null while it holds nothing. */
    @Nullable
    public static String input(final Node node) {
        final String key = node.powerSettings.get(INPUT);
        return key == null || key.isEmpty() ? null : key;
    }

    /** It takes items and fluids in ({@code supply} false); it gives nothing out. */
    public static boolean canTake(final String key, final boolean supply) {
        return !supply && (key.startsWith("item:") || key.startsWith("fluid:"));
    }

    /** Takes a resource to smelt; false, changing nothing, when it cannot. The caller rebuilds the ports. */
    public static boolean hold(final Node node, final String key, final boolean supply) {
        if (!canTake(key, supply)) return false;
        node.powerSettings.put(INPUT, key);
        return true;
    }

    /** Lets go of what it smelts, keeping its settings. */
    public static void release(final Node node) {
        node.powerSettings.remove(INPUT);
    }

    /**
     * A resource key as an {@link NrResources} flow: "item:gregtech:gt.metaitem.01:2019" is
     * "nr:item:gregtech:gt.metaitem.01@2019", "item:minecraft:wheat:0" "nr:item:minecraft:wheat", "fluid:water"
     * "nr:fluid:water"; null for anything else.
     */
    @Nullable
    public static String flow(final String key) {
        if (key.startsWith("fluid:")) return NrResources.fluid(key.substring("fluid:".length()));
        if (!key.startsWith("item:")) return null;
        final int colon = key.lastIndexOf(':');
        if (colon <= "item:".length()) return null;
        final String name = key.substring("item:".length(), colon), meta = key.substring(colon + 1);
        return NrResources.item("0".equals(meta) ? name : name + "@" + meta);
    }

    // endregion

    // region the game's maths

    /**
     * checkMachine: mParallel = (len + 1) x 2^pTier, len being the middle slices (3 to 7) between the front and the
     * back, so (blocks long - 1) x 2^cell tier, the cell's meta 0 to 3.
     */
    public static int parallel(final int lengthBlocks, final int cellMeta) {
        return (lengthBlocks - 1) << cellMeta;
    }

    /**
     * checkProcessing_EM: the run's base duration, {@code (int) ceil(visSize x 32 x (1 - nodeIncrease x 0.005))}, the
     * Perditio drawn from the node (0 to 100) taking up to half of it off.
     */
    public static int baseTicks(final int visSize, final int nodeIncrease) {
        return (int) Math.ceil(visSize * RECIPE_DURATION * (1 - nodeIncrease * 0.005));
    }

    /** A run after its overclocks: EU/t and ticks. */
    public record Overclocked(int overclocks, long euPerTick, int ticks) {}

    /**
     * OverclockCalculator with setRecipeEUt(480), setEUt(getMaxInputEu()), the base duration and enablePerfectOC: each
     * overclock is x4 EU/t and a quarter of the time, as many as the power is whole tiers above 480 (getTiersAbove)
     * and the hatches' summed EU is voltage tiers above HV, at least none; the time is cut to no less than a tick.
     */
    public static Overclocked overclock(final long maxInputEu, final int baseTicks) {
        int overclocks = tiersAbove(maxInputEu, RECIPE_EUT);
        final long machineTier = Math.max(log4ceil(maxInputEu / 8), 1);
        final long recipeTier = Math.max(log4ceil(RECIPE_EUT / 8), 1);
        overclocks = (int) Math.min(overclocks, machineTier - recipeTier);
        overclocks = Math.max(overclocks, 0);
        final double factor = Math.pow(4, overclocks);
        final long euPerTick = (long) Math.ceil(RECIPE_EUT * factor);
        final int ticks = (int) Math.max(baseTicks / factor, 1);
        return new Overclocked(overclocks, euPerTick, ticks);
    }

    /** OverclockCalculator.getTiersAbove, in its hundredths: -1 below the base, else floor(log4(power / base)). */
    static int tiersAbove(final long power, final long base) {
        if (power < base) return -1;
        return (int) log4(Math.round(power * 100d) / Math.max(Math.round(base * 100d), 32L * 100));
    }

    /** GTUtility.log4(long): floor of log base 4, 0 at 1 and below. */
    static long log4(final long a) {
        if (a <= 1) return 0;
        return 63 - Long.numberOfLeadingZeros(a) >> 1;
    }

    /** GTUtility.log4ceil(long): ceil of log base 4, 0 at 1 and below. */
    static long log4ceil(final long a) {
        if (a <= 1) return 0;
        return 65 - Long.numberOfLeadingZeros(a - 1) >> 1;
    }

    /**
     * expectedPower: the node power a run uses, {@code (int) (tier^2 x 1.15f)} for the tier of the best energy hatch's
     * voltage (getMaxEnergyInputTier_EM); drawn from the vis network as Aqua and Ignis (drainNodePower).
     */
    public static int nodePowerPerRun(final int hatchTier) {
        return (int) (hatchTier * hatchTier * NODE_COST_MULTIPLIER);
    }

    // endregion

    public static PowerSource source(final Data data) {
        return new PowerSource(
            ID,
            "Large Essentia Smeltery",
            PowerGroup.OTHER,
            // Its infusion recipe (goodgenerator crossmod Research) takes IV circuits and an IV piston.
            "IV",
            "Smelts what is wired to it into essentia, on power and vis.",
            List.of(
                new PowerSetting.Number("length", "Length", 5, 9, 1, 5, null, null),
                new PowerSetting.Select("cell", "Diffusion cells", data.cells(), "0"),
                NrProcess.tierSetting("IV"),
                NrProcess.ampsSetting(),
                new PowerSetting.Number("perditio", "Perditio drawn per 5 ticks", 0, MAX_PERDITIO, 1, 0, "cv", null),
                new PowerSetting.Number("purification", "Purification", 0, 100, 1, 0, "%", null)),
            read -> compute(data, read));
    }

    /** One smeltery a second, at its settings, smelting what it holds. */
    static PowerModel compute(final Data data, final SettingsReader read) {
        final int length = (int) Math.floor(read.number("length"));
        final int cell = Math.max(0, Math.min(3, parseInt(read.select("cell"))));
        final int parallel = parallel(length, cell);
        final int hatchTier = NrProcess.tierIndex(read.select(NrProcess.TIER));
        final long maxInputEu = NrProcess.maxInputEu(read);
        final int purification = (int) Math.floor(read.number("purification"));
        final List<Stat> stats = new ArrayList<>();
        stats.add(stat("Parallel", number(parallel)));
        stats.add(stat("Pollution", number(POLLUTION_PER_POINT * (100 - purification)) + "/s"));
        // onRunningTick: one tick in twenty puts flux gas on a muffler, as often as purification falls short of 100.
        stats.add(stat("Flux gas", number((100 - purification) / 100.0) + "/s"));
        final String key = read.raw(INPUT);
        final String input = key.isEmpty() ? null : flow(key);
        if (input == null) return new PowerModel(0, List.of(), List.of(), stats);
        final Map<String, Integer> aspects = data.aspects(input);
        if (aspects == null)
            return new PowerModel(0, List.of(), List.of(), stats, List.of("The game has no such item or fluid."));
        int visEach = 0;
        for (final int amount : aspects.values()) visEach += amount;
        // onPostTick: every 5 ticks nodeIncrease = min(100, the Perditio it drew, up to 125 centivis).
        final int perditio = (int) Math.floor(read.number("perditio"));
        final int nodeIncrease = Math.min(100, perditio);
        final Overclocked run = overclock(maxInputEu, baseTicks(visEach * parallel, nodeIncrease));
        final double perSecond = 20.0 / run.ticks();
        final boolean fluid = key.startsWith("fluid:");
        stats.add(stat("Cycle", number(run.ticks() / 20.0) + " s"));
        stats.add(stat("Node vis", number(nodePowerPerRun(hatchTier) * perSecond) + " cv/s"));
        if (perditio > 0) stats.add(stat("Perditio", number(perditio * 4) + " cv/s"));
        final List<Flow> outputs = new ArrayList<>();
        for (final Map.Entry<String, Integer> aspect : aspects.entrySet())
            outputs.add(items(aspect.getKey(), (double) aspect.getValue() * parallel * perSecond));
        final List<String> warnings = new ArrayList<>();
        if (maxInputEu < RECIPE_EUT)
            warnings.add("The smeltery needs 480 EU/t: on these hatches it stops for lack of power.");
        if (aspects.isEmpty()) warnings.add("This fluid has no block: the smeltery uses it up and makes no essentia.");
        return new PowerModel(
            -(double) run.euPerTick(),
            List.of(fluid ? liters(input, parallel * 1000.0 * perSecond) : items(input, parallel * perSecond)),
            outputs,
            stats,
            warnings);
    }

    private static int parseInt(final String text) {
        try {
            return Integer.parseInt(text);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private NrEssentiaSmeltery() {}
}
