package com.gtnhplanner.power.sources;

import java.util.List;

/**
 * What the resource multiblocks with no recipe page run on, as the running game holds it (read by
 * {@code power/game/NrGameData}; the tests build their own): the Void Miners' drop table per dimension and their
 * noble gases, the Planetary Gas Siphon's planets, and the Space Elevator pump modules' recipes. Flow names are
 * {@link NrResources} names. Labels of things the game names (ores, fluids) are the game's own, in the player's
 * language; dimension and planet labels are English, translated where drawn.
 */
public record NrData(List<Dimension> dimensions, List<NobleGas> nobleGases, List<SiphonPlanet> siphonPlanets,
    List<PumpRecipe> pumpRecipes) {

    public static final NrData EMPTY = new NrData(List.of(), List.of(), List.of(), List.of());

    public NrData {
        dimensions = List.copyOf(dimensions);
        nobleGases = List.copyOf(nobleGases);
        siphonPlanets = List.copyOf(siphonPlanets);
        pumpRecipes = List.copyOf(pumpRecipes);
    }

    /**
     * A dimension a Void Miner can work in: {@code key} is its galacticgreg dimension name (the drop table's key),
     * {@code label} "T1 Moon", and its drop table (VoidMinerUtility.DropMap, the extra drops merged in).
     */
    public record Dimension(String key, String label, List<Ore> ores) {

        public Dimension {
            ores = List.copyOf(ores);
        }

        /** The table's whole weight, the sum the miner's draw is normalised by. */
        public double totalWeight() {
            double total = 0;
            for (final Ore ore : ores) total += ore.weight();
            return total;
        }
    }

    /** One drop: the ore (a {@link NrResources#item} flow), its name, and its unnormalised weight. */
    public record Ore(String flow, String label, float weight) {}

    /** A noble gas the miners take (VoidMinerUtility.NOBLE_GASSES): its fluid and its output multiplier. */
    public record NobleGas(String key, String label, String flow, int multiplier) {}

    /**
     * A gas planet the siphon can orbit (GasSiphonRecipes.RECIPES): {@code key} as the game keys it
     * ("planet.jupiter"), its English name, its tier, and the gas at each depth.
     */
    public record SiphonPlanet(String key, String label, int tier, List<SiphonGas> gases) {

        public SiphonPlanet {
            gases = List.copyOf(gases);
        }
    }

    /** The fluid a siphon pumps at one depth, and how much one run makes before overclocks. */
    public record SiphonGas(int depth, String flow, String label, int amount) {}

    /**
     * One pumping recipe (SpacePumpingRecipes.RECIPES): the planet and gas type the module is set to, the fluid, and
     * the litres one parallel pumps a run.
     */
    public record PumpRecipe(int planetType, int gasType, String flow, String label, int amount) {

        /** The select key a recipe is stored under: "planet:gas". */
        public String key() {
            return planetType + ":" + gasType;
        }
    }
}
