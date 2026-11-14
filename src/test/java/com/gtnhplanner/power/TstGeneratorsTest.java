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
import com.gtnhplanner.power.sources.TstGenerators;

/**
 * TwistSpaceTechnology's Universal Generator against TST 0.8.1's TST_UniversalGenerator.checkProcessing: EU/t is the
 * dynamos' voltage x their summed amps, and one run burns 1,000 L for floor(EU/L x 1,500 / (EU/t)) ticks.
 */
class TstGeneratorsTest {

    private static final String UG = TstGenerators.UNIVERSAL_GENERATOR;

    static PowerModel compute(final Map<String, String> settings) {
        final PowerSource source = PowerRegistry.get(UG);
        if (source == null) fail("No power source " + UG);
        return source.compute(settings);
    }

    static double flow(final PowerModel model, final String name) {
        final List<Flow> all = new ArrayList<>(model.inputs());
        all.addAll(model.outputs());
        for (final Flow flow : all) if (flow.name()
            .equals(name)) return flow.perSecond();
        return Double.NaN;
    }

    private static PowerSetting setting(final String settingId) {
        final PowerSource source = PowerRegistry.get(UG);
        assertNotNull(source);
        return source.setting(settingId);
    }

    private static List<String> optionKeys(final String settingId) {
        final PowerSetting.Select select = assertInstanceOf(PowerSetting.Select.class, setting(settingId));
        final List<String> keys = new ArrayList<>();
        for (final PowerSetting.Option option : select.options()) keys.add(option.key());
        return keys;
    }

    // ---- checkProcessing

    @Test
    void runsOneHvDynamoOnBenzeneByDefault() {
        // 360 EU/L x 1,500 = 540,000 EU a run; 540,000 / 512 = 1,054.69 -> 1,054 ticks.
        final PowerModel model = compute(Map.of());
        assertEquals(512, model.euPerTick(), 0);
        assertEquals(List.of(new Flow("Benzene", 20_000.0 / 1054, PowerModel.Unit.L)), model.inputs());
        assertEquals(List.of(), model.outputs());
        assertEquals("Gas mode", model.stat("Structure"));
        assertEquals("1,000 L every 1,054 ticks", model.stat("Burn"));
        // 512 x 1,054 / 1,000 = 539.648 EU/L: the truncated tick costs a little of the 150%.
        assertEquals("539.65", model.stat("EU per L"));
        assertEquals("149.9%", model.stat("Efficiency"));
        assertEquals(List.of(), model.warnings());
    }

    @Test
    void burnsDieselInTheFuelStructureAtAFull150Percent() {
        // 4 A of LV = 128 EU/t; 480 x 1,500 / 128 = 5,625 ticks exactly.
        final PowerModel model = compute(Map.of("fuel", "Diesel", "dynamoTier", "LV", "amps", "4"));
        assertEquals(128, model.euPerTick(), 0);
        assertEquals(20_000.0 / 5625, flow(model, "Diesel"), 1e-12);
        assertEquals("Fuel mode", model.stat("Structure"));
        assertEquals("720", model.stat("EU per L"));
        assertEquals("150%", model.stat("Efficiency"));
    }

    @Test
    void outputIsTheDynamosWhateverTheFuel() {
        // 8 V x 1 A on hydrogen: 30,000 / 8 = 3,750 ticks a run.
        final PowerModel ulv = compute(Map.of("fuel", "Hydrogen", "dynamoTier", "ULV"));
        assertEquals(8, ulv.euPerTick(), 0);
        assertEquals(20_000.0 / 3750, flow(ulv, "Hydrogen"), 1e-12);
        // Eight 4 A HV hatches: 16,384 EU/t; natural gas lasts 30,000 / 16,384 -> 1 tick, 1,000 L every tick.
        final PowerModel full = compute(Map.of("fuel", "Natural Gas", "amps", "32"));
        assertEquals(16_384, full.euPerTick(), 0);
        assertEquals(20_000, flow(full, "Natural Gas"), 0);
        assertEquals("16.38", full.stat("EU per L"));
        assertEquals("81.92%", full.stat("Efficiency"));
    }

