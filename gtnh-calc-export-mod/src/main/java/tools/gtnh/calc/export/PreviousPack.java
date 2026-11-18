package tools.gtnh.calc.export;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import tools.gtnh.calc.export.ExportData.*;

/** Bounded read-only parser for the version-6/7 fields used by OldRecipesGenerator. */
final class PreviousPack {
    record Entry(String id, String method, List<Part> io) {}
    record Part(int type, String goods, int amount) {}
    final List<Entry> recipes;
    final Map<String, String> aliases;
    private PreviousPack(List<Entry> recipes, Map<String, String> aliases) {
        this.recipes = List.copyOf(recipes); this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    }
    static PreviousPack read(Path path) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            int limit = 512 * 1024 * 1024;
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Previous export exceeds 512 MiB decompressed");
            return decode(bytes);
        }
    }
    static PreviousPack decode(byte[] bytes) throws IOException {
        Reader r = new Reader(bytes);
        int version = r.word(0);
        if (version != 6 && version != 7) throw new IOException("Unsupported previous export version " + version + " (supported: 6, 7)");
        int prefix = version == 6 ? 0 : 4;
        List<Entry> recipes = new ArrayList<>(); Map<Integer, String> identities = new HashMap<>(); Set<String> ids = new HashSet<>();
        for (int pointer : r.array(r.word(5))) {
            String id = r.string(r.word(pointer + prefix));
            if (id == null || !ids.add(id)) throw new IOException("Missing or duplicate recipe ID in previous export");
            String method = r.string(r.word(r.word(pointer + prefix + 2)));
            if (method == null) throw new IOException("Missing previous recipe type");
            int[] io = r.array(r.word(pointer + prefix + 1));
            if (io.length % 5 != 0) throw new IOException("Invalid previous recipe I/O length");
            List<Part> parts = new ArrayList<>();
            for (int i = 0; i < io.length; i += 5) {
                if (io[i] < 0 || io[i] > 4) throw new IOException("Invalid previous recipe I/O type");
                String goods = r.string(r.word(io[i + 1] + 4));
                if (goods == null) throw new IOException("Missing previous goods identity");
                parts.add(new Part(io[i], goods, io[i + 3]));
            }
            recipes.add(new Entry(id, method, List.copyOf(parts))); identities.put(pointer, id);
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        for (int p : r.array(r.word(7))) {
            String from = r.string(r.word(p)), to = identities.get(r.word(p + 1));
            if (from == null || to == null) throw new IOException("Invalid previous recipe remap target");
            aliases.put(from, to);
        }
        return new PreviousPack(recipes, aliases);
    }

    void apply(ExportData data) {
        Map<String, Recipe> current = new HashMap<>(), hashes = new HashMap<>(), resolved = new HashMap<>();
        Set<String> goods = new HashSet<>(); data.goods.values().forEach(g -> goods.add(g.id)); int ambiguous = 0, unchanged = 0, missing = 0, exact = 0, relaxed = 0, inherited = 0;
        for (Recipe recipe : data.recipes.stream().sorted(Comparator.comparing(r -> r.id)).toList()) {
            current.put(recipe.id, recipe);
            List<Part> io = recipe.io.stream().map(p -> new Part(p.type(), p.goodsId(), p.amount())).toList();
            for (boolean quantities : new boolean[] {true, false}) {
                String key = fingerprint(recipe.method.name, io, quantities, null);
                Recipe prior = hashes.put(key, recipe);
                if (prior != null && prior != recipe) {
                    ambiguous++; data.report.add("REMAP ambiguous fingerprint: " + prior.id + " -> " + recipe.id + " (stable last-match-wins)");
                }
            }
        }
        for (Entry old : recipes) {
            if (current.containsKey(old.id)) { unchanged++; continue; }
            Recipe match = hashes.get(fingerprint(old.method, old.io, true, goods));
            if (match != null) exact++;
            else {
                match = hashes.get(fingerprint(old.method, old.io, false, goods));
                if (match != null) relaxed++;
            }
            if (match == null) { missing++; data.report.add("REMAP unresolved " + old.id); }
            else { data.remaps.put(old.id, match); resolved.put(old.id, match); }
        }
        for (var alias : aliases.entrySet()) {
            if (current.containsKey(alias.getKey())) continue;
            Recipe target = current.getOrDefault(alias.getValue(), resolved.get(alias.getValue()));
            if (target == null) { data.report.add("REMAP unresolved inherited alias " + alias.getKey()); continue; }
            data.remaps.put(alias.getKey(), target); inherited++;
        }
        data.report.add("Previous export: unchanged=" + unchanged + ", quantity matches=" + exact + ", fallback matches=" + relaxed
            + ", inherited aliases=" + inherited + ", unresolved recipes=" + missing + ", ambiguous fingerprints=" + ambiguous);
    }

    private static String fingerprint(String method, List<Part> io, boolean quantities, Set<String> currentGoods) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5"); digest.update(method.getBytes(StandardCharsets.UTF_8));
            for (Part part : io) if (part.amount > 0 || part.type >= 3) {
                String id = part.goods;
                if (currentGoods != null && !currentGoods.contains(id)) {
                    String[] segments = id.split(":", -1);
                    if (segments.length >= 2) { segments[1] = "gregtech"; String candidate = String.join(":", segments); if (currentGoods.contains(candidate)) id = candidate; }
                }
                digest.update(id.getBytes(StandardCharsets.UTF_8));
                if (quantities) digest.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(part.amount).array());
            }
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    static final class Reader {
        final ByteBuffer data;
        Reader(byte[] bytes) throws IOException {
            if (bytes.length < 32 || bytes.length % 4 != 0) throw new IOException("Invalid previous export word buffer");
            data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }
        int word(int index) throws IOException {
            if (index < 0 || index >= data.capacity() / 4) throw new IOException("Previous export pointer out of bounds: " + index);
            return data.getInt(index * 4);
        }
        int[] array(int pointer) throws IOException {
            int length = word(pointer);
            if (length < 0 || length > data.capacity() / 4 - pointer - 1) throw new IOException("Invalid previous array length");
            int[] result = new int[length]; for (int i = 0; i < length; i++) result[i] = word(pointer + 1 + i); return result;
        }
        String string(int pointer) throws IOException {
            if (pointer == -1) return null;
            int length = word(pointer);
            long start = 4L * (pointer + 1);
            if (length < 0 || start + length > data.capacity()) throw new IOException("Invalid previous string length");
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data.array(), (int) start, length)).toString();
        }
    }
}
