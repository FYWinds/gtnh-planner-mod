package com.gtnhplanner.bench;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.gtnhplanner.layout.RouteMetrics;
import com.gtnhplanner.layout.WireRouter;

/**
 * Fan-outs drawn with their wires apart and on a trunk, side by side ({@code build/bench/<label>/trunk-*.png}): one
 * card feeding a column of six, the card above the column, cards all round it, and two ports of one card feeding
 * alternate cards. {@code -Pbench=<label> --tests '*TrunkPictures*'}; left out of the normal run.
 */
@Tag("bench")
class TrunkPictures {

    @Test
    void pictures() throws IOException {
        draw("A-column", board(new int[] { 0, 300 }, column(900, -150, 6, 230), 1));
        draw("B-above", board(new int[] { 0, -400 }, column(900, -150, 5, 230), 1));
        draw(
            "C-spread",
            board(
                new int[] { 600, 300 },
                new int[][] { { 1300, 100 }, { 1300, 500 }, { 500, 900 }, { 1000, 900 }, { -200, 300 } },
                1));
        draw("D-two-ports", board(new int[] { 0, 300 }, column(900, -150, 6, 230), 2));
    }

    private static int[][] column(final int x, final int y0, final int n, final int step) {
        final int[][] out = new int[n][];
        for (int k = 0; k < n; k++) out[k] = new int[] { x, y0 + step * k };
        return out;
    }

    /** A card at {@code source} and one at each target, the n-th fed from port n mod {@code ports}. */
    private static BenchBoard board(final int[] source, final int[][] targets, final int ports) {
        final List<BenchBoard.Item> items = new ArrayList<>();
        final List<BenchBoard.Link> links = new ArrayList<>();
        final BenchBoard.Item from = item("source", source);
        items.add(from);
        for (int t = 0; t < targets.length; t++) {
            final BenchBoard.Item to = item("target" + t, targets[t]);
            items.add(to);
            final int port = t % ports;
            links.add(new BenchBoard.Link(uuid("link" + t), from.id(), port, to.id(), 0, "res" + port));
        }
        return new BenchBoard("trunks", items, links);
    }

    private static BenchBoard.Item item(final String name, final int[] at) {
        return new BenchBoard.Item(uuid(name), name, false, false, at[0], at[1], 320, 180, 3, 3);
    }

    private static void draw(final String name, final BenchBoard b) throws IOException {
        for (final boolean on : new boolean[] { false, true }) {
            final List<WireRouter.Box> boxes = new ArrayList<>();
            final Map<UUID, Integer> index = new HashMap<>();
            for (final BenchBoard.Item i : b.items()) {
                index.put(i.id(), boxes.size());
                boxes.add(new WireRouter.Box(i.x(), i.y(), i.w(), i.h()));
            }
            final List<WireRouter.Wire> wires = new ArrayList<>();
            final List<RouteMetrics.Wire> measured = new ArrayList<>();
            for (final BenchBoard.Link l : b.links()) wires.add(
                new WireRouter.Wire(
                    l.id(),
                    index.get(l.from()),
                    index.get(l.to()),
                    WireRouter.ANY,
                    WireRouter.ANY,
                    0,
                    List.of(),
                    on ? BenchRun.trunk(l) : null));
            final List<UUID> fellBack = new ArrayList<>();
            final Map<UUID, List<int[]>> routes = new WireRouter().route(boxes, wires, fellBack, true);
            final List<RouteMetrics.Box> mboxes = new ArrayList<>();
            for (final WireRouter.Box x : boxes) mboxes.add(new RouteMetrics.Box(x.x(), x.y(), x.w(), x.h()));
            for (final BenchBoard.Link l : b.links()) measured.add(
                new RouteMetrics.Wire(
                    index.get(l.from()),
                    index.get(l.to()),
                    routes.get(l.id()),
                    on ? BenchRun.trunk(l) : null));
            final BenchRun.Routed r = new BenchRun.Routed(
                b,
                routes,
                fellBack,
                0,
                RouteMetrics.measure(mboxes, measured));
            final String label = System.getProperty("gtnhplanner.bench", "run");
            BenchRender.render(
                r,
                name + (on ? " trunks" : " apart"),
                new File(
                    "build/bench/" + (label.isEmpty() ? "run" : label),
                    "trunk-" + name + (on ? "-on" : "-off") + ".png"));
        }
    }

    private static UUID uuid(final String s) {
        return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
