package com.gtnhplanner.machines.eto;

import static com.gtnhplanner.machines.eto.TstControls.*;
import static com.gtnhplanner.machines.web.MachineTable.machine;
import static com.gtnhplanner.machines.web.MachineTable.none;
import static com.gtnhplanner.machines.web.MachineTable.normal;
import static com.gtnhplanner.machines.web.MachineTable.perfect;

import java.util.List;
import java.util.Map;

import com.gtnhplanner.machines.web.MachineTable;
import com.gtnhplanner.machines.web.MachineTable.Behaviour;
import com.gtnhplanner.machines.web.MachineTable.Coefficient;
import com.gtnhplanner.machines.web.MachineTable.Context;
import com.gtnhplanner.machines.web.RecipeRules;
import com.gtnhplanner.machines.web.Tiers;
import com.gtnhplanner.machines.web.Web;

/**
 * TwistSpaceTechnology 0.8.1's recipe-processing multiblocks as machine table entries, beside the website's own
 * (MachineTable keeps them out of its names): each machine's speed, EU/t, parallels and overclock as its controller
 * computes them, from the pack's config (docs/design/tst-machines.md has the inventory and the source lines). The
 * maps each machine runs, and its item, are in {@code assets/gtnhplanner/machines/eto-tst.json}. A speed is the
 * inverse of TST's speed bonus, which is a duration multiplier kept as a float, so the duration rounds as the game's.
 * Pure: no game classes.
 */
public final class TstMachines {

    private TstMachines() {}

    // region Recipe maps, by the name the data gives them (a formula's Context.recipeMap)

    static final String ICD = "Intense Chemical Distorter", CHEMICAL_PLANT = "Chemical Plant",
        FUSION = "Fusion Reactor", LASER_ENGRAVER = "Laser Engraver", EXTRUDER = "Extruder",
        CYCLOTRON = "COMET - Compact Cyclotron", EIC = "Electric Implosion Compressor",
        NEUTRONIUM_COMPRESSOR = "Neutronium Compressor", AUTOCLAVE = "Autoclave", CHEMICAL_BATH = "Chemical Bath",
        COMPONENT_ASSEMBLY_LINE = "Component Assembly Line", ASSEMBLER = "Assembler",
        PRECISE_ASSEMBLER = "Precise Assembler", DISTILLERY = "Distillery", STAR_KERNEL = "Star Kernel Generator",
        PHOTON_MANIPULATOR = "Precise High-Energy Photonic Quantum Manipulator";

    // endregion

    // region The game's tier functions

    /** GTValues.V[14]: MAX's voltage, the wireless hatches' top. */
    private static final long MAX_VOLTAGE = (long) Tiers.MAX_EUT[14];

    /**
     * GTUtility.getTierExtended: the lowest tier whose voltage carries {@code eu}, also past MAX; 0 at 8 EU or less.
     */
    static int tierExtended(final long eu) {
        if (eu <= 8) return 0;
        return (64 - Long.numberOfLeadingZeros(eu - 1) - 2) / 2;
    }

    /** GTUtility.getTier: as {@link #tierExtended}, 15 past MAX. */
    static int gtTier(final long eu) {
        return eu > MAX_VOLTAGE ? 15 : tierExtended(eu);
    }

    private static final double LOG2 = Math.log(2);

    /** TSTUtils.calculateVoltageTier: log4 of an EU amount over 8, as a fraction. */
    static double voltageTier(final double eu) {
        return Math.max(0, ((Math.log(eu) / LOG2) - 3) / 2);
    }

    /** getMaxInputEu: the hatches' voltage times their amps, as the card's tier and amps give it. */
    static long totalEu(final Context c) {
        return Double.isFinite(c.poolEuT) && c.poolEuT > 0 ? (long) c.poolEuT : 0;
    }

    /** TSTUtils.getMachineTotalPowerTier: the tier of the hatches' total EU (2 A of IV is LuV). */
    static int totalPowerTier(final Context c) {
        return tierExtended(totalEu(c));
    }

