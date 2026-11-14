package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstCoresArrays.FRAME;
import static com.gtnhplanner.machines.eto.TstCoresArrays.MACHINES;
import static com.gtnhplanner.machines.eto.TstCoresArrays.MEGA_ARRAY;
import static com.gtnhplanner.machines.eto.TstCoresArrays.PARALLELISM;
import static com.gtnhplanner.machines.eto.TstCoresArrays.PROCESSING_ARRAY;
import static com.gtnhplanner.machines.eto.TstCoresArrays.TIER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.gtnhplanner.machines.web.HandlerData;
import com.gtnhplanner.machines.web.NodeMath;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's arrays on cards, through the whole port (NodeMath), each case worked by hand from the
 * 0.8.1 tag (TST_ProcessingArray = PA, TST_BigBroArray = BBA, PAHelper) and GregTech 5.09.54.265's ProcessingLogic as
 * TST's GTCM_ParallelHelper runs it: each parallel billed ceil(EU/t x EU modifier) (:86-90), a sub-tick bonus on the
 * cap (:103), then OverclockCalculator (:385-437). Without amperage overclocks (the PA) the steps are capped at
 * max(log4ceil(V / 8), 1) - max(log4ceil(EU/t / 8), 1), so a recipe under 8 EU/t counts as LV. A card's numbers are per
 * parallel; its hatches must carry the whole run.
 */
class TstCoresArraysTest {

    // region Harness

    /** A recipe on a map as WebCards.recipe builds it from the NEI recipe. */
    private static Web.Recipe recipe(final String mapId, final double eut, final double ticks) {
        final HandlerData.MapEntry entry = HandlerData.map(mapId);
        assertNotNull(entry, mapId);
        final Web.Recipe r = new Web.Recipe();
        r.id = mapId + ":test";
        r.name = "test";
        r.kind = "gregtech_machine";
        r.category = "gregtech";
        r.machineType = entry.name;
        r.durationTicks = ticks;
        r.eut = eut;
        r.minimumTier = HandlerData.recipeMinimumTier(eut);
        r.specialValue = 0.0;
        r.nei = new Web.Nei();
        r.nei.additionalInfo = List.of("Special value: 0");
        r.source = new Web.Source();
        r.source.recipeMap = entry.name;
        r.metadata = new JsonObject();
        r.metadata.addProperty("recipeMapId", entry.id);
        r.metadata.addProperty("specialValue", 0);
        r.inputs = List.of();
        r.outputs = List.of();
        r.machineConfigControls = HandlerData.recipeControls(entry, 0);
        r.machineHandlers = HandlerData.handlers(entry, r.minimumTier, r.durationTicks, r.eut, r.machineConfigControls);
        return r;
    }

    /** One {@code machine} on hatches of {@code tier} with {@code amps} working amps, at these settings. */
    private static Web.Node node(final String machine, final String tier, final double amps,
        final Map<String, String> settings, @Nullable final String coil) {
        final Web.Node n = new Web.Node();
        n.overclockTier = tier;
        n.hatchVoltageTier = tier;
        n.hatchAmps = amps;
        n.powerInputMode = "amps";
        n.machineHandlerId = "tst-" + TstControls.key(machine);
        n.machineConfigTiers = new LinkedHashMap<>(settings);
        n.coilTier = coil;
        n.parallel = 1.0;
        n.machineCount = 1.0;
        return n;
    }

    private static NodeMath.Result run(final String mapId, final double eut, final double ticks, final String machine,
        final String tier, final double amps, final Map<String, String> settings, @Nullable final String coil) {
        return NodeMath.compute(recipe(mapId, eut, ticks), node(machine, tier, amps, settings, coil));
    }

    private static NodeMath.Result pa(final String mapId, final double eut, final double ticks, final String tier,
        final double amps, final Map<String, String> settings) {
        return run(mapId, eut, ticks, PROCESSING_ARRAY, tier, amps, settings, null);
    }

