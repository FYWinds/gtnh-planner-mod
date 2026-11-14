package com.gtnhplanner.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.sources.TstSteamGenerators;

/**
 * TwistSpaceTechnology's Basic Steam Generator, Large Solar Boiler and Lightning Spire against TST 0.8.1's
 * TST_SteamBasicGenerator, TST_LargeSolarBoiler and GTCM_LightningSpire (checkProcessing, onPostTick).
 */
class TstSteamGeneratorsTest {

    private static final String BSG = TstSteamGenerators.BASIC_STEAM_GENERATOR;
    private static final String LSB = TstSteamGenerators.LARGE_SOLAR_BOILER;
    private static final String SPIRE = TstSteamGenerators.LIGHTNING_SPIRE;
    private static final PowerModel.Unit L = PowerModel.Unit.L;

    private static PowerSource source(final String sourceId) {
        final PowerSource source = PowerRegistry.get(sourceId);
        if (source == null) fail("No power source " + sourceId);
        return source;
    }

    private static PowerModel compute(final String sourceId, final Map<String, String> settings) {
        return source(sourceId).compute(settings);
    }

    private static double flow(final PowerModel model, final String name) {
        final List<Flow> all = new ArrayList<>(model.inputs());
        all.addAll(model.outputs());
        for (final Flow flow : all) if (flow.name()
            .equals(name)) return flow.perSecond();
        return Double.NaN;
    }

    private static List<String> optionKeys(final String sourceId, final String settingId) {
        final PowerSetting.Select select = assertInstanceOf(
            PowerSetting.Select.class,
            source(sourceId).setting(settingId));
        final List<String> keys = new ArrayList<>();
        for (final PowerSetting.Option option : select.options()) keys.add(option.key());
        return keys;
    }

    // ---- Basic Steam Generator

