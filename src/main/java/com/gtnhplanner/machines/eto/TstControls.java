package com.gtnhplanner.machines.eto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.gtnhplanner.machines.web.MachineControls;
import com.gtnhplanner.machines.web.Web;

/**
 * The settings of TwistSpaceTechnology's machines (docs/design/tst-machines.md): what each structure is built of, as
 * the machine's formulas in {@link TstMachines} read it. Labels are English data, drawn through UiText.data; an
 * option's position is what a formula reads ({@code Context.tier}), a count's number its value ({@code Context.value}).
 * Pure: no game classes.
 */
public final class TstControls {

    private TstControls() {}

    // region Ids

    public static final String COIL = MachineControls.COIL, GLASS = "tstGlassTier", FIELD = "tstFieldGenerator",
        MSTF_FIELD = "tstMstfField", BALL_FIELD = "tstBallLightningField", PHOTON = "tstPhotonIntensifier",
        RINGS = "tstRings", PIECES = "tstPieces", FUSION_COIL = "tstFusionCoil", COOLING = "tstCoolingCasing",
        MATRIX = "tstEnergyMatrix", BALL_TIER = "tstBallLightningTier", COMPONENT_CASING = "tstComponentCasing",
        DEVOURER_MODE = "tstDevourerMode", MACERATOR_BLOCK = "tstMaceratorBlock", PARALLELS = "tstParallels",
        SEED = "tstSpacetimeSeed", ALTAR_TIER = "tstAltarTier", SPEED_RUNES = "tstSpeedRunes",
        TIME_BENDING_RUNES = "tstTimeBendingRunes", WATER_LAVA = "tstWaterLava", CORE = "tstMultiUseCore",
        NANITES = "tstNanites", NANITE_COUNT = "tstNaniteCount";

    /**
     * Settings the website's data puts on a whole map (its own machines' parallels, casings, pipes...): none of them is
     * part of a TwistSpaceTechnology machine, so its entries hide them.
     */
    static final String[] MAP_CONTROLS = { "machineParallel", "voltageParallel", "itemPipeCasing", "pipeCasing",
        "steamPressure", "parallel-containment-block-tier", "parallel-precise-casing-tier", "preciseCasing",
        "cokeOvenCasing", "cokeOvenSlices", "pcbNanites" };

    // endregion

    // region Builders

