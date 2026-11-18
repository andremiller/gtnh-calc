// SPDX-License-Identifier: LGPL-3.0-only
// Adapted compatibility behavior: NESQL Exporter contributors, b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57.
// See META-INF/licenses/NOTICE.txt and NESQL-LGPL-3.0.md.
package tools.gtnh.calc.export;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.gtnh.calc.export.ExportData.Goods;

/** NESQL b5b896e compatibility: deterministic proto3 wire encoding, length prefix, UUID v3.
 * The schema is recorded under src/test/resources/legacy-proto for independent verification.
 */
final class LegacyIds {
    record Stack(Goods goods, int amount) {}
    record Slot(int slot, List<Stack> stacks, double probability) {}
    static final Comparator<Stack> ORDER = Comparator.comparing((Stack s) -> s.goods.mod)
        .thenComparing(s -> s.goods.internal).thenComparingInt(s -> s.goods.damage)
        .thenComparing(s -> Objects.toString(s.goods.nbt, "")).thenComparingInt(Stack::amount);

    static String recipe(String category, String type, List<Slot> itemsIn, List<Slot> fluidsIn,
                         List<Slot> itemsOut, List<Slot> fluidsOut) {
        Wire typeMessage = new Wire().string(1, category).string(2, type);
        Wire result = new Wire().message(1, typeMessage.bytes());
        slots(result, 2, itemsIn, false, false); slots(result, 3, fluidsIn, true, false);
        slots(result, 4, itemsOut, false, true); slots(result, 5, fluidsOut, true, true);
        return "r~" + encoded(result.bytes());
    }

    static String group(List<Stack> stacks) { return "ig~" + encoded(groupBytes(stacks, false)); }

    private static void slots(Wire into, int field, List<Slot> slots, boolean fluid, boolean output) {
        slots.stream().sorted(Comparator.comparingInt(Slot::slot)).forEach(slot -> {
            byte[] value = output ? stackBytes(slot.stacks.get(0), fluid, slot.probability)
                : groupBytes(slot.stacks, fluid);
            into.message(field, new Wire().integerAlways(1, slot.slot).message(2, value).bytes());
        });
    }

    private static byte[] groupBytes(List<Stack> stacks, boolean fluid) {
        Wire group = new Wire();
        stacks.stream().distinct().sorted(ORDER).forEach(s -> group.message(1, stackBytes(s, fluid, null)));
        return group.bytes();
    }

    private static byte[] stackBytes(Stack stack, boolean fluid, Double probability) {
        Goods g = stack.goods;
        Wire w = new Wire().string(1, g.mod).string(2, g.internal);
        if (fluid) w.integer(3, stack.amount).string(4, g.nbt);
        else w.integer(3, g.damage).integer(4, stack.amount).string(5, g.nbt);
        if (probability != null && probability != 0) w.floating(fluid ? 5 : 6, probability);
        return w.bytes();
    }

    static String encoded(byte[] message) {
        Wire envelope = new Wire(); envelope.varint(message.length); envelope.out.writeBytes(message);
        UUID id = UUID.nameUUIDFromBytes(envelope.bytes());
        return Base64.getUrlEncoder().encodeToString(ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array());
    }

    static final class Wire {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        void varint(long value) { while ((value & ~127L) != 0) { out.write(((int) value & 127) | 128); value >>>= 7; } out.write((int) value); }
        Wire integer(int field, int value) { return value == 0 ? this : integerAlways(field, value); }
        Wire integerAlways(int field, int value) { varint(field * 8); varint(value); return this; }
        Wire string(int field, String text) { return text == null || text.isEmpty() ? this : message(field, text.getBytes(StandardCharsets.UTF_8)); }
        Wire message(int field, byte[] bytes) { varint(field * 8 + 2); varint(bytes.length); out.writeBytes(bytes); return this; }
        Wire floating(int field, double value) { varint(field * 8 + 1); out.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array()); return this; }
        byte[] bytes() { return out.toByteArray(); }
    }
}
