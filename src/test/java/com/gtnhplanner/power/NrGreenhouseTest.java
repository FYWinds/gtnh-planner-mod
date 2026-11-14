package com.gtnhplanner.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.sources.NrGreenhouse;

/**
 * kubatech's Extreme Industrial Greenhouse (GT 5.09.54.265 MTEExtremeIndustrialGreenhouse.checkProcessing and
 * EIGNormalMode), worked by hand: tier = (int) (ln(EU/8) / ln 4 + 1e-8), 2^(tier - 4) seed types of 64 seeds, a cycle
 * of max(20, 100 / (tier - 3)) ticks at (long) (V[tier] x 0.99) EU/t, 1,000 L of water a seed a cycle, Weed-EX a seed
 * from 1,000 seeds, fertilizer two a seed for x5.
 */
class NrGreenhouseTest {

    private static final String WATER = "nr:fluid:water", WEED_EX = "nr:fluid:weedex9000";
    private static final String WHEAT_SEEDS = "nr:item:minecraft:wheat_seeds", WHEAT = "nr:item:minecraft:wheat";
    private static final String BONE_MEAL = "nr:item:minecraft:dye@15";
    private static final String STONE = "nr:item:minecraft:stone";

    /** A greenhouse whose wheat seed grows one wheat a cycle and refuses stone. */
    private static final NrGreenhouse.Data DATA = new NrGreenhouse.Data() {

        @Override
        public List<Option> seeds() {
            return List.of(new Option(STONE, "Stone"), new Option(WHEAT_SEEDS, "Seeds"));
        }

        @Override
        @Nullable
        public Map<String, Double> drops(final String seed) {
            return WHEAT_SEEDS.equals(seed) ? Map.of(WHEAT, 1.0) : null;
        }

        @Override
        public List<Option> fertilizers() {
            return List.of(new Option(BONE_MEAL, "Bone Meal"));
        }

        @Override
        public String water() {
            return WATER;
        }

        @Override
        public String weedEx() {
            return WEED_EX;
        }
    };

    private static PowerModel compute(final Map<String, String> settings) {
        return NrGreenhouse.source(DATA)
            .compute(settings);
    }

    private static double flow(final List<Flow> flows, final String name) {
        for (final Flow flow : flows) if (flow.name()
            .equals(name)) return flow.perSecond();
        return 0;
    }

    // ---- the game's maths

    @Test
    void voltageTierCountsAmps() {
        // One 2 A EV hatch: ln(4,096 / 8) / ln 4 = 4.5.
        assertEquals(4, NrGreenhouse.voltageTier(4_096));
        // Exactly 2,048: 4.0, which the 1e-8 keeps from falling to 3.
        assertEquals(4, NrGreenhouse.voltageTier(2_048));
        // Two 2 A EV hatches run it at IV.
        assertEquals(5, NrGreenhouse.voltageTier(8_192));
        assertEquals(3, NrGreenhouse.voltageTier(1_024));
    }

    @Test
    void slotsDoubleFromEv() {
        assertEquals(0, NrGreenhouse.slots(3));
        assertEquals(1, NrGreenhouse.slots(4));
        assertEquals(2, NrGreenhouse.slots(5));
        assertEquals(16, NrGreenhouse.slots(8));
    }

    @Test
    void cyclesShortenToASecond() {
        assertEquals(100, NrGreenhouse.cycleTicks(4));
        assertEquals(50, NrGreenhouse.cycleTicks(5));
        assertEquals(33, NrGreenhouse.cycleTicks(6));
        assertEquals(25, NrGreenhouse.cycleTicks(7));
        assertEquals(20, NrGreenhouse.cycleTicks(8));
        assertEquals(20, NrGreenhouse.cycleTicks(9));
    }

    @Test
    void drawsNinetyNinePercentOfAnAmpTruncated() {
        // 2,048 x 0.99 = 2,027.52; 8,192 x 0.99 = 8,110.08; 524,288 x 0.99 = 519,045.12.
        assertEquals(2_027, NrGreenhouse.euPerTick(4));
        assertEquals(8_110, NrGreenhouse.euPerTick(5));
        assertEquals(519_045, NrGreenhouse.euPerTick(8));
    }

