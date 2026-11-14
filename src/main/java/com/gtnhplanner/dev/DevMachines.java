package com.gtnhplanner.dev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.gtnhplanner.ui.card.StructureArt;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * GregTech's machines as the game names them, for matching bundled pictures and checking machine lookups. Only loaded
 * when GregTech is (the endpoint catches the linkage error otherwise). Client thread only.
 */
final class DevMachines {

    private DevMachines() {}

    /** Every GregTech machine whose display name contains {@code query}, multiblocks only unless {@code all}. */
    static Map<String, Object> list(final String query, final boolean all, final boolean art) {
        final String q = query.toLowerCase(Locale.ROOT);
        final List<String> found = new ArrayList<>();
        for (int meta = 0; meta < GregTechAPI.METATILEENTITIES.length; meta++) {
            final IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[meta];
            if (mte == null || !all && !(mte instanceof MTEMultiBlockBase)) continue;
            final String name;
            try {
                final ItemStack stack = mte.getStackForm(1);
                name = stack == null ? "?" : stack.getDisplayName();
            } catch (final RuntimeException e) {
                continue;
            }
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT)
                .contains(q)) continue;
            String line = meta + " | "
                + name
                + " | "
                + mte.getClass()
                    .getSimpleName();
            if (art) {
                final StructureArt.Art picture = StructureArt.forMachine(name);
                line += " | " + (picture == null ? "-"
                    : picture.location()
                        .getResourcePath());
            }
            found.add(line);
        }
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", found.size());
        m.put("machines", found);
        return m;
    }

    /**
     * Which machines of this game the planner has no model for: every catalyst NEI lists on a GregTech recipe map
     * against that map's machine data, and the multiblocks that are on no map at all (generators and own-logic
     * machines, to be read by eye).
     */
    static Map<String, Object> audit() {
        final java.util.Set<Integer> onAMap = new java.util.HashSet<>();
        final List<String> unknownMaps = new ArrayList<>(), unmatched = new ArrayList<>(), plain = new ArrayList<>();
        for (final gregtech.api.recipe.RecipeMap<?> map : gregtech.api.recipe.RecipeMap.ALL_RECIPE_MAPS.values()) {
            final com.gtnhplanner.machines.web.HandlerData.MapEntry entry = com.gtnhplanner.machines.game.WebCards
                .entry(map.unlocalizedName);
            final List<String> catalysts = new ArrayList<>();
            for (final codechicken.nei.PositionedStack ps : codechicken.nei.recipe.RecipeCatalysts
                .getRecipeCatalysts(map.unlocalizedName)) {
                if (ps == null || ps.item == null) continue;
                final ItemStack stack = ps.item;
                final String item = com.gtnhplanner.importer.game.GameIds.itemId(stack);
                String name;
                try {
                    name = stack.getDisplayName();
                } catch (final RuntimeException e) {
                    name = "?";
                }
                final IMetaTileEntity mte = mte(stack);
                if (mte != null) onAMap.add(stack.getItemDamage());
                final String line = map.unlocalizedName + " | "
                    + item
                    + " | "
                    + name
                    + " | "
                    + (mte == null ? "-"
                        : mte.getClass()
                            .getName());
                catalysts.add(item + " " + name);
                if (entry == null) continue;
                final String how = match(entry, item, name);
                if (how == null) unmatched.add(line);
                else if (mte instanceof MTEMultiBlockBase
                    && com.gtnhplanner.machines.web.MachineTable.behaviour(how) == null) plain.add(line + " | " + how);
            }
            if (entry == null && !map.getAllRecipes()
                .isEmpty()) unknownMaps.add(
                    map.unlocalizedName + " ("
                        + map.getAllRecipes()
                            .size()
                        + ") "
                        + catalysts);
        }
        final List<String> offMaps = new ArrayList<>();
        for (int meta = 0; meta < GregTechAPI.METATILEENTITIES.length; meta++) {
            final IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[meta];
            if (!(mte instanceof MTEMultiBlockBase) || onAMap.contains(meta)) continue;
            String name;
            try {
                name = mte.getStackForm(1)
                    .getDisplayName();
            } catch (final RuntimeException e) {
                name = "?";
            }
            offMaps.add(
                meta + " | "
                    + name
                    + " | "
                    + mte.getClass()
                        .getName());
        }
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("unknownMaps", unknownMaps);
        m.put("unmatched", unmatched);
        m.put("multiblockWithoutTable", plain);
        m.put("multiblocksOnNoMap", offMaps);
        return m;
    }

    private static IMetaTileEntity mte(final ItemStack stack) {
        if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(GregTechAPI.sBlockMachines)) return null;
        final int meta = stack.getItemDamage();
        return meta >= 0 && meta < GregTechAPI.METATILEENTITIES.length ? GregTechAPI.METATILEENTITIES[meta] : null;
    }

    /** The label the card would run {@code item} on in this map (as WebCards.handlerId looks it up), or null. */
    private static String match(final com.gtnhplanner.machines.web.HandlerData.MapEntry entry, final String item,
        final String name) {
        if (entry.handlers != null) for (final com.gtnhplanner.machines.web.HandlerData.Template t : entry.handlers)
            if (t.items != null && t.items.contains(item)) return t.label;
        if (entry.app != null) for (final com.gtnhplanner.machines.web.HandlerData.AppHandler a : entry.app)
            if (a.items != null && a.items.contains(item)) return a.table != null ? a.table : a.label;
        final String wanted = com.gtnhplanner.machines.web.MachineTable.normalizeMachineName(name);
        if (entry.handlers != null) for (final com.gtnhplanner.machines.web.HandlerData.Template t : entry.handlers)
            if (com.gtnhplanner.machines.web.MachineTable.normalizeMachineName(t.label)
                .equals(wanted)) return t.label;
        if (com.gtnhplanner.machines.web.MachineTable.behaviour(name) != null) return name;
        return null;
    }
}
