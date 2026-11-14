package com.gtnhplanner.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.sources.NrEssentiaSmeltery;

/**
 * GoodGenerator's Large Essentia Smeltery (GT 5.09.54.265 MTELargeEssentiaSmeltery), worked by hand: (blocks long - 1)
 * x 2^cell parallels, a run of ceil(essentia x 32 x (1 - Perditio x 0.005)) ticks from 480 EU/t, perfectly
 * overclocked as GregTech's OverclockCalculator does it.
 */
class NrEssentiaSmelteryTest {

    private static final String WHEAT = "nr:item:minecraft:wheat", WATER = "nr:fluid:water";

    /** Wheat smelts to a Messis and a Herba; water's block to four Aqua. */
    private static final NrEssentiaSmeltery.Data DATA = new NrEssentiaSmeltery.Data() {

        @Override
        @Nullable
        public Map<String, Integer> aspects(final String input) {
            final Map<String, Integer> out = new LinkedHashMap<>();
            switch (input) {
                case WHEAT -> {
                    out.put("Messis", 1);
                    out.put("Herba", 1);
                }
                case WATER -> out.put("Aqua", 4);
                default -> {
                    return null;
                }
            }
            return out;
        }

        @Override
        public List<Option> cells() {
            return List.of(
                new Option("0", "Novice"),
                new Option("1", "Adept"),
                new Option("2", "Master"),
                new Option("3", "Grandmaster"));
        }
    };

    private static PowerModel compute(final Map<String, String> settings) {
        return NrEssentiaSmeltery.source(DATA)
            .compute(settings);
    }

    private static double flow(final List<Flow> flows, final String name) {
        for (final Flow flow : flows) if (flow.name()
            .equals(name)) return flow.perSecond();
        return 0;
    }

    // ---- the game's maths

    @Test
    void parallelsAreTheSlicesTimesTheCells() {
        assertEquals(4, NrEssentiaSmeltery.parallel(5, 0));
        assertEquals(10, NrEssentiaSmeltery.parallel(6, 1));
        assertEquals(64, NrEssentiaSmeltery.parallel(9, 3));
    }

    @Test
    void perditioCutsTheTimeByUpToHalf() {
        assertEquals(320, NrEssentiaSmeltery.baseTicks(10, 0));
        assertEquals(160, NrEssentiaSmeltery.baseTicks(10, 100));
        // 3 x 32 x 0.75 = 72; 1 x 32 x 0.995 = 31.84, up to 32.
        assertEquals(72, NrEssentiaSmeltery.baseTicks(3, 50));
        assertEquals(32, NrEssentiaSmeltery.baseTicks(1, 1));
    }

    @Test
    void overclocksPerfectlyByHatchPower() {
        // 480 EU/t exactly: no overclock.
        assertEquals(new NrEssentiaSmeltery.Overclocked(0, 480, 320), NrEssentiaSmeltery.overclock(480, 320));
        // A 2 A HV hatch (1,024): 1,024 / 480 is 2.1, under four times.
        assertEquals(new NrEssentiaSmeltery.Overclocked(0, 480, 320), NrEssentiaSmeltery.overclock(1_024, 320));
        // A 2 A IV hatch (16,384): power 34 times, two tiers; voltage tier 6 against 3.
        assertEquals(new NrEssentiaSmeltery.Overclocked(2, 7_680, 20), NrEssentiaSmeltery.overclock(16_384, 320));
        // A 2 A UV hatch: 1,048,576 / 480 = 2,184, five tiers; voltage tier 9 against 3; 320 / 1,024 is under a tick.
        assertEquals(new NrEssentiaSmeltery.Overclocked(5, 491_520, 1), NrEssentiaSmeltery.overclock(1_048_576, 320));
        // Under 480 EU/t the game's -1 tiers is no overclock.
        assertEquals(new NrEssentiaSmeltery.Overclocked(0, 480, 320), NrEssentiaSmeltery.overclock(64, 320));
    }

    @Test
    void nodePowerIsTheHatchTierSquaredTimes115() {
        assertEquals(18, NrEssentiaSmeltery.nodePowerPerRun(4));
        assertEquals(28, NrEssentiaSmeltery.nodePowerPerRun(5));
        assertEquals(73, NrEssentiaSmeltery.nodePowerPerRun(8));
    }

    // ---- holding what it smelts

