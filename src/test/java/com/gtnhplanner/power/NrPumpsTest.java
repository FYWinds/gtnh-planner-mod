package com.gtnhplanner.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.power.PowerModel.Flow;
import com.gtnhplanner.power.sources.NrData;
import com.gtnhplanner.power.sources.NrPumps;
import com.gtnhplanner.power.sources.NrResources;
import com.gtnhplanner.power.sources.NrSources;

/**
 * The Planetary Gas Siphon against gtnhintergalactic's MTEPlanetaryGasSiphon.checkProcessing and the Space Pumping
 * Modules against TileEntityModulePump.checkProcessing_EM (GregTech 5.09.54.265). The planets and recipes here are
 * GasSiphonRecipes' and SpacePumpingRecipes' own numbers; in game they are read from the running game.
 */
class NrPumpsTest {

    private static final String HYDROGEN = NrResources.fluid("hydrogen"), HELIUM = NrResources.fluid("helium"),
        CHLOROBENZENE = NrResources.fluid("chlorobenzene"), IRON = NrResources.fluid("molten.iron"),
        RADON = NrResources.fluid("radon");

    static final NrData DATA = new NrData(
        List.of(),
        List.of(),
        List.of(
            new NrData.SiphonPlanet(
                "planet.jupiter",
                "Jupiter",
                3,
                List.of(
                    new NrData.SiphonGas(1, HYDROGEN, "Hydrogen", 15_000),
                    new NrData.SiphonGas(2, HELIUM, "Helium", 500),
                    new NrData.SiphonGas(3, NrResources.fluid("nitrogen"), "Nitrogen", 300),
                    new NrData.SiphonGas(4, NrResources.fluid("oxygen"), "Oxygen", 200))),
            new NrData.SiphonPlanet(
                "planet.saturn",
                "Saturn",
                5,
                List.of(new NrData.SiphonGas(1, HYDROGEN, "Hydrogen", 18_000)))),
        List.of(
            new NrData.PumpRecipe(2, 1, CHLOROBENZENE, "Chlorobenzene (2, 1)", 896_000),
            new NrData.PumpRecipe(4, 2, IRON, "Molten Iron (4, 2)", 896_000),
            new NrData.PumpRecipe(8, 6, RADON, "Radon (8, 6)", 64_000)));

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

    // ---- Planetary Gas Siphon

    @Test
    void siphonsJupitersHydrogenAtEvWithCupronickel() {
        final PowerModel model = compute(NrPumps.GAS_SIPHON, Map.of());
        // calculateEUt: 1 x (4 << 8) = 1,024 EU/t; EV's 2,048 V is under 4x that: no overclock.
        assertEquals(-1_024, model.euPerTick());
        // processTime (int) (20 / 2.1) = 9 ticks for 15,000 L.
        assertEquals(15_000 * 20.0 / 9, flow(model.outputs(), HYDROGEN), 1e-9);
        assertEquals(List.of(), model.inputs());
        assertEquals("64", model.stat("Mining pipes"));
        assertEquals("0", model.stat("Overclocks"));
        assertEquals("9", model.stat("Ticks a run"));
    }

    @Test
    void eachOverclockDoublesTheGasForFourTimesThePower() {
        // IV: log4(8,192 / 1,024) = 1.5, one overclock: 30,000 L at 4,096 EU/t; Eternal coils run it in 5 ticks.
        final PowerModel model = compute(NrPumps.GAS_SIPHON, Map.of("hatch", "IV", "coil", "eternal"));
        assertEquals(-4_096, model.euPerTick());
        assertEquals(30_000 * 20.0 / 5, flow(model.outputs(), HYDROGEN), 1e-9);
        assertEquals("30,000", model.stat("Litres a run"));
    }

    @Test
    void aHatchExactlyAPowerOfFourOverTheDrawTakesThatManyOverclocks() {
        // Depth 2 draws 2,048: EV gives none, LuV (32,768 = 16 x 2,048) exactly two.
        final PowerModel ev = compute(NrPumps.GAS_SIPHON, Map.of("depth", "2"));
        assertEquals(-2_048, ev.euPerTick());
        assertEquals(500 * 20.0 / 9, flow(ev.outputs(), HELIUM), 1e-9);
        final PowerModel luv = compute(NrPumps.GAS_SIPHON, Map.of("depth", "2", "hatch", "LuV"));
        assertEquals(-32_768, luv.euPerTick());
        assertEquals(2_000 * 20.0 / 9, flow(luv.outputs(), HELIUM), 1e-9);
        assertEquals(2, NrPumps.siphonOverclocks(32_768, 2_048));
        assertEquals(0, NrPumps.siphonOverclocks(2_048, 2_048));
    }

    @Test
    void aHatchUnderTheDrawRunsNothing() {
        // Saturn is tier 5: 1 x (4 << 12) = 16,384 EU/t, over EV's 2,048 V.
        final PowerModel model = compute(NrPumps.GAS_SIPHON, Map.of("planet", "planet.saturn"));
        assertEquals(0, model.euPerTick());
        assertEquals(List.of(), model.outputs());
        assertEquals(List.of(NrPumps.HATCH_TOO_WEAK), model.warnings());
        assertEquals("16,384", model.stat("Base EU/t"));
        // A depth the planet has no gas at.
        final PowerModel deep = compute(NrPumps.GAS_SIPHON, Map.of("planet", "planet.saturn", "depth", "3"));
        assertEquals(List.of(NrPumps.NO_GAS_AT_DEPTH), deep.warnings());
        assertEquals(List.of(), deep.outputs());
    }

