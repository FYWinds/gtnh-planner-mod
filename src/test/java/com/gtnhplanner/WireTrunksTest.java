package com.gtnhplanner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.layout.RouteMetrics;
import com.gtnhplanner.layout.WireTrunks;

/**
 * Wires on one trunk as the routes' corners show them: how far two run together and where they part, the tree they
 * make, and the yardstick measuring the trunk once; the same for wires coming into one port, and for a wire on both.
 */
class WireTrunksTest {

    /** Out of the port right to 600, on to the far box. */
    private static final List<int[]> TRUNK = List.of(p(320, 105), p(800, 105));
    /** Along the trunk to 600, then down a diagonal and on right. */
    private static final List<int[]> DOWN = List.of(p(320, 105), p(600, 105), p(760, 265), p(800, 265));
    /** Along the trunk and the first branch's diagonal to (700, 205), then straight down. */
    private static final List<int[]> FURTHER = List.of(p(320, 105), p(600, 105), p(700, 205), p(700, 515), p(800, 515));
    /** The same three the other way round: from the far boxes into the port at (320, 105). */
    private static final List<int[]> INTO = TRUNK.reversed(), UP = DOWN.reversed(), FURTHER_UP = FURTHER.reversed();

    @Test
    void twoPathsRunTogetherUntilTheyPart() {
        final WireTrunks.Branch b = WireTrunks.parting(TRUNK, DOWN);
        assertEquals(280, b.length(), 1e-9);
        assertArrayEquals(p(600, 105), b.at());
        // Into the diagonal and down it to where FURTHER turns: 280 along, then 100 px of diagonal.
        final WireTrunks.Branch c = WireTrunks.parting(DOWN, FURTHER);
        assertEquals(280 + 100 * Math.sqrt(2), c.length(), 1e-9);
        assertArrayEquals(p(700, 205), c.at());
        // Apart from the start: nothing shared.
        assertNull(WireTrunks.parting(TRUNK, List.of(p(320, 125), p(800, 125))));
        assertNull(WireTrunks.parting(TRUNK, List.of(p(320, 105), p(320, 400))));
    }

    @Test
    void eachWireHangsOffTheOneItRunsAlongLongest() {
        final WireTrunks.Branch[] tree = WireTrunks.tree(List.of(TRUNK, DOWN, FURTHER));
        assertEquals(-1, tree[0].parent());
        assertEquals(0, tree[1].parent());
        assertEquals(1, tree[2].parent());
        assertArrayEquals(p(700, 205), tree[2].at());
        // Its own stretch: from where it leaves on.
        final List<int[]> own = WireTrunks.between(FURTHER, tree[2].length(), tree[2].at(), 0, null);
        assertEquals(3, own.size());
        assertArrayEquals(p(700, 205), own.getFirst());
        assertArrayEquals(p(800, 515), own.getLast());
        // A stretch between two points along it.
        final List<int[]> mid = WireTrunks.between(DOWN, 100, p(420, 105), 280 + 50 * Math.sqrt(2), p(650, 155));
        assertEquals(3, mid.size());
        assertArrayEquals(p(600, 105), mid.get(1));
    }

    @Test
    void theYardstickMeasuresATrunkOnce() {
        final List<RouteMetrics.Box> boxes = List.of(
            new RouteMetrics.Box(0, 0, 320, 200),
            new RouteMetrics.Box(800, 0, 320, 200),
            new RouteMetrics.Box(800, 250, 320, 200));
        final RouteMetrics.Result apart = RouteMetrics
            .measure(boxes, List.of(new RouteMetrics.Wire(0, 1, TRUNK), new RouteMetrics.Wire(0, 2, DOWN)));
        final RouteMetrics.Result shared = RouteMetrics.measure(
            boxes,
            List.of(new RouteMetrics.Wire(0, 1, TRUNK, "port"), new RouteMetrics.Wire(0, 2, DOWN, "port")));
        // As two wires: the 280 px they share is overlap, and they end at one point on the port's box.
        assertEquals(280, apart.overlap(), 1e-9);
        assertEquals(1, apart.sharedEnds());
        // As a trunk: drawn once, the branch's turn off it a bend, nothing doubled.
        assertEquals(0, shared.overlap(), 1e-9);
        assertEquals(0, shared.sharedEnds());
        assertEquals(apart.length() - 280, shared.length(), 1e-9);
        assertEquals(apart.bends(), shared.bends());
        assertEquals(apart.excess(), shared.excess(), 1e-9);
        assertEquals(apart.crossings(), shared.crossings());
    }

