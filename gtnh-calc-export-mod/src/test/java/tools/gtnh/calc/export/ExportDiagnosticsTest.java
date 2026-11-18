package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.junit.Test;

public class ExportDiagnosticsTest {
    @Test public void malformedRegistryFormattingKeepsSlotAndDoesNotHideOriginalFailure() {
        List<String> report = new ArrayList<>();
        IllegalStateException original = new IllegalStateException("original collection failure");
        try {
            try { throw original; }
            catch (IllegalStateException error) {
                ExportDiagnostics.detail(report, "input[1] alternative[2] raw registry", () -> RegistryName.parse(null));
                ExportDiagnostics.detail(report, "nullable raw registry", () -> null);
                throw error;
            }
        } catch (IllegalStateException caught) { assertSame(original, caught); }
        assertTrue(report.get(0).contains("input[1] alternative[2] raw registry=<unavailable:"));
        assertTrue(report.get(1).endsWith("=null"));
    }
    @Test public void nestedReflectionFailureIncludesCauseAndStack() {
        Throwable error = new InvocationTargetException(new IllegalArgumentException("missing infusion input"));
        String trace = ExportDiagnostics.trace(error);
        assertTrue(trace.contains("InvocationTargetException")); assertTrue(trace.contains("Caused by: java.lang.IllegalArgumentException: missing infusion input"));
        assertTrue(trace.contains("ExportDiagnosticsTest")); assertFalse(ExportDiagnostics.expectedUnsupported(error));
        assertFalse(ExportDiagnostics.expectedUnsupported(new UnsupportedOperationException("Missing or malformed registry name: null")));
        assertTrue(ExportDiagnostics.expectedUnsupported(new UnsupportedOperationException("Reference does not support example.CustomRecipe")));
    }
    @Test public void decodesResearchFieldsWithoutTruncatingLong() {
        long packed = (128000L << 16) | 32;
        assertEquals(8388608032L, packed); assertEquals(128000, ExportDiagnostics.researchUpper(packed));
        assertEquals(32, ExportDiagnostics.researchLower(packed)); assertEquals(-201326560, (int) packed);
        assertEquals(-1, ExportDiagnostics.researchLower(65535));
    }
}
