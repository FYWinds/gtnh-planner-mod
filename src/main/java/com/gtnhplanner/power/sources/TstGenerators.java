package com.gtnhplanner.power.sources;

import static com.gtnhplanner.power.sources.Helpers.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnhplanner.power.PowerData;
import com.gtnhplanner.power.PowerData.Fuel;
import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * TwistSpaceTechnology's generators (the ETO fork; the website has none of them), worked from TST 0.8.1's source and
 * GregTech 5.09.54.265's. docs/design/tst-machines.md lists the ones still left out.
 */
public final class TstGenerators {

    /** The Universal Generator's source id, stored in plans: never rename it. */
    public static final String UNIVERSAL_GENERATOR = "tst-universal-generator";
    /** Its controller (MachineLoader: MetaTileEntity 19074), the card's picture. */
    public static final String UNIVERSAL_GENERATOR_ITEM = "gregtech:gt.blockmachines@19074";

    // region Universal Generator

    /** TST_UniversalGenerator.checkProcessing: each run drains this much of one fuel. */
    private static final int LITRES_PER_RUN = 1_000;
    /** checkProcessing: a run's fuel value is the map's mSpecialValue (EU per litre) x 1,500, 150% of 1,000 L. */
    private static final int FUEL_VALUE_PER_EU_PER_LITRE = 1_500;
    /**
     * Dynamo amps: checkMachine allows one to eight dynamo hatches (checkHatchMin, checkHatchMax) and the Dynamo
     * element takes a hatch of at most 4 A (MTEMultiBlockBase.addDynamoToMachineList): 8 x 4.
     */
    private static final int MAX_AMPS = 32;
    /** The warning of a fuel worth less than a tick; PowerText says it again in the player's language. */
    private static final String UNDER_A_TICK = "%s lasts under a tick at %s EU/t: each 1,000 L burns for no EU.";

    /**
     * The dynamo tiers checkMachine allows (setDynamoTier(3, false): every hatch at HV or below, all one voltage, or
     * checkMixedDynamo fails it), at GregTech's V[tier].
     */
    private record Tier(String name, long voltage) {}

    private static final List<Tier> TIERS = List
        .of(new Tier("ULV", 8), new Tier("LV", 32), new Tier("MV", 128), new Tier("HV", 512));

    /** One fuel the generator burns and the maps (structures) it is in. */
    private record UgFuel(String name, int euPerLiter, boolean gas, boolean diesel) {}

    /**
     * getRecipeMap: the gas structure burns RecipeMaps.gasTurbineFuels, the fuel structure RecipeMaps.dieselFuels:
     * the tables the Gas Turbine and the Combustion Generator burn. A fluid in both (Ether) is listed once.
     */
    private static Map<String, UgFuel> fuels() {
        final PowerData data = PowerData.get();
        final Map<String, UgFuel> out = new LinkedHashMap<>();
        for (final Fuel fuel : data.gasFuels) out.put(fuel.name, new UgFuel(fuel.name, euPerLiter(fuel), true, false));
        for (final Fuel fuel : data.combustionFuels) {
            final UgFuel gas = out.get(fuel.name);
            out.put(fuel.name, new UgFuel(fuel.name, euPerLiter(fuel), gas != null, true));
        }
        return out;
    }

    private static int euPerLiter(final Fuel fuel) {
        return fuel.euPerLiter != null ? (int) Math.round(fuel.euPerLiter) : 0;
    }

    /**
     * Ticks one run of 1,000 L lasts: checkProcessing's {@code (int) (fuelBurning * 20)}, fuelBurning being
     * {@code (double) fuelValue / (euPerTick * 20)}, in the game's own double steps. 0 when the fuel is worth less
     * than a tick of output.
     */
    public static int runTicks(final int fuelEuPerLiter, final long euPerTick) {
        final int fuelValue = fuelEuPerLiter * FUEL_VALUE_PER_EU_PER_LITRE;
        final double fuelBurning = (double) fuelValue / (euPerTick * 20);
        return (int) (fuelBurning * 20);
    }

    private static PowerSource universalGenerator() {
        final Map<String, UgFuel> fuels = fuels();
        final List<Option> fuelOptions = new ArrayList<>();
        for (final UgFuel fuel : fuels.values()) fuelOptions.add(new Option(fuel.name(), fuel.name()));
        final List<Option> tierOptions = new ArrayList<>();
        for (final Tier tier : TIERS) tierOptions.add(new Option(tier.name(), tier.name()));
        return new PowerSource(
            UNIVERSAL_GENERATOR,
            "Universal Generator",
            PowerGroup.ENGINES,
            // GTCMMachineRecipes: LV hull, robot arms and pistons.
            "LV",
            "Burns gas or diesel fuels at 150% for its dynamos' full output.",
            List.of(
                new PowerSetting.Select("fuel", "Fuel", fuelOptions, "Benzene"),
                new PowerSetting.Select("dynamoTier", "Dynamo tier", tierOptions, "HV"),
                new PowerSetting.Number("amps", "Dynamo amps", 1, MAX_AMPS, 1, 1, "A", null)),
            read -> computeUniversal(fuels, read));
    }

    /**
     * checkProcessing: the output is every dynamo's voltage times their summed amps (getTierDynamo x
     * getDynamoAmperage), at full efficiency (getMaxEfficiency 10,000), whatever the fuel; the fuel only sets how long
     * a run of 1,000 L lasts. A finished run starts the next on the same tick (MTEMultiBlockBase.runMachine), so the
     * output is steady.
     */
    private static PowerModel computeUniversal(final Map<String, UgFuel> fuels, final SettingsReader read) {
        // Both selects read as one of their options (the default for a stored key they lack).
        final UgFuel fuel = fuels.get(read.select("fuel"));
        final Tier tier = tier(read.select("dynamoTier"));
        final long amps = (long) Math.floor(read.number("amps"));
        final long euPerTick = tier.voltage() * amps;
        final int ticks = runTicks(fuel.euPerLiter(), euPerTick);
        final String structure = fuel.gas() && fuel.diesel() ? "Gas or fuel mode"
            : fuel.gas() ? "Gas mode" : "Fuel mode";
        if (ticks <= 0) {
            // mMaxProgresstime 0: the run ends before it starts, its 1,000 L gone and no EU made.
            return new PowerModel(
                0,
                List.of(),
                List.of(),
                List.of(stat("Structure", structure)),
                List.of(String.format(UNDER_A_TICK, fuel.name(), number(euPerTick))));
        }
        final double euPerLitre = (double) euPerTick * ticks / LITRES_PER_RUN;
        return new PowerModel(
            euPerTick,
            List.of(liters(fuel.name(), (LITRES_PER_RUN * 20.0) / ticks)),
            List.of(),
            List.of(
                stat("Structure", structure),
                stat("Burn", number(LITRES_PER_RUN) + " L every " + number(ticks) + " ticks"),
                stat("EU per L", formatAmount(euPerLitre)),
                stat("Efficiency", fuel.euPerLiter() > 0 ? percent(euPerLitre / fuel.euPerLiter()) : "-")));
    }

    private static Tier tier(final String name) {
        for (final Tier tier : TIERS) {
            if (tier.name()
                .equals(name)) return tier;
        }
        return TIERS.get(TIERS.size() - 1);
    }

    // endregion

    private static List<PowerSource> sources;

    public static synchronized List<PowerSource> sources() {
        if (sources == null) sources = List.of(universalGenerator());
        return sources;
    }

    private TstGenerators() {}
}
