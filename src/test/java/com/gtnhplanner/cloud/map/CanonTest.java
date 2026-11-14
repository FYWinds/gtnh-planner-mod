package com.gtnhplanner.cloud.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnhplanner.cloud.sync.LiveOp;

/** The mapping's small rules: one spelling of JSON, the plan's ids, and what is worked out on load. */
class CanonTest {

    private static JsonObject json(final String text) {
        return new JsonParser().parse(text)
            .getAsJsonObject();
    }

    @Test
    void jsonHasOneSpellingWhateverWroteIt() {
        // As the game writes it, and as the website reads it and writes it back.
        final JsonObject game = json("{\"machineCount\":4.0,\"position\":{\"x\":118,\"y\":-0.0},\"a\":[1e0,\"x\"]}");
        final JsonObject site = json("{\"a\":[1,\"x\"],\"position\":{\"y\":0,\"x\":118.00},\"machineCount\":4}");
        assertEquals(Canon.of(game), Canon.of(site));
        assertEquals(Canon.sum(game), Canon.sum(site));
        assertEquals("{\"a\":[1,\"x\"],\"machineCount\":4,\"position\":{\"x\":118,\"y\":0}}", Canon.of(game));
        assertNotEquals(Canon.sum(game), Canon.sum(json("{\"a\":[\"x\",1],\"machineCount\":4}")), "arrays in order");
        assertNotEquals(Canon.of(json("{\"a\":1}")), Canon.of(json("{\"a\":\"1\"}")), "a number is not a string");
        assertEquals(
            16,
            Canon.sum(game)
                .length());
        assertEquals("{\"r\":0.1}", Canon.of(json("{\"r\":0.1}")));
    }

    @Test
    void aThingsSumLeavesOutItsPlaceAndItsGameData() {
        final JsonObject a = json("{\"id\":\"n\",\"machineCount\":2,\"position\":{\"x\":1,\"y\":2}}");
        final JsonObject b = json(
            "{\"machineCount\":2.0,\"id\":\"n\",\"position\":{\"x\":9,\"y\":9},\"gtnhplanner\":{\"v\":1,\"game\":{},\"sum\":\"x\"}}");
        assertEquals(Fresh.sum(a), Fresh.sum(b));
        assertNotEquals(
            Fresh.sum(a),
            Fresh.sum(json("{\"id\":\"m\",\"machineCount\":2}")),
            "the id is a website field");
    }

    @Test
    void theGamesIdsAreThemselvesAndTheWebsitesKeepTheirs() {
        final UUID u = UUID.randomUUID();
        assertEquals(u, PlanIds.uuid(u.toString()));
        assertEquals(UUID.nameUUIDFromBytes("node-1".getBytes(StandardCharsets.UTF_8)), PlanIds.uuid("node-1"));
        // Only the canonical spelling is a UUID: any other parses differently on the way back.
        assertNotEquals(
            u,
            PlanIds.uuid(
                u.toString()
                    .toUpperCase(java.util.Locale.ROOT)));
        assertNotEquals(new UUID(1, 1), PlanIds.uuid("0-1-0-0-1"));
        assertEquals(PlanIds.part("card", "r1"), PlanIds.part("card", "r1"));
        assertNotEquals(PlanIds.part("card", "r1"), PlanIds.part("card", "r2"));
    }

    @Test
    void powerReadingsAreWorkedOutOnLoad() {
        final JsonObject recipe = json(
            "{\"id\":\"r\",\"power\":{\"sourceId\":\"gas-turbine\",\"euPerTick\":32,\"stats\":[],\"warnings\":[\"w\"]}}");
        final JsonObject stripped = Derived.recipe(recipe);
        assertEquals("{\"id\":\"r\",\"power\":{\"sourceId\":\"gas-turbine\"}}", stripped.toString());
        assertTrue(
            recipe.getAsJsonObject("power")
                .has("euPerTick"),
            "the recipe given is unchanged");
        final JsonObject plain = json("{\"id\":\"q\",\"inputs\":[]}");
        assertSame(plain, Derived.recipe(plain));
        final JsonObject plan = json("{\"name\":\"P\",\"recipes\":[" + plain + "]}");
        assertSame(plan, Derived.plan(plan), "nothing to strip, nothing copied");
        final JsonObject withPower = json("{\"name\":\"P\",\"recipes\":[" + recipe + "," + plain + "]}");
        assertFalse(
            Derived.plan(withPower)
                .getAsJsonArray("recipes")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("power")
                .has("stats"));
    }

    /** Issue 515: the live service refused every new generator, its power sent without euPerTick and stats. */
    @Test
    void anOpThatSendsAPowerCarriesItsReadings() {
        final String turbine = "{\"id\":\"r\",\"outputs\":[],\"power\":{\"sourceId\":\"gas-turbine\",\"euPerTick\":32,"
            + "\"stats\":[{\"label\":\"Fuel\",\"value\":\"Benzene\"}]}}";
        final JsonObject none = json("{\"name\":\"P\",\"recipes\":[]}"),
            one = json("{\"name\":\"P\",\"recipes\":[" + turbine + "]}");

        final List<LiveOp> added = CloudPlans.diff(none, one);
        assertEquals(1, added.size(), added.toString());
        assertEquals(
            LiveOp.Kind.PUT,
            added.getFirst()
                .o());
        final JsonObject put = added.getFirst()
            .v()
            .getAsJsonObject("power");
        assertEquals(
            32,
            put.get("euPerTick")
                .getAsDouble());
        assertEquals(
            1,
            put.getAsJsonArray("stats")
                .size());

        final JsonObject reread = json("{\"name\":\"P\",\"recipes\":[" + turbine.replace("32", "40") + "]}");
        assertEquals(List.of(), CloudPlans.diff(one, reread), "readings alone are no edit");

        final JsonObject resourced = json(
            "{\"name\":\"P\",\"recipes\":[" + turbine.replace("gas-turbine", "steam-turbine")
                .replace("32", "24") + "]}");
        final List<LiveOp> patched = CloudPlans.diff(one, resourced);
        assertEquals(1, patched.size(), patched.toString());
        assertEquals(
            LiveOp.Kind.PATCH,
            patched.getFirst()
                .o());
        final JsonObject sent = patched.getFirst()
            .f()
            .getAsJsonObject("power");
        assertEquals(
            "steam-turbine",
            sent.get("sourceId")
                .getAsString());
        assertEquals(
            24,
            sent.get("euPerTick")
                .getAsDouble());
        assertTrue(sent.has("stats"));

        final JsonObject rewired = json(
            "{\"name\":\"P\",\"recipes\":[" + turbine.replace("\"outputs\":[]", "\"outputs\":[{\"id\":\"eu\"}]")
                + "]}");
        final List<LiveOp> other = CloudPlans.diff(one, rewired);
        assertEquals(1, other.size(), other.toString());
        assertFalse(
            other.getFirst()
                .f()
                .has("power"),
            "a patch that leaves the power alone sends none");
    }
}
