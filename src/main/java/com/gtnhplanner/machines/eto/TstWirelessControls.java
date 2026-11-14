package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.choice;
import static com.gtnhplanner.machines.eto.TstControls.count;

import com.gtnhplanner.machines.web.Web;

/**
 * The settings of the TwistSpaceTechnology machines that run on the wireless network, on steam or on a heat that
 * changes while they run ({@link TstMachinesWireless}; docs/design/tst-machines.md, "Wireless, steam and heat
 * machines"), beside {@link TstControls}. Labels are English data, drawn through UiText.data; an option's position is
 * what a formula reads, a count's number its value. Pure: no game classes.
 */
public final class TstWirelessControls {

    private TstWirelessControls() {}

    public static final String WIRELESS = "tstWireless", OVERCLOCK_PARAMETER = "tstOverclockParameter",
        STEAM_CASING = "tstSteamCasing", SBF_MODE = "tstSwelegfyrMode", SBF_HEAT = "tstSwelegfyrHeat",
        CRAFTS_A_RUN = "tstCraftsARun", INGOT_MOLD = "tstIngotMold";

    /**
     * Energy hatches, or none: WirelessEnergyMultiMachineBase's wireless mode (the Silksong from 94 sections, the
     * Deployed Nano Core).
     */
    public static final Web.Control WIRELESS_CONTROL = choice(
        WIRELESS,
        "Power Source",
        "Energy Hatches",
        "Wireless (No Hatches)");

    /**
     * The Indistinct Tentacle's: wireless with UMV glass, a UMV or better component casing and no hatches; an Astral
     * Array Fabricator in the controller slot then runs 20-tick batches at 64 times the EU.
     */
    public static final Web.Control TENTACLE_POWER_CONTROL = choice(
        WIRELESS,
        "Power Source",
        "Energy Hatches",
        "Wireless (No Hatches)",
        "Wireless with Astral Array Fabricator");

    /** The Thermal Energy Devourer's: an Energised Tesseract in the controller slot makes it wireless. */
    public static final Web.Control DEVOURER_POWER_CONTROL = choice(
        WIRELESS,
        "Power Source",
        "Energy Hatches",
        "Wireless (Energised Tesseract)");

    /**
     * The Miracle Door's overclock parameter: the controller slot's Integrated Circuit number times its stack size, 1
     * without one (flushOverclockParameter); circuits run 1 to 24, a stack to 64.
     */
    public static final Web.Control OVERCLOCK_PARAMETER_CONTROL = count(
        OVERCLOCK_PARAMETER,
        "Circuit Number x Stack Size",
        1,
        24 * 64);

    /**
     * The crafts one Miracle Door run finds: as many as its buses hold (no limit, 0, the game's), or so many. A run's
     * Critical Photons are shared by its crafts, so only a number makes them a rate.
     */
    public static final Web.Control CRAFTS_A_RUN_CONTROL = count(CRAFTS_A_RUN, "Crafts a Run (0 = No Limit)", 0, null);

    /** A White Dwarf Mold (Ingot) in the input bus: the Miracle Door casts molten outputs (turnToIngotRecipe). */
    public static final Web.Control INGOT_MOLD_CONTROL = choice(
        INGOT_MOLD,
        "White Dwarf Mold",
        "No Mold",
        "Mold in the Input Bus");

    /** The Large Steam Forge Hammer's casings (checkSteamCasingTier): bronze is tier 1, steel tier 2. */
    public static final Web.Control STEAM_CASING_CONTROL = choice(
        STEAM_CASING,
        "Steam Casing",
        "Bronze Plated Bricks",
        "Solid Steel Machine Casing");

    /** The Swelegfyr Blast Furnace's machine mode: 0 normal, 1 passive. */
    public static final Web.Control SBF_MODE_CONTROL = choice(SBF_MODE, "Mode", "Normal Mode", "Passive Mode");

    /**
     * The Swelegfyr Blast Furnace's heat (mHeatingCapacity) in K, held between the coil's heat and its maximum; 0 is
     * the mode's steady heat: the coil's in normal mode, the maximum in passive mode.
     */
    public static final Web.Control SBF_HEAT_CONTROL = count(SBF_HEAT, "Heat (K, 0 = Steady)", 0, null);

    /**
     * The Swelegfyr Blast Furnace's glass, HV to UXV (chainAllGlasses): below UMV no energy hatch may be above it
     * (checkMachine :333-346). UMV by default, the first that limits nothing.
     */
    public static final Web.Control SBF_GLASS_CONTROL;

    static {
        final Web.Control glass = TstControls.GLASS_CONTROL.copy();
        glass.defaultKey = "umv";
        SBF_GLASS_CONTROL = glass;
    }
}