    private static NodeMath.Result mega(final String mapId, final double eut, final double ticks, final String tier,
        final double amps, final Map<String, String> settings, @Nullable final String coil) {
        return run(mapId, eut, ticks, MEGA_ARRAY, tier, amps, settings, coil);
    }

    /** The run as the game's: the machine picked, ticks, EU/t per parallel, parallels, and no refusal. */
    private static void expect(final NodeMath.Result r, final String machine, final double ticks, final double eut,
        final double parallels) {
        assertEquals(machine, r.effectiveRecipe().machineType);
        assertNull(r.stall(), machine);
        assertEquals(
            ticks,
            r.overclock()
                .durationTicks(),
            machine + " ticks");
        assertEquals(
            eut,
            r.overclock()
                .eut(),
            1e-4,
            machine + " EU/t");
        assertEquals(parallels, r.machineParallels(), machine + " parallels");
    }

    // endregion

    // region TST Processing Array

    /**
     * Sixteen LV macerators: V = V[1] x 1 A = 32, amperage 16 (PA :197-201, PAHelper :77-80). 2 EU/t over 16
     * parallels is 32, and LV against a sub-LV recipe is no step: 400 ticks at 32 EU/t, 16 parallels.
     */
    @Test
    void lvMaceratorsRunAtTheirOwnTier() {
        expect(
            pa("gt.recipe.macerator", 2, 400, "LV", 1, Map.of(TIER, "lv", MACHINES, "16")),
            PROCESSING_ARRAY,
            400,
            2,
            16);
    }

    /**
     * Sixteen HV macerators on the same recipe: V x A = 8192 is four steps over 32, but without amperage overclocks
     * only max(log4ceil(512 / 8), 1) - max(log4ceil(2 / 8), 1) = 3 - 1 = 2 (OverclockCalculator :419-426): 100 ticks
     * at 32 x 4^2 = 512 EU/t, 32 a parallel.
     */
    @Test
    void theVoltageTierCapsTheOverclocks() {
        expect(
            pa("gt.recipe.macerator", 2, 400, "HV", 1, Map.of(TIER, "hv", MACHINES, "16")),
            PROCESSING_ARRAY,
            100,
            32,
            16);
    }

    /**
     * The thermal centrifuge's map is 2 A (RecipeMaps :167): an LV one is 64 V, so a 48 EU/t recipe runs
     * (validateRecipe
     * :190) where an LV macerator refuses it. At HV, 1024 V is log4ceil(128) = 4 against the recipe's 2: two steps,
     * 125 ticks at 12288 EU/t; an HV macerator (512 V, log4ceil(64) = 3) takes one, 250 ticks at 3072.
     */
    @Test
    void theThermalCentrifugesTwoAmpsLiftItsVoltage() {
        expect(
            pa("gt.recipe.thermalcentrifuge", 48, 500, "MV", 2, Map.of(TIER, "lv", MACHINES, "4")),
            PROCESSING_ARRAY,
            500,
            48,
            4);
        assertEquals(
            TstCoresArrays.PA_VOLTAGE_GATE,
            pa("gt.recipe.macerator", 48, 500, "MV", 2, Map.of(TIER, "lv", MACHINES, "4")).stall());
        expect(
            pa("gt.recipe.thermalcentrifuge", 48, 500, "IV", 2, Map.of(TIER, "hv", MACHINES, "16")),
            PROCESSING_ARRAY,
            125,
            768,
            16);
        expect(
            pa("gt.recipe.macerator", 48, 500, "IV", 2, Map.of(TIER, "hv", MACHINES, "16")),
            PROCESSING_ARRAY,
            250,
            192,
            16);
    }

