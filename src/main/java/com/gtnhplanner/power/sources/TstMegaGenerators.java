package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * TwistSpaceTechnology's Tower of Abstraction (GT_TileEntity_MegaEggGenerator) and Mega Naquadah Reactor
 * (TST_MegaNqReactor), worked from TST 0.8.1's source, GregTech 5.09.54.265's and the pack's TwistSpaceTechnology.cfg
 * and GoodGenerator.cfg (both at their defaults for everything read here). docs/design/tst-machines.md has the
 * derivation.
 */
public final class TstMegaGenerators {

    /** The Tower of Abstraction's source id, stored in plans: never rename it. */
    public static final String TOWER_OF_ABSTRACTION = "tst-tower-of-abstraction";
    /** Its controller (MachineLoader: MetaTileEntity 19026), the card's picture. */
    public static final String TOWER_OF_ABSTRACTION_ITEM = "gregtech:gt.blockmachines@19026";
    /** The Mega Naquadah Reactor's source id, stored in plans: never rename it. */
    public static final String MEGA_NAQUADAH_REACTOR = "tst-mega-naquadah-reactor";
    /** Its controller (MachineLoader: MetaTileEntity 19081), the card's picture. */
    public static final String MEGA_NAQUADAH_REACTOR_ITEM = "gregtech:gt.blockmachines@19081";

    /** The controller item a source of this file is drawn as; null for any other source. */
    @Nullable
    public static String controllerItem(final String sourceId) {
        if (TOWER_OF_ABSTRACTION.equals(sourceId)) return TOWER_OF_ABSTRACTION_ITEM;
        if (MEGA_NAQUADAH_REACTOR.equals(sourceId)) return MEGA_NAQUADAH_REACTOR_ITEM;
        return null;
    }

    // region Tower of Abstraction

    /** shapeMiddle: a middle piece's upper layer holds 16 egg positions ('K'), its lower layer none. */
    public static final int SLOTS_PER_PIECE = 16;
    /**
     * The base layer, two layers a middle piece and the top layer, stacked up from the controller: a world 256 blocks
     * tall holds 2 x 127 + 2.
     */
    public static final int MAX_PIECES = 127;
    /** Config MEG_CrepperEgg_Gen, MEG_DragonEgg_Gen, MEG_InfinityEgg_Gen: EU/t an egg adds to genPower. */
    private static final long CREEPER_EGG_EUT = 512, DRAGON_EGG_EUT = 2_048, INFINITY_EGG_EUT = 16_384;
    /** Config MEG_Overall_Multiply 1.0; getOutputEUt casts it to long before it multiplies. */
    private static final long OVERALL_MULTIPLY = (long) 1.0D;
    /**
     * Config MEG_Efficiency_PiecesBuff (per whole power of two of the pieces), MEG_Efficiency_InfinityEggBuff (per
     * infinity egg), MEG_Efficiency_Lost (per empty egg position), in GregTech's 1/10,000ths.
     */
    private static final int PIECES_BUFF = 200, INFINITY_EGG_BUFF = 100, EMPTY_SLOT_LOSS = 500;
    /** Config MEG_Laser_Pieces: checkLaser refuses a laser dynamo below this many pieces. */
    private static final int LASER_PIECES = 16;
    private static final String TOO_MANY_INFINITY_EGGS = "More infinity eggs than pieces: the tower does not form.";
    private static final String TOO_MANY_EGGS = "More eggs than egg slots (16 a piece): the tower does not form.";

    /**
     * getMaxEfficiency: 10,000 plus 200 for each whole power of two in the pieces (the game's own
     * {@code (int) (Math.log(pieces) / Math.log(2))}), plus 100 an infinity egg, less 500 an empty egg position, never
     * below 0. It may pass 10,000: the output then passes the eggs' sum.
     */
    public static int maxEfficiency(final int pieces, final int infinityEggs, final int emptySlots) {
        return Math.max(
            0,
            10_000 + PIECES_BUFF * (int) (Math.log(pieces) / Math.log(2))
                + INFINITY_EGG_BUFF * infinityEggs
                - EMPTY_SLOT_LOSS * emptySlots);
    }

    /**
     * updateEfficiencyIncrement: what each 20-tick run adds to the efficiency (mEfficiencyIncrease): 20, plus 100 an
     * infinity egg, plus one for each pair of dragon eggs, 50 from 100 dragon eggs on.
     */
    public static int efficiencyIncrement(final int dragonEggs, final int infinityEggs) {
        int increment = 20;
        if (infinityEggs != 0) increment += infinityEggs * 100;
        if (dragonEggs != 0) increment += dragonEggs < 100 ? dragonEggs / 2 : 50;
        return increment;
    }

