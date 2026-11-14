package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.annotation.Nullable;

import com.gtnhplanner.machines.web.MachineControls;
import com.gtnhplanner.machines.web.Tiers;
import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * gtnhintergalactic's fluid pumps with no recipe page of their own (GregTech 5.09.54.265): the Planetary Gas Siphon
 * (MTEPlanetaryGasSiphon) and the Space Elevator's Space Pumping Modules Mk-I to Mk-III (TileEntityModulePump). What
 * they pump is the game's (GasSiphonRecipes, SpacePumpingRecipes, read into NrData). docs/design/nr-sources.md.
 */
public final class NrPumps {

    /** Source ids, stored in plans: never rename one. */
    public static final String GAS_SIPHON = "nr-planetary-gas-siphon", PUMP_1 = "nr-space-pump-1",
        PUMP_2 = "nr-space-pump-2", PUMP_3 = "nr-space-pump-3";

    // region Planetary Gas Siphon

    /** Its controller (MetaTileEntityIDs.PlanetarySiphon). */
    static final int SIPHON_META = 15559;
    /** MTEPlanetaryGasSiphon.SPEED_PER_COIL. */
    private static final double SPEED_PER_COIL = 0.10;
    /** MTEPlanetaryGasSiphon.LOG4. */
    private static final double LOG4 = Math.log10(4);
    /** MTEPlanetaryGasSiphon.MINING_PIPES_PER_DEPTH: pipes the input bus must hold per depth; none are used up. */
    static final int MINING_PIPES_PER_DEPTH = 64;

    public static final String NO_GAS_AT_DEPTH = "This planet has no gas at that depth.";
    public static final String HATCH_TOO_WEAK = "The energy hatch is too weak for this planet and depth.";

    /** GasSiphonRecipes.calculateEUt: depth x 4^(tier + 2), in the game's int arithmetic. */
    public static int siphonEuPerTick(final int depth, final int planetTier) {
        return depth * (4 << (2 * planetTier + 2));
    }

    /**
     * checkProcessing's overclocks: {@code floor(log4(V / EU/t))}, worked as the game does (Math.log10 over LOG4), so
     * a hatch exactly 4^n over the draw gets n. Negative when the hatch is too weak.
     */
    public static int siphonOverclocks(final long hatchVoltage, final int recipeEuPerTick) {
        return (int) Math.floor(Math.log10((double) hatchVoltage / recipeEuPerTick) / LOG4);
    }

    /** speedBoost, in the game's float steps: 1 / (2 + 0.1 x max(1, coil tier)). */
    static float speedBoost(final int coilTier) {
        return (float) 1 / (2 + (float) SPEED_PER_COIL * Math.max(1, coilTier));
    }

    /** checkProcessing's processTime: {@code (int) (20 x speedBoost)} ticks a run. */
    public static int siphonTicks(final int coilTier) {
        return (int) (20 * speedBoost(coilTier));
    }

    /** A run's litres after {@code overclocks}: x2 each (fluid.amount *= 2 << (n - 1)), in int as the game. */
    static int siphonLitres(final int amount, final int overclocks) {
        return overclocks <= 0 ? amount : amount * (2 << (overclocks - 1));
    }

    /** A run's EU/t after {@code overclocks}: x4 each (recipeEUt * (4 << 2(n - 1))), in int as the game. */
    static int siphonDraw(final int recipeEuPerTick, final int overclocks) {
        return overclocks <= 0 ? recipeEuPerTick : recipeEuPerTick * (4 << (2 * (overclocks - 1)));
    }

    private static PowerSource siphon(final NrData data) {
        final List<Option> planets = new ArrayList<>();
        final TreeSet<Integer> depthSet = new TreeSet<>();
        for (final NrData.SiphonPlanet planet : data.siphonPlanets()) {
            planets.add(new Option(planet.key(), planet.label()));
            for (final NrData.SiphonGas gas : planet.gases()) depthSet.add(gas.depth());
        }
        final List<Option> depths = new ArrayList<>();
        for (final int depth : depthSet) depths.add(new Option(Integer.toString(depth), Integer.toString(depth)));
        // From the lowest tier that runs anything at all (Jupiter at depth 1, 1,024 EU/t, needs EV) to MAX.
        final int lowestHatch = lowestHatch(data);
        final List<Option> hatches = new ArrayList<>();
        for (int t = lowestHatch; t < Tiers.NAMES.length; t++) hatches.add(new Option(Tiers.NAMES[t], Tiers.NAMES[t]));
        final List<Option> coils = new ArrayList<>();
        for (final MachineControls.Coil coil : MachineControls.HEATING_COIL_TIERS)
            coils.add(new Option(coil.key(), coil.label()));
        return new PowerSource(
            GAS_SIPHON,
            "Planetary Gas Siphon",
            PowerGroup.OTHER,
            // MachineRecipes: IV motors, pump and hulls.
            "IV",
            "Pumps a gas planet's atmosphere from a space station in its orbit.",
            List.of(
                new PowerSetting.Select(
                    "planet",
                    "Planet",
                    planets,
                    planets.get(0)
                        .key()),
                new PowerSetting.Select(
                    "depth",
                    "Depth",
                    depths,
                    depths.get(0)
                        .key()),
                new PowerSetting.Select("hatch", "Energy hatch", hatches, Tiers.NAMES[lowestHatch]),
                new PowerSetting.Select(
                    "coil",
                    "Heating Coil",
                    coils,
                    coils.get(0)
                        .key())),
            read -> computeSiphon(data, read));
    }

