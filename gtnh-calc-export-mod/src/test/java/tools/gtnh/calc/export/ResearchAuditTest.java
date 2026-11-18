package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import tools.gtnh.calc.export.ExportData.*;

public class ResearchAuditTest {
    private ExportData sample() {
        ExportData data = PackWriterTest.fixture(); Recipe recipe = data.recipes.get(0);
        long packed = (128000L << 16) | 32;
        recipe.metadata.put("research_station_data", (double) packed); recipe.special = (int) packed;
        ResearchAudit.capture(data, recipe); return data;
    }
    @Test public void verifiesActualGzipRecordAgainstDetachedSource() throws Exception {
        ExportData data = sample(); byte[] binary = new PackWriter().write(data);
        assertEquals(1, data.researchChecked); assertEquals(0, data.researchMismatches);
        assertTrue(data.report.stream().anyMatch(s -> s.contains("upper=128000; lower(signed short)=32")));
        assertTrue(data.report.stream().anyMatch(s -> s.startsWith("RESEARCH read-back OK")));
        // Report-only diagnostics leave the artifact untouched.
        data.researchSnapshots.clear(); assertArrayEquals(binary, new PackWriter().write(data));
    }
    @Test public void detectsMetadataAndSpecialValueMismatches() throws Exception {
        ExportData data = sample(); Recipe recipe = data.recipes.get(0);
        recipe.metadata.put("research_station_data", 1.0); recipe.special = 17;
        new PackWriter().write(data);
        assertEquals(1, data.researchChecked); assertEquals(1, data.researchMismatches);
        assertTrue(data.report.stream().anyMatch(s -> s.startsWith("RESEARCH MISMATCH") && s.contains("source=") && s.contains("binary=")));
    }
    @Test public void distinguishesFilteredRecipesFromMissingSerializedRecords() throws Exception {
        ExportData data = sample(); byte[] original = new PackWriter().write(data);
        Recipe recipe = data.recipes.get(0);
        data.recipes.remove(recipe); data.prepare(); byte[] filtered = new PackWriter().write(data);
        assertEquals(0, data.researchChecked); assertEquals(1, data.researchExcluded); assertEquals(0, data.researchMismatches);
        data.recipes.add(recipe); ResearchAudit.verify(data, filtered);
        assertEquals(1, data.researchMismatches); assertEquals(0, data.researchExcluded);
        ResearchAudit.verify(data, original); assertEquals(0, data.researchMismatches);
    }
    @Test public void reportsBrokenBinaryWithoutChangingExportFailurePolicy() {
        ExportData data = sample(); ResearchAudit.verify(data, new byte[] {1,2,3});
        assertEquals(1, data.researchMismatches);
        assertTrue(data.report.stream().anyMatch(s -> s.contains("read-back could not complete")));
    }
    @Test public void honorsIntentionalMetadataCleanup() throws Exception {
        ExportData data = sample(); Recipe recipe = data.recipes.get(0);
        data.researchSnapshots.clear(); recipe.metadata.put("cleanroom", 0.0); recipe.metadata.put("no_gas", 1.0);
        ResearchAudit.capture(data, recipe); RawExport.cleanMetadata(recipe.metadata);
        new PackWriter().write(data); assertEquals(1, data.researchChecked); assertEquals(0, data.researchMismatches);
    }
}
