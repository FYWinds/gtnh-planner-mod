# TwistSpaceTechnology machines

TwistSpaceTechnology (TST) 0.8.0's recipe-processing multiblocks on cards, for the ETO GTNH 2.9.0 pack. The website's
data knows none of them: without this a TST machine picked on a card ran as the map's first machine. Every number
below is from the TST source (`common/machine/...`) with the pack's `config/TwistSpaceTechnology.cfg`, whose relevant
values all equal TST's defaults.

## How a TST machine reaches a card

1. **Picking.** GT registers each controller's `getAvailableRecipeMaps()` as NEI catalysts, so NEI already offers the
   TST machine on those maps; `MachineChoices` lists it, and `WebCards.handlerId` matches the catalyst's item id
   (`gregtech:gt.blockmachines@<MTE id>`) against a handler's `items`.
2. **Handlers.** `assets/gtnhplanner/machines/eto-tst.json` is a second file in `handlers.json`'s shape, merged by
   `HandlerData` (`EXTRA_FILES`, `merge`) after the website's file:
   - On a map the website has, the TST templates are appended to its `handlers` and recorded in `packHandlers`.
   - On a map whose website recipes list no machine (`handlersOnRecipes` false: cyclotron, EIC, multielectro,
     component assembly line, precise assembler, vacuum furnace, dehydrator, `bw.recipe.cal`), `HandlerData.handlers`
     now returns the website's own map machine first (`RecipeRules.mapMachine`, same id and label as the old
     fallback, multiblock where the table says so), then the TST templates. A card with no pick still runs the
     map's machine.
   - TST's own maps (17, `tst.common.recipe.*`) are new entries with English names and their machines.
3. **Maths.** A template's `label` names a machine table entry. `MachineTable` keeps the TST entries in a separate
   `EXTRA` map filled by `machines/eto/TstMachines.register`; they are found by name (`behaviour`) but never listed
   in `names()`, and a name the website's table already has stays the website's. Each entry gives the speed (the
   inverse of TST's float speed bonus, so durations truncate as the game's), the EU modifier, the structural
   parallels, the overclock rule, settings (`TstControls`) and recipe gates.
4. **Pool.** `MachineTable.Context` gained `poolEuT` (the card's tier × amps), so formulas can read TST's
   `getMaxInputEu()`-based tiers: `getTotalPowerTier` (`getTierExtended`, the tier whose voltage carries the whole
   sum: 2 A of IV is LuV) and `calculateVoltageTier` (`floor((log2 EU - 3) / 2)`).
5. **Rounding.** `TstMachines.paid` caps every entry's parallels as GT's ParallelHelper does: each parallel billed at
   `ceil(EU/t × EU modifier)`. The website's own limit does not round (a 0.6× Scavenger would get 455 parallels where
   the game runs 431 at IV).

Conventions the owner may want to know:

- **One hatch.** GT runs a single normal hatch at 1 A but counts its 2 A in `getMaxInputEu()`. The card's amps feed
  both, so for a one-hatch TST build set 2 A to get the total-power tier right (parallels then also assume 2 A).
- **Summed hatch voltage** (Industrial Magnetar Separator, Manufacturing Center: `getTier(getMaxInputVoltage())`) is
  read from the website's `voltageTier`, which assumes 2 A hatches (4 A of IV = two IV hatches = LuV).
- **Crystalline Infinitier**: a controller whose mode was never cycled runs the autoclave at 1×, not 1/4
  (`speedBonus` is only set in `setMachineMode`). The card models the intended 1/4.
- **Ball Lightning**: the arc furnace is mode 0 (mode 1 is the same map, slower).
- **Photonic Quantum Master**: one intensifier tier for all 25 slots.

## Export and share

`PlanExport` posts the card's recipe with its `machineHandlers`, settings and `machineHandlerId` as before; nothing
is dropped. On the website (gtnh-factory-flow `refresh-project-recipes.ts`) a recipe it knows is refreshed from its
own dataset, so a TST card on a GT map shows the map's own machine there (its handler list lacks TST); a recipe on a
TST map is kept as posted. The plan in the mod is unchanged either way. No notice is shown: the card is not
downgraded by the export itself.

## Inventory

Done = the card runs the machine's own speed, EU, parallels and overclock. Maps: `gt.recipe.` prefix left out; TST's
own maps by their English name.