    @Test
    void weedExStartsAtAThousandSeeds() {
        assertEquals(0, NrGreenhouse.weedExPerCycle(999));
        assertEquals(1_000, NrGreenhouse.weedExPerCycle(1_000));
        assertEquals(64_000, NrGreenhouse.waterPerCycle(64));
        assertEquals(5, NrGreenhouse.multiplier(1), 0);
        assertEquals(1, NrGreenhouse.multiplier(0), 0);
    }

    @Test
    void rainbowCactusDropsItsExpectedDyes() {
        // 3 + round(4.91662 u): round averages (1 + 2 + 3 + 4 + 5 x 0.41662) / 4.91662 = 2.457603.
        assertEquals(5.457603 / 16, NrGreenhouse.rainbowDyesEach(16), 1e-6);
        assertEquals(0, NrGreenhouse.rainbowDyesEach(0), 0);
    }

    // ---- a card

    @Test
    void anEvGreenhouseFillsItsOneSlot() {
        final PowerModel model = compute(Map.of("seed", WHEAT_SEEDS));
        // 64 seeds, a cycle every 5 s.
        assertEquals(-2_027, model.euPerTick(), 0);
        assertEquals(64_000 / 5.0, flow(model.inputs(), WATER), 1e-9);
        assertEquals(64 / 5.0, flow(model.outputs(), WHEAT), 1e-9);
        assertEquals(0, flow(model.inputs(), WEED_EX), 0);
        assertEquals("64", model.stat("Seed capacity"));
        assertEquals("5 s", model.stat("Cycle"));
    }

    @Test
    void fertilizerUsesTwoASeedForFiveTimesTheCrop() {
        final PowerModel model = compute(Map.of("seed", WHEAT_SEEDS, "fertilizer", BONE_MEAL));
        assertEquals(128 / 5.0, flow(model.inputs(), BONE_MEAL), 1e-9);
        assertEquals(64, flow(model.outputs(), WHEAT), 1e-9);
    }

    @Test
    void twoEvHatchesRunItAtIv() {
        final PowerModel model = compute(Map.of("seed", WHEAT_SEEDS, "amps", "4"));
        assertEquals(-8_110, model.euPerTick(), 0);
        // 128 seeds, a cycle every 2.5 s.
        assertEquals(128_000 / 2.5, flow(model.inputs(), WATER), 1e-9);
        assertEquals(128 / 2.5, flow(model.outputs(), WHEAT), 1e-9);
    }

    @Test
    void aFullUvGreenhouseTakesWeedEx() {
        final PowerModel model = compute(Map.of("seed", WHEAT_SEEDS, "tier", "UV"));
        // 16 slots of 64: 1,024 seeds, a cycle a second.
        assertEquals(-519_045, model.euPerTick(), 0);
        assertEquals(1_024, flow(model.inputs(), WEED_EX), 1e-9);
        assertEquals(1_024_000, flow(model.inputs(), WATER), 1e-9);
        assertEquals(1_024, flow(model.outputs(), WHEAT), 1e-9);
    }

    @Test
    void plantsFewerWhenAsked() {
        final PowerModel model = compute(Map.of("seed", WHEAT_SEEDS, "seeds", "10"));
        assertEquals(10 / 5.0, flow(model.outputs(), WHEAT), 1e-9);
        assertEquals(10_000 / 5.0, flow(model.inputs(), WATER), 1e-9);
    }

    @Test
    void refusesWhatTheGameRefuses() {
        // More seeds than a slot holds: EIG_seedOverflow.
        assertEquals(
            List.of("More seeds than the greenhouse holds at this voltage: it will not run."),
            compute(Map.of("seed", WHEAT_SEEDS, "seeds", "65")).warnings());
        assertEquals(0, compute(Map.of("seed", WHEAT_SEEDS, "seeds", "65")).euPerTick(), 0);
        assertEquals(
            List.of("Below EV the greenhouse has no room for seeds, so it does nothing."),
            compute(Map.of("seed", WHEAT_SEEDS, "tier", "HV")).warnings());
        assertEquals(List.of("The greenhouse does not take this seed."), compute(Map.of("seed", STONE)).warnings());
    }

    @Test
    void defaultsToWheatSeedsAtEv() {
        final PowerSource source = NrGreenhouse.source(DATA);
        assertEquals(WHEAT_SEEDS, ((PowerSetting.Select) source.setting("seed")).defaultKey());
        assertEquals("EV", ((PowerSetting.Select) source.setting("tier")).defaultKey());
        assertEquals(PowerGroup.OTHER, source.group());
    }
}
