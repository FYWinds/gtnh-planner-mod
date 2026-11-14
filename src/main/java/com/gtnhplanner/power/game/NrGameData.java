package com.gtnhplanner.power.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.power.sources.NrData;
import com.gtnhplanner.power.sources.NrResources;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.util.GTUtility;
import gtneioreplugin.util.DimensionHelper;
import gtnhintergalactic.recipe.GasSiphonRecipes;
import gtnhintergalactic.recipe.SpacePumpingRecipes;

/**
 * The running game's tables for the resource multiblocks (NrSources): every Void Miner drop table (bartworks fills
 * VoidMinerUtility's from the ore veins, small ores and the Deep Dark weights in the pack's config when loading
 * completes), the miners' noble gases, the siphon's planets and the pump modules' recipes. Loaded only where
 * GregTech's classes are (NrSources checks first); each part that fails to read is left out on its own.
 */
public final class NrGameData {

    public static NrData read() {
        return new NrData(
            guard("Void Miner drop tables", NrGameData::dimensions),
            guard("Void Miner noble gases", NrGameData::nobleGases),
            guard("Planetary Gas Siphon recipes", NrGameData::siphonPlanets),
            guard("Space Pumping Module recipes", NrGameData::pumpRecipes));
    }

    private static <T> List<T> guard(final String what, final Supplier<List<T>> read) {
        try {
            return read.get();
        } catch (final RuntimeException | LinkageError e) {
            GtnhPlanner.LOG
                .warn("[nr-sources] could not read the {}; the machines that need them are left out", what, e);
            return List.of();
        }
    }

    // region Void Miners

    private record Found(NrData.Dimension dimension, int tier, int order) {}

    /**
     * MTEVoidMinerBase.calculateDropMap: a miner works where its world has a galacticgreg DimensionDef that can be
     * void mined (DimensionDef.getDefForWorld), on dropMapsByDimName for that name with extraDropsByDimName merged in
     * (DropMap.isDistributionCached, which merges them into the first map the first time a miner runs, after which
     * getOres is set). Ores of no weight are never drawn and left out; a dimension with none is too.
     */
    private static List<NrData.Dimension> dimensions() {
        final Map<String, String> english = englishNames();
        final List<Found> found = new ArrayList<>();
        for (final DimensionDef def : DimensionDef.VALUES) {
            // The End's outer islands are a galacticgreg dimension for worldgen only: a miner there is in "The End".
            if (def == DimensionDef.EndAsteroids) continue;
            final ModDimensionDef mod = def.modDimensionDef;
            if (!mod.canBeVoidMined()) continue;
            final String name = mod.getDimensionName();
            final Map<GTUtility.ItemId, Float> weights = new LinkedHashMap<>();
            final VoidMinerUtility.DropMap drops = VoidMinerUtility.dropMapsByDimName.get(name);
            final VoidMinerUtility.DropMap extra = VoidMinerUtility.extraDropsByDimName.get(name);
            if (drops != null) addAll(weights, drops.getInternalMap());
            if (extra != null && (drops == null || drops.getOres() == null)) addAll(weights, extra.getInternalMap());
            final List<NrData.Ore> ores = new ArrayList<>();
            for (final Map.Entry<GTUtility.ItemId, Float> entry : weights.entrySet()) {
                if (!(entry.getValue() > 0)) continue;
                final NrData.Ore ore = ore(entry.getKey(), entry.getValue());
                if (ore != null) ores.add(ore);
            }
            if (ores.isEmpty()) continue;
            ores.sort(
                Comparator.comparingDouble((NrData.Ore ore) -> -ore.weight())
                    .thenComparing(NrData.Ore::label));
            final String full = DimensionHelper.INTERNAL_TO_FULL.get(name);
            final int tier = full == null ? Integer.MAX_VALUE : tier(DimensionHelper.getDimTier(full));
            final int order = full == null ? Integer.MAX_VALUE : DimensionHelper.ALL_DIM_NAMES.indexOf(full);
            final String label = english.getOrDefault(DimensionHelper.getDimUnlocalizedName(name), name);
            found.add(
                new Found(
                    new NrData.Dimension(name, tier == Integer.MAX_VALUE ? label : "T" + tier + " " + label, ores),
                    tier,
                    order));
        }
        // In progression order, as the ore plugin lists them.
        found.sort(
            Comparator.comparingInt(Found::tier)
                .thenComparingInt(Found::order));
        final List<NrData.Dimension> out = new ArrayList<>();
        for (final Found f : found) out.add(f.dimension());
        return out;
    }

    private static void addAll(final Map<GTUtility.ItemId, Float> into, final Map<GTUtility.ItemId, Float> from) {
        for (final Map.Entry<GTUtility.ItemId, Float> entry : from.entrySet())
            into.merge(entry.getKey(), entry.getValue(), Float::sum);
    }

