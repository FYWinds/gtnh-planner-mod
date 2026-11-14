package com.gtnhplanner.machines.eto;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.gtnhplanner.machines.web.HandlerData;
import com.gtnhplanner.machines.web.NodeMath;
import com.gtnhplanner.machines.web.Web;

/**
 * Cards on the maps of TwistSpaceTechnology's machines with their own recipe pages (TstMachinesVisual and the
 * TstVisual* classes): a recipe as WebCards.recipe builds it from the NEI recipe, with its ports, and one machine on
 * it.
 */
final class TstVisualHarness {

    private TstVisualHarness() {}

    /** A port: kind "item" or "fluid", an id, the amount NEI shows and its chance (null for always). */
    static Web.Resource port(final String kind, final String id, final double amount, final Double chance) {
        final Web.Resource r = new Web.Resource();
        r.kind = kind;
        r.id = id;
        r.amount = amount;
        r.chance = chance;
        return r;
    }

    static Web.Resource item(final String id, final double amount) {
        return port("item", id, amount, null);
    }

    static Web.Resource item(final String id, final double amount, final double chance) {
        return port("item", id, amount, chance);
    }

    static Web.Resource fluid(final String id, final double amount) {
        return port("fluid", id, amount, null);
    }

    /** A recipe on the map {@code mapId} with these ports, items before fluids on each side as GTProvider adds them. */
    static Web.Recipe recipe(final String mapId, final double eut, final double ticks, final List<Web.Resource> inputs,
        final List<Web.Resource> outputs) {
        final HandlerData.MapEntry entry = HandlerData.map(mapId);
        assertNotNull(entry, mapId);
        final Web.Recipe r = new Web.Recipe();
        r.id = mapId + ":test";
        r.name = "test";
        r.kind = "gregtech_machine";
        r.category = "gregtech";
        r.machineType = entry.name;
        r.durationTicks = ticks;
        r.eut = eut;
        r.minimumTier = HandlerData.recipeMinimumTier(eut);
        r.specialValue = 0.0;
        r.nei = new Web.Nei();
        r.nei.additionalInfo = List.of("Special value: 0");
        r.source = new Web.Source();
        r.source.recipeMap = entry.name;
        r.metadata = new JsonObject();
        r.metadata.addProperty("recipeMapId", entry.id);
        r.metadata.addProperty("specialValue", 0);
        r.inputs = new ArrayList<>(inputs);
        r.outputs = new ArrayList<>(outputs);
        r.machineConfigControls = HandlerData.recipeControls(entry, 0);
        r.machineHandlers = HandlerData.handlers(entry, r.minimumTier, r.durationTicks, r.eut, r.machineConfigControls);
        return r;
    }

    /**
     * The machine of handler {@code handlerId} on hatches of {@code tier} with {@code amps} amps, at these settings.
     */
    static Web.Node node(final String handlerId, final String tier, final double amps,
        final Map<String, String> settings) {
        final Web.Node n = new Web.Node();
        n.overclockTier = tier;
        n.hatchVoltageTier = tier;
        n.hatchAmps = amps;
        n.powerInputMode = "amps";
        n.machineHandlerId = handlerId;
        n.machineConfigTiers = new LinkedHashMap<>(settings);
        n.parallel = 1.0;
        n.machineCount = 1.0;
        return n;
    }

    static NodeMath.Result run(final Web.Recipe recipe, final String handlerId, final String tier, final double amps,
        final Map<String, String> settings) {
        return NodeMath.compute(recipe, node(handlerId, tier, amps, settings));
    }

    /** What one run takes of input {@code index}: its rate times the run's length in seconds, over the parallels. */
    static double inputPerRun(final NodeMath.Result r, final int index) {
        return r.inputsPerSecond()
            .get(index)
            * r.overclock()
                .durationTicks()
            / 20;
    }

    /** What one run makes of output {@code index}, as {@link #inputPerRun}. */
    static double outputPerRun(final NodeMath.Result r, final int index) {
        return r.outputsPerSecond()
            .get(index)
            * r.overclock()
                .durationTicks()
            / 20;
    }

    /** The whole run's EU/t: per parallel times the parallels. */
    static double runEut(final NodeMath.Result r) {
        return r.euT();
    }
}
