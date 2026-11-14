package com.gtnhplanner.machines.eto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.machines.web.MachineTable;
import com.gtnhplanner.machines.web.Web;

/**
 * The cores and Thaumcraft machines' numbers, worked by hand from TwistSpaceTechnology 0.8.1 and GregTech 5.09.54
 * (docs/design/tst-machines.md, "Cores, arrays and Thaumcraft machines").
 */
class TstCoresTest {

    // region GT's ProcessingLogic (TstCoresGt)

    @Test
    void amperageOverclocksTakeWholeLog4Steps() {
        // 2048 EU/t over 30: log4(68.3) = 3.05, three steps of 2 / 4: 128 / 8 ticks at 30 x 64.
        final TstCoresGt.Run normal = TstCoresGt.process(
            new TstCoresGt.Setup().voltage(2048)
                .overclock(2, 4),
            30,
            128);
        assertEquals(new TstCoresGt.Run(1, 1920, 16), normal);
        // Perfect: the same three steps quarter the ticks.
        final TstCoresGt.Run perfect = TstCoresGt.process(
            new TstCoresGt.Setup().voltage(2048)
                .overclock(4, 4),
            30,
            128);
        assertEquals(new TstCoresGt.Run(1, 1920, 2), perfect);
    }

    @Test
    void parallelsFirstThenWhatPowerIsLeftOverclocks() {
        // 64 parallels of 30 EU/t take 1920 of 2048: no step left.
        final TstCoresGt.Run run = TstCoresGt.process(
            new TstCoresGt.Setup().voltage(2048)
                .maxParallel(64)
                .overclock(2, 4),
            30,
            128);
        assertEquals(new TstCoresGt.Run(64, 1920, 128), run);
    }

    @Test
    void powerShortOfOneParallelIsRefused() {
        assertNull(
            TstCoresGt.process(
                new TstCoresGt.Setup().voltage(29)
                    .overclock(2, 4),
                30,
                128));
        assertNotNull(
            TstCoresGt.process(
                new TstCoresGt.Setup().voltage(30)
                    .overclock(2, 4),
                30,
                128));
    }

    // endregion

    // region Execution cores (TstCoresExecution)

    @Test
    void theControllerAloneFinishesATickAfterItsDuration() {
        // Budget (long) (0.975 x 2048) = 1996. 32 EU/t: log4(1996 / 32) = 2 steps, 32 ticks at 512; the 1484 spare
        // buys no boost ((int) log4(1484 / 512 + 1) = (int) 0.79 = 0), and the controller ends a tick after progress
        // reaches 32: 33 ticks a recipe at 512 EU/t.
        final TstCoresExecution.Result r = TstCoresExecution
            .run(new TstCoresExecution.Setup(2048, 32, 128, 1, 1, 2, 4, 1, 0, 0));
        assertNotNull(r);
        assertEquals(1, r.parallels());
        assertEquals(33, r.duration(), 1e-9);
        assertEquals(512, r.hatchEuT(), 1e-9);
        assertEquals(0, r.networkEuT(), 1e-9);
    }

    @Test
    void aNormalBoostBuysOneTickForThreeTimesTheCoreEuT() {
        // 30 EU/t counts as 32 (getTiersAbove): log4(1996 / 32) = 2 steps, 32 ticks at 480; the 1516 spare gives
        // (int) log4(1516 / 480 + 1) = 1: one more tick for (4 - 1) x 480 = 1440 while 2 or more ticks are left
        // (log2(need) >= 1). Progress 1, 3, ... 31 over ticks 0-15, 32 at tick 16, the finish at tick 17: 17 ticks a
        // recipe, the hatches paying 17 x 480 + 16 x 1440 = 31200 over them.
        final TstCoresExecution.Result r = TstCoresExecution
            .run(new TstCoresExecution.Setup(2048, 30, 128, 1, 1, 2, 4, 1, 0, 0));
        assertNotNull(r);
        assertEquals(17, r.duration(), 1e-9);
        assertEquals(31200d / 17, r.hatchEuT(), 1e-9);
    }

