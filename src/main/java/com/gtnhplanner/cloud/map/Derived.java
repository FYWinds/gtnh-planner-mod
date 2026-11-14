package com.gtnhplanner.cloud.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gtnhplanner.cloud.sync.LiveJson;
import com.gtnhplanner.cloud.sync.LiveOp;
import com.gtnhplanner.cloud.sync.LiveOps;

/**
 * What a shared plan holds that is worked out from the rest of it, so it is never an edit by itself: stripped from both
 * plans before every diff (docs/design/cloud-live.md, "Game mapping"). The list:
 * <ul>
 * <li>a power card recipe's readings, {@code recipes[].power.euPerTick}, {@code .stats} and {@code .warnings}: the
 * source's model at the card's settings, which both the game and the website work out again on load
 * ({@code sourceId} stays: it names the source).</li>
 * </ul>
 * The game still writes them, and an op that sends a recipe's {@code power} carries them ({@link #carry}): the live
 * service checks every recipe put and every field patched against the website's schema, which wants a power card's
 * {@code euPerTick} and {@code stats}, and refuses the batch without them.
 * Nothing the solve answers is written at all: a card's {@code machineCount} is the count it is set to (the posting
 * export writes the solve's answer there instead), and the solve's flows, scales and notes have no place in the plan.
 */
public final class Derived {

    /** The power readings, by their key under a recipe's {@code power}. */
    public static final List<String> POWER_READINGS = List.of("euPerTick", "stats", "warnings");

    private Derived() {}

    /** A recipe without what is worked out on load; the recipe itself when it has none of it. */
    public static JsonObject recipe(final JsonObject recipe) {
        final JsonElement power = recipe.get("power");
        if (power == null || !power.isJsonObject()) return recipe;
        boolean any = false;
        for (final String key : POWER_READINGS) any |= power.getAsJsonObject()
            .has(key);
        if (!any) return recipe;
        final JsonObject stripped = LiveJson.shallow(recipe);
        final JsonObject p = LiveJson.shallow(power.getAsJsonObject());
        for (final String key : POWER_READINGS) p.remove(key);
        stripped.add("power", p);
        return stripped;
    }

    /** A plan without what is worked out on load; untouched parts are shared with the plan given. */
    public static JsonObject plan(final JsonObject plan) {
        final JsonElement recipes = plan.get("recipes");
        if (recipes == null || !recipes.isJsonArray()) return plan;
        final JsonArray out = new JsonArray();
        boolean changed = false;
        for (final JsonElement r : recipes.getAsJsonArray()) {
            final JsonElement stripped = r.isJsonObject() ? recipe(r.getAsJsonObject()) : r;
            changed |= stripped != r;
            out.add(stripped);
        }
        if (!changed) return plan;
        final JsonObject copy = LiveJson.shallow(plan);
        copy.add("recipes", out);
        return copy;
    }

    /**
     * The ops with a power card's readings back wherever they send its {@code power}: a recipe put whole, or a patch
     * that sets its {@code power}, takes that recipe's {@code power} as {@code plan} has it. Every other op is kept.
     */
    public static List<LiveOp> carry(final List<LiveOp> ops, final JsonObject plan) {
        Map<String, JsonObject> recipes = null;
        final List<LiveOp> out = new ArrayList<>(ops.size());
        for (final LiveOp op : ops) {
            final boolean put = op.o() == LiveOp.Kind.PUT, patch = op.o() == LiveOp.Kind.PATCH;
            final JsonObject sent = !"recipes".equals(op.c()) ? null : put ? op.v() : patch ? op.f() : null;
            if (sent == null || !(sent.get("power") instanceof JsonObject)) {
                out.add(op);
                continue;
            }
            if (recipes == null) recipes = recipesById(plan);
            final JsonObject whole = recipes.get(op.id());
            final JsonElement power = whole == null ? null : whole.get("power");
            if (power == null || !power.isJsonObject()) out.add(op);
            else if (put) out.add(LiveOp.put(op.c(), op.id(), LiveJson.copy(whole)));
            else {
                final JsonObject f = LiveJson.shallow(op.f());
                f.add("power", LiveJson.copy(power));
                out.add(LiveOp.patch(op.c(), op.id(), f, op.d()));
            }
        }
        return out;
    }

    private static Map<String, JsonObject> recipesById(final JsonObject plan) {
        final Map<String, JsonObject> byId = new HashMap<>();
        for (final JsonElement r : LiveOps.collection(plan, "recipes")) {
            final String id = LiveJson.idOf(r);
            if (id != null) byId.putIfAbsent(id, r.getAsJsonObject());
        }
        return byId;
    }
}
