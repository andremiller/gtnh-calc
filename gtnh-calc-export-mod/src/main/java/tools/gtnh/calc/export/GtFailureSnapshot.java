package tools.gtnh.calc.export;

import java.util.List;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Called only after a collection failure, on the client thread. No names/tooltips/rendering. */
final class GtFailureSnapshot {
    static void capture(List<String> report, String map, GTRecipe recipe, String operation) {
        ExportDiagnostics.detail(report, "GT failure map", () -> map + "; operation=" + operation);
        ExportDiagnostics.detail(report, "GT source", () -> "class=" + recipe.getClass().getName() + "; EU/t=" + recipe.mEUt
            + "; ticks=" + recipe.mDuration + "; special=" + recipe.mSpecialValue);
        for (int i = 0; i < recipe.mInputs.length; i++) {
            final int slot = i; ItemStack original = recipe.mInputs[i]; item(report, "input[" + i + "]", original);
            if (original == null) continue;
            ExportDiagnostics.detail(report, "input[" + i + "] reverse unification", () -> {
                ItemStack unified = GTOreDictUnificator.get(original); item(report, "input[" + slot + "] unified", unified);
                List<ItemStack> alternatives = GTOreDictUnificator.getNonUnifiedStacks(unified);
                if (alternatives == null) return "null alternatives";
                for (int j = 0; j < alternatives.size(); j++) item(report, "input[" + slot + "] alternative[" + j + "]", alternatives.get(j));
                return alternatives.size() + " alternatives";
            });
        }
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            item(report, "output[" + i + "]", recipe.mOutputs[i]); final int slot = i;
            ExportDiagnostics.detail(report, "output[" + i + "] chance", () -> recipe.getOutputChance(slot));
        }
        for (int i = 0; i < recipe.mFluidInputs.length; i++) fluid(report, "fluid input[" + i + "]", recipe.mFluidInputs[i]);
        for (int i = 0; i < recipe.mFluidOutputs.length; i++) fluid(report, "fluid output[" + i + "]", recipe.mFluidOutputs[i]);
    }
    private static void item(List<String> report, String label, ItemStack stack) {
        ExportDiagnostics.detail(report, label + " stack", () -> stack == null ? "null" : stack.getClass().getName());
        if (stack == null) return;
        ExportDiagnostics.detail(report, label + " item class", () -> stack.getItem() == null ? "null" : stack.getItem().getClass().getName());
        ExportDiagnostics.detail(report, label + " raw registry", () -> Item.itemRegistry.getNameForObject(stack.getItem()));
        ExportDiagnostics.detail(report, label + " numeric ID", () -> Item.getIdFromItem(stack.getItem()));
        ExportDiagnostics.detail(report, label + " quantity", () -> stack.stackSize);
        ExportDiagnostics.detail(report, label + " damage", () -> Items.feather.getDamage(stack));
        ExportDiagnostics.detail(report, label + " NBT", () -> stack.hasTagCompound() ? stack.getTagCompound().toString() : "none");
    }
    private static void fluid(List<String> report, String label, FluidStack stack) {
        ExportDiagnostics.detail(report, label + " stack", () -> stack == null ? "null" : stack.getClass().getName());
        if (stack == null) return;
        ExportDiagnostics.detail(report, label + " fluid class", () -> stack.getFluid() == null ? "null" : stack.getFluid().getClass().getName());
        ExportDiagnostics.detail(report, label + " raw default registry", () -> FluidRegistry.getDefaultFluidName(stack.getFluid()));
        ExportDiagnostics.detail(report, label + " registry", () -> FluidRegistry.getFluidName(stack.getFluid()));
        ExportDiagnostics.detail(report, label + " internal name", () -> stack.getFluid().getName());
        ExportDiagnostics.detail(report, label + " numeric ID", () -> stack.getFluidID());
        ExportDiagnostics.detail(report, label + " quantity", () -> stack.amount);
        ExportDiagnostics.detail(report, label + " NBT", () -> stack.tag == null ? "none" : stack.tag.toString());
    }
}
