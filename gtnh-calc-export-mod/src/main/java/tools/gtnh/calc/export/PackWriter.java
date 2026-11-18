package tools.gtnh.calc.export;

import java.io.IOException;
import java.util.*;
import tools.gtnh.calc.export.ExportData.*;

final class PackWriter {
    private final BinaryWriter writer = new BinaryWriter();
    private final Map<Object, Integer> pointers = new IdentityHashMap<>();

    byte[] write(ExportData data) throws IOException {
        writer.reserve(8);
        List<Goods> items = data.goods.values().stream().filter(g -> !g.fluid).toList();
        List<Goods> fluids = data.goods.values().stream().filter(g -> g.fluid).toList();
        for (Goods g : data.goods.values()) allocate(g, g.fluid ? 17 : 18);
        for (Group g : data.groups.values()) allocate(g, 6);
        for (Method m : data.methods) allocate(m, 7);
        for (Recipe r : data.recipes) allocate(r, 8);
        writer.set(0, 7);
        writer.set(1, refs(items)); writer.set(2, refs(fluids)); writer.set(3, refs(data.groups.values()));
        writer.set(4, refs(data.methods)); writer.set(5, refs(data.recipes));
        writer.set(6, refs(data.service));
        int[] remaps = new int[data.remaps.size()]; int remapIndex = 0;
        for (var entry : data.remaps.entrySet()) {
            int p = writer.reserve(2); remaps[remapIndex++] = p;
            writer.set(p, writer.string(entry.getKey())); writer.set(p + 1, pointer(entry.getValue()));
        }
        writer.set(7, writer.array(remaps));
        for (Goods g : data.goods.values()) {
            int p = pointer(g);
            List<String> search = new ArrayList<>(g.tooltip); search.add(g.name);
            prefix(p, g.id, SearchBits.of(search.toArray(String[]::new)));
            writer.set(p + 5, writer.string(MinecraftText.html(g.name)));
            writer.set(p + 6, writer.string(g.mod)); writer.set(p + 7, writer.string(g.internal));
            writer.set(p + 8, g.numericId); writer.set(p + 9, g.icon);
            writer.set(p + 10, writer.array(g.tooltip.stream().map(MinecraftText::html).mapToInt(writer::string).toArray()));
            writer.set(p + 11, writer.string(g.unlocalized)); writer.set(p + 12, writer.string(g.nbt));
            writer.set(p + 13, refs(g.production)); writer.set(p + 14, refs(g.consumption));
            if (g.fluid) {
                writer.set(p + 15, g.gas ? 1 : 0);
                writer.set(p + 16, writer.array(java.util.stream.IntStream.range(0, items.size())
                    .filter(i -> items.get(i).containedFluid == g).toArray()));
            } else {
                writer.set(p + 15, g.stackSize); writer.set(p + 16, g.damage);
                int container = -1;
                if (g.containedFluid != null) {
                    container = writer.reserve(3);
                    writer.set(container, pointer(g.containedFluid)); writer.set(container + 1, g.amount);
                    writer.set(container + 2, pointer(g.empty));
                }
                writer.set(p + 17, container);
            }
        }
        for (Group g : data.groups.values()) {
            int[] bits = new int[4];
            for (Goods variant : g.variants()) SearchBits.merge(bits, SearchBits.of(variant.name));
            prefix(pointer(g), g.id(), bits); writer.set(pointer(g) + 5, refs(g.variants()));
        }
        for (Method m : data.methods) {
            int p = pointer(m);
            writer.set(p, writer.string(m.name)); writer.set(p + 1, writer.string(m.category));
            writer.set(p + 2, writer.array(m.dimensions)); writer.set(p + 3, refs(m.multi));
            writer.set(p + 4, m.shapeless ? 1 : 0); writer.set(p + 5, refs(m.single)); writer.set(p + 6, pointer(m.crafter));
        }
        for (Recipe r : data.recipes) {
            int p = pointer(r);
            int[] bits = new int[4];
            for (Io io : r.io) for (Goods g : ExportData.variants(io.goods())) SearchBits.merge(bits, SearchBits.of(g.name));
            prefix(p, r.id, bits);
            int[] io = new int[r.io.size() * 5]; int i = 0;
            for (Io entry : r.io) {
                io[i++] = entry.type(); io[i++] = pointer(entry.goods()); io[i++] = entry.slot();
                io[i++] = entry.amount(); io[i++] = entry.probability();
            }
            writer.set(p + 5, writer.array(io)); writer.set(p + 6, pointer(r.method));
            if (!r.gt) { writer.set(p + 7, -1); continue; }
            int gt = writer.reserve(7); writer.set(p + 7, gt);
            writer.set(gt, r.voltage); writer.set(gt + 1, r.ticks); writer.set(gt + 2, r.amperage);
            writer.set(gt + 3, r.tier); writer.set(gt + 5, r.conflicts); writer.set(gt + 6, r.special);
            List<Integer> metadata = new ArrayList<>();
            r.metadata.forEach((key, value) -> {
                int m = writer.reserve(3); writer.set(m, writer.string(key)); writer.setDouble(m + 1, value); metadata.add(m);
            });
            writer.set(gt + 4, writer.array(metadata.stream().mapToInt(Integer::intValue).toArray()));
        }
        byte[] binary = writer.gzip();
        ResearchAudit.verify(data, binary);
        return binary;
    }

    private void allocate(Object object, int words) { pointers.put(object, writer.reserve(words)); }
    private int pointer(Object object) {
        if (object == null) return -1;
        Integer result = pointers.get(object);
        if (result == null) throw new IllegalStateException("Reference outside export graph: " + object);
        return result;
    }
    private int refs(Collection<?> objects) { return writer.array(objects.stream().mapToInt(this::pointer).toArray()); }
    private void prefix(int p, String id, int[] bits) {
        for (int i = 0; i < 4; i++) writer.set(p + i, bits[i]);
        writer.set(p + 4, writer.string(id));
    }
}
