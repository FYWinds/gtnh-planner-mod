package com.gtnhplanner.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.sources.TstDysonSphere;

/**
 * TwistSpaceTechnology 0.8.1's Dyson Sphere Program generators, with the pack's TwistSpaceTechnology.cfg. The Ray
 * Receiving Station makes {@code (long) (planet x stellar coefficient x lens x power points)} EU/t, its points the
 * sphere's {@code (long) (524,288 x sails x (nodes + 1)^0.8)} up to 1,024 x Integer.MAX_VALUE; the Artificial Star adds
 * {@code (long) (fuel units x multiplier x bonus) x Integer.MAX_VALUE} EU every 128-tick run.
 */
class TstDysonSphereTest {

    private static final String RECEIVER = TstDysonSphere.RECEIVER;
    private static final String STAR = TstDysonSphere.ARTIFICIAL_STAR;
    private static final long INT_MAX = Integer.MAX_VALUE;
    /** Config.maxPowerPointPerReceiver: 1,024 x Integer.MAX_VALUE. */
    private static final long CAP = 2_199_023_254_528L;
    /** A sphere far past one receiver's cap: 10^6 sails, 1,000 nodes. */
    private static final Map<String, String> BIG = Map.of("sails", "1000000", "nodes", "1000");
    /** Every field at its Gallifreyan generator, tier 10. */
    private static final Map<String, String> GALLIFREYAN = Map
        .of("dimensionField", "10", "timeField", "10", "stabilisationField", "10", "rods", "0");

    private static PowerModel compute(final String sourceId, final Map<String, String> settings) {
        final PowerSource source = PowerRegistry.get(sourceId);
        if (source == null) fail("No power source " + sourceId);
        return source.compute(settings);
    }

    private static PowerModel receiver(final Map<String, String> settings) {
        return compute(RECEIVER, settings);
    }

    private static PowerModel star(final Map<String, String> settings) {
        return compute(STAR, settings);
    }

    private static double receiverEu(final Map<String, String> settings) {
        final PowerModel model = receiver(settings);
        return model.euPerTick();
    }

    private static double starEu(final Map<String, String> settings) {
        final PowerModel model = star(settings);
        return model.euPerTick();
    }

    private static List<String> receiverWarnings(final Map<String, String> settings) {
        final PowerModel model = receiver(settings);
        return model.warnings();
    }

    private static Map<String, String> with(final Map<String, String> base, final String... pairs) {
        final Map<String, String> out = new HashMap<>(base);
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return out;
    }

    private static double flow(final PowerModel model, final String name) {
        final List<Flow> all = new ArrayList<>(model.inputs());
        all.addAll(model.outputs());
        for (final Flow flow : all) if (name.equals(flow.name())) return flow.perSecond();
        return Double.NaN;
    }

    // ---- Ray Receiving Station: the sphere

