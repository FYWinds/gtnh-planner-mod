package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.MAP_CONTROLS;
import static com.gtnhplanner.machines.eto.TstControls.choice;
import static com.gtnhplanner.machines.eto.TstControls.count;
import static com.gtnhplanner.machines.eto.TstMachinesVisual.number;
import static com.gtnhplanner.machines.eto.TstMachinesVisual.withDefault;
import static com.gtnhplanner.machines.web.MachineTable.machine;
import static com.gtnhplanner.machines.web.MachineTable.none;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.gtnhplanner.machines.web.MachineTable.Behaviour;
import com.gtnhplanner.machines.web.MachineTable.Context;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's Eco-Sphere Simulator (19051) on cards made from its four fake maps (docs/design/
 * tst-machines.md, "Machines with their own recipe pages"). The maps are NEI-only (every recipe {@code .fake()}, 0
 * EU/t, 100 ticks): {@code TST_EcoSphereSimulator.process} (:1096-1180) looks no recipe up, the execution protocol's
 * mode works the run out. Paths are under {@code common/machine/EcoSphere/} unless named; lines are 0.8.1's.
 * <ul>
 * <li>Every mode: power tier t = (int) calculateVoltageTier(hatches' V x A) (:1112), refused below 1 (:1114); EU/t
 * calculateEut(t) = 30 x 4^(t-1) for the whole run ({@code EcoSphereModeSupport} :44-47,
 * {@code EcoSphereModeResult} :23-30); 100 ticks (:452), 20 with a Speed upgrade ({@code EcoSphereUpgradeResult} :53).
 * Power-limited parallels P (:49-54): 1 at t &le; 1, 4^(t-1) on the Tier 2 structure (:35-42), the Tier 1 curve
 * (:29-33) otherwise; the card has the medium it needs, so the fluid never limits P. A medium per parallel halves
 * (integer, never under 1) once per Fluid Reduction or Speed upgrade ({@code EcoSphereUpgradeResult} :41-46). Each
 * output stack: x 1.5 per Output, Perfect Genetics and Output Pulverization upgrade and one draw of (0.85 + 0.15U),
 * truncated, at least 1 (:73-90, :112-123): the card has the draw's expected value, worked exactly.</li>
 * <li>Arboreal Genesis ({@code TreeGrowthSimulatorMode} :55-171): a parallel is P x saplings; an output is (long)
 * (product x the category multiplier 20/3/8/1 x P x saplings x focus bonus) for the selected categories (:136-149).
 * The page's outputs already carry the multiplier ({@code TreeGrowthSimulatorWithoutToolFakeRecipe} :225-233). The
 * special media (temporal fluid, death water, unknown water) make the special tree's products from any sapling; UU
 * matter ignores the saplings and picks one product a category, the multiplier applied twice (:84-87, :196-206).</li>
 * <li>Aquatic Simulation ({@code AquaticZoneSimulatorMode} :58-206): a page is one member of a pool, its stack size
 * the weight; chance = floor(100000 / pool weight) x weight ({@code AquaticZoneSimulatorFakeRecipe} :159-167), the
 * focus multiplying or dividing it (:136-142, :162-168); a run rolls r in [0, 100000) and makes (long) (weight x P x
 * chance x r / 1e8) when r &le; chance x log2(t + 2) (:198-206).</li>
 * <li>Artificial Greenhouse ({@code ArtificialGreenHouseMode} :54-106, {@code Handler/CropsNHFarm}): a parallel is P x
 * seeds; an output is ceil(yield x P x seeds x 40), the yield CropsNH's growth chain (:168-216), x 0.25 for an
 * alternate seed (:111-161), the simulated drops for any other plantable (:220-324).</li>
 * <li>Directed Mob Cloning ({@code DirectedMobClonerMode} :60-197): the address-0 page (blood to Life Essence) is the
 * whole run (:64-73, :330-356, scaled in 5 L steps); a mob page's ports are a soul vial (display only) and the mob's
 * first drop, whose MobsInfo chance is a setting (:104-118).</li>
 * </ul>
 * Pure: no game classes.
 */
final class TstVisualEcoSphere {

    private TstVisualEcoSphere() {}

    /** The handler label every Eco-Sphere map gives the controller. */
    static final String NAME = "Eco-Sphere Simulator";

    /** The maps, by the name the data gives them (Context.recipeMap). */
    static final String ARBOREAL = "Eco-Sphere: Arboreal Genesis", AQUATIC = "Eco-Sphere: Aquatic Simulation",
        GREENHOUSE = "Eco-Sphere: Artificial Greenhouse", CLONING = "Eco-Sphere: Directed Mob Cloning";

    /** The media, by fluid id. */
    static final String TEMPORAL_FLUID = "temporalfluid", DEATH_WATER = "fluiddeath", UNKNOWN_WATER = "unknowwater",
        UU_MATTER = "ic2uumatter", BLOOD = "blood";

    // region TST's numbers

    /** MODE_RECIPE_DURATION (TST_EcoSphereSimulator :452); a Speed upgrade's run (EcoSphereUpgradeResult :53). */
    static final int RUN_TICKS = 100, SPEED_TICKS = 20;
    /** TST_EcoSphereUpgradeInterfaceHatch.MAX_UPGRADE_SLOTS (:46). */
    static final int MAX_UPGRADE_SLOTS = 9;
    /** TreeGrowthSimulatorMode.getModeMultiplier (:212-219): LOG, SAPLING, LEAVES, FRUIT. */
    static final int[] TREE_MULTIPLIERS = { 20, 3, 8, 1 };
    /** AquaticZoneSimulatorFakeRecipe.CHANCE_SCALE (:33); the amount scale (AquaticZoneSimulatorMode :201-203). */
    static final int CHANCE_SCALE = 100_000;
    static final double AMOUNT_SCALE = (double) CHANCE_SCALE * CHANCE_SCALE / 100;
    /**
     * The pools' total weights (AquaticZoneSimulatorFakeRecipe :97-148, :159-167): distilled water 156 (lily pad 1,
     * vine 3, bone 3, ink sac 2, five BoP lilies 1, four BoP corals 2, Pam's cranberry 1, rice 1, seaweed 5, water
     * chestnut 2, the Twilight Forest's huge lily pad 1, the 31 aquatic items of :45-79 4 each; all present: the pack's
     * map has its 49 pages), no empty weight; unknown water 72 (six T-Ceti E dandelions 3, the five algae biomasses
     * 6/16/13/10/9) and 18 empty.
     */
    static final int DISTILLED_POOL_WEIGHT = 156, UNKNOWN_POOL_WEIGHT = 72 + 18;
    /** BoP's lilyBop meta 3, listed twice (:113-114): two pages, two rolls a run into one stack. */
    static final String DUPLICATED_LILY = "biomesoplenty:lilybop@3";
    /** An Offspring focuses 41 times an ordinary target (TST_EcoSphereInputInterfaceHatch :277-278). */
    static final int OFFSPRING_FOCUS_WEIGHT = 41;
    /** ArtificialGreenHouseMode.OUTPUT_SCALE (:40), PERFECT_SEED_STAT (:41). */
    static final double GREENHOUSE_OUTPUT_SCALE = 40.0d;
    static final int PERFECT_SEED_STAT = 31;
    /** CropsNHFarm (:47-50, :91): NORMAL_SEED_EFFICIENCY, SIMULATED_NUTRIENT_SCORE, NORMAL_SEED_GROWTH_DURATION. */
    static final double NORMAL_SEED_EFFICIENCY = 0.25d;
    static final int SIMULATED_NUTRIENT_SCORE = 220, NORMAL_SEED_GROWTH_DURATION = 1200, PLAIN_FERTILIZER = 20;
    /** LP_NETWORK_CONVERSION_DIVISOR (DirectedMobClonerMode :42); address 0's 100 L a parallel (fake recipe :26). */
    static final int LP_DIVISOR = 5, LIFE_ESSENCE_PER_PARALLEL = 100;
    /** DirectedMobClonerMode :38-40, :93-94: 5000 a Looting level, capped at X. */
    static final int LOOTING_CAP = 10;
    static final double LOOTING_CHANCE = 5_000d;
    /** DirectedMobClonerRecipeCache.EEC_MIN_DURATION, EEC_DIAMOND_SPIKES_DAMAGE (:43-44). */
    static final int EEC_MIN_DURATION = 55;
    static final double EEC_DIAMOND_SPIKES_DAMAGE = 9d;

    // endregion

    // region CropsNH 2.0.140's numbers

    /** MTEIndustrialFarm.CYCLE_TICK_RATE_SCALAR (:119, :158): CYCLE_DURATION 100 / TICK_RATE 256. */
    static final double CYCLE_TICK_RATE_SCALAR = 100d / 256;
    /** TileEntityCropSticks: BASE_GROWTH_SPEED, NUTRIENT_POINT_SCALE, NUTRIENTS_NEEDED_PER_TIER. */
    static final int BASE_GROWTH_SPEED = 6, NUTRIENT_POINT_SCALE = 5, NUTRIENTS_NEEDED_PER_TIER = 10;
    /** NHCropCard.getGrowthDuration: 600 x tier, unless the crop overrides it. */
    static final int BASE_GROWTH_DURATION_PER_TIER = 600;

    // endregion

    // region Settings

    static final String STRUCTURE = "tstEcoStructureTier", GLASS = "tstEcoGlassTier",
        FLUID_REDUCTION = "tstEcoFluidReduction", CAPACITY = "tstEcoCapacity", OUTPUT = "tstEcoOutput",
        SPEED = "tstEcoSpeed", PERFECT_GENETICS = "tstEcoPerfectGenetics", BLOOD_ORB = "tstEcoBloodOrb",
        PULVERIZATION = "tstEcoPulverization", SAPLINGS = "tstEcoSaplings", LOGS = "tstEcoLogs",
        SAPLING_OUTPUT = "tstEcoSaplingOutput", LEAVES = "tstEcoLeaves", FRUIT = "tstEcoFruit", FOCUS = "tstEcoFocus",
        FOCUS_ITEMS = "tstEcoFocusItems", SEEDS = "tstEcoSeeds", SEED_KIND = "tstEcoSeedKind",
        CROP_TIER = "tstEcoCropTier", GROWTH_DURATION = "tstEcoGrowthDuration", DROP_CHANCE = "tstEcoDropChance",
        GROWTH = "tstEcoGrowth", GAIN = "tstEcoGain", FIRST_DROP_CHANCE = "tstEcoFirstDropChance",
        LOOTING = "tstEcoLooting";

    /** controllerTier + 1 (:538-544): a Fount of Ecology makes it Tier 2, a structure of its own. */
    static final Web.Control STRUCTURE_CONTROL = choice(STRUCTURE, "Structure Tier", "Tier 1", "Tier 2");
    /** bw_realglas metas 4 to 7, glass tiers 1 to 4 (:263-280): only the Upgrade Interface's slots depend on it. */
    static final Web.Control GLASS_CONTROL = choice(GLASS, "Glass Tier", "ZPM", "UV", "UHV", "UEV");
    static final Web.Control FLUID_REDUCTION_CONTROL = count(
        FLUID_REDUCTION,
        "Fluid Reduction Upgrades",
        0,
        MAX_UPGRADE_SLOTS);
    static final Web.Control CAPACITY_CONTROL = count(CAPACITY, "Capacity Upgrades", 0, MAX_UPGRADE_SLOTS);
    static final Web.Control OUTPUT_CONTROL = count(OUTPUT, "Output Upgrades", 0, MAX_UPGRADE_SLOTS);
    static final Web.Control SPEED_CONTROL = count(SPEED, "Speed Upgrades", 0, MAX_UPGRADE_SLOTS);
    /** The special upgrades fit once each (TST_EcoSphereUpgradeInterfaceHatch :166-175). */
    static final Web.Control PERFECT_GENETICS_CONTROL = choice(
        PERFECT_GENETICS,
        "Perfect Genetics Upgrade",
        "No",
        "Yes");
    static final Web.Control BLOOD_ORB_CONTROL = choice(BLOOD_ORB, "Blood Orb Upgrade", "No", "Yes");
    static final Web.Control PULVERIZATION_CONTROL = choice(PULVERIZATION, "Output Pulverization Upgrade", "No", "Yes");

    /** The saplings counted: each kind's largest stack (TreeGrowthSimulatorMode :58-66, :94). */
    static final Web.Control SAPLINGS_CONTROL = count(SAPLINGS, "Sapling Count", 1, 256);
    /** The Input Interface's category buttons (:91, :406-425); the game starts with none, the card with all. */
    static final Web.Control LOGS_CONTROL = choice(LOGS, "Logs", "Yes", "No"),
        SAPLING_OUTPUT_CONTROL = choice(SAPLING_OUTPUT, "Saplings", "Yes", "No"),
        LEAVES_CONTROL = choice(LEAVES, "Leaves", "Yes", "No"), FRUIT_CONTROL = choice(FRUIT, "Fruit", "Yes", "No");
    private static final String[] TREE_SELECTIONS = { LOGS, SAPLING_OUTPUT, LEAVES, FRUIT };

    /** What the Input Interface holds in Aquatic Simulation (findTargetingSelection :208-224). */
    static final Web.Control FOCUS_CONTROL = choice(FOCUS, "Focus", "None", "This Item", "Another Item", "Offspring");
    static final Web.Control FOCUS_ITEMS_CONTROL = count(FOCUS_ITEMS, "Focus Items", 1, 256);

    /** The seeds counted: the crop's largest stack (ArtificialGreenHouseMode :108-139). */
    static final Web.Control SEEDS_CONTROL = count(SEEDS, "Seed Count", 1, 64);
    static final List<String> SEED_KINDS = List.of("Analyzed Hybrid Seed", "Alternate Seed", "Other Plantable");
    /** ICropCard.getDropChance: CropCard's (float) 0.95^tier, or one of the nine overrides in CropsNH 2.0.140. */
    static final Web.Control DROP_CHANCE_CONTROL = choice(
        DROP_CHANCE,
        "Drop Chance",
        "0.95^Tier",
        "0.95^Tier x 0.75",
        "0.95^Tier x 0.5",
        "0.5",
        "0.8",
        "0.85",
        "1.5",
        "2");
    /** A seed's stats, 1 to 31 (CropsNH Constants.MAX_SEED_STAT); the page's seed is analyzed 1/1/1. */
    static final Web.Control GROWTH_CONTROL = count(GROWTH, "Growth", 1, PERFECT_SEED_STAT),
        GAIN_CONTROL = count(GAIN, "Gain", 1, PERFECT_SEED_STAT);

    /** The first drop's MobsInfo chance, in percent as MobsInfo's NEI page shows it. */
    static final Web.Control FIRST_DROP_CHANCE_CONTROL = percent(FIRST_DROP_CHANCE, "First Drop Chance (%)", 100);
    /** A weapon's Looting level; the Tier III protocol or an Infinity Sword is X. */
    static final Web.Control LOOTING_CONTROL = count(LOOTING, "Looting", 0, LOOTING_CAP);

    private static final List<Web.Control> COMMON = List
        .of(STRUCTURE_CONTROL, GLASS_CONTROL, FLUID_REDUCTION_CONTROL, CAPACITY_CONTROL, OUTPUT_CONTROL, SPEED_CONTROL);

    // endregion

    /** The gate messages, English data. */
    static final String POWER_GATE = "Needs at least 1 A of LV (32 EU/t) of hatches.",
        UPGRADE_GATE = "More upgrades than the Upgrade Interface has slots for this glass and structure tier.",
        INPUT_GATE = "More items than the Input Interface holds: add Capacity Upgrades.",
        TREE_SELECTION_GATE = "No arboreal output category selected",
        CLONING_GATE = "Tier I Structure: Initial Biological Address Only";

    static void register(final Map<String, Behaviour> machines) {
        machines.put(
            NAME,
            machine().overclock(none())
                .fixedTicks(c -> ticks(c))
                .fixedEut(TstVisualEcoSphere::eutPerParallel)
                .parallels(TstVisualEcoSphere::parallels)
                .inputMultiplier(TstVisualEcoSphere::inputMultiplier)
                .outputMultiplier(TstVisualEcoSphere::outputMultiplier)
                .recipeGate(TstVisualEcoSphere::gate)
                .controlsFor(TstVisualEcoSphere::controls)
                .hidesControls(MAP_CONTROLS));
    }

    // region The card

    /** Each map's settings; a greenhouse page's defaults follow its fertilizer. */
    static List<Web.Control> controls(final Web.Recipe recipe) {
        final String map = recipe.source != null && recipe.source.recipeMap != null ? recipe.source.recipeMap
            : recipe.machineType;
        final String fluid = fluidId(recipe);
        if (ARBOREAL.equals(map)) return UU_MATTER.equals(fluid) ? ARBOREAL_UU_CONTROLS : ARBOREAL_CONTROLS;
        if (AQUATIC.equals(map)) return AQUATIC_CONTROLS;
        if (GREENHOUSE.equals(map)) {
            final Web.Resource fertilizer = fluidInput(recipe);
            return GREENHOUSE_CONTROLS.computeIfAbsent(
                fertilizer != null ? (int) fertilizer.amount : 0,
                TstVisualEcoSphere::greenhouseControls);
        }
        if (CLONING.equals(map)) return BLOOD.equals(fluid) ? CLONING_ZERO_CONTROLS : CLONING_MOB_CONTROLS;
        return COMMON;
    }

    private static final List<Web.Control> ARBOREAL_CONTROLS = with(
        PERFECT_GENETICS_CONTROL,
        SAPLINGS_CONTROL,
        LOGS_CONTROL,
        SAPLING_OUTPUT_CONTROL,
        LEAVES_CONTROL,
        FRUIT_CONTROL),
        ARBOREAL_UU_CONTROLS = with(
            PERFECT_GENETICS_CONTROL,
            LOGS_CONTROL,
            SAPLING_OUTPUT_CONTROL,
            LEAVES_CONTROL,
            FRUIT_CONTROL),
        AQUATIC_CONTROLS = with(FOCUS_CONTROL, FOCUS_ITEMS_CONTROL),
        CLONING_ZERO_CONTROLS = with(BLOOD_ORB_CONTROL, PULVERIZATION_CONTROL),
        CLONING_MOB_CONTROLS = with(
            BLOOD_ORB_CONTROL,
            PULVERIZATION_CONTROL,
            FIRST_DROP_CHANCE_CONTROL,
            LOOTING_CONTROL);
    private static final Map<Integer, List<Web.Control>> GREENHOUSE_CONTROLS = new ConcurrentHashMap<>();

    /**
     * A page's fertilizer is getFertilizerCost (CropsNHFarm :88-95): 20 without CropsNH seed data (alternate seeds and
     * other plantables), else max(1, ceil(2 x tier x ln(growth duration))). The defaults: a hybrid seed of the tier
     * whose base growth duration (600 x tier) costs that much, else another plantable.
     */
    private static List<Web.Control> greenhouseControls(final int fertilizer) {
        int tier = 0;
        for (int t = 1; t <= 20 && fertilizer != PLAIN_FERTILIZER; t++)
            if (fertilizerCost(t, BASE_GROWTH_DURATION_PER_TIER * t) == fertilizer) {
                tier = t;
                break;
            }
        final Web.Control kind = withDefault(
            choice(SEED_KIND, "Seed Kind", SEED_KINDS, null),
            TstControls.key(SEED_KINDS.get(fertilizer != PLAIN_FERTILIZER ? 0 : 2)));
        final int cropTier = Math.max(1, tier);
        return with(
            PERFECT_GENETICS_CONTROL,
            SEEDS_CONTROL,
            kind,
            withDefault(count(CROP_TIER, "Crop Tier", 1, null), cropTier),
            withDefault(count(GROWTH_DURATION, "Growth Duration", 1, null), BASE_GROWTH_DURATION_PER_TIER * cropTier),
            DROP_CHANCE_CONTROL,
            GROWTH_CONTROL,
            GAIN_CONTROL);
    }

    private static List<Web.Control> with(final Web.Control... own) {
        final List<Web.Control> out = new ArrayList<>(COMMON);
        Collections.addAll(out, own);
        return Collections.unmodifiableList(out);
    }

    /** A setting in percent, to two places. */
    private static Web.Control percent(final String id, final String label, final int defaultValue) {
        final Web.Control control = count(id, label, 0, null);
        control.numeric.step = 0.01;
        return withDefault(control, defaultValue);
    }

    /** The run's ticks: 20 with any Speed upgrade. */
    static int ticks(final Context c) {
        return number(c, SPEED) > 0 ? SPEED_TICKS : RUN_TICKS;
    }

    /** The card's parallels: the controller's (P x saplings or seeds where a mode multiplies them), at most an int. */
    static double parallels(final Context c) {
        return Math.min(Integer.MAX_VALUE, multiplyParallel(gameParallels(c), operations(c)));
    }

    /** The run's flat EU/t spread over the card's parallels; 0 where it refuses to start. */
    static double eutPerParallel(final Context c) {
        final int tier = powerTier(c);
        return tier < 1 ? 0 : (double) runEut(tier) / parallels(c);
    }

    /** No item is ever used up (saplings, seeds, targets and weapons stay in the Input Interface); the medium is. */
    static double inputMultiplier(final Context c, final Web.Resource input) {
        if (!"fluid".equals(input.kind) || !(input.amount > 0)) return 0;
        final double run = (double) fluidPerParallel(c, (long) input.amount) * operations(c) * gameParallels(c);
        return run / (input.amount * parallels(c));
    }

    /** The run's expected amount of this output over what the card's parallels make of the page's. */
    static double outputMultiplier(final Context c, final Web.Resource output) {
        final double shown = output.amount * (output.chance != null ? output.chance : 1);
        if (!(shown > 0)) return 0;
        final double run;
        if (ARBOREAL.equals(c.recipeMap)) run = treeRun(c, output);
        else if (AQUATIC.equals(c.recipeMap)) run = aquaticRun(c, output);
        else if (GREENHOUSE.equals(c.recipeMap)) run = greenhouseRun(c, output);
        else if (CLONING.equals(c.recipeMap)) run = clonerRun(c, output);
        else return 1;
        return run / (shown * parallels(c));
    }

    @Nullable
    static String gate(final Context c) {
        if (powerTier(c) < 1) return POWER_GATE;
        if (upgradeCount(c) > upgradeSlots(c)) return UPGRADE_GATE;
        final int capacity = capacity(c);
        if (ARBOREAL.equals(c.recipeMap)) {
            if (!UU_MATTER.equals(fluidId(c.recipe))
                && number(c, SAPLINGS) > slotLimit(capacity) * (specialMedium(c) ? inputSlots(capacity) : 1))
                return INPUT_GATE;
            if (selectedCategories(c) == 0) return TREE_SELECTION_GATE;
        } else if (AQUATIC.equals(c.recipeMap)) {
            if (c.tier(FOCUS) > 0 && number(c, FOCUS_ITEMS) > slotLimit(capacity) * inputSlots(capacity))
                return INPUT_GATE;
        } else if (GREENHOUSE.equals(c.recipeMap)) {
            if (number(c, SEEDS) > slotLimit(capacity)) return INPUT_GATE;
        } else if (CLONING.equals(c.recipeMap) && !recipeZero(c) && c.tier(STRUCTURE) < 1) return CLONING_GATE;
        return null;
    }

    // endregion

    // region Every mode

    /** EuTier = (int) calculateVoltageTier(getMaxInputEu()) (:1112). */
    static int powerTier(final Context c) {
        return (int) TstMachines.voltageTier(TstMachines.totalEu(c));
    }

    /** EcoSphereModeSupport.getParallelFromEUt (:49-54), :29-33 on Tier 1 and :35-42 on Tier 2. */
    static long powerParallels(final int tier, final boolean tierTwo) {
        if (tier <= 1) return 1;
        if (tierTwo) {
            final int exponent = tier - 1;
            if (exponent >= 31) return 1L << 62;
            return 1L << (exponent * 2);
        }
        return (int) Math
            .floor(2 * Math.pow(2, 0.1 * (tier - 1) * (8 + Math.log(25 + Math.exp(25 - tier)) / Math.log(5))));
    }

    /** EcoSphereModeSupport.calculateEut (:44-47): 30 x 4^(tier - 1), the whole run's. */
    static long runEut(final int tier) {
        return (long) (2d * Math.pow(4, tier + 1) * 15 / 16);
    }

    /** P: the power-limited parallels; the card is fed all the medium they take. */
    static long gameParallels(final Context c) {
        final int tier = powerTier(c);
        return tier < 1 ? 1 : powerParallels(tier, c.tier(STRUCTURE) >= 1);
    }

    /** The operations a parallel runs: saplings (Arboreal Genesis, 1 on UU matter) or seeds (Greenhouse). */
    static long operations(final Context c) {
        if (ARBOREAL.equals(c.recipeMap))
            return UU_MATTER.equals(fluidId(c.recipe)) ? 1 : Math.max(1, number(c, SAPLINGS));
        if (GREENHOUSE.equals(c.recipeMap)) return Math.max(1, number(c, SEEDS));
        return 1;
    }

    /** EcoSphereUpgradeResult.applyFluidDiscount (:41-46): Fluid Reduction and Speed upgrades halve it. */
    static long fluidPerParallel(final Context c, long amount) {
        final int upgrades = number(c, FLUID_REDUCTION) + number(c, SPEED);
        for (int i = 0; i < upgrades && amount > 1; i++) amount = Math.max(1, amount / 2);
        return amount;
    }

    /** getOutputMultiplier (:112-117): 1.5^(Output + Perfect Genetics + Output Pulverization). */
    static double outputScale(final Context c) {
        return Math.pow(1.5, number(c, OUTPUT) + c.tier(PERFECT_GENETICS) + c.tier(PULVERIZATION));
    }

    /** getCapacityUpgrades (:64-67): Capacity and Blood Orb upgrades, at most 4. */
    static int capacity(final Context c) {
        return Math.min(4, number(c, CAPACITY) + c.tier(BLOOD_ORB));
    }

    /** TST_EcoSphereInputInterfaceHatch.getSlotStackLimit (:220-227) outside cloning: min(64, 4^capacity). */
    static int slotLimit(final int capacity) {
        int limit = 1;
        for (int i = 0; i < capacity; i++) limit *= 4;
        return Math.min(64, limit);
    }

    /** getActiveInputSlots (:285-288) outside cloning: one, and one a capacity upgrade, at most 4. */
    static int inputSlots(final int capacity) {
        return Math.min(4, 1 + capacity);
    }

    /** TST_EcoSphereUpgradeInterfaceHatch.getActiveSlots (:158-160). */
    static int upgradeSlots(final Context c) {
        final int glass = 1 + c.tier(GLASS);
        return Math.min(1 + Math.min(4, glass) * (c.tier(STRUCTURE) >= 1 ? 2 : 1), MAX_UPGRADE_SLOTS);
    }

    static int upgradeCount(final Context c) {
        return number(c, FLUID_REDUCTION) + number(c, CAPACITY)
            + number(c, OUTPUT)
            + number(c, SPEED)
            + c.tier(PERFECT_GENETICS)
            + c.tier(BLOOD_ORB)
            + c.tier(PULVERIZATION);
    }

    /**
     * The expected size of an output stack of {@code amount} after applyOutputScaling (:73-90, :119-123): max(1,
     * (long) (amount x scale x V)), V uniform on [0.85, 1). Worked exactly: the mean of V less the mean fraction cut.
     */
    static double expectedScaled(final long amount, final double scale) {
        if (amount <= 0) return 0;
        final double hi = amount * scale, lo = amount * (scale * 0.85);
        if (!(hi > lo)) return Math.max(1, Math.floor(hi));
        final double floorLo = Math.floor(lo), floorHi = Math.floor(hi);
        final double fracLo = lo - floorLo, fracHi = hi - floorHi;
        // The integral of t - floor(t) from lo to hi.
        final double cut = (floorHi - floorLo) / 2 + (fracHi * fracHi - fracLo * fracLo) / 2;
        double expected = (lo + hi) / 2 - cut / (hi - lo);
        // Below 1 the stack is still 1 where floor gave 0.
        if (lo < 1) expected += (Math.min(hi, 1) - lo) / (hi - lo);
        return expected;
    }

    static long multiplyParallel(final long parallel, final long count) {
        if (parallel <= 0 || count <= 0) return 0;
        return parallel > Long.MAX_VALUE / count ? Long.MAX_VALUE : parallel * count;
    }

    // endregion

    // region Arboreal Genesis

    /**
     * One output of a run (TreeGrowthSimulatorMode.processOutputs :119-171): (long) (product x multiplier x P x
     * saplings x focus) for each sapling kind, then scaled. UU matter's page shows four symbols of one each: its picks
     * are counted as products of one (most of GT's registrations; the picked product's own count is random).
     */
    private static double treeRun(final Context c, final Web.Resource output) {
        final List<Web.Resource> items = itemOutputs(c);
        final int index = indexOf(items, output);
        if (index < 0) return 0;
        final int[] categories = treeCategories(c, items);
        final int category = categories[index];
        if (!selected(c, category)) return 0;
        final int chosen = selectedCategories(c);
        final int available = items.size();
        final float focus = chosen < available ? 1 + (float) (available - chosen) / chosen / 3 : 1;
        final int multiplier = TREE_MULTIPLIERS[category];
        final boolean uu = UU_MATTER.equals(fluidId(c.recipe));
        final int product = uu ? multiplier : (int) (output.amount / multiplier);
        final long parallel = gameParallels(c);
        long amount = 0;
        for (final long saplings : saplingStacks(c)) {
            final double each = product * (double) multiplier * multiplyParallel(parallel, saplings) * focus;
            amount += each >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) each;
        }
        return expectedScaled(amount, outputScale(c));
    }

    /**
     * The category of each page output: the page lists LOG, SAPLING, LEAVES, FRUIT in order, each times its multiplier,
     * leaving out what the tree lacks (fake recipe :225-240). Each output takes the first category left whose
     * multiplier divides it; UU matter's four symbols are the four in order.
     */
    static int[] treeCategories(final Context c, final List<Web.Resource> items) {
        final int n = Math.min(4, items.size());
        final int[] out = new int[items.size()];
        final boolean uu = UU_MATTER.equals(fluidId(c.recipe));
        int next = 0;
        for (int i = 0; i < n; i++) {
            final long amount = (long) items.get(i).amount;
            final int last = 4 - (n - i);
            int k = next;
            while (!uu && k < last && amount % TREE_MULTIPLIERS[k] != 0) k++;
            out[i] = k;
            next = k + 1;
        }
        for (int i = n; i < out.length; i++) out[i] = 3;
        return out;
    }

    private static boolean selected(final Context c, final int category) {
        return c.tier(TREE_SELECTIONS[category]) == 0;
    }

    /** The selected categories the page's tree has (:126-130). */
    static int selectedCategories(final Context c) {
        final List<Web.Resource> items = itemOutputs(c);
        final int[] categories = treeCategories(c, items);
        int chosen = 0;
        for (final int category : categories) if (selected(c, category)) chosen++;
        return chosen;
    }

    /** Temporal fluid, death water and unknown water: any sapling makes the special tree's products (:89-92). */
    private static boolean specialMedium(final Context c) {
        final String fluid = fluidId(c.recipe);
        return TEMPORAL_FLUID.equals(fluid) || DEATH_WATER.equals(fluid) || UNKNOWN_WATER.equals(fluid);
    }

    /**
     * The sapling counts a run reads, one a kind: UU matter one; the page's own sapling one stack of them all; a
     * special medium any kinds, as full stacks of the slot limit and the rest.
     */
    private static List<Long> saplingStacks(final Context c) {
        if (UU_MATTER.equals(fluidId(c.recipe))) return List.of(1L);
        final long saplings = Math.max(1, number(c, SAPLINGS));
        if (!specialMedium(c)) return List.of(saplings);
        final long limit = slotLimit(capacity(c));
        final List<Long> stacks = new ArrayList<>();
        for (long left = saplings; left > 0; left -= limit) stacks.add(Math.min(limit, left));
        return stacks;
    }

    // endregion

    // region Aquatic Simulation

    /**
     * The page's member over a run (processStandardOutputs :154-179, processUnknownWaterOutputs :129-152): its chance
     * focused (x W on the target, max(chance / W, 1) on the rest; an Offspring target, distilled water only, W x 41),
     * rolled at tier chance log2(t + 2). BoP's duplicated lily rolls twice; the game adds both rolls before the one
     * scaling, the card scales each (within one item a run).
     */
    private static double aquaticRun(final Context c, final Web.Resource output) {
        final boolean unknown = UNKNOWN_WATER.equals(fluidId(c.recipe));
        final int weight = (int) output.amount;
        int chance = CHANCE_SCALE / (unknown ? UNKNOWN_POOL_WEIGHT : DISTILLED_POOL_WEIGHT) * weight;
        final int items = Math.max(1, number(c, FOCUS_ITEMS));
        switch (c.tier(FOCUS)) {
            case 1 -> chance *= items;
            case 2 -> chance = Math.max(chance / items, 1);
            case 3 -> {
                if (!unknown) chance = Math.max(chance / (items * OFFSPRING_FOCUS_WEIGHT), 1);
            }
            default -> {}
        }
        final double tierChance = Math.log(powerTier(c) + 2) / Math.log(2);
        final int rolls = !unknown && DUPLICATED_LILY.equalsIgnoreCase(output.id) ? 2 : 1;
        return rolls * aquaticRoll(weight, gameParallels(c), chance, tierChance, outputScale(c));
    }

    private record Roll(int weight, long parallel, int chance, double tierChance, double scale) {}

    /** A card asks for the same roll many times a solve: the last few, kept. */
    private static final Map<Roll, Double> ROLLS = Collections
        .synchronizedMap(new LinkedHashMap<Roll, Double>(64, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(final Map.Entry<Roll, Double> eldest) {
                return size() > 256;
            }
        });

    /** addRandomOutput (:198-206) over every r = nextInt(100000), then scaled: its expected value. */
    static double aquaticRoll(final int weight, final long parallel, final int chance, final double tierChance,
        final double scale) {
        return ROLLS.computeIfAbsent(new Roll(weight, parallel, chance, tierChance, scale), k -> {
            double sum = 0;
            for (int random = 0; random < CHANCE_SCALE; random++) {
                if (random > chance * tierChance) break;
                final long amount = (long) (weight * (double) parallel * chance * random / AMOUNT_SCALE);
                if (amount > 0) sum += expectedScaled(amount, scale);
            }
            return sum / CHANCE_SCALE;
        });
    }

    // endregion

    // region Artificial Greenhouse

    /** CropsNHFarm.addOutputStacks (:70-82): ceil(yield x P x seeds x 40), then scaled. */
    private static double greenhouseRun(final Context c, final Web.Resource output) {
        final long seedParallel = multiplyParallel(gameParallels(c), Math.max(1, number(c, SEEDS)));
        final double each = Math.ceil(greenhouseYield(c, output) * (seedParallel * GREENHOUSE_OUTPUT_SCALE));
        final long amount = each >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) each;
        return amount < 1 ? 0 : expectedScaled(amount, outputScale(c));
    }

    /**
     * A seed's cached yield of one drop a cycle. Hybrid (createCrop :168-216): (stack + (gain + 1) / 100) x chance x
     * drop chance x 1.03^gain x CYCLE_TICK_RATE_SCALAR / ceil(growth duration / growth rate), Perfect Genetics growth
     * and gain 31. Alternate seed (:111-161): the same at 1/1/1, x 0.25. Other plantable (:220-324): the simulated
     * drops a harvest (the page's stack x chance, rounded to 4 places) x CYCLE_TICK_RATE_SCALAR / ceil(1200 / growth
     * rate at tier 1, growth 1) x 0.25.
     */
    static double greenhouseYield(final Context c, final Web.Resource output) {
        final int chance = output.chance != null ? (int) Math.round(output.chance * 10_000) : 10_000;
        final int kind = c.tier(SEED_KIND);
        if (kind == 2) return output.amount * (chance / 10_000d) * PLANTABLE_PROGRESS;
        final boolean hybrid = kind == 0;
        final boolean perfect = hybrid && c.tier(PERFECT_GENETICS) == 1;
        final int growth = perfect ? PERFECT_SEED_STAT : hybrid ? Math.max(1, number(c, GROWTH)) : 1;
        final int gain = perfect ? PERFECT_SEED_STAT : hybrid ? Math.max(1, number(c, GAIN)) : 1;
        final int tier = Math.max(1, number(c, CROP_TIER));
        final double progress = growthProgressPerCycle(tier, growth, Math.max(1, number(c, GROWTH_DURATION)));
        final double increase = (gain + 1) / 100.0d;
        final double rounds = dropChance(c, tier) * Math.pow(1.03D, gain);
        final double yield = ((int) output.amount + increase) * (chance / 10_000d) * rounds * progress;
        return hybrid ? yield : yield * NORMAL_SEED_EFFICIENCY;
    }

    /** createVanillaCrop (:304-307): CYCLE_TICK_RATE_SCALAR / ceil(1200 / 83) x 0.25. */
    static final double PLANTABLE_PROGRESS = CYCLE_TICK_RATE_SCALAR
        / (int) Math.ceil((double) NORMAL_SEED_GROWTH_DURATION / growthRate(SIMULATED_NUTRIENT_SCORE, 1, 1))
        * NORMAL_SEED_EFFICIENCY;

    /** getGrowthProgressPerCycle (:333-354); a rate of 0 falls back to 1 (:181-185). */
    static double growthProgressPerCycle(final int tier, final int growth, final int duration) {
        final int rate = growthRate(SIMULATED_NUTRIENT_SCORE, tier, growth);
        if (rate <= 0) return 1;
        int ticks = duration / rate;
        if (duration % rate != 0) ticks++;
        return 1.0d / ticks * CYCLE_TICK_RATE_SCALAR;
    }

    /** TileEntityCropSticks.getGrowthRate (CropsNH 2.0.140 :918-931). */
    static int growthRate(int nutrientPoints, final int tier, final int growth) {
        nutrientPoints *= NUTRIENT_POINT_SCALE;
        final int need = tier * NUTRIENTS_NEEDED_PER_TIER;
        if (need < 0) return 0;
        final int baseSpeed = BASE_GROWTH_SPEED + growth;
        if (nutrientPoints >= need) return baseSpeed * (100 + (nutrientPoints - need)) / 100;
        return Math.max(baseSpeed * (100 - (need - nutrientPoints) * 4) / 100, 0);
    }

    /** The crop's drop chance, a float: CropCard's 0.95^tier or an override's. */
    private static float dropChance(final Context c, final int tier) {
        final float base = (float) Math.pow(0.95, tier);
        return switch (c.tier(DROP_CHANCE)) {
            case 1 -> base * 0.75f;
            case 2 -> base * 0.5f;
            case 3 -> 0.5f;
            case 4 -> 0.8f;
            case 5 -> 0.85f;
            case 6 -> 1.5f;
            case 7 -> 2.0f;
            default -> base;
        };
    }

    /** CropsNHFarm.getFertilizerCost (:88-95) of a crop. */
    static int fertilizerCost(final int tier, final int growthDuration) {
        return Math.max(1, (int) Math.ceil(2d * Math.max(1, tier) * Math.log(Math.max(1, growthDuration))));
    }

    // endregion

    // region Directed Mob Cloning

    /** Address 0's page: blood in. */
    private static boolean recipeZero(final Context c) {
        return BLOOD.equals(fluidId(c.recipe));
    }

    /**
     * Address 0 (createFallbackResult :330-356): 100 L of Life Essence a parallel, scaled in 5 L steps (applyTo with
     * the LP conversion, EcoSphereUpgradeResult :92-106). A mob: the soul vial is display only; the first drop
     * (:104-118) is stack x P x (chance + Looting x 5000) / 10000 x 15125 / 1024 / D^2, D = max(55, (int) (health / 9
     * x 10)) and health = the page's Life Essence / 4 (DirectedMobClonerRecipeCache :77, :343-345).
     */
    private static double clonerRun(final Context c, final Web.Resource output) {
        final long parallel = gameParallels(c);
        if (recipeZero(c)) {
            if (!"fluid".equals(output.kind)) return 0;
            final long amount = multiplyParallel(LIFE_ESSENCE_PER_PARALLEL, parallel);
            return LP_DIVISOR * expectedScaled(amount / LP_DIVISOR, outputScale(c));
        }
        if (indexOf(itemOutputs(c), output) != 1) return 0;
        final double chance = c.value(FIRST_DROP_CHANCE) * 100;
        final double bonus = Math.min(LOOTING_CAP * LOOTING_CHANCE, number(c, LOOTING) * LOOTING_CHANCE);
        final double each = (int) output.amount * (double) parallel
            * (chance + bonus)
            / 10_000d
            * durationMultiplier(c);
        final long amount = each >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) each;
        return expectedScaled(amount, outputScale(c));
    }

    /** 15125 / 1024 / D^2 (:96), D from the health the page's Life Essence cost (max(1, ceil(4 x health))) gives. */
    static double durationMultiplier(final Context c) {
        final Web.Resource lifeEssence = fluidInput(c.recipe);
        final float health = (lifeEssence != null ? (int) lifeEssence.amount : 1) / 4f;
        final int duration = Math.max(EEC_MIN_DURATION, (int) ((health / EEC_DIAMOND_SPIKES_DAMAGE) * 10d));
        return 1 * 15125d / 1024d / ((double) duration * duration);
    }

    // endregion

    // region The page's ports

    @Nullable
    static Web.Resource fluidInput(@Nullable final Web.Recipe recipe) {
        if (recipe == null || recipe.inputs == null) return null;
        for (final Web.Resource in : recipe.inputs) if ("fluid".equals(in.kind)) return in;
        return null;
    }

    static String fluidId(@Nullable final Web.Recipe recipe) {
        final Web.Resource fluid = fluidInput(recipe);
        return fluid != null && fluid.id != null ? fluid.id.toLowerCase(Locale.ROOT) : "";
    }

    static List<Web.Resource> itemOutputs(final Context c) {
        final List<Web.Resource> out = new ArrayList<>();
        if (c.recipe == null || c.recipe.outputs == null) return out;
        for (final Web.Resource r : c.recipe.outputs) if ("item".equals(r.kind)) out.add(r);
        return out;
    }

    private static int indexOf(final List<Web.Resource> list, final Web.Resource port) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) == port) return i;
        return -1;
    }

    // endregion
}