| ID | Machine | 中文 | Maps | Status | Settings / rule missing |
|---|---|---|---|---|---|
| 19001 | Intensify Chemical Distorter | 深度化学扭曲仪 | Intense Chemical Distorter; largechemicalreactor | done | coil (gates recipe heat) |
| 19002 | Precise High-Energy Photonic Quantum Master | 精密高能光量子掌控者 | laserengraver; Photon Manipulator | done | intensifier tier |
| 19003 | Miracle Top | 奇迹顶点 | Miracle Top; Quantum Inversion | done | rings |
| 19004 | Magnetic Drive Pressure Former | 磁驱压力成型机 | extruder, metalbender, press, hammer | done | coil |
| 19005 | Physical Form Switcher | 物质形态转换器 | fluidsolidifier, fluidextractor | done | glass (gate) |
| 19006 | "Mini" Magnetic Mixer | "小型"磁力搅拌机 | multimixer | done | — |
| 19007 | Magnetic Domain Constructor | 磁畴构建器 | electromagneticseparator, polarizer | done | rings |
| 19008 | Silksong | 丝之歌 | wiremill | done on hatches | coil, sections; wireless (≥94 sections, no hatches): fixed 188 t × 100 batches on network EU |
| 19009 | Holy Separator | 神圣分离者 | lathe | lathe done | sections; cutting runs `cuttingsaw` but NEI lists `fakecuttingsaw`, which the website data lacks (all cutters) |
| 19010 | Space Scaler | 空间缩放仪 | compressor, extractor, cyclotron, EIC, neutroniumcompressor | done | field generator; compressor CompressionTierKey gate not modelled (no recipe metadata) |
| 19011 | Molecule Deconstructor | 分子解构器 | multielectro, multicentrifuge | done | sections |
| 19012 | Crystalline Infinitier | 无限晶胞 | autoclave, chemicalbath; Crystalline Infinitier | done | glass, field (gate) |
| 19013 | DSP Launch Site | — | — | not processing | launcher |
| 19014, 19015 | Dyson Sphere Ray Receiving Station, Artificial Star | 戴森球射线接收站, 人造恒星 | — | done as power sources | see Generators |
| 19016 | Miracle Door | 奇迹之门 | Stellar Forge maps | not done | always wireless: fixed 512/1280 t over a circuit, 100 batches, critical photons |
| 19017 | General Ore Processing Factory TST | 通用矿物处理厂TST | — | not processing | own ore logic |
| 19018, 19500 | ElvenWorkshop | 精灵工坊 | Rune Engraver; Mana Infuser | done | — |
| 19019 | HyperSpacetimeTransformer | 极限时空转换仪 | Hyper Spacetime Transformer | done | fusion coil, cooling, matrix (gate) |
| 19020 | General Circuit Converter | 通用电路板转换器 | — | not processing | oredict conversion |
| 19021 | Large Industrial Coking Factory | 大型工业炼焦厂 | Coking Factory | done | coil |
| 19022 | Mega Bricked Blast Furnace | 巨型砖高炉 | — | not processing | primitive, fuel-efficiency over run time |
| 19023 | Scavenger | 拾荒者 | sifter | done | — |
| 19024 | CleanRoom | TST超净间 | — | not processing | cleanroom |
| 19025 | Biosphere III | 生物圈III号 | brewer, fermenter | brewing/fermenting done | glass; bacterial vat modes scale fluids by hatch fill and Sievert (not a coefficient) |
| 19026 | Tower of Abstraction (Mega Egg Generator) | 抽象之塔 | — | done as a power source | pieces, infinity, dragon and creeper eggs; see Generators |
| 19027 | Advanced Mega Oil Cracker | 进阶巨型石油裂化机 | craker | done | coil |
| 19028 | Indistinct Tentacle | 不可视之触 | Mega Assembly Line, componentassemblyline, assembler, precise_assembler | done on hatches | component casing (gates); wireless (UMV glass and casing, no hatches): fixed 512 t × 100 batches |
| 19029 | Astral Computing Array | 星规阵列 | — | not processing | computation |
| 19030 | Thermal Energy Devourer | 热能饕餮 | vacuumfreezer | done on hatches | mode; wireless with a tesseract: fixed 128 t × 100 batches |
| 19031 | Vacuum Filter Extractor | 真空抽滤器 | distillationtower, distillery | done | — |
| 19032 | Large Steam Forge Hammer | 大型蒸汽锻造锤 | hammer | not done | steam at 1.33× EU with no pressure tier, parallels capped by 8 × 16·casing EU; PowerReport's steam is 1.25 × pressure |
| 19033 | Large Steam Alloy Smelter | 大型蒸汽合金炉 | alloysmelter | not done | as above, 48 parallels on 24576 EU |
| 19034, 19035 | Eye of Wood, Bee Engineer | 武德之眼, 蜜蜂操纵者 | — | not processing | ore generator, bee breeding |
| 19036 | "Mini" Household Cell Fragmentizer | "小型"家用破壁机 | macerator | done | glass (speed and gate), structure block |
| 19037 | Hephaestus' Atelier | 赫菲斯托斯的工坊 | alloysmelter | alloy smelting on Molecular coils done | furnace mode (item-count EU, no map); T2/T3 coils force wireless |
| 19038 | Deployed Nano Core | 展开的纳米核心 | Nano Core | done on hatches | wireless without hatches: 128 t × 100 batches |
| 19039 | Core Device of Human Power Generation Facility | 人类能源设施的核心装置 | fluidheater | done | coil |
| 19040, 19041 | Starcore Miner, TST Large Disassembler | 星核钻机, TST大型拆解机 | — | not processing | miner, disassembly |
| 19042–19045 | Space Apiary Module MK-I–IV | 太空蜂箱模块 | — | not processing | bee simulation |
| 19046 | BallLightning | 球状闪电 | arcfurnace; Star Kernel Generator | arc and star kernel done | machine tier, fusion coil, coil (Infinity+), field, glass; fusion mode (FusionMaxEut cap, fusion tier gate); wireless at tier 4 without hatches |
| 19047 | Industrial Magic Matrix | 工业注魔矩阵 | Industrial Magic Matrix | not done | speed from world node aspects, parallels from networked essentia |
| 19048 | Large Canner | 大型装罐机 | canner | done | — |
| 19049 | MegaArray (Big Bro Array) | 大哥阵列 | inserted machine's map | not done | map and voltage gate come from the inserted machine item |
| 19050 | Industrial Magnetar Separator | 工业电磁离析机 | electromagneticseparator | done | — |
| 19051, 19052 | Eco-Sphere Simulator, Extreme Crafting Center | 拟似生态圈, 梦魇工业合成中心 | — | not processing | own simulation, AE2 crafting |
| 19053 | Lightning Spire | 闪电尖塔 | — | done as a power source | rods, dynamo tier and amps; see Generators |
| 19054 | DTPF Prototype MK-II | 超维度物质等离子锻炉原型机MK-II | plasmaforge | not done | execution cores share EU and split parallels, spare EU boosts, catalyst discount over run time |
| 19055 | Large Neutron Oscillator | 大型中子振荡器 | NeutronActivatorWithEU | not done | execution cores (as above) |
| 19056 | Indistinct Tentacle Prototype MK-II | 不可视之触原型机MK-II | as 19028 | not done | always wireless: 20 t per batch, lump-sum network EU |
| 19057 | Mass Fabricator : Genesis | 质量发生器 : 创世纪 | Mass Fabricator: Genesis | done | parallels (controllers + 1) |
| 19058 | PULSAR - Incompact Cyclotron | PULSAR - 非紧凑式回旋加速器 | cyclotron | done | — |
| 19059 | Strange Matter Aggregator | 奇异物质聚合器 | — | not processing | fuel-rod logic |
| 19060 | Micro SpaceTime Fabricatorio | 微型时空发生器 | Micro SpaceTime Fabricatorio | done | field, SpaceTime Seed (outputs ×2) |
| 19061 | Bloody Hell | 血狱 | Bloody Hell Altar | done | structure tier, speed runes; orb tier gate not modelled |
| 19062 | Silicon Rock Synthesizer | 硅岩制造机 | Mega Stone Breaker | done | water and lava (outputs ×4 / ×1024; the 1000 L/s upkeep is not an input) |
| 19063 | Manufacturing Center | 加工中心 | compressor, lathe, polarizer, fermenter, fluidextractor, extractor, laserengraver, autoclave, fluidsolidifier | done | core (gate); CompressionTierKey gate not modelled |
| 19064 | Industrial Alchemy Tower | 工业炼金塔 | Industrial Alchemy Tower | not done | speed from node aspects, parallels from essentia |
| 19065 | Giant Vacuum Drying Furnace | 巨型真空干燥炉 | vacfurnace, multidehydrator | done | coil, sections |
| 19066 | TST Processing Array | TST处理阵列 | inserted machine's map | not done | no NEI catalyst; map, voltage and parallels from the inserted machine |
| 19067 | Advanced Circuit Assembly Line | 进阶电路装配线 | bw.recipe.cal (runs a copy without imprints) | done | imprint whitelist and single-hatch voltage cap not modelled |
| 19068, 19298 | Swelegfyr Blast Furnace | 熯焱高炉 | blastfurnace | not done | EU discount and heat overclocks follow a heat that rises and decays over time |
| 19069 | Hyper Thermal Convector | 高能态热对流器 | Rapid Heat Exchange, Rapid Cooling | not done | fixed 20 t, up to 128 or 16 batches of unlimited parallels, no EU |
| 19070–19072, 19075, 19077, 19078 | Primordial Disjunctus, Skypiercer Tower, Laser Meteor Miner, Infusion Material Dispenser, Nether Interface, Super Water Purifier | — | — | not processing | own logic |
| 19073, 19076 | Basic Steam Generator, Large Solar Boiler | 基础蒸汽发电机, 大型太阳能锅炉 | — | done as power sources | dynamo; boiler, water, heat, calcification; see Generators |
| 19074 | Universal Generator | 通用发电机 | — (gasTurbineFuels, dieselFuels) | done as a power source | see Generators |
| 19079 | Integrated Assembly Matrix | 集成装配矩阵 | Mega Assembly Line | done | nanites; data-stick whitelist and highest-hatch voltage cap not modelled |
| 19080 | Mega Solar Panel Factory | 巨型太阳能板工厂 | Mega Solar Panel Factory | done | component casing |
| 19081 | Mega Naquadah Reactor | 巨型硅岩反应堆 | — (gg.recipe.naquadah_reactor) | done as a power source | new in 0.8.1; fuel, coolant, excited fluid, parallels, running time; see Generators |

