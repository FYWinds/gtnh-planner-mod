package com.gtnhplanner.power.sources;

import java.util.ArrayList;
import java.util.List;

import com.gtnhplanner.power.PowerSource;

/**
 * The resource multiblocks with no recipe page (the ETO fork): the Void Miners (NrMiners), the Planetary Gas Siphon
 * and the Space Pumping Modules (NrPumps), in the picker's Other column. Their tables are the running game's, read
 * once on first use (power/game/NrGameData); a game without GregTech, and the tests, have none, and then neither do
 * these machines.
 */
public final class NrSources {

    private static List<PowerSource> sources;

    public static synchronized List<PowerSource> sources() {
        if (sources == null) sources = sources(load());
        return sources;
    }

    /**
     * The machines these tables give, none for a part with nothing to mine or pump; each registers its controller as
     * its picture.
     */
    public static List<PowerSource> sources(final NrData data) {
        final List<PowerSource> out = new ArrayList<>(NrMiners.sources(data));
        out.addAll(NrPumps.sources(data));
        for (final PowerSource source : out) {
            final String miner = NrMiners.icon(source.id());
            NrResources.machine(source.id(), miner != null ? miner : NrPumps.icon(source.id()));
        }
        return out;
    }

    /** The game's tables; empty where GregTech's classes are absent (as in the tests) or do not link. */
    private static NrData load() {
        try {
            Class.forName("bwcrossmod.galacticgreg.VoidMinerUtility", false, NrSources.class.getClassLoader());
            return com.gtnhplanner.power.game.NrGameData.read();
        } catch (final ClassNotFoundException | LinkageError e) {
            return NrData.EMPTY;
        }
    }

    private NrSources() {}
}
