# Resource multiblocks with no recipe page

The ETO fork's Void Miners, Planetary Gas Siphon and Space Pumping Modules: machines that make ores or fluids out of
a dimension or a planet, which upstream never modelled because no NEI page lists what they make at what rate. Each is
a non-recipe machine (a power source in `power/`, group Other, after the Railcraft Water Tank), worked from GregTech
5.09.54.265's source (the pack's version, which carries bartworks, galacticgreg, gtneioreplugin and
gtnhintergalactic). Line numbers below are that source's (`GT5-Unofficial/src/main/java/`).

## Where things are

- `power/sources/NrMiners` (Void Miners) and `NrPumps` (siphon and pump modules): settings and the pure maths.
- `power/sources/NrData`: the tables they run on. `power/game/NrGameData` reads them from the running game the first
  time the non-recipe catalog is built; `power/sources/NrSources` builds the machines from them (none when GregTech's
  classes are absent, as in the unit tests) and is the one line in `PowerRegistry.sources()`.
- `power/sources/NrResources`: flow names for game resources the website's resource map does not have,
  `nr:item:<mod:name@meta>` and `nr:fluid:<registry name>`. `PowerResources.resolve` falls back to it, so these flows
  become ports, the picker's search finds them (by the game's name in the player's language) and `call power` checks
  them like any other. It also holds each machine's controller item, which `PowerPorts.machineStack` draws.