    /** The lowest tier whose voltage carries the least any planet's depth draws. */
    private static int lowestHatch(final NrData data) {
        int least = Integer.MAX_VALUE;
        for (final NrData.SiphonPlanet planet : data.siphonPlanets()) for (final NrData.SiphonGas gas : planet.gases())
            least = Math.min(least, siphonEuPerTick(gas.depth(), planet.tier()));
        for (int t = 0; t < NrMiners.V.length; t++) if (NrMiners.V[t] >= least) return t;
        return NrMiners.V.length - 1;
    }

    /**
     * MTEPlanetaryGasSiphon.checkProcessing: the depth's fluid, overclocked by the one energy hatch's voltage
     * (checkHatchExact Energy 1; getMaxInputVoltage is V of its tier, whatever its amps), every processTime ticks.
     * Batch mode runs 128 / processTime runs at once for as many times the fluid, the same rate. The EU/t is the
     * planet's tier from GasSiphonRecipes, which is the space station's (GalaxySpace 1.1.144: Jupiter's 3, Saturn's
     * and Uranus's 5, Neptune's 6), at full efficiency.
     */
    static PowerModel computeSiphon(final NrData data, final SettingsReader read) {
        final NrData.SiphonPlanet planet = planet(data, read.select("planet"));
        final int depth = Integer.parseInt(read.select("depth"));
        final int hatch = Tiers.index(read.select("hatch"));
        final int coilTier = coilTier(read.select("coil"));
        final List<PowerModel.Stat> stats = new ArrayList<>();
        stats.add(stat("Mining pipes", number((double) depth * MINING_PIPES_PER_DEPTH)));
        final NrData.SiphonGas gas = gasAt(planet, depth);
        if (gas == null) return new PowerModel(0, List.of(), List.of(), stats, List.of(NO_GAS_AT_DEPTH));
        final int recipeEuPerTick = siphonEuPerTick(depth, planet.tier());
        final int overclocks = siphonOverclocks(NrMiners.V[hatch], recipeEuPerTick);
        stats.add(stat("Base EU/t", number(recipeEuPerTick)));
        if (overclocks < 0) return new PowerModel(0, List.of(), List.of(), stats, List.of(HATCH_TOO_WEAK));
        final int litres = siphonLitres(gas.amount(), overclocks);
        final int ticks = siphonTicks(coilTier);
        stats.add(stat("Overclocks", number(overclocks)));
        stats.add(stat("Ticks a run", number(ticks)));
        stats.add(stat("Litres a run", number(litres)));
        return new PowerModel(
            -siphonDraw(recipeEuPerTick, overclocks),
            List.of(),
            List.of(liters(gas.flow(), litres * 20.0 / ticks)),
            stats);
    }

    /** getCoilTier: the coil's HeatingCoilLevel.getTier() + 1, Cupronickel 1 to Eternal 14. */
    static int coilTier(final String coilKey) {
        final List<MachineControls.Coil> coils = MachineControls.HEATING_COIL_TIERS;
        for (int i = 0; i < coils.size(); i++) if (coils.get(i)
            .key()
            .equals(coilKey)) return i + 1;
        return 1;
    }

    private static NrData.SiphonPlanet planet(final NrData data, final String key) {
        for (final NrData.SiphonPlanet planet : data.siphonPlanets()) if (planet.key()
            .equals(key)) return planet;
        return data.siphonPlanets()
            .get(0);
    }

    @Nullable
    private static NrData.SiphonGas gasAt(final NrData.SiphonPlanet planet, final int depth) {
        for (final NrData.SiphonGas gas : planet.gases()) if (gas.depth() == depth) return gas;
        return null;
    }

    // endregion

    // region Space Pumping Modules

    /** TileEntityModulePump.ENERGY_CONSUMPTION: (int) VP[9], 1 A of UHV, for each parallel. */
    static final int EU_PER_PARALLEL = (int) NrMiners.vp(9);
    /** A run lasts 20 ticks for each of the batch's runs (mMaxProgresstime = 20 * maxBatchSize). */
    static final int TICKS_PER_BATCH = 20;

    public static final String NO_RECIPE = "none";

