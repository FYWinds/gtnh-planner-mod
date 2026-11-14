# Processing multiblocks with no recipe page

The ETO fork's Extreme Industrial Greenhouse, Integrated Ore Factory and Large Essentia Smeltery: machines that work
out what they make in their own code, which upstream never modelled because no NEI page lists it. Each is a
non-recipe machine (a power source in `power/`, group Other, after the Railcraft Water Tank and the resource machines
of `nr-sources.md`), worked from GregTech 5.09.54.265's source, the pack's version, which carries kubatech and
GoodGenerator. Line numbers below are that source's (`GT5-Unofficial/src/main/java/`).

## Where things are

- `power/sources/NrGreenhouse`, `NrOreFactory`, `NrEssentiaSmeltery`: settings and the pure maths, each with a `Data`
  interface for what the running game knows. `NrProcess` builds the machines (none for a part of the game that is
  missing, as in the unit tests) and is the one line in `PowerRegistry.sources()`; it also holds the shared energy
  hatch settings.
- `power/game/NrGreenhouseGame`, `NrOreFactoryGame`, `NrEssentiaSmelteryGame` (with `NrProcessGame`, their shared
  helpers): the game side, loaded only after `NrProcess` has found the class it needs (`Class.forName` without
  initialising, and every read guarded: a `LinkageError` or exception leaves that one machine out, with a log line).
- Items and fluids are `NrResources` flows (`nr:item:<mod:name@meta>`, `nr:fluid:<name>`, `nr-sources.md`), named in
  the player's language when read, so they become ports and the picker's search finds them. Each machine's controller
  is registered there as its picture: MetaTileEntity 12792, 15564 and 32024.
- The energy hatch is two settings on all three: **Energy hatch** (`tier`, LV to MAX, stepped by the card's tier chip)
  and **Energy hatch amps** (the amps of all hatches together; an ordinary hatch is 2 A, `MTEHatchEnergy.maxAmperesIn`).
  `getMaxInputEu` is their product (`ExoticEnergyInputHelper.getTotalEuMulti`: each hatch's voltage times
  `maxWorkingAmperesIn`).
- Tests: `NrGreenhouseTest`, `NrOreFactoryTest`, `NrEssentiaSmelteryTest` (made-up seeds, ores and aspects; every
  number worked by hand from the formulas below).

## Extreme Industrial Greenhouse

`kubatech/tileentity/gregtech/multiblock/MTEExtremeIndustrialGreenhouse.java` (checkProcessing from line 774),
`eigmodes/EIGNormalMode.java`, `eigbuckets/`. Source id `extreme-industrial-greenhouse`. One card is one greenhouse
growing one seed type in its operational phase.

- Voltage tier: `(int) (ln(getMaxInputEu / 8) / ln 4 + 1e-8)` (`KubaTechGTMultiBlockBase` line 135), so amps count:
  two 2 A EV hatches are 8,192 EU/t and run it at IV.
- Slots: `2^(tier - 4)` seed types from EV, none below (`EIGNormalMode.getSlotCount`, line 84;
  `EIG_BALANCE_REGULAR_MODE_MIN_TIER`, line 123), 64 seeds each (`getSeedCapacityPerSlot`). One card uses one slot's
  worth or more of one seed: **Seeds planted** is the count, 0 meaning as many as the slots hold. More than they hold
  is `EIG_seedOverflow` (line 814): a warning, nothing made.
- A cycle is `max(20, 100 / (tier - 3))` ticks (line 881) at `(long) (V[tier] x 0.99)` EU/t (line 890).
- Each cycle takes 1,000 L of water a seed (line 833) and, from 1,000 seeds, 1 L of Weed-EX 9000 a seed (line 834;
  `getWeedEXMultiplier` 1). The card asks for both in full (short of Weed-EX the game kills seeds, line 846).
- **Fertilizer**: none, or one of the game's fertilizers (`FERTILIZER_ITEM_LIST`: bone meal and Forestry's
  fertilizer, `kubatech/loaders/RecipeLoader.java` lines 186, 197), used at 2 a seed a cycle
  (`getMaxFertilizerUsagePerSeed`, line 865). The growth multiplier is `1 + used / most x 4` (lines 876-877,
  `EIG_BALANCE_MAX_FERTILIZER_BOOST`): x5 with all of it. (The mode's `getFertilizerBoost`, 2.0, only feeds its
  tooltip.) A part supply is not offered: the card takes all or none.
