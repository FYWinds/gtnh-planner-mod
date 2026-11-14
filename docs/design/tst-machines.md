# TwistSpaceTechnology machines

TwistSpaceTechnology (TST) 0.8.1's recipe-processing multiblocks on cards, for the ETO GTNH 2.9.0 pack. The website's
data knows none of them: without this a TST machine picked on a card ran as the map's first machine. Every number
below is from the TST source (`common/machine/...`) with the pack's `config/TwistSpaceTechnology.cfg`, whose relevant
values all equal TST's defaults. From 0.8.0, 0.8.1 renamed every TST map to `tst.recipe.*`, changed the Intensify
Chemical Distorter (coil-tier speed on the chemical reactor, a new chemical plant mode; its cfg key
`SpeedUpMultiplier_LCRMode` is now ignored), and added the Mega Digester and Bottled Chaos; other machines changed only
text.

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
   - TST's own maps (`tst.recipe.*`; 0.8.0 called them `tst.common.recipe.*` and `tst.dyson.recipe.*`) are new
     entries with English names and their machines.
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
| 19001 | Intensify Chemical Distorter | 深度化学扭曲仪 | Intense Chemical Distorter; largechemicalreactor; fluidchemicaleactor (Chemical Plant) | done | coil (gates recipe heat; the chemical plant needs Infinity) |
| 19002 | Precise High-Energy Photonic Quantum Master | 精密高能光量子掌控者 | laserengraver; Photon Manipulator | done | intensifier tier |
| 19003 | Miracle Top | 奇迹顶点 | Miracle Top; Quantum Inversion | done | rings |
| 19004 | Magnetic Drive Pressure Former | 磁驱压力成型机 | extruder, metalbender, press, hammer | done | coil |
| 19005 | Physical Form Switcher | 物质形态转换器 | fluidsolidifier, fluidextractor | done | glass (gate) |
| 19006 | "Mini" Magnetic Mixer | "小型"磁力搅拌机 | multimixer | done | — |
| 19007 | Magnetic Domain Constructor | 磁畴构建器 | electromagneticseparator, polarizer | done | rings |
| 19008 | Silksong | 丝之歌 | wiremill | done | coil, sections; Power Source (wireless from 94 sections: 100 batches in 188 t on network EU) |
| 19009 | Holy Separator | 神圣分离者 | lathe, cuttingsaw | done | sections; NEI's `fakecuttingsaw` page is read as `cuttingsaw` (all cutters) |
| 19010 | Space Scaler | 空间缩放仪 | compressor, extractor, cyclotron, EIC, neutroniumcompressor | done | field generator; compressor CompressionTierKey gate not modelled (no recipe metadata) |
| 19011 | Molecule Deconstructor | 分子解构器 | multielectro, multicentrifuge | done | sections |
| 19012 | Crystalline Infinitier | 无限晶胞 | autoclave, chemicalbath; Crystalline Infinitier | done | glass, field (gate) |
| 19013 | DSP Launch Site | 戴森球模块发射场 | Dyson Sphere Module Launch Site | done | motor tier, overload seconds left (Space Warpers as a port), energy hatches or wireless |
| 19014, 19015 | Dyson Sphere Ray Receiving Station, Artificial Star | 戴森球射线接收站, 人造恒星 | — | done as power sources | see Generators |
| 19016 | Miracle Door | 奇迹之门 | Stellar Forge; Stellar Forge: Alloy Smelter | done (always wireless) | circuit number x stack size; crafts a run (0 = no limit), which counts the run's Critical Photons as an input (read out on the card at 0); White Dwarf Mold casts molten outputs into ingots |
| 19017 | General Ore Processing Factory TST | 通用矿物处理厂TST | Ore Processing Recipes | done | energy input (one hatch, several or exotic, wireless), ores a run on wireless; 1600 L Lubricant a run |
| 19018, 19500 | ElvenWorkshop | 精灵工坊 | Rune Engraver; Mana Infuser | done | — |
| 19019 | HyperSpacetimeTransformer | 极限时空转换仪 | Hyper Spacetime Transformer | done | fusion coil, cooling, matrix (gate) |
| 19020 | General Circuit Converter | 通用电路板转换器 | — | not processing | oredict conversion |
| 19021 | Large Industrial Coking Factory | 大型工业炼焦厂 | Coking Factory | done | coil |
| 19022 | Mega Bricked Blast Furnace | 巨型砖高炉 | — | not processing | primitive, fuel-efficiency over run time |
| 19023 | Scavenger | 拾荒者 | sifter | done | — |
| 19024 | CleanRoom | TST超净间 | — | not processing | cleanroom |
| 19025 | Biosphere III | 生物圈III号 | brewer, fermenter, BacteriaVat | done | glass; vat: mode, petri dishes and the bartworks vat's own settings (hatch, fill, void protection, radio hatch) |
| 19026 | Tower of Abstraction (Mega Egg Generator) | 抽象之塔 | — | done as a power source | pieces, infinity, dragon and creeper eggs; see Generators |
| 19027 | Advanced Mega Oil Cracker | 进阶巨型石油裂化机 | craker | done | coil |
| 19028 | Indistinct Tentacle | 不可视之触 | Mega Assembly Line, componentassemblyline, assembler, precise_assembler | done | component casing (gates); Power Source (wireless with UMV glass and casing: 100 batches in 512 t, or 20 t at 64x EU with an Astral Array Fabricator) |
| 19029 | Astral Computing Array | 星规阵列 | — | not processing | computation |
| 19030 | Thermal Energy Devourer | 热能饕餮 | vacuumfreezer | done | mode; Power Source (an Energised Tesseract: 100 batches in 128 t) |
| 19031 | Vacuum Filter Extractor | 真空抽滤器 | distillationtower, distillery | done | — |
| 19032 | Large Steam Forge Hammer | 大型蒸汽锻造锤 | hammer | done (steam) | steam casing: 16 parallels a tier on 8 EU/t each, steam 1.33F a recipe EU (the card's steam bill, not 1.25 x pressure) |
| 19033 | Large Steam Alloy Smelter | 大型蒸汽合金炉 | alloysmelter | done (steam) | as above, 48 parallels on 24,576 EU/t |
| 19034, 19035 | Eye of Wood, Bee Engineer | 武德之眼, 蜜蜂操纵者 | — | not processing | ore generator, bee breeding |
| 19036 | "Mini" Household Cell Fragmentizer | "小型"家用破壁机 | macerator | done | glass (speed and gate), structure block |
| 19037 | Hephaestus' Atelier | 赫菲斯托斯的工坊 | alloysmelter, smelting, furnace | done | ring coil (T1 hatches; T2/T3 wireless, 256/20-tick runs) |
| 19038 | Deployed Nano Core | 展开的纳米核心 | Nano Core | done | Power Source (no hatches: 100 batches in 128 t) |
| 19039 | Core Device of Human Power Generation Facility | 人类能源设施的核心装置 | fluidheater | done | coil |
| 19040, 19041 | Starcore Miner, TST Large Disassembler | 星核钻机, TST大型拆解机 | — | not processing | miner, disassembly |
| 19042–19045 | Space Apiary Module MK-I–IV | 太空蜂箱模块 | — | not processing | bee simulation |
| 19046 | BallLightning | 球状闪电 | arcfurnace; fusionreactor; Star Kernel Generator | done with energy hatches | machine tier, fusion coil, coil (Infinity+), field, glass; wireless (tier 4 without hatches) not modelled |
| 19047 | Industrial Magic Matrix | 工业注魔矩阵 | Industrial Magic Matrix | done | essentia cell, controller slot, energized nodes and their primal amounts; essentia and research not modelled |
| 19048 | Large Canner | 大型装罐机 | canner | done | — |
| 19049 | MegaArray (Big Bro Array) | 大哥阵列 | its 28 machine types' maps (cell mixer, centrifuge, electrolyzer; massfab; smelting) | processor mode done | machine tier, count, parallelism casing, coil, frame (gate); generator mode not done |
| 19050 | Industrial Magnetar Separator | 工业电磁离析机 | electromagneticseparator | done | — |
| 19051 | Eco-Sphere Simulator | 拟似生态圈 | Eco-Sphere: Arboreal Genesis, Aquatic Simulation, Artificial Greenhouse, Directed Mob Cloning | done | see Machines with their own recipe pages |
| 19052 | Extreme Crafting Center | 梦魇工业合成中心 | Extreme Craft Recipe Transform | done | pattern magnification, pattern crafts a cycle; no EU; containers handed back as outputs |
| 19053 | Lightning Spire | 闪电尖塔 | — | done as a power source | rods, dynamo tier and amps; see Generators |
| 19054 | DTPF Prototype MK-II | 超维度物质等离子锻炉原型机MK-II | plasmaforge | done | coil, controllers, core counts, hours running (catalyst discount) |
| 19055 | Large Neutron Oscillator | 大型中子振荡器 | NeutronActivatorWithEU | done | controllers, core counts |
| 19056 | Indistinct Tentacle Prototype MK-II | 不可视之触原型机MK-II | as 19028 | done (always wireless) | — (an unlimited batch every 21 t on network EU) |
| 19057 | Mass Fabricator : Genesis | 质量发生器 : 创世纪 | Mass Fabricator: Genesis | done | parallels (controllers + 1) |
| 19058 | PULSAR - Incompact Cyclotron | PULSAR - 非紧凑式回旋加速器 | cyclotron | done | — |
| 19059 | Strange Matter Aggregator | 奇异物质聚合器 | Strange Matter Aggregation | done | oscillator, constraintor, merger tiers and counts, constrainers a run, consecutive runs, Core Element, speed and power controllers, energy hatches or wireless |
| 19060 | Micro SpaceTime Fabricatorio | 微型时空发生器 | Micro SpaceTime Fabricatorio | done | field, SpaceTime Seed (outputs ×2) |
| 19061 | Bloody Hell | 血狱 | Bloody Hell Altar | done | structure tier, speed runes; orb tier gate not modelled |
| 19062 | Silicon Rock Synthesizer | 硅岩制造机 | Mega Stone Breaker | done | water and lava (outputs ×4 / ×1024; the 1000 L/s upkeep is not an input) |
| 19063 | Manufacturing Center | 加工中心 | compressor, lathe, polarizer, fermenter, fluidextractor, extractor, laserengraver, autoclave, fluidsolidifier | done | core (gate); CompressionTierKey gate not modelled |
| 19064 | Industrial Alchemy Tower | 工业炼金塔 | Industrial Alchemy Tower | done | essentia cell, controller slot, energized nodes and their primal amounts |
| 19065 | Giant Vacuum Drying Furnace | 巨型真空干燥炉 | vacfurnace, multidehydrator | done | coil, sections |
| 19066 | TST Processing Array | TST处理阵列 | every basic single block's map (multimixer, multicentrifuge, multielectro for the cell three) | done | machine tier, count; CompressionTierKey gate not modelled |
| 19067 | Advanced Circuit Assembly Line | 进阶电路装配线 | bw.recipe.cal (runs a copy without imprints) | done | imprint whitelist and single-hatch voltage cap not modelled |
| 19068, 19298 | Swelegfyr Blast Furnace | 熯焱高炉 | blastfurnace | done | coil (any; a recipe above coil heat + 100 K a hatch tier over MV refused), mode, heat (rises and decays: a setting), glass (below UMV caps the hatch tier); Blazing Pyrotheum an input |
| 19069 | Hyper Thermal Convector | 高能态热对流器 | Rapid Heat Exchange, Rapid Cooling Down | done | — (20 t a run, unlimited parallels, no EU) |
| 19070 | Primordial Disjunctus | — | — | not processing | own logic |
| 19071 | Skypiercer Tower | 穿云尖塔 | Skypiercer Tower | done | mode, rings, crystals a stack, the tree's seconds in Passive Mode |
| 19072 | Laser Meteor Miner | — | — | not processing | own logic |
| 19073, 19076 | Basic Steam Generator, Large Solar Boiler | 基础蒸汽发电机, 大型太阳能锅炉 | — | done as power sources | dynamo; boiler, water, heat, calcification; see Generators |
| 19074 | Universal Generator | 通用发电机 | — (gasTurbineFuels, dieselFuels) | done as a power source | see Generators |
| 19075 | Infusion Material Dispenser | — | — | not processing | own logic |
| 19077, 19078 | Nether Interface, Super Water Purifier | 地狱接口, 超净水生成器 | Nether Interface; Super Water Purifier | done | no settings: parallels from the hatches' EU |
| 19079 | Integrated Assembly Matrix | 集成装配矩阵 | Mega Assembly Line | done | nanites; data-stick whitelist and highest-hatch voltage cap not modelled |
| 19080 | Mega Solar Panel Factory | 巨型太阳能板工厂 | Mega Solar Panel Factory | done | component casing |
| 19081 | Mega Naquadah Reactor | 巨型硅岩反应堆 | — (gg.recipe.naquadah_reactor) | done as a power source | new in 0.8.1; fuel, coolant, excited fluid, parallels, running time; see Generators |
| 19082 | Bottled Chaos | 瓶中混沌 | Bottled Chaos; Bottled Chaos - Credential Injection; Bottled Chaos - Machine Structure Packaging | done | speed, power and overclock controllers, core tier, energy hatches or wireless, batches a run (wireless), credential capacity share, source upkeep fluid and L/s, Critical Photons a run, route time a batch |
| 19083 | Mega Digester | 巨型煮解池 | digester | done | coil (gates recipe heat); new in 0.8.1 |

Not registered in 0.8.0: `TST_TombOfTheDragon`, `TST_PatternMachineClass`, `GTCM_TestMultiMachine`.

## Wireless, steam and heat machines

`machines/eto/TstMachinesWireless` (settings in `TstWirelessControls`, handler templates in
`assets/gtnhplanner/machines/eto-tst-wireless.json`, a second `HandlerData.EXTRA_FILES` entry), worked from TST tag
0.8.1 with the pack's config (every value these read is TST's default) and GregTech 5.09.54.265; pinned by
`TstWirelessMachinesTest`. Source lines below are under `common/machine/` unless named otherwise.

