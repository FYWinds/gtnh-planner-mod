package com.gtnhplanner.power.game;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.sources.NrOreFactory;
import com.gtnhplanner.power.sources.NrOreFactory.Kind;
import com.gtnhplanner.power.sources.NrOreFactory.Output;
import com.gtnhplanner.power.sources.NrOreFactory.Recipe;
import com.gtnhplanner.power.sources.NrOreFactory.Step;

import gregtech.api.enums.Materials;
import gregtech.api.recipe.FindRecipeQuery;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/**
 * The Integrated Ore Factory's ores and recipes from the running game: its item kinds built as its initHash builds
 * them (ore dictionary names by prefix, items by GTUtility.stackToInt), and each step's recipe found in the same recipe
 * map with the same query its checkProcessing makes. Lookups are cached.
 */
public final class NrOreFactoryGame implements NrOreFactory.Data {

    /** A stack big enough for any recipe's input count: the factory looks up its whole pile of an item at once. */
    private static final int PILE = 1 << 20;
    /** The hatches hold far more than a recipe asks: their fluids' amount in a lookup. */
    private static final int TANKS = Integer.MAX_VALUE;

    private final Map<Integer, Set<Kind>> kinds = new HashMap<>();
    private final List<Option> ores;
    private final List<Option> bathFluids;
    private final String distilledWater, lubricant;
    private final Fluid distilledWaterFluid, lubricantFluid;
    private final List<ItemStack> voidable;
    private final Map<String, Optional<Recipe>> recipes = new ConcurrentHashMap<>();

    private NrOreFactoryGame() {
        initKinds();
        distilledWaterFluid = GTModHandler.getDistilledWater(1L)
            .getFluid();
        lubricantFluid = Materials.Lubricant.getFluid(1L)
            .getFluid();
        distilledWater = NrProcessGame.fluidFlow(distilledWaterFluid);
        lubricant = NrProcessGame.fluidFlow(lubricantFluid);
        // VOIDABLE_STONE_DUSTS.
        voidable = new ArrayList<>();
        for (final Materials stone : List
            .of(Materials.Stone, Materials.Netherrack, Materials.Endstone, Materials.Marble, Materials.GraniteRed)) {
            final ItemStack dust = stone.getDust(1);
            if (dust != null) voidable.add(dust);
        }
        ores = oreOptions();
        bathFluids = bathOptions();
    }

    public static NrOreFactory.Data read() {
        return new NrOreFactoryGame();
    }

    /**
     * initHash: every ore dictionary name, by its prefix, the first that fits ("crushedPurified" before "crushed"); its
     * items keyed as the factory keys them.
     */
    private void initKinds() {
        for (final String name : OreDictionary.getOreNames()) {
            if (name == null || name.isEmpty()) continue;
            final Kind kind;
            if (name.startsWith("crushedPurified")) kind = Kind.PURIFIED;
            else if (name.startsWith("crushedCentrifuged")) kind = Kind.CENTRIFUGED;
            else if (name.startsWith("crushed")) kind = Kind.CRUSHED;
            else if (name.startsWith("dustImpure")) kind = Kind.IMPURE;
            else if (name.startsWith("dustPure")) kind = Kind.PURE;
            else if (name.startsWith("ore") || name.startsWith("rawOre")) kind = Kind.ORE;
            else continue;
            for (final ItemStack stack : OreDictionary.getOres(name))
                kinds.computeIfAbsent(GTUtility.stackToInt(stack), k -> EnumSet.noneOf(Kind.class))
                    .add(kind);
        }
    }

    /** The isOre set's items, by name; wildcard entries are left out, as no item in a bus has that meta. */
    private List<Option> oreOptions() {
        final Map<String, Option> out = new LinkedHashMap<>();
        for (final Map.Entry<Integer, Set<Kind>> entry : kinds.entrySet()) {
            if (!entry.getValue()
                .contains(Kind.ORE)) continue;
            final ItemStack stack = GTUtility.intToStack(entry.getKey());
            if (stack == null || stack.getItem() == null || stack.getItemDamage() == OreDictionary.WILDCARD_VALUE)
                continue;
            final String flow = NrProcessGame.itemFlow(stack);
            if (flow != null) out.putIfAbsent(flow, new Option(flow, NrProcessGame.label(stack)));
        }
        return NrProcessGame.sorted(out.values());
    }

    /** The fluids chemical bath recipes take with a crushed or purified ore: what the bath step can use. */
    private List<Option> bathOptions() {
        final Map<String, Option> out = new LinkedHashMap<>();
        for (final GTRecipe recipe : RecipeMaps.chemicalBathRecipes.getAllRecipes()) {
            if (recipe.mInputs == null || recipe.mInputs.length == 0
                || recipe.mFluidInputs == null
                || recipe.mFluidInputs.length == 0) continue;
            final Set<Kind> of = kinds.get(GTUtility.stackToInt(recipe.mInputs[0]));
            if (of == null || !of.contains(Kind.CRUSHED) && !of.contains(Kind.PURIFIED)) continue;
            final FluidStack fluid = recipe.mFluidInputs[0];
            if (fluid == null || fluid.getFluid() == null
                || fluid.getFluid() == distilledWaterFluid
                || fluid.getFluid() == lubricantFluid) continue;
            final String flow = NrProcessGame.fluidFlow(fluid.getFluid());
            if (flow != null) out.putIfAbsent(flow, new Option(flow, NrProcessGame.label(fluid.getFluid())));
        }
        return NrProcessGame.sorted(out.values());
    }

