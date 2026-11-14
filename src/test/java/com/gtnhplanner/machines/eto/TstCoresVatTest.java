package com.gtnhplanner.machines.eto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.gtnhplanner.machines.BacterialVat;
import com.gtnhplanner.machines.BacterialVat.Needs;
import com.gtnhplanner.machines.web.HandlerData;
import com.gtnhplanner.machines.web.NodeMath;
import com.gtnhplanner.machines.web.Web;

/**
 * Biosphere III's bacterial vat modes (TST 0.8.1 common/machine/TST_BiosphereIII.java), through the whole port
 * (NodeMath) on bartworks' map, and the vat's own numbers. bartworks' two recipes (BioRecipeLoader.java:145-162):
 * grape juice to wine, 100 L to 12 L in 400 ticks, and fermented biomass to fulvic acid, 10,000 L to 1,000 L in 2,748
 * ticks, both 30 EU/t and HV glass. The game's arithmetic: the speed bonus multiplies the duration before the
 * overclocks
 * halve it, the whole truncated (OverclockCalculator); each parallel billed at 30 EU/t, as many as V x A pays for
 * (GTCM_ParallelHelper :86-90,160); void protection first cuts the parallels to what the output hatch takes (:133-155);
 * the overclocks are whole powers of four of V x A over the parallels' draw (:186-187). One hatch amp throughout.
 */
class TstCoresVatTest {

    private static final String VAT = "bw.recipe.BacteriaVat", NORMAL_MODE = "bacterial-vat-mode",
        AUTOMATION_MODE = "bacterial-vat-automation-mode";

    // region Harness

    private static Web.Resource fluid(final String id, final double litres) {
        final Web.Resource r = new Web.Resource();
        r.kind = "fluid";
        r.id = id;
        r.amount = litres;
        return r;
    }

    /** A vat recipe as WebCards.recipe builds it, its glass tier in the metadata. */
    private static Web.Recipe recipe(final double ticks, final double in, final double out) {
        final HandlerData.MapEntry entry = HandlerData.map(VAT);
        assertNotNull(entry, VAT);
        final Web.Recipe r = new Web.Recipe();
        r.id = VAT + ":test";
        r.name = "test";
        r.kind = "gregtech_machine";
        r.category = "gregtech";
        r.machineType = entry.name;
        r.durationTicks = ticks;
        r.eut = 30;
        r.minimumTier = HandlerData.recipeMinimumTier(30);
        r.specialValue = 0.0;
        r.nei = new Web.Nei();
        r.nei.additionalInfo = List.of("Special value: 0");
        r.source = new Web.Source();
        r.source.recipeMap = entry.name;
        r.metadata = new JsonObject();
        r.metadata.addProperty("recipeMapId", entry.id);
        r.metadata.addProperty("specialValue", 0.0);
        r.metadata.addProperty(TstCoresVat.RECIPE_GLASS, 3);
        r.inputs = List.of(fluid("in", in));
        r.outputs = List.of(fluid("out", out));
        r.machineConfigControls = HandlerData.recipeControls(entry, 0);
        r.machineHandlers = HandlerData.handlers(entry, r.minimumTier, r.durationTicks, r.eut, r.machineConfigControls);
        return r;
    }

    private static Web.Recipe wine() {
        return recipe(400, 100, 12);
    }

    private static Web.Recipe fulvic() {
        return recipe(2748, 10_000, 1_000);
    }

    /** Biosphere III on hatches of {@code tier}, one amp, these settings (its own and the vat's, as WebCards.node). */
    private static NodeMath.Result run(final Web.Recipe recipe, final String tier, final Map<String, String> settings) {
        final Web.Node n = new Web.Node();
        n.overclockTier = tier;
        n.hatchVoltageTier = tier;
        n.hatchAmps = 1.0;
        n.powerInputMode = "amps";
        n.machineHandlerId = "tst-" + TstControls.key("Biosphere III");
        n.machineConfigTiers = new LinkedHashMap<>(settings);
        n.parallel = 1.0;
        n.machineCount = 1.0;
        return NodeMath.compute(recipe, n);
    }

    private static Map<String, String> settings(final String... pairs) {
        final Map<String, String> out = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return out;
    }

    private static void expect(final NodeMath.Result r, final double ticks, final double eut, final double parallels) {
        assertEquals("Biosphere III", r.effectiveRecipe().machineType);
        assertNull(r.stall());
        assertEquals(
            ticks,
            r.overclock()
                .durationTicks(),
            "ticks");
        assertEquals(
            eut,
            r.overclock()
                .eut(),
            1e-9,
            "EU/t per parallel");
        assertEquals(parallels, r.machineParallels(), "parallels");
    }

    private static final Needs HV = new Needs(3, 0, false);

    private static TstCoresVat.Run vat(final int mode, final int dishes, final long out, final String... settings) {
        return TstCoresVat.run(mode, dishes, settings(settings), HV, out, List.of());
    }

    // endregion

