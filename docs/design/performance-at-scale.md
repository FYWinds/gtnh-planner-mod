# Performance at scale

Measured 2026-10-12 on the dev game with GregTech (`PLANNH_GTNH=1`), 1920x1080, GUI scale 2, frame cap off. The
plan is the library's Platline (49 cards in game) tiled 10, 40, 200 and 400 times into one plan, so every size is the
same real plan many times over. Nothing was solved past 490 cards (see the solver below), so the larger boards show
unsolved cards.

## How to reproduce

```bash
PLANNH_GTNH=1 tools/dev/mc.sh start
tools/dev/mc.sh call 'importff?file=<a real plan>'   # e.g. a library download
tools/dev/mc.sh call open
tools/dev/mc.sh call 'stress?copies=10'               # the open plan tiled 10x, one edit
tools/dev/mc.sh call 'stress?fps=300'                 # frame cap and vsync off
tools/dev/mc.sh call 'stress?fit=1'                   # frame the whole plan
tools/dev/mc.sh call 'stress?animate=pan'             # pan | zoom | yaw | none, every frame
tools/dev/mc.sh call 'perf?start=1'; sleep 5; tools/dev/mc.sh call perf
tools/dev/mc.sh call 'stress?timesave=1'              # what one edit costs: undo encodes, save
tools/dev/mc.sh call 'stress?world=1&spacing=5'       # every machine placed in the world, one edit
tools/dev/mc.sh call stress                           # the plan as numbers (cards, wires, placed, heap)
```

Profiles: `jcmd <pid> JFR.start settings=profile filename=x.jfr`, then `jfr print --json --events
jdk.ExecutionSample` and sum the client thread's stacks (the pid is in `run/client/plannh-dev.pid`).

## Numbers

| Plan | Board fps, whole plan in view | p50 frame | One edit (undo + save) | Undo | Solve |
| --- | --- | --- | --- | --- | --- |
| 49 cards | 112 (165 panning) | 6-9 ms | 11 ms | 10 ms | 1.8 s |
| 490 | 55 | 17 ms | 85 ms | 100 ms | 34 s |
| 1,960 | 33 | 30 ms | 280 ms | 130 ms | did not finish in 15 min |
| 9,800 | 3.7 (3.4 zoomed in on a few cards) | 235 ms | 1.15 s | 1.6 s | - |
| 19,600 | 1.1 | 790 ms | 2.9 s | 6.4 s | - |

| Plan over the world | fps | p50 frame |
| --- | --- | --- |
| 9,800 machines placed (5 blocks apart, around the player) | 2.3 standing, 2.1 turning | 430 ms |

Other costs: tiling 7,840 cards in one paste took 3 s, 9,800 more took 9 s. Routing every wire took 1.7 s at 490 cards,
3.3 s at 1,960 and 23 s at 19,600, on the router's thread. The save at 19,600 cards is 5.6 MB, and its debug copy is
50 MB. The heap stayed under 3 GB of 6.

## What looks wrong, worst first

