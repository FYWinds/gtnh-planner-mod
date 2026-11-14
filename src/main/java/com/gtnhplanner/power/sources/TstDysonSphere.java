package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerModel.Stat;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Condition;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * TwistSpaceTechnology's Dyson Sphere Program generators (the ETO fork; the website has none of them): the Dyson
 * Sphere Ray Receiving Station and the Artificial Star, worked from TST 0.8.1's {@code system/DysonSphereProgram} with
 * the pack's {@code config/TwistSpaceTechnology.cfg} (its DSP values are TST 0.8.1's defaults) and GregTech
 * 5.09.54.265's {@code MTEMultiBlockBase.runMachine}. Both put their EU straight into the owner's wireless network
 * ({@code addEUToGlobalEnergyMap}); the planner's ledger has no wireless kind, so it is the card's EU as any
 * generator's, and a reading says where it goes. docs/design/tst-machines.md has the model.
 */
public final class TstDysonSphere {

    /** The Ray Receiving Station's source id, stored in plans: never rename it. */
    public static final String RECEIVER = "tst-dsp-receiver";
    /** Its controller (MachineLoader: MetaTileEntity 19014), the card's picture. */
    public static final String RECEIVER_ITEM = "gregtech:gt.blockmachines@19014";
    /** The Artificial Star's source id, stored in plans: never rename it. */
    public static final String ARTIFICIAL_STAR = "tst-artificial-star";
    /** Its controller (MachineLoader: MetaTileEntity 19015), the card's picture. */
    public static final String ARTIFICIAL_STAR_ITEM = "gregtech:gt.blockmachines@19015";

    /**
     * The TST items these cards take and make, which the website's resource map lacks: ItemRegister's MetaItem01
     * variants, registered under TST's mod id. Without TST they are no item, and the card lists them as readings.
     */
    public static final Map<String, String> ITEMS = Map.of(
        "Critical Photon",
        "TwistSpaceTechnology:MetaItem01@13",
        "Antimatter",
        "TwistSpaceTechnology:MetaItem01@14",
        "Antimatter Fuel Rod",
        "TwistSpaceTechnology:MetaItem01@16",
        "Stellar Construction Frame Material",
        "TwistSpaceTechnology:MetaItem01@17",
        "Gravitational Lens",
        "TwistSpaceTechnology:MetaItem01@18",
        "Strange Annihilation Fuel Rod",
        "TwistSpaceTechnology:MetaItem01@29");

    /** The controller item a source of this file is drawn as, or null for another source. */
    @Nullable
    public static String controller(final String sourceId) {
        if (RECEIVER.equals(sourceId)) return RECEIVER_ITEM;
        if (ARTIFICIAL_STAR.equals(sourceId)) return ARTIFICIAL_STAR_ITEM;
        return null;
    }

    /** TSTUtils.INTEGER_MAX_VALUE: the unit both machines count their EU in. */
    private static final long INT_MAX = Integer.MAX_VALUE;
    /** The reading of where both machines' EU goes. */
    private static final String WIRELESS = "Wireless EU network";

    // region Dyson Sphere Ray Receiving Station

    /** Config.solarSailPowerPoint: the power points each solar sail gives. */
    private static final long SOLAR_SAIL_POWER_POINT = 524_288;
    /** Config.solarSailCanHoldPerNode and solarSailCanHoldDefault: the sails a sphere holds safely. */
    private static final long SAILS_PER_NODE = 256;
    private static final long SAILS_HELD_DEFAULT = 2_048;
    /** Config.maxPowerPointPerReceiver: 1,024 x Integer.MAX_VALUE, the most one receiver requests. */
    private static final long MAX_POWER_POINT_PER_RECEIVER = 1_024L * Integer.MAX_VALUE;
    /** Config.gravitationalLensSpeedMultiplier: intensify mode multiplies the output by 4. */
    private static final double LENS_MULTIPLIER = 4;
    /** Config.secondsOfEveryGravitationalLensProvideToIntensifyTime: each lens gives 600 s of it. */
    private static final int LENS_SECONDS = 600;
    /** Config.EUPerCriticalPhoton: the EU one Critical Photon stores. */
    private static final long EU_PER_CRITICAL_PHOTON = Integer.MAX_VALUE;
    /** TST_DSPReceiver.checkProcessing: every run is 128 ticks; it hands over its bank as each run ends. */
    private static final int RECEIVER_RUN_TICKS = 128;