    private static PowerSource towerOfAbstraction() {
        return new PowerSource(
            TOWER_OF_ABSTRACTION,
            "Tower of Abstraction",
            PowerGroup.PASSIVE,
            // GTCMMachineRecipes: assembled at RECIPE_LuV.
            "LuV",
            "Dragon, creeper and infinity eggs in its pieces make EU from nothing.",
            List.of(
                new PowerSetting.Number("pieces", "Pieces", 1, MAX_PIECES, 1, 1),
                new PowerSetting.Number("infinityEggs", "Infinity eggs", 0, MAX_PIECES, 1, 0),
                new PowerSetting.Number("dragonEggs", "Dragon eggs", 0, SLOTS_PER_PIECE * MAX_PIECES, 1, 16),
                new PowerSetting.Number("creeperEggs", "Creeper eggs", 0, SLOTS_PER_PIECE * MAX_PIECES, 1, 0)),
            TstMegaGenerators::computeTower);
    }

    /**
     * checkProcessing runs 20 ticks at a time, every run at lEUt = genPower (the eggs' sum); each tick
     * MTEExtendedPowerMultiBlockBase.onRunningTick sends {@code lEUt * mEfficiency / 10,000} (long division) to the
     * dynamo, and each run's end raises mEfficiency by the increment up to getMaxEfficiency. It never stops, so the
     * steady output is the eggs' sum at the max efficiency; from a cold start that takes ceil(max / increment) runs.
     */
    private static PowerModel computeTower(final SettingsReader read) {
        final int pieces = (int) Math.floor(read.number("pieces"));
        final int infinity = (int) Math.floor(read.number("infinityEggs"));
        final int dragon = (int) Math.floor(read.number("dragonEggs"));
        final int creeper = (int) Math.floor(read.number("creeperEggs"));
        final int empty = SLOTS_PER_PIECE * pieces - infinity - dragon - creeper;
        final List<String> warnings = new ArrayList<>();
        // checkInfinityEgg: a structure error, the machine does not form.
        if (infinity > pieces) warnings.add(TOO_MANY_INFINITY_EGGS);
        if (empty < 0) warnings.add(TOO_MANY_EGGS);
        if (!warnings.isEmpty()) return new PowerModel(0, List.of(), List.of(), List.of(), warnings);
        final long genPower = OVERALL_MULTIPLY
            * (CREEPER_EGG_EUT * creeper + DRAGON_EGG_EUT * dragon + INFINITY_EGG_EUT * infinity);
        final int maxEfficiency = maxEfficiency(pieces, infinity, empty);
        final long euPerTick = genPower * maxEfficiency / 10_000;
        final int increment = efficiencyIncrement(dragon, infinity);
        final int warmUpRuns = (maxEfficiency + increment - 1) / increment;
        return new PowerModel(
            euPerTick,
            List.of(),
            List.of(),
            List.of(
                stat("Base output", formatAmount(genPower) + " EU/t"),
                stat("Empty slots", number(empty)),
                stat("Max efficiency", percent(maxEfficiency / 10_000.0)),
                stat("Warm-up", number(warmUpRuns) + " s"),
                // Config MEG_Dynamo_Limit 1; checkLaser.
                stat("Dynamo", pieces >= LASER_PIECES ? "One hatch, laser allowed" : "One hatch, no laser")));
    }

    // endregion

    // region Mega Naquadah Reactor

    /** Config Parallel_MegaNqReactor (the pack's 1,000). */
    public static final int MAX_PARALLELS = 1_000;
    /** FuelRecipeLoader: every naquadahReactorFuels recipe takes 1 L (getFluidOrGas(1)) and gives 1 L depleted. */
    private static final int FUEL_LITRES = 1;
    /** LIQUID_AIR_PER_SECOND: litres each parallel takes for each second of the run. */
    private static final int LIQUID_AIR_PER_SECOND = 2_400;
    /** TICKS_TO_MAX_BONUS: 24 hours; incrementRunTimeTicks stops runTimeTicks there. */
    private static final long TICKS_TO_MAX_BONUS = 24L * 60 * 60 * 20;
    private static final int TICKS_PER_HOUR = 60 * 60 * 20;
    /** DEFAULT_COOLANT_EFFICIENCY: no coolant, 100%. */
    private static final int NO_COOLANT_EFFICIENCY = 100;
    private static final String NONE = "None";

    /** A naquadahReactorFuels recipe: GoodGenerator's FuelRecipeLoader at GGConfigLoader's (the pack's) values. */
    private record NqFuel(String name, String depleted, int basicOutput, int ticks) {}