    @Test
    void capsTheAmpsAtEightFourAmpHatches() {
        final PowerModel model = compute(Map.of("amps", "40"));
        assertEquals(16_384, model.euPerTick(), 0);
        // 540,000 / 16,384 = 32.96 -> 32 ticks.
        assertEquals(20_000.0 / 32, flow(model, "Benzene"), 1e-12);
        final PowerSetting.Number amps = assertInstanceOf(PowerSetting.Number.class, setting("amps"));
        assertEquals(1, amps.min(), 0);
        assertEquals(32, amps.max(), 0);
    }

    @Test
    void makesNothingFromAFuelWorthUnderATick() {
        // Fish oil: 2 x 1,500 = 3,000 EU a run. 23 A of MV (2,944 EU/t) still gets one tick...
        final PowerModel oneTick = compute(Map.of("fuel", "Fish Oil", "dynamoTier", "MV", "amps", "23"));
        assertEquals(2944, oneTick.euPerTick(), 0);
        assertEquals(20_000, flow(oneTick, "Fish Oil"), 0);
        // ...24 A (3,072 EU/t) gets (int) 0.98 = 0: the run ends before it starts.
        assertEquals(0, TstGenerators.runTicks(2, 3072));
        final PowerModel none = compute(Map.of("fuel", "Fish Oil", "dynamoTier", "MV", "amps", "24"));
        assertEquals(0, none.euPerTick(), 0);
        assertEquals(List.of(), none.inputs());
        assertEquals(
            List.of("Fish Oil lasts under a tick at 3,072 EU/t: each 1,000 L burns for no EU."),
            none.warnings());
    }

    @Test
    void runTicksTruncateAsTheGameDoes() {
        assertEquals(1054, TstGenerators.runTicks(360, 512));
        assertEquals(5625, TstGenerators.runTicks(480, 128));
        assertEquals(1, TstGenerators.runTicks(20, 16_384));
    }

    // ---- fuels and settings

    @Test
    void offersBothMapsFuelsWithEtherOnce() {
        final List<String> fuels = optionKeys("fuel");
        final PowerData data = PowerData.get();
        // Gas turbine fuels, then the diesel fuels the gas map lacks.
        assertEquals(data.gasFuels.size() + data.combustionFuels.size() - 1, fuels.size());
        assertEquals(data.gasFuels.get(0).name, fuels.get(0));
        assertTrue(fuels.contains("Ether"));
        assertEquals(fuels.indexOf("Ether"), fuels.lastIndexOf("Ether"));
        assertTrue(fuels.contains("Benzene"));
        assertTrue(fuels.contains("High Octane Gasoline"));
        final PowerModel ether = compute(Map.of("fuel", "Ether"));
        assertEquals("Gas or fuel mode", ether.stat("Structure"));
    }

    @Test
    void takesDynamosFromUlvToHv() {
        assertEquals(List.of("ULV", "LV", "MV", "HV"), optionKeys("dynamoTier"));
    }

    @Test
    void sitsWithTheEnginesAtLv() {
        final PowerSource source = PowerRegistry.get(UG);
        assertNotNull(source);
        assertEquals("Universal Generator", source.name());
        assertEquals(PowerGroup.ENGINES, source.group());
        assertEquals("LV", source.unlock());
        final List<String> engines = new ArrayList<>();
        for (final PowerSource s : PowerRegistry.inGroup(PowerGroup.ENGINES)) engines.add(s.id());
        assertEquals(engines.indexOf("solid-oxide-fuel-cell-2") + 1, engines.indexOf(UG));
    }

    @Test
    void theSearchFindsItByADieselFuelWithTheFuelDialedIn() {
        PowerSearch.Hit hit = null;
        for (final PowerSearch.Hit h : PowerSearch.search("high octane")) {
            final PowerSource source = h.source();
            if (UG.equals(source.id())) hit = h;
        }
        assertNotNull(hit);
        assertEquals(Map.of("fuel", "High Octane Gasoline"), hit.settings());
    }
}
