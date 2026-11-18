package tools.gtnh.calc.export;

import java.util.*;
import java.util.function.LongSupplier;

/** Thread-safe timing only; never touches the game or sends chat from a worker. */
final class ExportProgress {
    private final LongSupplier clock;
    private String phase = "Starting", warning;
    private final Deque<String> announcements = new ArrayDeque<>();
    private long tasks, taskNanos;
    private long start, sample, sampledDone, lastWarning = Long.MIN_VALUE;
    private final PriorityQueue<Slow> slowest = new PriorityQueue<>(Comparator.comparingLong(Slow::nanos));
    private record Slow(String source, long nanos) {}
    ExportProgress() { this(System::nanoTime); }
    ExportProgress(LongSupplier clock) { this.clock = clock; start = sample = clock.getAsLong(); }
    synchronized void stage(String next, List<String> report) {
        long now = clock.getAsLong();
        report.add("TIMING " + phase + ": " + seconds(now - start) + "s; tasks=" + tasks + "; task time=" + seconds(taskNanos) + "s");
        phase = next; announcements.add(next); start = sample = now; sampledDone = tasks = taskNanos = 0;
    }
    synchronized String announcement() { return announcements.pollFirst(); }
    synchronized String status(long done, long total) {
        long now = clock.getAsLong(), elapsed = now - sample;
        double rate = elapsed > 0 ? (done - sampledDone) * 1e9 / elapsed : 0;
        if (elapsed >= 2_000_000_000L) { sample = now; sampledDone = done; }
        return phase + ": " + done + "/" + total + "; " + Math.round(rate) + "/s; " + seconds(now - start) + "s elapsed";
    }
    synchronized String waiting() { return phase + ": " + seconds(clock.getAsLong() - start) + "s elapsed"; }
    synchronized void task(String source, long nanos, List<String> report) {
        tasks++; taskNanos += nanos;
        if (slowest.size() < 10 || nanos > slowest.peek().nanos) {
            slowest.add(new Slow(source, nanos)); if (slowest.size() > 10) slowest.remove();
        }
        if (nanos < 250_000_000L) return;
        String text = "Slow task: " + source + " took " + seconds(nanos) + "s";
        report.add(text);
        long now = clock.getAsLong();
        if (lastWarning == Long.MIN_VALUE || now - lastWarning >= 10_000_000_000L) { warning = text; lastWarning = now; }
    }
    synchronized String warning() { String result = warning; warning = null; return result; }
    synchronized void finish(List<String> report) {
        stage("Complete", report);
        slowest.stream().sorted(Comparator.comparingLong(Slow::nanos).reversed())
            .forEach(s -> report.add("SLOWEST " + s.source + ": " + seconds(s.nanos) + "s"));
    }
    private static String seconds(long nanos) { return String.format(Locale.ROOT, "%.1f", nanos / 1e9); }
}
