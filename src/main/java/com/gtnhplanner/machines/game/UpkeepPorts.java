package com.gtnhplanner.machines.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhplanner.api.RecipePropertyAPI;
import com.gtnhplanner.data.MachineConfig;
import com.gtnhplanner.data.effect.EffectResult;
import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.data.flowchart.Port;
import com.gtnhplanner.machines.web.MachineEffects;
import com.gtnhplanner.machines.web.NodeMath;
import com.gtnhplanner.machines.web.Upkeep;
import com.gtnhplanner.machines.web.Web;
import com.gtnhplanner.power.game.PowerPorts;

/**
 * What a GregTech card's machine adds to its recipe (machines/web {@link Upkeep}, its table entry's
 * MachineTable.Behaviour#upkeep) as ports of the card: a run's amount (a second's times the run's seconds) shared by
 * the run's crafts, so the plan wires and totals it as it does the recipe's own. A machine with no limit to a run's
 * crafts cannot share it out: it gets no port, and its card reads the amount instead ({@link #uncounted}).
 */
public final class UpkeepPorts {

    private UpkeepPorts() {}

    /** The ports each card was last given, so a second pass replaces them. */
    private static final Map<Node, List<Port<?>>> ADDED = new WeakHashMap<>();

    /** A card's uncounted upkeep: its Upkeep id, resource name, so much a run or a second, and the run's ticks. */
    public record Uncounted(String id, String name, double amount, boolean fluid, boolean perRun, double runTicks) {}

    private record Run(List<Upkeep> flows, double ticks, double crafts, boolean unlimited) {}

    /** Gives a card its machine's upkeep ports, after its recipe's are read; safe to run again. */
    public static void afterRefresh(final Node node) {
        final List<Port<?>> earlier = ADDED.remove(node);
        if (earlier != null) for (final Port<?> p : earlier) {
            node.inputs.remove(p);
            node.outputs.remove(p);
        }
        final Run run = run(node);
        if (run == null || run.unlimited()) return;
        final List<Port<?>> added = new ArrayList<>();
        for (final Upkeep u : run.flows()) {
            final double perCraft = u.perCraft(run.ticks(), run.crafts());
            final Port<?> port = perCraft > 0 ? port(u.id(), perCraft) : null;
            if (port == null) continue;
            port.fromModel = true;
            (u.input() ? node.inputs : node.outputs).add(port);
            added.add(port);
        }
        if (!added.isEmpty()) ADDED.put(node, added);
    }

    /** The upkeep a card with no limit to a run's crafts cannot count, for it to read out; empty for any other. */
    public static List<Uncounted> uncounted(final Node node) {
        final Run run = run(node);
        if (run == null || !run.unlimited()) return List.of();
        final List<Uncounted> out = new ArrayList<>();
        for (final Upkeep u : run.flows()) {
            if (!(u.amount() > 0)) continue;
            final Object value = resource(u.id());
            if (value instanceof final FluidStack fluid) out.add(
                new Uncounted(u.id(), fluid.getLocalizedName(), u.amount(), true, u.perRun(), run.ticks()));
            else if (value instanceof final ItemStack item) out.add(
                new Uncounted(u.id(), item.getDisplayName(), u.amount(), false, u.perRun(), run.ticks()));
        }
        return out;
    }

    /** The card's upkeep and its run: the engine's ticks and crafts a run; null when it has none. */
    @Nullable
    private static Run run(final Node node) {
        final MachineConfig cfg = node.machineConfig;
        if (node.isPower() || cfg == null) return null;
        final NodeMath.Result result = WebEffect.result(node, cfg);
        if (result == null) return null;
        final Web.Recipe recipe = WebEffect.recipe(node);
        if (recipe == null) return null;
        final List<Upkeep> flows = MachineEffects.upkeep(recipe, WebCards.node(node, cfg, recipe));
        if (flows.isEmpty()) return null;
        final EffectResult effect = cfg.computeEffect(node.properties);
        return new Run(
            flows,
            effect.duration(),
            effect.throughputFactor(),
            result.machineParallels() >= Integer.MAX_VALUE);
    }

    /** The item or fluid of an Upkeep id; null when the game has none. */
    @Nullable
    private static Object resource(final String id) {
        if (id.startsWith("fluid:")) return PowerPorts.fluid(id.substring("fluid:".length()));
        if (id.startsWith("item:")) return PowerPorts.item(id.substring("item:".length()));
        return null;
    }

    @Nullable
    private static Port<?> port(final String id, final double perCraft) {
        final Object value = resource(id);
        final int whole = (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(perCraft)));
        if (value instanceof final FluidStack fluid) {
            fluid.amount = whole;
            final Port<FluidStack> port = new Port<>(RecipePropertyAPI.FLUID, fluid, 1f);
            port.setExactAmount(perCraft);
            return port;
        }
        if (value instanceof final ItemStack item) {
            item.stackSize = whole;
            final Port<ItemStack> port = new Port<>(RecipePropertyAPI.ITEM, item, 1f);
            port.setExactAmount(perCraft);
            return port;
        }
        return null;
    }
}
