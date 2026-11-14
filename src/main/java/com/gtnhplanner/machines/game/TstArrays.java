package com.gtnhplanner.machines.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.gtnhplanner.importer.game.GameIds;
import com.gtnhplanner.machines.web.HandlerData;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * TwistSpaceTechnology's TST Processing Array (19066) and MegaArray (19049) run the map of the single-block machines
 * put in them, so neither is an NEI catalyst (TST_ProcessingArray.getAvailableRecipeMaps is empty; the MegaArray's
 * prototype holds no machine, so its getRecipeMap is null). A recipe's machine choices get them after NEI's own on the
 * maps the pack's handler data lists them for ({@code eto-tst-cores-arrays.json}), where {@link WebCards#handlerId}
 * finds their templates by the controller's item. Only with TST loaded.
 */
public final class TstArrays {

    private TstArrays() {}

    /** The controllers' meta tile ids and their classes' simple names (MachineLoader :486, :684). */
    private static final int[] IDS = { 19066, 19049 };
    private static final String[] CLASSES = { "TST_ProcessingArray", "TST_BigBroArray" };

    private static volatile List<ItemStack> controllers;
    private static final Map<String, List<ItemStack>> BY_MAP = new ConcurrentHashMap<>();

    /** The arrays that run {@code handler}'s recipes, in id order; empty for any other page. */
    public static List<ItemStack> stacks(final IRecipeHandler handler) {
        final String mapId = mapId(handler);
        if (mapId == null) return List.of();
        final List<ItemStack> all = controllers();
        if (all.isEmpty()) return List.of();
        return BY_MAP.computeIfAbsent(mapId, id -> on(id, all));
    }

    /** The game map id WebCards gives the page's cards: a GregTech map's name, or "smelting" for the furnace page. */
    @Nullable
    private static String mapId(final IRecipeHandler handler) {
        if (handler instanceof final GTNEIDefaultHandler gt)
            return gt.getRecipeMap() != null ? gt.getRecipeMap().unlocalizedName : null;
        return handler instanceof FurnaceRecipeHandler && "smelting".equals(handler.getOverlayIdentifier())
            ? "smelting"
            : null;
    }

    /** The controllers this game has; kept once known, asked again while GregTech is still registering. */
    private static List<ItemStack> controllers() {
        final List<ItemStack> known = controllers;
        if (known != null) return known;
        final List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            final IMetaTileEntity mte = IDS[i] < GregTechAPI.METATILEENTITIES.length
                ? GregTechAPI.METATILEENTITIES[IDS[i]]
                : null;
            if (mte != null && CLASSES[i].equals(
                mte.getClass()
                    .getSimpleName()))
                out.add(new ItemStack(GregTechAPI.sBlockMachines, 1, IDS[i]));
        }
        if (!out.isEmpty()) controllers = List.copyOf(out);
        return out;
    }

    /** The controllers whose item a template of map {@code mapId} lists. */
    private static List<ItemStack> on(final String mapId, final List<ItemStack> all) {
        final HandlerData.MapEntry entry = WebCards.entry(mapId);
        if (entry == null || entry.handlers == null) return List.of();
        final List<ItemStack> out = new ArrayList<>();
        for (final ItemStack stack : all) {
            final String item = GameIds.itemId(stack);
            for (final HandlerData.Template t : entry.handlers) if (t.items != null && t.items.contains(item)) {
                out.add(stack);
                break;
            }
        }
        return List.copyOf(out);
    }
}
