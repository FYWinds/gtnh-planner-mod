package com.gtnhplanner.layout;

import java.util.List;
import java.util.Locale;

/**
 * What a set of routed wires looks like, in numbers: the yardstick the router and the arrange are tuned against, and
 * the terms of the score that picks between arrangements. Pure geometry over world-space polylines and boxes.
 *
 * <ul>
 * <li>{@code crossings}: places where two wires cross, proper intersections of their segments (not where they only
 * touch or run along each other).</li>
 * <li>{@code overlap}: length two wires share, drawn one on the other so neither can be followed.</li>
 * <li>{@code bends45}, {@code bends90}, {@code sharp}: corners by how sharp they are, as Factory Flow counts them
 * (under
 * about 8 degrees none; to 60, a 45; to 120, a right angle; past that, sharp: a 135-degree kink or doubling back).</li>
 * <li>{@code length}: total length.</li>
 * <li>{@code excess}: length beyond the shortest a wire could be between its two boxes (straight across the gap, or
 * one 45-degree run where they stand diagonally apart): detours, loops, and ends on sides that face away.</li>
 * <li>{@code backtrack}: length run away from where a wire is going, on either axis: a wire heading right that goes
 * left for a while, or one that leaves, doubles back and loops round.</li>
 * <li>{@code jogs}: short sidesteps between two parallel runs (under three cells): the little hooks where two ends
 * almost line up.</li>
 * <li>{@code wrongSides}: ends that leave or enter a box on a side facing away from the other box.</li>
 * <li>{@code boxHits}: length run through a box (a card or drawer), its own two included past their edges.</li>
 * <li>{@code sharedEnds}: wires meeting a box at the same point as another wire.</li>
 * </ul>
 *
 * Wires that share a trunk ({@link Wire#trunk()}) are measured as drawn: the trunk once, each wire from where it
 * branches off it (the turn there included), so running together on the trunk is not overlap and leaving the port as
 * one is not a shared end; a wire's excess is still its whole length, trunk and branch, against the shortest.
 *
 * {@link Result#points()} folds them into one number, lower better, in pixels of wire: Factory Flow's route points
 * (length, bends and crossings at its prices) and, for what those miss, overlap, boxes run through, jogs, wrong sides
 * and doubling back.
 */
public final class RouteMetrics {

    /** A box on the board, in world space. */
    public record Box(int x, int y, int w, int h) {}

    /**
     * A routed wire: its two boxes (indexes into the box list, or -1) and its corners, first at its source, last at its
     * target; and its trunk ({@link WireRouter.Wire#trunk()}: wires from the same box with equal trunks share one, null
     * for none).
     */
    public record Wire(int from, int to, List<int[]> points, Object trunk) {

        public Wire(final int from, final int to, final List<int[]> points) {
            this(from, to, points, null);
        }
    }

    /** The numbers; see the class comment. */
    public record Result(int wires, int crossings, double overlap, int bends45, int bends90, int sharp, double length,
        double excess, double backtrack, int jogs, int wrongSides, double boxHits, int sharedEnds) {

        /** Corners of every kind. */
        public int bends() {
            return bends45 + bends90 + sharp;
        }

        /**
         * One number, lower better, in pixels of wire: Factory Flow's route points (length; a 45-degree bend 35, a
         * right
         * angle 80, a sharp one three right angles; a crossing 400) and what they miss: overlap at four times its
         * length,
         * a box run through at ten, a jog 120, an end on a side facing away 300, doubling back at its length again.
         */
        public double points() {
            return length + 35 * bends45
                + 80 * (bends90 + 3 * sharp)
                + 400 * crossings
                + 4 * overlap
                + 10 * boxHits
                + 120 * jogs
                + 300 * wrongSides
                + backtrack;
        }

        /** One line, for logs and tables. */
        public String line() {
            return String.format(
                Locale.ROOT,
                "points %.0f  crossings %d  overlap %.0f  bends %d/%d/%d  length %.0f  excess %.0f  backtrack %.0f  jogs %d  wrongSides %d  boxHits %.0f  sharedEnds %d",
                points(),
                crossings,
                overlap,
                bends45,
                bends90,
                sharp,
                length,
                excess,
                backtrack,
                jogs,
                wrongSides,
                boxHits,
                sharedEnds);
        }
    }

