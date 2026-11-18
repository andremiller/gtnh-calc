// SPDX-License-Identifier: LGPL-3.0-only
// Adapted compatibility behavior: NESQL Exporter contributors, b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57.
// See META-INF/licenses/NOTICE.txt and NESQL-LGPL-3.0.md.
package tools.gtnh.calc.export;

import codechicken.nei.ItemList;
import codechicken.nei.NEIServerUtils;
import cpw.mods.fml.common.Loader;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.enums.GTValues;
import gregtech.api.util.GTUtility;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.oredict.*;
import net.minecraftforge.fluids.*;
import tools.gtnh.calc.export.ExportData.*;
import tools.gtnh.calc.export.LegacyIds.*;
import tools.gtnh.calc.export.LegacyIds.Stack;
import static tools.gtnh.calc.export.GameReflection.*;

/** Live-game collection only. All steps execute on the client thread. */
final class GameExtraction {
    @FunctionalInterface interface Action { void run() throws Exception; }
    record Task(String source, boolean required, Action action) {}
    final ExportData data = new ExportData();
    final RawExport raw = new RawExport(data);
    final Map<String, ItemStack> itemIcons = new HashMap<>();
    final Map<String, FluidStack> fluidIcons = new HashMap<>();
    final Map<String, List<Stack>> aspects = new HashMap<>();
    private final Map<Item, List<ItemStack>> catalogue = new IdentityHashMap<>();
    private final Deque<Task> tasks = new ArrayDeque<>();
    private final Field metadataIdentifier;
    private int done, failed, unexpectedFailures;
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("gtnh_calc_export");
    private boolean containersScheduled;
    final ExportProgress progress = new ExportProgress();
    private int sourceRecipes, sourceTasks;
    private final boolean thaumcraft = Loader.isModLoaded("Thaumcraft");

