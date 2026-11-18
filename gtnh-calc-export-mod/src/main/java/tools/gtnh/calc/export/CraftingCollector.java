// SPDX-License-Identifier: LGPL-3.0-only
// Adapted compatibility behavior: NESQL Exporter contributors, b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57.
// See META-INF/licenses/NOTICE.txt and NESQL-LGPL-3.0.md.
package tools.gtnh.calc.export;

import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.oredict.*;
import codechicken.nei.NEIServerUtils;
import tools.gtnh.calc.export.ExportData.*;
import tools.gtnh.calc.export.LegacyIds.*;
import tools.gtnh.calc.export.LegacyIds.Stack;
import static tools.gtnh.calc.export.GameReflection.*;

final class CraftingCollector {
    final GameExtraction game;
    CraftingCollector(GameExtraction game) { this.game = game; }
    void schedule() {
        Method shaped = game.raw.method("minecraft", "Crafting (Shaped)", false, 3,3,0,0,1,1,0,0);
        Method shapeless = game.raw.method("minecraft", "Crafting (Shapeless)", true, 3,3,0,0,1,1,0,0);
        Method furnace = game.raw.method("minecraft", "Furnace", true, 1,1,0,0,1,1,0,0);
        game.associate(shaped, new ItemStack(Blocks.crafting_table)); game.associate(shapeless, new ItemStack(Blocks.crafting_table));
        game.associate(furnace, new ItemStack(Blocks.furnace));
        for (Object object : new ArrayList<>(CraftingManager.getInstance().getRecipeList())) {
            IRecipe recipe = (IRecipe) object;
            game.task("Minecraft crafting " + recipe.getClass().getName(), () -> {
                boolean isShaped = recipe instanceof ShapedRecipes || recipe instanceof ShapedOreRecipe;
                if (!(isShaped || recipe instanceof ShapelessRecipes || recipe instanceof ShapelessOreRecipe))
                    throw new UnsupportedOperationException("Crafting implementation not supported by reference collector");
                crafting("minecraft", isShaped ? "Crafting (Shaped)" : "Crafting (Shapeless)", isShaped ? shaped : shapeless, recipe);
            });
        }
        Map<?, ?> smelting = FurnaceRecipes.smelting().getSmeltingList();
        for (var entry : smelting.entrySet()) game.task("Minecraft furnace", () -> {
            List<Slot> in = new ArrayList<>(); ingredient(in, 0, entry.getKey(), null);
            add("minecraft", "Furnace", furnace, in, (ItemStack) entry.getValue());
        });
    }
    void crafting(String category, String name, Method method, IRecipe recipe) throws Exception {
        ItemStack output = recipe.getRecipeOutput(); if (!GameExtraction.valid(output)) throw new UnsupportedOperationException("No static recipe output");
        Object inputs;
        if (recipe instanceof ShapedRecipes shaped) inputs = shaped.recipeItems;
        else if (recipe instanceof ShapelessRecipes shapeless) inputs = shapeless.recipeItems;
        else if (recipe instanceof ShapedOreRecipe ore) inputs = ore.getInput();
        else if (recipe instanceof ShapelessOreRecipe ore) inputs = ore.getInput();
        else {
            try { inputs = field(recipe, "recipeItems"); }
            catch (NoSuchFieldException absent) { inputs = call(recipe, "getInput"); }
        }
        List<Slot> in = inputs(inputs, 1);
        add(category, name, method, in, output);
    }
    List<Slot> inputs(Object values, Integer amount) {
        List<Slot> result = new ArrayList<>(); int slot = 0;
        for (Object value : list(values)) ingredient(result, slot++, value, amount);
        return result;
    }
    void ingredient(List<Slot> into, int slot, Object value, Integer amount) {
        if (value == null) return;
        ItemStack[] extracted = NEIServerUtils.extractRecipeItems(value);
        if (extracted == null || extracted.length == 0) return;
        List<Stack> stacks = game.stacks(extracted, amount);
        if (!stacks.isEmpty()) into.add(new Slot(slot, stacks, 1));
    }
    void add(String category, String name, Method method, List<Slot> in, ItemStack output) {
        if (!GameExtraction.valid(output)) throw new UnsupportedOperationException("No static recipe output");
        Recipe recipe = new Recipe(method); recipe.gt = false;
        game.raw.add(category, name, recipe, in, List.of(), List.of(new Slot(0, List.of(new Stack(game.item(output), output.stackSize)), 1)), List.of());
    }
}
