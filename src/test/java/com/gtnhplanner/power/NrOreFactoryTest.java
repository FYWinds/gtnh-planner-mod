package com.gtnhplanner.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.sources.NrOreFactory;
import com.gtnhplanner.power.sources.NrOreFactory.Kind;
import com.gtnhplanner.power.sources.NrOreFactory.Mode;
import com.gtnhplanner.power.sources.NrOreFactory.Output;
import com.gtnhplanner.power.sources.NrOreFactory.Recipe;
import com.gtnhplanner.power.sources.NrOreFactory.Step;

/**
 * GregTech's Integrated Ore Factory (GT 5.09.54.265 MTEIntegratedOreFactory.checkProcessing), worked by hand on a
 * made-up ore: getMaxInputEu / 30 ores a run, 200 L of distilled water and 2 L of lubricant an ore, 30 EU/t an ore, the
 * mode's fixed time, and its chain of recipes with getOutputStack's chanced outputs at their expected value.
 */
class NrOreFactoryTest {

    private static final String ORE = "nr:item:test:ore", CRUSHED = "nr:item:test:crushed",
        PURIFIED = "nr:item:test:purified", GEM = "nr:item:test:gem", DUST = "nr:item:test:dust",
        TINY = "nr:item:test:tiny", STONE = "nr:item:test:stone_dust", IMPURE = "nr:item:test:impure";
    private static final String WATER = "nr:fluid:ic2distilledwater", LUBE = "nr:fluid:lubricant",
        MERCURY = "nr:fluid:mercury";

    /**
     * An ore that macerates to 2 crushed and a stone dust; washes to a purified, a stone dust and a tiny pile at
     * 33.33%;
     * sifts to a gem and a dust; bathes in 1,000 L of mercury to a purified; and macerates crushed to an impure dust,
     * which centrifuges to a dust.
     */
    private static final NrOreFactory.Data DATA = new NrOreFactory.Data() {

        @Override
        public List<Option> ores() {
            return List.of(new Option(ORE, "Test Ore"));
        }

        @Override
        public List<Option> bathFluids() {
            return List.of(new Option(MERCURY, "Mercury"));
        }

        @Override
        public String distilledWater() {
            return WATER;
        }

        @Override
        public String lubricant() {
            return LUBE;
        }

        @Override
        public Set<Kind> kinds(final String item) {
            return switch (item) {
                case ORE -> Set.of(Kind.ORE);
                case CRUSHED -> Set.of(Kind.CRUSHED);
                case PURIFIED -> Set.of(Kind.PURIFIED);
                case IMPURE -> Set.of(Kind.IMPURE);
                default -> Set.of();
            };
        }

        @Override
        @Nullable
        public Recipe recipe(final Step step, final String item, @Nullable final String bath) {
            return switch (step) {
                case MACERATE -> ORE.equals(item)
                    ? new Recipe(List.of(new Output(CRUSHED, 2, 10_000), new Output(STONE, 1, 10_000)), null, 0)
                    : CRUSHED.equals(item) ? new Recipe(List.of(new Output(IMPURE, 1, 10_000)), null, 0) : null;
                case WASH -> CRUSHED.equals(item) ? new Recipe(
                    List.of(new Output(PURIFIED, 1, 10_000), new Output(TINY, 1, 3_333), new Output(STONE, 1, 10_000)),
                    null,
                    0) : null;
                case SIFT -> PURIFIED.equals(item)
                    ? new Recipe(List.of(new Output(GEM, 1, 10_000), new Output(DUST, 1, 10_000)), null, 0)
                    : null;
                case BATH -> CRUSHED.equals(item) && MERCURY.equals(bath)
                    ? new Recipe(List.of(new Output(PURIFIED, 1, 10_000)), MERCURY, 1_000)
                    : null;
                case CENTRIFUGE -> IMPURE.equals(item) ? new Recipe(List.of(new Output(DUST, 1, 10_000)), null, 0)
                    : null;
                default -> null;
            };
        }

        @Override
        public boolean voidable(final String item) {
            return STONE.equals(item);
        }
    };

    private static PowerModel compute(final Map<String, String> settings) {
        return NrOreFactory.source(DATA)
            .compute(settings);
    }

    private static double flow(final List<Flow> flows, final String name) {
        for (final Flow flow : flows) if (flow.name()
            .equals(name)) return flow.perSecond();
        return 0;
    }

    // ---- the game's maths

    @Test
    void oneOreAPerThirtyEuOfHatches() {
        // A 2 A UV hatch: 1,048,576 / 30 = 34,952.5.
        assertEquals(34_952, NrOreFactory.parallel(1_048_576));
        assertEquals(2, NrOreFactory.parallel(64));
        assertEquals(0, NrOreFactory.parallel(29));
    }