    /** A drop as handleOutputs makes it: ItemId.getItemStack, the item at its meta with no tag. */
    @Nullable
    private static NrData.Ore ore(final GTUtility.ItemId id, final float weight) {
        final Item item = id.item();
        final Object name = item == null ? null : Item.itemRegistry.getNameForObject(item);
        if (name == null) return null;
        final int meta = id.metaData();
        final String flow = NrResources.item(name + (meta == 0 ? "" : "@" + meta));
        final ItemStack stack = id.getItemStack();
        final String label = stack == null ? String.valueOf(name) : stack.getDisplayName();
        NrResources.name(flow, label);
        return new NrData.Ore(flow, label, weight);
    }

    /** "gtnop.tier.3" as 3 (DimensionHelper.getDimTier). */
    private static int tier(final String key) {
        try {
            return Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
        } catch (final RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * The ore plugin's English dimension names (its en_US.lang, {@code gtnop.world.*}): the planner keeps them English
     * and translates them where drawn, since the pack's Chinese file names few of them under the keys the game asks.
     */
    private static Map<String, String> englishNames() {
        final Map<String, String> out = new HashMap<>();
        try (InputStream in = DimensionHelper.class.getResourceAsStream("/assets/gtneioreplugin/lang/en_US.lang")) {
            if (in == null) return out;
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                final int eq = line.indexOf('=');
                if (eq > 0 && line.startsWith("gtnop.world.")) out.put(
                    line.substring(0, eq),
                    line.substring(eq + 1)
                        .trim());
            }
        } catch (final IOException e) {
            GtnhPlanner.LOG.warn("[nr-sources] could not read the dimension names", e);
        }
        return out;
    }

    /** VoidMinerUtility.NOBLE_GASSES and NOBLE_GASSES_MULTIPLIER, in their order. */
    private static List<NrData.NobleGas> nobleGases() {
        final List<NrData.NobleGas> out = new ArrayList<>();
        final FluidStack[] gases = VoidMinerUtility.NOBLE_GASSES;
        for (int i = 0; i < gases.length; i++) {
            final FluidStack gas = gases[i];
            if (gas == null || gas.getFluid() == null) continue;
            final String id = gas.getFluid()
                .getName();
            final String flow = NrResources.fluid(id);
            final String label = gas.getLocalizedName();
            NrResources.name(flow, label);
            out.add(new NrData.NobleGas(id, label, flow, VoidMinerUtility.NOBLE_GASSES_MULTIPLIER[i]));
        }
        return out;
    }

    // endregion

    // region Pumps

    /** GasSiphonRecipes.RECIPES, by tier then name; each planet's gases by depth. */
    private static List<NrData.SiphonPlanet> siphonPlanets() {
        final List<NrData.SiphonPlanet> out = new ArrayList<>();
        for (final GasSiphonRecipes.GasSiphonRecipe recipe : GasSiphonRecipes.RECIPES.values()) {
            final List<NrData.SiphonGas> gases = new ArrayList<>();
            for (final Map.Entry<Integer, FluidStack> depth : recipe.depths.entrySet()) {
                final FluidStack fluid = depth.getValue();
                if (fluid == null || fluid.getFluid() == null) continue;
                final String flow = fluidFlow(fluid);
                gases.add(new NrData.SiphonGas(depth.getKey(), flow, fluid.getLocalizedName(), fluid.amount));
            }
            if (gases.isEmpty()) continue;
            gases.sort(Comparator.comparingInt(NrData.SiphonGas::depth));
            out.add(new NrData.SiphonPlanet(recipe.planet, planetName(recipe.planet), recipe.tier, gases));
        }
        out.sort(
            Comparator.comparingInt(NrData.SiphonPlanet::tier)
                .thenComparing(NrData.SiphonPlanet::key));
        return out;
    }

    /** "planet.jupiter" as "Jupiter": Galacticraft's unlocalised planet name, English. */
    private static String planetName(final String key) {
        final String name = key.startsWith("planet.") ? key.substring("planet.".length()) : key;
        return name.isEmpty() ? key : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** SpacePumpingRecipes.RECIPES, by planet type then gas type, labelled "Fluid (planet, gas)". */
    private static List<NrData.PumpRecipe> pumpRecipes() {
        final List<NrData.PumpRecipe> out = new ArrayList<>();
        for (final Map.Entry<Pair<Integer, Integer>, FluidStack> entry : SpacePumpingRecipes.RECIPES.entrySet()) {
            final FluidStack fluid = entry.getValue();
            if (fluid == null || fluid.getFluid() == null) continue;
            final int planet = entry.getKey()
                .getLeft(),
                gas = entry.getKey()
                    .getRight();
            out.add(
                new NrData.PumpRecipe(
                    planet,
                    gas,
                    fluidFlow(fluid),
                    fluid.getLocalizedName() + " (" + planet + ", " + gas + ")",
                    fluid.amount));
        }
        out.sort(
            Comparator.comparingInt(NrData.PumpRecipe::planetType)
                .thenComparingInt(NrData.PumpRecipe::gasType));
        return out;
    }

    private static String fluidFlow(final FluidStack fluid) {
        final String flow = NrResources.fluid(
            fluid.getFluid()
                .getName());
        NrResources.name(flow, fluid.getLocalizedName());
        return flow;
    }

    // endregion

    private NrGameData() {}
}
