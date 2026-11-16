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
- **Pinyin search.** With NeverEnoughCharacters in the pack (mod id `nech`), the planner's own search boxes (the
  overview filter, the non-recipe machine picker, pick lists, the plan library) also match Chinese text by its pinyin,
  as NEI's search does there (`TextMatch`, which calls nech's matcher by reflection and is a plain substring test
  without it).
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
- **Team mode.** On a server running ETO GTNH Tweaks, cloud plans and live editing are the server's, shared with
  the player's GTNHLib team, instead of gtnhplanner.com's (see "Team mode" below).
- **Fixes** for reports open upstream: see `.changelogs/unreleased.md`.
- **The tour runs in English only.** It finds interface parts and NEI items by their English text; in another
  language it says so instead of starting.
- **No update offer.** A build whose version has `-eto` does not ask GitHub for upstream's releases: the pack delivers
  this fork's updates.

## Team mode

ETO GTNH Tweaks serves the planner's cloud protocol (`docs/design/cloud-live.md`: the website's REST routes, the live
socket) on the Minecraft game port under `/etotweaks/planner`, scoped to the player's GTNHLib team, with the Minecraft
player as the account (UUID as id, Minecraft name as username). After login the server says hello, and Tweaks' client
side hands the connection over by reflection; on disconnect it hands it back. Neither mod needs the other.

```java
com.gtnhplanner.api.TeamBackend.attach(site, live, token, userId, username, serverId, worldKey);
com.gtnhplanner.api.TeamBackend.detach();
```

- `site` is the REST base (`http://<host>:<port>/etotweaks/planner`), `live` the socket
  (`ws://<host>:<port>/etotweaks/planner/live`), `token` the server's session (`<uuid>.<expires>.<hmac>`), `userId`
  and `username` the player, `serverId` the server's plan store, `worldKey` the world's key. `attach` again with the
  same `serverId` and `userId` only takes the new session; `detach` is safe when nothing is attached. Each call logs one
  INFO line (`[team] attached: site=... userId=... username=... serverId=...`, `[team] detached: ...`), never the session.
- What moves: cloud REST and the socket go to `site` and `live` (the planner asks `P/live/health`, `P/live/nearby`,
  `P/api/community/auth/me`, `P/api/library`, `P/api/library/designs/<id>[?owner=]`, `.../members`,
  `P/api/accounts/search?q=` and opens `P/live`), with `Cookie: gtnh_session=<token>`. The session decides the host
  (`CloudApi.useTeam`, `site(token)`, `live(token)`): a server's session goes to that server only, even a call still
  under way after a new one came or the player left; a session's calls stop once the server is detached
  (`CloudHooks.session`). Who cloud plans are for (`Account.accountId`, `userId`, `username`: the top bar, tasks'
  people, comments, presence) is the player there; nothing of it is written to `config/gtnhplanner-account.properties`.
  This device keeps the plans under `gtnhplanner/team/<serverId>/<userId>/` (the sticky `device.json` too), apart from
  every website account and every other server. Placed machines and task locations use `worldKey`
  (`GameCloud.worldKey`). Entering and leaving is heard as a sign-in and sign-out (`Account.onChange`, on the client
  thread whatever thread called): the plans in hand are saved where they belong and load from the team. The first load
  sends the world's own plans into the team, as the first sign-in does for an account (`CloudSession.bringOver`,
  `Migration`), each keyed to the player so it goes once per server store and player.
- What stays: the community library (its list goes with the website's session, if any), feedback, the website listener
  and the website's sign-in, which is the one again after `detach`.
- What is hidden while attached: Sign in, Sign out and Create account (the top bar's account key is the player's face
  and name, with no menu), Invite people and the share box (the team is who edits), Publish to Community and Update in
  Community, the Community shelf's My posts and Edit and Delete on posts. The library has one shelf, TEAM, with the
  player's plans and the team mates' together (a team mate's says who made it), in place of My plans and Shared with
  me; Community stays. A team mate's plan is the player's to delete for the team (`DELETE ...?owner=`), never to leave.
  Assigning tasks works as it does signed in.
- Testing without the game: `TeamBackendLiveTest` runs the planner's session against `LiveTestServer` mounted under
  the path, with a session per player. `TeamE2ETest` runs the planner's client against a real server (two players of
  one team, optionally one outside it) and prints PASS or FAIL a step (also to `build/team-e2e.log`):
  `gp-gradle.py test --tests com.gtnhplanner.cloud.TeamE2ETest -Pteam.site=http://HOST:PORT/etotweaks/planner
  -Pteam.tokenA=... -Pteam.tokenB=... [-Pteam.tokenC=...]`.

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