1. **The solver** (fixed 2026-10-12, `docs/design/solver-plan.md`: the plan's islands solve apart and are cached, so 19,600 cards solve in 4.4 s and an edit in 0.36 s). One ILP for the whole plan: ten unconnected copies took 19 times as long as one, and 40 copies
   never finished. The thread dump sits in `GateCountStage.deletionFilter`, which solves the whole LP again once per
   candidate gate (`Solver.externalsLp`), so the cost grows with gates times plan size. Unconnected parts could be
   solved on their own, and a part no edit touched need not be solved again. A stale solve also runs to the end
   before the newer one starts.
2. **The look ray in the world.** `WorldView.findLook` → `WorldLinks.onRay` tests every placed machine of every plan
   each tick, and each test builds a structure's cache key as a string (`StructureGhosts.peek`, `key`, `channels`).
   88% of the client thread with 9,800 placed, and it keeps running behind the open planner (72% at 19,600 cards).
   Wants the extents cached per placement and a spatial index (chunks), rebuilt when placements change.
3. **Every edit encodes the whole plan about four times on the client thread.** The undo snapshot encodes the graph
   before and after (pretty JSON, gzip, base64) and compares the strings; then the save writes every plan, and its
   debug copy is ten times bigger. Undo decodes a whole graph. Dragging one card in a 10,000-card plan stalls for over a
   second. Saves could go to a background thread and coalesce; undo could keep diffs or structural copies.
4. **The overview rebuilds its lists every frame.** `OverviewRail.resources` → `openPorts` calls `Graph.drawerAt`,
   a scan of every drawer, for every port: 40% of the client thread at 9,800 cards (120 ms a frame). `pinsFor`,
   `drawersFor` and `BoardSession.canonicalKey` add more. It only changes when the plan or its solve changes.
5. **ModularUI walks every widget every tick and frame.** `WidgetTree.onUpdate` (`TreeUtil.foreachChildBFS`, 22% at
   9,800) and the per-frame resize (`InternalWidgetTree.resize`, `WidgetResizeNode.getArea`). Every card and drawer is
   a widget whether it is on screen or not. Cards off screen could leave the tree, or the canvas could update only
   what is in view.
6. **The board's drawing.** About 17,000 card and drawer draws during a five-second sample at 490 cards, many tiny
   tessellator draws (`TessellatorStreamingDrawer.draw` 13% of the client thread), the zoomed-out labels' overlap
   test is every label against every other (`ZoomedOutLabels.overlapsAny`), and the wire display list rebuild spiked
   to 1.75 s at 9,800 cards. `BoardCanvas.cardHolding` scans every card too.
7. **The world drawing** of the placed machines: `ar.ghosts` about 35 ms a frame at 9,800 placed, drawn even behind
   the open planner; `ar.prep+layout` 7 ms.

## The owner's notes (2026-10-12)

- Zoomed far out, wires shrink to about a pixel and flicker. Draw them at a fixed screen width, or fade them out,
  below some zoom.
- Zoom out further: the floor is 1/16 (`BoardCanvas.ZOOMS`); it should reach as far as the plan is big.

## What was done (2026-10-12)

Measured again on the same 19,600-card plan with 9,800 machines placed, frame cap off:

| | Before | After |
| --- | --- | --- |
| Board at zoom 1, still or panning | 1.1 fps | 135-165 fps (as a 49-card plan) |
| Board fully zoomed out (1/16, about 1,000 cards and drawers on screen) | about 3 fps | about 60 fps |
| Plan over the world | 2.2 fps | 165-185 fps |
| Opening the planner, first time in a session | 17 s frozen, 23 s until smooth | 5 s frozen, 11 s until smooth |
| Opening a 196-card plan | | 0.2 s |

- The look ray and the overlay ask `ui/world/WorldIndex` (placements in 16-block columns, made again when a plan, its
  placements, the board's snapshot or the known ghosts change) instead of every placement; `PlanSnapshot.cardOf` and
  `wiresFrom` are indexed; a structure ghost's cache key is worked out once (`StructureGhosts.key`).
- Ghosts are drawn from GTNHLib vertex buffers captured once per structure (`ui/gt/GhostMeshes`) wherever none of
  their blocks is built yet (rechecked twice a second), and only those in the camera's view.
- NEI lays out every frame and our layout style asked for a whole new layout of the board each time: it now does only
  when NEI's item panel or the screen moved (`FlowchartLayoutStyle`).
- `BoardCanvas.getChildren` hands ModularUI only the cards and drawers in view (filed in 512-unit cells) and whatever
  is in hand; it is every child while the board builds or disposes them. Cards out of view catch up with new models
  when the models change (`syncModels`).
- The overview works out what sets each resource once per plan state (`OverviewRail.Index`), groups its machines once
  per totals, and draws only the rows in its list's view.
- Wires: the geometry is hashed only when something could have moved (`WireLayer.moved`), hover tests and hops use a
  grid of runs (`SegmentGrid`), the draw order is kept, and far out every wire keeps 1.5 screen pixels of width (no
  more flicker) with its shadow and arrows left out.
- The zoomed-out names are placed once while nothing changes, against grids, and drawn in two draws.
- Loading a save adds wires and drawers through indexes (`Graph.addEdgesLoaded`, `addDrawersLoaded`): one at a time,
  each looked through all the others. Undo decodes the same way.
- The machine maths' name normalizers (regular expressions) are worked out once per name.

Tried and dropped: recording the zoomed-out faces into display lists (Angelica replays them no faster than drawing
them) and rendering board tiles into textures (about 175 fps fully zoomed out, but some 500 lines of framebuffer code
for a case few plans reach).

Still slow on huge plans: an edit encodes every plan for the undo snapshot and the save (about 1 s at 19,600 cards,
20 ms at 200), and opening builds every card's model and widget on the client thread. A 200-card plan feels neither.

## In the full pack (2026-11-23)

The numbers above are from the dev game. A player's report from the ETO GTNH 2.9.0 pack (343 mods, Angelica 2.2.43,
Qz-UILib 5.0.0-beta5 drawing the game's text) said a board with many cards drops frames badly and the first open
after joining freezes. Measured in a copy of that pack: Java 25, the window 3818x2032 at GUI scale 2, a 250-card plan
(Factory Flow's "Early auto Baux-Ti-W" tiled 10 times) on a team backend, 35 cards and 45 drawers in view in detail
(zoom 0.574; `board.zoomedOutAt=35` so that zoom is not the glance view).

The harness runs in a shipped jar with `-Dgtnhplanner.dev=true`, so the steps are the ones under "How to reproduce"
with `curl http://127.0.0.1:25599/...` in place of `mc.sh call`. Give the window its size before measuring: frame
time grows with the board's area on screen.

| 35 cards and 45 drawers in view | Before | After |
| --- | --- | --- |
| Qz-UILib draws the text (`mc.replaceVanillaFont: true`) | 15.7 fps, 63.7 ms | 27.3 fps, 36.7 ms |
| Angelica draws the text (`false`) | 25.9 fps, 38.6 ms | 69.5 fps, 14.4 ms |

Fully zoomed out (248 cards as glances) nothing changed: 38 fps with Qz-UILib's text, 64 with Angelica's.

What it was, from the client thread's samples with Angelica's text (the planner's drawing was 85% of the thread):

- **The dot grid, 34%.** A draw for every dot, 12,500 of them on that screen. Now a display list for each zoom and
  board size, moved by the pan and cut at the board's edge (`BoardCanvas.drawDots`).
- **Diagonal stripes, about 25%.** A free end's tile, a lit tile and a drawer's frame were a rectangle for every run
  of every row, thousands of vertices a card, and a vertex costs about 40 ns through Angelica's tessellator path. Now
  one quad of a repeated texture for each box (`Hyb.stripes`), the same pixels.
- **A thread-local for every vertex, 10%.** ModularUI's buffer reads `Tessellator.instance` up to three times a
  vertex, and GTNHLib turns each read into a thread-local lookup. `Hyb.endBatch` reads it once.

With Qz-UILib's text the text is most of what is left: in the player's own profile its `FontRenderer` hooks were 38%
of the client thread and 56% of the planner's drawing, a fixed cost for every string drawn (it replays GL state round
each one) and for every width measured. That is the pack's to settle, with `mc.replaceVanillaFont` in
`config/qz_uilib.yaml`.

The first open after joining, the same plan: the two longest frames added up to about 1.8 s (1.03 + 0.85 s and
0.96 + 0.79 s in two runs) and now to about 1.0 s (0.62 + 0.40 s, 0.60 + 0.43 s, 0.81 + 0.12 s). What went is reading
every plan of the team into boards, which a team backend now does as the player joins. What is left is first-time
work that grows with the open plan, none of it more than a quarter: loading the planner's classes, building 250
cards and their widgets, the world snapshot's structure shadows and item colours (`SnapshotMaker.make`), the wires'
first routing and draw, and NEI's item panel. A second open in the same game takes 50 to 80 ms.