    @Test
    void aPerfectBoostBuysATickPerCoreEuT() {
        // Budget 3993: perfect steps give 2 ticks at 1920; the 2073 spare buys one tick (1920) as it starts, so a
        // recipe each 2 ticks, the hatches paying 1920 x 2 + 1920 over them.
        final TstCoresExecution.Result r = TstCoresExecution
            .run(new TstCoresExecution.Setup(4096, 30, 128, 1, 1, 4, 4, 1, 0, 0));
        assertNotNull(r);
        assertEquals(2, r.duration(), 1e-9);
        assertEquals(2880, r.hatchEuT(), 1e-9);
    }

    @Test
    void noShareThatPaysOneParallelStartsNothing() {
        assertNull(TstCoresExecution.run(new TstCoresExecution.Setup(30, 30, 128, 1, 1, 2, 4, 1, 0, 0)));
    }

    @Test
    void eachCoreChecksWithItsShareOfTheParallels() {
        // getParallelOfEveryNormalExecutionCore: 1 + 64 / 4 with three cores; one each once cores + 1 >= parallels.
        assertEquals(64, TstCoresExecution.parallelPerCore(64, 0));
        assertEquals(17, TstCoresExecution.parallelPerCore(64, 3));
        assertEquals(1, TstCoresExecution.parallelPerCore(4, 3));
    }

    @Test
    void perfectExecutionCoresRunEverythingInTwentyTicksOnTheNetwork() {
        final TstCoresCards.Exact e = TstMachinesCores.executionRun(context(TstMachinesCores.EXEC_PERFECT, 1, 30, 400));
        assertNotNull(e);
        assertEquals(Integer.MAX_VALUE, e.parallels(), 0);
        assertEquals(20, e.ticks(), 0);
        assertEquals(true, e.network());
        // 30 EU/t x 400 ticks a parallel, over 20 ticks: 600 EU/t a parallel.
        assertEquals(600, e.euT() / e.parallels(), 1e-6);
    }

    // endregion

    // region DTPF Prototype MK-II fuel

    @Test
    void excitedFuelDiscountGrowsOverADay() {
        assertEquals(1.0, fuel(0, 1000), 0);
        // 12 h: 1 - 0.5 x 0.75.
        assertEquals(0.625, fuel(12, 1000), 0);
        // 24 h (1,728,000 ticks, not past the day): the 0.25 floor.
        assertEquals(0.25, fuel(24, 1000), 0);
        // round(3 x 0.25) = 1 of 3.
        assertEquals(1.0 / 3, fuel(24, 3), 1e-15);
        // 6 h: 144 x 0.8125 = 117.
        assertEquals(0.8125, fuel(6, 144), 0);
    }

    // endregion

    // region Thaumcraft speeds

    @Test
    void matrixSpeedFromSixPrimals() {
        // No nodes; five nodes: (6 - 5) x 1.75; a missing primal: 1 and 20 ticks each.
        assertEquals(1, TstMachinesCores.matrixSpeed(0, new int[6]), 0);
        assertEquals(1.75, TstMachinesCores.matrixSpeed(5, new int[6]), 0);
        assertEquals(1, TstMachinesCores.matrixSpeed(6, new int[] { 20, 20, 0, 20, 0, 20 }), 0);
        assertEquals(40, TstMachinesCores.matrixExtraTicks(6, new int[] { 20, 20, 0, 20, 0, 20 }));
        assertEquals(0, TstMachinesCores.matrixExtraTicks(5, new int[6]));
        // All 20: Var 0, Mean 20: 1 + (0.4 + 1 + 0.15) x 20 / 500 = 1.062.
        assertEquals(1 / 1.062, TstMachinesCores.matrixSpeed(6, new int[] { 20, 20, 20, 20, 20, 20 }), 1e-12);
        assertEquals(0.9257737316338055, TstMachinesCores.matrixSpeed(6, new int[] { 10, 20, 30, 40, 50, 60 }), 1e-12);
        // Mean 4000 passes 1 + 1.55 x Mean / 500 = 11.4514 (Mean 3371.4): the clamp.
        assertEquals(1 / 11.4514, TstMachinesCores.matrixSpeed(6, new int[] { 4000, 4000, 4000, 4000, 4000, 4000 }), 0);
    }