    /**
     * One module: TileEntityModulePumpT1, T2, T3 (MAX_PARALLEL_RECIPES, MAX_PARALLELS, MODULE_VOLTAGE_TIER; MTE ids
     * 14010, 14011, 14014).
     */
    record Module(String id, String name, int recipes, int parallels, int voltageTier, int meta) {}

    static final List<Module> MODULES = List.of(
        new Module(PUMP_1, "Space Pumping Module Mk-I", 1, 4, 9, 14010),
        new Module(PUMP_2, "Space Pumping Module Mk-II", 4, 4, 10, 14011),
        new Module(PUMP_3, "Space Pumping Module Mk-III", 4, 64, 13, 14014));

    private static PowerSource pump(final Module module, final NrData data) {
        final List<Option> recipes = new ArrayList<>();
        recipes.add(new Option(NO_RECIPE, "None"));
        for (final NrData.PumpRecipe recipe : data.pumpRecipes()) recipes.add(new Option(recipe.key(), recipe.label()));
        final List<PowerSetting> settings = new ArrayList<>();
        for (int slot = 1; slot <= module.recipes(); slot++) {
            settings.add(
                new PowerSetting.Select(
                    "recipe" + slot,
                    "Recipe " + slot,
                    recipes,
                    slot == 1 ? data.pumpRecipes()
                        .get(0)
                        .key() : NO_RECIPE));
            // The game's parallel parameter starts at getParallels().
            settings.add(
                new PowerSetting.Number(
                    "parallels" + slot,
                    "Parallels " + slot,
                    0,
                    module.parallels(),
                    1,
                    module.parallels(),
                    null,
                    null));
        }
        return new PowerSource(
            module.id(),
            module.name(),
            PowerGroup.OTHER,
            Tiers.NAMES[module.voltageTier()],
            "A Space Elevator module pumping planets' fluids, each recipe set by planet and gas type.",
            settings,
            read -> computePump(module, data, read));
    }

    /**
     * TileEntityModulePump.checkProcessing_EM: each recipe slot with a recipe and parallels above zero pumps the
     * recipe's litres x parallels x batch in a run of 20 x batch ticks, so litres x parallels a second, and adds
     * ENERGY_CONSUMPTION x parallels to the draw, taken from the module's buffer at full efficiency (modules fix
     * their own maintenance). Slots on one fluid add up. Batch size does not change the rates.
     */
    static PowerModel computePump(final Module module, final NrData data, final SettingsReader read) {
        final Map<String, Double> pumped = new LinkedHashMap<>();
        int usedEuPerTick = 0;
        int parallelsInUse = 0;
        for (int slot = 1; slot <= module.recipes(); slot++) {
            final NrData.PumpRecipe recipe = recipe(data, read.select("recipe" + slot));
            final int parallels = Math.min((int) Math.floor(read.number("parallels" + slot)), module.parallels());
            if (recipe == null || parallels <= 0) continue;
            pumped.merge(recipe.flow(), pumpLitresPerSecond(recipe.amount(), parallels), Double::sum);
            usedEuPerTick += EU_PER_PARALLEL * parallels;
            parallelsInUse += parallels;
        }
        final List<Flow> outputs = new ArrayList<>();
        for (final Map.Entry<String, Double> fluid : pumped.entrySet())
            outputs.add(liters(fluid.getKey(), fluid.getValue()));
        return new PowerModel(
            -usedEuPerTick,
            List.of(),
            outputs,
            List.of(stat("Parallels in use", number(parallelsInUse))));
    }

    /** A slot's litres a second: the recipe's litres x parallels each 20 ticks. */
    static double pumpLitresPerSecond(final int amount, final int parallels) {
        return (double) amount * parallels * 20 / TICKS_PER_BATCH;
    }

    @Nullable
    private static NrData.PumpRecipe recipe(final NrData data, final String key) {
        for (final NrData.PumpRecipe recipe : data.pumpRecipes()) if (recipe.key()
            .equals(key)) return recipe;
        return null;
    }

    // endregion

    /** The machine item a pump is drawn as. */
    @Nullable
    static String icon(final String sourceId) {
        if (GAS_SIPHON.equals(sourceId)) return "gregtech:gt.blockmachines@" + SIPHON_META;
        for (final Module module : MODULES) if (module.id()
            .equals(sourceId)) return "gregtech:gt.blockmachines@" + module.meta();
        return null;
    }

    /** The siphon on its planets, then the three modules on their recipes; each only with something to pump. */
    static List<PowerSource> sources(final NrData data) {
        final List<PowerSource> out = new ArrayList<>();
        if (!data.siphonPlanets()
            .isEmpty()) out.add(siphon(data));
        if (!data.pumpRecipes()
            .isEmpty()) for (final Module module : MODULES) out.add(pump(module, data));
        return out;
    }

    private NrPumps() {}
}
