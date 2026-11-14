package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.gtnhplanner.machines.web.HandlerData;
import com.gtnhplanner.machines.web.MachineTable;
import com.gtnhplanner.machines.web.NodeMath;
import com.gtnhplanner.machines.web.RecipeRules;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.0's machines on cards, through the whole port (NodeMath): one hand-worked case per machine,
 * each from the controller's own lines (paths under common/machine of the TST source; the pack's config equals the
 * defaults). The game's arithmetic: the speed bonus multiplies the duration before the overclocks divide it, the whole
 * then truncated (OverclockCalculator.java:386-436); each parallel is billed at ceil(EU/t x EU modifier) and as many
 * run as the hatches' V x A pays for (ParallelHelper.java:399-401,479); an overclock is a whole power-of-four step of
 * V x A over the parallels' draw. Cards here have one hatch amp unless the case says otherwise.
 */
class TstMachinesTest {

    private static final String ICD = "tst.recipe.IntensifyChemicalDistorterRecipeMap.name",
        PHC = "tst.recipe.PreciseHighEnergyPhotonicQuantumMasterRecipeMap.name",
        MIRACLE_TOP = "tst.recipe.MiracleTopRecipeMap.name",
        CRYSTALLINE = "tst.recipe.CrystallineInfinitierRecipeMap.name",
        RUNE_ENGRAVER = "tst.recipe.RuneEngraverRecipeMap.name",
        HST = "tst.recipe.HyperSpacetimeTransformerRecipeMap.name", COKING = "tst.recipe.CokingFactoryRecipeMap.name",
        MEGA_AL = "tst.recipe.AssemblyLineWithoutResearchRecipeMap.name",
        NANO_CORE = "tst.recipe.DeployedNanoCoreRecipeMap.name",
        STAR_KERNEL = "tst.recipe.StarKernelGeneratorRecipeMap.name",
        GENESIS = "tst.recipe.MassFabricatorGenesisRecipeMap.name",
        MSTF = "tst.recipe.MicroSpaceTimeFabricatorioRecipeMap.name",
        BLOODY_HELL = "tst.recipe.BloodyHellRecipeMap.name",
        STONE_BREAKER = "tst.recipe.MegaStoneBreakerRecipeMap.name",
        SOLAR = "tst.recipe.MegaSolarPanelFactoryRecipeMap.name";

    // region Harness

    /** A recipe on a map as WebCards.recipe builds it from the NEI recipe (as the web tests' MapRecipes). */
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

    /** The handler id the pack's data gives a machine. */
    private static String id(final String machine) {
        return "tst-" + TstControls.key(machine);
    }

    /** One {@code machine} on hatches of {@code tier} with {@code amps} working amps, at these settings. */
    private static Web.Node node(final String machine, final String tier, final double amps,
        final Map<String, String> settings, @Nullable final String coil) {
        final Web.Node n = new Web.Node();
        n.overclockTier = tier;
        n.hatchVoltageTier = tier;
        n.hatchAmps = amps;
        n.powerInputMode = "amps";
        n.machineHandlerId = id(machine);
        n.machineConfigTiers = new LinkedHashMap<>(settings);
        n.coilTier = coil;
        n.parallel = 1.0;
        n.machineCount = 1.0;
        return n;
    }

    private static NodeMath.Result run(final String mapId, final double eut, final double ticks, final double special,
        final String machine, final String tier, final double amps, final Map<String, String> settings,
        @Nullable final String coil) {
        return NodeMath.compute(recipe(mapId, eut, ticks, special), node(machine, tier, amps, settings, coil));
    }