    /** getCoolantTiers: a coolant, its efficiency (COOLANT_EFFICIENCY, %) and litres a parallel takes a second. */
    private record NqCoolant(String name, int efficiency, int litres) {}

    /** getExcitedTiers: an excited fluid, its coefficient (EXCITED_LIQUID_COEFF) and litres a parallel a second. */
    private record NqBooster(String name, int coefficient, int litres) {}

    /** NaquadahFuelVoltage (mSpecialValue, LNG_BASIC_OUTPUT) and NaquadahFuelTime (mDuration, ticks). */
    private static final List<NqFuel> FUELS = List.of(
        new NqFuel("Thorium Fuel (Excited)", "Thorium Based Liquid Fuel (Depleted)", 2_200, 500),
        new NqFuel("Uranium Fuel (Excited)", "Uranium Based Liquid Fuel (Depleted)", 12_960, 100),
        new NqFuel("Plutonium Fuel (Excited)", "Plutonium Based Liquid Fuel (Depleted)", 32_400, 150),
        new NqFuel("Naq Fuel Mk-I", "Naquadah Based Liquid Fuel MkI (Depleted)", 975_000, 60),
        new NqFuel("Naq Fuel Mk-II", "Naquadah Based Liquid Fuel MkII (Depleted)", 2_300_000, 70),
        new NqFuel("Naq Fuel Mk-III", "Naquadah Based Liquid Fuel MkIII (Depleted)", 9_511_000, 80),
        new NqFuel("Naq Fuel Mk-IV", "Naquadah Based Liquid Fuel MkIV (Depleted)", 88_540_000, 100),
        new NqFuel("Naq Fuel Mk-V", "Naquadah Based Liquid Fuel MkV (Depleted)", 399_576_000, 160),
        new NqFuel("Naq Fuel Mk-VI", "Naquadah Based Liquid Fuel MkVI (Depleted)", 2_077_795_200, 240));

    /** IC2 coolant, Super Coolant, Gelid Cryotheum, Tachyon Rich Temporal Fluid (Materials.Time). */
    private static final List<NqCoolant> COOLANTS = List.of(
        new NqCoolant("Coolant", 105, 1_000),
        new NqCoolant("Super Coolant", 150, 1_000),
        new NqCoolant("Cryotheum", 275, 1_000),
        new NqCoolant("Tachyon Rich Temporal Fluid", 500, 20));

    /** Molten Caesium, Uranium-235, Naquadah, Atomic Separation Catalyst, and Materials.Space. */
    private static final List<NqBooster> BOOSTERS = List.of(
        new NqBooster("Molten Caesium", 2, 180),
        new NqBooster("Molten Uranium-235", 3, 180),
        new NqBooster("Molten Naquadah", 4, 20),
        new NqBooster("Molten Atomic Separation Catalyst", 16, 20),
        new NqBooster("Spatially Enlarged Fluid", 64, 20));

    /** getRuntimeBonus: (1 + min(0.5, runTimeTicks / 24 h))², 2.25 from 12 hours of running on. */
    public static double runtimeBonus(final long runTimeTicks) {
        final double timeBonus = 1d + Math.min(0.5d, (double) runTimeTicks / (double) TICKS_TO_MAX_BONUS);
        return timeBonus * timeBonus;
    }

    /**
     * checkProcessing's currentEUt, in the game's own double steps and truncated: basic output x coolant efficiency x
     * excited coefficient x parallels x runtime bonus.
     */
    public static long reactorEuPerTick(final int basicOutput, final int coolantEfficiency, final int coefficient,
        final int parallels, final long runTimeTicks) {
        final double actualEfficiency = coolantEfficiency / 100d;
        return (long) ((double) basicOutput * actualEfficiency * coefficient * parallels * runtimeBonus(runTimeTicks));
    }