    @Test
    void receivesAFirstSphereOnTheOverworldByDefault() {
        // 2,048 sails, no nodes: 524,288 x 2,048 x 1^0.8 = 1,073,741,824 points, all of them under the cap.
        final PowerModel model = receiver(Map.of());
        assertEquals(1_073_741_824, model.euPerTick(), 0);
        assertEquals(List.of(), model.inputs());
        assertEquals(List.of(), model.outputs());
        assertEquals("Solar System", model.stat("Star system"));
        assertEquals("1", model.stat("Stellar coefficient"));
        assertEquals("1", model.stat("Planet coefficient"));
        assertEquals("1.07G", model.stat("Sphere power points"));
        assertEquals("1.07G", model.stat("Power points used"));
        assertEquals("2,048", model.stat("Sails held safely"));
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void sphereMaxFollowsTheDataCell() {
        assertEquals(1_073_741_824L, TstDysonSphere.maxPowerPoint(2048, 0));
        assertEquals(0, TstDysonSphere.maxPowerPoint(0, 1000));
        // 524,288 x 10^6 x 1,001^0.8 (251.39...) = 131,800,536,961,998.4.
        assertEquals(131_800_536_961_998L, TstDysonSphere.maxPowerPoint(1_000_000, 1000));
    }

    @Test
    void switchesToBigIntegerWhereTheLongWouldRunOut() {
        // shouldUseBigInteger: Long.MAX_VALUE / 524,288 / 10^9 - 1 = 17,591 against (nodes + 1)^0.8.
        // 202,587^0.8 = 17,590.95: still the double formula, just under Long.MAX_VALUE.
        assertFalse(TstDysonSphere.bigIntegerMode(1_000_000_000, 202_586));
        assertEquals(9.2227232381011e18, TstDysonSphere.maxPowerPoint(1_000_000_000, 202_586), 1e6);
        // 202,588^0.8 = 17,591.02: BigInteger, the node factor rounded up to 17,592.
        assertTrue(TstDysonSphere.bigIntegerMode(1_000_000_000, 202_587));
        assertEquals(9_223_274_496_000_000_000L, TstDysonSphere.maxPowerPoint(1_000_000_000, 202_587));
        // Past a long it reads as Long.MAX_VALUE.
        assertEquals(Long.MAX_VALUE, TstDysonSphere.maxPowerPoint(10_000_000_000L, 202_587));
        // No nodes, never BigInteger.
        assertFalse(TstDysonSphere.bigIntegerMode(1_000_000_000_000L, 0));
    }

    @Test
    void warnsOfSailsPastWhatTheNodesHold() {
        // solarSailCanHoldDefault 2,048 + 256 a node.
        assertEquals(List.of(), receiverWarnings(Map.of("sails", "2048")));
        assertEquals(List.of("Sails past what the nodes hold may decay."), receiverWarnings(Map.of("sails", "2049")));
        final PowerModel noded = receiver(Map.of("sails", "2304", "nodes", "1"));
        assertEquals("2,304", noded.stat("Sails held safely"));
        assertEquals(List.of(), noded.warnings());
    }

    // ---- Ray Receiving Station: planets

    @Test
    void multipliesByThePlanetAndItsStar() {
        // Moon 1.3 in the Solar System: (long) 1,395,864,371.2.
        assertEquals(1_395_864_371L, receiverEu(Map.of("planet", "Moon")), 0);
        // Vega B 2.6 x Vega 2.98 = 7.748: (long) 8,319,351,652.35.
        final PowerModel vega = receiver(Map.of("planet", "VegaB"));
        assertEquals(8_319_351_652L, vega.euPerTick(), 0);
        assertEquals("Vega", vega.stat("Star system"));
        assertEquals("2.98", vega.stat("Stellar coefficient"));
        assertEquals("2.6", vega.stat("Planet coefficient"));
        // Horus 2.9 x Amun-Ra 0.9 = 2.61: (long) 2,802,466,160.64.
        assertEquals(2_802_466_160L, receiverEu(Map.of("planet", "Horus")), 0);
        // A personal dimension (180 and up) is the Overworld.
        assertEquals(1_073_741_824, receiverEu(Map.of("planet", "Personal")), 0);
    }

    @Test
    void anotherDimensionReceivesNothing() {
        final PowerModel other = receiver(Map.of("planet", "Other"));
        assertEquals(0, other.euPerTick(), 0);
        assertEquals("None", other.stat("Star system"));
        assertEquals(
            List.of("TST gives this dimension no coefficient: the station receives nothing."),
            other.warnings());
    }

    @Test
    void aGravitationalLensQuadruplesItForOneLensEveryTenMinutes() {
        // 7.748 x 4 = 30.992 x 1,073,741,824 = 33,277,406,609.41.
        final PowerModel model = receiver(Map.of("planet", "VegaB", "lens", "1"));
        assertEquals(33_277_406_609L, model.euPerTick(), 0);
        assertEquals(List.of(new Flow("Gravitational Lens", 1.0 / 600, PowerModel.Unit.ITEM)), model.inputs());
    }

    // ---- Ray Receiving Station: the request

    @Test
    void oneReceiverTakesAtMostItsCap() {
        final PowerModel model = receiver(BIG);
        assertEquals(CAP, model.euPerTick(), 0);
        assertEquals("131,800.54G", model.stat("Sphere power points"));
        assertEquals("2,199.02G", model.stat("Power points used"));
        // 2,048 + 256,000 sails held; a million is past it.
        assertEquals("258,048", model.stat("Sails held safely"));
        assertEquals(List.of("Sails past what the nodes hold may decay."), model.warnings());
    }

    @Test
    void astralArrayFabricatorsRaiseTheCapByTheirSquareTimesTwo() {
        // One: 2 x the cap.
        final PowerModel one = receiver(with(BIG, "controller", "astral"));
        assertEquals(2 * CAP, one.euPerTick(), 0);
        assertEquals("2", one.stat("Overload multiplier"));
        // Three: 18 x the cap.
        assertEquals(18 * CAP, receiverEu(with(BIG, "controller", "astral", "astral", "3")), 0);
        // Sixty-four: 8,192 x the cap is past the sphere, which gives all it has.
        assertEquals(131_800_536_961_998L, receiverEu(with(BIG, "controller", "astral", "astral", "64")), 0);
    }

    @Test
    void aCircuitAsksForItsShareOfTheWholeSphere() {
        // Circuit 1 in a stack of 4: (long) (0.25 x 1,073,741,824).
        assertEquals(268_435_456, receiverEu(Map.of("controller", "circuit", "circuitStack", "4")), 0);
        // 5 / 2 is capped at the whole sphere.
        assertEquals(
            1_073_741_824,
            receiverEu(Map.of("controller", "circuit", "circuit", "5", "circuitStack", "2")),
            0);
        // A sixty-fourth of the big sphere, 2,059,383,390,031.2, is under the cap.
        assertEquals(2_059_383_390_031L, receiverEu(with(BIG, "controller", "circuit", "circuitStack", "64")), 0);
        // Circuit 0 asks for nothing.
        final PowerModel none = receiver(Map.of("controller", "circuit", "circuit", "0"));
        assertEquals(0, none.euPerTick(), 0);
        assertEquals(List.of("This receiver takes no power points."), none.warnings());
    }

    @Test
    void takesWhatOtherReceiversLeave() {
        // A quarter held elsewhere: 1,073,741,824 - 268,435,456.
        assertEquals(805_306_368, receiverEu(Map.of("others", "25")), 0);
        final PowerModel all = receiver(Map.of("others", "100"));
        assertEquals(0, all.euPerTick(), 0);
        assertEquals(List.of("This receiver takes no power points."), all.warnings());
        final PowerModel empty = receiver(Map.of("sails", "0"));
        assertEquals(0, empty.euPerTick(), 0);
        assertEquals(List.of("The sphere has no power points: it needs solar sails."), empty.warnings());
    }

    // ---- Ray Receiving Station: photon collection

    @Test
    void collectsCriticalPhotonsInsteadOfEu() {
        // 1,073,741,824 EU/t banked as storageEU: 128 x that / 2,147,483,647 photons a run, 20/128 runs a second.
        final PowerModel model = receiver(Map.of("mode", "photons"));
        assertEquals(0, model.euPerTick(), 0);
        assertEquals(20.0 * 1_073_741_824 / INT_MAX, flow(model, "Critical Photon"), 1e-12);
        // The cap is exactly 1,024 x Integer.MAX_VALUE a tick: 1,024 photons a tick, 20,480 a second.
        assertEquals(20_480, TstDysonSphere.criticalPhotonsPerSecond(CAP), 0);
        assertEquals(20_480, flow(receiver(with(BIG, "mode", "photons")), "Critical Photon"), 0);
    }

    // ---- Artificial Star

    @Test
    void burnsOneAntimatterFuelRodARunByDefault() {
        // 1,024 units x 0.62965 (every base block) x 150% = 967.14 -> 967 x Integer.MAX_VALUE a 128-tick run.
        final PowerModel model = star(Map.of());
        assertEquals(967 * INT_MAX / 128.0, model.euPerTick(), 0);
        assertEquals(16_223_567_864.4453125, model.euPerTick(), 0);
        assertEquals(List.of(new Flow("Antimatter Fuel Rod", 0.15625, PowerModel.Unit.ITEM)), model.inputs());
        // Recovery chance 1 x 1 x 1 / 1,000.
        assertEquals(0.15625 / 1000, flow(model, "Stellar Construction Frame Material"), 1e-18);
        assertEquals("2,076.62G", model.stat("EU per run"));
        assertEquals("0.63", model.stat("Output multiplier"));
        assertEquals("150%", model.stat("Continuous-run bonus"));
        assertEquals("6.4 s", model.stat("Run time"));
        assertEquals("0.1%", model.stat("Recovery chance"));
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void theContinuousRunBonusClimbsFromNothingToFiftyPercent() {
        // The first run: 644.76 -> 644.
        assertEquals(644 * INT_MAX / 128.0, starEu(Map.of("bonus", "0")), 0);
        // rewardContinuous stops at 50: 150% is the most.
        assertEquals(starEu(Map.of()), starEu(Map.of("bonus", "60")), 0);
        // (int) (20 x 6.4): 6.4 s a run.
        assertEquals(128, TstDysonSphere.STAR_RUN_TICKS);
    }

    @Test
    void theFieldTiersSetTheMultiplierAndTheRecovery() {
        assertEquals(1 / 1.588186, TstDysonSphere.outputMultiplier(1, 1, 1), 1e-15);
        // 100^0.25 x 1.588186^8 = 128.00028.
        assertEquals(128.000276225929, TstDysonSphere.outputMultiplier(10, 10, 10), 1e-9);
        // Every Gallifreyan generator, 64 Antimatter (192 units): 36,864.08 -> 36,864.
        final PowerModel full = star(with(GALLIFREYAN, "antimatter", "64"));
        assertEquals(36_864 * INT_MAX / 128.0, full.euPerTick(), 0);
        assertEquals(64 * 0.15625, flow(full, "Antimatter"), 0);
        assertEquals("128", full.stat("Output multiplier"));
        assertEquals("100%", full.stat("Recovery chance"));
        // Antimatter gives no frame material back.
        assertEquals(List.of(), full.outputs());
        // Superb fields (tier 5 each), 16 rods, bonus 37: 16,384 x 8.95755 x 1.37 = 201,061.8 -> 201,061; recovery
        // 125 / 1,000 of the 16 rods.
        final PowerModel mid = star(
            Map.of("rods", "16", "dimensionField", "5", "timeField", "5", "stabilisationField", "5", "bonus", "37"));
        assertEquals(3_373_243_824_605.2109375, mid.euPerTick(), 0);
        assertEquals("12.5%", mid.stat("Recovery chance"));
        assertEquals(0.3125, flow(mid, "Stellar Construction Frame Material"), 1e-15);
    }

    @Test
    void sumsTheFuelsBeforeCuttingToAWholeUnit() {
        // Apart, 1 Antimatter (2.83 -> 2) and 2 rods (1,934.28 -> 1,934) make 1,936; together 1,937.12 -> 1,937.
        assertEquals(2 * INT_MAX / 128.0, starEu(Map.of("antimatter", "1", "rods", "0")), 0);
        assertEquals(1934 * INT_MAX / 128.0, starEu(Map.of("rods", "2")), 0);
        assertEquals(1937 * INT_MAX / 128.0, starEu(Map.of("antimatter", "1", "rods", "2")), 0);
    }

    @Test
    void keepsEuPastALongWhole() {
        // 1,000 Strange Annihilation Fuel Rods at every Gallifreyan generator: 32,768,000 units x 128.00028 x 1.5 =
        // 6,291,469,577 x Integer.MAX_VALUE, past Long.MAX_VALUE.
        final BigInteger run = TstDysonSphere.euPerRun(32_768_000, TstDysonSphere.outputMultiplier(10, 10, 10), 50);
        assertEquals(new BigInteger("13510828032205507319"), run);
        assertTrue(run.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        final PowerModel model = star(with(GALLIFREYAN, "strangeRods", "1000"));
        assertEquals(run.doubleValue() / 128, model.euPerTick(), 0);
        assertEquals(156.25, flow(model, "Strange Annihilation Fuel Rod"), 0);
        assertEquals(156.25, flow(model, "Stellar Construction Frame Material"), 0);
    }

    @Test
    void doesNotRunWithoutFuel() {
        final PowerModel model = star(Map.of("rods", "0"));
        assertEquals(0, model.euPerTick(), 0);
        assertEquals(List.of(), model.inputs());
        assertEquals(List.of("No fuel: the star does not run."), model.warnings());
    }

    // ---- the picker

    @Test
    void bothSitWithTheEndgameAtTheirControllersTiers() {
        final PowerSource receiver = PowerRegistry.get(RECEIVER);
        final PowerSource star = PowerRegistry.get(STAR);
        assertNotNull(receiver);
        assertNotNull(star);
        assertEquals(PowerGroup.ENDGAME, receiver.group());
        assertEquals(PowerGroup.ENDGAME, star.group());
        // DSPRecipePool: assembly line recipes at RECIPE_UIV and RECIPE_UXV.
        assertEquals("UIV", receiver.unlock());
        assertEquals("UXV", star.unlock());
    }
}
