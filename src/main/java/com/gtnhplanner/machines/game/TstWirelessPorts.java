package com.gtnhplanner.machines.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnhplanner.GtnhPlanner;
import com.gtnhplanner.api.RecipePropertyAPI;
import com.gtnhplanner.data.MachineConfig;
import com.gtnhplanner.data.flowchart.Node;
import com.gtnhplanner.data.flowchart.Port;
import com.gtnhplanner.machines.eto.TstMachinesWireless;
import com.gtnhplanner.machines.web.Web;

/**
 * The Miracle Door's White Dwarf Mold (Ingot): with one in its input bus the Door casts each molten output it has an
 * ingot for (TST_MiracleDoor.turnToIngotRecipe :493-516, the ingot from StellarForgeRecipePool.MoltenToIngot): litres
 * / 144 ingots, the rest left molten. The card keeps the recipe's fluid port at the share left molten and gains the
 * ingots as a port of its own.
 */
public final class TstWirelessPorts {

    private TstWirelessPorts() {}

    /** The ingot ports and scaled fluid outputs each card was last given, so a second pass replaces them. */
    private record Cast(List<Port<?>> ingots, List<Integer> scaled) {}

    private static final Map<Node, Cast> CAST = new WeakHashMap<>();

    /** Casts the card's molten outputs when its Miracle Door has the mold; safe to run again. */
    public static void afterRefresh(final Node node) {
        final MachineConfig cfg = node.machineConfig;
        final Cast earlier = CAST.remove(node);
        if (earlier != null) {
            node.outputs.removeAll(earlier.ingots());
            if (cfg != null) for (final Integer i : earlier.scaled()) cfg.outputProductivity.remove(i);
        }
        if (node.isPower() || cfg == null || WebEffect.result(node, cfg) == null) return;
        final Web.Recipe recipe = WebEffect.recipe(node);
        if (recipe == null || !TstMachinesWireless.ingotMold(recipe, WebCards.node(node, cfg, recipe))) return;
        final List<Port<?>> ingots = new ArrayList<>();
        final List<Integer> scaled = new ArrayList<>();
        final int outputs = node.outputs.size();
        for (int i = 0; i < outputs; i++) {
            final Port<?> port = node.outputs.get(i);
            if (port.fromModel || !(port.getValue() instanceof final FluidStack fluid)) continue;
            final ItemStack ingot = ingotOf(fluid.getFluid());
            final int[] cast = ingot == null ? null : TstMachinesWireless.ingots(fluid.amount);
            if (cast == null) continue;
            final ItemStack stack = ingot.copy();
            stack.stackSize = cast[0];
            final Port<ItemStack> out = new Port<>(RecipePropertyAPI.ITEM, stack, port.getChance());
            out.fromModel = true;
            ingots.add(out);
            cfg.outputProductivity.put(i, (float) cast[1] / fluid.amount);
            scaled.add(i);
        }
        node.outputs.addAll(ingots);
        if (!ingots.isEmpty()) CAST.put(node, new Cast(ingots, scaled));
    }

    @Nullable
    private static Map<?, ?> moltenToIngot;

    /** StellarForgeRecipePool.MoltenToIngot's ingot for a fluid (filled as TST loads its recipes); null for none. */
    @Nullable
    private static ItemStack ingotOf(final Fluid fluid) {
        if (moltenToIngot == null) {
            try {
                moltenToIngot = (Map<?, ?>) Class
                    .forName("com.Nxer.TwistSpaceTechnology.recipe.machineRecipe.expanded.StellarForgeRecipePool")
                    .getField("MoltenToIngot")
                    .get(null);
            } catch (final ReflectiveOperationException | LinkageError | ClassCastException e) {
                GtnhPlanner.LOG.warn("No Miracle Door ingot moulds: {}", e.toString());
                moltenToIngot = Map.of();
            }
        }
        return moltenToIngot.get(fluid) instanceof final ItemStack ingot ? ingot : null;
    }
}
