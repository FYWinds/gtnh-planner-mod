package com.gtnhplanner.power.game;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockStem;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSeedFood;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.fluids.FluidRegistry;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.sources.NrGreenhouse;
import com.gtnhplanner.power.sources.NrResources;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import kubatech.api.eig.EIGBucket;
import kubatech.api.eig.EIGDropTable;
import kubatech.api.enums.EIGModes;
import kubatech.tileentity.gregtech.multiblock.MTEExtremeIndustrialGreenhouse;

/**
 * The Extreme Industrial Greenhouse's seeds and drops from the running game. A seed's drops are its bucket's, made by
 * the greenhouse's own factories (EIGModes.Normal.tryCreateNewBucket, simulating, on a greenhouse of our own in the
 * player's world) and read with addProgress(1, table): EIGSeedBucket's 1,000 simulated harvests less the seed it
 * replants, EIGStemBucket's 100, a flower its own stack. As in the game the seed and stem tables are a sample, taken
 * once a seed (cached), so they match a greenhouse's to that sampling. The rainbow cactus draws dyes at random each
 * cycle; it gets its expected share (NrGreenhouse.rainbowDyesEach).
 */
public final class NrGreenhouseGame implements NrGreenhouse.Data {

    private static final String RAINBOW_BUCKET = "kubatech.tileentity.gregtech.multiblock.eigbuckets.EIGRainbowCactusBucket";

    private final List<Option> seeds;
    private final List<Option> fertilizers;
    private final String water, weedEx;
    private final Map<String, Optional<Map<String, Double>>> drops = new ConcurrentHashMap<>();
    @Nullable
    private MTEExtremeIndustrialGreenhouse greenhouse;

    private NrGreenhouseGame() {
        this.water = NrProcessGame.fluidFlow(FluidRegistry.WATER);
        // The greenhouse drains Materials.WeedEX9000.mFluid, which GregTech always makes.
        final String weedExFlow = NrProcessGame.fluidFlow(Materials.WeedEX9000.mFluid);
        this.weedEx = weedExFlow != null ? weedExFlow : NrResources.fluid("weedex9000");
        this.fertilizers = fertilizerOptions();
        this.seeds = seedOptions();
    }

    public static NrGreenhouse.Data read() {
        return new NrGreenhouseGame();
    }

    @Override
    public List<Option> seeds() {
        return seeds;
    }

    @Override
    public List<Option> fertilizers() {
        return fertilizers;
    }

    @Override
    public String water() {
        return water;
    }

    @Override
    public String weedEx() {
        return weedEx;
    }

    @Override
    @Nullable
    public Map<String, Double> drops(final String seed) {
        // Stems drop through the world they grow in: before the player is in one, nothing is kept.
        if (world() == null) return simulate(seed);
        return drops.computeIfAbsent(seed, s -> Optional.ofNullable(simulate(s)))
            .orElse(null);
    }

    // region seeds

    /**
     * Every item the greenhouse might take: what passes the factories' own tests of the item's kind (EIGSeedBucket:
     * ItemSeeds, ItemSeedFood or Hodgepodge's INetherSeed; EIGStemBucket: an IPlantable planting a BlockStem), and the
     * flowers, cacti and reeds its factories take outright (EIGFlowerBucket, EIGRainbowCactusBucket; cheap to try). A
     * seed or stem can still be refused once simulated: the card says so.
     */
    private List<Option> seedOptions() {
        final Class<?> netherSeed = hodgepodgeNetherSeed();
        final List<Option> out = new ArrayList<>();
        for (final Object name : Item.itemRegistry.getKeys()) {
            if (!(Item.itemRegistry.getObject(name) instanceof final Item item)) continue;
            try {
                final Block block = Block.getBlockFromItem(item);
                final boolean seedKind = item instanceof ItemSeeds || item instanceof ItemSeedFood
                    || netherSeed != null && netherSeed.isInstance(item)
                    || item instanceof final IPlantable plant && plant.getPlant(world(), 0, 0, 0) instanceof BlockStem;
                final boolean flowerKind = item == Items.reeds || block instanceof BlockFlower
                    || block instanceof BlockCactus;
                if (!seedKind && !flowerKind) continue;
                for (final ItemStack stack : NrProcessGame.subItems(item)) {
                    if (!seedKind && bucket(stack) == null) continue;
                    final String flow = NrProcessGame.itemFlow(stack);
                    if (flow != null) out.add(new Option(flow, NrProcessGame.label(stack)));
                }
            } catch (final RuntimeException | LinkageError e) {
                // An item that fails to say what it plants is one the greenhouse could not take either.
            }
        }
        return NrProcessGame.sorted(dedupe(out));
    }