    /**
     * Mode 0 (:269, :278): 4 parallels a dish at half the time. One dish: 4 parallels; the default EV hatch (64,000 L
     * of room at half full holds 4 x 1,001 x 12 = 48,048) takes 5. 4 x 30 = 120 of 512: one step; 400 x 0.5 / 2 = 100
     * ticks at 120 EU/t a parallel.
     */
    @Test
    void normalModeRunsFourParallelsADishAtHalfTheTime() {
        expect(run(wine(), "HV", settings(TstCoresVat.MODE, NORMAL_MODE)), 100, 120, 4);
        final TstCoresVat.Run run = vat(TstCoresVat.NORMAL, 1, 12);
        assertEquals(
            "ev",
            run.setup()
                .hatch()
                .key());
        assertEquals(1001, run.multiplier());
        assertEquals(5, run.fit());
        assertEquals(4, run.parallels());
    }

    /**
     * Mode 1 (:270, :279): 1 parallel a dish at full time, on the ME output hatch by default (no room to run out of).
     * 30 EU/t is billed as 32: 512 / 32 = 16, two steps; 400 / 4 = 100 ticks at 480 EU/t.
     */
    @Test
    void automationModeRunsOneParallelADishAtFullTime() {
        expect(run(wine(), "HV", settings(TstCoresVat.MODE, AUTOMATION_MODE)), 100, 480, 1);
        final TstCoresVat.Run run = vat(TstCoresVat.AUTOMATION, 1, 12);
        assertEquals(
            "me",
            run.setup()
                .hatch()
                .key());
        assertEquals(Long.MAX_VALUE, run.fit());
    }

    /**
     * A stack of 64 dishes: 256 parallels in mode 0, 64 in mode 1; the setting stops at 64. On the ME hatch nothing is
     * cut: 256 x 30 = 7,680 of LuV's 32,768, one step: 400 x 0.5 / 2 = 100 ticks.
     */
    @Test
    void aStackOfDishesCapsTheParallels() {
        assertEquals(256, TstCoresVat.structuralParallels(TstCoresVat.NORMAL, TstCoresVat.MAX_DISHES));
        assertEquals(64, TstCoresVat.structuralParallels(TstCoresVat.AUTOMATION, TstCoresVat.MAX_DISHES));
        final Map<String, String> stack = settings(
            TstCoresVat.MODE,
            NORMAL_MODE,
            TstCoresVat.DISHES,
            "64",
            BacterialVat.OUTPUT_HATCH,
            "me");
        expect(run(wine(), "LuV", stack), 100, 120, 256);
        stack.put(TstCoresVat.DISHES, "65");
        expect(run(wine(), "LuV", stack), 100, 120, 256);
        stack.put(TstCoresVat.MODE, AUTOMATION_MODE);
        assertEquals(64, run(wine(), "LuV", stack).machineParallels());
    }

    /**
     * Mode 1's multiplier (:363-364): (int) (((glass - recipe glass) x 600 + 1601.0) / 1000 x 1000), the fill never
     * read. HV glass on an HV recipe 1,601; a tier over 2,201; UXV 7,601. Below the recipe's glass the doubles truncate
     * to 1,000 (the glass gate refuses it anyway).
     */
    @Test
    void automationMultiplierIsSixHundredATierOverTheRecipesGlass() {
        assertEquals(1601, TstCoresVat.automationMultiplier(3, 3));
        assertEquals(2201, TstCoresVat.automationMultiplier(4, 3));
        assertEquals(7001, TstCoresVat.automationMultiplier(12, 3));
        assertEquals(7601, TstCoresVat.automationMultiplier(13, 3));
        assertEquals(1000, TstCoresVat.automationMultiplier(2, 3));
        assertEquals(2201, vat(TstCoresVat.AUTOMATION, 1, 12, BacterialVat.GLASS, "4").multiplier());
        assertEquals(1601, vat(TstCoresVat.AUTOMATION, 1, 12, BacterialVat.FILL, "0").multiplier());
    }

    /**
     * Mode 0's multiplier is bartworks' calcMod + 1 (:380-393): 1,001 at half full, ceil(1000 x (1 - (2 x 0.25 - 1)^2))
     * + 1 = 751 a quarter full, 1 empty or on the ME hatch.
     */
    @Test
    void normalMultiplierReadsTheHatchsFill() {
        assertEquals(1001, vat(TstCoresVat.NORMAL, 1, 12, BacterialVat.OUTPUT_HATCH, "giant").multiplier());
        assertEquals(1, vat(TstCoresVat.NORMAL, 1, 12, BacterialVat.FILL, "0").multiplier());
        assertEquals(1, vat(TstCoresVat.NORMAL, 1, 12, BacterialVat.OUTPUT_HATCH, "me").multiplier());
        assertEquals(
            751,
            vat(TstCoresVat.NORMAL, 1, 12, BacterialVat.OUTPUT_HATCH, "giant", BacterialVat.FILL, "25").multiplier());
    }

