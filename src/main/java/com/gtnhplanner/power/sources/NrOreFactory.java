package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * GregTech's Integrated Ore Factory (MTEIntegratedOreFactory, MetaTileEntity 15564, GT 5.09.54.265), worked from its
 * checkProcessing: every 30 EU/t of its hatches is one ore a run, each ore taking 200 L of distilled water and 2 L of
 * lubricant, and the run's ores go through its mode's machines one after another, each looked up in the game's own
 * recipe maps (power/game/NrOreFactoryGame), its chanced outputs drawn as the game draws them. The old controller
 * (MTEIntegratedOreFactoryLegacy, MetaTileEntity 1132) works differently and is not this card.
 */
public final class NrOreFactory {

    /** The source id, stored in plans: never rename it. */
    public static final String ID = "integrated-ore-factory";
    /** MetaTileEntityIDs.IntegratedOreFactory, the card's picture. */
    public static final String MACHINE = "gregtech:gt.blockmachines@15564";

    /** The bath fluid select's "none". */
    public static final String NONE = "none";

    /** RECIPE_EUT: the EU/t one ore takes, and so one parallel's worth of the hatches' power. */
    static final long EU_PER_ORE = 30;
    /** WATER_PER_ORE: distilled water an ore. */
    static final int WATER_PER_ORE = 200;
    /** LUBRICANT_PER_ORE. */
    static final int LUBRICANT_PER_ORE = 2;
    /** getPollutionPerSecond. */
    static final int POLLUTION = 200;

    /** The factory's item kinds: its hash sets, filled from ore dictionary names (initHash). */
    public enum Kind {
        /** isOre: "ore*" and "rawOre*". */
        ORE,
        /** isCrushedOre: "crushed*" but purified or centrifuged. */
        CRUSHED,
        /** isCrushedPureOre: "crushedPurified*". */
        PURIFIED,
        /** isThermal: "crushedCentrifuged*". */
        CENTRIFUGED,
        /** isImpureDust: "dustImpure*". */
        IMPURE,
        /** isPureDust: "dustPure*". */
        PURE
    }

    /** One machine of a mode: which recipe map it looks in. */
    public enum Step {
        /** RecipeMaps.maceratorRecipes. */
        MACERATE,
        /** RecipeMaps.oreWasherRecipes, with distilled water. */
        WASH,
        /** RecipeMaps.thermalCentrifugeRecipes. */
        THERMAL,
        /** RecipeMaps.centrifugeRecipes. */
        CENTRIFUGE,
        /** RecipeMaps.sifterRecipes. */
        SIFT,
        /** RecipeMaps.chemicalBathRecipes, with the fluids in its hatches; it uses up the bath fluid. */
        BATH,
        /** RecipeMaps.hammerRecipes. */
        HAMMER,
        /** RecipeMaps.simpleWasherRecipes, with water. */
        SIMPLE_WASH
    }

    /** A step and the kinds of item it takes; the rest pass by it. */
    public record Stage(Step step, Set<Kind> kinds) {

        static Stage of(final Step step, final Kind first, final Kind... rest) {
            return new Stage(step, EnumSet.of(first, rest));
        }
    }

    /** ProcessingMode, in the game's order, with its getRecipeTickTime and its checkProcessing chain. */
    public enum Mode {

