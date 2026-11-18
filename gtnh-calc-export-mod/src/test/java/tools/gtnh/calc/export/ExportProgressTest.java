package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class ExportProgressTest {
    @Test public void tracksStagesDynamicTotalsAndRecentRate() {
        AtomicLong clock = new AtomicLong(); List<String> report = new ArrayList<>();
        ExportProgress progress = new ExportProgress(clock::get);
        progress.stage("Sources", report); clock.set(2_000_000_000L);
        assertEquals("Sources: 20/100; 10/s; 2.0s elapsed", progress.status(20, 100));
        clock.set(4_000_000_000L);
        assertEquals("Sources: 30/200; 5/s; 4.0s elapsed", progress.status(30, 200));
        progress.stage("Aspects", report);
        assertTrue(report.stream().anyMatch(s -> s.startsWith("TIMING Sources: 4.0s; tasks=0")));
        assertEquals("Sources", progress.announcement()); assertEquals("Aspects", progress.announcement()); assertNull(progress.announcement());
        clock.set(6_000_000_000L);
        assertEquals("Aspects: 2/8; 1/s; 2.0s elapsed", progress.status(2, 8));
    }
    @Test public void logsEverySlowTaskButThrottlesChatAndKeepsTopTen() {
        AtomicLong clock = new AtomicLong(); List<String> report = new ArrayList<>();
        ExportProgress progress = new ExportProgress(clock::get);
        progress.task("fast", 1, report); assertNull(progress.warning());
        progress.task("first", 300_000_000L, report); assertTrue(progress.warning().contains("first"));
        progress.task("second", 400_000_000L, report); assertNull(progress.warning());
        clock.set(10_000_000_000L);
        progress.task("third", 500_000_000L, report); assertTrue(progress.warning().contains("third"));
        for (int i = 0; i < 20; i++) progress.task("task" + i, i, report);
        progress.finish(report);
        assertEquals(3, report.stream().filter(s -> s.startsWith("Slow task:")).count());
        assertEquals(10, report.stream().filter(s -> s.startsWith("SLOWEST ")).count());
    }
}
