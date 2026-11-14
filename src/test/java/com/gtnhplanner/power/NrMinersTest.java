package com.gtnhplanner.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.sources.NrData;
import com.gtnhplanner.power.sources.NrMiners;
import com.gtnhplanner.power.sources.NrResources;
import com.gtnhplanner.power.sources.NrSources;

/**
 * The Void Miners against bwcrossmod.galacticgreg.MTEVoidMinerBase (GregTech 5.09.54.265): a draw every 10 ticks from
 * the dimension's table at weight / total weight, a stack of TIER_MULTIPLIER (x the noble gas's multiplier, 1 L of it
 * a draw), VP[tier + 5] EU/t. The tables here are made up; the game's are read in game.
 */
class NrMinersTest {

    private static final String A = NrResources.item("gregtech:gt.blockores@86"),
        B = NrResources.item("gregtech:gt.blockores@32"), C = NrResources.item("bartworks:bw.blockores.01@11");
    private static final String NEON = NrResources.fluid("neon"), XENON = NrResources.fluid("xenon"),
        OGANESSON = NrResources.fluid("oganesson");

    /** The Overworld draws A three times as often as B; the Moon only C. */
    static final NrData DATA = new NrData(
        List.of(
            new NrData.Dimension(
                "Overworld",
                "T0 Overworld",
                List.of(new NrData.Ore(A, "Ore A", 3f), new NrData.Ore(B, "Ore B", 1f))),
            new NrData.Dimension("moon", "T1 Moon", List.of(new NrData.Ore(C, "Ore C", 2f)))),
        List.of(
            new NrData.NobleGas("neon", "Neon", NEON, 4),
            new NrData.NobleGas("krypton", "Krypton", NrResources.fluid("krypton"), 8),
            new NrData.NobleGas("xenon", "Xenon", XENON, 16),
            new NrData.NobleGas("oganesson", "Oganesson", OGANESSON, 64)),
        List.of(),
        List.of());

    private static PowerSource source(final String id) {
        for (final PowerSource source : NrSources.sources(DATA)) if (source.id()
            .equals(id)) return source;
        return fail("No source " + id);
    }

    private static PowerModel compute(final String id, final Map<String, String> settings) {
        return source(id).compute(settings);
    }

    private static double flow(final List<Flow> flows, final String name) {
        for (final Flow flow : flows) if (flow.name()
            .equals(name)) return flow.perSecond();
        return fail("No flow " + name + " in " + flows);
    }

    @Test
    void voidMinerOneDrawsTwoOresASecondByWeightAtLuvPower() {
        final PowerModel model = compute(NrMiners.VOID_MINER_1, Map.of());
        // VP[6] = 32,768 x 30 / 32.
        assertEquals(-30_720, model.euPerTick());
        assertEquals(List.of(), model.inputs());
        // 20 / 10 ticks = 2 draws a second, a stack of 1: A 2 x 3/4, B 2 x 1/4.
        assertEquals(
            2,
            model.outputs()
                .size());
        assertEquals(1.5, flow(model.outputs(), A), 1e-12);
        assertEquals(0.5, flow(model.outputs(), B), 1e-12);
        assertEquals("2", model.stat("Ores a second"));
        assertEquals("1", model.stat("Ores a draw"));
        assertEquals("2", model.stat("Kinds of ore"));
    }

    @Test
    void tiersDrawMoreAndHarderMinerTwoAtZpmMinerThreeAtUv() {
        final PowerModel two = compute(NrMiners.VOID_MINER_2, Map.of());
        assertEquals(-122_880, two.euPerTick());
        assertEquals(3, flow(two.outputs(), A), 1e-12);
        assertEquals(1, flow(two.outputs(), B), 1e-12);
        final PowerModel three = compute(NrMiners.VOID_MINER_3, Map.of());
        assertEquals(-491_520, three.euPerTick());
        assertEquals("6", three.stat("Ores a second"));
        assertEquals(4.5, flow(three.outputs(), A), 1e-12);
    }

