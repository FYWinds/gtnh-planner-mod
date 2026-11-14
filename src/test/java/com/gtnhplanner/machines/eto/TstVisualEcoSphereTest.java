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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.machines.web.NodeMath;
import com.gtnhplanner.machines.web.Web;

/**
 * The Eco-Sphere Simulator (TstVisualEcoSphere) on cards made from its four fake maps: every number worked by hand from
 * TST 0.8.1's controller and modes (and CropsNH 2.0.140 for the greenhouse). A run's random scale (x U(0.85, 1),
 * truncated, at least 1) is its expected value: for a stack of A that is A x 0.925 less 0.5 where A x 0.15 spans whole
 * numbers.
 */
class TstVisualEcoSphereTest {

    private static final String ARBOREAL = "tst.recipe.TreeGrowthSimulatorWithoutToolFakeRecipes.name",
        AQUATIC = "tst.recipe.AquaticZoneSimulatorFakeRecipes.name",
        GREENHOUSE = "tst.recipe.ArtificialGreenHouseFakeRecipes.name",
        CLONING = "tst.recipe.DirectedMobClonerFakeRecipes.name", ECO = "tst-eco-sphere-simulator";

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

    /** Settings as id, key pairs. */
    private static Map<String, String> settings(final String... pairs) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return out;
    }

    private static final String T2 = "tier-2";

    // region Every mode

    /** Address 0 (blood to Life Essence): its parallels are the power-limited ones themselves. */
    private static Web.Recipe bloodToEssence() {
        return recipe(CLONING, 0, 100, List.of(fluid("blood", 100)), List.of(fluid("lifeessence", 100)));
    }

    @Test
    void powerTierBuysParallelsOnEitherCurveAtAFlatRunEut() {
        final Web.Recipe r = bloodToEssence();
        final Map<String, String> tierTwo = settings(TstVisualEcoSphere.STRUCTURE, T2);
        // LV 1 A: 32 EU/t is power tier 1, one parallel on both structures at 30 EU/t
        assertRun(run(r, ECO, "LV", 1, Map.of()), 100, 1, 30);
        assertRun(run(r, ECO, "LV", 1, tierTwo), 100, 1, 30);
        // MV (t = 2): Tier 1 floor(2 x 2^(0.1 x (8 + ln(25 + e^23) / ln 5))) = floor(9.38) = 9; Tier 2 4
        assertRun(run(r, ECO, "MV", 1, Map.of()), 100, 9, 120);
        assertRun(run(r, ECO, "MV", 1, tierTwo), 100, 4, 120);
        // LuV 1 A and IV 4 A are both 32768 EU/t, t = 6: Tier 1 1914 (1914.41), Tier 2 4^5 = 1024, 30 x 4^5 EU/t
        assertRun(run(r, ECO, "LuV", 1, Map.of()), 100, 1914, 30720);
        assertRun(run(r, ECO, "IV", 4, tierTwo), 100, 1024, 30720);
        // UV (t = 8): Tier 2's 16384 passes Tier 1's 16314 (16314.72)
        assertRun(run(r, ECO, "UV", 1, Map.of()), 100, 16314, 491520);
        assertRun(run(r, ECO, "UV", 1, tierTwo), 100, 16384, 491520);
        // ULV 3 A: 24 EU/t is power tier 0, insufficientPower(32)
        assertGate(run(r, ECO, "ULV", 3, Map.of()), TstVisualEcoSphere.POWER_GATE);
    }

    @Test
    void speedRunsTwentyTicksAndEveryReductionHalvesTheMediumDownToOneLitre() {
        // HV T1 (t = 3, P = 40): a Speed upgrade is 20 ticks at the same 480 EU/t and one halving: 50 L blood a
        // parallel
        final NodeMath.Result fast = run(bloodToEssence(), ECO, "HV", 1, settings(TstVisualEcoSphere.SPEED, "1"));
        assertRun(fast, 20, 40, 480);
        assertEquals(50.0 * 40, inputPerRun(fast, 0), EPS);
        // Temporal fluid 100 L a sapling: 50, 25, 12, 6, 3 after five halvings, then 1 and never below
        final Web.Recipe time = timeTree();
        final Map<String, String> five = settings(
            TstVisualEcoSphere.STRUCTURE,
            T2,
            TstVisualEcoSphere.GLASS,
            "uev",
            TstVisualEcoSphere.FLUID_REDUCTION,
            "5");
        // EV T2: t = 4, P = 64, 1920 EU/t
        final NodeMath.Result three = run(time, ECO, "EV", 1, five);
        assertRun(three, 100, 64, 1920);
        assertEquals(3.0 * 64, inputPerRun(three, 0), EPS);
        five.put(TstVisualEcoSphere.FLUID_REDUCTION, "7");
        assertEquals(1.0 * 64, inputPerRun(run(time, ECO, "EV", 1, five), 0), EPS);
        five.put(TstVisualEcoSphere.FLUID_REDUCTION, "9");
        assertEquals(1.0 * 64, inputPerRun(run(time, ECO, "EV", 1, five), 0), EPS);
    }

    @Test
    void upgradesFitOneSlotAndOneAGlassTierTwiceOnTierTwo() {
        final Web.Recipe r = bloodToEssence();
        // ZPM glass, Tier 1: 1 + 1 = 2 slots
        assertRun(run(r, ECO, "HV", 1, settings(TstVisualEcoSphere.OUTPUT, "2")), 100, 40, 480);
        assertGate(
            run(r, ECO, "HV", 1, settings(TstVisualEcoSphere.OUTPUT, "2", TstVisualEcoSphere.SPEED, "1")),
            TstVisualEcoSphere.UPGRADE_GATE);
        // UEV glass, Tier 2: min(1 + 4 x 2, 9) = 9 slots
        final Map<String, String> nine = settings(
            TstVisualEcoSphere.STRUCTURE,
            T2,
            TstVisualEcoSphere.GLASS,
            "uev",
            TstVisualEcoSphere.FLUID_REDUCTION,
            "9");
        assertRun(run(r, ECO, "HV", 1, nine), 100, 16, 480);
        nine.put(TstVisualEcoSphere.BLOOD_ORB, "yes");
        assertGate(run(r, ECO, "HV", 1, nine), TstVisualEcoSphere.UPGRADE_GATE);
    }

    // endregion

    // region Arboreal Genesis

    /** GT's oak: log, sapling, leaves and apple 1 each, the page's x 20 / 3 / 8 / 1, 2000 L water. */
    private static Web.Recipe oak() {
        return recipe(
            ARBOREAL,
            0,
            100,
            List.of(fluid("water", 2000)),
            List.of(
                item("minecraft:log", 20),
                item("minecraft:sapling", 3),
                item("minecraft:leaves", 8),
                item("minecraft:apple", 1)));
    }

    /** The Tree of Time on temporal fluid, 100 L: any sapling makes its products. */
    private static Web.Recipe timeTree() {
        return recipe(
            ARBOREAL,
            0,
            100,
            List.of(fluid("temporalfluid", 100)),
            List.of(
                item("twilightforest:tile.tfmagiclog", 20),
                item("twilightforest:tile.tfsapling@5", 3),
                item("twilightforest:tile.tfmagicleaves", 8),
                item("twilightforest:tile.tfmagiclogspecial", 1)));
    }

    @Test
    void oakMakesThePageForEveryParallelAndSapling() {
        // HV T1: t = 3, P = 40 at 480 EU/t; one sapling, all four categories: no focus bonus
        final NodeMath.Result r = run(oak(), ECO, "HV", 1, Map.of());
        assertRun(r, 100, 40, 480);
        assertEquals(2000.0 * 40, inputPerRun(r, 0), EPS);
        // logs (long) (1 x 20 x 40) = 800, scaled to [680, 800): 739.5 expected
        assertEquals(739.5, outputPerRun(r, 0), EPS);
        assertEquals(110.5, outputPerRun(r, 1), EPS);
        assertEquals(295.5, outputPerRun(r, 2), EPS);
        assertEquals(36.5, outputPerRun(r, 3), EPS);
    }

    @Test
    void fewerCategoriesFocusTheRestAndSaplingsMultiplyTheRun() {
        // Logs alone of four: focus 1 + 3 / 1 / 3 = 2; four saplings (one Capacity upgrade: a slot holds 4)
        final Map<String, String> logs = settings(
            TstVisualEcoSphere.SAPLING_OUTPUT,
            "no",
            TstVisualEcoSphere.LEAVES,
            "no",
            TstVisualEcoSphere.FRUIT,
            "no",
            TstVisualEcoSphere.SAPLINGS,
            "4",
            TstVisualEcoSphere.CAPACITY,
            "1");
        final NodeMath.Result r = run(oak(), ECO, "HV", 1, logs);
        // 40 x 4 = 160 parallels sharing the run's 480 EU/t; 2000 L a sapling
        assertRun(r, 100, 160, 480);
        assertEquals(2000.0 * 4 * 40, inputPerRun(r, 0), EPS);
        // (long) (1 x 20 x 160 x 2.0) = 6400: [5440, 6400) is 5919.5
        assertEquals(5919.5, outputPerRun(r, 0), EPS);
        assertEquals(0, outputPerRun(r, 1), EPS);
        // Logs and leaves: focus the float 1 + 2 / 2 / 3 = 1.3333334f; (long) 1066.67 = 1066 and (long) 426.67 = 426
        final NodeMath.Result two = run(
            oak(),
            ECO,
            "HV",
            1,
            settings(TstVisualEcoSphere.SAPLING_OUTPUT, "no", TstVisualEcoSphere.FRUIT, "no"));
        assertEquals(985.5497185741087, outputPerRun(two, 0), EPS);
        assertEquals(393.5492957746478, outputPerRun(two, 2), EPS);
        // A slot holds one sapling without Capacity upgrades; nothing selected stops the run
        assertGate(run(oak(), ECO, "HV", 1, settings(TstVisualEcoSphere.SAPLINGS, "2")), TstVisualEcoSphere.INPUT_GATE);
        assertGate(
            run(
                oak(),
                ECO,
                "HV",
                1,
                settings(
                    TstVisualEcoSphere.LOGS,
                    "no",
                    TstVisualEcoSphere.SAPLING_OUTPUT,
                    "no",
                    TstVisualEcoSphere.LEAVES,
                    "no",
                    TstVisualEcoSphere.FRUIT,
                    "no")),
            TstVisualEcoSphere.TREE_SELECTION_GATE);
    }

    @Test
    void aTreeWithoutLeavesHasItsFruitThird() {
        // Log, sapling and a fruit of 1 (1 is no multiple of 8): three categories; dropping the fruit focuses the
        // other two by 1 + 1 / 2 / 3 = 1.1666666f
        final Web.Recipe r = recipe(
            ARBOREAL,
            0,
            100,
            List.of(fluid("water", 2000)),
            List.of(item("etfuturum:log", 20), item("etfuturum:sapling", 3), item("etfuturum:chorus_fruit", 1)));
        assertEquals(36.5, outputPerRun(run(r, ECO, "HV", 1, settings(TstVisualEcoSphere.LEAVES, "no")), 2), EPS);
        final NodeMath.Result noFruit = run(r, ECO, "HV", 1, settings(TstVisualEcoSphere.FRUIT, "no"));
        assertEquals(0, outputPerRun(noFruit, 2), EPS);
        // (long) (20 x 40 x 1.1666666) = 933, (long) (3 x 40 x 1.1666666) = 139
        assertEquals(862.5248302965344, outputPerRun(noFruit, 0), EPS);
        assertEquals(128.07194244604315, outputPerRun(noFruit, 1), EPS);
    }

    @Test
    void specialMediaTakeAnySaplingsAndUuMatterSquaresTheMultiplier() {
        // Temporal fluid, EV T2 (P = 64), seven reductions (1 L a sapling) and one Capacity upgrade: two slots of 4;
        // six saplings are a stack of 4 and one of 2: (long) (20 x 256) + (long) (20 x 128) = 7680, 7103.5 expected
        final Map<String, String> six = settings(
            TstVisualEcoSphere.STRUCTURE,
            T2,
            TstVisualEcoSphere.GLASS,
            "uev",
            TstVisualEcoSphere.FLUID_REDUCTION,
            "7",
            TstVisualEcoSphere.CAPACITY,
            "1",
            TstVisualEcoSphere.SAPLINGS,
            "6");
        final NodeMath.Result time = run(timeTree(), ECO, "EV", 1, six);
        assertRun(time, 100, 64 * 6, 1920);
        assertEquals(1.0 * 6 * 64, inputPerRun(time, 0), EPS);
        assertEquals(7103.5, outputPerRun(time, 0), EPS);
        six.put(TstVisualEcoSphere.SAPLINGS, "9");
        assertGate(run(timeTree(), ECO, "EV", 1, six), TstVisualEcoSphere.INPUT_GATE);
        // UU matter, 500 L: no saplings counted; a pick of one product already x 20, x 20 again: 400 x 40 = 16000
        final Web.Recipe uu = recipe(
            ARBOREAL,
            0,
            100,
            List.of(fluid("ic2uumatter", 500)),
            List.of(
                item("minecraft:log", 1),
                item("minecraft:sapling", 1),
                item("minecraft:leaves", 1),
                item("minecraft:apple", 1)));
        final NodeMath.Result r = run(uu, ECO, "HV", 1, Map.of());
        assertRun(r, 100, 40, 480);
        assertEquals(500.0 * 40, inputPerRun(r, 0), EPS);
        assertEquals(14799.5, outputPerRun(r, 0), EPS);
        assertEquals(332.5, outputPerRun(r, 1), EPS);
        assertEquals(2367.5, outputPerRun(r, 2), EPS);
        assertEquals(36.5, outputPerRun(r, 3), EPS);
    }

    // endregion

    // region Aquatic Simulation

    private static Web.Recipe aquatic(final String fluid, final int litres, final String member, final int weight) {
        return recipe(AQUATIC, 0, 100, List.of(fluid(fluid, litres)), List.of(item(member, weight)));
    }

    @Test
    void unknownWaterRollsItsMemberAtTheTierChance() {
        // Green algae, weight 16 of 72 + 18 empty: chance 1111 x 16 = 17776; LuV T2: t = 6, P = 1024, log2(8) = 3;
        // the sum over r <= 53328 of (long) (16 x 1024 x 17776 x r / 1e8), each scaled, over 100000
        final Web.Recipe algae = aquatic("unknowwater", 5000, "miscutils:item.greenalgaebiomass", 16);
        final Map<String, String> tierTwo = settings(TstVisualEcoSphere.STRUCTURE, T2);
        final NodeMath.Result r = run(algae, ECO, "LuV", 1, tierTwo);
        assertRun(r, 100, 1024, 30720);
        assertEquals(5000.0 * 1024, inputPerRun(r, 0), EPS);
        assertEquals(38307.0434952593, outputPerRun(r, 0), 1e-6);
        tierTwo.put(TstVisualEcoSphere.OUTPUT, "1");
        assertEquals(57460.69859265458, outputPerRun(run(algae, ECO, "LuV", 1, tierTwo), 0), 1e-6);
        // An Offspring focuses only distilled water's pool
        tierTwo.put(TstVisualEcoSphere.OUTPUT, "0");
        tierTwo.put(TstVisualEcoSphere.FOCUS, "offspring");
        assertEquals(38307.0434952593, outputPerRun(run(algae, ECO, "LuV", 1, tierTwo), 0), 1e-6);
    }

    @Test
    void distilledWaterFocusMultipliesTheTargetAndDividesTheRest() {
        // A raw fish, weight 4 of 156: chance 641 x 4 = 2564
        final Web.Recipe fish = aquatic("ic2distilledwater", 10000, "minecraft:fish", 4);
        // HV T1: t = 3, P = 40, log2(5)
        final NodeMath.Result hv = run(fish, ECO, "HV", 1, Map.of());
        assertRun(hv, 100, 40, 480);
        assertEquals(10000.0 * 40, inputPerRun(hv, 0), EPS);
        assertEquals(0.6139337491575507, outputPerRun(hv, 0), 1e-9);
        final Map<String, String> tierTwo = settings(TstVisualEcoSphere.STRUCTURE, T2);
        assertEquals(28.668217696507227, outputPerRun(run(fish, ECO, "LuV", 1, tierTwo), 0), 1e-9);
        // Four fish in two slots of 4 (one Capacity upgrade): chance x 4; four of another member: 2564 / 4 = 641
        tierTwo.put(TstVisualEcoSphere.CAPACITY, "1");
        tierTwo.put(TstVisualEcoSphere.FOCUS_ITEMS, "4");
        tierTwo.put(TstVisualEcoSphere.FOCUS, "this-item");
        assertEquals(1839.0451652014924, outputPerRun(run(fish, ECO, "LuV", 1, tierTwo), 0), 1e-6);
        tierTwo.put(TstVisualEcoSphere.FOCUS, "another-item");
        assertEquals(0.4304008928742572, outputPerRun(run(fish, ECO, "LuV", 1, tierTwo), 0), 1e-9);
        // Without the upgrade a slot holds one target
        tierTwo.put(TstVisualEcoSphere.CAPACITY, "0");
        assertGate(run(fish, ECO, "LuV", 1, tierTwo), TstVisualEcoSphere.INPUT_GATE);
        // BoP's lily meta 3 is in the pool twice: two rolls of chance 641
        final Web.Recipe lily = aquatic("ic2distilledwater", 10000, "biomesoplenty:lilybop@3", 1);
        assertEquals(
            0.18707385762385761,
            outputPerRun(run(lily, ECO, "LuV", 1, settings(TstVisualEcoSphere.STRUCTURE, T2)), 0),
            1e-9);
    }

    // endregion

    // region Artificial Greenhouse

    private static Web.Recipe crop(final int fertilizer, final double amount, final double chance) {
        return recipe(
            GREENHOUSE,
            0,
            100,
            List.of(fluid("enrichedfertilizer", fertilizer)),
            List.of(item("cropsnh:drop", amount, chance)));
    }

    @Test
    void hybridSeedsGrowByCropsNhsChain() {
        // 13 L is ceil(2 x 1 x ln 600): a tier 1 crop of 600 growth points. Analyzed 1/1/1: rate 7 x 1190 / 100 = 83,
        // ceil(600 / 83) = 8 growth ticks, (1 + 0.02) x 1 x 0.95f x 1.03 x 0.390625 / 8 = 0.0487339 a cycle;
        // HV T1 (P = 40), one seed: ceil(0.0487339 x 40 x 40) = 78, scaled 71.64
        final Web.Recipe tierOne = crop(13, 1, 1.0);
        final NodeMath.Result r = run(tierOne, ECO, "HV", 1, Map.of());
        assertRun(r, 100, 40, 480);
        assertEquals(13.0 * 40, inputPerRun(r, 0), EPS);
        assertEquals(71.64102564102565, outputPerRun(r, 0), EPS);
        // Perfect Genetics: growth and gain 31, rate 37 x 1190 / 100 = 440, 2 growth ticks, and x 1.5 output:
        // ceil((1.32 x 0.95f x 1.03^31 x 0.390625 / 2) x 1600) = 980
        assertEquals(
            1359.249433106576,
            outputPerRun(run(tierOne, ECO, "HV", 1, settings(TstVisualEcoSphere.PERFECT_GENETICS, "yes")), 0),
            EPS);
        // 45 L: tier 3 of 1800; a drop of 2 at 50 %, growth 10 and gain 5: rate 16 x 1170 / 100 = 187, 10 ticks;
        // LuV T2 (P = 1024) on four seeds: 4096 parallels, ceil(0.0399903 x 4096 x 40) = 6553
        final NodeMath.Result three = run(
            crop(45, 2, 0.5),
            ECO,
            "LuV",
            1,
            settings(
                TstVisualEcoSphere.STRUCTURE,
                T2,
                TstVisualEcoSphere.CAPACITY,
                "1",
                TstVisualEcoSphere.SEEDS,
                "4",
                TstVisualEcoSphere.GROWTH,
                "10",
                TstVisualEcoSphere.GAIN,
                "5"));
        assertRun(three, 100, 4096, 30720);
        assertEquals(45.0 * 4096, inputPerRun(three, 0), EPS);
        assertEquals(6061.024975838038, outputPerRun(three, 0), EPS);
        assertGate(run(tierOne, ECO, "HV", 1, settings(TstVisualEcoSphere.SEEDS, "2")), TstVisualEcoSphere.INPUT_GATE);
    }

    @Test
    void plainSeedsAreAQuarterOfAnAnalyzedOneOrTheirSimulatedDrops() {
        // 20 L: no CropsNH seed data. As an alternate seed of a tier 1 crop of 600: 1/1/1 x 0.25, ceil(19.49) = 20
        final Web.Recipe alternate = crop(20, 1, 1.0);
        assertEquals(
            18.0,
            outputPerRun(
                run(
                    alternate,
                    ECO,
                    "HV",
                    1,
                    settings(
                        TstVisualEcoSphere.SEED_KIND,
                        "alternate-seed",
                        TstVisualEcoSphere.GROWTH_DURATION,
                        "600")),
                0),
            EPS);
        // Any other plantable (the default at 20 L): 2 x 60 % a harvest x 0.390625 / ceil(1200 / 83) x 0.25
        // = 0.0078125 a cycle, ceil(0.0078125 x 1600) = 13
        final NodeMath.Result plant = run(crop(20, 2, 0.6), ECO, "HV", 1, Map.of());
        assertRun(plant, 100, 40, 480);
        assertEquals(20.0 * 40, inputPerRun(plant, 0), EPS);
        assertEquals(11.512820512820511, outputPerRun(plant, 0), EPS);
    }

    // endregion

    // region Directed Mob Cloning

    @Test
    void addressZeroTurnsBloodIntoLifeEssenceInFiveLitreSteps() {
        // HV T1: 40 parallels, 4000 L blood, 4000 L Life Essence scaled as 800 steps of 5 L: 5 x 739.5
        final NodeMath.Result r = run(bloodToEssence(), ECO, "HV", 1, Map.of());
        assertRun(r, 100, 40, 480);
        assertEquals(4000, inputPerRun(r, 0), EPS);
        assertEquals(3697.5, outputPerRun(r, 0), EPS);
        // LuV T2 with an Output upgrade: 102400 L, 20480 steps x 1.5
        final NodeMath.Result big = run(
            bloodToEssence(),
            ECO,
            "LuV",
            1,
            settings(TstVisualEcoSphere.STRUCTURE, T2, TstVisualEcoSphere.OUTPUT, "1"));
        assertRun(big, 100, 1024, 30720);
        assertEquals(142077.5, outputPerRun(big, 0), EPS);
    }

    @Test
    void aMobRunsOnTierTwoAndMakesItsFirstDropAtItsMobsInfoChance() {
        // 80 L Life Essence: 20 health, D = max(55, (int) 22.2) = 55, 15125 / 1024 / 55^2 = 5 / 1024
        final Web.Recipe zombie = recipe(
            CLONING,
            0,
            100,
            List.of(fluid("lifeessence", 80)),
            List.of(item("enderio:itemsoulvessel", 1), item("minecraft:rotten_flesh", 1)));
        final Map<String, String> tierTwo = settings(TstVisualEcoSphere.STRUCTURE, T2);
        final NodeMath.Result r = run(zombie, ECO, "LuV", 1, tierTwo);
        assertRun(r, 100, 1024, 30720);
        assertEquals(80.0 * 1024, inputPerRun(r, 0), EPS);
        // The soul vial is display only; a 100 % drop: (long) (1024 x 1 x 5 / 1024) = 5, scaled to [4.25, 5): 4
        assertEquals(0, outputPerRun(r, 0), EPS);
        assertEquals(4.0, outputPerRun(r, 1), EPS);
        // Looting X (the Tier III protocol): + 5.0 to the chance, 30 a run
        tierTwo.put(TstVisualEcoSphere.LOOTING, "10");
        assertEquals(27.22222222222222, outputPerRun(run(zombie, ECO, "LuV", 1, tierTwo), 1), EPS);
        // 50 %: (long) 2.5 = 2, scaled to [1.7, 2): 1
        tierTwo.put(TstVisualEcoSphere.LOOTING, "0");
        tierTwo.put(TstVisualEcoSphere.FIRST_DROP_CHANCE, "50");
        assertEquals(1.0, outputPerRun(run(zombie, ECO, "LuV", 1, tierTwo), 1), EPS);
        assertGate(run(zombie, ECO, "LuV", 1, Map.of()), TstVisualEcoSphere.CLONING_GATE);
    }

    // endregion
}
