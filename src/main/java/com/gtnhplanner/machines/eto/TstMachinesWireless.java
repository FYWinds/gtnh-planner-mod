package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.COIL_CONTROL;
import static com.gtnhplanner.machines.eto.TstWirelessControls.*;
import static com.gtnhplanner.machines.web.MachineTable.machine;
import static com.gtnhplanner.machines.web.MachineTable.none;
import static com.gtnhplanner.machines.web.MachineTable.perfectThenNormal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import com.gtnhplanner.machines.web.MachineControls;
import com.gtnhplanner.machines.web.MachineEffects;
import com.gtnhplanner.machines.web.MachineTable;
import com.gtnhplanner.machines.web.MachineTable.Behaviour;
import com.gtnhplanner.machines.web.MachineTable.Coefficient;
import com.gtnhplanner.machines.web.MachineTable.Context;
import com.gtnhplanner.machines.web.RecipeRules;
import com.gtnhplanner.machines.web.Upkeep;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's machines that run on the wireless network, on steam, or on a heat that changes while
 * they run, as machine table entries beside {@link TstMachines} (docs/design/tst-machines.md, "Wireless, steam and heat
 * machines"): the Miracle Door, the Indistinct Tentacle Prototype MK-II, the Hyper Thermal Convector, the two large
 * steam machines and the Swelegfyr Blast Furnace, and the wireless mode the Silksong, the Indistinct Tentacle, the
 * Thermal Energy Devourer and the Deployed Nano Core may run in ({@link #orWireless}). A wireless run is a fixed number
 * of ticks ({@link MachineTable.Behaviour#fixedTicks}) whose EU the network pays as a lump sum when it starts; the
 * card spreads it over the run. Numbers are the pack's config (config/TwistSpaceTechnology.cfg), which keeps every
 * default these read. Pure: no game classes.
 */
public final class TstMachinesWireless {

    private TstMachinesWireless() {}

    /** Recipe maps by the name the data gives them (a formula's Context.recipeMap). */
    static final String STELLAR_FORGE = "Stellar Forge";

    /** Config.DefaultCycleNum_WirelessEnergyMultiMachineBase: the batches one wireless run checks. */
    static final int BATCHES = 100;

    /** The gate messages, English data. */
    static final String SECTIONS_GATE = "Wireless mode needs at least 94 middle sections.",
        CASING_GATE = "Wireless mode needs UMV glass and a UMV or better component casing.",
        STEAM_GATE = "This recipe draws more EU/t than this steam machine provides.",
        GLASS_GATE = "An energy hatch above the glass tier: the structure does not form.",
        HEAT_GATE = "Too hot for this coil: recipe heat above coil heat + 100 K per hatch tier over MV.";

    /** What the machines use up besides their recipes (Upkeep ids). */
    static final String CRITICAL_PHOTON = "item:TwistSpaceTechnology:MetaItem01@13", PYROTHEUM = "fluid:pyrotheum";

    /** Every machine of this group, by the label its handler templates carry (eto-tst-wireless.json). */
    public static void register(final Map<String, Behaviour> machines) {
        // TST_MiracleDoor: always wireless (getDefaultWirelessMode :440-443), never overclocked (:347-351), no parallel
        // limit (:386). A run is 100 batches in max(1, 512 or 1280 / p) ticks, p the overclock parameter (:457-463,
        // ValueEnum :152-155); each batch costs its recipe EU/t x ticks x p (x 2 as a stellar forge) (:451-455), and
        // the run p Critical Photons (checkProcessing :399-407, consumePhoton :465-480), shared by its crafts.
        TstMachines.put(
            machines,
            "Miracle Door",
            machine().overclock(none())
                .fixedTicks(TstMachinesWireless::miracleDoorTicks)
                .power(c -> batchEu(c, miracleDoorTicks(c), overclockParameter(c) * (on(c, STELLAR_FORGE) ? 2 : 1)))
                .parallels(TstMachinesWireless::doorCrafts)
                .controls(OVERCLOCK_PARAMETER_CONTROL, CRAFTS_A_RUN_CONTROL, INGOT_MOLD_CONTROL)
                .upkeep(c -> List.of(Upkeep.perRun(CRITICAL_PHOTON, overclockParameter(c)))));
        // MM_IndistinctTentaclePrototypeMK2: the controller is its own perfect execution core, never overclocked
        // (:308-312) and unlimited (MultiExecutionCoreMachineBase.setupProcessingLogicWirelessEU :727-730). done()
        // takes recipe EU/t x ticks from the network and sets 20 ticks (:317-336), which the controller counts 1 to 20
        // and ends on the tick after (runExecutionCoreTick :280-309): a batch every 21 ticks.
        TstMachines.put(
            machines,
            "Indistinct Tentacle Prototype MK-II",
            machine().overclock(none())
                .fixedTicks(c -> MK2_TICKS)
                .power(c -> batchEu(c, MK2_TICKS, 1))
                .parallels(Integer.MAX_VALUE));
        // TST_HyperThermalConvector: up to 128 (heat exchange) or 16 (cooling) batches of unlimited parallels
        // (:298-301), the run 20 ticks whatever the recipe (:313-336); no energy hatch, recipes at 0 EU/t.
        TstMachines.put(
            machines,
            "Hyper Thermal Convector",
            machine().overclock(none())
                .fixedTicks(c -> 20)
                .parallels(Integer.MAX_VALUE));
        // TST_LargeSteamForgeHammer: 16 parallels a casing tier (:118), V[0] x parallels EU/t to spend
        // (MTESteamMultiBlockBase :176-183); no overclock, 1.33F steam a recipe EU (TST_SteamMultiMachineBase :86-98).
        TstMachines.put(
            machines,
            "Large Steam Forge Hammer",
            machine().overclock(none())
                .steamPerEu(1.33F)
                .parallels(c -> steamParallels(c, 16 * steamCasing(c), 8))
                .controls(STEAM_CASING_CONTROL)
                .recipeGate(c -> steamGate(c, 16 * steamCasing(c), 8)));
        // TST_LargeSteamAlloySmelter: 48 parallels (:120-123) at getTierRecipes 3, V[3] = 512 (:153-157).
        TstMachines.put(
            machines,
            "Large Steam Alloy Smelter",
            machine().overclock(none())
                .steamPerEu(1.33F)
                .parallels(c -> steamParallels(c, 48, 512))
                .recipeGate(c -> steamGate(c, 48, 512)));
        // TST_SwelegfyrBlastFurnace (and its legacy controller): 1 / 3.9F, 4096 parallels, 256 in passive mode
        // (:479-489); EU x 0.9^((heat - recipe heat) / 900) (:588-590) and a perfect overclock per 1800 K over the
        // recipe's heat (:597-602), the heat the furnace holds now (a setting). Blazing Pyrotheum every 20 running
        // ticks: 1000 L, or heat / 5 L in passive mode (onRunningTick :749-766).
        TstMachines.put(
            machines,
            "Swelegfyr Blast Furnace",
            machine().overclock(c -> perfectThenNormal(heatSurplus(c) / 1800))
                .speed(TstMachines.speedOf(1f / 3.9f))
                .power(c -> (float) Math.pow(0.9, heatSurplus(c) / 900))
                .parallels(c -> passive(c) ? 256 : 4096)
                .controls(COIL_CONTROL, SBF_MODE_CONTROL, SBF_HEAT_CONTROL, SBF_GLASS_CONTROL)
                .recipeGate(TstMachinesWireless::swelegfyrGate)
                .upkeep(c -> List.of(Upkeep.perSecond(PYROTHEUM, pyrotheum(c)))));
    }

    // region Wireless runs

    /** The MK-II controller's batch: 20 ticks set, counted 1 to 20, ended on the 21st. */
    static final int MK2_TICKS = 21;

    /**
     * {@code hatches} (an entry of {@link TstMachines} on energy hatches) or, with {@code power} past its first option,
     * WirelessEnergyMultiMachineBase's wireless mode: {@link #BATCHES} batches of {@code perBatch} parallels (the
     * entry's own when null) in {@code ticks}, never overclocked (OverclockCalculator.ofNoOverclock, so neither speed
     * nor EU modifier applies), each costing recipe EU/t x recipe ticks x {@code extra} from the network
     * (WirelessEnergyMultiMachineBase :141-198). The entry's gates still apply; {@code wirelessGate} adds the
     * structure wireless mode needs.
     */
    static Behaviour orWireless(final Behaviour hatches, final Web.Control power, final ToIntFunction<Context> ticks,
        final ToIntFunction<Context> extra, @Nullable final Coefficient perBatch,
        @Nullable final Function<Context, String> wirelessGate) {
        final Coefficient eu = hatches.power, parallels = hatches.parallels;
        final Function<Context, String> gate = hatches.recipeGate;
        final List<Web.Control> controls = new ArrayList<>();
        if (hatches.controls != null) controls.addAll(hatches.controls);
        controls.add(power);
        return hatches.fixedTicks(c -> wireless(c) ? ticks.applyAsInt(c) : 0)
            .power(
                c -> wireless(c) ? batchEu(c, ticks.applyAsInt(c), extra.applyAsInt(c))
                    : MachineTable.resolve(eu, c, 1))
            .parallels(
                c -> wireless(c) ? batches(MachineTable.resolve(perBatch != null ? perBatch : parallels, c, 1))
                    : MachineTable.resolve(parallels, c, 1))
            .controls(controls)
            .recipeGate(c -> {
                final String own = gate != null ? gate.apply(c) : null;
                if (own != null || !wireless(c) || wirelessGate == null) return own;
                return wirelessGate.apply(c);
            });
    }

    /** The power setting past its first option (energy hatches). */
    static boolean wireless(final Context c) {
        return c.tier(WIRELESS) >= 1;
    }

    /** The Indistinct Tentacle's third power option: an Astral Array Fabricator in the controller slot. */
    static boolean astralArray(final Context c) {
        return c.tier(WIRELESS) == 2;
    }

    /**
     * A wireless batch's EU over its run, a parallel's EU/t factor: recipe EU/t x recipe ticks x {@code extra} paid
     * once (ceil(EU/t x parallels) is whole: EU/t is), spread over {@code ticks}.
     */
    static double batchEu(final Context c, final int ticks, final int extra) {
        if (c.recipe == null || !(c.recipe.durationTicks > 0)) return 1;
        return c.recipe.durationTicks * extra / ticks;
    }

    /** {@link #BATCHES} batches of {@code perBatch}, counted to 2^31 - 1 (WebEffect's cap). */
    static double batches(final double perBatch) {
        return Math.min((double) BATCHES * perBatch, Integer.MAX_VALUE);
    }

    /** TST_IndistinctTentacle :231-257: wireless needs glass and component casing of UMV (casing meta 11) or better. */
    static String tentacleGate(final Context c) {
        return c.tier(TstControls.COMPONENT_CASING) < 11 ? CASING_GATE : null;
    }

    /** GT_TileEntity_Silksong :268-274: wireless from 94 sections. */
    static String silksongGate(final Context c) {
        return Math.max(1, (int) c.value(TstControls.PIECES)) < 94 ? SECTIONS_GATE : null;
    }

    /** The overclock parameter: 1 or more. */
    static int overclockParameter(final Context c) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, c.value(OVERCLOCK_PARAMETER)));
    }

    /** getWirelessModeProcessingTime: max(1, 1280 (stellar forge) or 512 (alloy smelter) / p), whole ticks. */
    static int miracleDoorTicks(final Context c) {
        return Math.max(1, (on(c, STELLAR_FORGE) ? 1280 : 512) / overclockParameter(c));
    }

    /** The crafts a Miracle Door run finds: the setting's number, or no limit ({@link #batches}) at 0. */
    static double doorCrafts(final Context c) {
        final double crafts = Math.floor(c.value(CRAFTS_A_RUN));
        final double unlimited = batches(Integer.MAX_VALUE);
        return crafts >= 1 ? Math.min(crafts, unlimited) : unlimited;
    }

    /** Whether the card's Miracle Door has a White Dwarf Mold (Ingot) in its input bus. */
    public static boolean ingotMold(final Web.Recipe recipe, final Web.Node node) {
        final Web.Recipe effective = recipe.machineType != null ? RecipeRules.applyHandler(recipe, node) : recipe;
        return "Miracle Door".equals(effective.machineType) && MachineEffects.context(effective, node)
            .tier(INGOT_MOLD) == 1;
    }

    /**
     * turnToIngotRecipe :493-516, for a molten output of {@code litres} with an ingot: the ingots it casts (litres /
     * 144) and the litres left molten, or null when it stays as it is (under 144 L).
     */
    @Nullable
    public static int[] ingots(final int litres) {
        if (litres < 144) return null;
        final int ingots = litres / 144;
        return new int[] { ingots, litres - 144 * ingots };
    }

    private static boolean on(final Context c, final String recipeMap) {
        return recipeMap.equals(c.recipeMap);
    }

    // endregion

    // region Steam

    /**
     * The recipe's EU/t: a steam machine's card draws none (RecipeRules.applyHandler), but the handler keeps it on its
     * profile (the template's eutMultiplier 1 in eto-tst-wireless.json).
     */
    static double steamEut(final Context c) {
        if (c.recipe == null || c.recipe.machineProfile == null || c.recipe.machineProfile.eut == null) return 0;
        return Math.abs(c.recipe.machineProfile.eut);
    }

    /** The casing tier, bronze 1 or steel 2. */
    static int steamCasing(final Context c) {
        return c.tier(STEAM_CASING) + 1;
    }

    /**
     * GTCM_ParallelHelper :86-90, :160-161: as many of {@code parallels} as V x parallels EU/t pays for, each billed
     * its recipe EU/t (the 1.33F lands only on the steam).
     */
    static double steamParallels(final Context c, final int parallels, final long voltage) {
        final double eut = steamEut(c);
        if (!(eut > 0)) return parallels;
        return Math.min(parallels, Math.floor(voltage * parallels / (long) Math.ceil(eut)));
    }

    /** A recipe above V x parallels EU/t never starts (insufficientPower). */
    static String steamGate(final Context c, final int parallels, final long voltage) {
        return steamEut(c) > voltage * parallels ? STEAM_GATE : null;
    }

    // endregion

    // region Heat

    /** Passive mode: 256 parallels, the heat rising toward the maximum. */
    static boolean passive(final Context c) {
        return c.tier(SBF_MODE) == 1;
    }

    /** The coil's heat, HeatingCoilLevel.getHeat(). */
    static int coilHeat(final Context c) {
        final List<MachineControls.Coil> coils = MachineControls.HEATING_COIL_TIERS;
        return coils.get(Math.max(0, Math.min(coils.size() - 1, TstMachines.coil(c))))
            .heat();
    }

    /** checkMachine :331: floor(coil heat^1.08 / 100) x 100 + 1, the most passive heating reaches. */
    static int maxHeat(final int coilHeat) {
        return (int) (Math.floor(Math.pow(coilHeat, 1.08) / 100) * 100 + 1);
    }

    /**
     * The heat the furnace holds: the setting, between the coil's heat (checkMachine :330 raises it there, normal mode
     * decays toward it, :819-831) and the maximum (passive mode climbs 5 K a second toward it, :749-766); 0 is the
     * mode's steady heat.
     */
    static int heat(final Context c) {
        final int coil = coilHeat(c), max = maxHeat(coil);
        final double typed = c.value(SBF_HEAT);
        if (!(typed > 0)) return passive(c) ? max : coil;
        return (int) Math.max(coil, Math.min(max, typed));
    }

    /** Heat over the recipe's, whole K (validateRecipe :588-589 divides it as ints). */
    static int heatSurplus(final Context c) {
        final int recipeHeat = c.recipeSpecialValue != null ? c.recipeSpecialValue : 0;
        return Math.max(heat(c) - recipeHeat, 0);
    }

    /**
     * checkMachine :333-346: under UMV glass (12) an energy hatch above the glass fails the structure; :354 and
     * validateRecipe :568: a recipe hotter than the coil's heat + 100 K per tier of the dominant hatch over MV never
     * starts. The card's hatches are one tier, its run tier.
     */
    static String swelegfyrGate(final Context c) {
        final int hatch = Math.max(0, c.runTier), glass = TstMachines.glass(c);
        if (glass < 12 && hatch > glass) return GLASS_GATE;
        final int recipeHeat = c.recipeSpecialValue != null ? c.recipeSpecialValue : 0;
        return recipeHeat > coilHeat(c) + 100 * Math.max(hatch - 2, 0) ? HEAT_GATE : null;
    }

    /** Litres of Blazing Pyrotheum a second it runs: 1000, or in passive mode the heat / 5 (as ints). */
    static int pyrotheum(final Context c) {
        return passive(c) ? heat(c) / 5 : 1000;
    }

    // endregion
}