    @Test
    void eachWireJoinsTheOneItRunsAlongLongestIntoThePort() {
        final WireTrunks.Branch[] tree = WireTrunks.treeIn(List.of(INTO, UP, FURTHER_UP));
        assertEquals(-1, tree[0].parent());
        assertArrayEquals(p(320, 105), tree[0].at());
        // Joins the trunk at 600, 280 px before the port.
        assertEquals(0, tree[1].parent());
        assertEquals(280, tree[1].length(), 1e-9);
        assertArrayEquals(p(600, 105), tree[1].at());
        // Joins the first one's diagonal at (700, 205), and runs with it from there.
        assertEquals(1, tree[2].parent());
        assertEquals(280 + 100 * Math.sqrt(2), tree[2].length(), 1e-9);
        assertArrayEquals(p(700, 205), tree[2].at());
    }

    @Test
    void wiresIntoOnePortAreDrawnOnceAndComeInAsOneLine() {
        final List<List<int[]>> paths = List.of(INTO, UP, FURTHER_UP);
        final WireTrunks.Share[] shares = WireTrunks
            .shares(paths, Arrays.asList(null, null, null), List.of("port", "port", "port"));
        assertEquals(
            -1,
            shares[1].head()
                .parent());
        assertEquals(
            0,
            shares[1].tail()
                .parent());
        assertEquals(
            1,
            shares[2].tail()
                .parent());
        final List<WireTrunks.Stretch> drawn = WireTrunks.stretches(paths, shares);
        assertEquals(5, drawn.size());
        // The trunk: on its own to where the first joins it, then all three into the port.
        assertStretch(drawn.get(0), 0, new int[] { 0 }, p(800, 105), p(600, 105));
        assertStretch(drawn.get(1), 0, new int[] { 0, 1, 2 }, p(600, 105), p(320, 105));
        // The first: on its own to where the second joins it, then the two of them to the trunk.
        assertStretch(drawn.get(2), 1, new int[] { 1 }, p(800, 265), p(700, 205));
        assertStretch(drawn.get(3), 1, new int[] { 1, 2 }, p(700, 205), p(600, 105));
        assertStretch(drawn.get(4), 2, new int[] { 2 }, p(800, 515), p(700, 205));
        // Each stretch once: what is drawn is the wires' own stretches, nothing twice.
        assertEquals(480 + 40 + 160 * Math.sqrt(2) + 410, drawnLength(drawn), 1e-6);
        for (int i = 0; i < paths.size(); i++)
            assertEquals(WireTrunks.length(paths.get(i)), carried(drawn, i), 1e-6, "wire " + i);
        final List<int[]> dots = WireTrunks.junctions(paths, shares);
        assertEquals(2, dots.size());
        assertArrayEquals(p(600, 105), dots.get(0));
        assertArrayEquals(p(700, 205), dots.get(1));
    }

    @Test
    void aWireOnBothTrunksLeavesTheOneAndJoinsTheOther() {
        // From the port at (0, 0): up to one box, and right to the port at (1000, 0), which a wire from below feeds
        // too.
        final List<int[]> up = List.of(p(0, 0), p(300, 0), p(300, -300));
        final List<int[]> below = List.of(p(700, 300), p(700, 0), p(1000, 0));
        final List<int[]> both = List.of(p(0, 0), p(1000, 0));
        final List<List<int[]>> paths = List.of(up, below, both);
        final List<String> out = Arrays.asList("from", null, "from"), in = Arrays.asList(null, "into", "into");
        assertArrayEquals(new int[] { 0, 0, 0 }, WireTrunks.bundles(out, in));
        final WireTrunks.Share[] shares = WireTrunks.shares(paths, out, in);
        assertEquals(
            0,
            shares[2].head()
                .parent());
        assertEquals(
            300,
            shares[2].head()
                .length(),
            1e-9);
        assertEquals(
            1,
            shares[2].tail()
                .parent());
        assertEquals(
            300,
            shares[2].tail()
                .length(),
            1e-9);
        final List<WireTrunks.Stretch> drawn = WireTrunks.stretches(paths, shares);
        assertEquals(5, drawn.size());
        assertStretch(drawn.get(0), 0, new int[] { 0, 2 }, p(0, 0), p(300, 0));
        assertStretch(drawn.get(1), 0, new int[] { 0 }, p(300, 0), p(300, -300));
        assertStretch(drawn.get(2), 1, new int[] { 1 }, p(700, 300), p(700, 0));
        assertStretch(drawn.get(3), 1, new int[] { 1, 2 }, p(700, 0), p(1000, 0));
        // Its own stretch is only the middle, between the two trunks: no gap, nothing twice.
        assertStretch(drawn.get(4), 2, new int[] { 2 }, p(300, 0), p(700, 0));
        assertEquals(600 + 600 + 400, drawnLength(drawn), 1e-6);
        for (int i = 0; i < paths.size(); i++)
            assertEquals(WireTrunks.length(paths.get(i)), carried(drawn, i), 1e-6, "wire " + i);
    }