    /** Shorter than this, a sidestep between parallel runs is a jog. */
    static final double JOG = 30;

    private RouteMetrics() {}

    public static Result measure(final List<Box> boxes, final List<Wire> wires) {
        return measure(boxes, wires, null);
    }

    /** As {@link #measure(List, List)}, also adding to {@code crossed} (when given) every crossing as its two wires. */
    public static Result measure(final List<Box> boxes, final List<Wire> wires, final List<int[]> crossed) {
        int crossings = 0, b45 = 0, b90 = 0, sharp = 0, shared = 0, jogs = 0, wrong = 0;
        double overlap = 0, length = 0, excess = 0, backtrack = 0, hits = 0;
        final int n = wires.size();
        // What each wire draws itself: from where it leaves its trunk (all of it for a wire on its own), how far along
        // the trunk that is, and the way the trunk runs into it.
        final List<List<int[]>> drawn = new java.util.ArrayList<>(n);
        final double[] along = new double[n];
        final double[][] into = new double[n][];
        branches(wires, drawn, along, into);
        // Each wire's bounding box, so pairs far apart are skipped at once.
        final int[][] bb = new int[n][];
        for (int i = 0; i < n; i++) bb[i] = bounds(drawn.get(i));
        for (int i = 0; i < n; i++) {
            final Wire w = wires.get(i);
            final List<int[]> whole = w.points(), p = drawn.get(i);
            if (whole.size() < 2 || p.size() < 2) continue;
            final int[] s = whole.get(0), t = whole.get(whole.size() - 1);
            final int gx = Integer.signum(t[0] - s[0]), gy = Integer.signum(t[1] - s[1]);
            double own = 0;
            for (int k = 0; k + 1 < p.size(); k++) {
                final int[] a = p.get(k), b = p.get(k + 1);
                final int dx = b[0] - a[0], dy = b[1] - a[1];
                final double len = Math.hypot(dx, dy);
                own += len;
                // Away from the target on either axis; with the ends level on an axis, any run along it and back.
                backtrack += away(dx, gx) + away(dy, gy);
                for (int bi = 0; bi < boxes.size(); bi++) hits += inside(boxes.get(bi), a, b);
            }
            length += own;
            // Bends by angle, skipping runs too short to have a direction; a branch's turn off its trunk is one.
            double px = into[i] == null ? 0 : into[i][0], py = into[i] == null ? 0 : into[i][1];
            boolean have = into[i] != null;
            for (int k = 0; k + 1 < p.size(); k++) {
                final double dx = p.get(k + 1)[0] - p.get(k)[0], dy = p.get(k + 1)[1] - p.get(k)[1];
                final double len = Math.hypot(dx, dy);
                if (len < 0.5) continue;
                final double ux = dx / len, uy = dy / len;
                if (have) {
                    final double cos = ux * px + uy * py;
                    if (cos <= -0.5) sharp++;
                    else if (cos <= 0.5) b90++;
                    else if (cos < 0.99) b45++;
                }
                px = ux;
                py = uy;
                have = true;
            }
            // Jogs: a short run between two runs going the same way.
            for (int k = 1; k + 2 < p.size(); k++) {
                final int[] a = p.get(k - 1), b = p.get(k), c = p.get(k + 1), d = p.get(k + 2);
                final double mid = Math.hypot(c[0] - b[0], c[1] - b[1]);
                if (mid >= JOG || mid == 0) continue;
                final long dot = (long) (b[0] - a[0]) * (d[0] - c[0]) + (long) (b[1] - a[1]) * (d[1] - c[1]);
                final long cross = (long) (b[0] - a[0]) * (d[1] - c[1]) - (long) (b[1] - a[1]) * (d[0] - c[0]);
                if (dot > 0 && cross == 0) jogs++;
            }
            // Excess (the whole wire, trunk and branch) and wrong sides, against the two boxes; a branch's source end
            // is its trunk's.
            final Box from = w.from() >= 0 && w.from() < boxes.size() ? boxes.get(w.from()) : null;
            final Box to = w.to() >= 0 && w.to() < boxes.size() ? boxes.get(w.to()) : null;
            if (from != null && to != null && from != to) {
                excess += Math.max(0, along[i] + own - shortest(from, to));
                if (along[i] == 0 && facesAway(from, s, p.get(1), to)) wrong++;
                if (facesAway(to, t, p.get(p.size() - 2), from)) wrong++;
            }
            for (int j = i + 1; j < n; j++) {
                if (bb[i][2] < bb[j][0] || bb[j][2] < bb[i][0] || bb[i][3] < bb[j][1] || bb[j][3] < bb[i][1]) {
                    continue;
                }
                final List<int[]> q = drawn.get(j);
                for (int k = 0; k + 1 < p.size(); k++) for (int m = 0; m + 1 < q.size(); m++) {
                    final int[] a = p.get(k), b = p.get(k + 1), c = q.get(m), d = q.get(m + 1);
                    if (crosses(a, b, c, d)) {
                        crossings++;
                        if (crossed != null) crossed.add(new int[] { i, j });
                    } else overlap += collinearOverlap(a, b, c, d);
                }
                if (sameEnd(w, wires.get(j), along[i] > 0, along[j] > 0)) shared++;
            }
        }
        return new Result(n, crossings, overlap, b45, b90, sharp, length, excess, backtrack, jogs, wrong, hits, shared);
    }

