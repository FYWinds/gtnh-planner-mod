package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.COMPONENT_CASING;
import static com.gtnhplanner.machines.eto.TstControls.PIECES;
import static com.gtnhplanner.machines.eto.TstWirelessControls.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import com.gtnhplanner.machines.web.MachineEffects;
import com.gtnhplanner.machines.web.NodeMath;
import com.gtnhplanner.machines.web.PowerReport;
import com.gtnhplanner.machines.web.Upkeep;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's wireless, steam and heat machines on cards, through the whole port (NodeMath): one
 * hand-worked case per machine and setting, each from the controller's own lines (paths under common/machine of the
 * TST source at 0.8.1, the pack's config). A wireless batch is never overclocked (OverclockCalculator.ofNoOverclock
 * :94-99, :399-403): it costs ceil(EU/t x parallels) x the recipe's own ticks, times the machine's extra multiplier
 * (WirelessEnergyMultiMachineBase :180-186), and a run is 100 batches (DefaultCycleNum) in a fixed time; the card's
 * EU/t a parallel is that EU over the run. Unlimited parallels are counted as 2^31 - 1 a run.
 */
class TstWirelessMachinesTest {

    private static final String NANO_CORE = "tst.recipe.DeployedNanoCoreRecipeMap.name",
        STELLAR_FORGE = "tst.recipe.StellarForgeRecipeMap.name",
        STELLAR_ALLOY = "tst.recipe.StellarForgeAlloySmelterRecipeMap.name",
        HEAT_EXCHANGE = "tst.recipe.RapidHeatExchangeRecipeMap.name",
        COOLING = "tst.recipe.RapidCoolingDownRecipeMap.name";

    private static final double UNLIMITED = Integer.MAX_VALUE;

    // region Harness

    /** A recipe on a map as WebCards.recipe builds it from the NEI recipe (as TstMachinesTest's). */
    private static Web.Recipe recipe(final String mapId, final double eut, final double ticks, final double special) {
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
        r.specialValue = special;
        r.nei = new Web.Nei();
        r.nei.additionalInfo = List.of("Special value: " + (long) special);
        r.source = new Web.Source();
        r.source.recipeMap = entry.name;
        r.metadata = new JsonObject();
        r.metadata.addProperty("recipeMapId", entry.id);
        r.metadata.addProperty("specialValue", special);
        r.inputs = List.of();
        r.outputs = List.of();
        r.machineConfigControls = HandlerData.recipeControls(entry, special);
        r.machineHandlers = HandlerData.handlers(entry, r.minimumTier, r.durationTicks, r.eut, r.machineConfigControls);
        return r;
    }