    private static PowerSource megaNaquadahReactor() {
        final List<Option> fuels = new ArrayList<>();
        for (final NqFuel fuel : FUELS) fuels.add(new Option(fuel.name(), fuel.name()));
        final List<Option> coolants = new ArrayList<>();
        coolants.add(new Option(NONE, NONE));
        for (final NqCoolant coolant : COOLANTS) coolants
            .add(new Option(coolant.name(), coolant.name() + " (x" + number(coolant.efficiency() / 100.0) + ")"));
        final List<Option> boosters = new ArrayList<>();
        boosters.add(new Option(NONE, NONE));
        for (final NqBooster booster : BOOSTERS)
            boosters.add(new Option(booster.name(), booster.name() + " (x" + booster.coefficient() + ")"));
        return new PowerSource(
            MEGA_NAQUADAH_REACTOR,
            "Mega Naquadah Reactor",
            PowerGroup.ENDGAME,
            // GTCMMachineRecipes: on the assembly line at RECIPE_UIV.
            "UIV",
            "Up to 1,000 Large Naquadah Reactors in one, up to 2.25x when kept running.",
            List.of(
                new PowerSetting.Select("fuel", "Fuel", fuels, "Naq Fuel Mk-I"),
                new PowerSetting.Select("coolant", "Coolant", coolants, NONE),
                new PowerSetting.Select("booster", "Booster", boosters, NONE),
                new PowerSetting.Number("parallels", "Parallels", 1, MAX_PARALLELS, 1, MAX_PARALLELS),
                new PowerSetting.Number("runningHours", "Running time", 0, 24, 0.5, 24, "h", null)),
            TstMegaGenerators::computeReactor);
    }

    /**
     * checkProcessing: one run lasts the fuel's mDuration and is paid for at its start. It takes the fuel's 1 L times
     * the excited coefficient for each parallel, and for each parallel and each started second of the run
     * (recipeSeconds, the duration rounded up to whole seconds) 2,400 L of liquid air, the coolant's and the excited
     * fluid's litres. It returns the fuel's depleted form litre for litre and runs at currentEUt. The next run starts
     * on the tick this one ends (MTEMultiBlockBase.runMachine), so runTimeTicks never decays and the output is steady;
     * to dynamos or, with none, to the wireless network as currentEUt x the duration at the run's start.
     */
    private static PowerModel computeReactor(final SettingsReader read) {
        final NqFuel fuel = fuel(read.select("fuel"));
        final NqCoolant coolant = coolant(read.select("coolant"));
        final NqBooster booster = booster(read.select("booster"));
        final int parallels = (int) Math.floor(read.number("parallels"));
        final long runTimeTicks = Math
            .min(TICKS_TO_MAX_BONUS, Math.round(read.number("runningHours") * TICKS_PER_HOUR));
        final int coefficient = booster == null ? 1 : booster.coefficient();
        final int efficiency = coolant == null ? NO_COOLANT_EFFICIENCY : coolant.efficiency();
        final int ticks = fuel.ticks();
        final int seconds = (ticks + 19) / 20;
        final long fuelPerRun = (long) FUEL_LITRES * coefficient * parallels;
        final long euPerTick = reactorEuPerTick(fuel.basicOutput(), efficiency, coefficient, parallels, runTimeTicks);
        final List<Flow> inputs = new ArrayList<>();
        inputs.add(liters(fuel.name(), perSecond(fuelPerRun, ticks)));
        inputs.add(liters("Liquid Air", perSecond((long) LIQUID_AIR_PER_SECOND * parallels * seconds, ticks)));
        if (coolant != null)
            inputs.add(liters(coolant.name(), perSecond((long) coolant.litres() * parallels * seconds, ticks)));
        if (booster != null)
            inputs.add(liters(booster.name(), perSecond((long) booster.litres() * parallels * seconds, ticks)));
        return new PowerModel(
            euPerTick,
            inputs,
            List.of(liters(fuel.depleted(), perSecond(fuelPerRun, ticks))),
            List.of(
                stat("Burn", number(fuelPerRun) + " L every " + number(ticks) + " ticks"),
                stat("Efficiency", percent(efficiency / 100.0)),
                stat("Runtime bonus", number(runtimeBonus(runTimeTicks)) + "x")));
    }

    /** Litres a second of what one run of {@code ticks} takes or gives {@code perRun} of. */
    private static double perSecond(final long perRun, final int ticks) {
        return perRun * 20.0 / ticks;
    }

    private static NqFuel fuel(final String name) {
        for (final NqFuel fuel : FUELS) if (fuel.name()
            .equals(name)) return fuel;
        return FUELS.get(3);
    }

    @Nullable
    private static NqCoolant coolant(final String name) {
        for (final NqCoolant coolant : COOLANTS) if (coolant.name()
            .equals(name)) return coolant;
        return null;
    }

    @Nullable
    private static NqBooster booster(final String name) {
        for (final NqBooster booster : BOOSTERS) if (booster.name()
            .equals(name)) return booster;
        return null;
    }

    // endregion

    private static List<PowerSource> sources;

    public static synchronized List<PowerSource> sources() {
        if (sources == null) sources = List.of(towerOfAbstraction(), megaNaquadahReactor());
        return sources;
    }

    private TstMegaGenerators() {}
}
