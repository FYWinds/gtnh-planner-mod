package com.gtnhplanner.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.sources.TstMegaGenerators;

/**
 * TwistSpaceTechnology 0.8.1's Tower of Abstraction (GT_TileEntity_MegaEggGenerator) and Mega Naquadah Reactor
 * (TST_MegaNqReactor), with the pack's TwistSpaceTechnology.cfg and GoodGenerator.cfg. Every number is worked by hand
 * from their checkProcessing and the helpers it calls.
 */
class TstMegaGeneratorsTest {

    private static final String TOWER = TstMegaGenerators.TOWER_OF_ABSTRACTION;
    private static final String MNR = TstMegaGenerators.MEGA_NAQUADAH_REACTOR;

    private static PowerSource source(final String id) {
        final PowerSource source = PowerRegistry.get(id);
        if (source == null) fail("No power source " + id);
        return source;
    }

    private static PowerModel compute(final String id, final Map<String, String> settings) {
        return source(id).compute(settings);
    }

    private static double flow(final List<Flow> flows, final String name) {
        for (final Flow flow : flows) if (flow.name()
            .equals(name)) return flow.perSecond();
        return Double.NaN;
    }

    private static List<String> optionKeys(final String id, final String settingId) {
        final PowerSetting.Select select = assertInstanceOf(PowerSetting.Select.class, source(id).setting(settingId));
        final List<String> keys = new ArrayList<>();
        for (final PowerSetting.Option option : select.options()) keys.add(option.key());
        return keys;
    }

    private static Map<String, String> tower(final int pieces, final int infinity, final int dragon,
        final int creeper) {
        return Map.of(
            "pieces",
            String.valueOf(pieces),
            "infinityEggs",
            String.valueOf(infinity),
            "dragonEggs",
            String.valueOf(dragon),
            "creeperEggs",
            String.valueOf(creeper));
    }

    // ---- Tower of Abstraction: getOutputEUt x getMaxEfficiency / 10,000