    @Test
    void runsOneHvDynamoOnFourteenThousandFourHundredLitresByDefault() {
        // 512 EU/t x 225 / 8 = 14,400 L of steam a 20-tick run.
        final PowerModel model = compute(BSG, Map.of());
        assertEquals(512, model.euPerTick(), 0);
        assertEquals(List.of(new Flow("Steam", 14_400, L)), model.inputs());
        assertEquals(List.of(), model.outputs());
        assertEquals("14,400 L every 20 ticks", model.stat("Burn"));
        // 512 x 20 / 14,400 = 0.711 EU a litre, 142.2% of steam's 0.5.
        assertEquals("0.71", model.stat("EU per L"));
        assertEquals("142.22%", model.stat("Efficiency"));
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void takesOneOneAmpMvHatchWhateverTheAmps() {
        final PowerModel model = compute(BSG, Map.of("dynamoTier", "MV", "amps", "4"));
        assertEquals(128, model.euPerTick(), 0);
        assertEquals(3_600, flow(model, "Steam"), 0);
    }

    @Test
    void sumsUpToEightFourAmpLvHatches() {
        final PowerModel one = compute(BSG, Map.of("dynamoTier", "LV"));
        assertEquals(32, one.euPerTick(), 0);
        assertEquals(900, flow(one, "Steam"), 0);
        final PowerModel full = compute(BSG, Map.of("dynamoTier", "LV", "amps", "32"));
        assertEquals(1_024, full.euPerTick(), 0);
        assertEquals(28_800, flow(full, "Steam"), 0);
        assertEquals("28,800 L every 20 ticks", full.stat("Burn"));
        final PowerModel over = compute(BSG, Map.of("dynamoTier", "LV", "amps", "40"));
        assertEquals(1_024, over.euPerTick(), 0);
        final PowerSetting.Number amps = assertInstanceOf(PowerSetting.Number.class, source(BSG).setting("amps"));
        assertEquals(1, amps.min(), 0);
        assertEquals(32, amps.max(), 0);
        assertEquals(List.of("LV", "MV", "HV"), optionKeys(BSG, "dynamoTier"));
    }

    @Test
    void steamPerSecondDividesInLongs() {
        assertEquals(14_400, TstSteamGenerators.steamPerSecond(512));
        assertEquals(900, TstSteamGenerators.steamPerSecond(32));
        // 33 x 225 / 8 = 928.125 and 225 / 8 = 28.125: the game's long division drops the fraction.
        assertEquals(928, TstSteamGenerators.steamPerSecond(33));
        assertEquals(28, TstSteamGenerators.steamPerSecond(1));
    }

    // ---- Large Solar Boiler

    @Test
    void theBronzeBoilerBoilsThirtyLitresARunAtFullHeat() {
        // 4,800 / 160 = 30 L of water a run, 30 x 160 = 4,800 L/s of steam.
        final PowerModel model = compute(LSB, Map.of());
        assertEquals(0, model.euPerTick(), 0);
        assertEquals(List.of(new Flow("Water", 30, L)), model.inputs());
        assertEquals(List.of(new Flow("Steam", 4_800, L)), model.outputs());
        assertEquals("240 L/t", model.stat("Steam"));
        // The first step ends run 86,832 (the first multiple of 1,296 past 86,400), so 86,833 runs are clean; the
        // hundredth, 99 x 1,296 runs later, leaves 30 / 3 = 10 L a run from run 215,137.
        assertEquals(
            List.of(
                "Regular water calcifies this boiler: full 4,800 L/s for 24.12 hours of run time, then down to "
                    + "1,600 L/s by 59.76 hours. Distilled water does not."),
            model.warnings());
    }

    @Test
    void theSteelBoilerOnDistilledWaterNeverCalcifies() {
        final PowerModel model = compute(
            LSB,
            Map.of("model", "steel", "waterKind", "Distilled Water", "calcification", "100"));
        assertEquals(List.of(new Flow("Distilled Water", 90, L)), model.inputs());
        assertEquals(List.of(new Flow("Steam", 14_400, L)), model.outputs());
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void calcificationCountsInTheGamesOwnHundredthSteps() {
        assertEquals(1, TstSteamGenerators.calcificationAfter(100), 0);
        assertEquals(0.5000000000000002, TstSteamGenerators.calcificationAfter(50), 0);
        // Full: 90 / 3 = 30 L a run.
        assertEquals(4_800, flow(compute(LSB, Map.of("model", "steel", "calcification", "100")), "Steam"), 0);
        // Fifty steps sum to a hair over 0.5: 90 / 2.0000000000000004 = 44.99... -> 44 L, not 45.
        assertEquals(7_040, flow(compute(LSB, Map.of("model", "steel", "calcification", "50")), "Steam"), 0);
        assertEquals(2_240, flow(compute(LSB, Map.of("calcification", "50")), "Steam"), 0);
        // 40 steps: 90 / 1.8000000000000005 -> 49 L; 75 steps: 90 / 2.500000000000001 -> 35 L.
        assertEquals(7_840, flow(compute(LSB, Map.of("model", "steel", "calcification", "40")), "Steam"), 0);
        assertEquals(5_600, flow(compute(LSB, Map.of("model", "steel", "calcification", "75")), "Steam"), 0);
    }

    @Test
    void heatScalesTheWaterAndTruncates() {
        assertEquals(1, TstSteamGenerators.heatAfter(1000), 0);
        assertEquals(1, TstSteamGenerators.heatAfter(1200), 0);
        assertEquals(0.5000000000000003, TstSteamGenerators.heatAfter(500), 0);
        // 90 x 0.945 = 85.05 -> 85 L a run.
        assertEquals(13_600, flow(compute(LSB, Map.of("model", "steel", "heat", "94.5")), "Steam"), 0);
        // 90 x 0.5 = 45; with fifty calcification steps 45 / 2 = 22.5 -> 22.
        final PowerModel half = compute(LSB, Map.of("model", "steel", "heat", "50"));
        assertEquals(7_200, flow(half, "Steam"), 0);
        assertEquals(
            List.of(
                "Regular water calcifies this boiler: full 7,200 L/s for 24.12 hours of run time, then down to "
                    + "2,400 L/s by 59.76 hours. Distilled water does not."),
            half.warnings());
        assertEquals(
            3_520,
            flow(compute(LSB, Map.of("model", "steel", "heat", "50", "calcification", "50")), "Steam"),
            0);
        // 90 x 0.012 = 1.08 -> 1 L; 90 x 0.011 = 0.99 -> nothing.
        assertEquals(160, flow(compute(LSB, Map.of("model", "steel", "heat", "1.2")), "Steam"), 0);
        final PowerModel cold = compute(LSB, Map.of("model", "steel", "heat", "1.1"));
        assertEquals(List.of(), cold.inputs());
        assertEquals(List.of(), cold.outputs());
        assertEquals(List.of("Too cold: under 1 L of water a run, so no steam."), cold.warnings());
        // Bronze: 30 x 0.034 = 1.02 -> 1 L; 30 x 0.033 = 0.99 -> nothing.
        assertEquals(160, flow(compute(LSB, Map.of("heat", "3.4")), "Steam"), 0);
        assertEquals(List.of(), compute(LSB, Map.of("heat", "3.3")).outputs());
    }

    // ---- Lightning Spire

    @Test
    void fiveHundredTwelveRodsMakeFiftySixMillionEuT() {
        // 512 x 28,000,000 EU a strike every 256 ticks; a UV 256 A dynamo carries 134,217,728 EU/t.
        final PowerModel model = compute(SPIRE, Map.of());
        assertEquals(56_000_000, model.euPerTick(), 0);
        // 128 L of cryotheum and 512 x 72 = 36,864 L of molten iron a strike: x 20 / 256 a second.
        assertEquals(List.of(new Flow("Cryotheum", 10, L), new Flow("Molten Iron", 2_880, L)), model.inputs());
        assertEquals(List.of(), model.outputs());
        assertEquals("14.34G EU", model.stat("Per strike"));
        assertEquals("12.8 s", model.stat("Strike every"));
        assertEquals("128 L", model.stat("Cryotheum per strike"));
        assertEquals("36,864 L", model.stat("Molten Iron per strike"));
        assertEquals("143.36G EU", model.stat("Store"));
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void oneRodMakesItsShareOfAStrike() {
        // 28,000,000 / 256 = 109,375 EU/t; 72 x 20 / 256 = 5.625 L/s of molten iron.
        final PowerModel model = compute(SPIRE, Map.of("rods", "1"));
        assertEquals(109_375, model.euPerTick(), 0);
        assertEquals(10, flow(model, "Cryotheum"), 0);
        assertEquals(5.625, flow(model, "Molten Iron"), 0);
    }

    @Test
    void theDynamosCapTheOutputAndTheRestIsLost() {
        // Eight rods make 875,000 EU/t: 109,375 A of ULV carries it all, one amp less does not.
        final PowerModel carried = compute(SPIRE, Map.of("rods", "8", "dynamoTier", "ULV", "amps", "109375"));
        assertEquals(875_000, carried.euPerTick(), 0);
        assertEquals(List.of(), carried.warnings());
        final PowerModel under = compute(SPIRE, Map.of("rods", "8", "dynamoTier", "ULV", "amps", "109374"));
        assertEquals(874_992, under.euPerTick(), 0);
        assertEquals(
            List.of("The dynamos carry 874,992 EU/t of the 875,000 EU/t the strikes make; the rest is lost."),
            under.warnings());
        // Every strike still burns its fluids.
        assertEquals(10, flow(under, "Cryotheum"), 0);
        assertEquals(45, flow(under, "Molten Iron"), 0);
    }

    @Test
    void holdsAtMostFiveHundredTwelveRods() {
        assertEquals(56_000_000, compute(SPIRE, Map.of("rods", "600")).euPerTick(), 0);
        final PowerSetting.Number rods = assertInstanceOf(PowerSetting.Number.class, source(SPIRE).setting("rods"));
        assertEquals(1, rods.min(), 0);
        assertEquals(512, rods.max(), 0);
        assertEquals(
            List.of("ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV"),
            optionKeys(SPIRE, "dynamoTier"));
        // No amps reads as one: a single IV amp carries 8,192 of the 56,000,000 EU/t.
        assertEquals(8_192, compute(SPIRE, Map.of("dynamoTier", "IV", "amps", "0")).euPerTick(), 0);
    }
}
