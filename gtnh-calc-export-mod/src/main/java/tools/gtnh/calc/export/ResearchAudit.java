package tools.gtnh.calc.export;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import tools.gtnh.calc.export.ExportData.*;
import tools.gtnh.calc.export.LegacyIds.Slot;

/** Detached source snapshots and independent read-back of the generated v7 artifact. */
final class ResearchAudit {
    record Snapshot(int voltage, int ticks, int amperage, int special, Map<String, Double> metadata) {
        Snapshot { metadata = Map.copyOf(metadata); }
    }
    static void capture(ExportData data, Recipe recipe) {
        Map<String, Double> expectedMetadata = new TreeMap<>(recipe.metadata);
        RawExport.cleanMetadata(expectedMetadata);
        Snapshot snapshot = new Snapshot(recipe.voltage, recipe.ticks, recipe.amperage, recipe.special, expectedMetadata);
        data.researchSnapshots.putIfAbsent(recipe.id, snapshot);
        data.report.add("RESEARCH source " + recipe.id + ": " + snapshot);
        Double packed = recipe.metadata.get("research_station_data");
        if (packed != null) data.report.add("RESEARCH decoded " + recipe.id + ": packed=" + packed.longValue()
            + "; upper=" + ExportDiagnostics.researchUpper(packed.longValue())
            + "; lower(signed short)=" + ExportDiagnostics.researchLower(packed.longValue())
            + "; legacy special(signed int)=" + recipe.special);
    }
    static String slots(List<Slot> slots) {
        List<String> result = new ArrayList<>();
        for (Slot slot : slots) result.add("slot " + slot.slot() + "=" + slot.stacks().stream()
            .map(s -> s.goods().id + " [" + s.goods().name + "] x" + s.amount()).toList());
        return result.toString();
    }
    static void verify(ExportData data, byte[] gzip) {
        if (data.researchSnapshots.isEmpty()) return;
        data.researchChecked = data.researchMismatches = data.researchExcluded = 0;
        try {
            byte[] bytes;
            try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
                int limit = 512 * 1024 * 1024;
                bytes = input.readNBytes(limit + 1);
                if (bytes.length > limit) throw new IOException("Research read-back exceeds 512 MiB");
            }
            PreviousPack.Reader reader = new PreviousPack.Reader(bytes);
            if (reader.word(0) != 7) throw new IOException("Research read-back requires version 7");
            Set<String> seen = new HashSet<>(), retained = new HashSet<>();
            data.recipes.forEach(r -> retained.add(r.id));
            for (int p : reader.array(reader.word(5))) {
                String id = reader.string(reader.word(p + 4));
                Snapshot expected = data.researchSnapshots.get(id); if (expected == null) continue;
                if (!seen.add(id)) { mismatch(data, id + ": duplicate serialized ID"); continue; }
                int gt = reader.word(p + 7);
                if (gt == -1) { mismatch(data, id + ": missing GT record"); continue; }
                Map<String, Double> metadata = new TreeMap<>();
                for (int m : reader.array(reader.word(gt + 4))) {
                    String key = reader.string(reader.word(m));
                    long bits = Integer.toUnsignedLong(reader.word(m + 1)) | (long) reader.word(m + 2) << 32;
                    if (metadata.put(key, Double.longBitsToDouble(bits)) != null) throw new IOException("Duplicate research metadata key");
                }
                Snapshot actual = new Snapshot(reader.word(gt), reader.word(gt + 1), reader.word(gt + 2), reader.word(gt + 6), metadata);
                data.researchChecked++;
                if (!expected.equals(actual)) mismatch(data, id + ": source=" + expected + "; binary=" + actual);
                else data.report.add("RESEARCH read-back OK " + id + ": " + actual);
            }
            for (String id : data.researchSnapshots.keySet()) if (!seen.contains(id)) {
                if (retained.contains(id)) mismatch(data, id + ": retained recipe missing from binary");
                else { data.researchExcluded++; data.report.add("RESEARCH not exported (filtered or duplicate source): " + id); }
            }
        } catch (IOException | RuntimeException error) {
            mismatch(data, "read-back could not complete\n" + ExportDiagnostics.trace(error));
        }
        data.report.add("RESEARCH summary: checked=" + data.researchChecked + ", mismatches/errors=" + data.researchMismatches
            + ", not exported=" + data.researchExcluded);
    }
    private static void mismatch(ExportData data, String text) { data.researchMismatches++; data.report.add("RESEARCH MISMATCH " + text); }
}