    @Test
    void aWireRunningStraightFromOneTrunkOntoTheOtherIsCountedOnce() {
        // It runs with the first wire to 800 and with the second from 500: the head has the stretch both would take.
        final List<int[]> first = List.of(p(0, 0), p(800, 0), p(800, -300));
        final List<int[]> second = List.of(p(500, 300), p(500, 0), p(1000, 0));
        final List<int[]> both = List.of(p(0, 0), p(1000, 0));
        final List<List<int[]>> paths = List.of(first, second, both);
        final WireTrunks.Share[] shares = WireTrunks
            .shares(paths, Arrays.asList("from", null, "from"), Arrays.asList(null, "into", "into"));
        assertEquals(
            800,
            shares[2].head()
                .length(),
            1e-9);
        assertEquals(
            200,
            shares[2].tail()
                .length(),
            1e-9);
        assertArrayEquals(
            p(800, 0),
            shares[2].tail()
                .at());
        final List<WireTrunks.Stretch> drawn = WireTrunks.stretches(paths, shares);
        for (final WireTrunks.Stretch s : drawn) assertTrue(s.wire() != 2, "nothing of its own");
        assertEquals(1000, carried(drawn, 2), 1e-6);
    }

    @Test
    void trunksTieWiresIntoBundles() {
        // Two leave one port, the second and a third come into another; a fourth on no trunk; a fifth alone on its key.
        assertArrayEquals(
            new int[] { 0, 0, 0, -1, -1 },
            WireTrunks.bundles(Arrays.asList("a", "a", null, null, "b"), Arrays.asList(null, "c", "c", null, null)));
    }

    @Test
    void theYardstickMeasuresAFanInOnce() {
        final List<RouteMetrics.Box> boxes = List.of(
            new RouteMetrics.Box(0, 0, 320, 200),
            new RouteMetrics.Box(800, 0, 320, 200),
            new RouteMetrics.Box(800, 250, 320, 200));
        final RouteMetrics.Result apart = RouteMetrics
            .measure(boxes, List.of(new RouteMetrics.Wire(1, 0, INTO), new RouteMetrics.Wire(2, 0, UP)));
        final RouteMetrics.Result shared = RouteMetrics.measure(
            boxes,
            List.of(new RouteMetrics.Wire(1, 0, INTO, null, "port"), new RouteMetrics.Wire(2, 0, UP, null, "port")));
        // As two wires: the 280 px they share is overlap, and they end at one point on the port's box.
        assertEquals(280, apart.overlap(), 1e-9);
        assertEquals(1, apart.sharedEnds());
        // As a trunk: drawn once, the turn onto it a bend, nothing doubled.
        assertEquals(0, shared.overlap(), 1e-9);
        assertEquals(0, shared.sharedEnds());
        assertEquals(apart.length() - 280, shared.length(), 1e-9);
        assertEquals(apart.bends(), shared.bends());
        assertEquals(apart.excess(), shared.excess(), 1e-9);
        assertEquals(apart.crossings(), shared.crossings());
    }

    /** A stretch: whose it is, the wires along it, and where it starts and ends. */
    private static void assertStretch(final WireTrunks.Stretch s, final int wire, final int[] along, final int[] from,
        final int[] to) {
        assertEquals(wire, s.wire());
        assertArrayEquals(along, s.along());
        assertArrayEquals(
            from,
            s.path()
                .getFirst());
        assertArrayEquals(
            to,
            s.path()
                .getLast());
    }

    private static double drawnLength(final List<WireTrunks.Stretch> drawn) {
        double total = 0;
        for (final WireTrunks.Stretch s : drawn) total += WireTrunks.length(s.path());
        return total;
    }

    /** How long the stretches carrying a wire are, all told. */
    private static double carried(final List<WireTrunks.Stretch> drawn, final int wire) {
        double total = 0;
        for (final WireTrunks.Stretch s : drawn) if (Arrays.stream(s.along())
            .anyMatch(k -> k == wire)) total += WireTrunks.length(s.path());
        return total;
    }

    private static int[] p(final int x, final int y) {
        return new int[] { x, y };
    }
}