    /** A choice of options, the first the minimum and the default; {@code multipliers} (if any) per option. */
    static Web.Control choice(final String id, final String label, final List<String> options,
        @Nullable final double[] outputMultipliers) {
        final Web.Control control = new Web.Control();
        control.id = id;
        control.label = label;
        control.tiers = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            final Web.TierOption option = new Web.TierOption();
            option.key = key(options.get(i));
            option.label = options.get(i);
            if (outputMultipliers != null) option.outputMultiplier = outputMultipliers[i];
            control.tiers.add(option);
        }
        control.minimumKey = control.tiers.get(0).key;
        control.defaultKey = control.minimumKey;
        return control;
    }

    static Web.Control choice(final String id, final String label, final String... options) {
        return choice(id, label, List.of(options), null);
    }

    /** A typed whole number from {@code min} (its default), up to {@code max} (none when null). */
    static Web.Control count(final String id, final String label, final int min, @Nullable final Integer max) {
        final Web.Control control = new Web.Control();
        control.id = id;
        control.label = label;
        control.numeric = new Web.Numeric();
        control.numeric.min = min;
        control.numeric.max = max != null ? Double.valueOf(max) : null;
        control.numeric.step = 1.0;
        final Web.TierOption option = new Web.TierOption();
        option.key = String.valueOf(min);
        option.label = String.valueOf(min);
        control.tiers = List.of(option);
        control.minimumKey = option.key;
        control.defaultKey = option.key;
        return control;
    }

    /** An option's key: its label in lower case, every run of anything but a-z and 0-9 one hyphen. */
    static String key(final String label) {
        return label.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }

    // endregion

    // region The settings

    /** VoltageIndex names, ULV first. */
    static final List<String> VOLTAGES = List
        .of("ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX");

    /** GlassTier: tiered glass runs from HV (3) to UXV (13); the formula reads 3 + the option's position. */
    public static final Web.Control GLASS_CONTROL = choice(GLASS, "Glass Tier", VOLTAGES.subList(3, 14), null);

    /** The Stabilisation Field Generators, meta 0 to 8. */
    private static final List<String> STABILISATION = List.of(
        "Crude Stabilisation Field Generator",
        "Primitive Stabilisation Field Generator",
        "Stable Stabilisation Field Generator",
        "Advanced Stabilisation Field Generator",
        "Superb Stabilisation Field Generator",
        "Exotic Stabilisation Field Generator",
        "Perfect Stabilisation Field Generator",
        "Tipler Stabilisation Field Generator",
        "Gallifreyan Stabilisation Field Generator");

    private static List<String> fields(final String... first) {
        final List<String> out = new ArrayList<>(List.of(first));
        out.addAll(STABILISATION);
        return out;
    }

    /**
     * The field generator tier of the Crystalline Infinitier and the Space Scaler (getBlockFieldGeneratorTier):
     * Containment 1, Ultimate Containment 2, the Stabilisation generators 3 to 11; the tier is position + 1.
     */
    static final List<String> FIELD_OPTIONS = fields(
        "Containment Field Generator",
        "Ultimate Containment Field Generator");
    public static final Web.Control FIELD_CONTROL = choice(FIELD, "Field Generator", FIELD_OPTIONS, null);

    /** The Micro SpaceTime Fabricatorio's: Ultimate Containment 1, the Stabilisation generators 2 to 10. */
    public static final Web.Control MSTF_FIELD_CONTROL = choice(
        MSTF_FIELD,
        "Field Generator",
        fields("Ultimate Containment Field Generator"),
        null);

    /** Ball Lightning's: Hollow Casing 1, Spacetime Altering Casing 2, the Stabilisation generators 3 to 11. */
    public static final Web.Control BALL_FIELD_CONTROL = choice(
        BALL_FIELD,
        "Field Generator",
        fields("Hollow Casing", "Spacetime Altering Casing"),
        null);

    /**
     * The Photonic Intensifiers in all 25 upgrade slots of the Precise High-Energy Photonic Quantum Master (the
     * position past "None" is the casing's meta + 1).
     */
    public static final Web.Control PHOTON_CONTROL = choice(
        PHOTON,
        "Photonic Intensifiers",
        listOf("None", VOLTAGES.subList(1, 15)),
        null);

    /** Compact fusion coils 1 to 5 (GoodGenerator's, meta 0 to 4), the Advanced High Power Coil Block 6. */
    static final List<String> FUSION_COILS = List.of(
        "Ameliorated Superconducting Coil",
        "Compact Fusion Coil",
        "Advanced Compact Fusion Coil",
        "Compact Fusion Coil Mk-II Prototype",
        "Compact Fusion Coil Mk-II Finaltype",
        "Advanced High Power Coil Block");
    public static final Web.Control FUSION_COIL_CONTROL = choice(FUSION_COIL, "Fusion Coil", FUSION_COILS, null);

    public static final Web.Control COOLING_CONTROL = choice(
        COOLING,
        "Central Cooling Casing",
        "Cryogenic Cooling Casing",
        "Infinity Cooled Casing");

    /** The Energy Sustainment Matrix, tiers 1 (UHV) to 6 (MAX). */
    public static final Web.Control MATRIX_CONTROL = choice(
        MATRIX,
        "Energy Sustainment Matrix",
        "Energy Sustainment Matrix Framework",
        "Energy Sustainment Matrix UEV Tier",
        "Energy Sustainment Matrix UIV Tier",
        "Energy Sustainment Matrix UMV Tier",
        "Energy Sustainment Matrix UXV Tier",
        "Energy Sustainment Matrix MAX Tier");

    /** Ball Lightning's machine tier (checkTier): 1 empty controller, 2 lens, 3 MK2 and chip, 4 full upgrade. */
    public static final Web.Control BALL_TIER_CONTROL = choice(
        BALL_TIER,
        "Machine Tier",
        "Tier 1",
        "Tier 2",
        "Tier 3",
        "Tier 4");

    /** GoodGenerator's component assembly line casing, LV (meta 0) to MAX (meta 13). */
    public static final Web.Control COMPONENT_CASING_CONTROL = choice(
        COMPONENT_CASING,
        "Component Casing",
        VOLTAGES.subList(1, 15),
        null);

    public static final Web.Control DEVOURER_MODE_CONTROL = choice(
        DEVOURER_MODE,
        "Mode",
        "High Speed Mode",
        "Devourer Mode");

    public static final Web.Control MACERATOR_BLOCK_CONTROL = choice(
        MACERATOR_BLOCK,
        "Structure Block",
        "Damascus Steel Block",
        "Neutronium Block",
        "Universium Block");

    /** Every parallel controller's parallels together, plus the machine's own one. */
    public static final Web.Control PARALLELS_CONTROL = count(PARALLELS, "Parallels", 1, null);

    /** A SpaceTime Seed in the special input bus doubles every output of the run. */
    public static final Web.Control SEED_CONTROL = choice(
        SEED,
        "SpaceTime Seed",
        List.of("No Seed", "One Seed a Run"),
        new double[] { 1, 2 });

    public static final Web.Control ALTAR_TIER_CONTROL = choice(
        ALTAR_TIER,
        "Structure Tier",
        "Tier 1",
        "Tier 2",
        "Tier 3",
        "Tier 4",
        "Tier 5",
        "Tier 6");
    public static final Web.Control SPEED_RUNES_CONTROL = count(SPEED_RUNES, "Speed Runes", 0, null);
    public static final Web.Control TIME_BENDING_RUNES_CONTROL = count(
        TIME_BENDING_RUNES,
        "Time Bending Speed Runes",
        0,
        null);

    /** The Silicon Rock Synthesizer's outputs: 4 per recipe output and parallel, 1024 with water and lava fed. */
    public static final Web.Control WATER_LAVA_CONTROL = choice(
        WATER_LAVA,
        "Water and Lava",
        List.of("Not Supplied", "Supplied"),
        new double[] { 4, 1024 });

    /** The Manufacturing Center's Advanced MultiUse Core, IV (5) to UV (8). */
    public static final Web.Control CORE_CONTROL = choice(
        CORE,
        "MultiUse Core",
        "Advanced MultiUse Core IV",
        "Advanced MultiUse Core LuV",
        "Advanced MultiUse Core ZPM",
        "Advanced MultiUse Core UV");

    public static final Web.Control NANITES_CONTROL = choice(
        NANITES,
        "Nanites",
        "No Nanites",
        "Silver Nanites",
        "Gold Nanites");
    public static final Web.Control NANITE_COUNT_CONTROL = count(NANITE_COUNT, "Nanite Count", 0, null);

    /** The plain coil ladder, Cupronickel (tier 0) to Eternal (13). */
    public static final Web.Control COIL_CONTROL = MachineControls.HEATING_COIL_CONTROL;

    /** The coil ladder from the recipe's heat: the Intensify Chemical Distorter refuses a hotter recipe. */
    public static final Web.Control HOT_COIL_CONTROL;

    /** The coil ladder from Infinity: Ball Lightning's structure refuses anything colder. */
    public static final Web.Control INFINITY_COIL_CONTROL;

    static {
        final Web.Control hot = COIL_CONTROL.copy();
        hot.minimumHeatFromSpecialValue = true;
        HOT_COIL_CONTROL = hot;
        final Web.Control infinity = COIL_CONTROL.copy();
        infinity.minimumKey = "infinity";
        infinity.defaultKey = "infinity";
        INFINITY_COIL_CONTROL = infinity;
    }

    private static List<String> listOf(final String first, final List<String> rest) {
        final List<String> out = new ArrayList<>();
        out.add(first);
        out.addAll(rest);
        return out;
    }

    // endregion
}