- Output: each cycle every bucket adds `drops x multiplier x seeds` to a drop tracker that keeps fractions (line 883,
  `EIGDropTable`), so over time the rate is exact: `drops x multiplier x seeds x 20 / ticks` a second.
- **Seed** and what it drops are the game's. The list is every item the greenhouse's factories could take: what
  `EIGSeedBucket` plants (`ItemSeeds`, `ItemSeedFood`, Hodgepodge's `INetherSeed`), what `EIGStemBucket` plants (an
  `IPlantable` whose plant is a `BlockStem`), and the flowers, cactus, reeds and rainbow cactus that `EIGFlowerBucket`
  and `EIGRainbowCactusBucket` take. Drops come from the bucket the game would make: `EIGModes.Normal
  .tryCreateNewBucket` (simulating, nothing used up) on a greenhouse of our own in the player's world, then
  `addProgress(1, table)`: `EIGSeedBucket`'s 1,000 simulated harvests less the replanted seed, `EIGStemBucket`'s 100,
  a flower's own item. Like the game's these are a sample taken once a seed, so a card matches a greenhouse's table to
  that sampling, not to the digit. A seed the factories refuse says so on the card. The rainbow cactus draws
  `3 + round(nextDouble() x 4.91662)` random dyes each cycle (Thaumic Bases' `BlockRainbowCactus
  .addDyeDropsToOutput`): 5.4576 on average, shared equally among its `allowedDyes`.
- Not modelled: the setup phases (seed input and output), maintenance (assumed done), and the old structure. The
  Glass tier reading is the hatch tier the glass must reach (checkMachine, line 302).
- No IC2 or CropsNH mode: in this version `EIGModes` has only Normal, and `EIGIC2Bucket`'s factory makes no bucket
  (it only restores old saves). So the crop farm cards and their CropsNH data do not apply here; CropsNH seeds are not
  `IPlantable` and the greenhouse refuses them.

## Integrated Ore Factory

`gregtech/common/tileentities/machines/multi/MTEIntegratedOreFactory.java`, MetaTileEntity 15564 (the current
controller; the legacy one, 1132, `MTEIntegratedOreFactoryLegacy`, works differently and is not this card). Source id
`integrated-ore-factory`.

- Ores a run: `getMaxInputEu / 30` (`RECIPE_EUT`, line 85; line 257), the fluids and the ores in the bus allowing.
  The card asks for them in full: 200 L of distilled water and 2 L of lubricant an ore (lines 86-87), 30 EU/t an ore
  (line 325).
- A run takes the mode's fixed time (`getRecipeTickTime`, line 232): 30, 15, 10, 20, 17, 32 or 1 s, whatever the
  power. Pollution 200 a second (line 642).
- **Mode** (`ProcessingMode`) is a chain of steps (line 348 on), each taking the items of its kinds and passing the
  rest by. The kinds are the factory's hash sets, built by `initHash` (line 183) from ore dictionary names by prefix:
  `crushedPurified`, `crushedCentrifuged`, `crushed`, `dustImpure`, `dustPure`, `ore`/`rawOre`. Each step
  (`processStep`, line 434) looks its item up in the game's recipe map with the factory's own query: the macerator,
  the ore washer with distilled water, the thermal centrifuge, the centrifuge, the sifter, the forge hammer, the simple
  washer with water, the chemical bath with the fluids in the hatches. An item with no recipe passes by.
  `doCompress` (line 604) merges same items after every step and, with **Void stone dust** on (shift-screwdriver),
  drops stone, netherrack, endstone, marble and red granite dust (`VOIDABLE_STONE_DUSTS`, line 152).
- Chanced outputs (`getOutputStack`, line 579): `(int) ceil(std x gaussian + n p) x stack size` for n items at chance
  p, std = `sqrt(n p (1 - p))`, kept only above zero. The card uses that draw's expected value,
  `sum over j >= 0 of P(X > j)` for the normal X, which is `n p + 0.5` once the spread is two or more: the game's
  ceil adds half an item on average, and much more at small counts (one item at 10% averages 0.632). Items made by a
  chanced output and processed again later are carried at their expected count.
- **Bath fluid** (the chemical bath modes): the fluids chemical bath recipes take with a crushed or purified ore,
  read from the game's recipe map. The bath (`chemicalWash`, line 515) uses `getRepresentativeFluidInput(0)` a
  processed item (mercury or sodium persulfate), asked of the card in full; with none, or none for this ore, the bath
  passes the ore by and the card says so.
- **Ore** is the game's: every item in the factory's ore set, by name.

## Large Essentia Smeltery

`goodgenerator/blocks/tileEntity/MTELargeEssentiaSmeltery.java`, MetaTileEntity 32024 (GoodGenerator registers it
only with Thaumcraft). Source id `large-essentia-smeltery`.

- It smelts what is wired to it: like the fluid tank it is a holder (`power/Holders`). Empty, it shows one socket,
  Smelt item/fluid; a port dropped on it (or the socket dragged to one) makes it take that item or fluid in, kept on
  the card as its resource key (`input`); with nothing wired it lets go. An item list to pick from was not an option:
  any item with aspects can go in, most of them given aspects only when first asked (Thaumcraft's `generateTags`).
- Parallels: `(slices + 1) x 2^cell` (line 174), the middle slices being the **Length** in blocks less two (5 to 9
  blocks), **Diffusion cells** the cell tier (meta 0 to 3: novice to grandmaster). So `(length - 1) x 2^cell`.
- Each run takes that many items, or buckets of a fluid (line 340), and makes their aspects (`getEssentia`, lines 388
  and 401): Thaumcraft's object tags and bonus tags of the item, one Perditio for an item with none; a fluid's are its
  block's, none for a fluid with no block (it is still used up).
- Time: `ceil(essentia x 32 x (1 - Perditio x 0.005))` ticks (lines 95, 375), essentia being the whole run's.
  **Perditio drawn per 5 ticks**: every 5 ticks it drains up to 125 centivis of Perditio from the vis network and
  counts `min(100, drained)` (line 514); 100 halves the time.
- Power: GregTech's `OverclockCalculator` from 480 EU/t (line 96) on `getMaxInputEu`, perfect overclocks (line 377):
  as many as the power is whole tiers above 480 (`getTiersAbove`, line 524) and the summed EU's voltage tier is above
  HV's, each x4 EU/t and a quarter of the time, never under a tick (`calculateOverclock`, line 385). Only ordinary
  energy hatches fit (`HatchElement.Energy`), and TecTech's base counts only those in `getMaxInputEu`.
- Node power: each run uses `(int) (tier^2 x 1.15f)` of it (`expectedPower`, line 420; the tier of the best energy
  hatch), drawn as Aqua and Ignis centivis from the vis network (`drainNodePower`, line 483). The card shows it as
  centivis a second; a smeltery short of it stops, which the card does not model.
- **Purification**: the node purification efficiency, 0 to 100 (the game's starting 0; Ordo raises it). Pollution
  is `22 x (100 - purification)` a second (line 588), and one tick in twenty puts flux gas on a muffler as often as
  purification falls short of 100 (line 521): `(100 - purification) / 100` a second. The Ordo it takes to hold a
  level is not worked out.

### Essentia on the card

The planner has no port kind for essentia: a port holds an item, a fluid or EU (`RecipePropertyAPI`), keyed by
registry name and meta, and every item form of essentia (phials, crystals, ThaumicEnergistics' crafting aspect) tells
its aspect by NBT, which keys drop. The smallest representation that fits is the one machines already use for what
they draw without a port, as EU on a machine card and steam on a steam machine are a figure on the card rather than a
port: each aspect is a flow named for it (its Latin name, `Aspect.getName`, the same in every language), which no
resource stands for, so the card lists it with its readings, "Aqua 10/s out" (`PowerPorts.unported`). It is not wired,
drawn in the overview or posted. `NrEssentiaSmelteryTest.essentiaIsReadingsNotPorts` holds that the aspect names
resolve to nothing. A real port kind would be a new `ResourceProperty` like `POWER` with its own key
(`essentia:<aspect>`) through `Resources`, saves, drawers, the overview, icons and the website's plan format.

## Limits

- The three machines are read once, the first time the non-recipe catalog is built. The greenhouse's seed drops are
  simulated on first use and kept, but never before the player is in a world (stems drop through it).
- The picker's search computes every option of every select: the first open runs every seed's simulation and every
  ore's chain once.
- Not checked in the game yet: a greenhouse card's wheat (or any seed) against a running greenhouse's drop table, the
  ore factory's chain against its output for one ore in each mode, the smeltery's essentia and cycle against one run.
