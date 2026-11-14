package com.gtnhplanner.power.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.power.PowerSetting;
import com.gtnhplanner.power.PowerSetting.Option;
import com.gtnhplanner.power.PowerSource;
import com.gtnhplanner.power.SettingsReader;

/**
 * The processing multiblocks with no recipe page (the ETO fork; docs/design/nr-process.md): the Extreme Industrial
 * Greenhouse ({@link NrGreenhouse}), the Integrated Ore Factory ({@link NrOreFactory}) and the Large Essentia Smeltery
 * ({@link NrEssentiaSmeltery}), in the picker's Other column. What they grow, process and smelt is the running game's,
 * read on first use (power/game/Nr*Game); a game without the machine's mod, and the tests, have none of it, and then
 * that machine is left out. Their items and fluids are {@link NrResources} flows.
 */
public final class NrProcess {

    /** GTValues.V: the voltage of each tier, ULV to MAX, then the error tier 15 the game's arrays carry. */
    static final long[] V = { 8L, 32L, 128L, 512L, 2_048L, 8_192L, 32_768L, 131_072L, 524_288L, 2_097_152L, 8_388_608L,
        33_554_432L, 134_217_728L, 536_870_912L, Integer.MAX_VALUE - 7, 8_589_934_592L };
    /** GTValues.VN, the tier names; the head's tier chip knows them. */
    static final String[] TIERS = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV",
        "UXV", "MAX" };

    /** The energy hatch setting's id: "tier", so the card's head steps it with its tier chip. */
    static final String TIER = "tier";
    static final String AMPS = "amps";
    /** Laser hatches reach this many amps (the website's own cap on a card's amps). */
    private static final double MAX_AMPS = 16_777_216;

    /** The energy hatch tier, LV to MAX. */
    static PowerSetting.Select tierSetting(final String defaultTier) {
        final List<Option> options = new ArrayList<>();
        for (int t = 1; t < TIERS.length; t++) options.add(new Option(TIERS[t], TIERS[t]));
        return new PowerSetting.Select(TIER, "Energy hatch", options, defaultTier);
    }

    /** The amps of all energy hatches together: two for each ordinary hatch (MTEHatchEnergy.maxAmperesIn). */
    static PowerSetting.Number ampsSetting() {
        return new PowerSetting.Number(AMPS, "Energy hatch amps", 1, MAX_AMPS, 1, 2, "A", null);
    }

    /** A tier's index (LV is 1); the last tier for a name it does not know. */
    static int tierIndex(final String name) {
        for (int t = 0; t < TIERS.length; t++) if (TIERS[t].equals(name)) return t;
        return TIERS.length - 1;
    }

    /** A tier's name; "MAX" past it (the game's error tier 15 has none). */
    static String tierName(final int tier) {
        return tier >= 0 && tier < TIERS.length ? TIERS[tier] : "MAX+";
    }

    /**
     * getMaxInputEu (ExoticEnergyInputHelper.getTotalEuMulti): every energy hatch's voltage times its amps, summed; the
     * hatches here are all of one tier.
     */
    static long maxInputEu(final SettingsReader read) {
        final long amps = Math.max(1, (long) Math.floor(read.number(AMPS)));
        return V[tierIndex(read.select(TIER))] * amps;
    }

    // region registry

    private static List<PowerSource> sources;

    public static synchronized List<PowerSource> sources() {
        if (sources == null) sources = sources(
            load("kubatech.api.eig.EIGBucket", "Extreme Industrial Greenhouse", NrProcess::greenhouse),
            load(
                "gregtech.common.tileentities.machines.multi.MTEIntegratedOreFactory",
                "Integrated Ore Factory",
                NrProcess::oreFactory),
            load(
                "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager",
                "Large Essentia Smeltery",
                NrProcess::smeltery));
        return sources;
    }

    /** The machines these parts of the game give, in the picker's order; none for a part that is missing. */
    public static List<PowerSource> sources(@Nullable final NrGreenhouse.Data greenhouse,
        @Nullable final NrOreFactory.Data oreFactory, @Nullable final NrEssentiaSmeltery.Data smeltery) {
        final List<PowerSource> out = new ArrayList<>();
        if (greenhouse != null && !greenhouse.seeds()
            .isEmpty()) {
            out.add(NrGreenhouse.source(greenhouse));
            NrResources.machine(NrGreenhouse.ID, NrGreenhouse.MACHINE);
        }
        if (oreFactory != null && !oreFactory.ores()
            .isEmpty()) {
            out.add(NrOreFactory.source(oreFactory));
            NrResources.machine(NrOreFactory.ID, NrOreFactory.MACHINE);
        }
        if (smeltery != null) {
            out.add(NrEssentiaSmeltery.source(smeltery));
            NrResources.machine(NrEssentiaSmeltery.ID, NrEssentiaSmeltery.MACHINE);
        }
        return out;
    }

    private static NrGreenhouse.Data greenhouse() {
        return com.gtnhplanner.power.game.NrGreenhouseGame.read();
    }

    private static NrOreFactory.Data oreFactory() {
        return com.gtnhplanner.power.game.NrOreFactoryGame.read();
    }

    private static NrEssentiaSmeltery.Data smeltery() {
        return com.gtnhplanner.power.game.NrEssentiaSmelteryGame.read();
    }

    /**
     * One machine's game data: null where the class it needs is absent (as in the tests, or a pack without the mod), or
     * when reading it fails, which leaves that machine out.
     */
    @Nullable
    private static <T> T load(final String probe, final String machine, final Supplier<T> read) {
        try {
            Class.forName(probe, false, NrProcess.class.getClassLoader());
        } catch (final ClassNotFoundException | LinkageError e) {
            return null;
        }
        try {
            return read.get();
        } catch (final RuntimeException | LinkageError e) {
            GtnhPlanner.LOG.warn("[nr-process] could not read the game's data for the {}; it is left out", machine, e);
            return null;
        }
    }

    // endregion

    private NrProcess() {}
}
