package com.gtnhplanner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.layout.RouteMetrics;
import com.gtnhplanner.layout.WireTrunks;

/**
 * Wires on one trunk as the routes' corners show them: how far two run together and where they part, the tree they
 * make, and the yardstick measuring the trunk once.
 */
class WireTrunksTest {

    /** Out of the port right to 600, on to the far box. */
    private static final List<int[]> TRUNK = List.of(p(320, 105), p(800, 105));
    /** Along the trunk to 600, then down a diagonal and on right. */
    private static final List<int[]> DOWN = List.of(p(320, 105), p(600, 105), p(760, 265), p(800, 265));
    /** Along the trunk and the first branch's diagonal to (700, 205), then straight down. */
    private static final List<int[]> FURTHER = List.of(p(320, 105), p(600, 105), p(700, 205), p(700, 515), p(800, 515));

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

    private static int[] p(final int x, final int y) {
        return new int[] { x, y };
    }
}
