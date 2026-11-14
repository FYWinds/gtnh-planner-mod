package com.gtnhplanner.power.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.sources.NrEssentiaSmeltery;

import goodgenerator.loader.Loaders;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/**
 * What the Large Essentia Smeltery smelts things to, from the running game: its getEssentia, Thaumcraft's object tags
 * and bonus tags of the item (a fluid's block's for a fluid), one Perditio for an item with none. Cached a thing.
 */
public final class NrEssentiaSmelteryGame implements NrEssentiaSmeltery.Data {

    /** The cells' names when the game has none: essentiaCell.0 to .3 in GoodGenerator's en_US.lang. */
    private static final String[] CELLS = { "Novice Essentia Diffusion Cell", "Adept Essentia Diffusion Cell",
        "Master Essentia Diffusion Cell", "Grandmaster Essentia Diffusion Cell" };

    private final List<Option> cells;
    private final Map<String, Optional<Map<String, Integer>>> aspects = new ConcurrentHashMap<>();

    private NrEssentiaSmelteryGame() {
        final List<Option> out = new ArrayList<>();
        for (int meta = 0; meta < CELLS.length; meta++) {
            String label = CELLS[meta];
            try {
                final String named = NrProcessGame.label(new ItemStack(Loaders.essentiaCell, 1, meta));
                if (named != null && !named.isEmpty()) label = named;
            } catch (final RuntimeException ignored) {}
            out.add(new Option(String.valueOf(meta), label));
        }
        cells = List.copyOf(out);
    }

    /** The smeltery's data; null when this game has no smeltery (GoodGenerator registers it only with Thaumcraft). */
    @Nullable
    public static NrEssentiaSmeltery.Data read() {
        return Loaders.LES == null ? null : new NrEssentiaSmelteryGame();
    }

    @Override
    public List<Option> cells() {
        return cells;
    }

    @Override
    @Nullable
    public Map<String, Integer> aspects(final String input) {
        return aspects.computeIfAbsent(input, k -> Optional.ofNullable(read(k)))
            .orElse(null);
    }

    @Nullable
    private static Map<String, Integer> read(final String input) {
        final ItemStack item = NrProcessGame.stack(input);
        if (item != null) return essentia(item);
        final Fluid fluid = NrProcessGame.fluid(input);
        if (fluid == null) return null;
        // getEssentia(FluidStack): the fluid's block as an item, nothing for a fluid with no block.
        final Block block = fluid.getBlock();
        if (block == null) return Map.of();
        try {
            final ItemStack stack = new ItemStack(block);
            return stack.getItem() == null ? Map.of() : essentia(stack);
        } catch (final RuntimeException | LinkageError e) {
            return Map.of();
        }
    }

    /** getEssentia(ItemStack, 1): its object tags and bonus tags, or one Perditio when it has none. */
    private static Map<String, Integer> essentia(final ItemStack stack) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        try {
            AspectList tags = ThaumcraftCraftingManager.getObjectTags(stack);
            tags = ThaumcraftCraftingManager.getBonusTags(stack, tags);
            if (tags != null && tags.size() != 0 && tags.getAspects()[0] != null) {
                for (final Aspect aspect : tags.getAspects())
                    if (aspect != null) out.merge(aspect.getName(), tags.getAmount(aspect), Integer::sum);
                return out;
            }
        } catch (final RuntimeException | LinkageError e) {
            GtnhPlanner.LOG.warn("[nr-process] could not read the aspects of {}", stack, e);
        }
        out.put(Aspect.ENTROPY.getName(), 1);
        return out;
    }
}