    /**
     * The arc furnace sets 3 A (LoaderMetaTileEntities :7252): 96 V at LV carries 90 EU/t, its own EU/t unmultiplied.
     */
    @Test
    void theArcFurnacesThreeAmps() {
        expect(
            pa("gt.recipe.arcfurnace", 90, 200, "HV", 2, Map.of(TIER, "lv", MACHINES, "8")),
            PROCESSING_ARRAY,
            200,
            90,
            8);
    }

    /**
     * Ten ZPM machines on an 8 EU/t, 4-tick recipe: log4ceil(16384) = 7 against 1 is six steps, four past one tick,
     * so the cap grows ceil(2^4 x 2^2 / 4) = 16-fold to 160 (calculateMultiplierUnderOneTick); the overclock bills the
     * original 10 parallels, 80 x 4^6 = 327680 EU/t, at one tick.
     */
    @Test
    void underOneTickTheCapGrows() {
        expect(
            pa("gt.recipe.macerator", 8, 4, "UV", 1, Map.of(TIER, "zpm", MACHINES, "10")),
            PROCESSING_ARRAY,
            1,
            2048,
            160);
    }

    /** Electric furnaces run GregTech's furnace recipe, 128 ticks at 4 EU/t (FurnaceBackend :41-42), on smelting. */
    @Test
    void smeltingRunsTheFurnaceRecipe() {
        expect(pa("smelting", 0, 200, "MV", 2, Map.of(TIER, "lv", MACHINES, "64")), PROCESSING_ARRAY, 128, 4, 64);
    }

    /** GT++'s Cold Trap comes in IV and ZPM only (GregtechLFTR): there is no LuV one to put in. */
    @Test
    void aTierTheMachineLacksIsRefused() {
        assertEquals(
            TstCoresArrays.NO_TIER_GATE,
            pa("gt.recipe.coldtrap", 480, 100, "LuV", 1, Map.of(TIER, "luv", MACHINES, "4")).stall());
    }

    /**
     * Unset, a card holds a full stack of the lowest tier that runs the recipe: 100 EU/t is MV (128 V), 64 of them,
     * log4ceil(16) = log4ceil(12) = 2, no step: 400 ticks, 64 parallels.
     */
    @Test
    void unsetItHoldsAStackOfTheLowestTierThatRunsIt() {
        expect(pa("gt.recipe.macerator", 100, 400, "IV", 1, Map.of()), PROCESSING_ARRAY, 400, 100, 64);
    }

    // endregion

    // region MegaArray

    /**
     * maxParallelism with four add-ons (BBA :1104-1112) and the machines insertion takes (:496); at Mark 4 the max is
     * ((2^31 - 1) << 11) / 5 x 5. The parallel cap (int) min(n x 2^tier, max) stops at Integer.MAX_VALUE (:691-693).
     */
    @Test
    void theAddOnsCapsAndTheMachinesTheyTake() {
        final long[] max = { 1280, 5120, 20480, 5242880, 4398046509055L };
        final int[] cap = { 640, 1280, 2560, 5120, 2147483646 };
        for (int tier = 1; tier <= 5; tier++) {
            assertEquals(max[tier - 1], TstCoresArrays.maxParallelism(tier), "max at " + tier);
            assertEquals(cap[tier - 1], TstCoresArrays.machineCap(tier), "machines at " + tier);
        }
        assertEquals(2147483616, TstCoresArrays.megaParallels(67108863, 5));
        assertEquals(Integer.MAX_VALUE, TstCoresArrays.megaParallels(67108864, 5));
        assertEquals(81920, TstCoresArrays.megaParallels(5120, 4));
    }

    /**
     * Unset: Parallelism Casing Mark 0 (tier 1), Cupronickel, a stack of LV machines: 128 parallels at (float) 0.66
     * (:498). 2 x 128 = 256 of the HV hatch's 512 is no step: (int) (400 x 0.66000003) = 264 ticks, 256 EU/t.
     */
    @Test
    void unsetItIsAStackOnTheFirstCasing() {
        expect(mega("gt.recipe.macerator", 2, 400, "HV", 1, Map.of(), null), MEGA_ARRAY, 264, 2, 128);
    }

