package tools.gtnh.calc.export;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/** Failure-only formatting must never replace the failure being diagnosed. */
final class ExportDiagnostics {
    @FunctionalInterface interface Value { Object get() throws Exception; }
    static void detail(List<String> report, String label, Value value) {
        try { report.add("DIAGNOSTIC " + label + "=" + value.get()); }
        catch (Exception | LinkageError error) { report.add("DIAGNOSTIC " + label + "=<unavailable: " + error.getClass().getName() + ">"); }
    }
    static String trace(Throwable error) {
        StringWriter result = new StringWriter(); error.printStackTrace(new PrintWriter(result)); return result.toString();
    }
    static boolean expectedUnsupported(Throwable error) {
        if (!(error instanceof UnsupportedOperationException) || error.getCause() != null) return false;
        String message = error.getMessage();
        return message != null && (message.startsWith("Crafting implementation not supported by reference collector")
            || message.startsWith("Reference does not support ") || message.equals("No static recipe output")
            || message.equals("Infusion exceeds reference layout"));
    }
    static long researchUpper(long packed) { return packed >>> 16; }
    static int researchLower(long packed) { return (short) (packed & 65535L); }
}