### Wireless runs

`WirelessEnergyMultiMachineBase` (`:141-207`): in wireless mode a run checks up to 100 batches
(`DefaultCycleNum_WirelessEnergyMultiMachineBase`), each with the voltage at `Long.MAX_VALUE`, 1 A and
`OverclockCalculator.ofNoOverclock`, which sets neither the speed bonus nor the EU modifier (GT `:94-99`, `:399-403`).
A batch's EU, `ceil(recipe EU/t x parallels) x recipe ticks x getExtraEUCostMultiplier()`, leaves the owner's network
when it starts; the run then lasts `getWirelessModeProcessingTime()` ticks however much it did.

On the card this is the generic hook `MachineTable.Behaviour.fixedTicks` (read by `MachineEffects.fixedTicks`): a
card whose entry gives it more than 0 runs that many ticks with no overclock (`Overclock.stats`), its EU/t a
parallel is the recipe's times the entry's `power` (or the entry's own `fixedEut` where that is above 0), the pool
caps no parallel (`MachineEffects.poweredParallelLimit`, `TstMachines.put` skips `paid`) and the hatches stop nothing
(`PowerReport.report`, which `NodeMath` runs for a fixed run even at 0 EU/t; only a recipe gate stops it). For a
wireless run `power` is recipe ticks x extra / run ticks, so the card's EU/t is the run's lump sum spread over it,
and its parallels are 100 x a batch's. A batch with no parallel limit is counted as 2^31 - 1 crafts a run in all
(`WebEffect` caps there anyway): one machine never runs short.

What a machine uses up besides its recipe is the generic hook `MachineTable.Behaviour.upkeep`: a list of
`machines/web/Upkeep` (an `item:`/`fluid:` id, an amount a second of running or a run, in or out), read by
`MachineEffects.upkeep`. `machines/game/UpkeepPorts` (after every refresh, from `GTProvider`) gives the card a port
for each, its amount a craft the run's amount (a second's x the run's seconds) over the run's crafts, so the plan wires
and totals it like the recipe's own. A card with no limit to a run's crafts cannot share a run's amount out: it gets
no port, and reads the amount out as a pinned reading ("..., not counted").

The four machines that may run either way have a Power Source setting (`TstMachinesWireless.orWireless` wraps their
hatch entries; Energy Hatches keeps the old maths):

- **Silksong** (`GT_TileEntity_Silksong :261-274, :336`): wireless from 94 sections with no hatch; a batch is its
  sections x (coil tier + 1) x 32 parallels, the run 188 ticks. Fewer sections: refused.
- **Indistinct Tentacle** (`TST_IndistinctTentacle :231-257, :329-331, :436-453`): wireless with UMV glass, a UMV or
  better component casing and no hatch; unlimited batches in 512 ticks, or 20 ticks at 64x the EU with an Astral
  Array Fabricator in the controller (its own option). A casing below UMV is refused; the casing's recipe gates stay.
- **Thermal Energy Devourer** (`TST_ThermalEnergyDevourer :181-185, :229-246, :284-287`): an Energised Tesseract in
  the controller; unlimited batches in 128 ticks, the mode beside the point.
- **Deployed Nano Core** (`TST_DeployedNanoCore :358, :375-377, :399-421`): no hatch; unlimited batches in 128
  ticks.

Always wireless:

- **Miracle Door** (`TST_MiracleDoor`): never overclocked (`:347-351`), unlimited (`:386`). The run is
  `max(1, 1280 / p)` ticks as a stellar forge and `max(1, 512 / p)` as an alloy smelter (`:457-463`, `ValueEnum
  :152-155`: 64 s and 25.6 s), each batch costing its EU x p, x 2 as a stellar forge (`:451-455`), p the overclock
  parameter: the controller slot's circuit number x stack size, 1 without one (`:482-491`). The setting is that
  product. Each run takes p Critical Photons (`checkProcessing :399-407`, `consumePhoton :465-480`) however much it
  crafts: the crafts a run are whatever its buses hold when it starts, which the card cannot know, so Crafts a Run is
  a setting (0, the default, is the game's no limit). Set, it is the card's parallels and the photons are an input
  of p / crafts each (one Door fed that much every run: p x 20 / ticks photons a second); at 0 the card reads them
  out instead. White Dwarf Mold (a setting; the mold sits in an input bus, `prepareCatalyst :353-365`) casts each
  molten output that has an ingot in `StellarForgeRecipePool.MoltenToIngot` (read from TST at run time) as the game
  does (`turnToIngotRecipe :493-516`): litres / 144 ingots as an output of their own, the rest left on the fluid's
  port; under 144 L it stays molten (`machines/game/TstWirelessPorts`).
- **Indistinct Tentacle Prototype MK-II** (`modularizedMachine/MM_IndistinctTentaclePrototypeMK2`): the controller is
  its own perfect execution core: no overclock (`:308-312`), no parallel limit
  (`MultiExecutionCoreMachineBase.setupProcessingLogicWirelessEU :727-730`), recipe EU x ticks from the network, then
  20 ticks (`done :317-336`), counted 1 to 20 and ended on the next tick (`runExecutionCoreTick :280-309`): 21 ticks a
  batch. Perfect execution core hatches add more such lanes, which one card's unlimited batch does not need.
- **Hyper Thermal Convector** (`TST_HyperThermalConvector :298-336`): no hatch, recipes at 0 EU/t; 128 (heat
  exchange) or 16 (cooling) batches of unlimited parallels, the run 20 ticks whatever the recipe.

### Steam

The Large Steam Forge Hammer and Large Steam Alloy Smelter extend GT++'s `MTESteamMultiBlockBase`, but their logic is
TST's (`multiMachineClasses/TST_SteamMultiMachineBase :86-98`): `ofNoOverclock` with a 1.33F EU discount, so a run is
the recipe's own ticks and burns `ceil(EU/t x parallels x 1.33F)` L of steam a tick (`MTESteamMultiBlockBase
:240-249` at efficiency 10000). Parallels are paid from `V[getTierRecipes()] x parallels` EU/t (`:176-183`), each at
its recipe EU/t (`GTCM_ParallelHelper :86-90, :160-161`); a recipe above that never starts.

- **Large Steam Forge Hammer** (`TST_LargeSteamForgeHammer :118, :184-188`): 16 parallels per steam casing tier
  (Bronze Plated Bricks 1, Solid Steel Machine Casing 2, a setting) on 8 EU/t each: 128 or 256 EU/t.
- **Large Steam Alloy Smelter** (`TST_LargeSteamAlloySmelter :120-123, :153-157`): 48 parallels at tier 3: 24,576
  EU/t.

The card's steam bill (`PowerReport.steam`) takes the entry's `steamPerEu` (1.33F) in place of the website's
1.25 x pressure tier; the templates' `eutMultiplier` 1 keeps the recipe's EU/t on the handler's profile, which the
parallel limit reads (a steam card's own EU/t is 0).

### Swelegfyr Blast Furnace

`TST_SwelegfyrBlastFurnace` (and the legacy controller 19298, a subclass on the old structure): 1 / 3.9F
(`:485-489`), 4096 parallels, 256 in passive mode (`:479-483`), normal overclocks (`:491-494`) of which one perfect
per 1800 K of heat over the recipe's (`:597-602`), and EU x (float) 0.9^((heat - recipe heat) / 900) in whole steps
(`:588-590`), the heat being the furnace's `mHeatingCapacity`, which changes while it runs: raised to the coil's on
forming (`:330`), falling a tenth of the way to the coil's every 10 s in normal mode (`:819-831`), climbing 5 K a
second toward `floor(coil heat^1.08 / 100) x 100 + 1` in passive mode (`:331`, `:749-766`), partly reset on a new
recipe (`:517-546`). Settings: the coil (every coil; a recipe hotter than the coil's heat + 100 K per tier of the
dominant energy hatch over MV never starts, `:354`, `:568`, read off the card's hatch tier, `Context.runTier`), Mode,
Heat in K, held between the coil's and the maximum (0 is the mode's steady heat: the coil's in normal mode, the
maximum in passive mode), and Glass Tier (UMV by default): below UMV an energy hatch above the glass fails the
structure (`:333-346`), which the card refuses. Blazing Pyrotheum is an input (`onRunningTick :749-766`, every 20
running ticks): 1,000 L/s in normal mode, heat / 5 L/s (whole litres) in passive mode.

Rapid heating is no setting: it is passive mode's warm-up after a recipe change (`RapidHeating :634-667`, 20-tick
fake runs at V x A x 15/16 EU/t that burn extra pyrotheum and jump the heat toward the maximum, `:740-747`). It ends
once the heat reaches the maximum, which is passive mode's steady heat anyway, so a furnace running one recipe on
gets the same duration, EU and pyrotheum with it or without it; the warm-up itself is no steady rate (Heat in K
covers a furnace not yet at the maximum).
## Cores, arrays and Thaumcraft machines

Some machines are worked out whole, the game's way, and the card shows the run as it is
(`TstCoresCards.exact`): no overclock of the card's own, a speed and an EU factor that turn the recipe's ticks and
EU/t into the run's, and the run's own parallels. `TstCoresGt` is GT's ProcessingLogic (OverclockCalculator, sub-tick
parallels) with TST's `GTCM_ParallelHelper` (0.8.1 :60-190), which bills the machine's EU modifier and has no
one-tier-above check.

**Execution cores** (DTPF Prototype MK-II, Large Neutron Oscillator; `MultiExecutionCoreMachineBase`). The card
sets the overclock controller (none 2/4, Low Speed Perfect 2/2, Perfect 4/4, Singularity 8/4), Parallels (the
controller's plus 1), the speed controller's multiplier (speed bonus 1/m), the power controller (T1-T8: 0.95, 0.9,
0.85, 0.8, 0.75, 0.7, 0.5, 0.25) and the counts of Execution, Advanced and Perfect Execution Cores. Any Perfect core:
every input runs at once, 20 ticks, no overclock, EU/t x duration a parallel from the wireless network. Otherwise
`TstCoresExecution` runs the controller and its cores tick by tick until the state repeats and takes that stretch's
average: the budget is `(long) (0.975 x getMaxInputEu)`; idle cores are checked when one finishes and every 64 ticks,
Advanced cores first at the whole spare budget, then plain cores and the controller at the spare budget over the idle
ones left, each capped at `1 + parallels / (cores + 1)`; spare EU boosts working cores (a perfect type a tick per core
EU/t, else `2^n - 1` ticks for `(4^n - 1)` x EU/t); a plain core ends when progress reaches duration - 1, the
controller a tick after its duration. With Advanced cores the card counts the run as wireless (fixed ticks) and its
EU/t is the hatches' and the network's together. Assumed: inputs never run short. The DTPF's excited fuels
(DTEC/DTRC/DTPC/DTCC/DTSC, the first one the recipe takes) become `round(amount x max(0.25, 1 - hours / 24 x 0.75))`
(:362-383, `MaxFuelDiscount_DTMPFP` 0.25); Hours Running defaults to 24 (the decay after a stop is not modelled).

**Industrial Magic Matrix** (0.8.1 :389, :431-450, :558-644). Parallels = cell tier << 3, or Integer.MAX_VALUE with
a Proof of Heroes in the controller slot (then the run lasts 1 tick). Speed from the energized nodes: none 1, 1-5
nodes `(6 - n) x 1.75`, six nodes from each primal's amount (Aer, Ignis, Terra, Aqua, Ordo, Perditio): a missing
primal gives 1 and 20 extra ticks each, else `1 / (1 + (0.4 + (0.45e)^(-0.005 Var) + 0.15 log2(1 + e^-Var)) x Mean /
500)`, no lower than 1/11.4514, where the overclocks turn perfect. Each run gives back one Primordial Pearl per pearl
the infusion takes (not for Avaritia's big pearl): the card's pearl input is `(P - 1) / P` of its parallels' (the
pearl input's amount taken as the components'). Essentia supply and research are not modelled.

**Industrial Alchemy Tower** (:297-315, :415-467). Parallels = cell tier^5 (Proof of Heroes: unlimited). Speed:
none 1, 1-3 nodes `1 - 0.1 n`, four from the purest node's Ignis, Aer, Ordo, Perditio and 10:
`base = 1/11.4514 + (0.7 - 1/11.4514)(1 - e^(min - 10))`, plus `(0.7 - base)(max - min) / max`; perfect at 1/11.4514.

**Hephaestus' Atelier** (:182-187, :242-292, :325-513; Config :324-329). Its ring decides: Molecular Coil runs on
hatches (alloy smelting perfect and unlimited; smelting a fixed 512 ticks at 7 EU/t an item, `(15/16 x
getMaxInputEu) / 7` items); Ultimate Containment Field Generator and Teleportation Casing run wireless, every input in
256 or 20 ticks, smelting 2048 EU an item, alloy smelting the recipe's EU/t x duration a parallel. The furnace maps'
templates carry the 512 ticks and 7 EU/t (`Behaviour.handlerNumbers`).

**Ball Lightning fusion** (mode 2, :445-531, :613-627). Machine tier 3+; 65536 parallels; perfect overclocks; speed
0.25 at tier 4; when `FusionMaxEut = RECIPE_MAX x 4^(compact coil - 2) x 1.8^(field - 1)` is below the hatches, the
run has that voltage at 1 A without amperage overclocks. Its fusion tier gate needs a startup the planner reads as 0
from NEI, so it never refuses.

**Intensify Chemical Distorter** (0.8.1 :234-351): its map 16 parallels at `1 / (1 + (coil heat - recipe heat) /
900)`; the chemical reactor and the chemical plant 1024 parallels at `1 / (coil tier + 1)`; the chemical plant needs
Infinity coils. **Mega Digester** (:182-230): `1 / (coil tier + 1)`, 2147483640 parallels, perfect.

**TST Processing Array and MegaArray** (`TST_ProcessingArray`, `TST_BigBroArray` processor mode). Neither is an NEI
catalyst, so the card's machine menu adds them after NEI's own on the maps `eto-tst-cores-arrays.json` lists
(`machines/game/TstArrays`, from `CardModel.catalysts`); the machine put in them is the card's map, its tier and count
settings (`TstCoresArrays`). Processing Array: any `MTEBasicMachineWithRecipe` (PAHelper :57-68; mixer, centrifuge and
electrolyzer on their multiblock maps, :33-35), 1-64 machines; voltage V[tier] x the machine's amps (thermal centrifuge
2, arc furnace 3, PAHelper :77-80), a recipe over it refused (:190); ProcessingLogic at amperage n without amperage
overclocks, n parallels (:193-201), 2/4 overclocks. MegaArray: forms only with all four add-ons (:468-486); parallels
`(int) min(n x 2^casing, max)` (:691-693, max 1,280 / 5,120 / 20,480 / 5,242,880 / ((2^31 - 1) << 11) / 5 x 5,
:1104-1112), speed `(float) 0.66^casing`, EU `(float) 0.9^coil` (:498-499), on the hatches with 2/4 overclocks; refused
over `8 x 4^machine tier` (:686-689) or a machine tier above frame + 4 under Quantum (:1127, :1151). Both smelt at GT's
furnace recipe, 128 ticks at 4 EU/t. Not modelled: the PA's CompressionTierKey refusal, the deprecated Plasma Arc
Furnace, batch mode, the MegaArray's glass and generator mode.

**Biosphere III as a bacterial vat** (`TST_BiosphereIII` 0.8.1). On `bw.recipe.BacteriaVat` the card keeps the
bartworks vat's settings and model (`BacterialVat`, `MachineModels`) and adds Mode and Petri Dishes (1-64, default 1;
`TstCoresVat`). Parallels: 4 a dish in mode 0, 1 a dish in mode 1 (:266-273, the cap at :348); void protection (on by
default) cuts them to `(capacity - stored) / (M x first fluid out)` (`GTCM_ParallelHelper` :133-155, GT
`VoidProtectionHelper` :207-215), none fitting holds the machine. Speed bonus 0.5 / 1 (:275-282), normal overclocks.
Only the first fluid in and out are multiplied (:375-376): mode 0 by bartworks' fill formula (:380-393), mode 1 by
`1601 + 600 x (glass - recipe glass)` (:363-364). The vat card's Glass (`bioVatGlass`, HV-UXV here) serves the gate
and mode 1. Not modelled: inputs short of `P x M x first fluid in` (the game lowers M and P, :366-373), the petri dish
matching the culture.

GregTech's cutters list their NEI page under `gt.recipe.fakecuttingsaw`, which merges a cut's fluids; the planner
reads it as `gt.recipe.cuttingsaw` (`HandlerData.map`). The page's duration is the water variant's.

## Machines with their own recipe pages

These controllers look up no recipe: their NEI pages (TST's visual and fake maps) show what a run takes and makes,
and the controller works the run out itself. `assets/gtnhplanner/machines/eto-tst-visual.json` adds the 14 maps
(0.8.1 ids, `handlersOnRecipes`, the controller as the only machine; the Eco-Sphere maps also list their execution
protocols) and `machines/eto/TstMachinesVisual` (with `TstVisualEcoSphere`) the entries. Every entry uses
`fixedTicks` (the controller's own run length; no GT overclock on the card), `inputMultiplier` / `outputMultiplier`
where a page's amount is not a parallel's, and `upkeep` for what a run uses that the page has no slot for. Line
numbers are TST 0.8.1's; the pack's `TwistSpaceTechnology.cfg` keeps TST's defaults for all of them. Pinned by
`TstVisualMachinesTest` and `TstVisualEcoSphereTest`.

- **Nether Interface** (`TST_NetherInterface.checkProcessing` :204-263, `initStatics` :265-286): p = min(64,
  ⌊EU / 7680⌋ − 2), refused under 3 × 7680 EU/t; 1200 t at 7680 × (2 + p) EU/t. A parallel: 16000 L distilled water
  in, 16000 L Poor Nether Waste out, three weighted picks of the five stacks (weights 1/49/30/10/10, the page's
  chances, so each stack × 3), 288 L molten Hellish Metal on 30 % of runs (expected value).
- **Super Water Purifier** (`TST_SuperWaterPurifier.process` :196-287, `initStatics` :291-306): p = min(2,000,000,
  ⌊EU / RECIPE_UMV⌋), refused under one; 1200 t at RECIPE_UMV × p. A parallel takes 1000 L UU-Matter (1 L
  Concentrated) and makes 3 (6) picks of 1000 L: each grade 12 %, Stable Baryonic Matter 4 %.
- **General Ore Processing Factory TST** (`TST_OreProcessingFactory` :183-431, `OP_Logic` :267-363, :488-508): no
  overclock, 128 t, 30 EU/t an ore. Ores a run: one normal hatch ⌊V × 15/16 / 30⌋ (V its voltage, whatever its amps),
  several or exotic hatches ⌊EU × 31/32 / 30⌋, wireless every ore in the bus (setting, 64 by default; 3840 EU an ore).
  3200 L Lubricant every 256 running ticks (:446-452) is 1600 L a run whatever its size: the page's 1 L × 1600 / ores.
  The pages list the ore only as an ore-dictionary alternative (`mInputs` empty), so `GTProvider` takes each
  `mOreDictAlt` slot's first stack when a recipe has no `mInputs`.
- **Extreme Crafting Center** (`TST_MegaCraftingCenter` :291-334, `TickEveryProcess` 20): runs the AE2 patterns pushed
  into it, no EU, a cycle every 20 t; crafts a cycle = pattern magnification (GUI) × pattern crafts pushed. Each
  pattern slot's container (the bucket of a lava bucket, :313-326) comes back: the page omits it, so `GTProvider` adds
  `getContainerItem` × the input's count as an output on this map.
- **Skypiercer Tower** (`TST_SkypiercerTower` :367-692; `CrystalEssentiaRecipePool`: two aspects → one, 20 t × the
  aspect's tier, 1920 EU/t). GT's `OverclockCalculator` at 1920 EU/t on the hatches' whole EU with one amp: overclocks
  = min(log4 ⌊EU / 1920⌋, voltage tiers above EV); EU/t 1920 × 4^oc for the whole run. Crystal Essence Mode (the
  card's default, its ports are the page's crystals): p = max(6 × rings, 1), (int) (page ticks / 1.2^rings), each
  overclock ÷2. Essentia Mode: p = max(16 × rings, 1), page ticks, ÷4. Passive Mode: p = max(6 × rings, 1) of output
  from one of each input, ⌈20 × the tree's seconds / 1.2^rings⌉ (setting, the aspect's own seconds by default), ÷2.
  Crystal Essence Mode also stops at the smaller of the two crystal stacks (:510-511): a setting, 64 by default.
- **Strange Matter Aggregator** (`checkProcessingMM` :922-1236, `calculateParameters` :784-866, `setRunning`
  :908-919; numbers in `Config` :114-140): one run. The maintenance fluid's 576/96/16 L picks the fluid tier.
  Constrainer factor F = oscillator tier × count × constraintor tier × count × merger tier × count × 256/1024/16384;
  parallel P = constrainers × F / (1 + streak), streak capped 24/144/384 by the merger tier; rods (long) (P × (1 +
  streak)); antimatter max(1, (long) (P × (1 + 255/√oscillators))); constrainers ⌈rods / F⌉ (as many Core Elements,
  an upkeep port, when supplied: all rods then the strange ones); plasma P × input factor × 1000/50/1; tesseracts
  P × 1.0/0.99/0.90 / F's fluid factor; frames rods × the same rate; maintenance (int) (base × (oscillator tier ×
  count)^0.8 / constraintors); byproducts mergers × 144 × streak (× 2 with Core Elements). Time constraintor tier ×
  2400/800/200 by oscillator tier × the speed controller's float; EU/t (long) (power controller float × (long)
  (merger tier × mergers^0.7 × RECIPE_MAX × 256)) in float arithmetic, gated at the hatches carrying it and
  RECIPE_MAX × 256 when wired. Wireless (no energy hatches, :787): `setRunning` :909-913 hands the network
  (long) (EU modifier × draw × the unsped run time) once a run (`addEUToGlobalEnergyMap` with a positive amount, a
  gain in 0.8.1), so the card's EU/t is that over the run's ticks, negative; no hatch gate.
- **Dyson Sphere Module Launch Site** (`TST_DSPLauncher.checkProcessing` :340-395, :499-506): one launch at the page's
  EU/t; time page ticks / motor tier, or with overload left t s: / ((1 + t^(1/(5 + 900000/t))) × 30 × motor tier), ×30
  flat at t ≤ 1. Wireless pays the page's whole EU up front (EU/t × page ticks over the shorter run). A Space Warper
  buys 900 s of overload: 1/900 a second while overloaded, an upkeep port.
- **Bottled Chaos** (`BottledChaosProcessingLogic.processWired` :120-199, :286-341; `BottledChaosOverclock` :137-201;
  `BottledChaosChainedProcessingSeconds` 20, `MinimumOverclockSeconds` 5). Base B = route EU/t × power controller;
  fastest speed S from the overclock type (Normal 2/4 without a controller module, Low Speed Perfect 2/2, Perfect
  4/4, Singularity 8/4) with a fractional last step; budget L = 400 × S / speed bonus; batches N = ⌊L / route
  ticks⌋ (one when longer); work W = N × route ticks × speed bonus; speed = min(S, max(1, W / 100)); EU/t
  min(pool, ⌈⌈B⌉ × speed^(ln power / ln time)⌉); ticks 100 when W > 100 and S reaches W / 100, else ⌈W / speed⌉.
  Gates (wired only): route EU/t over V[UHV + core level] (V[MAX] at level 4), ⌈B⌉ over the group's EU/t. Several
  groups split the hatches' EU/t by their credential frames' capacity (`BottledChaosPowerAllocator.powerForShare`
  :6-10): this group's and all groups' capacity are settings (1 of 1). Wireless (`processWireless` :202-259): every
  batch waiting (setting), folded at V[MAX]; work W = batches × route ticks × (EU/t / V[MAX]) × speed bonus, billed
  once ⌈V[MAX] × power controller × W × max(1, W / 100)^(ln(power / time) / ln time)⌉ (`wirelessCost` :28-41) over a
  100 t run, no gates. The installed sources' upkeep (`BottledChaosMachineParameters` :562-710, one fluid at so many
  L/s while running) and Critical Photons a run (energy-only sources, `roundItemDemands` :669-681) are settings that
  add upkeep ports. The route time a batch is a setting (the page's reference time by default). The credential and
  structure pages are workbench crafts: 1 t, no EU.
- **Eco-Sphere Simulator** (`TST_EcoSphereSimulator.process` :1096-1180; `common/machine/EcoSphere/`). The four maps
  are NEI-only (`.fake()`, 0 EU/t, 100 t); the execution protocol's mode works each run out and no item is ever used
  up, so a card's item inputs (circuits, saplings, seeds, focus tokens, all stack 0) are no ports and the medium is.
  Shared: power tier t = (int) calculateVoltageTier(V × A) (:1112), refused under 1; EU/t 30 × 4^(t−1) for the whole
  run (`EcoSphereModeSupport` :44-47); 100 t, 20 t with a Speed upgrade (`EcoSphereUpgradeResult` :53). Parallels P
  (:49-54): 1 at t ≤ 1, 4^(t−1) on the Tier 2 structure (a Fount of Ecology), else ⌊2 × 2^(0.1 (t−1) (8 + ln(25 +
  e^(25−t)) / ln 5))⌋ (9, 40, 159, 576, 1914, 5834, 16314 from t = 2: Tier 2 passes it at t = 8). The medium a
  parallel halves (integer, never under 1) per Fluid Reduction or Speed upgrade (:41-46). Each output stack is
  × 1.5 per Output, Perfect Genetics and Output Pulverization upgrade and × U(0.85, 1), truncated, at least 1
  (:73-123): the card has that draw's expected value, worked exactly. Settings: structure tier (Tier 1), glass (ZPM
  to UEV, only for the upgrade slots min(1 + glass × (1 or 2 on Tier 2), 9), gated), upgrade counts (0), Perfect
  Genetics on the tree and greenhouse, Blood Orb and Output Pulverization on cloning (elsewhere they are an Output or
  Capacity upgrade); a slot holds min(64, 4^capacity) and there are min(4, 1 + capacity) slots (gated).
  - Arboreal Genesis (`TreeGrowthSimulatorMode` :55-171): the card runs P × saplings parallels (setting, 1; one kind
    holds one slot's limit). An output is (long) (product × 20/3/8/1 × P × saplings × focus) where the page already
    shows product × multiplier; a page output's category is the first of log, sapling, leaves, fruit left whose
    multiplier divides it. Categories are settings (all selected; the game starts with none and refuses a run without
    one); fewer selected than the tree has focus the rest by the float 1 + (have − chosen) / chosen / 3. Temporal
    fluid, death water and unknown water make the special tree's products from any saplings (counted as full slots
    and the rest). UU matter's four symbols are one random product a category with the multiplier applied twice
    (400 / 9 / 64 / 1 a parallel), counted as products of one (the picked product's own count is random). Not
    modelled: Perfect Genetics' Forestry genome on water (the page has the species' default genome only).
  - Aquatic Simulation (`AquaticZoneSimulatorMode` :58-206): a run rolls every pool member; the card is its page's.
    Chance = ⌊100000 / pool weight⌋ × the page's stack (its weight): distilled water 156 (all 49 pages' members),
    unknown water 72 + 18 empty. A roll r in [0, 100000) makes (long) (weight × P × chance × r / 10^8) when r ≤
    chance × log2(t + 2); the card sums the 100000 rolls. Focus (setting, none): W items of this member multiply its
    chance by W, of another divide it (at least 1), an Offspring divides distilled water's by 41 W. BoP's lily meta
    3 is in the pool twice and rolls twice (the game adds both before the scaling; the card is within an item a
    run). The Offspring (t ≥ 14) has no page.
  - Artificial Greenhouse (`ArtificialGreenHouseMode` :54-106, `CropsNHFarm`): P × seeds parallels (setting, 1), the
    page's fertilizer a seed; an output is ⌈yield × P × seeds × 40⌉. Hybrid seed: (stack + (gain + 1) / 100) ×
    chance × drop chance × 1.03^gain × (100/256) / ⌈growth duration / rate⌉, rate = (6 + growth) × (1200 − 10 ×
    tier) / 100 (CropsNH 2.0.140), Perfect Genetics 31/31; alternate seed the same at 1/1/1 × 0.25; another plantable
    the page's stack × chance (its simulated drops, to 4 places) × (100/256) / 15 × 0.25. Settings: seed kind (hybrid
    where the fertilizer is not 20 L, else another plantable), crop tier, growth duration (the tier whose 600 × tier
    costs the page's fertilizer, else 1 and 600), drop chance (0.95^tier or one of CropsNH's nine overrides), growth
    and gain (1). Drops past the page's top nine are no ports.
  - Directed Mob Cloning (`DirectedMobClonerMode` :60-197): address 0 (blood to Life Essence, either structure)
    makes 100 L a parallel scaled in 5 L steps. A mob page needs Tier 2 (gated); its Life Essence (⌈4 × health⌉ a
    parallel) is exact, its soul vial is display only, its first drop is stack × P × (chance + 5000 × Looting) /
    10000 × 15125 / 1024 / D², D = max(55, (int) (health / 9 × 10)), health the Life Essence / 4; the drop's MobsInfo
    chance (100 %) and Looting (0; the Tier III protocol is X) are settings. The mob's other drops come from MobsInfo
    and have no port; an orb paying LP (1 LP for 5 L) is not modelled.

`tst.recipe.AdvCircuitAssemblyLineRecipeMap.name` has no NEI page in the pack
(`Debug_DisplayAdvCircuitAssemblyLineCurrentRecipe=false`, `GTCMRecipe` :697-712 `disableRegisterNEI`); the Advanced
Circuit Assembly Line's catalyst is on `bw.recipe.cal` (`TST_AdvCircuitAssemblyLine` :238-241), already done.

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
