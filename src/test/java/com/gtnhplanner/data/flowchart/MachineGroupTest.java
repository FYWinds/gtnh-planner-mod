package com.gtnhplanner.data.flowchart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnhplanner.harness.GtnhFlowLoader;

/** A recipe split off its shared machine (docs/design/ff-shared-machine-spec.md, "Splitting"). */
class MachineGroupTest {

    private static Graph chart() {
        return GtnhFlowLoader.load("light_fuel")
            .graph();
    }

    private static MachineGroup share(final Graph g, final List<Node> recipes) {
        final MachineGroup shared = new MachineGroup();
        for (final Node n : recipes) {
            shared.addSection(n.id);
            n.x = 100;
            n.y = 200;
        }
        g.groups.put(shared.getId(), shared);
        return shared;
    }

    @Test
    void aRecipeSplitOffLeavesTheOthersOnTheMachineWithItsCount() {
        final Graph g = chart();
        final List<Node> nodes = List.copyOf(g.getNodes());
        final Node a = nodes.get(0), b = nodes.get(1), c = nodes.get(2);
        final MachineGroup shared = share(g, List.of(a, b, c));
        PinRule.set(shared, PinRule.EXACT, 4);
        final List<Edge> wires = List.copyOf(g.getEdges());

        assertTrue(MachineGroup.split(g, a.id, 100, 640), "the host can leave too");
        assertEquals(List.of(b.id, c.id), shared.getSections(), "the next recipe is the host");
        assertSame(shared, g.groups.get(shared.getId()));
        assertEquals(PinRule.EXACT, PinRule.of(shared));
        assertEquals(4, shared.getMachineCapacity());
        assertEquals(List.of(100, 640), List.of(a.x, a.y));
        assertEquals(List.of(100, 200), List.of(b.x, b.y), "the machine's card stays where it is");
        assertEquals(PinRule.ANY, PinRule.of(a), "the plan decides the count of the card that left");
        assertEquals(wires, List.copyOf(g.getEdges()), "every wire is where it was");
    }

    @Test
    void theLastRecipeOnTheMachineIsAnOrdinaryCardWithTheMachinesCount() {
        final Graph g = chart();
        final List<Node> nodes = List.copyOf(g.getNodes());
        final Node a = nodes.get(0), b = nodes.get(1);
        final MachineGroup shared = share(g, List.of(a, b));
        PinRule.set(shared, PinRule.UP_TO, 3);

        assertTrue(MachineGroup.split(g, b.id, 100, 640));
        assertNull(g.groups.get(shared.getId()), "one recipe is no shared machine");
        assertEquals(PinRule.UP_TO, PinRule.of(a));
        assertEquals(3, a.machineConfig.getMachineCount());
        assertEquals(PinRule.ANY, PinRule.of(b));
    }

    @Test
    void aCardOfItsOwnIsLeftAlone() {
        final Graph g = chart();
        final List<Node> nodes = List.copyOf(g.getNodes());
        share(g, List.of(nodes.get(0), nodes.get(1)));
        final Node alone = nodes.get(2);
        final int x = alone.x, y = alone.y;
        assertFalse(MachineGroup.split(g, alone.id, x + 500, y + 500));
        assertEquals(List.of(x, y), List.of(alone.x, alone.y));
    }
}
