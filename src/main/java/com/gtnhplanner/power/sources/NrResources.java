package com.gtnhplanner.power.sources;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.gtnhplanner.power.PowerResources;

/**
 * Flow names for what the resource multiblocks (NrMiners, NrPumps) make and take. The website's resource map
 * ({@link PowerResources}) knows none of the ores and few of the fluids, so these flows name the game's resource
 * itself: {@code nr:item:<mod:name@meta>} or {@code nr:fluid:<registry name>}, ids in the resource map's own form, and
 * {@link PowerResources#resolve} falls back to {@link #resolve} for them. The game side records each one's name in the
 * player's language ({@link #name}) for the picker's search. The website's machine icons know none of these machines
 * either: each registers its controller ({@link #machine(String, String)}), which PowerPorts.machineStack draws.
 */
public final class NrResources {

    private static final String ITEM = "nr:item:", FLUID = "nr:fluid:";

    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
    private static final Map<String, PowerResources.Ref> REFS = new ConcurrentHashMap<>();
    private static final Map<String, String> MACHINES = new ConcurrentHashMap<>();

    /** The flow name of an item, by its resource-map id ({@code mod:name@meta}, no {@code @} for meta 0). */
    public static String item(final String id) {
        return ITEM + id;
    }

    /** The flow name of a fluid, by its registry name. */
    public static String fluid(final String id) {
        return FLUID + id;
    }

    /** Records the name a flow shows in the player's language. */
    public static void name(final String flow, final String displayName) {
        NAMES.put(flow, displayName);
        REFS.remove(flow);
    }

    /** The resource a {@link #item} or {@link #fluid} flow name stands for; null for any other name. */
    @Nullable
    public static PowerResources.Ref resolve(final String flow) {
        final boolean item = flow.startsWith(ITEM);
        if (!item && !flow.startsWith(FLUID)) return null;
        return REFS.computeIfAbsent(flow, name -> {
            final PowerResources.Ref ref = new PowerResources.Ref();
            ref.kind = item ? "item" : "fluid";
            ref.id = name.substring((item ? ITEM : FLUID).length());
            ref.displayName = NAMES.getOrDefault(name, ref.id);
            return ref;
        });
    }

    /** Records the controller item ({@code mod:name@meta}) a source is drawn as. */
    public static void machine(final String sourceId, final String itemId) {
        MACHINES.put(sourceId, itemId);
    }

    /** The controller item a source registered, or null. */
    @Nullable
    public static String machine(final String sourceId) {
        return MACHINES.get(sourceId);
    }

    private NrResources() {}
}