    private static final String MODE = "mode", POWER = "power", PHOTONS = "photons";
    private static final String SLOT = "controller", EMPTY = "empty", CIRCUIT = "circuit", ASTRAL = "astral";

    /** DSP_Galaxy: a star system's stellar coefficient. A team has one sphere per star system (DSP_DataCell). */
    private record StarSystem(String name, double coefficient) {}

    private static final StarSystem SOLAR_SYSTEM = new StarSystem("Solar System", 1);
    private static final StarSystem ROSS128 = new StarSystem("Ross128", 0.4);
    private static final StarSystem BARNARDA = new StarSystem("Barnarda", 0.851);
    private static final StarSystem VEGA = new StarSystem("Vega", 2.98);
    private static final StarSystem T_CETI = new StarSystem("T Ceti", 1.9);
    private static final StarSystem CENTAURI_B = new StarSystem("Centauri B", 2.3);
    private static final StarSystem AMUN_RA = new StarSystem("Amun-Ra", 0.9);
    private static final StarSystem NO_STAR = new StarSystem("None", 0);

    /**
     * DSP_Planet: the planet a receiver stands on (found by dimension id; the ids match the pack's Galacticraft,
     * GalaxySpace, AmunRa, bartworks and Twilight Forest configs), its coefficient and its star system. {@code key} is
     * the enum constant's name, stored in plans.
     */
    private record Planet(String key, String name, double coefficient, StarSystem star) {}

    private static final List<Planet> PLANETS = List.of(
        new Planet("OverWorld", "Overworld", 1, SOLAR_SYSTEM),
        new Planet("TheEnd", "The End", 1.07, SOLAR_SYSTEM),
        new Planet("Miranda", "Miranda", 0.45, SOLAR_SYSTEM),
        new Planet("Haumea", "Haumea", 0.18, SOLAR_SYSTEM),
        new Planet("Pluto", "Pluto", 0.2, SOLAR_SYSTEM),
        new Planet("Triton", "Triton", 0.35, SOLAR_SYSTEM),
        new Planet("Proteus", "Proteus", 0.33, SOLAR_SYSTEM),
        new Planet("Oberon", "Oberon", 0.44, SOLAR_SYSTEM),
        new Planet("Callisto", "Callisto", 0.67, SOLAR_SYSTEM),
        new Planet("Titan", "Titan", 0.56, SOLAR_SYSTEM),
        new Planet("Ganymede", "Ganymede", 0.66, SOLAR_SYSTEM),
        new Planet("Ceres", "Ceres", 0.7, SOLAR_SYSTEM),
        new Planet("Enceladus", "Enceladus", 0.55, SOLAR_SYSTEM),
        new Planet("Deimos", "Deimos", 1.01, SOLAR_SYSTEM),
        new Planet("Venus", "Venus", 1.6, SOLAR_SYSTEM),
        new Planet("Phobos", "Phobos", 1.01, SOLAR_SYSTEM),
        // Not plain "Mercury": that label is the metal's (its translation).
        new Planet("Mercury", "Mercury (planet)", 2.3, SOLAR_SYSTEM),
        new Planet("Io", "Io", 0.66, SOLAR_SYSTEM),
        new Planet("Europa", "Europa", 0.66, SOLAR_SYSTEM),
        new Planet("KuiperBelt", "Kuiper Belt", 0.1, SOLAR_SYSTEM),
        new Planet("Asteroids", "Asteroids", 0.7, SOLAR_SYSTEM),
        new Planet("Mars", "Mars", 1.01, SOLAR_SYSTEM),
        new Planet("Moon", "Moon", 1.3, SOLAR_SYSTEM),
        new Planet("Makemake", "Makemake", 0.09, SOLAR_SYSTEM),
        new Planet("TwilightForest", "Twilight Forest", 1, SOLAR_SYSTEM),
        new Planet("Ross128b", "Ross128b", 1, ROSS128),
        new Planet("Ross128ba", "Ross128ba", 1.3, ROSS128),
        new Planet("BarnardaF", "Barnarda F", 0.8, BARNARDA),
        new Planet("BarnardaE", "Barnarda E", 0.9, BARNARDA),
        new Planet("BarnardaC", "Barnarda C", 1.5, BARNARDA),
        new Planet("VegaB", "Vega B", 2.6, VEGA),
        new Planet("TCetiE", "T Ceti E", 1.1, T_CETI),
        new Planet("CentauriBb", "Centauri Bb", 2.2, CENTAURI_B),
        new Planet("Seth", "Seth", 0.79, AMUN_RA),
        new Planet("Horus", "Horus", 2.9, AMUN_RA),
        new Planet("Anubis", "Anubis", 0.66, AMUN_RA),
        new Planet("Maahes", "Maahes", 0.77, AMUN_RA),
        new Planet("Neper", "Neper", 1.15, AMUN_RA),
        new Planet("MehenBelt", "Mehen Belt", 0.8, AMUN_RA),
        // getPlanetaryCoefficientWithDimID, getGalaxyFromDimID: every dimension from 180 up but 227 is the Overworld
        // of the Solar System (the tooltip's "Personal Dimension").
        new Planet("Personal", "Personal dimension (ID 180 and up)", 1, SOLAR_SYSTEM),
        // Any other dimension (the Nether, orbits): coefficient 0, in DSP_Galaxy_NULL.
        new Planet("Other", "Any other dimension", 0, NO_STAR));

