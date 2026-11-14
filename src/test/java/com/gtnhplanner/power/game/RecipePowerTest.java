package com.gtnhplanner.power.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtnhplanner.power.PowerGroup;
import com.gtnhplanner.power.PowerModel;
import com.gtnhplanner.power.PowerRegistry;
import com.gtnhplanner.power.PowerResources;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;

/**
 * The fuel rule (RecipePower.fuelCard): a fuel recipe on a generator becomes that generator's power card when a power
 * source's machine is the generator and the source lists the recipe's fuel; a GregTech single block never does.
 */
class RecipePowerTest {

    private static final String SOFC = "gregtech:gt.blockmachines@13101";
    private static final String TURBINE = "gregtech:gt.blockmachines@15527";
    private static final String BENZENE = RecipePower.key(true, "benzene");

    private static PowerSource source(final String id, final PowerSetting... settings) {
        return new PowerSource(
            id,
            id,
            PowerGroup.ENGINES,
            null,
            "",
            List.of(settings),
            read -> new PowerModel(0, List.of(), List.of(), List.of()));
    }

    private static PowerSetting.Select select(final String id, final String... keys) {
        final List<Option> options = new java.util.ArrayList<>();
        for (final String key : keys) options.add(new Option(key, key));
        return new PowerSetting.Select(id, id, options, keys[0]);
    }

    private static final PowerSource CELL = source("cell", select("fuel", "Methane", "Benzene"));
    private static final Function<String, String> MACHINES = Map.of("cell", SOFC, "turbine", TURBINE)::get;

    @Test
    @DisplayName("A generator a source is, burning a fuel it lists: that source at that fuel")
    void bothHold() {
        assertEquals(
            new RecipePower.Card("cell", Map.of("fuel", "Benzene")),
            RecipePower.fuelCard(SOFC, false, Set.of(BENZENE), List.of(CELL), MACHINES));
    }

    @Test
    @DisplayName("A GregTech single block stays a recipe card, even when a source is it and lists the fuel")
    void singleBlockStays() {
        assertNull(RecipePower.fuelCard(SOFC, true, Set.of(BENZENE), List.of(CELL), MACHINES));
    }

    @Test
    @DisplayName("A machine no source is stays a recipe card")
    void noSourceMachineStays() {
        assertNull(
            RecipePower.fuelCard("gregtech:gt.blockmachines@31026", false, Set.of(BENZENE), List.of(CELL), MACHINES));
    }

    @Test
    @DisplayName("Same item, another meta, is another machine")
    void otherMetaStays() {
        assertNull(
            RecipePower.fuelCard("gregtech:gt.blockmachines@13102", false, Set.of(BENZENE), List.of(CELL), MACHINES));
    }

    @Test
    @DisplayName("A fuel the source does not list stays a recipe card")
    void unlistedFuelStays() {
        assertNull(
            RecipePower.fuelCard(SOFC, false, Set.of(RecipePower.key(true, "hydrogen")), List.of(CELL), MACHINES));
    }

    @Test
    @DisplayName("An option is matched by the resource its name resolves to, a fluid against a fluid")
    void matchedByResolvedResource() {
        // "Creosote Oil" is the fluid creosote: the option's name is never compared with the recipe's.
        final PowerSource burner = source("cell", select("fuel", "Creosote Oil"));
        assertEquals(
            new RecipePower.Card("cell", Map.of("fuel", "Creosote Oil")),
            RecipePower.fuelCard(SOFC, false, Set.of(RecipePower.key(true, "creosote")), List.of(burner), MACHINES));
        // An item with the fluid's id is not the fluid.
        assertNull(
            RecipePower.fuelCard(SOFC, false, Set.of(RecipePower.key(false, "creosote")), List.of(burner), MACHINES));
    }

    @Test
    @DisplayName("An item fuel matches an item input")
    void itemFuel() {
        final PowerSource reactor = source("cell", select("fuel", "Fuel Rod (Naquadah)"));
        final String rod = PowerResources.resolve("Fuel Rod (Naquadah)").id;
        assertEquals(
            new RecipePower.Card("cell", Map.of("fuel", "Fuel Rod (Naquadah)")),
            RecipePower.fuelCard(SOFC, false, Set.of(RecipePower.key(false, rod)), List.of(reactor), MACHINES));
    }

    @Test
    @DisplayName("The Select listing the fuel is the one set, whatever its id; other settings are left alone")
    void selectThatListsIt() {
        final PowerSource reactor = source(
            "cell",
            select("coolant", "Liquid Air"),
            new PowerSetting.Number("amps", "amps", 1, 4, 1, 1),
            select("burns", "Hydrogen", "Benzene"));
        assertEquals(
            new RecipePower.Card("cell", Map.of("burns", "Benzene")),
            RecipePower.fuelCard(SOFC, false, Set.of(BENZENE), List.of(reactor), MACHINES));
    }

    @Test
    @DisplayName("Of two sources on one machine, the first that lists the fuel")
    void secondSourceOnTheMachine() {
        final PowerSource other = source("other", select("fuel", "Hydrogen"));
        final Function<String, String> machines = Map.of("other", SOFC, "cell", SOFC)::get;
        assertEquals(
            new RecipePower.Card("cell", Map.of("fuel", "Benzene")),
            RecipePower.fuelCard(SOFC, false, Set.of(BENZENE), List.of(other, CELL), machines));
    }

    /** Each source's machine as machine-icons.json draws it (PowerPorts.machineStack without the game). */
    private static String icon(final String sourceId) {
        final PowerResources.Ref ref = PowerResources.machineIcon(sourceId);
        return ref == null ? null : ref.id;
    }

    @Test
    @DisplayName("From the registry: the SOFCs, the Large Semifluid Generator and the Large Gas Turbine burn their fuels")
    void registryGenerators() {
        final List<PowerSource> all = PowerRegistry.sources();
        assertEquals(
            new RecipePower.Card("solid-oxide-fuel-cell-1", Map.of("fuel", "Benzene")),
            RecipePower.fuelCard(SOFC, false, Set.of(BENZENE), all, RecipePowerTest::icon));
        assertEquals(
            new RecipePower.Card("solid-oxide-fuel-cell-2", Map.of("fuel", "Benzene")),
            RecipePower
                .fuelCard("gregtech:gt.blockmachines@13102", false, Set.of(BENZENE), all, RecipePowerTest::icon));
        assertEquals(
            new RecipePower.Card("large-semifluid-generator", Map.of("fuel", "Creosote Oil")),
            RecipePower.fuelCard(
                "gregtech:gt.blockmachines@31026",
                false,
                Set.of(RecipePower.key(true, "creosote")),
                all,
                RecipePowerTest::icon));
        // The gas turbine fuels have a handler for it, and its recipe card is still the power card.
        assertEquals(
            "large-gas-turbine",
            RecipePower.fuelCard(TURBINE, false, Set.of(BENZENE), all, RecipePowerTest::icon)
                .source());
        // The Basic Gas Turbine is the gas-turbine source's machine, but a single block: it stays a recipe card.
        assertNull(
            RecipePower.fuelCard("gregtech:gt.blockmachines@1115", true, Set.of(BENZENE), all, RecipePowerTest::icon));
    }
}