    private static NodeMath.Result run(final String mapId, final double eut, final double ticks, final String machine,
        final String tier, final double amps, final Map<String, String> settings) {
        return run(mapId, eut, ticks, 0, machine, tier, amps, settings, null);
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

    private static double outputMultiplier(final NodeMath.Result r, final Map<String, String> settings) {
        double m = 1;
        for (final RecipeRules.TierControl c : RecipeRules.configTierControls(r.effectiveRecipe(), settings))
            m *= c.current().outputMultiplier != null ? c.current().outputMultiplier : 1;
        return m;
    }

    // endregion

    // region The data

    @Test
    void everyMachineInTheDataHasAnEntryAndEveryEntryAMap() {
        final Map<String, MachineTable.Behaviour> entries = new LinkedHashMap<>();
        TstMachines.register(entries);
        final Set<String> listed = new HashSet<>();
        for (final HandlerData.MapEntry m : HandlerData.maps()
            .values()) {
            if (m.handlers == null) continue;
            for (final HandlerData.Template t : m.handlers) if (t.id.startsWith("tst-")) {
                listed.add(t.label);
                assertTrue(entries.containsKey(t.label), t.label);
                assertEquals(id(t.label), t.id);
                assertEquals("multiblock", t.kind);
                assertNotNull(t.items, t.label);
            }
        }
        assertEquals(entries.keySet(), listed);
        assertEquals(60, entries.size());
    }

    @Test
    void noWebsiteMachineIsShadowed() {
        final Set<String> website = new HashSet<>();
        for (final String name : MachineTable.names()) website.add(MachineTable.normalizeMachineName(name));
        final Map<String, MachineTable.Behaviour> entries = new LinkedHashMap<>();
        TstMachines.register(entries);
        for (final String name : entries.keySet()) {
            assertFalse(website.contains(MachineTable.normalizeMachineName(name)), name);
            assertNotNull(MachineTable.behaviour(name), name);
        }
    }

    /**
     * A map whose website recipes list no machine (the cyclotron) keeps the website's own machine first, so a card
     * without a pick, and the website's refresh, still run it; the pack's machines follow.
     */
    @Test
    void aMapWithoutListedMachinesKeepsItsOwnFirst() {
        final Web.Recipe r = recipe("gt.recipe.cyclotron", 7680, 1000, 0);
        final List<String> ids = new ArrayList<>();
        for (final Web.Handler h : RecipeRules.machineHandlers(r)) ids.add(h.id);
        assertEquals(3, ids.size(), ids.toString());
        assertFalse(
            ids.get(0)
                .startsWith("tst-"));
        assertEquals(List.of(id("Space Scaler"), id("PULSAR - Incompact Cyclotron")), ids.subList(1, 3));
        final Web.Node plain = node("Space Scaler", "UV", 1, Map.of(), null);
        plain.machineHandlerId = null;
        assertEquals(
            RecipeRules.machineHandlers(r)
                .get(0).machineType,
            NodeMath.compute(r, plain)
                .effectiveRecipe().machineType);
    }

    /** A map the website's data lacks is the pack's: its own machine, named in English. */
    @Test
    void theModsOwnMapsCarryTheirMachines() {
        final HandlerData.MapEntry m = HandlerData.map(HST);
        assertNotNull(m);
        assertEquals("Hyper Spacetime Transformer", m.name);
        assertTrue(m.handlersOnRecipes);
        assertEquals(id("HyperSpacetimeTransformer"), recipe(HST, 491520, 200, 0).machineHandlers.get(0).id);
    }

    // endregion

    // region One case per machine

    /**
     * GT_TileEntity_IntensifyChemicalDistorter (0.8.1): own mode 16 parallels, duration x 1 / (1 + whole 900 K steps of
     * coil heat over the recipe's) (:286-288, :337-344), perfect (:290-293). A 0 K recipe on Cupronickel (1801 K): 1801
     * / 900 = 2 steps, 400 / 3 = 133.3 ticks; 120 EU/t x 16 = 1920 of 8192 (IV, 1 A): one step, 133.3 / 4 = 33.3 -> 33
     * ticks at 480 EU/t. A 4500 K recipe on TPV (4501 K): no step over, 400 / 4 = 100 ticks.
     */
    @Test
    void intensifyChemicalDistorterOwnMode() {
        expect(
            run(ICD, 120, 400, "Intensify Chemical Distorter", "IV", 1, Map.of()),
            "Intensify Chemical Distorter",
            33,
            480,
            16);
        expect(
            run(ICD, 120, 400, 4500, "Intensify Chemical Distorter", "IV", 1, Map.of(), null),
            "Intensify Chemical Distorter",
            100,
            480,
            16);
    }

    /**
     * As a chemical reactor (0.8.1): 1024 parallels at 1F / max(1, coil tier + 1) (:281-288; 0.8.0's 1/10 is gone). 30
     * EU/t at EV 1 A: 2048 / 30 = 68 parallels, 2040 EU/t, no step. Cupronickel (tier 0): 1200 ticks; Infinity (tier
     * 11): 1200 x 1F/12 = 100.0000029 -> 100 ticks. A 4500 K recipe asks TPV coils (validateRecipe :333-334).
     */
    @Test
    void intensifyChemicalDistorterAsAChemicalReactor() {
        expect(
            run("gt.recipe.largechemicalreactor", 30, 1200, "Intensify Chemical Distorter", "EV", 1, Map.of()),
            "Intensify Chemical Distorter",
            1200,
            30,
            68);
        expect(
            run(
                "gt.recipe.largechemicalreactor",
                30,
                1200,
                0,
                "Intensify Chemical Distorter",
                "EV",
                1,
                Map.of(),
                "infinity"),
            "Intensify Chemical Distorter",
            100,
            30,
            68);
        final NodeMath.Result hot = run(ICD, 120, 400, 4500, "Intensify Chemical Distorter", "IV", 1, Map.of(), null);
        assertEquals(
            "tpv",
            RecipeRules.coilTierControl(hot.effectiveRecipe(), null)
                .current().key);
    }

    /**
     * GT_TileEntity_PreciseHighEnergyPhotonicQuantumMaster: no intensifiers, bonus 10000F / 10000F (:226), halved as a
     * laser engraver, 256 parallels (:280-289). 120 EU/t at IV: 68 parallels, no step; 100 x 0.5 = 50 ticks. UMV
     * intensifiers in all 25 slots: 10000F / (10000 + 25 x 19000) = 0.0206186 and perfect (:173-184); 1920 EU/t at LuV:
     * 16 parallels (30720 of 32768, no step), 2000 x 0.0206186 = 41.2 -> 41 ticks.
     */
    @Test
    void photonicQuantumMaster() {
        final String m = "Precise High-Energy Photonic Quantum Master";
        expect(run("gt.recipe.laserengraver", 120, 100, m, "IV", 1, Map.of()), m, 50, 120, 68);
        expect(run(PHC, 1920, 2000, m, "LuV", 1, Map.of(PHOTON, "umv")), m, 41, 1920, 16);
    }

    /**
     * GT_TileEntity_MiracleTop: two rings, 1.0F / 8 and 256 parallels (:328-329), normal below 8 rings (:327). 30 EU/t
     * x
     * 256 = 7680 of 131072 (ZPM): two steps; 800 / 4 x 0.125 = 25 ticks at 480 EU/t.
     */
    @Test
    void miracleTop() {
        expect(run(MIRACLE_TOP, 30, 800, "Miracle Top", "ZPM", 1, Map.of(RINGS, "2")), "Miracle Top", 25, 480, 256);
    }

    /**
     * GT_TileEntity_MagneticDrivePressureFormer (:270-291): extruder 1/8 with Cupronickel, 0.75 EU; 120 EU/t -> 90,
     * 8192 / 90 = 91 parallels, no step, 200 / 8 = 25 ticks. Bending with Kanthal: (1/16) / 2, perfect; 32 -> 24 EU/t,
     * 2048 / 24 = 85 parallels, 640 / 32 = 20 ticks.
     */
    @Test
    void magneticDrivePressureFormer() {
        final String m = "Magnetic Drive Pressure Former";
        expect(run("gt.recipe.extruder", 120, 200, m, "IV", 1, Map.of()), m, 25, 90, 91);
        expect(run("gt.recipe.metalbender", 32, 640, 0, m, "EV", 1, Map.of(), "kanthal"), m, 20, 24, 85);
    }

    /**
     * GT_TileEntity_PhysicalFormSwitcher: (float) 0.9F^total power tier (:172); EV 2 A is 4096 EU, IV (5): 0.5904899;
     * 4096 / 30 = 136 parallels, 100 x 0.59 = 59 ticks. HV glass refuses an EV recipe (:271-277).
     */
    @Test
    void physicalFormSwitcher() {
        final String m = "Physical Form Switcher";
        expect(run("gt.recipe.fluidsolidifier", 30, 100, m, "EV", 2, Map.of()), m, 59, 30, 136);
        assertEquals(TstMachines.GLASS_GATE, run("gt.recipe.fluidsolidifier", 2048, 100, m, "EV", 1, Map.of()).stall());
    }

    /** GT_TileEntity_MagneticMixer: 0.8F^6 at LuV = 0.262144 (:168); 32768 / 30 = 1092 parallels, 26 ticks. */
    @Test
    void magneticMixer() {
        final String m = "\"Mini\" Magnetic Mixer";
        expect(run("gt.recipe.multimixer", 30, 100, m, "LuV", 1, Map.of()), m, 26, 30, 1092);
    }

    /**
     * GT_TileEntity_MagneticDomainConstructor: 3 rings x 64 = 192 parallels (:319), 0.75^5 = 0.2373 at IV (:320); 192 x
     * 30 = 5760, no step; 200 x 0.2373 = 47 ticks.
     */
    @Test
    void magneticDomainConstructor() {
        final String m = "Magnetic Domain Constructor";
        expect(run("gt.recipe.polarizer", 30, 200, m, "IV", 1, Map.of(RINGS, "3")), m, 47, 30, 192);
    }

    /**
     * GT_TileEntity_Silksong: 2 sections x Kanthal (getCoilTier 2) x 32 = 128 parallels (:261); HV 4 A is 2048 EU, EV
     * (4): 0.85F^4 / 2 = 0.2610 (:264-265); 2048 / 120 = 17 parallels, 400 x 0.261 = 104 ticks.
     */
    @Test
    void silksong() {
        expect(
            run("gt.recipe.wiremill", 120, 400, 0, "Silksong", "HV", 4, Map.of(PIECES, "2"), "kanthal"),
            "Silksong",
            104,
            120,
            17);
    }

    /**
     * GT_TileEntity_HolySeparator: 16 sections, 128 parallels and perfect (:241, :305-307); 0.9F^5 at IV (:239). 16 x
     * 128 = 2048 of 8192: one step; 100 x 0.59049 / 4 = 14.76 -> 14 ticks at 64 EU/t.
     */
    @Test
    void holySeparator() {
        expect(
            run("gt.recipe.lathe", 16, 100, "Holy Separator", "IV", 1, Map.of(PIECES, "16")),
            "Holy Separator",
            14,
            64,
            128);
    }

    /**
     * GT_TileEntity_SpaceScaler: a Perfect Stabilisation generator is field tier 9: 10x, perfect (:299-302, :313-314),
     * every collider output x (1 + 4 x 6) = 25 (:214, :343-345); 524288 / 7680 = 68 parallels, 100 ticks. The implosion
     * compressor needs tier 11 (:337-338).
     */
    @Test
    void spaceScaler() {
        final Map<String, String> perfect = Map.of(FIELD, "perfect-stabilisation-field-generator");
        final NodeMath.Result r = run("gt.recipe.cyclotron", 7680, 1000, "Space Scaler", "UV", 1, perfect);
        expect(r, "Space Scaler", 100, 7680, 68);
        assertEquals(25, outputMultiplier(r, perfect));
        assertEquals(
            TstMachines.MODE_FIELD_GATE,
            run("gt.recipe.electricimplosioncompressor", 7680, 20, "Space Scaler", "UV", 1, perfect).stall());
    }

    /**
     * GT_TileEntity_MoleculeDeconstructor: one section, 24 parallels (:340-342); EV 4 A is IV, 0.9F^5 (:288). 24 x 30 =
     * 720 of 8192: one normal step; 1000 / 2 x 0.59049 = 295 ticks at 120 EU/t.
     */
    @Test
    void moleculeDeconstructor() {
        final String m = "Molecule Deconstructor";
        expect(run("gt.recipe.multicentrifuge", 30, 1000, m, "EV", 4, Map.of()), m, 295, 120, 24);
    }

    /**
     * GTCM_CrystallineInfinitier: autoclave 1/4 (:282-289), EU 1 / 3 with a Crude generator (:236), ZPM glass 7 x field
     * 3 x total power tier 6 (IV 4 A) = 126 parallels (:237-239), perfect (:240). 30 x 0.3333 x 126 = 1260 of 32768:
     * two
     * steps; 600 x 0.25 / 16 = 9 ticks at 160 EU/t. A recipe asking field 4 is refused (:318-323).
     */
    @Test
    void crystallineInfinitier() {
        final String m = "Crystalline Infinitier";
        final Map<String, String> s = Map.of(GLASS, "zpm", FIELD, "crude-stabilisation-field-generator");
        expect(run("gt.recipe.autoclave", 30, 600, m, "IV", 4, s), m, 9, 160.0000048, 126);
        assertEquals(TstMachines.FIELD_GATE, run(CRYSTALLINE, 30, 100, 4, m, "IV", 4, s, null).stall());
    }

    /** GTCM_ElvenWorkshop: one parallel, perfect (:132). 480 of 8192: two steps; 100 / 16 = 6 ticks at 7680 EU/t. */
    @Test
    void elvenWorkshop() {
        expect(run(RUNE_ENGRAVER, 480, 100, "ElvenWorkshop", "IV", 1, Map.of()), "ElvenWorkshop", 6, 7680, 1);
    }

    /**
     * TST_HyperSpacetimeTransformer: Advanced Compact Fusion Coils (tier 3) 1F / 3 (:227), Infinity Cooled perfect
     * (:228), 256 parallels (:311-313); 2097152 / 491520 = 4 parallels, no step, 200 / 3 = 66 ticks. The matrix
     * framework caps recipes at V[UHV] (:229, :298-305).
     */
    @Test
    void hyperSpacetimeTransformer() {
        final String m = "HyperSpacetimeTransformer";
        expect(
            run(
                HST,
                491520,
                200,
                m,
                "UHV",
                1,
                Map.of(FUSION_COIL, "advanced-compact-fusion-coil", COOLING, "infinity-cooled-casing")),
            m,
            66,
            491520,
            4);
        assertEquals(TstMachines.MATRIX_GATE, run(HST, 8388608, 200, m, "UEV", 1, Map.of()).stall());
    }

    /** TST_LargeIndustrialCokingFactory: Nichrome 1F / 3 (:153); 512 / 120 = 4 parallels, 600 / 3 = 200 ticks. */
    @Test
    void largeIndustrialCokingFactory() {
        final String m = "Large Industrial Coking Factory";
        expect(run(COKING, 120, 600, 0, m, "HV", 1, Map.of(), "nichrome"), m, 200, 120, 4);
    }

    /**
     * TST_Scavenger: (float) 0.8^5 at IV (:156), 0.6F EU (:178-180). Each parallel is billed ceil(30 x 0.6F) = 19 EU/t,
     * so 8192 / 19 = 431 parallels (not the 455 an unrounded 18 would allow); 100 x 0.32768 = 32 ticks.
     */
    @Test
    void scavenger() {
        expect(run("gt.recipe.sifter", 30, 100, "Scavenger", "IV", 1, Map.of()), "Scavenger", 32, 18.0000007, 431);
    }

    /** TST_BiosphereIII: brewing 0.25 (:276-282), UV glass 1 << 10 parallels (:267-273); 512 / 4 = 128, 32 ticks. */
    @Test
    void biosphere() {
        expect(
            run("gt.recipe.brewer", 4, 128, "Biosphere III", "HV", 1, Map.of(GLASS, "uv")),
            "Biosphere III",
            32,
            4,
            128);
    }

    /**
     * TST_AdvancedMegaOilCracker: 0.5 (:177-179), Hypogen EU 1F / 13 and perfect (:152-153), 256 parallels; 120 x
     * 0.0769 x 256 = 2363 of 32768: one step; 40 x 0.5 / 4 = 5 ticks at 36.9 EU/t.
     */
    @Test
    void advancedMegaOilCracker() {
        final String m = "Advanced Mega Oil Cracker";
        expect(run("gt.recipe.craker", 120, 40, 0, m, "LuV", 1, Map.of(), "hypogen"), m, 5, 36.9230783, 256);
    }

    /**
     * TST_IndistinctTentacle: assembler 1F / 4 (:334-341), 256 parallels (:329-331), perfect for a recipe no higher
     * than
     * the UV casing (:360-367); 7680 of 32768: one step; 400 x 0.25 / 4 = 25 ticks. A UEV recipe is past casing + 2
     * (:356-363).
     */
    @Test
    void indistinctTentacle() {
        final String m = "Indistinct Tentacle";
        final Map<String, String> uv = Map.of(COMPONENT_CASING, "uv");
        expect(run("gt.recipe.assembler", 30, 400, m, "LuV", 1, uv), m, 25, 120, 256);
        assertEquals(TstMachines.CASING_GATE, run("gt.recipe.assembler", 8388608, 400, m, "UEV", 1, uv).stall());
    }

    /**
     * TST_ThermalEnergyDevourer at IV 3 A (24576 EU): k = 1 + (int) ((log2 24576 - 3) / 2) = 6 (:181, :295-297). High
     * speed: 0.5F / 6, 1024 parallels (:230-246); 24576 / 120 = 204, 300 / 12 = 25 ticks. Devourer: EU 1F / 6 billed
     * ceil(20.0000006) = 21, 24576 / 21 = 1170 parallels at 300 ticks.
     */
    @Test
    void thermalEnergyDevourer() {
        final String m = "Thermal Energy Devourer";
        expect(run("gt.recipe.vacuumfreezer", 120, 300, m, "IV", 3, Map.of()), m, 25, 120, 204);
        expect(
            run("gt.recipe.vacuumfreezer", 120, 300, m, "IV", 3, Map.of(DEVOURER_MODE, "devourer-mode")),
            m,
            300,
            20.0000006,
            1170);
    }

    /**
     * TST_VacuumFilterExtractor: 1F / (1 + total power tier) (:168-169), 0.5 EU (:228-230), perfect as a distillery
     * (:233-236). HV: 1/4, 16 -> 8 EU/t, 512 / 8 = 64 parallels, 16 ticks. Tower at EV: 1/5, 60 EU/t, 34 parallels,
     * 240.
     */
    @Test
    void vacuumFilterExtractor() {
        final String m = "Vacuum Filter Extractor";
        expect(run("gt.recipe.distillery", 16, 64, m, "HV", 1, Map.of()), m, 16, 8, 64);
        expect(run("gt.recipe.distillationtower", 120, 1200, m, "EV", 1, Map.of()), m, 240, 60, 34);
    }

    /** TST_MegaMacerator: IV glass above an LV recipe, 0.125 (:268-289); Damascus 128 parallels; 2048 / 30 = 68. */
    @Test
    void megaMacerator() {
        final String m = "\"Mini\" Household Cell Fragmentizer";
        expect(run("gt.recipe.macerator", 30, 400, m, "EV", 1, Map.of(GLASS, "iv")), m, 50, 30, 68);
    }

    /** TST_HephaestusAtelier, alloy smelting on hatches: perfect (:284), unlimited; 128 / 16 = 8 parallels. */
    @Test
    void hephaestusAtelier() {
        final String m = "Hephaestus' Atelier";
        expect(run("gt.recipe.alloysmelter", 16, 100, m, "MV", 1, Map.of()), m, 100, 16, 8);
    }

    /** TST_DeployedNanoCore on hatches: perfect (:380-382), unlimited; 131072 / 30720 = 4 parallels, 200 ticks. */
    @Test
    void deployedNanoCore() {
        final String m = "Deployed Nano Core";
        expect(run(NANO_CORE, 30720, 200, m, "ZPM", 1, Map.of()), m, 200, 30720, 4);
    }

    /** TST_CoreDeviceOfHumanPowerGenerationFacility: Kanthal 1F / 2 (:190), perfect; 128 / 30 = 4, 100 ticks. */
    @Test
    void coreDevice() {
        final String m = "Core Device of Human Power Generation Facility";
        expect(run("gt.recipe.fluidheater", 30, 200, 0, m, "MV", 1, Map.of(), "kanthal"), m, 100, 30, 4);
    }

    /**
     * TST_BallLightning arc furnace at machine tier 3: 0.25^(3 - 0 - 1) (:536-537, :623-625), Ameliorated coils (1) x
     * Infinity (11): 2^11 = 2048 parallels (:446-450), perfect with a Crude generator (:453-459); 32768 / 30 = 1092,
     * 100 x
     * 0.0625 = 6 ticks. The star kernel needs tier 4 (:578-583).
     */
    @Test
    void ballLightning() {
        final String m = "BallLightning";
        final Map<String, String> s = Map
            .of(BALL_TIER, "tier-3", BALL_FIELD, "crude-stabilisation-field-generator", GLASS, "uv");
        expect(run("gt.recipe.arcfurnace", 30, 100, m, "LuV", 1, s), m, 6, 30, 1092);
        assertEquals(TstMachines.BALL_TIER_GATE, run(STAR_KERNEL, 30, 100, m, "LuV", 1, s).stall());
    }

    /** TST_LargeCanner: perfect (:164-166), unlimited; 32 / 4 = 8 parallels at 100 ticks. */
    @Test
    void largeCanner() {
        expect(run("gt.recipe.canner", 4, 100, "Large Canner", "LV", 1, Map.of()), "Large Canner", 100, 4, 8);
    }

    /**
     * TST_IndustrialMagnetarSeparator: 0.25, 0.8F EU, 4 x the summed hatch tier (IV 5) = 20 parallels (:148-160). 20 x
     * 24 = 480 of 8192: two normal steps; 200 x 0.25 / 4 = 12 ticks at 384 EU/t.
     */
    @Test
    void industrialMagnetarSeparator() {
        final String m = "Industrial Magnetar Separator";
        expect(run("gt.recipe.electromagneticseparator", 30, 200, m, "IV", 1, Map.of()), m, 12, 384.0000057, 20);
    }

    /**
     * MM_MassFabricatorGenesis: controllers' 128 + 1 parallels, perfect (:155, :207-209). 129 x 200000 of 536870912
     * (UXV): two steps; 1000 / 16 = 62 ticks at 3.2M EU/t.
     */
    @Test
    void massFabricatorGenesis() {
        final String m = "Mass Fabricator : Genesis";
        expect(run(GENESIS, 200000, 1000, m, "UXV", 1, Map.of(PARALLELS, "129")), m, 62, 3200000, 129);
    }

    /** TST_IncompactCyclotron: 0.5, 1.6F EU billed ceil(12288.0002) = 12289, 524288 / 12289 = 42 (:158-176). */
    @Test
    void incompactCyclotron() {
        final String m = "PULSAR - Incompact Cyclotron";
        expect(run("gt.recipe.cyclotron", 7680, 1000, m, "UV", 1, Map.of()), m, 500, 12288.000183, 42);
    }

    /**
     * TST_MicroSpaceTimeFabricatorio: a Stable generator is field tier 4, t = 2, 1F / 3 and perfect (:220-242), 16
     * parallels (:267-269); 4 parallels at UHV, 600 / 3 = 200 ticks; a SpaceTime Seed doubles the outputs (:285-287).
     */
    @Test
    void microSpaceTimeFabricatorio() {
        final String m = "Micro SpaceTime Fabricatorio";
        final Map<String, String> s = Map
            .of(MSTF_FIELD, "stable-stabilisation-field-generator", SEED, "one-seed-a-run");
        final NodeMath.Result r = run(MSTF, 491520, 600, m, "UHV", 1, s);
        expect(r, m, 200, 491520, 4);
        assertEquals(2, outputMultiplier(r, s));
    }

    /**
     * TST_BloodyHell: tier 3 is 1.0F / (1.5 x 1) and 27 parallels (:636-643); 1000 x 0.6667 = 666 ticks. 100 Speed
     * Runes
     * add (float) 35 (1 - e^-0.2) = 6.3444 (TaskerenAdvancedMathUtils.java:19-21): 1000 / (1.5 x 7.3444) = 90 ticks.
     */
    @Test
    void bloodyHell() {
        final String m = "Bloody Hell";
        expect(run(BLOODY_HELL, 0, 1000, m, "LV", 1, Map.of(ALTAR_TIER, "tier-3")), m, 666, 0, 27);
        expect(run(BLOODY_HELL, 0, 1000, m, "LV", 1, Map.of(ALTAR_TIER, "tier-3", SPEED_RUNES, "100")), m, 90, 0, 27);
    }

    /**
     * TST_MegaStoneBreaker at IV 3 A (24576 EU): (int) calculateVoltageTier = 5, 2^5 x 4 = 128 parallels (:188-191);
     * 3840 of 24576: one step, 10 ticks at 120 EU/t; fed water and lava, outputs x1024 (:214-234).
     */
    @Test
    void siliconRockSynthesizer() {
        final String m = "Silicon Rock Synthesizer";
        final Map<String, String> fed = Map.of(WATER_LAVA, "supplied");
        final NodeMath.Result r = run(STONE_BREAKER, 30, 20, m, "IV", 3, fed);
        expect(r, m, 10, 120, 128);
        assertEquals(1024, outputMultiplier(r, fed));
    }

    /**
     * TST_ManufacturingCenter, ZPM core: 1 / (1.2 + 0.5 x 2), EU 1 - 0.15 x 2 (:279-285), 2 x IV = 10 parallels
     * (:213-216); one 2 A hatch: 16 x 0.7 billed 12, 112 of 16384: three steps; 400 / 8 x 0.4545 = 22 ticks. A UHV
     * recipe is past the core (:246-248).
     */
    @Test
    void manufacturingCenter() {
        final String m = "Manufacturing Center";
        final Map<String, String> zpm = Map.of(CORE, "advanced-multiuse-core-zpm");
        expect(run("gt.recipe.lathe", 16, 400, m, "IV", 2, zpm), m, 22, 716.7999809, 10);
        assertEquals(TstMachines.CORE_GATE, run("gt.recipe.lathe", 2097152, 400, m, "UHV", 1, zpm).stall());
    }

    /**
     * TST_GiantVacuumDryingFurnace: one section x Kanthal (2) x 32 = 64 parallels (:367-368), (float) 0.8F^4 / (2 x
     * 0.5)
     * at EV (:371-372); 2048 / 480 = 4 parallels, 400 x 0.4096 = 163 ticks.
     */
    @Test
    void giantVacuumDryingFurnace() {
        final String m = "Giant Vacuum Drying Furnace";
        expect(run("gt.recipe.vacfurnace", 480, 400, 0, m, "EV", 1, Map.of(PIECES, "1"), "kanthal"), m, 163, 480, 4);
    }

    /** TST_AdvCircuitAssemblyLine: 16 parallels, perfect (:250-252, :295); 30720 of 131072: one step, 50 ticks. */
    @Test
    void advancedCircuitAssemblyLine() {
        final String m = "Advanced Circuit Assembly Line";
        expect(run("bw.recipe.cal", 1920, 200, m, "ZPM", 1, Map.of()), m, 50, 7680, 16);
    }

    /**
     * TST_IntegratedAssemblyMatrix: no nanites 1.5x, 128 silver nanites (float) (1 + 0.5 x 128 / 256) = 1.25
     * (:281-323);
     * 131072 / 30720 = 4 parallels: 1500 and 1250 ticks.
     */
    @Test
    void integratedAssemblyMatrix() {
        final String m = "Integrated Assembly Matrix";
        expect(run(MEGA_AL, 30720, 1000, m, "ZPM", 1, Map.of()), m, 1500, 30720, 4);
        expect(
            run(MEGA_AL, 30720, 1000, m, "ZPM", 1, Map.of(NANITES, "silver-nanites", NANITE_COUNT, "128")),
            m,
            1250,
            30720,
            4);
    }

    /** TST_MegaSolarPanelFactory: ZPM casing (meta 6) 1F / 7, perfect (:172, :196-198); 68 parallels, 100 ticks. */
    @Test
    void megaSolarPanelFactory() {
        final String m = "Mega Solar Panel Factory";
        expect(run(SOLAR, 7680, 700, m, "UV", 1, Map.of(COMPONENT_CASING, "zpm")), m, 100, 7680, 68);
    }

    // endregion
}