    @Test
    void takesItemsAndFluidsInAndGivesNothingOut() {
        assertTrue(NrEssentiaSmeltery.canTake("item:minecraft:wheat:0", false));
        assertTrue(NrEssentiaSmeltery.canTake("fluid:water", false));
        assertFalse(NrEssentiaSmeltery.canTake("item:minecraft:wheat:0", true));
        assertFalse(NrEssentiaSmeltery.canTake("power:eu", false));
        // A Node needs the default machine profile, which the game registers and a test must (as CustomRateTest does).
        com.gtnhplanner.harness.GtnhFlowLoader.ensureDefaultMachineProfile();
        final Node node = new Node(UUID.randomUUID(), 0, 0);
        node.powerSource = NrEssentiaSmeltery.ID;
        assertTrue(Holders.is(node));
        assertFalse(Holders.holding(node));
        assertTrue(NrEssentiaSmeltery.hold(node, "fluid:water", false));
        assertEquals("fluid:water", NrEssentiaSmeltery.input(node));
        assertTrue(Holders.holding(node));
        assertFalse(Holders.canTake(node, "fluid:water", true));
        NrEssentiaSmeltery.release(node);
        assertNull(NrEssentiaSmeltery.input(node));
    }

    @Test
    void resourceKeysAreFlowNames() {
        assertEquals(WHEAT, NrEssentiaSmeltery.flow("item:minecraft:wheat:0"));
        assertEquals(
            "nr:item:gregtech:gt.metaitem.01@2019",
            NrEssentiaSmeltery.flow("item:gregtech:gt.metaitem.01:2019"));
        assertEquals(WATER, NrEssentiaSmeltery.flow("fluid:water"));
        assertNull(NrEssentiaSmeltery.flow("power:eu"));
    }

    @Test
    void essentiaIsReadingsNotPorts() {
        // Each aspect is a flow named for it, which no resource stands for: the card lists it with its readings.
        for (final String aspect : List.of(
            "Aer",
            "Terra",
            "Ignis",
            "Aqua",
            "Ordo",
            "Perditio",
            "Herba",
            "Messis",
            "Lucrum",
            "Metallum",
            "Potentia",
            "Machina",
            "Lux")) assertNull(PowerResources.resolve(aspect), aspect);
    }

    // ---- a card

    @Test
    void anEmptySmelteryMakesNothing() {
        final PowerModel model = compute(Map.of());
        assertEquals(List.of(), model.inputs());
        assertEquals(List.of(), model.outputs());
        assertEquals(0, model.euPerTick(), 0);
    }

    @Test
    void smeltsWheatOnAnIvHatch() {
        final PowerModel model = compute(Map.of(NrEssentiaSmeltery.INPUT, "item:minecraft:wheat:0", "tier", "IV"));
        // 4 wheat a run, 8 essentia: 256 ticks, two overclocks, 16 ticks at 7,680 EU/t: 1.25 runs a second.
        assertEquals(-7_680, model.euPerTick(), 0);
        assertEquals(5, flow(model.inputs(), WHEAT), 1e-9);
        assertEquals(5, flow(model.outputs(), "Messis"), 1e-9);
        assertEquals(5, flow(model.outputs(), "Herba"), 1e-9);
        // 28 node power a run.
        assertEquals("35 cv/s", model.stat("Node vis"));
        assertEquals("2,200/s", model.stat("Pollution"));
        assertEquals("1/s", model.stat("Flux gas"));
    }

    @Test
    void smeltsAFluidByTheBucket() {
        final PowerModel model = compute(Map.of(NrEssentiaSmeltery.INPUT, "fluid:water", "tier", "IV"));
        // 4 buckets a run, 16 Aqua: 512 ticks, 32 after two overclocks: 0.625 runs a second.
        assertEquals(2_500, flow(model.inputs(), WATER), 1e-9);
        assertEquals(10, flow(model.outputs(), "Aqua"), 1e-9);
    }

    @Test
    void perditioAndPurificationChangeTimeAndPollution() {
        final PowerModel model = compute(
            Map.of(
                NrEssentiaSmeltery.INPUT,
                "item:minecraft:wheat:0",
                "tier",
                "IV",
                "perditio",
                "125",
                "purification",
                "75"));
        // Perditio is counted to 100: 128 ticks, 8 after two overclocks.
        assertEquals(10, flow(model.outputs(), "Messis"), 1e-9);
        assertEquals("500 cv/s", model.stat("Perditio"));
        assertEquals("550/s", model.stat("Pollution"));
        assertEquals("0.25/s", model.stat("Flux gas"));
    }

    @Test
    void aLongerSmelteryWithBetterCellsRunsMoreAtOnce() {
        final PowerModel model = compute(
            Map.of(NrEssentiaSmeltery.INPUT, "item:minecraft:wheat:0", "tier", "IV", "length", "9", "cell", "3"));
        // 64 wheat a run, 128 essentia: 4,096 ticks, 256 after two overclocks: 5 wheat a second.
        assertEquals("64", model.stat("Parallel"));
        assertEquals(5, flow(model.inputs(), WHEAT), 1e-9);
    }
}
