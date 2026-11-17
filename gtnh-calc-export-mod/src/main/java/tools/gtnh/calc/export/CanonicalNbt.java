package tools.gtnh.calc.export;

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.nbt.*;

/** Deterministic, type-preserving debug text used for tagged-item identity. */
final class CanonicalNbt {
    static String text(NBTBase tag) {
        if (tag instanceof NBTTagCompound compound) {
            List<String> keys = new ArrayList<>();
            for (Object key : compound.func_150296_c()) keys.add((String) key);
            Collections.sort(keys);
            return keys.stream().map(key -> quote(key) + ":" + text(compound.getTag(key))).collect(Collectors.joining(",", "{", "}"));
        }
        if (tag instanceof NBTTagList list) {
            NBTTagList copy = (NBTTagList) list.copy(); List<String> values = new ArrayList<>();
            while (copy.tagCount() > 0) values.add(text(copy.removeTag(0)));
            // Include element type even for empty lists; NBT's own toString can lose it.
            return "[type=" + list.func_150303_d() + ";" + String.join(",", values) + "]";
        }
        if (tag instanceof NBTTagByteArray bytes) {
            byte[] values = bytes.func_150292_c(); List<String> parts = new ArrayList<>();
            for (byte value : values) parts.add(Byte.toString(value));
            return "[B;" + String.join(",", parts) + "]";
        }
        if (tag instanceof NBTTagIntArray ints) return "[I;" + Arrays.stream(ints.func_150302_c()).mapToObj(Integer::toString).collect(Collectors.joining(",")) + "]";
        if (tag instanceof NBTTagString string) return quote(string.func_150285_a_());
        return tag.toString();
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '\\' || c == '"') out.append('\\').append(c);
            else if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
