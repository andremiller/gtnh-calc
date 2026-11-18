package tools.gtnh.calc.export;

import java.util.*;
import tools.gtnh.calc.export.ExportData.*;
import tools.gtnh.calc.export.LegacyIds.*;
import tools.gtnh.calc.export.LegacyIds.Stack;

/** Keeps NESQL fingerprint inputs intact until the C# selection stage has run. */
final class RawExport {
    record Source(Recipe recipe, List<Slot> itemsIn, List<Slot> fluidsIn, List<Slot> itemsOut, List<Slot> fluidsOut, List<String> inputGroups) {}
    final ExportData data;
    final Map<String, Source> recipes = new TreeMap<>();
    final Map<String, List<Stack>> bases = new TreeMap<>();
    final Map<String, String> oreNames = new HashMap<>();
    final Map<String, Method> methods = new LinkedHashMap<>();
    private final Map<String, Object> compiledGroups = new HashMap<>();
    RawExport(ExportData data) { this.data = data; }

    Method method(String category, String name, boolean shapeless, int... dimensions) {
        String normalized = category.equals("gregtech") && name.contains(" (") ? name.substring(0, name.indexOf(" (")) : name;
        Method m = methods.get(normalized);
        if (m == null) {
            m = new Method(category + ":" + name, normalized); m.category = category; m.shapeless = shapeless;
            m.fixedDimensions = true; System.arraycopy(dimensions, 0, m.dimensions, 0, Math.min(8, dimensions.length));
            methods.put(normalized, m); data.methods.add(m);
        }
        return m;
    }

    void add(String rawCategory, String rawType, Recipe recipe, List<Slot> in, List<Slot> fluidsIn, List<Slot> out, List<Slot> fluidsOut) {
        recipe.id = LegacyIds.recipe(rawCategory, rawType, in, fluidsIn, out, fluidsOut);
        if (recipes.putIfAbsent(recipe.id, new Source(recipe, List.copyOf(in), List.copyOf(fluidsIn), List.copyOf(out), List.copyOf(fluidsOut), in.stream().map(slot -> register(slot.stacks())).toList())) != null) {
            data.report.add("DUPLICATE " + recipe.id); return;
        }
    }
    String register(List<Stack> stacks) {
        List<Stack> base = stacks.stream().map(s -> new Stack(s.goods(), 1)).distinct().sorted(LegacyIds.ORDER).toList();
        String id = LegacyIds.group(base); bases.putIfAbsent(id, base); return id;
    }
    void ore(String name, List<Stack> stacks) { oreNames.put(register(stacks), name); }

    void process() {
        Set<Goods> touched = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Source source : recipes.values()) for (Slot output : source.itemsOut) for (Stack s : output.stacks()) if (!s.goods().banned) touched.add(s.goods());
        Set<String> usedBases = new HashSet<>();
        for (Source source : recipes.values()) usedBases.addAll(source.inputGroups);
        for (String id : new TreeSet<>(usedBases)) {
            List<Stack> candidates = bases.get(id).stream().filter(s -> !s.goods().banned).toList();
            if (!candidates.isEmpty() && candidates.stream().noneMatch(s -> touched.contains(s.goods()))) touched.add(candidates.get(0).goods());
        }
        for (Goods g : data.goods.values()) if (!g.banned && (g.keep || g.internal.equals("aspect"))) { g.keep = true; touched.add(g); }
        for (Method m : data.methods) {
            for (int i = 0; i < m.single.size(); i++) if (m.single.get(i) != null && m.single.get(i).banned) m.single.set(i, null);
            m.multi.removeIf(g -> g.banned);
            m.single.stream().filter(Objects::nonNull).forEach(touched::add); touched.addAll(m.multi);
        }
        touched.forEach(g -> g.keep = true);
        Map<String, Long> itemCounts = new HashMap<>();
        for (Goods g : touched) itemCounts.merge(g.mod + ":" + g.internal, 1L, Long::sum);
        for (String id : usedBases) {
            List<Goods> variants = bases.get(id).stream().map(Stack::goods).filter(g -> !g.banned && touched.contains(g)).distinct().toList();
            if (variants.isEmpty()) continue;
            if (variants.size() == 1) { compiledGroups.put(id, variants.get(0)); continue; }
            Goods first = variants.get(0); String groupId;
            if (oreNames.containsKey(id)) groupId = "o:" + oreNames.get(id);
            else if (variants.stream().allMatch(g -> g.mod.equals(first.mod) && g.internal.equals(first.internal))
                && itemCounts.getOrDefault(first.mod + ":" + first.internal, 0L) == variants.size()) groupId = "o:" + first.mod + ":" + first.internal;
            else groupId = "o:" + id;
            Group group = data.groups.get(groupId);
            if (group == null) { group = new Group(groupId, variants); data.groups.put(groupId, group); }
            else if (!new HashSet<>(group.variants()).equals(new HashSet<>(variants))) throw new IllegalStateException("Conflicting ore identity " + groupId);
            compiledGroups.put(id, group);
        }
        for (Source source : recipes.values()) {
            Recipe recipe = source.recipe;
            boolean rejected = source.itemsOut.stream().flatMap(s -> s.stacks().stream()).anyMatch(s -> s.goods().banned);
            for (int inputIndex = 0; inputIndex < source.itemsIn.size(); inputIndex++) {
                Slot slot = source.itemsIn.get(inputIndex);
                Object group = compiledGroups.get(source.inputGroups.get(inputIndex));
                if (group == null) { rejected = true; continue; }
                int amount = slot.stacks().stream().filter(s -> !s.goods().banned).findFirst().orElseThrow().amount();
                recipe.io.add(new Io(group instanceof Goods ? 0 : 1, group, slot.slot(), amount, 100));
            }
            append(recipe, source.fluidsIn, 2); append(recipe, source.itemsOut, 3); append(recipe, source.fluidsOut, 4);
            if (rejected) { data.report.add("FILTERED " + recipe.id + ": banned output or empty ingredient group"); continue; }
            recipe.io.sort(Comparator.comparingInt(Io::type).thenComparingInt(Io::slot));
            cleanMetadata(recipe.metadata);
            data.recipes.add(recipe);
        }
        for (Goods g : data.goods.values()) if (g.containedFluid != null && g.empty != null && g.empty.name.equals("Empty Cell") && !g.tooltip.isEmpty())
            g.containedFluid.tooltip = new ArrayList<>(g.tooltip);
        for (Goods g : data.goods.values()) { ReferenceRules.tooltip(g); g.name = ReferenceRules.font(g.name); g.tooltip.replaceAll(ReferenceRules::font); }
        data.goods.values().removeIf(g -> g.banned);
    }
    static void cleanMetadata(Map<String, Double> metadata) {
        metadata.entrySet().removeIf(e -> Set.of("no_gas", "no_gas_circuit_config", "additives").contains(e.getKey())
                || e.getValue() == 0 && Set.of("low_gravity", "cleanroom", "recycle").contains(e.getKey()));
    }
    void releaseSources() {
        recipes.clear(); bases.clear(); oreNames.clear(); compiledGroups.clear();
    }
    private static void append(Recipe recipe, List<Slot> slots, int type) {
        for (Slot slot : slots) {
            Stack stack = slot.stacks().get(0);
            recipe.io.add(new Io(type, stack.goods(), slot.slot(), stack.amount(), type == 2 ? 100 : (int) Math.rint((float) slot.probability() * 100f)));
        }
    }
}