    /**
     * DSP_DataCell.getMaxDSPPowerPoint, the sphere's power points: {@code (long) (524,288 x sails x (nodes +
     * 1)^0.8)} in long and double steps (flushMaxDSPPowerPointNormal); once that would pass a long
     * (shouldUseBigInteger) it is {@code sails x 524,288 x ceil((nodes + 1)^0.8)} in BigInteger
     * (flushMaxDSPPowerPointBigInteger), read back capped at Long.MAX_VALUE.
     */
    public static long maxPowerPoint(final long sails, final long nodes) {
        final double nodeFactor = Math.pow(nodes + 1, 0.8);
        if (bigIntegerMode(sails, nodes)) {
            final BigInteger points = BigInteger.valueOf(sails)
                .multiply(BigInteger.valueOf(SOLAR_SAIL_POWER_POINT))
                .multiply(BigInteger.valueOf((long) Math.ceil(nodeFactor)));
            return points.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : points.longValue();
        }
        return (long) (SOLAR_SAIL_POWER_POINT * sails * nodeFactor);
    }

    /** DSP_DataCell.shouldUseBigInteger, as the game asks it. */
    public static boolean bigIntegerMode(final long sails, final long nodes) {
        if (sails < 1 || nodes < 1) return false;
        return Long.MAX_VALUE / SOLAR_SAIL_POWER_POINT / sails - 1 <= Math.pow(nodes + 1, 0.8);
    }

    private static PowerSource receiver() {
        final List<Option> planets = new ArrayList<>();
        for (final Planet planet : PLANETS) planets.add(new Option(planet.key(), planet.name()));
        final Condition circuit = new Condition(SLOT, CIRCUIT);
        final Condition astral = new Condition(SLOT, ASTRAL);
        return new PowerSource(
            RECEIVER,
            "Dyson Sphere Ray Receiving Station",
            PowerGroup.ENDGAME,
            // DSPRecipePool: the controller is an assembly line recipe at RECIPE_UIV.
            "UIV",
            "Receives its Dyson sphere's power into the wireless EU network.",
            List.of(
                new PowerSetting.Select(
                    MODE,
                    "Mode",
                    List.of(new Option(POWER, "Power generation"), new Option(PHOTONS, "Photon collection")),
                    POWER),
                new PowerSetting.Select("planet", "Planet", planets, "OverWorld"),
                new PowerSetting.Number("sails", "Solar sails", 0, 1e12, 1, SAILS_HELD_DEFAULT),
                new PowerSetting.Number("nodes", "Dyson sphere nodes", 0, 1e9, 1, 0),
                new PowerSetting.Number("others", "Held by other receivers", 0, 100, 1, 0, "%", null),
                new PowerSetting.Select(
                    SLOT,
                    "Controller slot",
                    List.of(
                        new Option(EMPTY, "Empty"),
                        new Option(CIRCUIT, "Programmed Circuit"),
                        new Option(ASTRAL, "Astral Array Fabricator")),
                    EMPTY),
                new PowerSetting.Number("circuit", "Circuit number", 0, 24, 1, 1, null, circuit),
                new PowerSetting.Number("circuitStack", "Circuit stack size", 1, 64, 1, 1, null, circuit),
                new PowerSetting.Number("astral", "Astral Array Fabricators", 1, 64, 1, 1, null, astral),
                new PowerSetting.Toggle("lens", "Gravitational Lens intensify", false)),
            TstDysonSphere::computeReceiver);
    }

