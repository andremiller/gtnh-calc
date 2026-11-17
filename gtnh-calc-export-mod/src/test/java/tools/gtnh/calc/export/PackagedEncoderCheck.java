package tools.gtnh.calc.export;

import java.awt.image.BufferedImage;
import java.net.URLClassLoader;
import java.nio.file.*;

/** Runs against the actual distributable, isolated from Gradle's unshaded test classes. */
public final class PackagedEncoderCheck {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        Path output = Files.createTempFile("packaged-calculator-atlas-", ".webp");
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> type = Class.forName("tools.gtnh.calc.export.AtlasImages", true, loader);
            var atlas = type.getDeclaredMethod("atlas", int.class); atlas.setAccessible(true);
            var write = type.getDeclaredMethod("write", BufferedImage.class, Path.class); write.setAccessible(true);
            write.invoke(null, atlas.invoke(null, 2), output);
            byte[] bytes = Files.readAllBytes(output);
            if (bytes.length < 12 || !new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))
                throw new AssertionError("Packaged encoder did not produce WebP");
            System.out.println("Packaged encoder verified on Java " + Runtime.version());
        } finally { Files.deleteIfExists(output); }
    }
}
