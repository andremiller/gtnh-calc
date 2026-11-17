package tools.gtnh.calc.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Version-7 word-addressed storage. Record construction is deliberately separate. */
public final class BinaryWriter {
    private final List<Integer> words = new ArrayList<>();
    private final Map<String, Integer> strings = new HashMap<>();

    public int position() {
        return words.size();
    }

    public int reserve(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative record size");
        int position = position();
        for (int i = 0; i < count; i++) words.add(0);
        return position;
    }

    public void set(int position, int value) {
        words.set(position, value);
    }

    public void setDouble(int position, double value) {
        long bits = Double.doubleToRawLongBits(value);
        set(position, (int) bits);
        set(position + 1, (int) (bits >>> 32));
    }

    public int array(int... values) {
        int position = reserve(values.length + 1);
        set(position, values.length);
        for (int i = 0; i < values.length; i++) set(position + i + 1, values[i]);
        return position;
    }

    public int string(String value) {
        if (value == null || value.isEmpty()) return -1;
        String normalized = value.strip().replace("\r", "");
        Integer existing = strings.get(normalized);
        if (existing != null) return existing;
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        int position = reserve(1 + (bytes.length + 3) / 4);
        set(position, bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            int word = position + 1 + i / 4;
            set(word, words.get(word) | ((bytes[i] & 255) << (8 * (i % 4))));
        }
        strings.put(normalized, position);
        return position;
    }

    public byte[] gzip() throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(result)) {
            for (int word : words) {
                output.write(word);
                output.write(word >>> 8);
                output.write(word >>> 16);
                output.write(word >>> 24);
            }
        }
        return result.toByteArray();
    }
}