    private static Collection<Option> dedupe(final List<Option> options) {
        final Map<String, Option> byKey = new LinkedHashMap<>();
        for (final Option option : options) byKey.putIfAbsent(option.key(), option);
        return byKey.values();
    }

    /**
     * Hodgepodge's INetherSeed (nether wart), the first kind EIGSeedBucket.recalculateDrops plants; null without it.
     */
    @Nullable
    private static Class<?> hodgepodgeNetherSeed() {
        try {
            return Class.forName("com.mitchej123.hodgepodge.mixins.interfaces.INetherSeed");
        } catch (final ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** MTEExtremeIndustrialGreenhouse.FERTILIZER_ITEM_LIST: bone meal, and Forestry's fertilizer with Forestry. */
    @SuppressWarnings("unchecked")
    private static List<Option> fertilizerOptions() {
        final List<Option> out = new ArrayList<>();
        try {
            final Field field = MTEExtremeIndustrialGreenhouse.class.getDeclaredField("FERTILIZER_ITEM_LIST");
            field.setAccessible(true);
            for (final ItemStack stack : (List<ItemStack>) field.get(null)) {
                final String flow = NrProcessGame.itemFlow(stack);
                if (flow != null) out.add(new Option(flow, NrProcessGame.label(stack)));
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            GtnhPlanner.LOG.warn("[nr-process] could not read the greenhouse's fertilizers", e);
        }
        return out;
    }

    // endregion

    // region drops

    /** One seed's drops a cycle at no fertilizer; null when the greenhouse refuses it. */
    @Nullable
    private Map<String, Double> simulate(final String seed) {
        final ItemStack stack = NrProcessGame.stack(seed);
        if (stack == null) return null;
        try {
            final EIGBucket bucket = bucket(stack);
            if (bucket == null) return null;
            if (RAINBOW_BUCKET.equals(
                bucket.getClass()
                    .getName()))
                return rainbowDyes();
            final EIGDropTable table = new EIGDropTable();
            bucket.addProgress(1.0, table);
            final Map<String, Double> out = new LinkedHashMap<>();
            for (final Map.Entry<ItemStack, Double> drop : table.entrySet()) {
                final String flow = NrProcessGame.itemFlow(drop.getKey());
                if (flow != null && drop.getValue() > 0) out.merge(flow, drop.getValue(), Double::sum);
            }
            return out;
        } catch (final RuntimeException | LinkageError e) {
            GtnhPlanner.LOG.warn("[nr-process] the greenhouse failed on {}", seed, e);
            return null;
        }
    }

    /** The bucket the greenhouse makes of one seed, simulating (nothing is used up); null when it refuses it. */
    @Nullable
    private EIGBucket bucket(final ItemStack stack) {
        final ItemStack one = stack.copy();
        one.stackSize = 1;
        return EIGModes.Normal.tryCreateNewBucket(greenhouse(), one, 1, true);
    }

    /** Thaumic Bases' rainbow cactus: its expected dyes a cycle, an equal share of each of its allowedDyes. */
    @Nullable
    private static Map<String, Double> rainbowDyes() {
        try {
            final ItemStack[] dyes = (ItemStack[]) Class.forName("tb.common.block.BlockRainbowCactus")
                .getField("allowedDyes")
                .get(null);
            final Map<String, Double> out = new LinkedHashMap<>();
            for (final ItemStack dye : dyes) {
                final String flow = NrProcessGame.itemFlow(dye);
                if (flow != null) out.merge(flow, NrGreenhouse.rainbowDyesEach(dyes.length), Double::sum);
            }
            return out;
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            GtnhPlanner.LOG.warn("[nr-process] could not read the rainbow cactus's dyes", e);
            return null;
        }
    }

    /**
     * A greenhouse of our own for the factories (EIGStemBucket and EIGSeedBucket read its world and place): a fresh
     * controller on a fresh base tile, in the world the player is in.
     */
    private MTEExtremeIndustrialGreenhouse greenhouse() {
        if (greenhouse == null) {
            final MTEExtremeIndustrialGreenhouse made = new MTEExtremeIndustrialGreenhouse(
                "multimachine.extremegreenhouse");
            made.setBaseMetaTileEntity(GregTechAPI.constructBaseMetaTileEntity());
            greenhouse = made;
        }
        ((BaseMetaTileEntity) greenhouse.getBaseMetaTileEntity()).setWorldObj(world());
        return greenhouse;
    }

    @Nullable
    private static World world() {
        return Minecraft.getMinecraft().theWorld;
    }

    // endregion
}