    /**
     * TST_DSPReceiver: checkMachine always sets wirelessMode, so onRunningTick banks generateTickEU() every tick and
     * checkProcessing, each 128-tick run, hands the bank to the wireless network (power generation) or turns it into
     * Critical Photons (photon collection). generateTickEU is {@code (long) (planet x stellar coefficient x lens x
     * used power points)}; the points are getPowerPoint's, from what the sphere has left (startUsingDSP).
     */
    private static PowerModel computeReceiver(final SettingsReader read) {
        final Planet planet = planet(read.select("planet"));
        final StarSystem star = planet.star();
        final long sails = whole(read.number("sails"));
        final long nodes = whole(read.number("nodes"));
        final long max = maxPowerPoint(sails, nodes);
        // getDSPPowerPointCanUse: the sphere's points less what other receivers on it hold.
        final long canUse = max - (long) Math.floor(max * (read.number("others") / 100));
        final String slot = read.select(SLOT);
        int overload = 1;
        final long used;
        if (CIRCUIT.equals(slot)) {
            // An integrated circuit asks for meta / stack size of the whole sphere, at most all of it.
            final long meta = whole(read.number("circuit"));
            final long stack = whole(read.number("circuitStack"));
            final double ratio = Math.min(1, (double) meta / (double) stack);
            used = Math.min(MAX_POWER_POINT_PER_RECEIVER, Math.min((long) (ratio * max), canUse));
        } else if (ASTRAL.equals(slot)) {
            // calculateAstralArrayOverloadMultiplier: Astral Array Fabricators^2 x 2 times the request cap.
            final int arrays = (int) whole(read.number("astral"));
            overload = arrays * arrays * 2;
            used = Math.min(MAX_POWER_POINT_PER_RECEIVER * overload, canUse);
        } else {
            used = Math.min(MAX_POWER_POINT_PER_RECEIVER, canUse);
        }
        final boolean lens = read.on("lens");
        // onFirstTick's stellarAndPlanetCoefficient, then generateTickEU, in the game's double steps.
        final double coefficient = planet.coefficient() * star.coefficient();
        final long euPerTick = (long) (coefficient * (lens ? LENS_MULTIPLIER : 1) * used);

        final List<Stat> stats = new ArrayList<>();
        stats.add(stat("Star system", star.name()));
        stats.add(stat("Stellar coefficient", number(star.coefficient())));
        stats.add(stat("Planet coefficient", number(planet.coefficient())));
        stats.add(stat("Sphere power points", formatAmount(max)));
        stats.add(stat("Power points used", formatAmount(used)));
        if (overload > 1) stats.add(stat("Overload multiplier", number(overload)));
        final long sailsHeld = SAILS_HELD_DEFAULT + SAILS_PER_NODE * nodes;
        stats.add(stat("Sails held safely", number(sailsHeld)));

        final List<String> warnings = new ArrayList<>();
        if (coefficient == 0) warnings.add("TST gives this dimension no coefficient: the station receives nothing.");
        else if (max == 0) warnings.add("The sphere has no power points: it needs solar sails.");
        else if (used == 0) warnings.add("This receiver takes no power points.");
        // DSP_SolarSailDecreaser: every 30 minutes a 1 in 5,000 roll, then 1 in 4 per sphere, halves the excess.
        if (sails > sailsHeld) warnings.add("Sails past what the nodes hold may decay.");

        // checkGravitationalLensInput: each lens adds 600 s of intensify mode; one every 600 s keeps it on.
        final List<Flow> inputs = lens ? List.of(items("Gravitational Lens", 1.0 / LENS_SECONDS)) : List.of();
        if (PHOTONS.equals(read.select(MODE))) {
            final List<Flow> outputs = new ArrayList<>();
            if (euPerTick > 0) outputs.add(items("Critical Photon", criticalPhotonsPerSecond(euPerTick)));
            return new PowerModel(0, inputs, outputs, stats, warnings);
        }
        stats.add(stat("Output", WIRELESS));
        return new PowerModel(euPerTick, inputs, List.of(), stats, warnings);
    }