    /**
     * Each wire's own stretch: past where it leaves the trunk it shares with wires before it in the list (its whole
     * path when it shares none), how far along the trunk that is, and the trunk's heading there (null for none).
     */
    private static void branches(final List<Wire> wires, final List<List<int[]>> drawn, final double[] along,
        final double[][] into) {
        final java.util.Map<List<Object>, List<Integer>> trunks = new java.util.LinkedHashMap<>();
        for (int i = 0; i < wires.size(); i++) {
            final Wire w = wires.get(i);
            drawn.add(w.points());
            if (w.trunk() != null && w.points()
                .size() >= 2) trunks.computeIfAbsent(List.of(w.trunk(), w.from()), k -> new java.util.ArrayList<>())
                    .add(i);
        }
        for (final List<Integer> members : trunks.values()) {
            if (members.size() < 2) continue;
            final List<List<int[]>> paths = new java.util.ArrayList<>(members.size());
            for (final int i : members) paths.add(
                wires.get(i)
                    .points());
            final WireTrunks.Branch[] tree = WireTrunks.tree(paths);
            for (int k = 0; k < members.size(); k++) {
                final WireTrunks.Branch b = tree[k];
                if (b.parent() < 0) continue;
                final int i = members.get(k);
                along[i] = b.length();
                drawn.set(i, WireTrunks.between(paths.get(k), b.length(), b.at(), 0, null));
                into[i] = WireTrunks.headingBefore(paths.get(k), b.length());
            }
        }
    }