    /**
     * One {@code machine} on hatches of {@code tier} (null: none, as WebCards gives a steam machine) with {@code amps}
     * working amps, at these settings.
     */
    private static Web.Node node(final String machine, @Nullable final String tier, final double amps,
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

    private static NodeMath.Result run(final String mapId, final double eut, final double ticks, final double special,
        final String machine, @Nullable final String tier, final Map<String, String> settings,
        @Nullable final String coil) {
        return NodeMath.compute(recipe(mapId, eut, ticks, special), node(machine, tier, 1, settings, coil));
    }

    private static NodeMath.Result run(final String mapId, final double eut, final double ticks, final String machine,
        @Nullable final String tier, final Map<String, String> settings) {
        return run(mapId, eut, ticks, 0, machine, tier, settings, null);
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

    /** What one steam machine burns, L/t. */
    private static double steam(final String mapId, final double eut, final double ticks, final String machine,
        final Map<String, String> settings) {
        final PowerReport.Steam s = PowerReport
            .steam(recipe(mapId, eut, ticks, 0), node(machine, null, 1, settings, null));
        assertNotNull(s, machine);
        return s.drawSteamPerTick();
    }

    /** The structure or recipe gate a card at these settings reports, or null. */
    private static String gate(final String mapId, final double eut, final double ticks, final double special,
        final String machine, final String tier, final Map<String, String> settings, final String coil) {
        return PowerReport.report(recipe(mapId, eut, ticks, special), node(machine, tier, 1, settings, coil))
            .recipeGateReason();
    }

    // endregion

    // region Always wireless

    /**
     * TST_MiracleDoor, as a stellar forge (getRecipeMap :298-301, machineMode 1): a run of 1280 ticks (ValueEnum
     * :154-155, 64 s) / the overclock parameter, each batch's EU x the parameter x 2 (:451-463). 1920 EU/t x 100
     * ticks: 384,000 EU a craft over 1280 ticks = 300 EU/t a parallel, the hatches' ULV beside the point. Parameter 16:
     * 80 ticks and 1920 x 100 x 32 / 80 = 76,800. As an alloy smelter (512 ticks, x 1): 480 x 100 / 512 = 93.75; a
     * parameter of 512 leaves one tick (max(1, 512 / 512)), 513 rounds to 0 and is held at 1.
     */
    @Test
    void miracleDoor() {
        final String m = "Miracle Door";
        expect(run(STELLAR_FORGE, 1920, 100, m, "ULV", Map.of()), m, 1280, 300, UNLIMITED);
        expect(run(STELLAR_FORGE, 1920, 100, m, "ULV", Map.of(OVERCLOCK_PARAMETER, "16")), m, 80, 76800, UNLIMITED);
        expect(run(STELLAR_ALLOY, 480, 100, m, "ULV", Map.of()), m, 512, 93.75, UNLIMITED);
        expect(run(STELLAR_ALLOY, 480, 100, m, "ULV", Map.of(OVERCLOCK_PARAMETER, "256")), m, 2, 6_144_000, UNLIMITED);
        expect(run(STELLAR_ALLOY, 480, 100, m, "ULV", Map.of(OVERCLOCK_PARAMETER, "513")), m, 1, 24_624_000, UNLIMITED);
    }

    /**
     * A Miracle Door run takes p Critical Photons (checkProcessing :399-407, consumePhoton :465-480) whatever it
     * crafts, so the card shares them only over a number of crafts a run: 64 crafts at p 16 run in 80 ticks and share
     * 16 photons, a quarter each; one door fed so runs 64 x 20 / 80 = 16 crafts a second and burns 4 photons a second.
     * At 0 the run's crafts are unlimited, as the game's.
     */
    @Test
    void miracleDoorCraftsAndPhotons() {
        final String m = "Miracle Door";
        final Map<String, String> set = Map.of(OVERCLOCK_PARAMETER, "16", CRAFTS_A_RUN, "64");
        expect(run(STELLAR_FORGE, 1920, 100, m, "ULV", set), m, 80, 76800, 64);
        expect(run(STELLAR_FORGE, 1920, 100, m, "ULV", Map.of(CRAFTS_A_RUN, "0")), m, 1280, 300, UNLIMITED);
        final List<Upkeep> photons = MachineEffects
            .upkeep(recipe(STELLAR_FORGE, 1920, 100, 0), node(m, "ULV", 1, set, null));
        assertEquals(List.of(new Upkeep("item:TwistSpaceTechnology:MetaItem01@13", 16, true, true)), photons);
        assertEquals(
            0.25,
            photons.get(0)
                .perCraft(80, 64));
        assertEquals(
            4,
            64 * 20
                / 80.0
                * photons.get(0)
                    .perCraft(80, 64));
    }

    /**
     * turnToIngotRecipe :493-516: a molten output with an ingot casts litres / 144 ingots and leaves the rest molten;
     * under 144 L it stays as it is.
     */
    @Test
    void miracleDoorIngotMold() {
        assertNull(TstMachinesWireless.ingots(143));
        assertArrayEquals(new int[] { 1, 0 }, TstMachinesWireless.ingots(144));
        assertArrayEquals(new int[] { 2, 50 }, TstMachinesWireless.ingots(338));
        assertArrayEquals(new int[] { 10, 0 }, TstMachinesWireless.ingots(1440));
    }

    /**
     * MM_IndistinctTentaclePrototypeMK2: the controller's own perfect execution core takes recipe EU/t x ticks once
     * (done :317-336) and ends on the 21st tick (MultiExecutionCoreMachineBase.runExecutionCoreTick :280-309); no
     * overclock (:308-312), no parallel limit. 30720 EU/t x 200 ticks over 21 = 292,571.43 EU/t a parallel.
     */
    @Test
    void indistinctTentaclePrototypeMk2() {
        final String m = "Indistinct Tentacle Prototype MK-II";
        expect(run("gt.recipe.assembler", 30720, 200, m, "ULV", Map.of()), m, 21, 30720.0 * 200 / 21, UNLIMITED);
        expect(run("gg.recipe.precise_assembler", 7680, 400, m, "ULV", Map.of()), m, 21, 7680.0 * 400 / 21, UNLIMITED);
    }

    /** TST_HyperThermalConvector: 20 ticks a run whatever the recipe (:313-336), unlimited parallels (:298-301). */
    @Test
    void hyperThermalConvector() {
        final String m = "Hyper Thermal Convector";
        expect(run(HEAT_EXCHANGE, 0, 20, m, "ULV", Map.of()), m, 20, 0, UNLIMITED);
        expect(run(COOLING, 0, 40, m, "ULV", Map.of()), m, 20, 0, UNLIMITED);
    }

    // endregion

    // region Wireless modes

    /**
     * GT_TileEntity_Silksong without hatches from 94 sections (:268-274): 188 ticks a run (:336-338), 94 x Kanthal
     * (getCoilTier 2) x 32 = 6016 parallels a batch (:261), 100 batches; 120 EU/t x 400 ticks over 188 = 255.32 EU/t a
     * parallel. 93 sections run no wireless mode.
     */
    @Test
    void silksongWireless() {
        final String m = "Silksong";
        final String wireless = TstControls.key("Wireless (No Hatches)");
        expect(
            run("gt.recipe.wiremill", 120, 400, 0, m, "HV", Map.of(PIECES, "94", WIRELESS, wireless), "kanthal"),
            m,
            188,
            120.0 * 400 / 188,
            601_600);
        assertEquals(
            TstMachinesWireless.SECTIONS_GATE,
            run("gt.recipe.wiremill", 120, 400, 0, m, "HV", Map.of(PIECES, "93", WIRELESS, wireless), "kanthal")
                .stall());
    }

    /**
     * TST_IndistinctTentacle with UMV glass and casing and no hatches (:231-257): unlimited batches (:329-331) in 512
     * ticks, 30 x 400 / 512 = 23.4375 EU/t a parallel; an Astral Array Fabricator: 20 ticks at 64x (:436-453), 30 x 400
     * x 64 / 20 = 38,400. A UIV casing runs no wireless mode.
     */
    @Test
    void indistinctTentacleWireless() {
        final String m = "Indistinct Tentacle", assembler = "gt.recipe.assembler", none = "wireless-no-hatches";
        expect(
            run(assembler, 30, 400, m, "LV", Map.of(COMPONENT_CASING, "umv", WIRELESS, none)),
            m,
            512,
            23.4375,
            UNLIMITED);
        expect(
            run(
                assembler,
                30,
                400,
                m,
                "LV",
                Map.of(COMPONENT_CASING, "umv", WIRELESS, "wireless-with-astral-array-fabricator")),
            m,
            20,
            38_400,
            UNLIMITED);
        assertEquals(
            TstMachinesWireless.CASING_GATE,
            run(assembler, 30, 400, m, "LV", Map.of(COMPONENT_CASING, "uiv", WIRELESS, none)).stall());
    }

    /**
     * TST_ThermalEnergyDevourer with an Energised Tesseract (:181-185): unlimited (:229-234) at EU and speed 1
     * (:236-246), 128 ticks a run (:284-287); 120 x 300 / 128 = 281.25 EU/t a parallel, whatever the mode.
     */
    @Test
    void thermalEnergyDevourerWireless() {
        final String m = "Thermal Energy Devourer";
        final String tesseract = TstControls.key("Wireless (Energised Tesseract)");
        expect(
            run("gt.recipe.vacuumfreezer", 120, 300, m, "LV", Map.of(WIRELESS, tesseract)),
            m,
            128,
            281.25,
            UNLIMITED);
        expect(
            run(
                "gt.recipe.vacuumfreezer",
                120,
                300,
                m,
                "LV",
                Map.of(WIRELESS, tesseract, TstControls.DEVOURER_MODE, "devourer-mode")),
            m,
            128,
            281.25,
            UNLIMITED);
    }

    /**
     * TST_DeployedNanoCore without hatches (:358): unlimited (:375-377, :404), 128 ticks a run (:418-421); 30720 x 200
     * / 128 = 48,000 EU/t a parallel.
     */
    @Test
    void deployedNanoCoreWireless() {
        final String m = "Deployed Nano Core";
        expect(run(NANO_CORE, 30720, 200, m, "LV", Map.of(WIRELESS, "wireless-no-hatches")), m, 128, 48_000, UNLIMITED);
    }

    // endregion

    // region Steam

    /**
     * TST_LargeSteamForgeHammer: 16 parallels a casing tier (:118), V[0] x parallels EU/t to spend
     * (MTESteamMultiBlockBase :176-183, GTCM_ParallelHelper :160-161), steam ceil(EU/t x parallels x 1.33F) a tick
     * (TST_SteamMultiMachineBase :86-98, MTESteamMultiBlockBase :240-249 at efficiency 10000), the recipe's own ticks.
     * Bronze, 16 EU/t: 128 / 16 = 8 parallels, ceil(170.24) = 171 L/t; steel: 16 parallels, ceil(340.48) = 341; 2 EU/t
     * fills all 16 bronze ones, ceil(42.56) = 43. 130 EU/t is past bronze's 128 and runs once on steel's 256: 173 L/t.
     */
    @Test
    void largeSteamForgeHammer() {
        final String m = "Large Steam Forge Hammer";
        final Map<String, String> steel = Map.of(STEAM_CASING, "solid-steel-machine-casing");
        expect(run("gt.recipe.hammer", 16, 64, m, null, Map.of()), m, 64, 0, 8);
        assertEquals(171, steam("gt.recipe.hammer", 16, 64, m, Map.of()));
        expect(run("gt.recipe.hammer", 16, 64, m, null, steel), m, 64, 0, 16);
        assertEquals(341, steam("gt.recipe.hammer", 16, 64, m, steel));
        assertEquals(43, steam("gt.recipe.hammer", 2, 64, m, Map.of()));
        assertEquals(TstMachinesWireless.STEAM_GATE, run("gt.recipe.hammer", 130, 64, m, null, Map.of()).stall());
        expect(run("gt.recipe.hammer", 130, 64, m, null, steel), m, 64, 0, 1);
        assertEquals(173, steam("gt.recipe.hammer", 130, 64, m, steel));
    }

    /**
     * TST_LargeSteamAlloySmelter: 48 parallels (:120-123) on V[3] x 48 = 24,576 EU/t (:153-157). 16 EU/t: all 48,
     * ceil(1021.44) = 1022 L/t; 24,576 EU/t: one, ceil(32686.08) = 32,687; one EU/t more never starts.
     */
    @Test
    void largeSteamAlloySmelter() {
        final String m = "Large Steam Alloy Smelter";
        expect(run("gt.recipe.alloysmelter", 16, 200, m, null, Map.of()), m, 200, 0, 48);
        assertEquals(1022, steam("gt.recipe.alloysmelter", 16, 200, m, Map.of()));
        assertEquals(32_687, steam("gt.recipe.alloysmelter", 24576, 200, m, Map.of()));
        assertEquals(
            TstMachinesWireless.STEAM_GATE,
            run("gt.recipe.alloysmelter", 24577, 200, m, null, Map.of()).stall());
    }

    // endregion

    // region Heat

    /**
     * TST_SwelegfyrBlastFurnace on Naquadah coils (7201 K) and a 4500 K recipe of 120 EU/t, 1000 ticks, ZPM 1 A
     * (131,072 EU): 1 / 3.9F = 0.25641024 (:485-489), EU x (float) 0.9^((heat - 4500) / 900) (:588-590), a perfect
     * overclock per 1800 K (:597-602).
     * <ul>
     * <li>Normal mode at its steady heat, the coil's 7201 K (:330, :819-831): 0.9^3 = 0.729, ceil(87.48) = 88 a
     * parallel, 131072 / 88 = 1489 of 4096 (:479-483), no step, 256 ticks.</li>
     * <li>Normal mode held at 9000 K: 0.9^5 = 0.59049, ceil(70.86) = 71, 1846 parallels, 256 ticks.</li>
     * <li>Passive mode at its steady heat, the maximum floor(7201^1.08 / 100) x 100 + 1 = 14601 K (:331): 0.9^11 =
     * 0.3138106, 256 parallels draw 9640.26, 131072 / 9641 = 13: one step, a heat (perfect) one: 1000 x 0.2564 / 4 =
     * 64 ticks at 4 x 37.657 = 150.63 EU/t.</li>
     * <li>A heat typed below the coil's or above the maximum is held there.</li>
     * </ul>
     */
    @Test
    void swelegfyrBlastFurnace() {
        final String m = "Swelegfyr Blast Furnace", bf = "gt.recipe.blastfurnace", coil = "naquadah";
        final String passive = TstControls.key("Passive Mode");
        expect(run(bf, 120, 1000, 4500, m, "ZPM", Map.of(), coil), m, 256, 87.47999668121338, 1489);
        expect(run(bf, 120, 1000, 4500, m, "ZPM", Map.of(SBF_HEAT, "100"), coil), m, 256, 87.47999668121338, 1489);
        expect(run(bf, 120, 1000, 4500, m, "ZPM", Map.of(SBF_HEAT, "9000"), coil), m, 256, 70.85879802703857, 1846);
        expect(run(bf, 120, 1000, 4500, m, "ZPM", Map.of(SBF_MODE, passive), coil), m, 64, 150.62908172607422, 256);
        expect(
            run(bf, 120, 1000, 4500, m, "ZPM", Map.of(SBF_MODE, passive, SBF_HEAT, "99999"), coil),
            m,
            64,
            150.62908172607422,
            256);
        assertEquals(14_601, TstMachinesWireless.maxHeat(7201));
        assertEquals(3_201, TstMachinesWireless.maxHeat(1801));
        assertEquals(28_801, TstMachinesWireless.maxHeat(13501));
    }

    /**
     * Blazing Pyrotheum, each 20 running ticks (onRunningTick :749-766): 1000 L in normal mode; heat / 5 L (as ints)
     * in passive mode, 14601 / 5 = 2920 L at the Naquadah coil's steady maximum, 1800 L held at 9000 K. A craft
     * carries its run's share: normal mode 1000 x 256 / 20 over 1489 parallels, passive 2920 x 64 / 20 / 256 = 36.5 L.
     */
    @Test
    void swelegfyrPyrotheum() {
        final String m = "Swelegfyr Blast Furnace", bf = "gt.recipe.blastfurnace", coil = "naquadah";
        final String passive = TstControls.key("Passive Mode");
        final Upkeep normal = MachineEffects.upkeep(recipe(bf, 120, 1000, 4500), node(m, "ZPM", 1, Map.of(), coil))
            .get(0);
        assertEquals(new Upkeep("fluid:pyrotheum", 1000, false, true), normal);
        assertEquals(12800.0 / 1489, normal.perCraft(256, 1489), 1e-12);
        final Upkeep steady = MachineEffects
            .upkeep(recipe(bf, 120, 1000, 4500), node(m, "ZPM", 1, Map.of(SBF_MODE, passive), coil))
            .get(0);
        assertEquals(2920, steady.amount());
        assertEquals(36.5, steady.perCraft(64, 256), 1e-12);
        final Map<String, String> held = Map.of(SBF_MODE, passive, SBF_HEAT, "9000");
        assertEquals(
            1800,
            MachineEffects.upkeep(recipe(bf, 120, 1000, 4500), node(m, "ZPM", 1, held, coil))
                .get(0)
                .amount());
    }

    /**
     * checkMachine :333-346: under UMV glass an energy hatch above the glass fails the structure (ZPM glass holds ZPM
     * hatches, not UV ones; UIV glass not UMV ones; UMV glass any). :354 and validateRecipe :568: a recipe hotter
     * than the coil's heat + 100 K a tier of the dominant hatch over MV never starts: Nichrome (3601 K) takes a 4401 K
     * recipe on UEV hatches (3601 + 800), not on UHV ones (4301), and not a 4402 K one on UEV.
     */
    @Test
    void swelegfyrGates() {
        final String m = "Swelegfyr Blast Furnace", bf = "gt.recipe.blastfurnace", glass = TstControls.GLASS;
        final String glassGate = TstMachinesWireless.GLASS_GATE, heatGate = TstMachinesWireless.HEAT_GATE;
        assertEquals(glassGate, gate(bf, 120, 1000, 4500, m, "UV", Map.of(glass, "zpm"), "naquadah"));
        assertNull(gate(bf, 120, 1000, 4500, m, "ZPM", Map.of(glass, "zpm"), "naquadah"));
        assertEquals(glassGate, gate(bf, 120, 1000, 4500, m, "UMV", Map.of(glass, "uiv"), "naquadah"));
        assertNull(gate(bf, 120, 1000, 4500, m, "UXV", Map.of(glass, "umv"), "naquadah"));
        assertEquals(heatGate, gate(bf, 120, 1000, 4401, m, "UHV", Map.of(), "nichrome"));
        assertNull(gate(bf, 120, 1000, 4401, m, "UEV", Map.of(), "nichrome"));
        assertEquals(heatGate, gate(bf, 120, 1000, 4402, m, "UEV", Map.of(), "nichrome"));
    }

    // endregion
}