    /**
     * Photon collection: each tick banks {@code eu / Integer.MAX_VALUE} in storageEUMAX and the rest in storageEU;
     * checkProcessing makes {@code storageEU / EUPerCriticalPhoton} photons (the remainder stays banked) plus
     * {@code (long) (storageEUMAX x (Integer.MAX_VALUE / EUPerCriticalPhoton))}. Per second, the remainder averaged.
     */
    public static double criticalPhotonsPerSecond(final long euPerTick) {
        final long perRunMax = RECEIVER_RUN_TICKS * (euPerTick / INT_MAX);
        final long fromMax = (long) ((double) perRunMax * (Integer.MAX_VALUE / EU_PER_CRITICAL_PHOTON));
        final double fromRest = (double) (RECEIVER_RUN_TICKS * (euPerTick % INT_MAX)) / EU_PER_CRITICAL_PHOTON;
        return (fromMax + fromRest) * 20 / RECEIVER_RUN_TICKS;
    }

    private static Planet planet(final String key) {
        for (final Planet planet : PLANETS) {
            if (key.equals(planet.key())) return planet;
        }
        return PLANETS.get(0);
    }

    // endregion

    // region Artificial Star

    /** initStatics: Config.EUEveryAntimatter (6,442,450,941) / Integer.MAX_VALUE, long division: 3. */
    private static final long ANTIMATTER_UNITS = 6_442_450_941L / Integer.MAX_VALUE;
    /** Config.EUEveryAntimatterFuelRod (2,199,023,254,528) / Integer.MAX_VALUE: 1,024. */
    private static final long FUEL_ROD_UNITS = 2_199_023_254_528L / Integer.MAX_VALUE;
    /** Config.EUEveryStrangeAnnihilationFuelRod (70,368,744,144,896) / Integer.MAX_VALUE: 32,768. */
    private static final long STRANGE_ROD_UNITS = 70_368_744_144_896L / Integer.MAX_VALUE;
    /** checkProcessing: {@code (int) (20 x secondsOfArtificialStarProgressCycleTime)}, 6.4 s. */
    public static final int STAR_RUN_TICKS = (int) (20 * 6.4);
    /** rewardContinuous: +1% a run with fuel, up to 50. */
    private static final int MAX_BONUS = 50;
    /** The TecTech field generators' grades, meta 0 to 8. */
    private static final List<String> GRADES = List
        .of("Crude", "Primitive", "Stable", "Advanced", "Superb", "Exotic", "Perfect", "Tipler", "Gallifreyan");

    /**
     * The three tiered blocks (getTier...FieldBlockFromBlock): the base block is tier 1, the TecTech field generators
     * meta 0 to 8 tiers 2 to 10. The option key is the tier.
     */
    private static List<Option> field(final String base, final String generator) {
        final List<Option> out = new ArrayList<>();
        out.add(new Option("1", base));
        int tier = 2;
        for (final String grade : GRADES) out.add(new Option(String.valueOf(tier++), grade + " " + generator));
        return out;
    }

    private static PowerSource artificialStar() {
        return new PowerSource(
            ARTIFICIAL_STAR,
            "Artificial Star",
            PowerGroup.ENDGAME,
            // DSPRecipePool: the controller is an assembly line recipe at RECIPE_UXV.
            "UXV",
            "Annihilates antimatter fuels into the wireless EU network.",
            List.of(
                new PowerSetting.Number("antimatter", "Antimatter each run", 0, 1e9, 1, 0),
                new PowerSetting.Number("rods", "Antimatter Fuel Rods each run", 0, 1e9, 1, 1),
                new PowerSetting.Number("strangeRods", "Strange Annihilation Fuel Rods each run", 0, 1e9, 1, 0),
                new PowerSetting.Select(
                    "dimensionField",
                    "Dimension field",
                    field("Dimensional Bridge", "Spacetime Compression Field Generator"),
                    "1"),
                new PowerSetting.Select(
                    "timeField",
                    "Time field",
                    field("Ultimate Containment Field Generator", "Time Dilation Field Generator"),
                    "1"),
                new PowerSetting.Select(
                    "stabilisationField",
                    "Stabilisation field",
                    field("Spacetime Altering Casing", "Stabilisation Field Generator"),
                    "1"),
                new PowerSetting.Number("bonus", "Continuous-run bonus", 0, MAX_BONUS, 1, MAX_BONUS, "%", null)),
            TstDysonSphere::computeStar);
    }

