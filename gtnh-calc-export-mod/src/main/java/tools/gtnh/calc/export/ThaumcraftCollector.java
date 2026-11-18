// SPDX-License-Identifier: LGPL-3.0-only
// Adapted compatibility behavior: NESQL Exporter contributors, b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57.
// See META-INF/licenses/NOTICE.txt and NESQL-LGPL-3.0.md.
package tools.gtnh.calc.export;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import tools.gtnh.calc.export.ExportData.*;
import tools.gtnh.calc.export.LegacyIds.*;
import tools.gtnh.calc.export.LegacyIds.Stack;
import static tools.gtnh.calc.export.GameReflection.*;

final class ThaumcraftCollector {
    static final int[] COMPONENT_ORDER = {2,22,10,14,0,24,4,20,1,23,3,21,5,19,15,9,7,17,11,13,6,18,8,16,25,26,27,28,29,30,31,32};
    final GameExtraction game;
    ThaumcraftCollector(GameExtraction game) { this.game = game; }
    void schedule() throws Exception {
        Class<?> config = type("thaumcraft.common.config.ConfigBlocks");
        Method shaped = game.raw.method("thaumcraft", "Magical Crafting (Shaped)", false, 3,5,0,0,1,1,0,0);
        Method shapeless = game.raw.method("thaumcraft", "Magical Crafting (Shapeless)", true, 3,5,0,0,1,1,0,0);
        Method crucible = game.raw.method("thaumcraft", "Crucible", true, 3,3,0,0,1,1,0,0);
        Method infusion = game.raw.method("thaumcraft", "Arcane Infusion", true, 5,7,0,0,1,1,0,0);
        game.associate(shaped, new ItemStack((Block) field(config, "blockTable"), 1, 15));
        game.associate(shapeless, new ItemStack((Block) field(config, "blockTable"), 1, 15));
        game.associate(crucible, new ItemStack((Block) field(config, "blockMetalDevice"), 1, 0));
        game.associate(infusion, new ItemStack((Block) field(config, "blockStoneDevice"), 1, 2));
        Method aspects = game.raw.method("thaumcraft", "Item aspects", true, 1,1,0,0,5,2,0,0);
        // Find the actual Alchemical Furnace item rather than depending on a metadata constant.
        ItemStack furnace = codechicken.nei.ItemList.items.stream().filter(GameExtraction::valid)
            .filter(s -> "Alchemical Furnace".equals(MinecraftText.plain(s.getDisplayName()))).findFirst().orElseThrow();
        game.associate(aspects, furnace);
        for (Object aspect : ((Map<?, ?>) field(type("thaumcraft.api.aspects.Aspect"), "aspects")).values()) icon(aspect, 1);
        CraftingCollector crafting = new CraftingCollector(game);
        for (Object recipe : list(call(type("thaumcraft.api.ThaumcraftApi"), "getCraftingRecipes"))) game.task("Thaumcraft " + recipe.getClass().getName(), () -> {
            String kind = recipe.getClass().getName(); List<Slot> in = new ArrayList<>(); Method method; Object output;
            if (isKind(recipe, "thaumcraft.api.crafting.ShapedArcaneRecipe") || isKind(recipe, "thaumcraft.api.crafting.ShapelessArcaneRecipe")) {
                boolean isShaped = isKind(recipe, "thaumcraft.api.crafting.ShapedArcaneRecipe");
                method = isShaped ? shaped : shapeless; in = crafting.inputs(call(recipe, "getInput"), 1);
                appendAspects(in, field(recipe, "aspects"), Math.max(next(in), 9)); output = call(recipe, "getRecipeOutput");
            } else if (isKind(recipe, "thaumcraft.api.crafting.CrucibleRecipe")) {
                method = crucible; crafting.ingredient(in, 0, field(recipe, "catalyst"), 1);
                Object list = field(recipe, "aspects"); int size = ((Number) call(list, "size")).intValue();
                appendAspects(in, list, Math.max(1, 9-size)); output = call(recipe, "getRecipeOutput");
            } else if (isKind(recipe, "thaumcraft.api.crafting.InfusionRecipe")) {
                method = infusion; ItemStack central = (ItemStack) call(recipe, "getRecipeInput");
                output = call(recipe, "getRecipeOutput"); if (!(output instanceof ItemStack)) output = central;
                ItemStack[] grid = new ItemStack[32]; grid[12] = central;
                List<?> components = list(call(recipe, "getComponents"));
                if (components.size() > 31) throw new UnsupportedOperationException("Infusion exceeds reference layout");
                for (int i = 0; i < components.size(); i++) grid[COMPONENT_ORDER[i]] = (ItemStack) components.get(i);
                for (int i = 0; i < grid.length; i++) if (grid[i] != null) in.add(new Slot(i, game.stacks(List.of(grid[i]), null), 1));
                appendAspects(in, call(recipe, "getAspects"), Math.max(next(in), 25));
            } else throw new UnsupportedOperationException("Reference does not support " + kind);
            crafting.add("thaumcraft", method.name, method, in, (ItemStack) output);
        });
    }
    private static int next(List<Slot> in) { return in.stream().mapToInt(Slot::slot).max().orElse(-1) + 1; }
    private void appendAspects(List<Slot> in, Object list, int slot) throws Exception {
        for (Object aspect : list(call(list, "getAspectsSorted"))) {
            int amount = ((Number) call(list, "getAmount", aspect)).intValue();
            in.add(new Slot(slot++, List.of(new Stack(icon(aspect, amount), amount)), 1));
        }
    }
    private Goods icon(Object aspect, int amount) throws Exception {
        Item item = (Item) field(type("com.gtnewhorizons.aspectrecipeindex.ModItems"), "itemAspect");
        ItemStack stack = new ItemStack(item, amount, 0);
        call(type("com.gtnewhorizons.aspectrecipeindex.common.items.ItemAspect"), "setAspect", stack, aspect);
        return game.item(stack);
    }
    void snapshotAspects(String id, ItemStack stack) throws Exception {
        try {
            Class<?> manager = type("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager");
            Object aspects = call(manager, "getObjectTags", stack);
            aspects = call(manager, "getBonusTags", stack, aspects);
            if (aspects == null) return;
            List<Stack> result = new ArrayList<>();
            // Preserve the reference's unsorted iteration for synthetic recipe slots.
            for (Object aspect : list(call(aspects, "getAspects"))) {
                int amount = ((Number) call(aspects, "getAmount", aspect)).intValue();
                if (amount > 0) result.add(new Stack(icon(aspect, amount), amount));
            }
            if (!result.isEmpty()) game.aspects.put(id, List.copyOf(result));
        } catch (StackOverflowError error) { game.data.report.add("ASPECT warning " + id + ": recursive aspect calculation"); }
    }
}
