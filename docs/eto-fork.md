# The ETO fork

This repository is a fork of [jackwrichards/gtnh-planner-mod](https://github.com/jackwrichards/gtnh-planner-mod) for
the ETO GT New Horizons 2.9.0 server pack. Branch `eto` is upstream `main` plus the changes below; the mod id, the
package and the save format are upstream's, so plans and the gtnhplanner.com account work as they do there.

## What differs from upstream

- **Simplified Chinese.** The fixed interface copy is in lang files (`UiText.tr`, keys `gtnhplanner.ui.*`): the
  source-level work of [Konenala21/gtnh-planner-mod-TW](https://github.com/Konenala21/gtnh-planner-mod-TW) (its
  commits are kept, with en_US and zh_TW), extended to the text it had left, with a new `zh_CN.lang`. Names follow the
  GTNH Chinese pack.
- **Data text is translated where it is drawn.** Names of machines and power sources, their settings, choices,
  readings and warnings stay English in the model (they are compared with the website's tables, saved in plans, sent
  to the website and asserted by tests). `UiText.data(english)` looks the English up under a key made from it
  (`gtnhplanner.data.<slug>`, `tools/eto/data_key.py`); a language without the key shows the English. Sentences built
  from numbers and names are matched and said again through one pattern (`ui/power/PowerText`, `ui/card/FormulaWords`).
- **Line breaks for Chinese.** `ui/TextWrap` breaks between CJK characters as well as at spaces; every wrap helper
  calls it.
- **Text input.** The planner's own editors (rate and count boxes, notes, text areas, the password box) turn the
  game's text input on while they are written in (`ui/TextInput`), so the numpad and an IME type into them under
  lwjgl3ify.
- **Wire trunks.** Wires from one output port to several cards leave as one line and fork near the cards; wires from
  several cards into one input port join near those cards and come in as one line (`layout/WireTrunks`,
  `docs/design/routing-and-arrange.md`); Settings, Board, "Shared wire trunks" turns both off.
- **Processing multiblocks.** The Extreme Industrial Greenhouse, the Integrated Ore Factory and the Large Essentia
  Smeltery are non-recipe machines under Other (`power/sources/NrGreenhouse`, `NrOreFactory`, `NrEssentiaSmeltery`,
  the game's seeds, recipes and aspects read by `power/game/Nr*Game`; essentia as card readings;
  `docs/design/nr-process.md`).
- **TwistSpaceTechnology machines.** 38 TST 0.8.0 multiblocks compute their own speed, power, parallels and
  overclocks on cards (`machines/eto/`, `assets/gtnhplanner/machines/eto-tst.json`, `docs/design/tst-machines.md`);
  TST 0.8.1's Universal Generator is a power source (`power/sources/TstGenerators`).
- **Fuel pages on generators are power cards.** A card from an NEI fuel recipe put on a generator is a stub (the fuel
  in, no EU out, as on the Large Gas Turbine), or runs as the map's first machine where the machine data has no
  handler for it (the Solid-Oxide Fuel Cells and the Universal Generator on the gas turbine fuels, GT++'s Large
  Semifluid Generator, TST's Mega Naquadah Reactor). Instead, when a power source is drawn as that generator
  (`PowerPorts.machineStack`, same item and meta) and one of its Select settings has an option whose resource
  (`PowerResources.resolve`) is one of the recipe's inputs, the card is that source's power card at that option, its
  other settings as last left (`RecipePower.fuelCard`; no table of generators). GregTech single blocks, and machines
  that are no recipe's NEI catalyst, are left alone. It applies to a new card from NEI (`MachineChoices.newNode`) and to
  a card switched to that machine from its machine menu (`BoardSession.chooseMachine`): replaced in place, one undo
  step, keeping its id, place, group, count and the wires, drawers and rates on the ports the power card has. A fuel
  the source does not list leaves the recipe card as it was.
- **Resource multiblocks.** The Void Miners, the Planetary Gas Siphon and the Space Pumping Modules are non-recipe
  machines under Other (`power/sources/NrMiners`, `NrPumps`, the game's tables read by `power/game/NrGameData`,
  `docs/design/nr-sources.md`).
- **Fixes** for reports open upstream: see `.changelogs/unreleased.md`.
- **The tour runs in English only.** It finds interface parts and NEI items by their English text; in another
  language it says so instead of starting.
- **No update offer.** A build whose version has `-eto` does not ask GitHub for upstream's releases: the pack delivers
  this fork's updates.

## Working on it

- Build and test through the wrapper on the maintainer's machine (`gp-gradle.py`, which sets the JDK and the Gradle
  heap), or `./gradlew build` anywhere with enough daemon memory (`-Dorg.gradle.jvmargs=-Xmx4g`).
- `tools/eto/lang_check.py`: `en_US.lang` against `zh_CN.lang` (keys, order, format specifiers), and `--code` for
  `UiText.tr` keys the code uses that have no line. `tools/eto/dead_keys.py` removes keys nothing names any more.
  `tools/eto/literals.py` lists string literals with no translation. `tools/eto/fold_lang.py` folds
  `lang/parts/<name>.<lang>.lang` files, which parallel work writes to avoid conflicts, into the real files.
- A lang value must not rely on a trailing space (the repository strips them): whole sentences are one pattern with
  `%s` arguments.
- Releases are tagged `<upstream version>-eto.<n>`, for example `0.2.0-beta.3-eto.1`.
- Taking upstream changes: merge `upstream/main` into `eto`. Expect conflicts where upstream edits a sentence that is
  now a `UiText.tr` call; new upstream text needs keys and zh_CN lines (`lang_check.py --code`, `literals.py`).