    /**
     * Mark 3 (tier 4), HSS-G (coil tier 4), 100 EV assemblers: 1600 parallels at speed (float) 0.66^4 = 0.18974736
     * and EU (float) 0.9^4 = 0.65609998 (:498-499). Each parallel is billed ceil(120 x 0.65609998) = 79 of 65536: 829
     * run, 829 x 78.732 = 65268.83 is no step: 65269 EU/t over (int) 37.95 = 37 ticks.
     */
    @Test
    void theCasingsSpeedAndTheCoilsDiscount() {
        expect(
            mega(
                "gt.recipe.assembler",
                120,
                200,
                "LuV",
                2,
                Map.of(TIER, "ev", MACHINES, "100", PARALLELISM, "parallelism-casing-mark-3"),
                "hss_g"),
            MEGA_ARRAY,
            37,
            65269.0 / 829,
            829);
    }

    /**
     * Four LV machines on Mark 0, 8 parallels of a one-tick 8 EU/t recipe: 0.66 ticks, and 2048 over 64 is two steps,
     * so the cap grows ceil(2^2 / 0.66) = 7-fold to 56; the overclock bills 8: 64 x 16 = 1024 EU/t at one tick.
     */
    @Test
    void megaArrayUnderOneTickTheCapGrows() {
        expect(
            mega("gt.recipe.assembler", 8, 1, "EV", 1, Map.of(TIER, "lv", MACHINES, "4"), null),
            MEGA_ARRAY,
            1,
            1024.0 / 56,
            56);
    }

    /**
     * On Mark 0 the four add-ons cap 1280 parallels: 639 machines are 1278, 641 still 1280 (a casing swapped down
     * after insertion leaves more machines than it takes). 2 x 1280 of 16384 is one step: 132 ticks, 8 a parallel.
     */
    @Test
    void theAddOnsCapTheParallels() {
        expect(
            mega("gt.recipe.macerator", 2, 400, "IV", 2, Map.of(TIER, "lv", MACHINES, "639"), null),
            MEGA_ARRAY,
            132,
            8,
            1278);
        expect(
            mega("gt.recipe.macerator", 2, 400, "IV", 2, Map.of(TIER, "lv", MACHINES, "641"), null),
            MEGA_ARRAY,
            132,
            8,
            1280);
    }

    /**
     * A recipe over the machines' 8 x 4^tier is refused (:686-689): LV machines take 32 EU/t, not 33. At 32, the EV
     * hatch's 2048 pays 64 of the 128: 66 ticks.
     */
    @Test
    void theMachinesTierGatesTheRecipe() {
        assertEquals(
            TstCoresArrays.MEGA_TIER_GATE,
            mega("gt.recipe.macerator", 33, 100, "EV", 1, Map.of(TIER, "lv"), null).stall());
        expect(mega("gt.recipe.macerator", 32, 100, "EV", 1, Map.of(TIER, "lv"), null), MEGA_ARRAY, 66, 32, 64);
    }

    /** Arcanite frames take machines up to tier 1 + 4, IV (:1127, :1151); Quantum takes all. */
    @Test
    void theFramesGateTheMachineTier() {
        final Map<String, String> luv = Map.of(TIER, "luv", FRAME, "arcanite-frame-box");
        assertEquals(TstCoresArrays.FRAME_GATE, mega("gt.recipe.assembler", 120, 200, "IV", 1, luv, null).stall());
        assertNull(
            mega("gt.recipe.assembler", 120, 200, "IV", 1, Map.of(TIER, "iv", FRAME, "arcanite-frame-box"), null)
                .stall());
        assertNull(mega("gt.recipe.assembler", 120, 200, "IV", 1, Map.of(TIER, "luv"), null).stall());
    }

    // endregion
}
