package com.gtnhplanner.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * How the routed wires of one trunk (wires leaving the same port with the same resource, see
 * {@link WireRouter.Wire#trunk()}) lie on each other: each wire runs from the port along the trunk and branches off
 * toward its own box. Worked out from the routes' corners alone, so it holds for any routes, those of a box being
 * dragged too: two wires share exactly the stretch they run together from where they start.
 */
public final class WireTrunks {

    private WireTrunks() {}

    /**
     * Where a wire leaves the trunk: the wire it runs along until then ({@code parent}, an index among the trunk's
     * wires, -1 for none), how far from the start that is in px ({@code length}, 0 for none) and the point ({@code at},
     * its start for none).
     */
    public record Branch(int parent, double length, int[] at) {}

    /**
     * How far two paths that start at the same point run together before they part, in px along them, and where they
     * part: null when they start apart or part at once. Paths are corners, as the router gives them.
     */
    public static Branch parting(final List<int[]> a, final List<int[]> b) {
        if (a.size() < 2 || b.size() < 2 || !same(a.getFirst(), b.getFirst())) return null;
        int[] at = a.getFirst();
        int i = 1, j = 1;
        double length = 0;
        while (i < a.size() && j < b.size()) {
            final int[] p = a.get(i), q = b.get(j);
            if (same(p, at)) {
                i++;
                continue;
            }
            if (same(q, at)) {
                j++;
                continue;
            }
            final long px = p[0] - at[0], py = p[1] - at[1], qx = q[0] - at[0], qy = q[1] - at[1];
            // Parting here: the two head different ways.
            if (px * qy - py * qx != 0 || px * qx + py * qy <= 0) break;
            final long lp = px * px + py * py, lq = qx * qx + qy * qy;
            final int[] next = lp <= lq ? p : q;
            length += Math.sqrt(Math.min(lp, lq));
            at = next;
            if (lp <= lq) i++;
            if (lq <= lp) j++;
        }
        return length > 0 ? new Branch(-1, length, at) : null;
    }

    /**
     * Each path's branch, in order: the earlier path it runs along longest from the start, how far and where it leaves
     * it (the first such path on a tie, so a wire leaving at a fork already counted hangs off the wire that made it).
     * All the paths start at the same port; a path that shares nothing with those before it has no parent.
     */
    public static Branch[] tree(final List<List<int[]>> paths) {
        final Branch[] out = new Branch[paths.size()];
        for (int j = 0; j < paths.size(); j++) {
            Branch best = null;
            int parent = -1;
            for (int i = 0; i < j; i++) {
                final Branch b = parting(paths.get(i), paths.get(j));
                if (b != null && (best == null || b.length() > best.length() + 1e-6)) {
                    best = b;
                    parent = i;
                }
            }
            final List<int[]> own = paths.get(j);
            out[j] = best == null ? new Branch(-1, 0, own.isEmpty() ? new int[2] : own.getFirst())
                : new Branch(parent, best.length(), best.at());
        }
        return out;
    }

    /**
     * The stretch of a path from {@code from} px along it, starting at {@code fromAt}, to {@code to} px, ending at
     * {@code toAt} (the path's end for none): its corners between, those ends included.
     */
    public static List<int[]> between(final List<int[]> path, final double from, final int[] fromAt, final double to,
        final int[] toAt) {
        final List<int[]> out = new ArrayList<>();
        out.add(fromAt);
        double at = 0;
        for (int k = 1; k < path.size(); k++) {
            final int[] a = path.get(k - 1), b = path.get(k);
            at += Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (at <= from + 1e-6) continue;
            if (toAt != null && at >= to - 1e-6) break;
            if (!same(b, out.getLast())) out.add(b);
        }
        if (toAt != null && !same(toAt, out.getLast())) out.add(toAt);
        return out;
    }

    /** A path's length. */
    public static double length(final List<int[]> path) {
        double total = 0;
        for (int k = 1; k < path.size(); k++)
            total += Math.hypot(path.get(k)[0] - path.get(k - 1)[0], path.get(k)[1] - path.get(k - 1)[1]);
        return total;
    }

    /**
     * The way a path runs just before {@code length} px along it, as a unit vector; null at its start or for a path
     * with no length there.
     */
    public static double[] headingBefore(final List<int[]> path, final double length) {
        if (length <= 1e-6) return null;
        double at = 0;
        for (int k = 1; k < path.size(); k++) {
            final int[] a = path.get(k - 1), b = path.get(k);
            final double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (len > 0 && at + len >= length - 1e-6) return new double[] { (b[0] - a[0]) / len, (b[1] - a[1]) / len };
            at += len;
        }
        return null;
    }

    private static boolean same(final int[] a, final int[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }
}
