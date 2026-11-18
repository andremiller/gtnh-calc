package tools.gtnh.calc.export;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Detached calculator graph: no Minecraft objects may reach the encoding worker. */
final class ExportData {
    final Map<String, Goods> goods = new TreeMap<>();
    final Map<String, Group> groups = new TreeMap<>();
    final List<Method> methods = new ArrayList<>();
    final List<Recipe> recipes = new ArrayList<>();
    final List<Goods> service = new ArrayList<>();
    final List<String> report = Collections.synchronizedList(new ArrayList<>());
    final Map<String, ResearchAudit.Snapshot> researchSnapshots = new TreeMap<>();
    int researchChecked, researchMismatches, researchExcluded;
    final Map<String, Recipe> remaps = new TreeMap<>();

    static final class Goods {
        final String id;
        final boolean fluid;
        String name, mod, internal, unlocalized, nbt, iconKey;
        List<String> tooltip = new ArrayList<>();
        int numericId, damage, stackSize = 64, icon, amount;
        boolean gas, keep, banned;
        Goods containedFluid, empty;
        final Set<Recipe> production = new LinkedHashSet<>(), consumption = new LinkedHashSet<>();
        Goods(String id, boolean fluid) { this.id = id; this.fluid = fluid; }
    }

    record Group(String id, List<Goods> variants) {}
    static final class Method {
        final String id, name;
        String category = "gregtech";
        boolean shapeless, fixedDimensions;
        final List<Goods> single = new ArrayList<>(), multi = new ArrayList<>();
        final int[] dimensions = new int[8];
        Goods crafter;
        Method(String id, String name) { this.id = id; this.name = name; }
    }
    record Io(int type, Object goods, int slot, int amount, int probability) {
        String goodsId() { return goods instanceof Goods g ? g.id : ((Group) goods).id(); }
    }
    static final class Recipe {
        String id;
        boolean gt = true;
        final Method method;
        final List<Io> io = new ArrayList<>();
        final Map<String, Double> metadata = new TreeMap<>();
        int voltage, ticks, amperage, tier, special, conflicts;
        Recipe(Method method) { this.method = method; }
        void identify() {
            List<LegacyIds.Slot> in = new ArrayList<>(), fluidsIn = new ArrayList<>(), out = new ArrayList<>(), fluidsOut = new ArrayList<>();
            for (Io entry : io) {
                List<LegacyIds.Stack> stacks = variants(entry.goods()).stream().map(g -> new LegacyIds.Stack(g, entry.amount())).toList();
                LegacyIds.Slot slot = new LegacyIds.Slot(entry.slot(), stacks, entry.probability() / 100.0);
                (entry.type() < 2 ? in : entry.type() == 2 ? fluidsIn : entry.type() == 3 ? out : fluidsOut).add(slot);
            }
            id = LegacyIds.recipe(method.category, method.name, in, fluidsIn, out, fluidsOut);
        }
    }

    static void append(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    static int chance(int nativeChance) {
        if (nativeChance < 0 || nativeChance > 10000) throw new IllegalArgumentException("Chance outside 0..10000");
        return (int) Math.rint(nativeChance / 100.0);
    }

    static int tier(long voltage) {
        int tier = 0;
        long limit = 32;
        while (voltage > limit && tier < 13) { limit *= 4; tier++; }
        if (voltage < 0 || voltage > limit) throw new IllegalArgumentException("Unrepresentable voltage: " + voltage);
        return tier;
    }

    static List<Goods> variants(Object object) {
        return object instanceof Goods g ? List.of(g) : ((Group) object).variants();
    }

    /** Exactly the eligibility set used to create synthetic aspect recipes. */
    Set<Goods> aspectCandidates() {
        Set<Goods> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        goods.values().stream().filter(g -> g.keep).forEach(retained::add);
        for (Recipe recipe : recipes) for (Io io : recipe.io) retained.addAll(variants(io.goods()));
        retained.removeIf(g -> g.fluid || g.banned);
        return retained;
    }

    void prepare() {
        recipes.sort(Comparator.comparing(r -> r.id));
        Set<Goods> used = new HashSet<>(service);
        goods.values().stream().filter(g -> g.fluid || g.keep).forEach(used::add);
        goods.values().forEach(g -> { g.production.clear(); g.consumption.clear(); });
        Set<Group> usedGroups = new HashSet<>();
        for (Method method : methods) {
            method.single.stream().filter(Objects::nonNull).forEach(used::add);
            used.addAll(method.multi);
            if (method.crafter != null) used.add(method.crafter);
        }
        for (Recipe recipe : recipes) for (Io entry : recipe.io) {
            used.addAll(variants(entry.goods()));
            if (entry.goods() instanceof Group group) usedGroups.add(group);
            for (Goods g : variants(entry.goods())) {
                (entry.type() >= 3 ? g.production : g.consumption).add(recipe);
            }
        }
        boolean changed;
        do {
            changed = false;
            for (Goods g : new ArrayList<>(used)) if (g.containedFluid != null) {
                changed |= used.add(g.containedFluid);
                changed |= used.add(g.empty);
            }
        } while (changed);
        goods.values().retainAll(used);
        groups.values().retainAll(usedGroups);
        int icon = 0;
        for (Goods g : goods.values()) g.icon = icon++;
        for (Method method : methods) {
            for (int section = 0; !method.fixedDimensions && section < 4; section++) {
                int maxSlot = -1;
                for (Recipe recipe : recipes) if (recipe.method == method) for (Io entry : recipe.io) {
                    int s = entry.type() <= 1 ? 0 : entry.type() == 2 ? 1 : entry.type() == 3 ? 2 : 3;
                    if (s == section) maxSlot = Math.max(maxSlot, entry.slot());
                }
                int width = maxSlot < 0 ? 0 : Math.min(4, maxSlot + 1);
                method.dimensions[section * 2] = width;
                method.dimensions[section * 2 + 1] = width == 0 ? 0 : (maxSlot + width) / width;
            }
        }
        calculateConflicts();
    }

    private void calculateConflicts() {
        Map<Recipe, Set<String>> consumed = new IdentityHashMap<>();
        Map<Recipe, Integer> circuits = new IdentityHashMap<>();
        for (Recipe r : recipes) {
            Set<String> set = new HashSet<>();
            int circuit = 0;
            for (Io io : r.io) if (io.type() < 3) {
                if (io.amount() > 0) set.add(io.goodsId());
                if (io.amount() == 0 && io.goods() instanceof Goods g && "gt.integrated_circuit".equals(g.unlocalized)) {
                    circuit = g.damage;
                }
            }
            if (circuit < 0 || circuit > 31) throw new IllegalArgumentException("Circuit outside conflict mask range");
            consumed.put(r, set);
            circuits.put(r, circuit);
        }
        Map<Method, Map<Integer, List<Recipe>>> buckets = new IdentityHashMap<>();
        for (Recipe r : recipes) buckets.computeIfAbsent(r.method, ignored -> new TreeMap<>())
            .computeIfAbsent(circuits.get(r), ignored -> new ArrayList<>()).add(r);
        for (Recipe r : recipes) {
            r.conflicts = 0;
            for (var bucket : buckets.get(r.method).entrySet()) {
                if (bucket.getKey().equals(circuits.get(r))) continue;
                for (Recipe other : bucket.getValue()) if (consumed.get(r).containsAll(consumed.get(other))) {
                    r.conflicts |= 1 << bucket.getKey(); break;
                }
            }
        }
    }
}
