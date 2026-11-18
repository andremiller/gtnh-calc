package tools.gtnh.calc.export;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class ExportFiles {
    static Path directory(Path root) throws IOException {
        Files.createDirectories(root);
        return Files.createTempDirectory(root, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-");
    }

    static void write(ExportData data, byte[] binary, BufferedImage atlas, Path directory) throws IOException {
        write(data, binary, atlas, directory, null);
    }

    static void write(ExportData data, byte[] binary, BufferedImage atlas, Path directory, ExportProgress progress) throws IOException {
        Path bin = directory.resolve("data.bin.tmp"), webp = directory.resolve("atlas.webp.tmp");
        try {
            Files.write(bin, binary);
            AtlasImages.write(atlas, webp);
            data.report.add("Items=" + data.goods.values().stream().filter(g -> !g.fluid).count()
                + ", fluids=" + data.goods.values().stream().filter(g -> g.fluid).count() + ", recipes=" + data.recipes.size());
            if (progress != null) progress.finish(data.report);
            Files.write(directory.resolve("report.txt"), java.util.List.copyOf(data.report));
            Files.move(bin, directory.resolve("data.bin"));
            Files.move(webp, directory.resolve("atlas.webp"));
            Files.writeString(directory.resolve("COMPLETE"), "Both calculator artifacts are ready. See report.txt for exclusions and warnings.\n");
        } catch (IOException | RuntimeException | LinkageError error) {
            failure(data, directory, error);
            throw error;
        }
    }

    static void failure(ExportData data, Path directory, Throwable error) {
        try {
            if (data != null) Files.write(directory.resolve("report.txt"), java.util.List.copyOf(data.report));
            Files.writeString(directory.resolve("FAILED.txt"), error.toString() + "\nThis directory is incomplete; do not use its artifacts.\n");
        } catch (IOException secondary) { error.addSuppressed(secondary); }
    }
}
