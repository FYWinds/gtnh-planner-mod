package com.gtnhplanner.power.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.data.flowchart.Port;
import com.gtnhplanner.importer.game.GameIds;
import com.gtnhplanner.machines.game.WebCards;
import com.gtnhplanner.machines.web.Web;
import com.gtnhplanner.power.PowerRegistry;
import com.gtnhplanner.power.PowerResources;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.sources.Reactors;

/**
 * NEI recipes whose card is a non-recipe machine here.
 * <p>
 * The HTGR's NEI page (kubatech's HTGRRecipes map) shows each fuel's whole making as one recipe: the dust it starts
 * from in, and the mixture, the BISO and TRISO steps, the fuel, the burned fuel and what that breaks down to all out.
 * The reactor runs none of that: it takes the finished TRISO fuel and gives back the burned fuel, as the HTGR card does
 * (power/sources/Reactors, the website's HTGR). So a card from that page is the HTGR card, burning the page's fuel.
 * <p>
 * A fuel page's generator the machine data has no handler for (the SOFCs on the gas turbine fuels, GT++'s Large
 * Semifluid Generator, TST's generators): a card on it would run as the map's first machine. When that generator is a
 * power source's machine and the source burns the recipe's fuel, the card is that source's, burning it
 * ({@link #fuelCard(Node, ItemStack)}).
 */
public final class RecipePower {

    /** kubatech's HTGR recipe map, as the game names it. */
    public static final String HTGR_MAP = "kubatech.htgrrecipes";

    /** A non-recipe machine at its settings. */
    public record Card(String source, Map<String, String> settings) {}

    /** The non-recipe machine an NEI recipe's card is, or null for a recipe card. */
    @Nullable
    public static Card of(final Node node) {
        if (!HTGR_MAP.equals(WebCards.mapId(node))) return null;
        final List<String> items = new ArrayList<>();
        for (final Port<?> port : node.outputs)
            if (port.getValue() instanceof final ItemStack stack && stack.getItem() != null)
                items.add(GameIds.itemId(stack));
        final String pebble = Reactors.htgrPebble(items);
        return pebble == null ? null : new Card(Reactors.HTGR, Map.of(Reactors.HTGR_PEBBLE, pebble));
    }

    /**
     * The power card a recipe card on {@code machine} is ({@link #fuelCard(String, Collection, List, Function,
     * BooleanSupplier)}), or null when it stays a recipe card.
     */
    @Nullable
    public static Card fuelCard(final Node node, @Nullable final ItemStack machine) {
        if (node.isPower() || machine == null || machine.getItem() == null) return null;
        return fuelCard(
            GameIds.itemId(machine),
            inputs(node),
            PowerRegistry.sources(),
            RecipePower::machineId,
            () -> handled(node, machine));
    }

    /**
     * A fuel recipe on a generator that is a power source here: the first source (in {@code sources}' order) whose
     * machine ({@code machineOf}, a source id to its item's website id) is {@code machine}, when none of the recipe's
     * handlers is that machine ({@code handled} false), and one of whose Select settings has an option that resolves
     * ({@link PowerResources#resolve}) to one of the recipe's {@code inputs} (keys of {@link #key(boolean, String)}).
     * The card is that source at that option. Null otherwise: the recipe card stays as it is.
     */
    @Nullable
    static Card fuelCard(final String machine, final Collection<String> inputs, final List<PowerSource> sources,
        final Function<String, String> machineOf, final BooleanSupplier handled) {
        Boolean free = null;
        for (final PowerSource source : sources) {
            if (!machine.equals(machineOf.apply(source.id()))) continue;
            // Asked once, and only of a machine some source is: reading the recipe's handlers is the costly part.
            if (free == null) free = !handled.getAsBoolean();
            if (!free) return null;
            for (final PowerSetting setting : source.settings()) {
                if (!(setting instanceof final PowerSetting.Select select)) continue;
                for (final PowerSetting.Option option : select.options()) {
                    final PowerResources.Ref ref = PowerResources.resolve(option.key());
                    if (ref != null && inputs.contains(key(ref.fluid(), ref.id)))
                        return new Card(source.id(), Map.of(select.id(), option.key()));
                }
            }
        }
        return null;
    }

    /** A resource as the fuel rule compares them: its kind and its website id (items {@code mod:name@meta}). */
    static String key(final boolean fluid, final String id) {
        return (fluid ? "fluid:" : "item:") + id;
    }

    /** The recipe's inputs as {@link #key(boolean, String)} keys; an item holding a fluid (a cell) also as the fluid. */
    private static Set<String> inputs(final Node node) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Port<?> port : node.inputs) {
            if (port.getValue() instanceof final FluidStack fluid && fluid.getFluid() != null)
                out.add(key(true, GameIds.fluidId(fluid)));
            else if (port.getValue() instanceof final ItemStack stack && stack.getItem() != null) {
                out.add(key(false, GameIds.itemId(stack)));
                final FluidStack held = codechicken.nei.recipe.StackInfo.getFluid(stack);
                if (held != null && held.getFluid() != null) out.add(key(true, GameIds.fluidId(held)));
            }
        }
        return out;
    }

    /** A source's machine item as a website id, or null when it is drawn as none ({@link PowerPorts#machineStack}). */
    @Nullable
    private static String machineId(final String sourceId) {
        final ItemStack stack = PowerPorts.machineStack(sourceId);
        return stack == null || stack.getItem() == null ? null : GameIds.itemId(stack);
    }

    /**
     * Whether one of the recipe's handlers is {@code machine} itself: the one {@link WebCards#handlerId} finds for it,
     * listing it among its blocks (or listing none). Not when it finds none (the card would run as the map's first
     * machine) or falls by name to another machine's.
     */
    private static boolean handled(final Node node, final ItemStack machine) {
        final Web.Recipe recipe = WebCards.recipe(node);
        if (recipe == null) return false;
        final String id = WebCards.handlerId(node, recipe, machine);
        if (id == null) return false;
        final List<String> items = WebCards.handlerItems(node, id);
        return items.isEmpty() || items.contains(GameIds.itemId(machine));
    }

    private RecipePower() {}
}