Not registered in 0.8.0: `TST_TombOfTheDragon`, `TST_PatternMachineClass`, `GTCM_TestMultiMachine`.

## Generators

A TST generator is not a recipe card: it is a power source (`power/`, the top bar's Non-recipe key), in
`power/sources/TstGenerators`, registered after the website's engines (`PowerRegistry`). The website has none of
them: their source ids are the fork's own, and a plan posted with one carries a `sourceId` the website does not know.
The card's picture is the controller (`PowerPorts.machineStack`, only with TST loaded).
Worked from TST tag 0.8.1 and GregTech 5.09.54.265; pinned by `TstGeneratorsTest`.

### Universal Generator (19074, `tst-universal-generator`)

In the picker's Engines column (unlock LV: its controller is a crafting recipe of an LV hull, LV robot arms and
pistons, `GTCMMachineRecipes`). Settings: Fuel, Dynamo tier, Dynamo amps.

- **Fuels.** `getRecipeMap`: the gas structure (`STRUCTURE_PIECE_GAS`, `mSetTier` 1) burns
  `RecipeMaps.gasTurbineFuels`, the fuel structure (`mSetTier` 2) `RecipeMaps.dieselFuels`. The card lists both, gas
  fuels first: the workbook's `gasFuels` and `combustionFuels`, the tables the Gas Turbine and the Combustion Generator
  burn. Ether is in both maps at 537 EU/L and listed once. The Structure reading names the build: Gas mode, Fuel
  mode, or Gas or fuel mode for Ether.