    /**
     * TST_ArtificialStar.checkProcessing: each run takes everything in its one input bus, adds
     * {@code (long) (fuel units x output multiplier x (bonus + 100) / 100) x Integer.MAX_VALUE} EU to the wireless
     * network at once (a BigInteger: past a long it is still whole) and lasts 128 ticks; the next run starts on the
     * tick it ends (runMachine), so a steady feed is a steady output.
     */
    private static PowerModel computeStar(final SettingsReader read) {
        final long antimatter = whole(read.number("antimatter"));
        final long rods = whole(read.number("rods"));
        final long strangeRods = whole(read.number("strangeRods"));
        final int dimension = Integer.parseInt(read.select("dimensionField"));
        final int time = Integer.parseInt(read.select("timeField"));
        final int stabilisation = Integer.parseInt(read.select("stabilisationField"));
        final int bonus = (int) whole(read.number("bonus"));
        final double multiplier = outputMultiplier(dimension, time, stabilisation);
        // checkMachine: recoveryChance = tDim x tTime x tStab, out of 1,000 (nextInt(1000) < chance).
        final double recovery = Math.min(dimension * time * stabilisation, 1000) / 1000.0;
        final double runsPerSecond = 20.0 / STAR_RUN_TICKS;

        final List<Stat> stats = new ArrayList<>();
        stats.add(stat("Output multiplier", number(multiplier)));
        stats.add(stat("Continuous-run bonus", number(100 + bonus) + "%"));
        stats.add(stat("Run time", number(STAR_RUN_TICKS / 20.0) + " s"));
        stats.add(stat("Recovery chance", percent(recovery)));
        if (antimatter + rods + strangeRods == 0) {
            // Nothing in the bus: NO_RECIPE, and the bonus falls back to 0.
            return new PowerModel(0, List.of(), List.of(), stats, List.of("No fuel: the star does not run."));
        }
        final long units = ANTIMATTER_UNITS * antimatter + FUEL_ROD_UNITS * rods + STRANGE_ROD_UNITS * strangeRods;
        final BigInteger euPerRun = euPerRun(units, multiplier, bonus);
        stats.add(0, stat("EU per run", formatAmount(euPerRun.doubleValue())));
        stats.add(stat("Output", WIRELESS));

        final List<Flow> inputs = new ArrayList<>();
        if (antimatter > 0) inputs.add(items("Antimatter", antimatter * runsPerSecond));
        if (rods > 0) inputs.add(items("Antimatter Fuel Rod", rods * runsPerSecond));
        if (strangeRods > 0) inputs.add(items("Strange Annihilation Fuel Rod", strangeRods * runsPerSecond));
        // getRecovers: one Stellar Construction Frame Material per fuel rod of the run, on a recovery roll.
        final List<Flow> outputs = new ArrayList<>();
        final double recovered = (rods + strangeRods) * recovery * runsPerSecond;
        if (recovered > 0) outputs.add(items("Stellar Construction Frame Material", recovered));
        return new PowerModel(euPerRun.doubleValue() / STAR_RUN_TICKS, inputs, outputs, stats);
    }

    /**
     * calculateOutputMultiplier: {@code (tTime x tDim)^0.25 x 1.588186^(tStab - 2)}, 0.63 with every base block, 128
     * with every Gallifreyan generator.
     */
    public static double outputMultiplier(final int dimension, final int time, final int stabilisation) {
        return Math.pow(1d * time * dimension, 0.25d) * Math.pow(1.588186d, stabilisation - 2);
    }

    /** The EU one run adds, in checkProcessing's steps: a double product cut to a long, times Integer.MAX_VALUE. */
    public static BigInteger euPerRun(final long fuelUnits, final double multiplier, final int bonus) {
        return BigInteger.valueOf((long) (fuelUnits * multiplier * ((bonus + 100d) / 100d)))
            .multiply(BigInteger.valueOf(INT_MAX));
    }

    // endregion

    private static long whole(final double value) {
        return (long) Math.floor(value);
    }

    private static List<PowerSource> sources;

    public static synchronized List<PowerSource> sources() {
        if (sources == null) sources = List.of(receiver(), artificialStar());
        return sources;
    }

    private TstDysonSphere() {}
}