        MAC_WASH_THERMAL_MAC("mac_wash_thermal_mac", "Macerate -> Ore Washer -> Thermal Centrifuge -> Macerate", 30,
            Stage.of(Step.MACERATE, Kind.ORE), Stage.of(Step.WASH, Kind.CRUSHED),
            Stage.of(Step.THERMAL, Kind.PURIFIED, Kind.CRUSHED),
            Stage.of(Step.MACERATE, Kind.CENTRIFUGED, Kind.ORE, Kind.CRUSHED, Kind.PURIFIED)),
        MAC_WASH_MAC_CENTRI("mac_wash_mac_centri", "Macerate -> Ore Washer -> Macerate -> Centrifuge", 15,
            Stage.of(Step.MACERATE, Kind.ORE), Stage.of(Step.WASH, Kind.CRUSHED),
            Stage.of(Step.MACERATE, Kind.ORE, Kind.CRUSHED, Kind.PURIFIED),
            Stage.of(Step.CENTRIFUGE, Kind.IMPURE, Kind.PURE)),
        MAC_MAC_CENTRI("mac_mac_centri", "Macerate -> Macerate -> Centrifuge", 10, Stage.of(Step.MACERATE, Kind.ORE),
            Stage.of(Step.MACERATE, Kind.CENTRIFUGED, Kind.ORE, Kind.CRUSHED, Kind.PURIFIED),
            Stage.of(Step.CENTRIFUGE, Kind.IMPURE, Kind.PURE)),
        MAC_WASH_SIFT("mac_wash_sift", "Macerate -> Ore Washer -> Sifter", 20, Stage.of(Step.MACERATE, Kind.ORE),
            Stage.of(Step.WASH, Kind.CRUSHED), Stage.of(Step.SIFT, Kind.PURIFIED)),
        MAC_CHEM_MAC_CENTRI("mac_chem_mac_centri", "Macerate -> Chemical Bathing -> Macerate -> Centrifuge", 17,
            Stage.of(Step.MACERATE, Kind.ORE), Stage.of(Step.BATH, Kind.CRUSHED, Kind.PURIFIED),
            Stage.of(Step.MACERATE, Kind.CRUSHED, Kind.PURIFIED), Stage.of(Step.CENTRIFUGE, Kind.IMPURE, Kind.PURE)),
        MAC_CHEM_THERMAL_MAC("mac_chem_thermal_mac", "Macerate -> Chemical Bathing -> Thermal Centrifuge -> Macerate",
            32, Stage.of(Step.MACERATE, Kind.ORE), Stage.of(Step.BATH, Kind.CRUSHED, Kind.PURIFIED),
            Stage.of(Step.THERMAL, Kind.PURIFIED, Kind.CRUSHED),
            Stage.of(Step.MACERATE, Kind.CENTRIFUGED, Kind.ORE, Kind.CRUSHED, Kind.PURIFIED)),
        FORGE_FORGE_SIMPLEWASH("forge_forge_simplewash", "Forge Hammer -> Forge Hammer -> Simple Washer", 1,
            Stage.of(Step.HAMMER, Kind.ORE),
            Stage.of(Step.HAMMER, Kind.CENTRIFUGED, Kind.ORE, Kind.CRUSHED, Kind.PURIFIED),
            Stage.of(Step.SIMPLE_WASH, Kind.IMPURE, Kind.PURE));

        /** The select key, stored in plans: never rename it. */
        public final String key;
        public final String label;
        /** getRecipeTickTime, in seconds. */
        public final int seconds;
        public final List<Stage> stages;

        Mode(final String key, final String label, final int seconds, final Stage... stages) {
            this.key = key;
            this.label = label;
            this.seconds = seconds;
            this.stages = List.of(stages);
        }

        public int ticks() {
            return seconds * 20;
        }

        public boolean bathes() {
            for (final Stage stage : stages) if (stage.step() == Step.BATH) return true;
            return false;
        }

        public static Mode of(final String key) {
            for (final Mode mode : values()) if (mode.key.equals(key)) return mode;
            return MAC_WASH_THERMAL_MAC;
        }
    }

    /** One output of a recipe: an item flow, the stack size, and its chance in hundredths of a percent. */
    public record Output(String item, int size, int chance) {}

    /**
     * A recipe a step finds: its outputs in the recipe's order, and for the chemical bath the fluid it uses
     * (getRepresentativeFluidInput(0)) and how much an item, null and 0 for none.
     */
    public record Recipe(List<Output> outputs, @Nullable String fluid, int fluidAmount) {

        public Recipe {
            outputs = List.copyOf(outputs);
        }
    }

    /** The factory's ores, recipes and fluids, from the running game (or a test's own). */
    public interface Data {

        /** The ores it takes (its isOre set), keyed by item flow, labelled with the game's names. */
        List<Option> ores();

        /** The fluids chemical bath recipes use on crushed ores, as fluid flows. */
        List<Option> bathFluids();

        String distilledWater();

        String lubricant();

        /** The kinds an item is: the factory's hash sets it is in. */
        Set<Kind> kinds(String item);

        /**
         * The recipe a step finds for an item, as the factory looks it up; for the chemical bath with distilled water,
         * lubricant and {@code bath} (a fluid flow, or null) in its hatches. Null when none.
         */
        @Nullable
        Recipe recipe(Step step, String item, @Nullable String bath);

        /** Whether an item is one of the stone dusts the factory voids when told to (VOIDABLE_STONE_DUSTS). */
        boolean voidable(String item);
    }

    // region the game's maths

    /** checkProcessing: maxParallelFromPower = safeInt(getMaxInputEu() / RECIPE_EUT). */
    public static int parallel(final long maxInputEu) {
        return (int) Math.min(Integer.MAX_VALUE, maxInputEu / EU_PER_ORE);
    }