- Tests: `NrMinersTest`, `NrPumpsTest` (made-up tables and the game's own recipe numbers, results worked by hand).

## Void Miner I, II, III

`bwcrossmod/galacticgreg/MTEVoidMinerBase.java`, `MTEVoidMiners.java` (VMLUV, VMZPM, VMUV registered with tier 1, 2, 3
at lines 133, 246, 354; MTE 12741, 12740, 12739). Source ids `nr-void-miner-1` to `-3`.

- A run is `RECIPE_DURATION_TICKS` = 10 ticks (line 84, 161) and draws one ore (line 308); batch mode makes a run 16
  times as long and draws 16, the same rate. So 2 draws a second.
- A draw is a stack of `multiplier` (line 313): `TIER_MULTIPLIER` (1, 2, 3; line 106), times
  `NOBLE_GASSES_MULTIPLIER` (4, 8, 16, 64 for neon, krypton, xenon, oganesson; `VoidMinerUtility` line 44) while the
  input hatch has the gas (line 228). The gas is taken at 1 L a draw (line 245): 2 L/s, kept or voided.
- Which ore: the dimension's drop map, each ore at its weight over the map's whole weight (`VoidMinerUtility`
  lines 176-184, `DiscreteDistribution`). An ore's rate is `2 x multiplier x weight / total weight` items a second.
- EU/t: `VP[TIER_MULTIPLIER + 5]` (line 158): 30,720 (LuV), 122,880 (ZPM), 491,520 (UV), whatever the hatch, at full
  efficiency (maintenance assumed done).
- Settings: **Dimension** (the default the Overworld), **Noble gas** (None or one of the four), **Ore filter** (All
  ores, or one ore: the controller's whitelist with that ore in it; `handleOutputs` voids the rest, line 319, and the
  kept ore's rate does not change). A filter ore the dimension does not have leaves the card empty, with a warning.
- Read from the game: `VoidMinerUtility.dropMapsByDimName` (and `extraDropsByDimName`, merged unless a miner already
  merged it), which bartworks fills when loading completes (`bartworks/MainMod.java` 223, `generateDropMaps`: each
  enabled vein's primary and secondary at the vein's weight, its sporadic and between at an eighth, each small ore at
  its amount, per stone type of the dimension; GT's Tellurium in the Overworld at 8; the Deep Dark's every ore at the
  weights in the pack's GregTech config, `VoidMinerLoader` lines 34, 55, 76; bartworks' `voidMinerBlacklist` already
  applied). The dimensions offered are galacticgreg's `DimensionDef`s that can be void mined and have an ore of weight
  above zero, in the ore plugin's progression order, named "T<tier> <English name>" from the ore plugin's tier and its
  `en_US.lang` (translated where drawn, mostly by the EoH star lines already in `zh_CN.lang`). The End's outer islands
  (`EndAsteroid`) are left out: a miner there is in "The End". The noble gases are `NOBLE_GASSES` and their multipliers.
- Ore names and the noble gas names are the game's, in the player's language.

## Planetary Gas Siphon

`gtnhintergalactic/tile/multi/MTEPlanetaryGasSiphon.java` (MTE 15559; not the deprecated controller 14002), recipes
`gtnhintergalactic/recipe/GasSiphonRecipes.java`. Source id `nr-planetary-gas-siphon`.

- The planet the station orbits and the depth (the programmed circuit) pick the fluid (line 308): Jupiter, Saturn,
  Uranus, Neptune, depths 1 to 4, read from `GasSiphonRecipes.RECIPES`.
- Base EU/t `depth x (4 << (2 x tier + 2))` (GasSiphonRecipes line 21, siphon line 369). The game takes the tier from
  the station's celestial body; GalaxySpace 1.1.144's `SolarSystemPlanets` gives the Jupiter, Saturn, Uranus and
  Neptune stations tiers 3, 5, 5, 6, the recipes' own tiers, which the card uses.
- Overclocks: `floor(log10(V / base) / log10(4))` (line 373), V the one energy hatch's voltage (`checkHatchExact`
  Energy 1, line 413; its amps do not count). Each doubles the litres and quadruples the EU/t (lines 386, 387).
  Negative: the siphon does not run (the card says so).
- A run lasts `(int) (20 x 1 / (2 + 0.1 x coil tier))` ticks in float steps (lines 390, 509): Cupronickel 9, Kanthal
  9, Nichrome to HSS-G 8, HSS-S to Naquadah Alloy 7, Trinium to Hypogen 6, Eternal 5. Batch mode multiplies litres and
  time alike (line 394).
- Litres a second: `litres x 20 / ticks`. It needs `depth x 64` mining pipes in the input bus (line 354), not used up.
- Settings: **Planet**, **Depth**, **Energy hatch** (EV, the least any depth needs, to MAX), **Heating Coil**.

## Space Pumping Module Mk-I, Mk-II, Mk-III

`gtnhintergalactic/tile/multi/elevatormodules/TileEntityModulePump.java` (MTE 14010, 14011, 14014), recipes
`gtnhintergalactic/recipe/SpacePumpingRecipes.java`. Source ids `nr-space-pump-1` to `-3`.

- Recipe slots and parallels: Mk-I 1 slot of 4, Mk-II 4 of 4, Mk-III 4 of 64 (lines 412-414, 501-503, 590-592).
- Each slot set to a (planet type, gas type) with a recipe and parallels above zero pumps `litres x parallels x batch`
  in a run of `20 x batch` ticks (lines 243-266): litres x parallels a second. Slots on one fluid add up.
- EU/t: `(int) VP[9]` = 1,966,080 for each parallel in use (lines 60, 256), from the module's buffer, which the Space
  Elevator fills; modules fix their own maintenance, so at full efficiency. The module only starts with
  `1,966,080 x slots x most parallels` EU stored (line 215), which its buffer holds.
- Settings: **Recipe 1..n** (None, or a recipe, labelled with the game's fluid name and "(planet type, gas type)" as
  the controller takes them) and **Parallels 1..n** (0 to the module's most; the game's default is the most). The first
  slot starts on the first recipe; the game's own default (planet 1, gas 1) pumps nothing.
- Read from the game: `SpacePumpingRecipes.RECIPES` (its Ender Goo and GalaxySpace's unknown water only where those
  mods are).

## Limits

- Output space is assumed: a full output bus or a locked output hatch short of room throttles the game, not the card.
- The Void Miner's whitelist can hold several ores and can be a blacklist; the card keeps one ore or all. The rest of
  the miner's output is the same either way, so unwanted ores can be left unwired.
- Rates are expectations: the miner draws at random. The game's weights are floats normalised in float; the card
  divides in double (well under a millionth apart).
- The tables are read once, on first use: a language change shows the old names until the game restarts.
- A multiplayer server with other ore configs than the client's has other tables; the card shows the client's.
