package tools.gtnh.calc.export;

import codechicken.nei.ItemList;
import gregtech.api.GregTechAPI;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;
import tools.gtnh.calc.export.ExportData.*;

/** Minecraft and GregTech access happens only on the client thread. */
final class GtExtraction {
    record Candidate(RecipeMap<?> map, ExportData.Method method, GTRecipe recipe, int index) {}
    final ExportData data = new ExportData();
    final Map<String, ItemStack> itemIcons = new HashMap<>();
    final Map<String, FluidStack> fluidIcons = new HashMap<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private final Set<String> recipeIds = new HashSet<>();
    private final Map<Item, List<ItemStack>> catalogue = new IdentityHashMap<>();
    private final Map<String, int[]> counts = new LinkedHashMap<>();
    private int next, machineIndex;
    private boolean machinesDone, finalized;
    private final Field metadataIdentifier;

    GtExtraction() throws ReflectiveOperationException {
        metadataIdentifier = RecipeMetadataKey.class.getDeclaredField("identifier");
        metadataIdentifier.setAccessible(true);
        for (ItemStack stack : ItemList.items) if (valid(stack)) catalogue.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>()).add(stack);
        addMap(RecipeMaps.maceratorRecipes, "Macerator");
        addMap(RecipeMaps.blastFurnaceRecipes, "Electric Blast Furnace");
        data.report.add("Target: GTNH 2.9.0 / GregTech 5.09.54.265; Macerator + Electric Blast Furnace only.");
        data.report.add("Base recipes; version-7 chances use integer percentages. No old recipe remaps.");
        data.report.add("Machine discovery uses registered controllers' advertised recipe maps; custom/dynamic modes may need later adapters.");
        ItemStack book = ItemList.items.stream().filter(GtExtraction::valid)
            .filter(s -> "Quest Book".equals(MinecraftText.plain(s.getDisplayName()))).findFirst()
            .orElseThrow(() -> new IllegalStateException("Quest Book not found; use English and wait for NEI item loading"));
        data.service.add(item(book));
        data.service.add(item(new ItemStack(Blocks.anvil)));
    }

    private void addMap(RecipeMap<?> map, String name) {
        ExportData.Method method = new ExportData.Method(map.unlocalizedName, name);
        data.methods.add(method); counts.put(method.id, new int[4]);
        int index = 0;
        for (GTRecipe recipe : map.getAllRecipes()) candidates.add(new Candidate(map, method, recipe, index++));
    }

    String summary() {
        int unsupported = counts.values().stream().mapToInt(count -> count[2]).sum();
        return data.recipes.size() + " recipes; " + unsupported + " unsupported";
    }

    String progress() { return machinesDone ? "Recipes " + next + "/" + candidates.size() : "Machines " + machineIndex + "/" + GregTechAPI.METATILEENTITIES.length; }

    /** One bounded unit per tick-loop iteration. */
    boolean step() throws Exception {
        if (!machinesDone) {
            if (machineIndex < GregTechAPI.METATILEENTITIES.length) { machine(GregTechAPI.METATILEENTITIES[machineIndex++]); return false; }
            machinesDone = true;
            for (ExportData.Method method : data.methods) {
                MachineSelection.finish(method);
                data.report.add(method.name + " machines: singleblocks=" + method.single.stream().filter(Objects::nonNull).count() + ", multiblocks=" + method.multi.size());
            }
        }
        if (next < candidates.size()) { extract(candidates.get(next++)); return false; }
        if (!finalized) {
            // Only registry container relations involving already-referenced goods are added.
            for (FluidContainerRegistry.FluidContainerData container : FluidContainerRegistry.getRegisteredFluidContainerData()) {
                if (!valid(container.filledContainer) || !valid(container.emptyContainer) || container.fluid == null) continue;
                Goods filled = data.goods.get(itemId(container.filledContainer));
                Goods liquid = data.goods.get(fluidId(container.fluid));
                if (filled == null && liquid == null) continue;
                if (container.fluid.tag != null && !container.fluid.tag.hasNoTags()) continue;
                if (filled == null) filled = item(container.filledContainer);
                if (filled.containedFluid != null) { data.report.add("Container warning: multiple contents for " + filled.id); continue; }
                filled.containedFluid = fluid(container.fluid); filled.amount = container.fluid.amount;
                filled.empty = item(container.emptyContainer);
            }
            for (ExportData.Method method : data.methods) {
                if (data.recipes.stream().noneMatch(recipe -> recipe.method == method))
                    throw new IllegalStateException("No supported recipes for " + method.name + "; see report");
            }
            counts.forEach((id, count) -> data.report.add(id + ": candidates=" + Arrays.stream(count).sum()
                + ", exported=" + count[0] + ", disabled/fake=" + count[1] + ", unsupported=" + count[2] + ", duplicates=" + count[3]));
            finalized = true;
        }
        return true;
    }

    private void extract(Candidate c) {
        GTRecipe source = c.recipe(); int[] count = counts.get(c.method().id);
        String label = c.method().id + " #" + c.index();
        if (!source.mEnabled || source.mFakeRecipe) { count[1]++; data.report.add("EXCLUDED " + label + ": disabled or fake"); return; }
        try {
            if (source.mDuration <= 0 || source.mEUt < 0) throw new UnsupportedOperationException("Nonpositive duration or generating recipe");
            if (source.mSpecialItems != null) throw new UnsupportedOperationException("Special slot requirements");
            Recipe r = new Recipe(c.method());
            r.ticks = source.mDuration; r.voltage = source.mEUt; r.amperage = c.map().getAmperage();
            if (r.amperage <= 0) throw new UnsupportedOperationException("Nonpositive map amperage");
            if (r.voltage % r.amperage != 0) throw new UnsupportedOperationException("Fractional per-amp voltage");
            r.voltage /= r.amperage;
            r.tier = ExportData.tier(r.voltage);
            r.special = source.getMetadataOrDefault(GTRecipeConstants.COIL_HEAT, source.mSpecialValue);
            if (source.getMetadataStorage() != null) for (var entry : source.getMetadataStorage().getEntries()) {
                String key = (String) metadataIdentifier.get(entry.getKey()); Object value = entry.getValue();
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) r.metadata.put(key, number.doubleValue());
                else if (value instanceof Boolean flag) r.metadata.put(key, flag ? 1.0 : 0.0);
                else throw new UnsupportedOperationException("Nonnumeric metadata " + key);
            }
            for (int i = 0; i < source.mInputs.length; i++) {
                ItemStack stack = source.mInputs[i]; if (stack == null) continue;
                if (!valid(stack)) throw new UnsupportedOperationException("Invalid input stack");
                if (source.getInputChance(i) != 10000) throw new UnsupportedOperationException("Probabilistic item consumption");
                ItemStack[] alternatives = source instanceof GTRecipe.GTRecipe_WithAlt alt && alt.mOreDictAlt != null
                    && i < alt.mOreDictAlt.length && alt.mOreDictAlt[i] != null && alt.mOreDictAlt[i].length > 0
                    ? alt.mOreDictAlt[i] : new ItemStack[] {stack};
                Object goods = alternatives(alternatives);
                r.io.add(new Io(goods instanceof Goods ? 0 : 1, goods, i, stack.stackSize, 100));
            }
            for (int i = 0; i < source.mFluidInputs.length; i++) {
                FluidStack stack = source.mFluidInputs[i]; if (stack == null) continue;
                if (source.getFluidInputChance(i) != 10000) throw new UnsupportedOperationException("Probabilistic fluid consumption");
                if (source.mAltFluidInputs != null && i < source.mAltFluidInputs.length && source.mAltFluidInputs[i] != null) {
                    for (FluidStack alt : source.mAltFluidInputs[i]) if (alt == null || !stack.isFluidEqual(alt))
                        throw new UnsupportedOperationException("Alternative fluid inputs");
                }
                r.io.add(new Io(2, fluid(stack), i, stack.amount, 100));
            }
            for (int i = 0; i < source.mOutputs.length; i++) {
                ItemStack stack = source.mOutputs[i]; if (stack == null) continue;
                if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) throw new UnsupportedOperationException("Wildcard output");
                r.io.add(new Io(3, item(stack), i, stack.stackSize, ExportData.chance(source.getOutputChance(i))));
            }
            for (int i = 0; i < source.mFluidOutputs.length; i++) {
                FluidStack stack = source.mFluidOutputs[i]; if (stack == null) continue;
                r.io.add(new Io(4, fluid(stack), i, stack.amount, ExportData.chance(source.getFluidOutputChance(i))));
            }
            if (r.io.stream().anyMatch(io -> io.amount() < 0)) throw new UnsupportedOperationException("Negative quantity");
            if (r.io.stream().noneMatch(io -> io.type() >= 3 && io.amount() > 0 && io.probability() > 0))
                throw new UnsupportedOperationException("No representable output");
            r.io.sort(Comparator.comparingInt(Io::type).thenComparingInt(Io::slot));
            r.identify();
            if (recipeIds.add(r.id)) { data.recipes.add(r); count[0]++; data.report.add("EXPORTED " + label + " -> " + r.id); }
            else { count[3]++; data.report.add("DUPLICATE " + label + " -> " + r.id); }
            if (Arrays.stream(source.mOutputChances == null ? new int[0] : source.mOutputChances).anyMatch(v -> v % 100 != 0)
                || Arrays.stream(source.mFluidOutputChances == null ? new int[0] : source.mFluidOutputChances).anyMatch(v -> v % 100 != 0)) {
                data.report.add("PRECISION " + label + ": chances rounded to version-7 integer percent");
            }
        } catch (Exception | LinkageError error) {
            count[2]++; data.report.add("UNSUPPORTED " + label + ": " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private Object alternatives(ItemStack[] alternatives) {
        TreeMap<String, Goods> values = new TreeMap<>();
        for (ItemStack stack : alternatives) {
            if (!valid(stack)) throw new UnsupportedOperationException("Invalid ingredient alternative");
            List<ItemStack> expanded = stack.getItemDamage() == OreDictionary.WILDCARD_VALUE
                ? catalogue.getOrDefault(stack.getItem(), List.of()) : List.of(stack);
            if (expanded.isEmpty()) throw new UnsupportedOperationException("Wildcard has no NEI variants: " + itemRegistry(stack));
            for (ItemStack candidate : expanded) {
                if (candidate.getItemDamage() == OreDictionary.WILDCARD_VALUE) continue;
                ItemStack copy = candidate.copy();
                if (stack.hasTagCompound()) copy.setTagCompound((NBTTagCompound) stack.getTagCompound().copy());
                Goods g = item(copy); values.put(g.id, g);
            }
        }
        if (values.isEmpty()) throw new UnsupportedOperationException("Empty ingredient alternatives");
        if (values.size() == 1) return values.firstEntry().getValue();
        StringBuilder identity = new StringBuilder(); values.keySet().forEach(id -> ExportData.append(identity, id));
        String id = "o:gt:" + ExportData.hash(identity.toString());
        return data.groups.computeIfAbsent(id, ignored -> new Group(id, List.copyOf(values.values())));
    }

    private Goods item(ItemStack stack) {
        if (!valid(stack)) throw new IllegalArgumentException("Invalid item");
        String id = itemId(stack); Goods existing = data.goods.get(id); if (existing != null) return existing;
        RegistryName registry = itemRegistry(stack);
        Goods g = new Goods(id, false); g.mod = registry.mod(); g.internal = registry.path();
        g.name = MinecraftText.plain(stack.getDisplayName()); g.unlocalized = stack.getUnlocalizedName();
        if (g.name.isBlank()) { g.name = registry + ":" + stack.getItemDamage(); data.report.add("Name warning " + id + ": registry fallback"); }
        g.numericId = Item.getIdFromItem(stack.getItem()); g.damage = stack.getItemDamage(); g.stackSize = stack.getMaxStackSize();
        g.nbt = stack.hasTagCompound() ? CanonicalNbt.text(stack.getTagCompound()) : null;
        try {
            List<?> tooltip = stack.getTooltip(Minecraft.getMinecraft().thePlayer, false);
            for (int i = 1; i < tooltip.size(); i++) {
                String line = String.valueOf(tooltip.get(i)); String lower = MinecraftText.plain(line).toLowerCase(Locale.ROOT);
                if ((lower.contains("hold") || lower.contains("press")) && (lower.contains("shift") || lower.contains("ctrl") || lower.contains("control"))) continue;
                g.tooltip.add(line);
            }
        } catch (Exception error) { data.report.add("Tooltip warning " + id + ": " + error); }
        data.goods.put(id, g); ItemStack icon = stack.copy(); icon.stackSize = 1; itemIcons.put(id, icon); return g;
    }

    private Goods fluid(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) throw new UnsupportedOperationException("Invalid fluid");
        if (stack.tag != null && !stack.tag.hasNoTags()) throw new UnsupportedOperationException("Tagged fluid");
        String id = fluidId(stack); Goods existing = data.goods.get(id); if (existing != null) return existing;
        Fluid f = stack.getFluid(); String registry = FluidRegistry.getDefaultFluidName(f);
        Goods g = new Goods(id, true); g.mod = registry.substring(0, registry.indexOf(':')); g.internal = f.getName();
        g.name = MinecraftText.plain(stack.getLocalizedName()); g.unlocalized = f.getUnlocalizedName(stack);
        g.numericId = f.getID(); g.gas = f.isGaseous(stack);
        data.goods.put(id, g); fluidIcons.put(id, stack.copy()); return g;
    }

    private void machine(Object machine) {
        if (machine == null) return;
        boolean single = inherits(machine.getClass(), "gregtech.api.metatileentity.implementations.MTEBasicMachine");
        boolean multi = inherits(machine.getClass(), "gregtech.api.metatileentity.implementations.MTEMultiBlockBase");
        if (!single && !multi) return;
        try {
            Set<Object> maps = Collections.newSetFromMap(new IdentityHashMap<>());
            maps.add(machine.getClass().getMethod("getRecipeMap").invoke(machine));
            try {
                Object available = machine.getClass().getMethod("getAvailableRecipeMaps").invoke(machine);
                if (available instanceof Collection<?> collection) maps.addAll(collection);
                if (available instanceof Object[] array) Collections.addAll(maps, array);
            } catch (NoSuchMethodException ignored) { /* Most machines expose only a default map. */ }
            catch (ReflectiveOperationException | LinkageError error) {
                data.report.add("Machine mode warning " + machine.getClass().getName() + ": " + error);
            }
            for (int mapIndex = 0; mapIndex < data.methods.size(); mapIndex++) {
                RecipeMap<?> target = mapIndex == 0 ? RecipeMaps.maceratorRecipes : RecipeMaps.blastFurnaceRecipes;
                if (!maps.contains(target)) continue;
                ExportData.Method method = data.methods.get(mapIndex);
                Goods g = item((ItemStack) machine.getClass().getMethod("getStackForm", long.class).invoke(machine, 1L));
                boolean electric = single && Boolean.TRUE.equals(machine.getClass().getMethod("isElectric").invoke(machine));
                int gtTier = electric ? ((Number) machine.getClass().getField("mTier").get(machine)).intValue() : -1;
                MachineSelection.add(method, g, single, electric, gtTier, data.report);
                data.report.add("MACHINE " + method.name + " -> " + g.id + " (" + g.name + ")");
            }
        } catch (Exception | LinkageError error) { data.report.add("Machine warning " + machine.getClass().getName() + ": " + error); }
    }

    private static boolean inherits(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) if (c.getName().equals(name)) return true;
        return false;
    }
    private static boolean valid(ItemStack stack) { return stack != null && stack.getItem() != null; }
    private static String itemId(ItemStack stack) {
        String id = "i:" + itemRegistry(stack) + ":" + stack.getItemDamage();
        return stack.hasTagCompound() ? id + ":" + ExportData.hash(CanonicalNbt.text(stack.getTagCompound())) : id;
    }
    private static RegistryName itemRegistry(ItemStack stack) {
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        try { return RegistryName.parse(name); }
        catch (UnsupportedOperationException error) {
            throw new UnsupportedOperationException(error.getMessage() + " (" + stack.getItem().getClass().getName()
                + ", damage=" + stack.getItemDamage() + ")", error);
        }
    }
    private static String fluidId(FluidStack stack) {
        String registry = FluidRegistry.getDefaultFluidName(stack.getFluid());
        return "f:" + registry.substring(0, registry.indexOf(':')) + ":" + stack.getFluid().getName();
    }
}