    @Test
    void oneDragonEggPieceRunsAtItsEggsSum() {
        // 16 x 2,048 = 32,768 EU/t at 10,000 (log2 1 = 0, no infinity egg, no empty slot).
        final PowerModel model = compute(TOWER, Map.of());
        assertEquals(32_768, model.euPerTick(), 0);
        assertEquals(List.of(), model.inputs());
        assertEquals(List.of(), model.outputs());
        assertEquals("32,768 EU/t", model.stat("Base output"));
        assertEquals("0", model.stat("Empty slots"));
        assertEquals("100%", model.stat("Max efficiency"));
        // 20 + 16 / 2 = 28 a run: ceil(10,000 / 28) = 358 one-second runs to reach it.
        assertEquals("358 s", model.stat("Warm-up"));
        assertEquals("One hatch, no laser", model.stat("Dynamo"));
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void sixteenPiecesPassTheEggsSumAndAllowALaser() {
        // 16 x 16,384 + 240 x 2,048 = 753,664; 10,000 + 200 x 4 + 100 x 16 = 12,400;
        // 753,664 x 12,400 / 10,000 = 934,543.36 -> 934,543 (long division).
        final PowerModel model = compute(TOWER, tower(16, 16, 240, 0));
        assertEquals(934_543, model.euPerTick(), 0);
        assertEquals("124%", model.stat("Max efficiency"));
        // 20 + 16 x 100 + 50: ceil(12,400 / 1,670) = 8.
        assertEquals("8 s", model.stat("Warm-up"));
        assertEquals("One hatch, laser allowed", model.stat("Dynamo"));
        // 15 pieces: 706,560 x 12,100 / 10,000 = 854,937.6 -> 854,937, and checkLaser still refuses a laser.
        final PowerModel fifteen = compute(TOWER, tower(15, 15, 225, 0));
        assertEquals(854_937, fifteen.euPerTick(), 0);
        assertEquals("121%", fifteen.stat("Max efficiency"));
        assertEquals("One hatch, no laser", fifteen.stat("Dynamo"));
    }

    @Test
    void anEmptySlotCostsFivePercent() {
        // 15 x 2,048 = 30,720 at 9,500: 29,184.
        final PowerModel model = compute(TOWER, tower(1, 0, 15, 0));
        assertEquals(29_184, model.euPerTick(), 0);
        assertEquals("1", model.stat("Empty slots"));
        assertEquals("95%", model.stat("Max efficiency"));
        // 3 dragon eggs, 13 empty: 6,144 x 3,500 / 10,000 = 2,150.4 -> 2,150.
        assertEquals(2_150, compute(TOWER, tower(1, 0, 3, 0)).euPerTick(), 0);
        // Two empty pieces: 10,000 + 200 - 32 x 500 is below zero, held at 0.
        final PowerModel empty = compute(TOWER, tower(2, 0, 0, 0));
        assertEquals(0, empty.euPerTick(), 0);
        assertEquals("0%", empty.stat("Max efficiency"));
    }

    @Test
    void creeperEggsAloneWarmUpAtTheBaseRate() {
        // 16 x 512 = 8,192 EU/t; 20 a run, 10,000 / 20 = 500 s.
        final PowerModel model = compute(TOWER, tower(1, 0, 0, 16));
        assertEquals(8_192, model.euPerTick(), 0);
        assertEquals("500 s", model.stat("Warm-up"));
    }

    @Test
    void piecesBuffEachWholePowerOfTwo() {
        assertEquals(10_000, TstMegaGenerators.maxEfficiency(1, 0, 0));
        assertEquals(10_200, TstMegaGenerators.maxEfficiency(2, 0, 0));
        assertEquals(10_400, TstMegaGenerators.maxEfficiency(7, 0, 0));
        assertEquals(10_600, TstMegaGenerators.maxEfficiency(8, 0, 0));
        assertEquals(10_600, TstMegaGenerators.maxEfficiency(15, 0, 0));
        assertEquals(10_800, TstMegaGenerators.maxEfficiency(16, 0, 0));
        assertEquals(11_200, TstMegaGenerators.maxEfficiency(127, 0, 0));
        // Infinity eggs +100, empty slots -500 each.
        assertEquals(10_100, TstMegaGenerators.maxEfficiency(1, 1, 0));
        assertEquals(9_700, TstMegaGenerators.maxEfficiency(3, 0, 1));
    }

    @Test
    void dragonEggsSpeedTheWarmUpUpToFiftyPairs() {
        assertEquals(20, TstMegaGenerators.efficiencyIncrement(0, 0));
        assertEquals(20, TstMegaGenerators.efficiencyIncrement(1, 0));
        assertEquals(69, TstMegaGenerators.efficiencyIncrement(99, 0));
        assertEquals(70, TstMegaGenerators.efficiencyIncrement(100, 0));
        assertEquals(70, TstMegaGenerators.efficiencyIncrement(101, 0));
        assertEquals(120, TstMegaGenerators.efficiencyIncrement(0, 1));
        // 8 pieces, 99 dragon and 29 creeper eggs: 217,600 x 10,600 / 10,000 = 230,656; ceil(10,600 / 69) = 154.
        final PowerModel model = compute(TOWER, tower(8, 0, 99, 29));
        assertEquals(230_656, model.euPerTick(), 0);
        assertEquals("154 s", model.stat("Warm-up"));
    }

    @Test
    void theTallestTower() {
        // 127 pieces, 127 infinity and 1,905 dragon eggs: 5,982,208 x 23,900 / 10,000 = 14,297,477.12.
        final PowerModel model = compute(TOWER, tower(127, 127, 1905, 0));
        assertEquals(14_297_477, model.euPerTick(), 0);
        assertEquals("239%", model.stat("Max efficiency"));
        final PowerSetting.Number pieces = assertInstanceOf(PowerSetting.Number.class, source(TOWER).setting("pieces"));
        assertEquals(1, pieces.min(), 0);
        assertEquals(127, pieces.max(), 0);
    }

    @Test
    void doesNotFormWithMoreInfinityEggsThanPiecesOrEggsThanSlots() {
        final PowerModel infinity = compute(TOWER, tower(1, 2, 14, 0));
        assertEquals(0, infinity.euPerTick(), 0);
        assertEquals(List.of("More infinity eggs than pieces: the tower does not form."), infinity.warnings());
        final PowerModel crowded = compute(TOWER, tower(1, 0, 17, 0));
        assertEquals(0, crowded.euPerTick(), 0);
        assertEquals(List.of("More eggs than egg slots (16 a piece): the tower does not form."), crowded.warnings());
    }

    // ---- Mega Naquadah Reactor: basic x coolant% x coefficient x parallels x runtime bonus

    @Test
    void runsAThousandMkIParallelsAtTheFullBonusByDefault() {
        // 975,000 x 1 x 1 x 1,000 x 2.25.
        final PowerModel model = compute(MNR, Map.of());
        assertEquals(2_193_750_000L, model.euPerTick(), 0);
        // 1,000 L of fuel and 2,400 x 1,000 x 3 L of liquid air every 60 ticks.
        assertEquals(1_000 / 3.0, flow(model.inputs(), "Naq Fuel Mk-I"), 1e-9);
        assertEquals(2_400_000, flow(model.inputs(), "Liquid Air"), 1e-6);
        assertEquals(
            2,
            model.inputs()
                .size());
        assertEquals(
            List.of(new Flow("Naquadah Based Liquid Fuel MkI (Depleted)", 1_000 * 20.0 / 60, PowerModel.Unit.L)),
            model.outputs());
        assertEquals("1,000 L every 60 ticks", model.stat("Burn"));
        assertEquals("100%", model.stat("Efficiency"));
        assertEquals("2.25x", model.stat("Runtime bonus"));
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void coolantAndExcitedFluidMultiplyAndAreDrawnForEveryStartedSecond() {
        // Mk-II, Cryotheum (275%), Molten Naquadah (x4): 2,300,000 x 2.75 x 4 x 1,000 x 2.25.
        final PowerModel model = compute(
            MNR,
            Map.of("fuel", "Naq Fuel Mk-II", "coolant", "Cryotheum", "booster", "Molten Naquadah"));
        assertEquals(56_925_000_000L, model.euPerTick(), 0);
        // 70 ticks start 4 seconds: per run 4 x 1,000 L fuel, 2,400 x 1,000 x 4 air, 1,000 x 1,000 x 4 cryotheum,
        // 20 x 1,000 x 4 naquadah.
        assertEquals(4_000 * 20.0 / 70, flow(model.inputs(), "Naq Fuel Mk-II"), 1e-9);
        assertEquals(9_600_000 * 20.0 / 70, flow(model.inputs(), "Liquid Air"), 1e-6);
        assertEquals(4_000_000 * 20.0 / 70, flow(model.inputs(), "Cryotheum"), 1e-6);
        assertEquals(80_000 * 20.0 / 70, flow(model.inputs(), "Molten Naquadah"), 1e-9);
        assertEquals(4_000 * 20.0 / 70, flow(model.outputs(), "Naquadah Based Liquid Fuel MkII (Depleted)"), 1e-9);
        assertEquals("4,000 L every 70 ticks", model.stat("Burn"));
        assertEquals("275%", model.stat("Efficiency"));
    }

    @Test
    void plutoniumPaysEightSecondsForItsSevenAndAHalf() {
        // One parallel, Molten Caesium (x2, 180 L), no running time: 32,400 x 2 = 64,800 EU/t.
        final PowerModel model = compute(
            MNR,
            Map.of(
                "fuel",
                "Plutonium Fuel (Excited)",
                "booster",
                "Molten Caesium",
                "parallels",
                "1",
                "runningHours",
                "0"));
        assertEquals(64_800, model.euPerTick(), 0);
        // 150 ticks: 2 L of fuel, 2,400 x 8 L of air, 180 x 8 L of caesium a run.
        assertEquals(2 * 20.0 / 150, flow(model.inputs(), "Plutonium Fuel (Excited)"), 1e-12);
        assertEquals(2_560, flow(model.inputs(), "Liquid Air"), 1e-9);
        assertEquals(192, flow(model.inputs(), "Molten Caesium"), 1e-9);
        assertEquals("1x", model.stat("Runtime bonus"));
        // Thorium's 500 ticks are 25 whole seconds: air is 2,400 L a parallel a second exactly.
        final PowerModel thorium = compute(MNR, Map.of("fuel", "Thorium Fuel (Excited)"));
        assertEquals(2_400_000, flow(thorium.inputs(), "Liquid Air"), 1e-6);
        assertEquals(40, flow(thorium.inputs(), "Thorium Fuel (Excited)"), 1e-12);
    }

    @Test
    void theStrongestMix() {
        // Mk-VI, Tachyon Rich Temporal Fluid (500%), Spatially Enlarged Fluid (x64), 1,000 parallels, 2.25.
        final PowerModel model = compute(
            MNR,
            Map.of(
                "fuel",
                "Naq Fuel Mk-VI",
                "coolant",
                "Tachyon Rich Temporal Fluid",
                "booster",
                "Spatially Enlarged Fluid"));
        assertEquals(1_496_012_544_000_000L, model.euPerTick(), 0);
        // 240 ticks are 12 seconds: 20 L a parallel a second of each.
        assertEquals(20_000, flow(model.inputs(), "Tachyon Rich Temporal Fluid"), 1e-9);
        assertEquals(20_000, flow(model.inputs(), "Spatially Enlarged Fluid"), 1e-9);
        assertEquals(64_000 * 20.0 / 240, flow(model.inputs(), "Naq Fuel Mk-VI"), 1e-9);
    }

    @Test
    void ic2CoolantAddsFivePercent() {
        // Thorium, one parallel, cold: 2,200 x (105 / 100d) = 2,310 EU/t; 1,000 L x 25 s every 500 ticks.
        final PowerModel model = compute(
            MNR,
            Map.of("fuel", "Thorium Fuel (Excited)", "coolant", "Coolant", "parallels", "1", "runningHours", "0"));
        assertEquals(2_310, model.euPerTick(), 0);
        assertEquals(1_000, flow(model.inputs(), "Coolant"), 1e-9);
        assertEquals("105%", model.stat("Efficiency"));
    }

    @Test
    void theRuntimeBonusTopsOutAtTwelveHours() {
        assertEquals(1.0, TstMegaGenerators.runtimeBonus(0), 0);
        assertEquals(1.5625, TstMegaGenerators.runtimeBonus(6L * 72_000), 0);
        assertEquals(2.25, TstMegaGenerators.runtimeBonus(12L * 72_000), 0);
        assertEquals(2.25, TstMegaGenerators.runtimeBonus(24L * 72_000), 0);
        assertEquals(12_960_000, uranium("0"), 0);
        assertEquals(20_250_000, uranium("6"), 0);
        assertEquals(29_160_000, uranium("12"), 0);
        assertEquals(29_160_000, uranium("24"), 0);
    }

    /** Uranium Fuel (Excited), no coolant or booster, 1,000 parallels, after this many hours of running. */
    private static double uranium(final String hours) {
        return compute(MNR, Map.of("fuel", "Uranium Fuel (Excited)", "runningHours", hours)).euPerTick();
    }

    @Test
    void currentEUtTruncatesTheGamesDoubles() {
        // Half an hour: (1 + 1/48)² is inexact, and 12,960 x 1,000 x 2,401/2,304 = 13,505,625 exactly comes out of
        // the game's doubles as 13,505,624.99... -> 13,505,624.
        assertEquals(13_505_624, TstMegaGenerators.reactorEuPerTick(12_960, 100, 1, 1_000, 36_000));
        final PowerModel model = compute(MNR, Map.of("fuel", "Uranium Fuel (Excited)", "runningHours", "0.5"));
        assertEquals(13_505_624, model.euPerTick(), 0);
        assertEquals("1.04x", model.stat("Runtime bonus"));
    }

    @Test
    void parallelsStopAtTheConfiguredThousand() {
        final PowerModel model = compute(MNR, Map.of("parallels", "5000"));
        assertEquals(2_193_750_000L, model.euPerTick(), 0);
        final PowerModel seven = compute(MNR, Map.of("parallels", "7"));
        assertEquals(15_356_250, seven.euPerTick(), 0);
        assertEquals(2_400 * 7, flow(seven.inputs(), "Liquid Air"), 1e-9);
    }

    @Test
    void offersTheLargeNaquadahReactorsFluids() {
        assertEquals(
            List.of(
                "Thorium Fuel (Excited)",
                "Uranium Fuel (Excited)",
                "Plutonium Fuel (Excited)",
                "Naq Fuel Mk-I",
                "Naq Fuel Mk-II",
                "Naq Fuel Mk-III",
                "Naq Fuel Mk-IV",
                "Naq Fuel Mk-V",
                "Naq Fuel Mk-VI"),
            optionKeys(MNR, "fuel"));
        assertEquals(
            List.of("None", "Coolant", "Super Coolant", "Cryotheum", "Tachyon Rich Temporal Fluid"),
            optionKeys(MNR, "coolant"));
        assertEquals(
            List.of(
                "None",
                "Molten Caesium",
                "Molten Uranium-235",
                "Molten Naquadah",
                "Molten Atomic Separation Catalyst",
                "Spatially Enlarged Fluid"),
            optionKeys(MNR, "booster"));
        // Every flow is a fluid the resource map knows, so the card gets ports.
        final PowerModel model = compute(
            MNR,
            Map.of("fuel", "Naq Fuel Mk-V", "coolant", "Super Coolant", "booster", "Molten Uranium-235"));
        for (final Flow flow : model.inputs()) assertNotNull(PowerResources.resolve(flow.name()), flow.name());
        for (final Flow flow : model.outputs()) assertNotNull(PowerResources.resolve(flow.name()), flow.name());
    }
}