- **Output.** `checkProcessing`: `euPerTick = DYNAMO_TIER * DYNAMO_AMP`, set in `checkMachine` from
  `GTCM_MultiMachineBase.getTierDynamo` (the hatches' common `maxEUOutput`, their voltage) and `getDynamoAmperage`
  (the sum of each hatch's `getOutputAmperage`). It runs at `getMaxEfficiency` = 10,000, so EU/t is the dynamos' full
  voltage x amps whatever the fuel. `checkMachine` takes 1 to 8 dynamo hatches (`checkHatchMin`, `checkHatchMax`), all
  at HV or below (`setDynamoTier(3, false)`: ULV 8, LV 32, MV 128, HV 512 V) and of one voltage (`checkMixedDynamo`);
  GregTech's Dynamo element takes a hatch of at most 4 A (`MTEMultiBlockBase.addDynamoToMachineList`: the plain
  1 A hatch, GT++'s 4 A buffer dynamos at ULV to HV, the 4 A HV dynamo), so Dynamo amps runs 1 to 32.
- **Fuel.** Each run drains 1,000 L of the first stored fluid the map knows and lasts
  `(int) ((double) (mSpecialValue x 1,500) / (euPerTick x 20) x 20)` ticks (`fuelBurning`, `mMaxProgresstime`):
  floor(EU/L x 1,500 / EU/t) for every fuel and setting the card offers (checked over all of them). The 1,500 is the
  tooltip's 150%: 1,000 L of fuel give 1.5 x their value, less the truncated tick. A finished run starts the next on
  the same tick (`MTEMultiBlockBase.runMachine` calls `checkRecipe` when progress ends), so the card burns
  20,000 / ticks L/s at a steady EU/t. Readings: the burn ("1,000 L every N ticks"), EU per L and the efficiency
  against the fuel's own value.
- **Under a tick.** A fuel worth less than one tick of output (Fish Oil from 3,072 EU/t, Short Mead from 6,144) gets
  `mMaxProgresstime` 0: the run drains its 1,000 L and ends before it makes anything. The card makes no EU and says so.

What the power model cannot express: the 1,000 L gulps (the card averages them); the structure's mode being the
fuel's (the card shows it, the game burns nothing from the other map); several fuels in the hatches (the game burns
the first it finds each run); EU the dynamos cannot pass on is lost, and the under-a-tick case burns 1,000 L on every
recipe check, which are driven by hatch events, not a rate. TST has no maintenance, pollution or lubricant on it.

### Tower of Abstraction and Mega Naquadah Reactor

Both in `power/sources/TstMegaGenerators`, pinned by `TstMegaGeneratorsTest`; the pack's TwistSpaceTechnology.cfg and
GoodGenerator.cfg keep the defaults of everything they read.

**Tower of Abstraction** (19026, `tst-tower-of-abstraction`, `GT_TileEntity_MegaEggGenerator`). In the picker's Solar
column, the passive one (it takes nothing; unlock LuV: `GTCMMachineRecipes` assembles the controller at
`RECIPE_LuV`). Settings: Pieces, Infinity eggs, Dragon eggs, Creeper eggs; the egg positions left over are empty.

- **Structure.** `checkAllPieces`: the controller's base layer, then middle pieces of two layers stacked upward as
  long as they form (at least one), then the top block. A middle piece's upper layer has 16 egg positions (`'K'`), each
  air, a Galacticraft creeper egg, a dragon egg or Witchery's infinity egg, counted by `onElementPass` (a failing piece
  above the tower fails on a casing before it reaches one, so it counts none). Pieces run 1 to 127, as many as a world
  256 blocks tall holds. More infinity eggs than pieces is a structure error (`checkInfinityEgg`), as are more eggs than
  positions: the card makes nothing and says why. One dynamo hatch of any amperage (`MEG_Dynamo_Limit` 1), a laser only
  from 16 pieces (`checkLaser`, `MEG_Laser_Pieces`); the Dynamo reading says which.
