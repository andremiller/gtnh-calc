// SPDX-License-Identifier: LGPL-3.0-only
// Adapted compatibility behavior: NESQL Exporter contributors, b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57.
// See META-INF/licenses/NOTICE.txt and NESQL-LGPL-3.0.md.
package tools.gtnh.calc.export;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import tools.gtnh.calc.export.ExportData.Method;
import tools.gtnh.calc.export.LegacyIds.Slot;
import static tools.gtnh.calc.export.GameReflection.*;

final class AvaritiaCollector {
    final GameExtraction game;
    AvaritiaCollector(GameExtraction game) { this.game = game; }
    void schedule() throws Exception {
        Class<?> blocks = type("fox.spiteful.avaritia.blocks.LudicrousBlocks");
        Method shaped = game.raw.method("avaritia", "Extreme Crafting (Shaped)", false, 9,9,0,0,1,1,0,0);
        Method shapeless = game.raw.method("avaritia", "Extreme Crafting (Shapeless)", true, 9,9,0,0,1,1,0,0);
        Method compressor = game.raw.method("avaritia", "Compressor", true, 1,1,0,0,1,1,0,0);
        ItemStack table = new ItemStack((Block) field(blocks, "dire_crafting"));
        game.associate(shaped, table); game.associate(shapeless, table);
        game.associate(compressor, new ItemStack((Block) field(blocks, "compressor")));
        CraftingCollector crafting = new CraftingCollector(game);
        for (Object recipe : list(call(type("fox.spiteful.avaritia.crafting.CompressorManager"), "getRecipes")))
            game.task("Avaritia compressor", () -> {
                List<Slot> in = new ArrayList<>(); crafting.ingredient(in, 0, call(recipe, "getIngredient"), null);
                // The reference collector exports getIngredient() quantities, not getCost().
                crafting.add("avaritia", "Compressor", compressor, in, (ItemStack) call(recipe, "getOutput"));
            });
        Object manager = call(type("fox.spiteful.avaritia.crafting.ExtremeCraftingManager"), "getInstance");
        for (Object value : list(call(manager, "getRecipeList"))) game.task("Avaritia extreme crafting", () -> {
            IRecipe recipe = (IRecipe) value; String name = recipe.getClass().getSimpleName();
            boolean isShaped = isKind(recipe, "fox.spiteful.avaritia.crafting.ExtremeShapedRecipe") || isKind(recipe, "fox.spiteful.avaritia.crafting.ExtremeShapedOreRecipe");
            if (!(isShaped || isKind(recipe, "fox.spiteful.avaritia.crafting.ExtremeShapelessRecipe") || recipe instanceof net.minecraftforge.oredict.ShapelessOreRecipe))
                throw new UnsupportedOperationException("Reference does not support " + name);
            crafting.crafting("avaritia", isShaped ? "Extreme Crafting (Shaped)" : "Extreme Crafting (Shapeless)", isShaped ? shaped : shapeless, recipe);
        });
    }
}
