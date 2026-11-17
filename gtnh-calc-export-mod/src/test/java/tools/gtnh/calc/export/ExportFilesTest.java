package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ExportFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void announcesCompletionOnlyAfterBothArtifactsArePublished() throws Exception {
        ExportData data = PackWriterTest.fixture(); Path directory = ExportFiles.directory(temporary.getRoot().toPath());
        ExportFiles.write(data, new PackWriter().write(data), AtlasImages.atlas(data.goods.size()), directory);
        assertTrue(Files.exists(directory.resolve("COMPLETE")));
        assertTrue(Files.size(directory.resolve("data.bin")) > 0);
        assertTrue(Files.size(directory.resolve("atlas.webp")) > 0);
        assertFalse(Files.exists(directory.resolve("data.bin.tmp")));
    }

    @Test
    public void failedEncodingNeverProducesACompleteMarker() throws Exception {
        ExportData data = PackWriterTest.fixture(); Path directory = ExportFiles.directory(temporary.getRoot().toPath());
        Files.createDirectory(directory.resolve("atlas.webp.tmp"));
        try {
            ExportFiles.write(data, new PackWriter().write(data), AtlasImages.atlas(data.goods.size()), directory);
            fail("Expected encoding failure");
        } catch (IOException expected) {
            assertTrue(Files.exists(directory.resolve("FAILED.txt")));
            assertFalse(Files.exists(directory.resolve("COMPLETE")));
            assertFalse(Files.exists(directory.resolve("data.bin")));
        }
    }
}