- **Output.** `checkProcessing` runs 20 ticks at `lEUt` = `getOutputEUt`: 512 EU/t a creeper egg, 2,048 a dragon egg,
  16,384 an infinity egg (`MEG_*_Gen`, times `MEG_Overall_Multiply` 1.0 cast to long), and
  `MTEExtendedPowerMultiBlockBase.onRunningTick` sends `lEUt x mEfficiency / 10,000` (long division) every tick.
  `getMaxEfficiency` is 10,000 + 200 x `(int) (log(pieces) / log(2))` + 100 an infinity egg - 500 an empty position,
  at least 0, and may pass 10,000: 16 pieces filled with an infinity egg each and dragon eggs run at 124%, 934,543 EU/t
  from eggs worth 753,664. The card is the steady state at that maximum.
- **Warm-up.** Each run's end adds `updateEfficiencyIncrement` to `mEfficiency`, up to the maximum: 20, plus 100 an
  infinity egg, plus one a pair of dragon eggs (50 from 100 eggs on). The tooltip's 0.01% a second is stale: the base is
  0.2% a second, 500 s from cold on creeper eggs alone. The Warm-up reading is ceil(maximum / increment) seconds. The
  machine never stops once formed (no input, no maintenance), so the steady state is where it stays.

**Mega Naquadah Reactor** (19081, `tst-mega-naquadah-reactor`, `TST_MegaNqReactor`). In the Endgame column (unlock
UIV: an assembly line recipe at `RECIPE_UIV`). Settings: Fuel, Coolant, Booster (the excited fluid, the Large Naquadah
Reactor's word), Parallels, Running time.

- **Fluids.** The fuels are GoodGenerator's `naquadahReactorFuels` (`FuelRecipeLoader` at `GGConfigLoader`'s values):
  1 L of fuel to 1 L of its depleted form, the basic output (`mSpecialValue`) and duration of each, the Large Naquadah
  Reactor's table. Coolants (`getCoolantTiers`: Tachyon Rich Temporal Fluid 500% at 20 L; Gelid Cryotheum 275%, Super
  Coolant 150%, IC2 Coolant 105% at 1,000 L) and excited fluids (`getExcitedTiers`: Spatially Enlarged Fluid x64,
  Molten Atomic Separation Catalyst x16, Molten Naquadah x4 at 20 L; Molten Uranium-235 x3, Molten Caesium x2 at 180 L)
  are TST's own constants, equal to GoodGenerator's, in litres a parallel a second. With several in the hatches a run
  takes the first of each list in that order; the card takes one of each.