    @Test
    void towerSpeedFromFourPrimals() {
        assertEquals(1, TstMachinesCores.towerSpeed(0, 0, 0, 0, 0), 0);
        assertEquals(0.7, TstMachinesCores.towerSpeed(3, 0, 0, 0, 0), 1e-15);
        // All ten: min = max = 10, the clamp (perfect overclocks).
        assertEquals(1 / 11.4514, TstMachinesCores.towerSpeed(4, 10, 10, 10, 10), 0);
        assertEquals(0.39366278358977935, TstMachinesCores.towerSpeed(4, 20, 20, 20, 20), 1e-12);
        assertEquals(0.49714870483058704, TstMachinesCores.towerSpeed(4, 9, 9, 9, 9), 1e-12);
        // A primal at 0: base all but 0.7, the punishment the rest.
        assertEquals(0.7, TstMachinesCores.towerSpeed(4, 0, 10, 10, 10), 1e-12);
    }

    // endregion

    // region GregTech's NEI cutting page

    @Test
    void theFakeCuttingMapIsTheCuttingMap() {
        // FakeCuttingRecipes: what NEI shows for every cutter; the machines run gt.recipe.cuttingsaw.
        assertNotNull(com.gtnhplanner.machines.web.HandlerData.map("gt.recipe.cuttingsaw"));
        assertEquals(
            com.gtnhplanner.machines.web.HandlerData.map("gt.recipe.cuttingsaw"),
            com.gtnhplanner.machines.web.HandlerData.map("gt.recipe.fakecuttingsaw"));
    }

    // endregion

    // region Ball Lightning fusion

    @Test
    void fusionRecipeTierFromStartupAndVoltage() {
        // GT fusion recipes carry no special value in NEI: tier 1, never gated.
        assertEquals(1, TstMachinesCores.fusionRecipeTier(fusionContext(0, 30)));
        assertEquals(2, TstMachinesCores.fusionRecipeTier(fusionContext(160_000_000, 30)));
        assertEquals(3, TstMachinesCores.fusionRecipeTier(fusionContext(320_000_000, 30)));
    }

    // endregion

    private static MachineTable.Context context(final String id, final double value, final double eut,
        final double ticks) {
        final Web.Recipe recipe = new Web.Recipe();
        recipe.eut = eut;
        recipe.durationTicks = ticks;
        recipe.inputs = List.of();
        recipe.outputs = List.of();
        return new MachineTable.Context(
            c -> 0,
            c -> c.equals(id) ? value : 0,
            0,
            null,
            null,
            "DTPF",
            recipe,
            null,
            2048);
    }

    private static MachineTable.Context fusionContext(final int special, final double eut) {
        final Web.Recipe recipe = new Web.Recipe();
        recipe.eut = eut;
        recipe.durationTicks = 20;
        recipe.inputs = List.of();
        recipe.outputs = List.of();
        return new MachineTable.Context(c -> 0, c -> 0, 0, null, special, "Fusion Reactor", recipe, null, 2048);
    }

    private static double fuel(final double hours, final int amount) {
        final Web.Resource fuel = new Web.Resource();
        fuel.kind = "fluid";
        fuel.id = "exciteddtec";
        fuel.amount = amount;
        final Web.Recipe recipe = new Web.Recipe();
        recipe.eut = 30;
        recipe.durationTicks = 100;
        recipe.inputs = List.of(fuel);
        recipe.outputs = List.of();
        final MachineTable.Context c = new MachineTable.Context(
            id -> 0,
            id -> id.equals(TstMachinesCores.DTPF_HOURS) ? hours : 0,
            0,
            null,
            null,
            "DTPF",
            recipe,
            null,
            2048);
        return TstMachinesCores.plasmaForgeFuel(c, fuel);
    }
}