    /**
     * Void protection cuts the parallels to what the hatch takes (VoidProtectionHelper :207-215): fulvic acid, 64
     * dishes, the Giant hatch at half full: 50,000,000 / 1,001,000 = 49 of 256. 49 x 30 = 1,470 of IV's 8,192, one
     * step:
     * 2,748 x 0.5 / 2 = 687 ticks at 120 EU/t.
     */
    @Test
    void voidProtectionCutsTheParallelsToTheHatchsRoom() {
        final Map<String, String> s = settings(TstCoresVat.MODE, NORMAL_MODE, TstCoresVat.DISHES, "64");
        expect(run(fulvic(), "IV", s), 687, 120, 49);
        final TstCoresVat.Run run = vat(TstCoresVat.NORMAL, 64, 1_000);
        assertEquals(
            "giant",
            run.setup()
                .hatch()
                .key());
        assertEquals(49, run.fit());
        assertEquals(49, run.parallels());
        assertFalse(run.full());
        assertEquals(1001, TstCoresVat.outputMultiplier(run, 1_000, 49));
    }

    /**
     * Voiding instead runs every parallel and loses what the hatch cannot take: 256 x 30 = 7,680 of 8,192, no step:
     * 2,748 x 0.5 = 1,374 ticks; each parallel keeps 50,000,000 / 256,000 = 195.3125 of its 1,000 L.
     */
    @Test
    void voidingRunsEveryParallelAndKeepsWhatFits() {
        final Map<String, String> s = settings(
            TstCoresVat.MODE,
            NORMAL_MODE,
            TstCoresVat.DISHES,
            "64",
            BacterialVat.VOID,
            "void");
        expect(run(fulvic(), "IV", s), 1374, 30, 256);
        final TstCoresVat.Run run = vat(TstCoresVat.NORMAL, 64, 1_000, BacterialVat.VOID, "void");
        assertEquals(256, run.parallels());
        assertEquals(195.3125, TstCoresVat.outputMultiplier(run, 1_000, 256), 1e-12);
    }

    /**
     * Not one parallel fits: an HV hatch half full has 32,000 L of room for a 1,001,000 L parallel, and the machine is
     * held (FLUID_OUTPUT_FULL); the card's parallels stay at the machine maths' floor of 1.
     */
    @Test
    void aHatchWithNoRoomForOneParallelHoldsTheMachine() {
        final TstCoresVat.Run run = vat(TstCoresVat.NORMAL, 1, 1_000, BacterialVat.OUTPUT_HATCH, "hv");
        assertEquals(0, run.fit());
        assertTrue(run.full());
        assertNotNull(TstCoresVat.gate(run, HV));
        assertNull(TstCoresVat.gate(vat(TstCoresVat.NORMAL, 1, 12, BacterialVat.OUTPUT_HATCH, "hv"), HV));
        assertEquals(
            1,
            run(fulvic(), "HV", settings(TstCoresVat.MODE, NORMAL_MODE, BacterialVat.OUTPUT_HATCH, "hv"))
                .machineParallels());
    }

    /**
     * The room boundary, one parallel of mode 1 at 1,601 into an empty EV hatch (128,000 L): 79 L out (126,479) fits,
     * 80 L (128,080) does not.
     */
    @Test
    void theRoomBoundary() {
        final String[] emptyEv = { BacterialVat.OUTPUT_HATCH, "ev", BacterialVat.FILL, "0" };
        assertEquals(1, vat(TstCoresVat.AUTOMATION, 1, 79, emptyEv).fit());
        assertEquals(0, vat(TstCoresVat.AUTOMATION, 1, 80, emptyEv).fit());
    }

    /**
     * Mode 1 into a drained Giant hatch: 100,000,000 / 1,601,000 = 62 of 64 dishes; 62 x 30 = 1,860 of 8,192, one step:
     * 2,748 / 2 = 1,374 ticks. With UXV glass 7,601: 13 fit; 390 of 8,192, two steps: 2,748 / 4 = 687 ticks at 480.
     */
    @Test
    void automationModeIsCutByTheRoomToo() {
        final Map<String, String> s = settings(
            TstCoresVat.MODE,
            AUTOMATION_MODE,
            TstCoresVat.DISHES,
            "64",
            BacterialVat.OUTPUT_HATCH,
            "giant",
            BacterialVat.FILL,
            "0");
        expect(run(fulvic(), "IV", s), 1374, 120, 62);
        s.put(BacterialVat.GLASS, "13");
        expect(run(fulvic(), "IV", s), 687, 480, 13);
    }

    /** The default hatch: mode 0's smallest that holds every parallel's x1001 run at half full, mode 1's ME. */
    @Test
    void defaultHatches() {
        assertEquals(
            "ev",
            TstCoresVat.defaultHatch(TstCoresVat.NORMAL, 4, 12)
                .key());
        assertEquals(
            "hv",
            TstCoresVat.defaultHatch(TstCoresVat.NORMAL, 2, 12)
                .key());
        assertEquals(
            "giant",
            TstCoresVat.defaultHatch(TstCoresVat.NORMAL, 256, 1_000)
                .key());
        assertEquals(
            "me",
            TstCoresVat.defaultHatch(TstCoresVat.AUTOMATION, 64, 1_000)
                .key());
    }
}