    /** GTUtility.getTier(recipe.mEUt): the recipe's own voltage tier. */
    static int recipeTier(final Context c) {
        return c.recipe != null ? gtTier((long) Math.abs(c.recipe.eut)) : 0;
    }

    /** The speed a duration multiplier (TST's speed bonus) is: its inverse, from the float the game keeps. */
    static double speedOf(final double durationMultiplier) {
        return durationMultiplier > 0 ? 1 / durationMultiplier : 1;
    }

    /** HeatingCoilLevel.getTier(): Cupronickel 0 to Eternal 13. */
    static int coil(final Context c) {
        return c.tier(COIL);
    }

    /** chainAllGlasses: HV 3 to UXV 13. */
    static int glass(final Context c) {
        return 3 + c.tier(GLASS);
    }

    private static int number(final Context c, final String id) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, c.value(id)));
    }

    private static boolean on(final Context c, final String recipeMap) {
        return recipeMap.equals(c.recipeMap);
    }

    /**
     * ParallelHelper's limit on the structure's parallels: as many as the hatches' EU pays for, each billed at the
     * recipe's EU/t times the EU modifier rounded UP (the website's own limit does not round, and would let a 0.6x
     * machine run a twentieth more parallels than the game does). With no hatches yet (a new card's seed), a machine
     * whose structure sets no limit asks for one parallel's worth.
     */
    static double paid(final Context c, final double structural, final double euModifier) {
        if (c.recipe == null) return structural;
        final double each = Math.ceil(Math.abs(c.recipe.eut) * euModifier);
        if (!(each > 0)) return structural;
        if (!(c.poolEuT > 0) || !Double.isFinite(c.poolEuT)) return structural >= Integer.MAX_VALUE ? 1 : structural;
        return Math.min(structural, Math.floor((long) c.poolEuT / each));
    }

    // endregion

    /** The gate messages, English data. */
    static final String GLASS_GATE = "The glass tier is below this recipe's voltage tier.",
        FIELD_GATE = "This recipe needs a higher field generator tier.",
        MODE_FIELD_GATE = "This mode needs a higher field generator tier.",
        MATRIX_GATE = "This recipe needs a higher Energy Sustainment Matrix tier.",
        CASING_GATE = "This recipe needs a higher component casing tier.",
        CORE_GATE = "This recipe needs a higher MultiUse Core tier.",
        BALL_TIER_GATE = "This mode needs a higher machine tier.",
        INFINITY_COIL_GATE = "This mode needs Infinity coils or better.";

    /** Every machine the inventory marks done, by the label its handler templates carry. */
    public static void register(final Map<String, Behaviour> machines) {
        TstMachinesVisual.register(machines);
        // GT_TileEntity_IntensifyChemicalDistorter (0.8.1, :283-351): its own map 16 parallels at 1 / (1 + whole
        // 900 K steps of coil heat over the recipe's), the chemical reactor and the chemical plant 1024 parallels at
        // 1 / (coil tier + 1); perfect overclocks; a recipe hotter than the coils is refused, the chemical plant takes
        // Infinity coils or better.
        put(
            machines,
            "Intensify Chemical Distorter",
            machine().overclock(perfect())
                .speed(c -> {
                    if (on(c, ICD)) {
                        final long heat = 1 + 900L * (coil(c) + 2);
                        final int excess = (int) Math.max(0, (heat - recipeSpecial(c)) / 900);
                        return speedOf((double) (1F / 1) / (1 + excess));
                    }
                    return speedOf(1F / Math.max(1, coil(c) + 1));
                })
                .parallels(c -> on(c, ICD) ? 16 : 1024)
                .controls(HOT_COIL_CONTROL)
                .recipeGate(c -> on(c, CHEMICAL_PLANT) && coil(c) < 11 ? INFINITY_COIL_GATE : null));
        // GT_TileEntity_PreciseHighEnergyPhotonicQuantumMaster: 10000 / (10000 + the 25 intensifiers' increments),
        // halved again as a laser engraver; perfect with UMV intensifiers or better.
        put(
            machines,
            "Precise High-Energy Photonic Quantum Master",
            machine().overclock(c -> c.tier(PHOTON) >= 12 ? perfect() : normal())
                .speed(c -> {
                    final float bonus = 10000F / (10000F + 25 * PHOTON_INCREMENT[c.tier(PHOTON)]);
                    return speedOf(bonus / (on(c, PHOTON_MANIPULATOR) ? 1 : 2));
                })
                .parallels(c -> on(c, PHOTON_MANIPULATOR) ? 16 : 256)
                .controls(PHOTON_CONTROL));
        // GT_TileEntity_MiracleTop: per ring 4x speed and 128 parallels; perfect from 8 rings.
        put(
            machines,
            "Miracle Top",
            machine().overclock(c -> rings(c, 2) >= 8 ? perfect() : normal())
                .speed(c -> speedOf(1.0F / (rings(c, 2) * 4)))
                .parallels(c -> rings(c, 2) * 128)
                .controls(count(RINGS, "Rings", 2, 16)));
        // GT_TileEntity_MagneticDrivePressureFormer: 8x as an extruder, 16x otherwise, divided by 1 + the coil tier;
        // 0.75 EU, 1024 parallels; perfect but as an extruder below Infinity coils.
        put(
            machines,
            "Magnetic Drive Pressure Former",
            machine().overclock(c -> !on(c, EXTRUDER) || coil(c) >= 11 ? perfect() : normal())
                .speed(c -> speedOf((on(c, EXTRUDER) ? 1.0F / 8 : 1.0F / 16) / (1 + coil(c) * 1)))
                .power(0.75F)
                .parallels(1024)
                .controls(COIL_CONTROL));
        // GT_TileEntity_PhysicalFormSwitcher: 0.9 per total power tier; parallels as the power pays.
        put(
            machines,
            "Physical Form Switcher",
            machine().overclock(normal())
                .speed(c -> speedOf((float) Math.pow(0.9F, totalPowerTier(c))))
                .parallels(Integer.MAX_VALUE)
                .controls(GLASS_CONTROL)
                .recipeGate(TstMachines::glassGate));
        // GT_TileEntity_MagneticMixer.
        put(
            machines,
            "\"Mini\" Magnetic Mixer",
            machine().overclock(normal())
                .speed(c -> speedOf((float) Math.pow(0.8F, totalPowerTier(c))))
                .parallels(Integer.MAX_VALUE));
        // GT_TileEntity_MagneticDomainConstructor: 64 parallels a ring.
        put(
            machines,
            "Magnetic Domain Constructor",
            machine().overclock(normal())
                .speed(c -> speedOf((float) Math.pow(0.75F, totalPowerTier(c))))
                .parallels(c -> (int) Math.min((long) rings(c, 1) * 64, Integer.MAX_VALUE))
                .controls(count(RINGS, "Rings", 1, null)));
        // GT_TileEntity_Silksong; from 94 sections without hatches it runs wireless: 100 batches of its parallels in
        // 188 ticks (TstMachinesWireless.orWireless).
        put(
            machines,
            "Silksong",
            TstMachinesWireless.orWireless(
                machine().overclock(c -> pieces(c) >= 94 ? perfect() : normal())
                    .speed(c -> speedOf((float) (Math.pow(0.85F, totalPowerTier(c)) / ((coil(c) + 1) * 1F))))
                    .parallels(c -> (int) Math.min((long) pieces(c) * (coil(c) + 1) * 32, Integer.MAX_VALUE))
                    .controls(COIL_CONTROL, PIECES_CONTROL),
                TstWirelessControls.WIRELESS_CONTROL,
                c -> 188,
                c -> 1,
                null,
                TstMachinesWireless::silksongGate));
        // GT_TileEntity_HolySeparator: 8 parallels a section, perfect from 16.
        put(
            machines,
            "Holy Separator",
            machine().overclock(c -> pieces(c) >= 16 ? perfect() : normal())
                .speed(c -> speedOf((float) Math.pow(0.9F, totalPowerTier(c))))
                .parallels(c -> 8 * pieces(c))
                .controls(PIECES_CONTROL));
        // GT_TileEntity_SpaceScaler: 10x and perfect from the second field generator; the particle collider from the
        // third, the implosion and neutronium compressors from the eleventh; the collider's outputs multiply.
        put(
            machines,
            "Space Scaler",
            machine().overclock(c -> field(c) >= 2 ? perfect() : normal())
                .speed(c -> speedOf(1F / (field(c) < 2 ? 1 : 10)))
                .parallels(Integer.MAX_VALUE)
                .controls(FIELD_CONTROL)
                .controlsFor(
                    r -> List.of(CYCLOTRON.equals(RecipeRules.recipeMapName(r)) ? COLLIDER_FIELD : FIELD_CONTROL))
                .recipeGate(
                    c -> on(c, CYCLOTRON) && field(c) < 3
                        || (on(c, EIC) || on(c, NEUTRONIUM_COMPRESSOR)) && field(c) < 11 ? MODE_FIELD_GATE : null));
        // GT_TileEntity_MoleculeDeconstructor: 24 parallels a section, perfect from 16.
        put(
            machines,
            "Molecule Deconstructor",
            machine().overclock(c -> pieces(c) >= 16 ? perfect() : normal())
                .speed(c -> speedOf((float) Math.pow(0.9F, totalPowerTier(c))))
                .parallels(c -> 24 * pieces(c))
                .controls(PIECES_CONTROL));
        // GTCM_CrystallineInfinitier: 4x autoclave, 16x chemical bath; glass x field x total power tier parallels;
        // EU / field tier; perfect from the first Stabilisation generator; a recipe's tier needs that field tier.
        put(
            machines,
            "Crystalline Infinitier",
            machine().overclock(c -> field(c) >= 3 ? perfect() : normal())
                .speed(c -> speedOf(on(c, AUTOCLAVE) ? 1F / 4 : on(c, CHEMICAL_BATH) ? 1F / 16 : 1F / 1))
                .power(c -> 1.0F / Math.max(field(c), 1))
                .parallels(c -> (int) Math.min(Integer.MAX_VALUE, (long) glass(c) * field(c) * totalPowerTier(c) * 1))
                .controls(GLASS_CONTROL, FIELD_CONTROL)
                .recipeGate(c -> c.recipeSpecialValue != null && c.recipeSpecialValue > field(c) ? FIELD_GATE : null));
        // GTCM_ElvenWorkshop (and its legacy controller): one parallel, perfect overclocks.
        put(machines, "ElvenWorkshop", machine().overclock(perfect()));
        // TST_HyperSpacetimeTransformer: 1 / fusion coil tier, 256 parallels, perfect with Infinity Cooled Casings;
        // the matrix caps the recipe at V[UHV + tier - 1].
        put(
            machines,
            "HyperSpacetimeTransformer",
            machine().overclock(c -> c.tier(COOLING) == 1 ? perfect() : normal())
                .speed(c -> speedOf(1F / (c.tier(FUSION_COIL) + 1)))
                .parallels(256)
                .controls(FUSION_COIL_CONTROL, COOLING_CONTROL, MATRIX_CONTROL)
                .recipeGate(
                    c -> c.recipe != null && Math.abs(c.recipe.eut) > Tiers.MAX_EUT[9 + c.tier(MATRIX)] ? MATRIX_GATE
                        : null));
        // TST_LargeIndustrialCokingFactory: 1 / (coil tier + 1).
        put(
            machines,
            "Large Industrial Coking Factory",
            machine().overclock(normal())
                .speed(c -> speedOf(1F / (coil(c) + 1)))
                .parallels(Integer.MAX_VALUE)
                .controls(COIL_CONTROL));
        // TST_Scavenger: 0.8 (a double) per total power tier, 0.6 EU.
        put(
            machines,
            "Scavenger",
            machine().overclock(normal())
                .speed(c -> speedOf((float) Math.pow(0.8D, totalPowerTier(c))))
                .power(0.6F)
                .parallels(Integer.MAX_VALUE));
        // TST_BiosphereIII (0.8.1, :266-282): a brewery or fermenter at 4x, 1 << (glass * 2 - 6) parallels; a bacterial
        // vat (TstCoresVat) at 2x and 4 parallels a petri dish (mode 0) or 1x and 1 a dish (mode 1), as many as the
        // output hatch takes. Normal overclocks (:284-287).
        put(
            machines,
            "Biosphere III",
            machine().overclock(normal())
                .speed(c -> speedOf(TstCoresVat.on(c) ? TstCoresVat.speedBonus(c) : 0.25f))
                .parallels(c -> TstCoresVat.on(c) ? TstCoresVat.parallels(c) : 1 << Math.max(glass(c) * 2 - 6, 0))
                .controls(GLASS_CONTROL)
                .controlsFor(
                    r -> TstCoresVat.MAP.equals(RecipeRules.recipeMapName(r)) ? TstCoresVat.CONTROLS
                        : List.of(GLASS_CONTROL)));
        // TST_AdvancedMegaOilCracker: 2x, EU / (coil tier + 1), 256 parallels, perfect from Hypogen coils.
        put(
            machines,
            "Advanced Mega Oil Cracker",
            machine().overclock(c -> coil(c) >= 12 ? perfect() : normal())
                .speed(speedOf(0.5F))
                .power(c -> 1F / (coil(c) + 1))
                .parallels(256)
                .controls(COIL_CONTROL));
        // TST_IndistinctTentacle with energy hatches: 1x/2x/4x/4x by mode, 256 parallels, perfect on a recipe no
        // higher than the component casing; a recipe two tiers past it (or a component recipe past it) is refused.
        // With UMV glass and casing and no hatches it runs wireless: 100 unlimited batches in 512 ticks, or in 20 at
        // 64x the EU with an Astral Array Fabricator in the controller (TstMachinesWireless.orWireless).
        put(
            machines,
            "Indistinct Tentacle",
            TstMachinesWireless.orWireless(
                machine().overclock(c -> recipeTier(c) <= c.tier(COMPONENT_CASING) ? perfect() : normal())
                    .speed(
                        c -> speedOf(
                            on(c, COMPONENT_ASSEMBLY_LINE) ? 1F / 2
                                : on(c, ASSEMBLER) || on(c, PRECISE_ASSEMBLER) ? 1F / 4 : 1F / 1))
                    .parallels(256)
                    .controls(COMPONENT_CASING_CONTROL)
                    .recipeGate(TstMachines::tentacleGate),
                TstWirelessControls.TENTACLE_POWER_CONTROL,
                c -> TstMachinesWireless.astralArray(c) ? 20 : 512,
                c -> TstMachinesWireless.astralArray(c) ? 64 : 1,
                c -> Integer.MAX_VALUE,
                TstMachinesWireless::tentacleGate));
        // TST_ThermalEnergyDevourer with energy hatches: high speed 0.5 / k at 1024 parallels, devourer EU / k at
        // any parallels; k = 1 + the hatches' EU tier, rounded down. With an Energised Tesseract in the controller it
        // runs wireless: 100 unlimited batches in 128 ticks (TstMachinesWireless.orWireless).
        put(
            machines,
            "Thermal Energy Devourer",
            TstMachinesWireless.orWireless(
                machine().overclock(normal())
                    .speed(c -> speedOf(c.tier(DEVOURER_MODE) == 1 ? 1 : 0.5F / devourerCoefficient(c)))
                    .power(c -> c.tier(DEVOURER_MODE) == 0 ? 1 : 1F / devourerCoefficient(c))
                    .parallels(c -> c.tier(DEVOURER_MODE) == 1 ? Integer.MAX_VALUE : 1024)
                    .controls(DEVOURER_MODE_CONTROL),
                TstWirelessControls.DEVOURER_POWER_CONTROL,
                c -> 128,
                c -> 1,
                c -> Integer.MAX_VALUE,
                null));
        // TST_VacuumFilterExtractor: 1 / (1 + total power tier), 0.5 EU, perfect as a distillery.
        put(
            machines,
            "Vacuum Filter Extractor",
            machine().overclock(c -> on(c, DISTILLERY) ? perfect() : normal())
                .speed(c -> speedOf(1F / (1 + totalPowerTier(c))))
                .power(0.5F)
                .parallels(Integer.MAX_VALUE));
        // TST_MegaMacerator: 8x when the glass is above the recipe's tier (or UMV), parallels by the block.
        put(
            machines,
            "\"Mini\" Household Cell Fragmentizer",
            machine().overclock(normal())
                .speed(c -> speedOf(glass(c) >= 12 || glass(c) > recipeTier(c) ? 0.125F : 1))
                .parallels(c -> switch (c.tier(MACERATOR_BLOCK)) {
                case 0 -> 128;
                case 1 -> 32768;
                default -> Integer.MAX_VALUE;
                })
                .controls(GLASS_CONTROL, MACERATOR_BLOCK_CONTROL)
                .recipeGate(TstMachines::glassGate));
        // TST_HephaestusAtelier: TstMachinesCores (both modes, every coil).
        // TST_DeployedNanoCore with energy hatches; without any it runs wireless: 100 unlimited batches in 128 ticks
        // (TstMachinesWireless.orWireless).
        put(
            machines,
            "Deployed Nano Core",
            TstMachinesWireless.orWireless(
                machine().overclock(perfect())
                    .parallels(Integer.MAX_VALUE),
                TstWirelessControls.WIRELESS_CONTROL,
                c -> 128,
                c -> 1,
                null,
                null));
        // TST_CoreDeviceOfHumanPowerGenerationFacility: 1 / (coil tier + 1), perfect.
        put(
            machines,
            "Core Device of Human Power Generation Facility",
            machine().overclock(perfect())
                .speed(c -> speedOf(1F / (1 + coil(c))))
                .parallels(Integer.MAX_VALUE)
                .controls(COIL_CONTROL));
        // TST_BallLightning: TstMachinesCores (arc furnace, fusion and star kernel modes).
        // TST_LargeCanner.
        put(
            machines,
            "Large Canner",
            machine().overclock(perfect())
                .parallels(Integer.MAX_VALUE));
        // TST_IndustrialMagnetarSeparator: 4x, 0.8 EU, 4 parallels per tier of the summed hatch voltage.
        put(
            machines,
            "Industrial Magnetar Separator",
            machine().overclock(normal())
                .speed(speedOf(0.25F))
                .power(0.8F)
                .parallels(c -> 4 * c.voltageTier));
        // MM_MassFabricatorGenesis: its parallel controllers' parallels plus one, perfect.
        put(
            machines,
            "Mass Fabricator : Genesis",
            machine().overclock(perfect())
                .parallels(c -> Math.max(1, number(c, PARALLELS)))
                .controls(PARALLELS_CONTROL));
        // TST_IncompactCyclotron: 2x, 1.6 EU, 256 parallels.
        put(
            machines,
            "PULSAR - Incompact Cyclotron",
            machine().overclock(normal())
                .speed(speedOf(0.5F))
                .power(1.6F)
                .parallels(256));
        // TST_MicroSpaceTimeFabricatorio: perfect past the first field tier, then 1 / (t(t+1)/2) for t = tier - 2;
        // 16 parallels; a SpaceTime Seed doubles the run's outputs.
        put(
            machines,
            "Micro SpaceTime Fabricatorio",
            machine().overclock(c -> field(c, MSTF_FIELD) > 1 ? perfect() : normal())
                .speed(c -> {
                    final int field = field(c, MSTF_FIELD);
                    if (field <= 2) return 1;
                    final int t = field - 2;
                    return speedOf(1F / ((t + 1) * t / 2));
                })
                .parallels(16)
                .controls(MSTF_FIELD_CONTROL, SEED_CONTROL));
        // TST_BloodyHell: no EU, so no overclock; tier^3 parallels; slower at tier 1, faster with tier and runes.
        put(
            machines,
            "Bloody Hell",
            machine().overclock(none())
                .speed(c -> {
                    final int tier = c.tier(ALTAR_TIER) + 1;
                    final int runes = number(c, SPEED_RUNES) + 3 * number(c, TIME_BENDING_RUNES);
                    final float tierBonus = (tier - 2) * 0.5F;
                    final float runeBonus = (float) (35 * (1 - Math.exp(-runes / 500.0)));
                    return speedOf(1.0F / ((1.0F + tierBonus) * (1.0F + runeBonus)));
                })
                .parallels(c -> {
                    final int tier = c.tier(ALTAR_TIER) + 1;
                    return tier * tier * tier;
                })
                .controls(ALTAR_TIER_CONTROL, SPEED_RUNES_CONTROL, TIME_BENDING_RUNES_CONTROL));
        // TST_MegaStoneBreaker: 4 * 2^(the hatches' EU tier, rounded down) parallels; outputs x4, x1024 when fed.
        put(
            machines,
            "Silicon Rock Synthesizer",
            machine().overclock(normal())
                .parallels(c -> {
                    final int tier = (int) voltageTier(totalEu(c));
                    return tier < 29 ? (int) (Math.pow(2, tier) * 4) : Integer.MAX_VALUE;
                })
                .controls(WATER_LAVA_CONTROL));
        // TST_ManufacturingCenter: 1 / (1.2 + 0.5 per core tier past IV), EU less 0.15 a tier, 2 parallels per tier
        // of the summed hatch voltage; no recipe above the core's tier.
        put(
            machines,
            "Manufacturing Center",
            machine().overclock(normal())
                .speed(c -> speedOf(1.0 / (1 + (double) 0.2F + (double) 0.5F * c.tier(CORE))))
                .power(c -> 1.0 - (double) 0.15F * c.tier(CORE))
                .parallels(c -> 2 * c.voltageTier)
                .controls(CORE_CONTROL)
                .recipeGate(c -> recipeTier(c) > 5 + c.tier(CORE) ? CORE_GATE : null));
        // TST_GiantVacuumDryingFurnace: 0.8 per total power tier over half the coil tier (+1); 32 parallels per section
        // and coil tier.
        put(
            machines,
            "Giant Vacuum Drying Furnace",
            machine().overclock(normal())
                .speed(c -> speedOf((float) Math.pow(0.8F, totalPowerTier(c)) / ((coil(c) + 1) * 0.5F)))
                .parallels(c -> (int) Math.min((long) pieces(c) * (coil(c) + 1) * 32, Integer.MAX_VALUE))
                .controls(COIL_CONTROL, PIECES_CONTROL));
        // TST_AdvCircuitAssemblyLine: 16 parallels, perfect.
        put(
            machines,
            "Advanced Circuit Assembly Line",
            machine().overclock(perfect())
                .parallels(16));
        // TST_IntegratedAssemblyMatrix: 1.5x the time without nanites; silver nanites bring it to 1x at 256, gold to
        // 0.5x at 1024 and double the 128 parallels.
        put(
            machines,
            "Integrated Assembly Matrix",
            machine().overclock(normal())
                .speed(c -> speedOf(assemblyMatrixDuration(c.tier(NANITES), number(c, NANITE_COUNT))))
                .parallels(c -> c.tier(NANITES) == 2 && number(c, NANITE_COUNT) > 0 ? 256 : 128)
                .controls(NANITES_CONTROL, NANITE_COUNT_CONTROL));
        // TST_MegaSolarPanelFactory: 1 / (component casing meta + 1), perfect.
        put(
            machines,
            "Mega Solar Panel Factory",
            machine().overclock(perfect())
                .speed(c -> speedOf(1F / (c.tier(COMPONENT_CASING) + 1)))
                .parallels(Integer.MAX_VALUE)
                .controls(COMPONENT_CASING_CONTROL));

        TstMachinesWireless.register(machines);
        TstMachinesCores.register(machines);
    }

    /**
     * An entry, its parallels as the game's ParallelHelper pays for them ({@link #paid}; a fixed run on the wireless
     * network pays nothing from hatches), the maps' settings hidden.
     */
    static void put(final Map<String, Behaviour> machines, final String name, final Behaviour behaviour) {
        final Coefficient structure = behaviour.parallels, power = behaviour.power, fixed = behaviour.fixedTicks;
        behaviour.parallels(
            c -> MachineTable.resolve(fixed, c, 0) > 0 ? MachineTable.resolve(structure, c, 1)
                : paid(c, MachineTable.resolve(structure, c, 1), MachineTable.resolve(power, c, 1)));
        machines.put(name, behaviour.hidesControls(MAP_CONTROLS));
    }

    // region Machine-specific pieces

    /** PhotonControllerUpgradeCasing.speedIncrement by the setting's position: none, then meta 0 (LV) to 13 (MAX). */
    static final int[] PHOTON_INCREMENT = { 0, 100, 200, 300, 400, 500, 1000, 2000, 4000, 7000, 10000, 14000, 19000,
        25000, 32000 };

    private static final Web.Control PIECES_CONTROL = count(PIECES, "Middle Sections", 1, null);

    /** The Space Scaler's field setting on the particle collider: 1 + 4 per tier past the third, every output. */
    private static final Web.Control COLLIDER_FIELD;

    static {
        final double[] multipliers = new double[FIELD_OPTIONS.size()];
        for (int i = 0; i < multipliers.length; i++) multipliers[i] = 1 + 4 * Math.max(0, i + 1 - 3);
        COLLIDER_FIELD = choice(FIELD, "Field Generator", FIELD_OPTIONS, multipliers);
    }

    private static int rings(final Context c, final int least) {
        return Math.max(least, number(c, RINGS));
    }

    private static int pieces(final Context c) {
        return Math.max(1, number(c, PIECES));
    }

    /** The field generator tier, position + 1. */
    private static int field(final Context c) {
        return field(c, FIELD);
    }

    private static int field(final Context c, final String id) {
        return c.tier(id) + 1;
    }

    /** GTRecipe.mSpecialValue: the recipe's heat on the coil machines' maps, 0 where NEI shows none. */
    static long recipeSpecial(final Context c) {
        return c.recipeSpecialValue != null ? c.recipeSpecialValue : 0;
    }

    /** The Thermal Energy Devourer's k: 1 + the hatches' EU tier, rounded down. */
    private static int devourerCoefficient(final Context c) {
        return 1 + (int) voltageTier(totalEu(c));
    }

    /** glassTier < 12 && glassTier < GTUtility.getTier(recipe.mEUt) refuses the recipe. */
    static String glassGate(final Context c) {
        return glass(c) < 12 && glass(c) < recipeTier(c) ? GLASS_GATE : null;
    }

    private static String tentacleGate(final Context c) {
        final int casing = c.tier(COMPONENT_CASING);
        if (on(c, COMPONENT_ASSEMBLY_LINE) && c.recipeSpecialValue != null && c.recipeSpecialValue > casing + 1)
            return CASING_GATE;
        return recipeTier(c) > casing + 2 ? CASING_GATE : null;
    }

    /** TST_IntegratedAssemblyMatrix.prepareProcessing: the duration multiplier for its nanites (0 none). */
    static float assemblyMatrixDuration(final int nanites, final int amount) {
        if (nanites == 0 || amount <= 0) return (float) 1.5d;
        if (nanites == 1) {
            final double fastest = 1d / 1;
            if (amount >= 256) return (float) fastest;
            return (float) (fastest + (1.5d - fastest) * (256 - amount) / 256);
        }
        final double fastest = 1d / 2;
        if (amount >= 1024) return (float) fastest;
        return (float) (fastest + (1.5d - fastest) * (1024 - amount) / 1024);
    }

    // endregion
}
