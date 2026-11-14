package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstVisualHarness.fluid;
import static com.gtnhplanner.machines.eto.TstVisualHarness.inputPerRun;
import static com.gtnhplanner.machines.eto.TstVisualHarness.item;
import static com.gtnhplanner.machines.eto.TstVisualHarness.outputPerRun;
import static com.gtnhplanner.machines.eto.TstVisualHarness.recipe;
import static com.gtnhplanner.machines.eto.TstVisualHarness.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.machines.web.NodeMath;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's machines with their own recipe pages (TstMachinesVisual), on cards made from those
 * pages: every number worked by hand from the controllers' source with the pack's config.
 */
class TstVisualMachinesTest {

    private static final double EPS = 1e-6;

    private static void assertRun(final NodeMath.Result r, final double ticks, final double parallels,
        final double runEut) {
        assertFalse(r.stalled(), r.stall());
        assertEquals(
            ticks,
            r.overclock()
                .durationTicks(),
            EPS,
            "ticks");
        assertEquals(parallels, r.machineParallels(), EPS, "parallels");
        assertEquals(runEut, r.euT(), Math.max(EPS, Math.abs(runEut) * 1e-12), "run EU/t");
    }

    private static void assertGate(final NodeMath.Result r, final String gate) {
        assertNotNull(r.power());
        assertEquals(
            gate,
            r.power()
                .recipeGateReason());
        assertTrue(r.stalled());
    }

    // region Nether Interface

    private static Web.Recipe nether() {
        return recipe(
            "tst.recipe.NetherInterfaceVisualRecipeMap.name",
            30720,
            1200,
            List.of(fluid("ic2distilledwater", 16000)),
            List.of(
                item("etfuturum:ancient_debris", 1, 0.01),
                item("gregtech:gt.metaitem.03@1", 48, 0.49),
                item("gregtech:gt.metaitem.03@2", 64, 0.30),
                item("gregtech:gt.metaitem.03@3", 64, 0.10),
                item("ThaumicTinkerer:kamiResource@6", 8, 0.10),
                fluid("poornetherwaste", 16000),
                fluid("molten.hellishmetal", 288)));
    }

    @Test
    void netherInterfaceRunsSixtyFourParallelsAtMostOnTwoAmpsAboveIvUpkeep() {
        // ZPM 2 A: 262144 / 7680 = 34, less 2 = 32 parallels at 7680 x 34 EU/t
        final NodeMath.Result zpm = run(nether(), "tst-nether-interface", "ZPM", 2, Map.of());
        assertRun(zpm, 1200, 32, 261_120);
        assertEquals(16000.0 * 32, inputPerRun(zpm, 0), EPS);
        // Three weighted picks a parallel: 1% of 1 debris x 3 x 32
        assertEquals(1 * 0.01 * 3 * 32, outputPerRun(zpm, 0), EPS);
        assertEquals(48 * 0.49 * 3 * 32, outputPerRun(zpm, 1), EPS);
        assertEquals(16000.0 * 32, outputPerRun(zpm, 5), EPS);
        // Hellish Metal on 30% of runs, 288 L a parallel
        assertEquals(288 * 0.3 * 32, outputPerRun(zpm, 6), EPS);
        // LuV 1 A: 32768 / 7680 = 4, less 2 = 2
        assertRun(run(nether(), "tst-nether-interface", "LuV", 1, Map.of()), 1200, 2, 7680 * 4);
        // UV 1 A: 524288 / 7680 - 2 = 66, capped at 64, EU/t still 7680 x (2 + 64)
        assertRun(run(nether(), "tst-nether-interface", "UV", 1, Map.of()), 1200, 64, 7680 * 66);
        // IV 2 A: 16384 < 3 x 7680
        assertGate(run(nether(), "tst-nether-interface", "IV", 2, Map.of()), TstMachinesVisual.NETHER_POWER_GATE);
    }

    // endregion

    // region Super Water Purifier