    private static int[] bounds(final List<int[]> p) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (final int[] q : p) {
            x0 = Math.min(x0, q[0]);
            y0 = Math.min(y0, q[1]);
            x1 = Math.max(x1, q[0]);
            y1 = Math.max(y1, q[1]);
        }
        return new int[] { x0, y0, x1, y1 };
    }

    /**
     * The shortest a wire between two boxes can be: straight across the gap when they face each other on an axis, else
     * a 45-degree run and a straight one across the two gaps.
     */
    static double shortest(final Box a, final Box b) {
        final int gx = Math.max(0, Math.max(b.x() - (a.x() + a.w()), a.x() - (b.x() + b.w())));
        final int gy = Math.max(0, Math.max(b.y() - (a.y() + a.h()), a.y() - (b.y() + b.h())));
        return Math.max(gx, gy) + (Math.sqrt(2) - 1) * Math.min(gx, gy);
    }

    /**
     * Whether a wire's end at {@code at} on {@code box}, running first to {@code next}, leaves on a side facing away
     * from the {@code other} box: the box stands wholly behind that side.
     */
    static boolean facesAway(final Box box, final int[] at, final int[] next, final Box other) {
        final int dx = Integer.signum(next[0] - at[0]), dy = Integer.signum(next[1] - at[1]);
        if (dx != 0 && dy != 0) return false;
        if (dx > 0) return other.x() + other.w() <= box.x() + box.w();
        if (dx < 0) return other.x() >= box.x();
        if (dy > 0) return other.y() + other.h() <= box.y() + box.h();
        if (dy < 0) return other.y() >= box.y();
        return false;
    }

    /**
     * How much of a run of {@code d} goes against the way to the target ({@code goal}, -1, 0 or 1). With the two ends
     * level on the axis (goal 0) half of any run counts: going out and coming back is a detour, a run one way only can
     * not happen.
     */
    private static double away(final int d, final int goal) {
        if (goal == 0) return Math.abs(d) / 2.0;
        return d * goal < 0 ? Math.abs(d) : 0;
    }

    /**
     * Whether two wires end on the same point: two wires drawn into one. A branch ({@code aBranch}, {@code bBranch})
     * leaves from its trunk, not its box, so its source end is not counted.
     */
    private static boolean sameEnd(final Wire a, final Wire b, final boolean aBranch, final boolean bBranch) {
        final List<int[]> p = a.points(), q = b.points();
        final int[] a0 = p.get(0), a1 = p.get(p.size() - 1), b0 = q.get(0), b1 = q.get(q.size() - 1);
        return !aBranch && !bBranch && same(a0, b0) && a.from() == b.from() || same(a1, b1) && a.to() == b.to()
            || !aBranch && same(a0, b1) && a.from() == b.to()
            || !bBranch && same(a1, b0) && a.to() == b.from();
    }

    private static boolean same(final int[] a, final int[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }

    /** Proper crossing of two segments: each one's ends strictly either side of the other. */
    static boolean crosses(final int[] a, final int[] b, final int[] c, final int[] d) {
        final long d1 = orient(c, d, a), d2 = orient(c, d, b), d3 = orient(a, b, c), d4 = orient(a, b, d);
        return (d1 > 0 && d2 < 0 || d1 < 0 && d2 > 0) && (d3 > 0 && d4 < 0 || d3 < 0 && d4 > 0);
    }

    private static long orient(final int[] a, final int[] b, final int[] c) {
        return (long) (b[0] - a[0]) * (c[1] - a[1]) - (long) (b[1] - a[1]) * (c[0] - a[0]);
    }

    /** Length two segments share when they lie on one line. */
    static double collinearOverlap(final int[] a, final int[] b, final int[] c, final int[] d) {
        if (orient(a, b, c) != 0 || orient(a, b, d) != 0) return 0;
        final double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
        if (len == 0) return 0;
        // Positions of c and d along a->b.
        final double ux = (b[0] - a[0]) / len, uy = (b[1] - a[1]) / len;
        final double tc = (c[0] - a[0]) * ux + (c[1] - a[1]) * uy, td = (d[0] - a[0]) * ux + (d[1] - a[1]) * uy;
        final double lo = Math.max(0, Math.min(tc, td)), hi = Math.min(len, Math.max(tc, td));
        return Math.max(0, hi - lo);
    }

    /** Length of a segment strictly inside a box (its edges are fine: that is where wires meet it). */
    static double inside(final Box r, final int[] a, final int[] b) {
        // Clip to the open box, shrunk a unit so a wire along an edge or ending on it does not count.
        final double x0 = r.x() + 1, y0 = r.y() + 1, x1 = r.x() + r.w() - 1, y1 = r.y() + r.h() - 1;
        if (x1 <= x0 || y1 <= y0) return 0;
        double t0 = 0, t1 = 1;
        final double dx = b[0] - a[0], dy = b[1] - a[1];
        final double[] p = { -dx, dx, -dy, dy }, q = { a[0] - x0, x1 - a[0], a[1] - y0, y1 - a[1] };
        for (int k = 0; k < 4; k++) {
            if (p[k] == 0) {
                if (q[k] < 0) return 0;
                continue;
            }
            final double t = q[k] / p[k];
            if (p[k] < 0) t0 = Math.max(t0, t);
            else t1 = Math.min(t1, t);
            if (t0 > t1) return 0;
        }
        return (t1 - t0) * Math.hypot(dx, dy);
    }
}