    /** A run's products and the litres of each fluid its chemical bath used, each the expected amount. */
    public record Run(Map<String, Double> outputs, Map<String, Double> bathFluids) {}

    /**
     * checkProcessing's chain for {@code ores} of one ore: each stage (processStep) runs every item of its kinds
     * through
     * its recipe and passes the rest by (and an item its step has no recipe for); doCompress then merges same items and
     * drops the voidable stone dusts if told to. The bath (chemicalWash) passes an item by when its recipe has no
     * fluid, and is assumed to have all the fluid it asks for. Chanced outputs are expected values ({@link #expected}).
     */
    public static Run chain(final Data data, final String ore, final Mode mode, @Nullable final String bath,
        final boolean voidStone, final long ores) {
        Map<String, Double> mid = new LinkedHashMap<>();
        mid.put(ore, (double) ores);
        final Map<String, Double> bathFluids = new LinkedHashMap<>();
        for (final Stage stage : mode.stages) {
            final Map<String, Double> out = new LinkedHashMap<>();
            for (final Map.Entry<String, Double> entry : mid.entrySet()) {
                final String item = entry.getKey();
                final double n = entry.getValue();
                if (!takes(stage, data.kinds(item))) {
                    add(out, data, voidStone, item, n);
                    continue;
                }
                final Recipe recipe = data.recipe(stage.step(), item, bath);
                if (recipe == null || stage.step() == Step.BATH && recipe.fluid() == null) {
                    add(out, data, voidStone, item, n);
                    continue;
                }
                if (stage.step() == Step.BATH) bathFluids.merge(recipe.fluid(), n * recipe.fluidAmount(), Double::sum);
                for (final Output output : recipe.outputs())
                    add(out, data, voidStone, output.item(), output.size() * expected(n, output.chance()));
            }
            mid = out;
        }
        return new Run(mid, bathFluids);
    }

    private static boolean takes(final Stage stage, final Set<Kind> kinds) {
        for (final Kind kind : kinds) if (stage.kinds()
            .contains(kind)) return true;
        return false;
    }

    /** doCompress: same items merged, voidable stone dusts dropped when told to. */
    private static void add(final Map<String, Double> out, final Data data, final boolean voidStone, final String item,
        final double amount) {
        if (amount <= 0 || voidStone && data.voidable(item)) return;
        out.merge(item, amount, Double::sum);
    }

    /**
     * The expected count of one output of {@code n} recipes at {@code chance} (getOutputStack): all of them at
     * 10,000; else {@code (int) ceil(std * nextGaussian() + n p)} with {@code std = sqrt(n p (1 - p))}, kept only when
     * above zero. That is {@code E[max(0, ceil(X))] = sum over j >= 0 of P(X > j)} for the normal X; with a spread of 2
     * or more and the mean 12 spreads above zero, ceil adds a uniform fraction, a half to 1e-30. A chance over 10,000
     * makes the spread NaN, which the game's int cast turns into nothing.
     */
    public static double expected(final double n, final int chance) {
        if (chance == 10_000) return n;
        if (!(n > 0) || chance <= 0) return 0;
        final double p = chance / 10_000.0;
        final double variance = n * p * (1 - p);
        if (!(variance >= 0)) return 0;
        final double mean = n * p;
        final double std = Math.sqrt(variance);
        if (std == 0) return Math.max(0, Math.ceil(mean));
        if (std >= 2 && mean >= 12 * std) return mean + 0.5;
        return expectedBySum(mean, std);
    }

    /** {@code sum over j >= 0 of P(X > j)} for X ~ N(mean, std), counted out to 12 spreads past the mean. */
    static double expectedBySum(final double mean, final double std) {
        final double low = mean - 12 * std;
        final long start = low > 0 ? (long) Math.floor(low) : 0;
        // Below the window every P(X > j) is 1 to 1e-32.
        double sum = start;
        final long end = (long) Math.ceil(mean + 12 * std);
        for (long j = start; j <= end; j++) sum += upperTail((j - mean) / std);
        return sum;
    }

    /** P(Z > z) for a standard normal Z. */
    static double upperTail(final double z) {
        return 0.5 * erfc(z / Math.sqrt(2));
    }

