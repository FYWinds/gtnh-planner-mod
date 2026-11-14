package com.gtnhplanner.power.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.sources.NrResources;

/**
 * What the processing multiblocks' game readers (NrGreenhouseGame, NrOreFactoryGame, NrEssentiaSmelteryGame) share:
 * the game's items and fluids as {@link NrResources} flows, each named in the player's language for the picker's
 * search, and back.
 */
final class NrProcessGame {

    private static final String ITEM = NrResources.item(""), FLUID = NrResources.fluid("");

    /** An item's flow ({@code nr:item:mod:name@meta}, no meta for 0), its name recorded; null for no item. */
    @Nullable
    static String itemFlow(@Nullable final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        final Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        if (name == null) return null;
        final int meta = stack.getItemDamage();
        final String flow = NrResources.item(meta == 0 ? String.valueOf(name) : name + "@" + meta);
        NrResources.name(flow, label(stack));
        return flow;
    }

    /** A fluid's flow ({@code nr:fluid:name}), its name recorded; null for no fluid. */
    @Nullable
    static String fluidFlow(@Nullable final Fluid fluid) {
        if (fluid == null) return null;
        final String flow = NrResources.fluid(fluid.getName());
        NrResources.name(flow, label(fluid));
        return flow;
    }

    /** One of a flow's item (PowerPorts.item reads its id); null for a fluid flow or an item the game lacks. */
    @Nullable
    static ItemStack stack(final String flow) {
        return flow.startsWith(ITEM) ? PowerPorts.item(flow.substring(ITEM.length())) : null;
    }

    /** A flow's fluid; null for an item flow or a fluid the game lacks. */
    @Nullable
    static Fluid fluid(final String flow) {
        return flow.startsWith(FLUID) ? FluidRegistry.getFluid(flow.substring(FLUID.length())) : null;
    }

    /** The game's name of an item, in the player's language; its registry name when the item fails to name itself. */
    static String label(final ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (final RuntimeException e) {
            return String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
        }
    }

    static String label(final Fluid fluid) {
        try {
            return new FluidStack(fluid, 1).getLocalizedName();
        } catch (final RuntimeException e) {
            return fluid.getName();
        }
    }

    /**
     * Options by name, A to Z; a name two things share is told apart by each one's id, so every row of the list can be
     * picked.
     */
    static List<Option> sorted(final Collection<Option> options) {
        final Map<String, Integer> seen = new HashMap<>();
        for (final Option option : options) seen.merge(option.label(), 1, Integer::sum);
        final List<Option> out = new ArrayList<>(options.size());
        for (final Option option : options) out.add(
            seen.get(option.label()) > 1 ? new Option(
                option.key(),
                option.label() + " ("
                    + option.key()
                        .substring(
                            option.key()
                                .indexOf(':', 3) + 1)
                    + ")")
                : option);
        out.sort(
            Comparator.comparing(
                (Option o) -> o.label()
                    .toLowerCase(Locale.ROOT))
                .thenComparing(Option::key));
        return out;
    }

    /** The sub-items of an item, as the creative list has them; the bare item when it lists none. */
    static List<ItemStack> subItems(final Item item) {
        final List<ItemStack> subs = new ArrayList<>();
        try {
            item.getSubItems(item, item.getCreativeTab(), subs);
        } catch (final RuntimeException | LinkageError ignored) {}
        if (subs.isEmpty()) subs.add(new ItemStack(item));
        return subs;
    }

    private NrProcessGame() {}
}