    @Test
    void aNobleGasMultipliesTheStackAndTakesALitreADraw() {
        // Void Miner III on xenon: 3 x 16 = 48 an ore, 96 a second.
        final PowerModel xenon = compute(NrMiners.VOID_MINER_3, Map.of("nobleGas", "xenon"));
        assertEquals(List.of(new Flow(XENON, 2, PowerModel.Unit.L)), xenon.inputs());
        assertEquals(72, flow(xenon.outputs(), A), 1e-9);
        assertEquals(24, flow(xenon.outputs(), B), 1e-9);
        assertEquals("48", xenon.stat("Ores a draw"));
        assertEquals(-491_520, xenon.euPerTick());
        // Void Miner II on oganesson: 2 x 64 = 128 an ore; neon on Void Miner I: 4.
        assertEquals("256", compute(NrMiners.VOID_MINER_2, Map.of("nobleGas", "oganesson")).stat("Ores a second"));
        final PowerModel neon = compute(NrMiners.VOID_MINER_1, Map.of("nobleGas", "neon"));
        assertEquals(6, flow(neon.outputs(), A), 1e-12);
        assertEquals(2, flow(neon.inputs(), NEON), 1e-12);
    }

    @Test
    void theDimensionPicksTheTable() {
        final PowerModel moon = compute(NrMiners.VOID_MINER_1, Map.of("dimension", "moon"));
        assertEquals(List.of(new Flow(C, 2, PowerModel.Unit.ITEM)), moon.outputs());
    }

    @Test
    void theFilterKeepsOneOreAtItsOwnRateAndVoidsTheRest() {
        final PowerModel onlyB = compute(NrMiners.VOID_MINER_1, Map.of("filter", B));
        assertEquals(List.of(new Flow(B, 0.5, PowerModel.Unit.ITEM)), onlyB.outputs());
        assertEquals(List.of(), onlyB.warnings());
        // The gas is still taken for every draw, kept or voided.
        final PowerModel gassed = compute(NrMiners.VOID_MINER_1, Map.of("filter", B, "nobleGas", "neon"));
        assertEquals(2, flow(gassed.outputs(), B), 1e-12);
        assertEquals(2, flow(gassed.inputs(), NEON), 1e-12);
        // An ore this dimension never draws: nothing comes out.
        final PowerModel none = compute(NrMiners.VOID_MINER_1, Map.of("filter", C));
        assertEquals(List.of(), none.outputs());
        assertEquals(List.of(NrMiners.FILTER_EMPTY), none.warnings());
    }

    @Test
    void offersTheGameDimensionsGasesAndEveryOre() {
        final PowerSource source = source(NrMiners.VOID_MINER_1);
        assertEquals(PowerGroup.OTHER, source.group());
        assertEquals("Void Miner I", source.name());
        assertEquals("LuV", source.unlock());
        assertEquals("ZPM", source(NrMiners.VOID_MINER_2).unlock());
        assertEquals("UV", source(NrMiners.VOID_MINER_3).unlock());
        final PowerSetting.Select dimension = assertInstanceOf(PowerSetting.Select.class, source.setting("dimension"));
        assertEquals("Overworld", dimension.defaultKey());
        assertEquals(List.of("Overworld", "moon"), keys(dimension));
        assertEquals(
            List.of(NrMiners.NO_GAS, "neon", "krypton", "xenon", "oganesson"),
            keys(assertInstanceOf(PowerSetting.Select.class, source.setting("nobleGas"))));
        // Every ore of every dimension, by name, after keeping them all.
        assertEquals(
            List.of(NrMiners.ALL_ORES, A, B, C),
            keys(assertInstanceOf(PowerSetting.Select.class, source.setting("filter"))));
    }

    @Test
    void noTableNoMinersAndTheFlowsResolveToTheGameIds() {
        assertTrue(
            NrSources.sources(NrData.EMPTY)
                .isEmpty());
        final PowerResources.Ref ore = PowerResources.resolve(A);
        assertNotNull(ore);
        assertEquals("item", ore.kind);
        assertEquals("gregtech:gt.blockores@86", ore.id);
        final PowerResources.Ref gas = PowerResources.resolve(XENON);
        assertNotNull(gas);
        assertEquals("fluid", gas.kind);
        assertEquals("xenon", gas.id);
        // The controllers are the cards' pictures, registered as the sources are made.
        NrSources.sources(DATA);
        assertEquals("gregtech:gt.blockmachines@12741", NrResources.machine(NrMiners.VOID_MINER_1));
        assertEquals("gregtech:gt.blockmachines@12739", NrResources.machine(NrMiners.VOID_MINER_3));
    }

    private static List<String> keys(final PowerSetting.Select select) {
        final List<String> out = new ArrayList<>();
        for (final PowerSetting.Option option : select.options()) out.add(option.key());
        return out;
    }
}