    /**
     * The complementary error function: 2 - erfc(-x) below zero, the all-positive series of erf
     * ({@code 2/sqrt(pi) e^-x^2 sum 2^k x^(2k+1) / (2k+1)!!}) up to 3, the continued fraction
     * {@code e^-x^2 / sqrt(pi) / (x + 1/2 / (x + 2/2 / (x + 3/2 / ...)))} from 3 on.
     */
    static double erfc(final double x) {
        if (x < 0) return 2 - erfc(-x);
        if (x < 3) {
            double term = x, sum = x;
            for (int k = 1; k < 200; k++) {
                term *= 2 * x * x / (2 * k + 1);
                sum += term;
                if (term < 1e-17 * sum) break;
            }
            return 1 - 2 / Math.sqrt(Math.PI) * Math.exp(-x * x) * sum;
        }
        double t = x;
        for (int k = 60; k >= 1; k--) t = x + (k / 2.0) / t;
        return Math.exp(-x * x) / Math.sqrt(Math.PI) / t;
    }

    // endregion

    public static PowerSource source(final Data data) {
        final List<Option> ores = data.ores();
        String oreDefault = ores.get(0)
            .key();
        final String iron = NrResources.item("minecraft:iron_ore");
        for (final Option ore : ores) if (ore.key()
            .equals(iron)) oreDefault = iron;
        final List<Option> modes = new ArrayList<>();
        for (final Mode mode : Mode.values()) modes.add(new Option(mode.key, mode.label));
        final List<Option> baths = new ArrayList<>();
        baths.add(new Option(NONE, "None"));
        baths.addAll(data.bathFluids());
        return new PowerSource(
            ID,
            "Integrated Ore Factory",
            PowerGroup.OTHER,
            // AssemblyLineRecipes: assembled at RECIPE_UV.
            "UV",
            "Runs an ore through its mode's machines. Every 30 EU/t of its hatches is one ore a run.",
            List.of(
                new PowerSetting.Select("ore", "Ore", ores, oreDefault),
                new PowerSetting.Select("mode", "Mode", modes, Mode.MAC_WASH_THERMAL_MAC.key),
                new PowerSetting.Select("bath", "Bath fluid", baths, NONE),
                new PowerSetting.Toggle("voidStone", "Void stone dust", false),
                NrProcess.tierSetting("UV"),
                NrProcess.ampsSetting()),
            read -> compute(data, read));
    }

    /** One factory a second, at its settings. */
    static PowerModel compute(final Data data, final SettingsReader read) {
        final Mode mode = Mode.of(read.select("mode"));
        final int parallel = parallel(NrProcess.maxInputEu(read));
        final List<Stat> stats = new ArrayList<>();
        stats.add(stat("Parallel", number(parallel)));
        stats.add(stat("Cycle", number(mode.seconds) + " s"));
        stats.add(stat("Pollution", number(POLLUTION) + "/s"));
        if (parallel <= 0) return new PowerModel(
            0,
            List.of(),
            List.of(),
            stats,
            List.of("Needs at least 30 EU/t of energy hatches for one ore a run."));
        final String ore = read.select("ore");
        final String bathKey = read.select("bath");
        final String bath = NONE.equals(bathKey) ? null : bathKey;
        final Run run = chain(data, ore, mode, bath, read.on("voidStone"), parallel);
        final double perSecond = 20.0 / mode.ticks();
        final List<Flow> inputs = new ArrayList<>();
        inputs.add(items(ore, parallel * perSecond));
        // A bath recipe on distilled water or lubricant draws from the same hatches: one flow each.
        final Map<String, Double> litres = new LinkedHashMap<>();
        litres.put(data.distilledWater(), (double) parallel * WATER_PER_ORE);
        litres.merge(data.lubricant(), (double) parallel * LUBRICANT_PER_ORE, Double::sum);
        for (final Map.Entry<String, Double> fluid : run.bathFluids()
            .entrySet()) litres.merge(fluid.getKey(), fluid.getValue(), Double::sum);
        for (final Map.Entry<String, Double> fluid : litres.entrySet())
            inputs.add(liters(fluid.getKey(), fluid.getValue() * perSecond));
        final List<Flow> outputs = new ArrayList<>();
        for (final Map.Entry<String, Double> product : run.outputs()
            .entrySet()) outputs.add(items(product.getKey(), product.getValue() * perSecond));
        outputs.sort(
            Comparator.comparingDouble(Flow::perSecond)
                .reversed());
        final List<String> warnings = new ArrayList<>();
        if (mode.bathes() && run.bathFluids()
            .isEmpty()) warnings.add("No bath fluid for this ore: the chemical bath passes it by.");
        return new PowerModel(-(double) EU_PER_ORE * parallel, inputs, outputs, stats, warnings);
    }

    private NrOreFactory() {}
}