- **A run** (`checkProcessing`) runs `min(Parallel_MegaNqReactor 1,000, stored fuel / (1 L x coefficient))`
  parallels. At its start it pays the fuel's 1 L x coefficient a parallel, and for each parallel and each second it
  starts (`recipeSecondsFromDurationTicks`, the duration rounded up: Mk-II's 70 ticks pay 4 seconds) 2,400 L of liquid
  air (always required), the coolant's and the excited fluid's litres; it returns the depleted fuel litre for litre.
  EU/t is `(long) (basic x coolant / 100d x coefficient x parallels x getRuntimeBonus())` in the game's double order:
  half an hour of uranium at 1,000 parallels is 13,505,624, not 13,505,625. The next run starts on the tick the last
  ends, so the rates are per-run amounts over the duration.
- **Running time.** `getRuntimeBonus` is (1 + min(0.5, `runTimeTicks` / 24 h))², 2.25x from 12 hours on (the tooltip's
  24 hours is where `runTimeTicks` stops counting). Each run adds its duration, each idle tick takes 20 off, so a
  reactor fed without a gap keeps it. The setting is the GUI's Running Time, 0 to 24 hours, 24 by default.
- **Parallels** is a setting because the game reads it from the hatch at each run's start: 1,000 by default, the most
  when 1,000 x coefficient L of fuel are stored.
- Without a dynamo hatch a run's EU goes to the owner's wireless network at its start (`currentEUt` x duration), the
  same average; with dynamos, too little room stops the reactor (`INSUFFICIENT_DYNAMO`). The structure has no tiers
  (any glass).
### Steam generators, solar boiler, lightning spire
`power/sources/TstSteamGenerators`, pinned by `TstSteamGeneratorsTest`, registered right after the Universal
Generator: the steam generator and the boiler come first in their columns, the spire after the Universal Generator.
Like it, none has maintenance or pollution, and a finished run starts the next on the same tick.
**Basic Steam Generator (19073, `tst-basic-steam-generator`).** In the Turbines column (unlock LV: an LV hull, LV
steam turbines and an LV input hatch, `GTCMMachineRecipes`). Settings: Dynamo (LV, 1 to 8 hatches; MV, one 1 A
hatch; HV, one 1 A hatch; default HV) and Dynamo amps (LV only).
- **Dynamos.** `hasValidDynamos`: every hatch LV (`setDynamoTier(1, true)`, 1 to 8 by `checkHatchMin`/`Max`), or
  exactly one MV or HV hatch with `maxAmperesOut` 1. GregTech's Dynamo element takes hatches of at most 4 A (the 1 A
  hatch, GT++'s 4 A LV buffer dynamo), so LV runs 1 to 32 A (32 to 1,024 EU/t); MV is 128 EU/t, HV 512.
- **Output.** `checkProcessing`: EU/t is every hatch's voltage x amps summed (`getDynamoOutputEUt`) at efficiency
  10,000; each 20-tick run drains `eut * 225 / 8` L of plain Steam (`MiscHelper.steam`, in longs; every EU/t the
  structure allows is a multiple of 32, so the division is exact): 900 L/s an LV amp, 3,600 at MV, 14,400 at HV,
  28,800 at 32 A of LV. That is 0.711 EU a litre, the tooltip's 142% of steam's 0.5 (`MTELargeTurbineSteam`). The
  whole second's steam must be in the hatches when a run starts (the Burn reading). It runs while the dynamos are not
  full (`getAllMaxDynamoBuffer() != getAllDynamoBuffer()`), so the card is the machine at full load.
**Large Solar Boiler (19076, `tst-large-solar-boiler`).** In the Boilers and exchangers column (unlock ULV: a Simple
Solar Boiler, reinforced glass from the alloy smelter at 4 EU/t, bronze parts). Settings: Boiler (bronze 4,800 L/s,
steel 14,400 L/s: `machineTier`, every casing of one set; default bronze, the game's), Water supply (default Water),
Heat (%, default 100) and Calcification (%, plain water only, default 0).
- **Steam.** `checkProcessing`: each 20-tick run boils
  `(int) (min(hatch, production / 160) * heat / (calcification * 2 + 1))` L of plain or distilled water into 160 L of
  Steam a litre: 30 or 90 L a run at full heat. It does not look at the sky itself: by day or night only the heat
  scales it.
- **Heat.** `onPostTick` adds 0.001 every sunny second (clear weather or a dry biome, open sky over the controller,
  daytime) up to 1, and takes 0.0001 every other second. The card reads Heat as the value a cold boiler reaches after
  that many sunny 0.1% steps, summed in doubles as the game does (500 steps are 0.5000000000000003). A clear night
  costs 0.01% a second of night, won back ten times as fast after dawn; the default 100% is the boiler in the sun.
- **Calcification.** On plain water only, one 0.01 step every 1,296 runs (seconds of run time) once 24 hours of runs
  have passed: the first step ends run 86,832, the hundredth run 215,136, and full calcification divides the water by
  3. The card counts whole steps summed as the game sums them (50 steps are 0.5000000000000002: a steel boiler boils
  44 L, not 45). The warning gives the hours: full output for 24.12 hours of run time, the least from 59.76. The GUI
  button clears it (Calcification 0).
- **Explosion.** A check that finds no water (or under a litre's boil) at 50% heat or more arms `shouldExplode`; the
  next water blows up the input hatches. The card says so in a reading (Dry hatch).
**Lightning Spire (19053, `tst-lightning-spire`).** In the Engines column after the Universal Generator (unlock IV:
its assembler recipe). Settings: Lightning rods (1 to 512, default 512), Dynamo tier (ULV to UXV, default UV) and
Dynamo amps (every hatch's summed, default 256: a UV laser source).
- **Strikes.** `checkInputRods` keeps up to 512 HV Lightning Rods (`MAXRODS`; given back, not used up). Each strike
  (`checkProcessing`, `mMaxProgresstime` 256) needs the hatches to hold exactly 128 L of Cryotheum and 72 L of Molten
  Iron a rod (more or less stops the machine), drains them and adds rods x 28,000,000 EU to a store of
  rods x 280,000,000 (`MoreSafetyPowerGeneration_LightningSpire` is false in the pack: at the strike). So 109,375 EU/t
  a rod (56,000,000 with 512), 10 L/s of Cryotheum and 5.625 L/s of Molten Iron a rod.
- **Dynamos.** `onRunningTick` tops every dynamo up from the store each tick; `TST_GeneratorBase.addDynamoToMachineList`
  takes any `MTEHatchDynamo` (multi-amp and laser source hatches go with the exotic ones). EU/t is the lesser of the
  strikes' and the dynamos' voltage x amps. Under the strikes' rate the store fills and the rest of each strike is
  lost while the fluids are still burned: the card warns. Over it, the dynamos empty the store in bursts at their
  full rate; the card gives the average.
What the power model cannot express: the spire's exact refills every 12.8 s and its bursts; the boiler's heat over a
day and its explosion (a setting and a reading); GregTech's dynamo output loss (each amp drains voltage +
2^(tier - 1) from the hatch, `BaseMetaTileEntity.handleEUOutput`), left out as on every multiblock card.
### Dyson Sphere Program (19014 `tst-dsp-receiver`, 19015 `tst-artificial-star`)
`power/sources/TstDysonSphere`, in the picker's Endgame column (unlock UIV and UXV: the controllers are assembly line
recipes at those tiers, `DSPRecipePool`). Both put their EU straight into the owner's wireless network
(`addEUToGlobalEnergyMap`). The planner has no wireless ledger: it is the card's EU as any generator's, and its Output
reading says "Wireless EU network". The pack's DSP config values are TST 0.8.1's defaults. Pinned by
`TstDysonSphereTest`.
**Dyson Sphere Ray Receiving Station.** Settings: Mode, Planet, Solar sails, Dyson sphere nodes, Held by other
receivers, Controller slot (with Circuit number and Circuit stack size, or Astral Array Fabricators), Gravitational Lens
intensify.
- **Output.** `checkMachine` always sets `wirelessMode` (the structure has no dynamo), so `onRunningTick` banks
  `generateTickEU()` every tick and `checkProcessing` hands the bank over at the end of each 128-tick run: EU/t is
  `(long) (planet x stellar coefficient x lens x used power points)`. The coefficients are `DSP_Planet`'s and
  `DSP_Galaxy`'s: the Planet setting lists TST's 39 planets (their dimension ids match the pack's Galacticraft,
  GalaxySpace, AmunRa, bartworks and Twilight Forest configs), a dimension from 180 up but 227 (the tooltip's personal
  dimension) is the Overworld, and any other dimension has 0 and receives nothing. The lens is 4 while intensify mode
  lasts: each Gravitational Lens gives 600 s, so the card takes one every 600 s.
- **Power points.** A team has one sphere per star system (`DSP_DataCell`): `(long) (524,288 x sails x (nodes +
  1)^0.8)`, and once that would pass a long (`shouldUseBigInteger`) `sails x 524,288 x ceil((nodes + 1)^0.8)` in
  BigInteger, read back at most Long.MAX_VALUE. A receiver takes (`getPowerPoint`, from what the sphere has left) at most
  1,024 x Integer.MAX_VALUE (2,199,023,254,528); a Programmed Circuit in the controller asks for meta / stack size of the
  whole sphere (at most all of it); Astral Array Fabricators (a stack, 1 to 64) raise the cap to their number squared x
  2 times it.
- **Other receivers.** Receivers on one sphere share it: the points the others hold are a setting, in percent of the
  sphere. A card counts each of its machines at its own rate, so a card of several receivers on one sphere needs the
  share set to what the rest hold (or one card per receiver).
- **Photon collection** (mode 1) makes no EU: each run turns the bank into Critical Photons, `storageEU /
  EUPerCriticalPhoton` (the rest stays banked) plus `storageEUMAX x (Integer.MAX_VALUE / EUPerCriticalPhoton)`: 20 x
  EU/t / 2,147,483,647 a second.
- **Sails.** Past 2,048 + 256 a node they may decay (`DSP_SolarSailDecreaser`: every 30 minutes a 1 in 5,000 roll, then
  1 in 4 a sphere, takes half the excess). The card counts the sails set and warns.
- Not modelled: the first run (the bank is handed over as each run ends); a sphere that once went BigInteger stays so
  (`useBigInteger` is saved), which only shows far above any receiver's cap.
**Artificial Star.** Settings: Antimatter, Antimatter Fuel Rods and Strange Annihilation Fuel Rods each run; Dimension,
Time and Stabilisation field (tier 1 the base block: Dimensional Bridge, Ultimate Containment Field Generator,
Spacetime Altering Casing; tiers 2 to 10 the TecTech Spacetime Compression, Time Dilation and Stabilisation Field
Generators, Crude to Gallifreyan); Continuous-run bonus.
- **Output.** It has no recipe map of its own to run: `checkProcessing` takes everything in its one input bus each run
  and adds `(long) (units x output multiplier x (100 + bonus) / 100) x Integer.MAX_VALUE` EU at once, a fuel's units
  being `Config.EUEvery... / Integer.MAX_VALUE` (Antimatter 3, Antimatter Fuel Rod 1,024, Strange Annihilation Fuel Rod
  32,768), the run's fuels summed before the cut. The multiplier is `(tTime x tDim)^0.25 x 1.588186^(tStab - 2)`: 0.63
  with the base blocks, 128 with Gallifreyan generators. A run is `(int) (20 x 6.4)` = 128 ticks and the next starts on
  the tick it ends, so the card is EU per run / 128 EU/t on fuel per run every 6.4 s.
- **Bonus.** `rewardContinuous` climbs 1% a run with fuel up to 50 and drops to 0 on a run without; the card defaults
  to the steady 150% (from the 51st run, 5 min 20 s in).
- **Recovery.** A run with fuel rods gives one Stellar Construction Frame Material per rod on a tDim x tTime x tStab in
  1,000 roll (certain at all 10); the card makes the expected rate.
- **Big numbers.** One run can pass Long.MAX_VALUE EU (1,000 strange rods with Gallifreyan generators: 1.35 x 10^19);
  the game adds it as a BigInteger and the card works it out as one, then hands EU/t to the ledger as a double, the
  planner's EU everywhere (no overflow; about 16 significant digits).
TST's items are not in the website's resource map: `PowerPorts.resolve` falls back to `TstDysonSphere.ITEMS` (TST's
MetaItem01 metas), so the fuels, the Gravitational Lens, Critical Photons and the frame material are ports in a game
with TST, readings without it.

## Other mods in the pack

None adds a recipe-processing machine:

- programmablehatches 0.2.1p7: hatches, plus three multiblocks (Ingredient Distributor, Large Programming Circuit
  Provider, Multiblock Proxy) with their own non-recipe processing.
- ae2thing 1.3.0: AE2 tiles (Essentia Discretizer, Infusion Interface, Wireless Distributor) and NEI helpers.
- msbf 1.0: mouse side-button fix. EZMiner 6.3: vein miner.

## Text

Machine, map, setting, option and gate labels are English data, drawn through `UiText.data`; their Simplified Chinese
lines (pack names) are in `lang/parts/tst.zh_CN.lang`.