    GameExtraction() throws Exception {
        metadataIdentifier = RecipeMetadataKey.class.getDeclaredField("identifier"); metadataIdentifier.setAccessible(true);
        data.report.add("Collector reference: NESQL b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57 + repository C# rules.");
        for (ItemStack stack : ItemList.items) if (valid(stack)) {
            catalogue.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>()).add(stack);
            task("NEI item", () -> item(stack));
        }
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) task("Registered fluid", () -> fluid(new FluidStack(fluid, 1)));
        // Sorted plugin order matches the reference's recipe-type table ordering, including name grouping.
        if (Loader.isModLoaded("Avaritia")) tasks.add(new Task("Avaritia adapter", true, () -> new AvaritiaCollector(this).schedule()));
        tasks.add(new Task("GregTech adapter", true, this::scheduleGt));
        tasks.add(new Task("Minecraft adapter", true, () -> new CraftingCollector(this).schedule()));
        if (thaumcraft) tasks.add(new Task("Thaumcraft adapter", true, () -> new ThaumcraftCollector(this).schedule()));
        tasks.add(new Task("Ore dictionary adapter", true, () -> {
            for (String name : Arrays.stream(OreDictionary.getOreNames()).sorted().toList()) if (!name.isEmpty()) task("Ore dictionary " + name, () -> raw.ore(name, stacks(OreDictionary.getOres(name, false), 1)));
        }));
    }

    void task(String source, Action action) { tasks.add(new Task(source, false, action)); }
    String progress() { return progress.status(done, done + tasks.size()); }
    String diagnosticSummary() {
        return (data.researchMismatches > 0 ? "WARNING: " : "") + "Diagnostics: " + unexpectedFailures + " unexpected collection failures; research read-back "
            + data.researchChecked + " checked, " + data.researchMismatches + " mismatches, " + data.researchExcluded + " not exported. See report.txt";
    }
    String summary() { return data.recipes.size() + " recipes; " + failed + " collection errors (see report)"; }

    boolean step() throws Exception {
        if (!tasks.isEmpty()) {
            Task task = tasks.removeFirst(); done++;
            long started = System.nanoTime();
            try { task.action.run(); }
            catch (Exception | LinkageError error) {
                data.report.add("UNSUPPORTED " + task.source + ": " + error);
                failed++;
                if (!ExportDiagnostics.expectedUnsupported(error)) {
                    unexpectedFailures++;
                    data.report.add("FAILURE " + task.source + "\n" + ExportDiagnostics.trace(error));
                    LOG.error("Calculator collection failure: " + task.source, error);
                }
                if (task.required) throw new IllegalStateException("Required adapter failed: " + task.source, error);
            }
            finally { progress.task(task.source, System.nanoTime() - started, data.report); }
            return false;
        }
        if (!containersScheduled) {
            containersScheduled = true;
            for (FluidContainerRegistry.FluidContainerData container : FluidContainerRegistry.getRegisteredFluidContainerData())
                task("Fluid container", () -> {
                    if (!valid(container.filledContainer) || !valid(container.emptyContainer) || container.fluid == null) return;
                    Goods filled = item(container.filledContainer), empty = item(container.emptyContainer);
                    if (filled.banned || empty.banned) return;
                    filled.containedFluid = fluid(container.fluid); filled.amount = container.fluid.amount; filled.empty = empty;
                    filled.keep = true; empty.keep = true;
                });
            tasks.add(new Task("Service items", true, () -> {
                ItemStack book = ItemList.items.stream().filter(GameExtraction::valid)
                    .filter(s -> "Quest Book".equals(MinecraftText.plain(s.getDisplayName()))).findFirst().orElseThrow();
                data.service.add(item(book)); data.service.add(item(new ItemStack(Blocks.anvil)));
            }));
            return false;
        }
        return true;
    }

    /** Client thread, after the filtering worker has completed. */
    void scheduleAspects() {
        done = 0;
        catalogue.clear();
        Set<Goods> retained = data.aspectCandidates();
        int eligible = (int) itemIcons.keySet().stream().filter(id -> data.goods.containsKey(id) && !data.goods.get(id).banned).count();
        if (thaumcraft) {
            ThaumcraftCollector collector = new ThaumcraftCollector(this);
            for (Goods g : retained.stream().sorted(Comparator.comparing(g -> g.id)).toList()) {
                ItemStack stack = itemIcons.get(g.id);
                if (stack != null) task("Item aspects " + g.id, () -> collector.snapshotAspects(g.id, stack));
            }
        }
        progress.stage("Item aspects", data.report);
        data.report.add("Aspect candidates: selected=" + tasks.size() + ", skipped=" + (eligible - tasks.size()));
    }

    /** Client thread, after final preprocessing and before rendering. */
    void releaseUnusedIcons() {
        Set<String> items = new HashSet<>(), fluids = new HashSet<>();
        for (Goods g : data.goods.values()) {
            if (g.fluid) fluids.add(g.iconKey == null ? g.id : g.iconKey);
            else items.add(g.id);
        }
        itemIcons.keySet().retainAll(items); fluidIcons.keySet().retainAll(fluids);
    }

    /** Detached filtering; no game API calls. */
    void filter() {
        sourceTasks = done;
        sourceRecipes = raw.recipes.size();
        raw.process();
        raw.releaseSources();
    }

    /** Detached postprocessing; no game API calls are allowed here. */
    void prepare() {
        Set<Goods> retained = data.aspectCandidates();
        if (thaumcraft && !aspects.isEmpty()) {
            Method method = raw.methods.get("Item aspects");
            for (Goods g : retained) {
                List<Stack> outputs = aspects.get(g.id); if (outputs == null || outputs.isEmpty()) continue;
                Recipe recipe = new Recipe(method); recipe.gt = false; recipe.id = "r:asp:" + g.id;
                recipe.io.add(new Io(0, g, 0, 1, 100)); int slot = 0;
                for (Stack output : outputs) recipe.io.add(new Io(3, output.goods(), slot++, output.amount(), 100));
                data.recipes.add(recipe);
            }
        }
        Map<Method, Integer> counts = new IdentityHashMap<>();
        for (Recipe recipe : data.recipes) counts.merge(recipe.method, 1, Integer::sum);
        for (Method method : data.methods) {
            if (method.name.equals("Canner")) { method.dimensions[2] = method.dimensions[3] = method.dimensions[6] = method.dimensions[7] = 1; }
            if (method.name.equals("Eye of Harmony")) { method.dimensions[2] = 1; method.dimensions[3] = 3; }
            if (method.crafter == null || !data.goods.containsKey(method.crafter.id)) {
                if (!method.multi.isEmpty() || method.single.stream().anyMatch(Objects::nonNull)) MachineSelection.finish(method);
            }
        }
        for (Method method : data.methods) if (method.crafter == null && counts.containsKey(method))
            throw new IllegalStateException("No usable crafter for " + method.name + "; see collection report");
        aspects.clear();
        data.prepare();
        for (Method method : data.methods) data.report.add("TYPE " + method.category + ":" + method.name + " recipes="
            + counts.getOrDefault(method, 0));
        data.report.add("Collected source records=" + sourceTasks + ", aspect tasks=" + done + ", collection errors=" + failed + ", source recipes=" + sourceRecipes + ", exported=" + data.recipes.size());
    }

    private void scheduleGt() throws Exception {
        Class<?> catalysts = type("codechicken.nei.recipe.RecipeCatalysts");
        for (RecipeMap<?> map : RecipeMap.ALL_RECIPE_MAPS.values().stream().sorted(Comparator.comparing(m -> m.unlocalizedName)).toList()) {
            List<?> machines = list(call(catalysts, "getRecipeCatalysts", map.unlocalizedName));
            if (machines.isEmpty()) { data.report.add("EXCLUDED map " + map.unlocalizedName + ": no NEI catalysts"); continue; }
            String name = gregtech.api.util.GTLanguageManager.getTranslation(map.unlocalizedName);
            int[] dimensions = gtDimensions(map);
            Method method = raw.method("gregtech", name, true, dimensions);
            for (Object catalyst : machines) {
                ItemStack stack = (ItemStack) field(catalyst, "item");
                if (valid(stack)) associate(method, stack);
            }
            int count = 0;
            for (GTRecipe recipe : map.getAllRecipes()) {
                int index = count++;
                task("GT " + map.unlocalizedName + " #" + index, () -> gt(map, method, name, recipe));
            }
            data.report.add("SOURCE " + map.unlocalizedName + ": " + count + " recipes");
        }
    }

    private int[] gtDimensions(RecipeMap<?> map) throws Exception {
        Object props = call(map.getFrontend(), "getUIProperties"); int[] result = new int[8];
        String[] names = {"ItemInput", "FluidInput", "ItemOutput", "FluidOutput"};
        for (int i = 0; i < 4; i++) {
            String prefix = Character.toLowerCase(names[i].charAt(0)) + names[i].substring(1);
            @SuppressWarnings("unchecked") IntFunction<Object> getter = (IntFunction<Object>) field(props, prefix + "PositionsGetter");
            List<?> positions = list(getter.apply(((Number) field(props, "max" + names[i] + "s")).intValue()));
            Set<Integer> x = new HashSet<>(), y = new HashSet<>();
            for (Object pos : positions) { x.add(((Number) field(pos, "x")).intValue()); y.add(((Number) field(pos, "y")).intValue()); }
            result[i * 2] = x.size(); result[i * 2 + 1] = y.size();
        }
        return result;
    }

    private void gt(RecipeMap<?> map, Method method, String name, GTRecipe source) throws Exception {
        String operation = "recipe header and metadata";
        try {
            Recipe r = new Recipe(method); r.ticks = source.mDuration; r.amperage = map.getAmperage();
            if (r.amperage <= 0) throw new IllegalArgumentException("Invalid map amperage");
            r.voltage = source.mEUt / r.amperage; // NESQL uses integer division.
            int gtTier = (int) GTUtility.getTier(r.voltage);
            if (gtTier < 0 || gtTier >= GTValues.VN.length) throw new IllegalArgumentException("Invalid voltage tier");
            r.tier = Math.max(0, gtTier - 1); r.special = source.mSpecialValue;
            if (source.getMetadataStorage() != null) for (var e : source.getMetadataStorage().getEntries()) {
                Object value = e.getValue(); String key = (String) metadataIdentifier.get(e.getKey());
                if (value instanceof Number number) r.metadata.put(key, (double) number.longValue());
                else if (value instanceof Boolean bool) r.metadata.put(key, bool ? 1.0 : 0.0);
            }
            if (map.unlocalizedName.equals("gt.recipe.fusionreactor")) r.special = (int) (long) source.getMetadataOrDefault(gregtech.api.util.GTRecipeConstants.FUSION_THRESHOLD, 0L);
            List<Slot> in = new ArrayList<>(), fluidsIn = new ArrayList<>(), out = new ArrayList<>(), fluidsOut = new ArrayList<>();
            for (int i = 0; i < source.mInputs.length; i++) if (source.mInputs[i] != null) {
                operation = "item input[" + i + "] reverse unification";
                ItemStack unified = GTOreDictUnificator.get(source.mInputs[i]);
                List<ItemStack> alternatives = GTOreDictUnificator.getNonUnifiedStacks(unified);
                operation = "item input[" + i + "] alternatives registry/identity";
                in.add(new Slot(i, stacks(alternatives, null), 1));
            }
            for (int i = 0; i < source.mFluidInputs.length; i++) if (source.mFluidInputs[i] != null) {
                operation = "fluid input[" + i + "] registry/identity";
                fluidsIn.add(new Slot(i, List.of(new Stack(fluid(source.mFluidInputs[i]), source.mFluidInputs[i].amount)), 1));
            }
            for (int i = 0; i < source.mOutputs.length; i++) if (source.mOutputs[i] != null) {
                operation = "item output[" + i + "] registry/identity";
                out.add(new Slot(i, List.of(new Stack(item(source.mOutputs[i]), source.mOutputs[i].stackSize)), source.getOutputChance(i) / 10000.0));
            }
            for (int i = 0; i < source.mFluidOutputs.length; i++) if (source.mFluidOutputs[i] != null) {
                operation = "fluid output[" + i + "] registry/identity";
                fluidsOut.add(new Slot(i, List.of(new Stack(fluid(source.mFluidOutputs[i]), source.mFluidOutputs[i].amount)), 1));
            }
            operation = "raw recipe identity";
            raw.add("gregtech", name + " (" + GTValues.VN[gtTier] + ")", r, in, fluidsIn, out, fluidsOut);
            if (map.unlocalizedName.equals("gt.recipe.researchStation")) {
                ExportDiagnostics.detail(data.report, "RESEARCH snapshot " + r.id, () -> {
                    ResearchAudit.capture(data, r);
                    ExportDiagnostics.detail(data.report, "RESEARCH " + r.id + " inputs/outputs", () -> r.id + "; raw EU/t=" + source.mEUt + "; inputs=" + ResearchAudit.slots(in) + "; fluid inputs=" + ResearchAudit.slots(fluidsIn) + "; outputs=" + ResearchAudit.slots(out) + "; fluid outputs=" + ResearchAudit.slots(fluidsOut));
                    if (source.getMetadataStorage() != null) for (var entry : source.getMetadataStorage().getEntries()) {
                        ExportDiagnostics.detail(data.report, "RESEARCH " + r.id + " raw metadata", () -> metadataIdentifier.get(entry.getKey())
                            + "; type=" + (entry.getValue() == null ? "null" : entry.getValue().getClass().getName()) + "; value=" + entry.getValue());
                    }
                    return "captured";
                });
            }
        } catch (Exception | LinkageError error) {
            String failedOperation = operation;
            ExportDiagnostics.detail(data.report, "GT failure snapshot", () -> {
                GtFailureSnapshot.capture(data.report, map.unlocalizedName, source, failedOperation); return "end snapshot";
            });
            throw error;
        }
    }

    void associate(Method method, ItemStack stack) {
        Goods g = item(stack); if (g.banned || method.multi.contains(g) || method.single.contains(g)) return;
        if (g.tooltip.stream().anyMatch(t -> t.contains("DEPRECATED"))) {
            data.report.add("EXCLUDED deprecated crafter from normal choices " + g.id);
            if (method.crafter == null) { method.crafter = g; g.keep = true; }
            return;
        }
        int tier = -1;
        String voltage = g.tooltip.stream().filter(t -> t.contains("Voltage IN")).findFirst().orElse(null);
        if (voltage != null) for (int i = 1; i < GTValues.VN.length; i++) if (MinecraftText.plain(voltage).contains("(" + GTValues.VN[i] + ")")) { tier = i; break; }
        MachineSelection.add(method, g, tier > 0, tier > 0, tier, data.report); g.keep = true;
        MachineSelection.finish(method);
    }

    List<Stack> stacks(Object input, Integer amount) {
        List<?> sources;
        if (input instanceof ItemStack[] array) sources = Arrays.asList(array);
        else if (input instanceof Collection<?> c) sources = new ArrayList<>(c);
        else sources = Arrays.asList(NEIServerUtils.extractRecipeItems(input));
        List<Stack> result = new ArrayList<>();
        for (Object object : sources) {
            if (object == null) continue; ItemStack stack = (ItemStack) object;
            int quantity = amount == null ? stack.stackSize : amount;
            if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                List<ItemStack> variants = catalogue.getOrDefault(stack.getItem(), List.of());
                if (variants.isEmpty()) { ItemStack copy = stack.copy(); copy.setItemDamage(0); variants = List.of(copy); }
                for (ItemStack variant : variants) result.add(new Stack(item(variant), quantity));
            } else result.add(new Stack(item(stack), quantity));
        }
        return result.stream().distinct().sorted(LegacyIds.ORDER).toList();
    }

    Goods item(ItemStack stack) {
        if (!valid(stack)) throw new IllegalArgumentException("Invalid item");
        RegistryName registry = registry(stack); int damage = Items.feather.getDamage(stack);
        String nbt = stack.hasTagCompound() ? stack.getTagCompound().toString() : "";
        String id = "i:" + registry + ":" + damage + (nbt.isEmpty() ? "" : ":" + ExportData.hash(nbt));
        Goods old = data.goods.get(id); if (old != null) return old;
        Goods g = new Goods(id, false); g.mod = registry.mod(); g.internal = registry.path(); g.damage = damage; g.nbt = nbt;
        g.numericId = Item.getIdFromItem(stack.getItem()); g.stackSize = stack.getMaxStackSize();
        try {
            g.name = stack.getDisplayName(); g.unlocalized = stack.getUnlocalizedName();
            for (Object line : stack.getTooltip(Minecraft.getMinecraft().thePlayer, true)) if (!String.valueOf(line).equals("§!shift")) g.tooltip.add(String.valueOf(line));
        } catch (Exception error) { g.name = "Error: " + error; g.unlocalized = ""; data.report.add("ITEM warning " + id + ": " + error); }
        g.banned = ReferenceRules.banned(g); g.keep = g.internal.equals("aspect") && !g.banned;
        data.goods.put(id, g); ItemStack icon = stack.copy(); icon.stackSize = 1; itemIcons.put(id, icon);
        return g;
    }
    Goods fluid(FluidStack stack) {
        RegistryName registry = RegistryName.parse(FluidRegistry.getDefaultFluidName(stack.getFluid()));
        String id = "f:" + registry, nbt = stack.tag == null ? "" : stack.tag.toString();
        String key = id + (nbt.isEmpty() ? "" : "~nbt:" + ExportData.hash(nbt));
        Goods old = data.goods.get(key); if (old != null) return old;
        Goods g = new Goods(id, true); g.mod = registry.mod(); g.internal = registry.path(); g.nbt = nbt; g.iconKey = key;
        g.name = stack.getLocalizedName(); g.unlocalized = stack.getUnlocalizedName(); g.numericId = stack.getFluidID(); g.gas = stack.getFluid().isGaseous(stack);
        data.goods.put(key, g); fluidIcons.put(key, stack.copy()); return g;
    }
    private static RegistryName registry(ItemStack stack) { return RegistryName.parse(Item.itemRegistry.getNameForObject(stack.getItem())); }
    static boolean valid(ItemStack stack) { return stack != null && stack.getItem() != null; }
}
