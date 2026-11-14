package com.gtnhplanner.power;

import javax.annotation.Nullable;

import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.power.sources.NrEssentiaSmeltery;

/**
 * The non-recipe cards that take what is wired to them: the custom rate card (any resource, at a rate you set), the
 * fluid tank (a cell and its fluid) and the Large Essentia Smeltery (an item or fluid to smelt). Empty, they show two
 * sockets; a port dropped on one, or a socket dragged to a port, makes it take that resource on that side; with
 * nothing wired to it, it lets go again.
 */
public final class Holders {

    private Holders() {}

    public static boolean is(@Nullable final Node node) {
        return CustomRate.is(node) || FluidTank.is(node) || NrEssentiaSmeltery.is(node);
    }

    /** Whether it holds anything (an empty one shows its sockets). */
    public static boolean holding(final Node node) {
        return CustomRate.is(node) ? CustomRate.resource(node) != null
            : FluidTank.is(node) ? FluidTank.cell(node) != null : NrEssentiaSmeltery.input(node) != null;
    }

    /**
     * Whether it would take a resource on a side ({@code supply}: it gives that resource to a port wanting it, else it
     * takes it from a port making it).
     */
    public static boolean canTake(final Node node, @Nullable final String key, final boolean supply) {
        if (key == null || key.isEmpty()) return false;
        if (CustomRate.is(node)) return true;
        if (NrEssentiaSmeltery.is(node)) return NrEssentiaSmeltery.canTake(key, supply);
        return FluidTank.is(node) && FluidTank.take(key, supply) != null;
    }

    /** Takes a resource on a side; false, changing nothing, when it cannot. The caller rebuilds the ports. */
    public static boolean take(final Node node, final String key, final boolean supply) {
        if (CustomRate.is(node)) {
            CustomRate.hold(node, key, supply);
            return true;
        }
        if (NrEssentiaSmeltery.is(node)) return NrEssentiaSmeltery.hold(node, key, supply);
        return FluidTank.is(node) && FluidTank.hold(node, key, supply);
    }

    /** Lets go of what it holds, keeping its dial or mode; the caller rebuilds the ports. */
    public static void release(final Node node) {
        if (CustomRate.is(node)) CustomRate.release(node);
        else if (FluidTank.is(node)) FluidTank.release(node);
        else if (NrEssentiaSmeltery.is(node)) NrEssentiaSmeltery.release(node);
    }
}