    @Override
    public List<Option> ores() {
        return ores;
    }

    @Override
    public List<Option> bathFluids() {
        return bathFluids;
    }

    @Override
    public String distilledWater() {
        return distilledWater;
    }

    @Override
    public String lubricant() {
        return lubricant;
    }

    @Override
    public Set<Kind> kinds(final String item) {
        final ItemStack stack = NrProcessGame.stack(item);
        final Set<Kind> of = stack == null ? null : kinds.get(GTUtility.stackToInt(stack));
        return of == null ? Set.of() : of;
    }

    @Override
    public boolean voidable(final String item) {
        final ItemStack stack = NrProcessGame.stack(item);
        if (stack == null) return false;
        for (final ItemStack dust : voidable) if (GTUtility.areStacksEqual(dust, stack)) return true;
        return false;
    }

    @Override
    @Nullable
    public Recipe recipe(final Step step, final String item, @Nullable final String bath) {
        final String key = step + "|" + item + "|" + (step == Step.BATH ? bath : "");
        return recipes.computeIfAbsent(key, k -> Optional.ofNullable(find(step, item, bath)))
            .orElse(null);
    }

    /** The query checkProcessing makes for one step, on the whole pile of an item. */
    @Nullable
    private Recipe find(final Step step, final String item, @Nullable final String bath) {
        final ItemStack one = NrProcessGame.stack(item);
        if (one == null) return null;
        final ItemStack pile = GTUtility.copyAmountUnsafe(PILE, one);
        try {
            final GTRecipe recipe = switch (step) {
                case MACERATE -> query(RecipeMaps.maceratorRecipes, false).items(pile)
                    .find();
                case WASH -> query(RecipeMaps.oreWasherRecipes, false).items(pile)
                    .fluids(GTModHandler.getDistilledWater(TANKS))
                    .find();
                case THERMAL -> query(RecipeMaps.thermalCentrifugeRecipes, false).items(pile)
                    .find();
                case CENTRIFUGE -> query(RecipeMaps.centrifugeRecipes, true).items(pile)
                    .find();
                case SIFT -> query(RecipeMaps.sifterRecipes, true).items(pile)
                    .find();
                case HAMMER -> query(RecipeMaps.hammerRecipes, false).items(pile)
                    .find();
                case SIMPLE_WASH -> query(RecipeMaps.simpleWasherRecipes, true).items(pile)
                    .fluids(Materials.Water.getFluid(100))
                    .find();
                // chemicalWash: the fluids in its hatches, distilled water, lubricant and the bath fluid.
                case BATH -> query(RecipeMaps.chemicalBathRecipes, true).items(pile)
                    .fluids(hatchFluids(bath))
                    .find();
            };
            return recipe == null ? null : recipe(step, recipe);
        } catch (final RuntimeException | LinkageError e) {
            GtnhPlanner.LOG.warn("[nr-process] the ore factory's {} lookup failed on {}", step, item, e);
            return null;
        }
    }

    /** A map's query: its own caching unless the factory turns it off, as each of its steps does. */
    private static FindRecipeQuery query(final RecipeMap<?> map, final boolean caching) {
        final FindRecipeQuery query = map.findRecipeQuery();
        return caching ? query : query.caching(false);
    }

    private FluidStack[] hatchFluids(@Nullable final String bath) {
        final List<FluidStack> fluids = new ArrayList<>();
        fluids.add(new FluidStack(distilledWaterFluid, TANKS));
        fluids.add(new FluidStack(lubricantFluid, TANKS));
        final Fluid fluid = bath == null ? null : NrProcessGame.fluid(bath);
        if (fluid != null) fluids.add(new FluidStack(fluid, TANKS));
        return fluids.toArray(new FluidStack[0]);
    }

    /** getOutputStack's view of a recipe: every output with its stack size and chance; the bath's fluid. */
    private static Recipe recipe(final Step step, final GTRecipe recipe) {
        final List<Output> outputs = new ArrayList<>();
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            final ItemStack template = recipe.getOutput(i);
            if (template == null) continue;
            final String flow = NrProcessGame.itemFlow(template);
            if (flow != null) outputs.add(new Output(flow, template.stackSize, recipe.getOutputChance(i)));
        }
        if (step != Step.BATH) return new Recipe(outputs, null, 0);
        final FluidStack fluid = recipe.getRepresentativeFluidInput(0);
        if (fluid == null || fluid.getFluid() == null) return new Recipe(outputs, null, 0);
        return new Recipe(outputs, NrProcessGame.fluidFlow(fluid.getFluid()), fluid.amount);
    }
}