    private static Web.Recipe water(final double uuMatter) {
        final List<Web.Resource> out = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) out.add(fluid("grade" + i, 1000));
        return recipe(
            "tst.recipe.SuperWaterPurifierVisualRecipeMap.name",
            125_829_120,
            1200,
            List.of(fluid(uuMatter < 1000 ? "concentrateduumatter" : "uumatter", uuMatter)),
            out);
    }

    @Test
    void superWaterPurifierMakesOneParallelPerUmvAmp() {
        // UMV 2 A: 2 parallels at 2 x RECIPE_UMV; 3 picks of 1000 L, 12% each grade, 4% baryonic matter
        final NodeMath.Result umv = run(water(1000), "tst-super-water-purifier", "UMV", 2, Map.of());
        assertRun(umv, 1200, 2, 2 * 125_829_120.0);
        assertEquals(2000, inputPerRun(umv, 0), EPS);
        assertEquals(1000 * 3 * 0.12 * 2, outputPerRun(umv, 0), EPS);
        assertEquals(1000 * 3 * 0.04 * 2, outputPerRun(umv, 8), EPS);
        // Concentrated UU-Matter: 1 L a parallel, six picks
        final NodeMath.Result concentrated = run(water(1), "tst-super-water-purifier", "UMV", 2, Map.of());
        assertEquals(2, inputPerRun(concentrated, 0), EPS);
        assertEquals(1000 * 6 * 0.12 * 2, outputPerRun(concentrated, 3), EPS);
        // UMV 4 A = 134217728 x 4 / 125829120 = 4.27: 4 parallels; UIV 1 A (33554432) is under one
        assertRun(run(water(1000), "tst-super-water-purifier", "UMV", 4, Map.of()), 1200, 4, 4 * 125_829_120.0);
        assertGate(
            run(water(1000), "tst-super-water-purifier", "UIV", 1, Map.of()),
            TstMachinesVisual.WATER_POWER_GATE);
    }

    // endregion

    // region General Ore Processing Factory TST

    private static Web.Recipe ore() {
        return recipe(
            "tst.recipe.OreProcessingVisualRecipeMap.name",
            30,
            128,
            List.of(item("gregtech:gt.blockores@32", 1), fluid("lubricant", 1)),
            List.of(item("gregtech:gt.metaitem.01@2032", 2), item("gregtech:gt.metaitem.01@2035", 1, 0.5)));
    }

    @Test
    void oreProcessingTakesWhatItsPowerPaysThirtyEuEachFor() {
        // One IV hatch: 8192 x 15/16 = 7680 = 256 ores, 30 EU/t each; 1600 L of Lubricant a run
        final NodeMath.Result iv = run(ore(), "tst-general-ore-processing-factory-tst", "IV", 1, Map.of());
        assertRun(iv, 128, 256, 7680);
        assertEquals(256, inputPerRun(iv, 0), EPS);
        assertEquals(1600, inputPerRun(iv, 1), EPS);
        assertEquals(2 * 256, outputPerRun(iv, 0), EPS);
        // A 2 A IV hatch is still one IV hatch: 256
        assertRun(run(ore(), "tst-general-ore-processing-factory-tst", "IV", 2, Map.of()), 128, 256, 7680);
        // Several hatches, 16384 x 31/32 = 15872: 529 ores
        final NodeMath.Result several = run(
            ore(),
            "tst-general-ore-processing-factory-tst",
            "IV",
            2,
            Map.of(TstMachinesVisual.ORE_POWER, "several-or-exotic-hatches"));
        assertRun(several, 128, 529, 529 * 30);
        assertEquals(1600, inputPerRun(several, 1), EPS);
        // LV: 32 x 15/16 = 30, one ore; ULV: 7, none
        assertRun(run(ore(), "tst-general-ore-processing-factory-tst", "LV", 1, Map.of()), 128, 1, 30);
        assertGate(
            run(ore(), "tst-general-ore-processing-factory-tst", "ULV", 1, Map.of()),
            TstMachinesVisual.ORE_POWER_GATE);
        // Wireless: every ore waiting (64 by default), 3840 EU an ore over the 128 ticks
        final NodeMath.Result wireless = run(
            ore(),
            "tst-general-ore-processing-factory-tst",
            "LV",
            1,
            Map.of(TstMachinesVisual.ORE_POWER, "wireless"));
        assertRun(wireless, 128, 64, 64 * 30);
        assertEquals(1600, inputPerRun(wireless, 1), EPS);
    }

    // endregion

    // region Extreme Crafting Center

    @Test
    void extremeCraftingCenterRunsMagnifiedPatternsEveryTwentyTicks() {
        final Web.Recipe craft = recipe(
            "tst.recipe.VisualExtremeCraftRecipeMap.name",
            0,
            20,
            List.of(item("avaritia:Resource@1", 8)),
            List.of(item("avaritia:Infinity_Pickaxe", 1)));
        assertRun(run(craft, "tst-extreme-crafting-center", "LV", 1, Map.of()), 20, 1, 0);
        final NodeMath.Result magnified = run(
            craft,
            "tst-extreme-crafting-center",
            "LV",
            1,
            Map.of(TstMachinesVisual.XC_MAGNIFICATION, "4", TstMachinesVisual.XC_CRAFTS, "16"));
        assertRun(magnified, 20, 64, 0);
        assertEquals(8 * 64, inputPerRun(magnified, 0), EPS);
        // Integer.MAX_VALUE crafts at most
        assertRun(
            run(
                craft,
                "tst-extreme-crafting-center",
                "LV",
                1,
                Map.of(TstMachinesVisual.XC_MAGNIFICATION, "2147483647", TstMachinesVisual.XC_CRAFTS, "2")),
            20,
            Integer.MAX_VALUE,
            0);
    }

    // endregion

    // region Skypiercer Tower

    private static Web.Recipe crystal() {
        // Lux: Lucrum's synthesis, 2 s (40 ticks) at 1920 EU/t
        return recipe(
            "tst.recipe.SkypiercerTowerRecipeMap.name",
            1920,
            40,
            List.of(item("thaumcraft:crystalessence@1", 1), item("thaumcraft:crystalessence@2", 1)),
            List.of(item("thaumcraft:crystalessence@3", 1)));
    }

    @Test
    void skypiercerEssentiaModeRunsSixteenParallelsARingAtPerfectOverclocks() {
        // IV 2 A = 16384: one overclock (tiers above 1920: log4(8) = 1; voltage tiers 6 - 4 = 2), 40 / 4 = 10 ticks,
        // 1920 x 4 EU/t for all 64 parallels
        final NodeMath.Result r = run(
            crystal(),
            "tst-skypiercer-tower",
            "IV",
            2,
            Map.of(TstMachinesVisual.SKY_MODE, "essentia-mode", TstMachinesVisual.SKY_RINGS, "4"));
        assertRun(r, 10, 64, 7680);
        assertEquals(64, inputPerRun(r, 0), EPS);
        assertEquals(64, outputPerRun(r, 0), EPS);
        // EV 1 A = 2048: no overclock; no rings is one parallel
        final Map<String, String> essentia = Map.of(TstMachinesVisual.SKY_MODE, "essentia-mode");
        assertRun(run(crystal(), "tst-skypiercer-tower", "EV", 1, essentia), 40, 1, 1920);
        // UV 2 A = 1048576: log4(546) = 4 overclocks (voltage allows 5), 40 / 256 floored at 1 tick, 1920 x 256 EU/t
        assertRun(run(crystal(), "tst-skypiercer-tower", "UV", 2, essentia), 1, 1, 491_520);
        // The card's default, Crystal Essence Mode, halves instead: 40 / 16 = 2.5, 2 ticks
        assertRun(run(crystal(), "tst-skypiercer-tower", "UV", 2, Map.of()), 2, 1, 491_520);
        // HV 2 A = 1024 < 1920
        assertGate(run(crystal(), "tst-skypiercer-tower", "HV", 2, Map.of()), TstMachinesVisual.SKY_POWER_GATE);
    }

    @Test
    void skypiercerCrystalModeDividesTimeByOnePointTwoARing() {
        // 4 rings: 24 parallels, (int) (40 / 1.2^4) = 19 ticks, one overclock on IV 2 A: 9 ticks at 7680 EU/t
        final NodeMath.Result r = run(
            crystal(),
            "tst-skypiercer-tower",
            "IV",
            2,
            Map.of(TstMachinesVisual.SKY_MODE, "crystal-essence-mode", TstMachinesVisual.SKY_RINGS, "4"));
        assertRun(r, 9, 24, 7680);
        assertEquals(24, outputPerRun(r, 0), EPS);
        // UV 2 A: 19 / 16 floored at 1
        assertRun(
            run(
                crystal(),
                "tst-skypiercer-tower",
                "UV",
                2,
                Map.of(TstMachinesVisual.SKY_MODE, "crystal-essence-mode", TstMachinesVisual.SKY_RINGS, "4")),
            1,
            24,
            491_520);
    }

    @Test
    void skypiercerCrystalModeStopsAtTheSmallerStack() {
        // 4 rings would run 24; stacks of 16 crystals run 16, the time unchanged
        final NodeMath.Result r = run(
            crystal(),
            "tst-skypiercer-tower",
            "IV",
            2,
            Map.of(TstMachinesVisual.SKY_RINGS, "4", TstMachinesVisual.SKY_STACK, "16"));
        assertRun(r, 9, 16, 7680);
        // Essentia Mode has no stacks: 64
        assertRun(
            run(
                crystal(),
                "tst-skypiercer-tower",
                "IV",
                2,
                Map.of(
                    TstMachinesVisual.SKY_MODE,
                    "essentia-mode",
                    TstMachinesVisual.SKY_RINGS,
                    "4",
                    TstMachinesVisual.SKY_STACK,
                    "16")),
            10,
            64,
            7680);
    }

    @Test
    void skypiercerPassiveModeMakesSixARingOfOneTree() {
        // 13 rings, a 10 s tree: ceil(200 / 1.2^13) = 19 ticks, 78 parallels of output from one of each input
        final NodeMath.Result r = run(
            crystal(),
            "tst-skypiercer-tower",
            "EV",
            1,
            Map.of(
                TstMachinesVisual.SKY_MODE,
                "passive-mode",
                TstMachinesVisual.SKY_RINGS,
                "13",
                TstMachinesVisual.SKY_TREE,
                "10"));
        assertRun(r, 19, 78, 1920);
        assertEquals(1, inputPerRun(r, 0), EPS);
        assertEquals(78, outputPerRun(r, 0), EPS);
        // The tree defaults to the aspect's own seconds: 40 ticks, no rings
        assertRun(
            run(crystal(), "tst-skypiercer-tower", "EV", 1, Map.of(TstMachinesVisual.SKY_MODE, "passive-mode")),
            40,
            1,
            1920);
    }

    // endregion

    // region Strange Matter Aggregator

    private static Web.Recipe aggregation(final int maintenance) {
        return recipe(
            "tst.recipe.StrangeMatterAggregatorRecipeMap.name",
            2_013_265_912,
            2400,
            List.of(
                item("antimatter", 256),
                item("constrainer", 1),
                item("tesseract", 1),
                item("frame", 1),
                fluid("plasma.hydrogen", 256_000),
                fluid("molten.spacetime", maintenance)),
            List.of(item("antimatterrod", 1), item("strangerod", 1), fluid("molten.infinity", 6912)));
    }

    @Test
    void strangeMatterAggregatorAtItsSmallestStructure() {
        // Everything T1 x 1, one constrainer, the streak at T1's 24 with Core Elements: constrainer factor 256,
        // parallel 256 / 25 = 10.24; 256 rods (all strange), 2621 antimatter, 2621440 L plasma, 1 tesseract, 256
        // frames,
        // 576 L maintenance; byproduct 144 x 24 x 2; 2400 ticks; EU/t (long) (1F x 515396073472L) in float
        final NodeMath.Result r = run(aggregation(576), "tst-strange-matter-aggregator", "MAX", 256, Map.of());
        assertRun(r, 2400, 1, 515_396_075_520.0);
        assertEquals(2621, inputPerRun(r, 0), EPS);
        assertEquals(1, inputPerRun(r, 1), EPS);
        assertEquals(1, inputPerRun(r, 2), EPS);
        assertEquals(256, inputPerRun(r, 3), EPS);
        assertEquals(2_621_440, inputPerRun(r, 4), EPS);
        assertEquals(576, inputPerRun(r, 5), EPS);
        assertEquals(0, outputPerRun(r, 0), EPS);
        assertEquals(256, outputPerRun(r, 1), EPS);
        assertEquals(6912, outputPerRun(r, 2), EPS);
        // MAX 128 A = 274877905920 < RECIPE_MAX x 256
        assertGate(
            run(aggregation(576), "tst-strange-matter-aggregator", "MAX", 128, Map.of()),
            TstMachinesVisual.SMA_POWER_GATE);
    }

    @Test
    void strangeMatterAggregatorAtAMixedStructureOnTierThreeFluid() {
        // Oscillator T3 x 4, constraintor T2 x 2, merger T3 x 3, 10 constrainers, streak 384, no Core Element, a x16
        // speed controller and the 50% power controller, maintenance 16 L (T3): constrainer factor 7077888,
        // parallel 70778880 / 385; input factor 1 + 255 / 2
        final Map<String, String> settings = Map.ofEntries(
            Map.entry(TstMachinesVisual.SMA_OSCILLATOR, "t3"),
            Map.entry(TstMachinesVisual.SMA_OSCILLATORS, "4"),
            Map.entry(TstMachinesVisual.SMA_CONSTRAINTOR, "t2"),
            Map.entry(TstMachinesVisual.SMA_CONSTRAINTORS, "2"),
            Map.entry(TstMachinesVisual.SMA_MERGER, "t3"),
            Map.entry(TstMachinesVisual.SMA_MERGERS, "3"),
            Map.entry(TstMachinesVisual.SMA_CONSTRAINERS, "10"),
            Map.entry(TstMachinesVisual.SMA_STREAK, "384"),
            Map.entry(TstMachinesVisual.SMA_CORE, "not-supplied"),
            Map.entry(TstMachinesVisual.MODULE_SPEED, "16"),
            Map.entry(TstMachinesVisual.MODULE_POWER, "static-power-consumption-controller-module-t7"));
        final NodeMath.Result r = run(aggregation(16), "tst-strange-matter-aggregator", "MAX", 1024, settings);
        // 2 x 200 ticks / 16; (long) (0.5F x (long) (3 x 3^0.7 x 515396073472))
        assertRun(r, 25, 1, 1_668_081_385_472.0);
        assertEquals(23_623_600, inputPerRun(r, 0), EPS);
        assertEquals(10, inputPerRun(r, 1), EPS);
        assertEquals(11, inputPerRun(r, 2), EPS);
        assertEquals(70_071_091, inputPerRun(r, 3), EPS);
        assertEquals(23_623_600, inputPerRun(r, 4), EPS);
        // (int) (16 x 12^0.8 / 2)
        assertEquals(58, inputPerRun(r, 5), EPS);
        assertEquals(70_778_880, outputPerRun(r, 0), EPS);
        assertEquals(0, outputPerRun(r, 1), EPS);
        // 3 mergers x 144 x 384
        assertEquals(165_888, outputPerRun(r, 2), EPS);
    }

    @Test
    void strangeMatterAggregatorStreakIsCappedByTheMergerTier() {
        // Merger T2 caps the streak at 144: parallel 256 x 2 / 145; 512 rods; byproduct 144 x 144 x 2
        final NodeMath.Result r = run(
            aggregation(576),
            "tst-strange-matter-aggregator",
            "MAX",
            1024,
            Map.of(TstMachinesVisual.SMA_MERGER, "t2"));
        assertEquals(512, outputPerRun(r, 1), EPS);
        assertEquals(144 * 144 * 2, outputPerRun(r, 2), EPS);
        // No streak: no byproduct, the parallel all 512 rods
        final NodeMath.Result fresh = run(
            aggregation(576),
            "tst-strange-matter-aggregator",
            "MAX",
            1024,
            Map.of(TstMachinesVisual.SMA_MERGER, "t2", TstMachinesVisual.SMA_STREAK, "0"));
        assertEquals(512, outputPerRun(fresh, 1), EPS);
        assertEquals(0, outputPerRun(fresh, 2), EPS);
        // (long) (512 x 256) antimatter
        assertEquals(131_072, inputPerRun(fresh, 0), EPS);
    }

    @Test
    void strangeMatterAggregatorWirelessHandsTheNetworkItsRunsEu() {
        // Wireless: (long) (1F x 515396073472 x 2400) = 1236950581248000 EU added a run, over 2400 ticks; no hatch gate
        final NodeMath.Result r = run(
            aggregation(576),
            "tst-strange-matter-aggregator",
            "LV",
            1,
            Map.of(TstMachinesVisual.SMA_POWER_INPUT, "wireless"));
        assertRun(r, 2400, 1, -1_236_950_581_248_000.0 / 2400);
        assertEquals(256, outputPerRun(r, 1), EPS);
    }

    // endregion

    // region Dyson Sphere Module Launch Site

    private static Web.Recipe sail() {
        return recipe(
            "tst.recipe.DSP_LauncherRecipeMap.name",
            1_966_080,
            2400,
            List.of(item("solarsail", 1)),
            List.of());
    }

    private static Web.Recipe node() {
        return recipe(
            "tst.recipe.DSP_LauncherRecipeMap.name",
            125_829_120,
            18000,
            List.of(item("smalllaunchvehicle", 1)),
            List.of(item("emptysmalllaunchvehicle", 1, 0.99)));
    }

    @Test
    void dspLauncherDividesTimeByTheMotorOrTheOverload() {
        final String id = "tst-dyson-sphere-module-launch-site";
        assertRun(run(sail(), id, "UHV", 1, Map.of()), 2400, 1, 1_966_080);
        assertRun(run(sail(), id, "UHV", 1, Map.of(TstMachinesVisual.DSP_MOTOR, "t5")), 480, 1, 1_966_080);
        // 900 s of overload with a T5 motor: (1 + 900^(1/1005)) x 30 x 5 = 301.02
        final Map<String, String> overload = Map
            .of(TstMachinesVisual.DSP_MOTOR, "t5", TstMachinesVisual.DSP_OVERLOAD, "900");
        assertRun(run(sail(), id, "UHV", 1, overload), 7, 1, 1_966_080);
        assertRun(run(node(), id, "UMV", 1, overload), 59, 1, 125_829_120);
        // 1 s left: x30 whatever the motor
        assertRun(run(node(), id, "UMV", 1, Map.of(TstMachinesVisual.DSP_OVERLOAD, "1")), 600, 1, 125_829_120);
        // 3600 s with a T3 motor
        assertRun(
            run(
                node(),
                id,
                "UMV",
                1,
                Map.of(TstMachinesVisual.DSP_MOTOR, "t3", TstMachinesVisual.DSP_OVERLOAD, "3600")),
            98,
            1,
            125_829_120);
        // Wireless pays the page's EU over the shortened run: 1966080 x 2400 / 480
        assertRun(
            run(
                sail(),
                id,
                "LV",
                1,
                Map.of(TstMachinesVisual.DSP_MOTOR, "t5", TstMachinesVisual.DSP_POWER, "wireless")),
            480,
            1,
            1_966_080.0 * 5);
        // Wired below the page's EU/t
        assertGate(run(sail(), id, "UV", 2, Map.of()), TstMachinesVisual.DSP_POWER_GATE);
    }

    // endregion

    // region Bottled Chaos

    private static Web.Recipe route(final double ticks) {
        return recipe(
            "tst.bottledchaos.recipe.name",
            30720,
            ticks,
            List.of(item("gregtech:gt.metaitem.01@11305", 1)),
            List.of(item("gregtech:gt.metaitem.01@2305", 4)));
    }

    @Test
    void bottledChaosBatchesTwentySecondsOfItsFastestRoute() {
        // UHV 2 A = 4194304, normal overclock: fastest speed 2^3 x (4194304 / 1966080)^(1/2) = 11.6847; budget
        // 400 x 11.6847 = 4673.9 ticks = 29 batches of 159; 4611 ticks of work at 11.6847: 395 ticks, the whole pool
        final NodeMath.Result normal = run(route(159), "tst-bottled-chaos", "UHV", 2, Map.of());
        assertRun(normal, 395, 29, 4_194_304);
        assertEquals(29, inputPerRun(normal, 0), EPS);
        assertEquals(4 * 29, outputPerRun(normal, 0), EPS);
        // Perfect overclock: speed 4^3 x 4194304 / 1966080 = 136.53; 343 batches; 54537 ticks of work in 400
        assertRun(
            run(
                route(159),
                "tst-bottled-chaos",
                "UHV",
                2,
                Map.of(TstMachinesVisual.CHAOS_OVERCLOCK, "perfect-overclock-controller-module")),
            400,
            343,
            4_194_304);
        // Singularity perfect (8 x time for 4 x EU): speed 8^3 x (4194304 / 1966080)^1.5 = 1595.36; 4013 batches
        assertRun(
            run(
                route(159),
                "tst-bottled-chaos",
                "UHV",
                2,
                Map.of(TstMachinesVisual.CHAOS_OVERCLOCK, "singularity-perfect-overclock-controller-module")),
            400,
            4013,
            4_194_304);
        // A route longer than the budget runs once. LuV 1 A = 32768: fastest speed (32768 / 30720)^(1/2) = 1.0328,
        // budget 413 ticks < 2000; 2000 / 1.0328 = 1937 ticks on the whole 32768 EU/t
        assertRun(run(route(2000), "tst-bottled-chaos", "LuV", 1, Map.of()), 1937, 1, 32768);
        // A x2 speed controller and the T7 (50%) power controller: base 15360 EU/t; 83 batches in 400 ticks
        assertRun(
            run(
                route(159),
                "tst-bottled-chaos",
                "UHV",
                2,
                Map.of(
                    TstMachinesVisual.MODULE_SPEED,
                    "2",
                    TstMachinesVisual.MODULE_POWER,
                    "static-power-consumption-controller-module-t7")),
            400,
            83,
            4_194_304);
    }

    @Test
    void bottledChaosRefusesRoutesAboveItsCoreOrPower() {
        // 30720 EU/t over IV 2 A (16384)
        assertGate(run(route(159), "tst-bottled-chaos", "IV", 2, Map.of()), TstMachinesVisual.CHAOS_POWER_GATE);
        // A route above V[UHV] on the UHV core
        final Web.Recipe uev = recipe(
            "tst.bottledchaos.recipe.name",
            2_500_000,
            200,
            List.of(item("gregtech:gt.metaitem.01@11305", 1)),
            List.of(item("gregtech:gt.metaitem.01@2305", 1)));
        assertGate(run(uev, "tst-bottled-chaos", "UEV", 4, Map.of()), TstMachinesVisual.CHAOS_CORE_GATE);
        assertFalse(run(uev, "tst-bottled-chaos", "UEV", 4, Map.of(TstMachinesVisual.CHAOS_CORE, "uev")).stalled());
    }

    @Test
    void bottledChaosGroupRunsOnItsShareOfThePool() {
        // One of two equal groups on UHV 2 A: 2097152 EU/t, speed 2^2 x (2097152 / 491520)^(1/2) = 8.26; 20 batches,
        // 3180 ticks of work in 385 ticks
        assertRun(
            run(
                route(159),
                "tst-bottled-chaos",
                "UHV",
                2,
                Map.of(TstMachinesVisual.CHAOS_SHARE, "1", TstMachinesVisual.CHAOS_TOTAL_SHARE, "2")),
            385,
            20,
            2_097_152);
    }

    @Test
    void bottledChaosWirelessBillsItsBatchesOnceInFiveSeconds() {
        // 10 batches of 159 t at 30720 EU/t: work 0.0227 ticks at V[MAX], speed 1: 48844800 EU over 100 ticks
        final NodeMath.Result small = run(
            route(159),
            "tst-bottled-chaos",
            "LV",
            1,
            Map.of(TstMachinesVisual.CHAOS_POWER, "wireless", TstMachinesVisual.CHAOS_BATCHES, "10"));
        assertRun(small, 100, 10, 488_448);
        // 10,000,000 batches: work 22745.13, speed 227.45; normal overclock bills speed^(ln 2 / ln 2) more
        assertRun(
            run(
                route(159),
                "tst-bottled-chaos",
                "LV",
                1,
                Map.of(TstMachinesVisual.CHAOS_POWER, "wireless", TstMachinesVisual.CHAOS_BATCHES, "10000000")),
            100,
            10_000_000,
            11_109_814_494_512_284.0 / 100);
        // Perfect: no extra
        assertRun(
            run(
                route(159),
                "tst-bottled-chaos",
                "LV",
                1,
                Map.of(
                    TstMachinesVisual.CHAOS_POWER,
                    "wireless",
                    TstMachinesVisual.CHAOS_BATCHES,
                    "10000000",
                    TstMachinesVisual.CHAOS_OVERCLOCK,
                    "perfect-overclock-controller-module")),
            100,
            10_000_000,
            488_448_000_000.0);
    }

    @Test
    void bottledChaosWorkbenchPagesAreOneTickCrafts() {
        final Web.Recipe credential = recipe(
            "tst.bottledchaos.recipe.credential.name",
            0,
            1,
            List.of(item("credential", 1), item("gregtech:gt.metaitem.01@32705", 4)),
            List.of(item("credential", 1)));
        assertRun(run(credential, "tst-bottled-chaos", "LV", 1, Map.of()), 1, 1, 0);
        assertEquals(4, inputPerRun(run(credential, "tst-bottled-chaos", "LV", 1, Map.of()), 1), EPS);
    }

    // endregion
}
