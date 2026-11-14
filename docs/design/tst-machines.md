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
| 19013–19015 | DSP Launch Site / Ray Receiver / Artificial Star | — | — | not processing | launcher / generators |
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
| 19026 | Mega Egg Generator | — | — | not processing | generator |
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
| 19053 | Lightning Spire | 闪电尖塔 | — | not processing | generator |
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
| 19073, 19074, 19076 | Steam Basic Generator, Universal Generator, Large Solar Boiler | — | — | not processing | generators |
| 19079 | Integrated Assembly Matrix | 集成装配矩阵 | Mega Assembly Line | done | nanites; data-stick whitelist and highest-hatch voltage cap not modelled |
| 19080 | Mega Solar Panel Factory | 巨型太阳能板工厂 | Mega Solar Panel Factory | done | component casing |

Not registered in 0.8.0: `TST_TombOfTheDragon`, `TST_PatternMachineClass`, `GTCM_TestMultiMachine`.

## Other mods in the pack

None adds a recipe-processing machine:

- programmablehatches 0.2.1p7: hatches, plus three multiblocks (Ingredient Distributor, Large Programming Circuit
  Provider, Multiblock Proxy) with their own non-recipe processing.
- ae2thing 1.3.0: AE2 tiles (Essentia Discretizer, Infusion Interface, Wireless Distributor) and NEI helpers.
- msbf 1.0: mouse side-button fix. EZMiner 6.3: vein miner.

## Text

Machine, map, setting, option and gate labels are English data, drawn through `UiText.data`; their Simplified Chinese
lines (pack names) are in `lang/parts/tst.zh_CN.lang`.