    @Test
    void modesRunTheirFixedTimes() {
        assertEquals(600, Mode.MAC_WASH_THERMAL_MAC.ticks());
        assertEquals(300, Mode.MAC_WASH_MAC_CENTRI.ticks());
        assertEquals(200, Mode.MAC_MAC_CENTRI.ticks());
        assertEquals(400, Mode.MAC_WASH_SIFT.ticks());
        assertEquals(340, Mode.MAC_CHEM_MAC_CENTRI.ticks());
        assertEquals(640, Mode.MAC_CHEM_THERMAL_MAC.ticks());
        assertEquals(20, Mode.FORGE_FORGE_SIMPLEWASH.ticks());
    }

    @Test
    void chancedOutputsAreTheGamesCeiledNormal() {
        assertEquals(7, NrOreFactory.expected(7, 10_000), 0);
        assertEquals(0, NrOreFactory.expected(7, 0), 0);
        // n = 1, p = 0.1: X ~ N(0.1, 0.3); P(X > 0) + P(X > 1) + ... = 0.630559 + 0.001350.
        assertEquals(0.6319085579698, NrOreFactory.expected(1, 1_000), 1e-10);
        // A wide spread: ceil adds a half on average.
        assertEquals(50.5, NrOreFactory.expected(100, 5_000), 1e-9);
        assertEquals(1_000.5, NrOreFactory.expected(10_000, 1_000), 1e-9);
    }

    @Test
    void chainsTheModesSteps() {
        final NrOreFactory.Run run = NrOreFactory.chain(DATA, ORE, Mode.MAC_WASH_SIFT, null, false, 1_000);
        // 1,000 ores: 2,000 crushed and 1,000 stone dust; washed, 2,000 purified, 2,000 more stone dust and a tiny pile
        // at 33.33% (mean 666.6, a spread of 21: +0.5); sifted, a gem and a dust each.
        assertEquals(
            2_000,
            run.outputs()
                .get(GEM),
            1e-9);
        assertEquals(
            2_000,
            run.outputs()
                .get(DUST),
            1e-9);
        assertEquals(
            3_000,
            run.outputs()
                .get(STONE),
            1e-9);
        assertEquals(
            667.1,
            run.outputs()
                .get(TINY),
            1e-9);
        assertEquals(
            4,
            run.outputs()
                .size());
    }

    @Test
    void voidsStoneDustAtEveryStep() {
        final NrOreFactory.Run run = NrOreFactory.chain(DATA, ORE, Mode.MAC_WASH_SIFT, null, true, 1_000);
        assertNull(
            run.outputs()
                .get(STONE));
        assertEquals(
            2_000,
            run.outputs()
                .get(GEM),
            1e-9);
    }

    // ---- a card

    @Test
    void anEvFactorySiftsSixPointEightOresASecond() {
        // A 2 A EV hatch: 4,096 / 30 = 136 ores a run of 20 s.
        final PowerModel model = compute(Map.of("ore", ORE, "mode", Mode.MAC_WASH_SIFT.key, "tier", "EV"));
        assertEquals(-4_080, model.euPerTick(), 0);
        assertEquals(6.8, flow(model.inputs(), ORE), 1e-9);
        assertEquals(136 * 200 / 20.0, flow(model.inputs(), WATER), 1e-9);
        assertEquals(136 * 2 / 20.0, flow(model.inputs(), LUBE), 1e-9);
        assertEquals(13.6, flow(model.outputs(), GEM), 1e-9);
        assertEquals("136", model.stat("Parallel"));
    }

    @Test
    void theBathUsesItsFluid() {
        final Map<String, String> settings = Map
            .of("ore", ORE, "mode", Mode.MAC_CHEM_MAC_CENTRI.key, "tier", "EV", "bath", MERCURY);
        final PowerModel model = compute(settings);
        // 272 crushed a run of 17 s, 1,000 L of mercury each; purified passes the maceration by.
        assertEquals(272_000 / 17.0, flow(model.inputs(), MERCURY), 1e-9);
        assertEquals(272 / 17.0, flow(model.outputs(), PURIFIED), 1e-9);
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void withoutABathFluidTheBathPassesTheOreBy() {
        final PowerModel model = compute(Map.of("ore", ORE, "mode", Mode.MAC_CHEM_MAC_CENTRI.key, "tier", "EV"));
        // Crushed is macerated to impure dust, which centrifuges to dust.
        assertEquals(272 / 17.0, flow(model.outputs(), DUST), 1e-9);
        assertEquals(0, flow(model.inputs(), MERCURY), 0);
        assertEquals(List.of("No bath fluid for this ore: the chemical bath passes it by."), model.warnings());
    }

    @Test
    void readsAtLeastOneAmp() {
        final PowerModel model = compute(Map.of("ore", ORE, "tier", "LV", "amps", "0"));
        // Amps read at least 1: 32 EU/t is one ore.
        assertEquals(-30, model.euPerTick(), 0);
    }
}