    @Test
    void coilsShortenTheRunInTheGamesFloatSteps() {
        // (int) (20 / (2 + 0.1 x max(1, tier))): Cupronickel 9.52, Kanthal 9.09, Nichrome 8.70, HSS-G exactly 8,
        // HSS-S 7.69, Trinium 6.90, Hypogen 6.06, Eternal 5.88.
        assertEquals(9, NrPumps.siphonTicks(1));
        assertEquals(9, NrPumps.siphonTicks(2));
        assertEquals(8, NrPumps.siphonTicks(3));
        assertEquals(8, NrPumps.siphonTicks(5));
        assertEquals(7, NrPumps.siphonTicks(6));
        assertEquals(6, NrPumps.siphonTicks(9));
        assertEquals(6, NrPumps.siphonTicks(13));
        assertEquals(5, NrPumps.siphonTicks(14));
        // calculateEUt for Neptune (tier 6) at depth 4: 4 x (4 << 14).
        assertEquals(262_144, NrPumps.siphonEuPerTick(4, 6));
    }

    @Test
    void siphonOffersThePlanetsDepthsAndHatchesFromEv() {
        final PowerSource source = source(NrPumps.GAS_SIPHON);
        assertEquals("IV", source.unlock());
        assertEquals(List.of("planet.jupiter", "planet.saturn"), keys(source, "planet"));
        assertEquals(List.of("1", "2", "3", "4"), keys(source, "depth"));
        assertEquals(
            List.of("EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX"),
            keys(source, "hatch"));
        assertEquals(14, keys(source, "coil").size());
    }

    // ---- Space Pumping Modules

    @Test
    void mkOnePumpsItsOneRecipeAtFourParallelsForFourAmpsOfUhv() {
        final PowerModel model = compute(NrPumps.PUMP_1, Map.of());
        // 896,000 L x 4 parallels every 20 ticks; (int) VP[9] = 1,966,080 EU/t a parallel.
        assertEquals(List.of(new Flow(CHLOROBENZENE, 3_584_000, PowerModel.Unit.L)), model.outputs());
        assertEquals(-7_864_320, model.euPerTick());
        assertEquals("4", model.stat("Parallels in use"));
    }

    @Test
    void slotsOnOneFluidAddUpAndEachParallelDraws() {
        final PowerModel model = compute(
            NrPumps.PUMP_2,
            Map.of(
                "recipe1",
                "4:2",
                "parallels1",
                "4",
                "recipe2",
                "8:6",
                "parallels2",
                "2",
                "recipe3",
                "4:2",
                "parallels3",
                "1"));
        assertEquals(896_000 * 5, flow(model.outputs(), IRON), 1e-9);
        assertEquals(64_000 * 2, flow(model.outputs(), RADON), 1e-9);
        assertEquals(-7L * 1_966_080, model.euPerTick());
    }

    @Test
    void parallelsStopAtTheModulesMostAndZeroPumpsNothing() {
        // Mk-III: four recipes at 64 parallels is 256 A of UHV.
        final PowerModel full = compute(
            NrPumps.PUMP_3,
            Map.of("recipe2", "4:2", "parallels2", "999", "recipe3", "8:6", "recipe4", "4:2"));
        assertEquals(-503_316_480, full.euPerTick());
        assertEquals(64.0 * 896_000, flow(full.outputs(), CHLOROBENZENE), 1e-9);
        assertEquals(128.0 * 896_000, flow(full.outputs(), IRON), 1e-9);
        final PowerModel off = compute(NrPumps.PUMP_1, Map.of("parallels1", "0"));
        assertEquals(List.of(), off.outputs());
        assertEquals(0, off.euPerTick());
        final PowerModel none = compute(NrPumps.PUMP_2, Map.of("recipe1", NrPumps.NO_RECIPE));
        assertEquals(List.of(), none.outputs());
    }

    @Test
    void modulesHaveTheirRecipeSlotsAndTiers() {
        final PowerSource one = source(NrPumps.PUMP_1);
        assertEquals("UHV", one.unlock());
        assertNull(one.setting("recipe2"));
        assertEquals(List.of(NrPumps.NO_RECIPE, "2:1", "4:2", "8:6"), keys(one, "recipe1"));
        assertEquals("2:1", ((PowerSetting.Select) one.setting("recipe1")).defaultKey());
        final PowerSource two = source(NrPumps.PUMP_2);
        assertEquals("UEV", two.unlock());
        assertEquals(NrPumps.NO_RECIPE, ((PowerSetting.Select) two.setting("recipe4")).defaultKey());
        assertEquals(4, ((PowerSetting.Number) two.setting("parallels4")).max(), 0);
        final PowerSource three = source(NrPumps.PUMP_3);
        assertEquals("UXV", three.unlock());
        assertEquals(64, ((PowerSetting.Number) three.setting("parallels1")).defaultValue(), 0);
        assertEquals("gregtech:gt.blockmachines@14014", NrResources.machine(NrPumps.PUMP_3));
        assertEquals("gregtech:gt.blockmachines@15559", NrResources.machine(NrPumps.GAS_SIPHON));
    }

    private static List<String> keys(final PowerSource source, final String settingId) {
        final PowerSetting.Select select = assertInstanceOf(PowerSetting.Select.class, source.setting(settingId));
        final List<String> out = new ArrayList<>();
        for (final PowerSetting.Option option : select.options()) out.add(option.key());
        return out;
    }
}
