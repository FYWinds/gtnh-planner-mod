package com.gtnhplanner.ui.gt;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachineBronze;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;

/**
 * What kind of GregTech machine a catalyst stack is: a multiblock, or a single block of some tier. Only touch this
 * class when GregTech is loaded.
 */
public final class GtMachines {

    /**
     * {@code tier} is GregTech's index (0 = ULV, 1 = LV, ...) or -1 when unknown; {@code steam} for the steam line,
     * which runs on steam rather than EU: the bronze and steel single blocks, and the steam multiblocks.
     */
    public record Kind(boolean multiblock, int tier, boolean steam) {}

    /**
     * The website's isSteamMachineHandler: every steam machine is named "Steam ..." or "High Pressure ..." (in
     * English: {@link #englishName}, so the game's language changes nothing).
     */
    private static final java.util.regex.Pattern STEAM = java.util.regex.Pattern
        .compile("\\bsteam\\b|\\bhigh pressure\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    private GtMachines() {}

    public static Kind of(final ItemStack stack) {
        if (stack == null) return null;
        try {
            if (Block.getBlockFromItem(stack.getItem()) != GregTechAPI.sBlockMachines) return null;
            final int meta = stack.getItemDamage();
            if (meta < 0 || meta >= GregTechAPI.METATILEENTITIES.length) return null;
            final IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[meta];
            if (mte instanceof MTEMultiBlockBase) {
                final String english = englishName(stack);
                return new Kind(
                    true,
                    -1,
                    STEAM.matcher(english != null ? english : stack.getDisplayName())
                        .find());
            }
            if (mte instanceof final MTETieredMachineBlock tiered) return new Kind(false, tiered.mTier, mte instanceof MTEBasicMachineBronze);
        } catch (final LinkageError e) {
            return null;
        }
        return null;
    }

    /**
     * A GregTech machine's English name whatever the game's language (GregTech keeps its English names as the game's
     * fallback): the name the website's tables and the structure pictures go by. Null for anything else, or when
     * GregTech left it no English.
     */
    @Nullable
    public static String englishName(@Nullable final ItemStack stack) {
        if (stack == null) return null;
        try {
            if (Block.getBlockFromItem(stack.getItem()) != GregTechAPI.sBlockMachines) return null;
            final int meta = stack.getItemDamage();
            if (meta < 0 || meta >= GregTechAPI.METATILEENTITIES.length || GregTechAPI.METATILEENTITIES[meta] == null)
                return null;
            final String key = "gt.blockmachines." + GregTechAPI.METATILEENTITIES[meta].getMetaName() + ".name";
            final String english = StatCollector.translateToFallback(key);
            return english == null || english.isEmpty() || english.equals(key) ? null : english;
        } catch (final LinkageError e) {
            return null;
        }
    }
}
