package com.gtnhplanner.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How routed wires lie on each other where they share a trunk. Wires leaving one port with one resource
 * ({@link WireRouter.Wire#trunk()}) run from the port along the trunk and each branches off toward its own box; wires
 * coming into one port with one resource ({@link WireRouter.Wire#merge()}) each leave their own box, join the trunk
 * and run along it into the port. A wire on both runs off the one, on its own, then onto the other. Worked out from the
 * routes' corners alone, so it holds for any routes, those of a box being dragged too: two wires share exactly the
 * stretch they run together from where they both start, or to where they both end.
 */
public final class WireTrunks {

    /** Lengths along a path closer than this, in px, are one place. */
    private static final double EPS = 1e-3;

    private WireTrunks() {}

    /**
     * Where a wire leaves the trunk it starts on: the wire it runs along until then ({@code parent}, an index among the
     * paths, -1 for none), how far from the start that is in px ({@code length}, 0 for none) and the point ({@code at},
     * its start for none). For the trunk a wire ends on ({@link #treeIn}) the same from its end: where it joins the
     * wire it runs along from then on, that far before its end (its end for none).
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
     * As {@link #tree} from the other end, for paths that all end at the same port: each path's join, the earlier path
     * it runs along longest to the end, how far before the end it joins it and where.
     */
    public static Branch[] treeIn(final List<List<int[]>> paths) {
        final List<List<int[]>> reversed = new ArrayList<>(paths.size());
        for (final List<int[]> p : paths) reversed.add(p.reversed());
        return tree(reversed);
    }

    /**
     * A wire's place on the trunks it shares: {@code head}, where it leaves the one it starts on, and {@code tail},
     * where it joins the one it ends on ({@link Branch}; parents index the whole list). Between the two is its own
     * stretch. Where both would hold the same stretch (a wire running off one trunk straight onto the other), the head
     * has it, so no stretch is counted twice.
     */
    public record Share(Branch head, Branch tail) {}

    /**
     * Each path's share, in order: paths with equal non-null {@code out} keys start on one trunk (they leave one port,
     * {@link #tree}), paths with equal non-null {@code in} keys end on one (they come into one port, {@link #treeIn}).
     * Within each, a path hangs off an earlier one, so every parent comes before its child.
     */
    public static Share[] shares(final List<List<int[]>> paths, final List<?> out, final List<?> in) {
        final int n = paths.size();
        final Branch[] head = new Branch[n], tail = new Branch[n];
        for (final List<Integer> group : groups(out)) {
            final Branch[] tree = tree(pick(paths, group));
            for (int k = 0; k < tree.length; k++) head[group.get(k)] = among(tree[k], group);
        }
        for (final List<Integer> group : groups(in)) {
            final Branch[] tree = treeIn(pick(paths, group));
            for (int k = 0; k < tree.length; k++) tail[group.get(k)] = among(tree[k], group);
        }
        final Share[] shares = new Share[n];
        for (int i = 0; i < n; i++) {
            final List<int[]> p = paths.get(i);
            final int[] start = p.isEmpty() ? new int[2] : p.getFirst(), end = p.isEmpty() ? new int[2] : p.getLast();
            final Branch h = head[i] != null ? head[i] : new Branch(-1, 0, start);
            Branch t = tail[i] != null ? tail[i] : new Branch(-1, 0, end);
            final double room = length(p) - h.length();
            if (t.parent() >= 0 && t.length() > room + EPS)
                t = room > EPS ? new Branch(t.parent(), room, h.at()) : new Branch(-1, 0, end);
            shares[i] = new Share(h, t);
        }
        return shares;
    }

    /**
     * A stretch of wire as drawn: a piece of path {@code wire}'s own stretch ({@code path}, its corners) and the wires
     * running along it ({@code along}, indexes: {@code wire} first, the rest in order).
     */
    public record Stretch(int wire, List<int[]> path, int[] along) {}

    /**
     * The wires as drawn, each stretch once: every path's own stretch (past where it leaves the trunk it starts on, up
     * to where it joins the one it ends on, {@link #shares}), cut wherever a wire running along it leaves or joins it,
     * each piece with all that runs along it; in order of the paths, and along each. A path that shares nothing is one
     * stretch, whole.
     */
    public static List<Stretch> stretches(final List<List<int[]>> paths, final Share[] shares) {
        final int n = paths.size();
        final double[] length = new double[n];
        // What runs along each path and where, {from, to, wire} in px along it: each path hands what runs on its head
        // and its tail to the paths it shares them with (which come before it), and keeps what is on its own stretch.
        final List<List<double[]>> on = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            length[i] = length(paths.get(i));
            final List<double[]> self = new ArrayList<>();
            self.add(new double[] { 0, length[i], i });
            on.add(self);
        }
        for (int i = n - 1; i >= 0; i--) {
            final Branch head = shares[i].head(), tail = shares[i].tail();
            final double a = head.length(), b = length[i] - tail.length();
            final List<double[]> kept = new ArrayList<>();
            for (final double[] r : on.get(i)) {
                if (head.parent() >= 0 && r[0] < a - EPS) on.get(head.parent())
                    .add(new double[] { r[0], Math.min(r[1], a), r[2] });
                if (tail.parent() >= 0 && r[1] > b + EPS) {
                    // Measured from the end, on the wire it runs into the port with.
                    final double shift = length[tail.parent()] - length[i];
                    on.get(tail.parent())
                        .add(new double[] { Math.max(r[0], b) + shift, r[1] + shift, r[2] });
                }
                final double from = Math.max(r[0], a), to = Math.min(r[1], b);
                if (to > from + EPS) kept.add(new double[] { from, to, r[2] });
            }
            on.set(i, kept);
        }
        final List<Stretch> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final List<double[]> runs = on.get(i);
            if (runs.isEmpty()) continue;
            final double[] ends = new double[2 * runs.size()];
            for (int k = 0; k < runs.size(); k++) {
                ends[2 * k] = runs.get(k)[0];
                ends[2 * k + 1] = runs.get(k)[1];
            }
            Arrays.sort(ends);
            final List<int[]> path = paths.get(i);
            double from = ends[0];
            for (int k = 1; k < ends.length; k++) {
                final double to = ends[k];
                if (to <= from + EPS) continue;
                final Set<Integer> along = new java.util.TreeSet<>();
                for (final double[] r : runs) if (r[0] <= from + EPS && r[1] >= to - EPS) along.add((int) r[2]);
                along.remove(i);
                final int[] wires = new int[along.size() + 1];
                wires[0] = i;
                int w = 1;
                for (final int j : along) wires[w++] = j;
                out.add(new Stretch(i, between(path, from, pointAt(path, from), to, pointAt(path, to)), wires));
                from = to;
            }
        }
        return out;
    }

    /** Where wires leave the trunk they start on or join the one they end on ({@link #shares}), each point once. */
    public static List<int[]> junctions(final List<List<int[]>> paths, final Share[] shares) {
        final List<int[]> out = new ArrayList<>();
        final Set<Long> seen = new HashSet<>();
        for (int i = 0; i < paths.size(); i++)
            for (final Branch b : new Branch[] { shares[i].head(), shares[i].tail() })
                if (b.parent() >= 0 && seen.add((long) b.at()[0] << 32 | b.at()[1] & 0xFFFFFFFFL)) out.add(b.at());
        return out;
    }

    /**
     * Each path's bundle: paths tied together by trunks (equal non-null {@code out} keys, or {@code in} keys, two or
     * more of them, and so on through every path tied to those) are one, numbered from 0 in order of their first path;
     * -1 for a path on no trunk.
     */
    public static int[] bundles(final List<?> out, final List<?> in) {
        final int n = out.size();
        final int[] root = new int[n];
        for (int i = 0; i < n; i++) root[i] = i;
        for (final List<Integer> group : groups(out)) for (final int i : group) tie(root, group.getFirst(), i);
        for (final List<Integer> group : groups(in)) for (final int i : group) tie(root, group.getFirst(), i);
        final int[] size = new int[n], number = new int[n], named = new int[n];
        for (int i = 0; i < n; i++) size[top(root, i)]++;
        Arrays.fill(named, -1);
        int next = 0;
        for (int i = 0; i < n; i++) {
            final int r = top(root, i);
            if (size[r] >= 2 && named[r] < 0) named[r] = next++;
            number[i] = size[r] >= 2 ? named[r] : -1;
        }
        return number;
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

    /**
     * The way a path runs just after {@code length} px along it, as a unit vector; null at its end or for a path with
     * no length there.
     */
    public static double[] headingAfter(final List<int[]> path, final double length) {
        double at = 0;
        for (int k = 1; k < path.size(); k++) {
            final int[] a = path.get(k - 1), b = path.get(k);
            final double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (len > 0 && at + len > length + 1e-6) return new double[] { (b[0] - a[0]) / len, (b[1] - a[1]) / len };
            at += len;
        }
        return null;
    }

    /** The point {@code length} px along a path, to the nearest whole px; its end past that. */
    public static int[] pointAt(final List<int[]> path, final double length) {
        double at = 0;
        for (int k = 1; k < path.size(); k++) {
            final int[] a = path.get(k - 1), b = path.get(k);
            final double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (len > 0 && at + len >= length - 1e-6) {
                final double t = Math.max(0, (length - at) / len);
                return new int[] { (int) Math.round(a[0] + t * (b[0] - a[0])),
                    (int) Math.round(a[1] + t * (b[1] - a[1])) };
            }
            at += len;
        }
        return path.isEmpty() ? new int[2] : path.getLast();
    }

    /** The indexes with each equal non-null key, two or more of them, in order, groups in order of their first. */
    private static List<List<Integer>> groups(final List<?> keys) {
        final Map<Object, List<Integer>> by = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++)
            if (keys.get(i) != null) by.computeIfAbsent(keys.get(i), k -> new ArrayList<>())
                .add(i);
        final List<List<Integer>> out = new ArrayList<>();
        for (final List<Integer> group : by.values()) if (group.size() >= 2) out.add(group);
        return out;
    }

    private static List<List<int[]>> pick(final List<List<int[]>> paths, final List<Integer> group) {
        final List<List<int[]>> out = new ArrayList<>(group.size());
        for (final int i : group) out.add(paths.get(i));
        return out;
    }

    /** A branch within a group, its parent made an index into the whole list. */
    private static Branch among(final Branch b, final List<Integer> group) {
        return b.parent() < 0 ? b : new Branch(group.get(b.parent()), b.length(), b.at());
    }

    private static int top(final int[] root, int i) {
        while (root[i] != i) {
            root[i] = root[root[i]];
            i = root[i];
        }
        return i;
    }

    private static void tie(final int[] root, final int a, final int b) {
        final int ra = top(root, a), rb = top(root, b);
        if (ra != rb) root[Math.max(ra, rb)] = Math.min(ra, rb);
    }

    private static boolean same(final int[] a, final int[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }
}
