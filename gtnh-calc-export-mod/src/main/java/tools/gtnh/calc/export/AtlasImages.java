package tools.gtnh.calc.export;

import com.luciad.imageio.webp.WebPImageWriterSpi;
import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

final class AtlasImages {
    static BufferedImage atlas(int icons) {
        int rows = Math.max(1, (icons + 255) / 256);
        if (rows * 32 > 16383) throw new IllegalArgumentException("Atlas exceeds WebP dimension limit");
        return new BufferedImage(8192, rows * 32, BufferedImage.TYPE_INT_ARGB);
    }

    static BufferedImage halfSize(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth() / 2, source.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < result.getHeight(); y++) for (int x = 0; x < result.getWidth(); x++) {
            int alpha = 0, red = 0, green = 0, blue = 0;
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int color = source.getRGB(x * 2 + dx, y * 2 + dy), a = color >>> 24;
                alpha += a; red += ((color >>> 16) & 255) * a; green += ((color >>> 8) & 255) * a; blue += (color & 255) * a;
            }
            result.setRGB(x, y, alpha == 0 ? 0 : ((alpha + 2) / 4 << 24) | (red / alpha << 16) | (green / alpha << 8) | blue / alpha);
        }
        return result;
    }

    static BufferedImage placeholder() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
            image.setRGB(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0xffff00ff : 0xff222222);
        return image;
    }

    static void place(BufferedImage atlas, int icon, BufferedImage tile) {
        if (tile.getWidth() != 32 || tile.getHeight() != 32) throw new IllegalArgumentException("Expected 32px tile");
        int x = (icon % 256) * 32, y = (icon / 256) * 32;
        atlas.setRGB(x, y, 32, 32, tile.getRGB(0, 0, 32, 32, null, 0, 32), 0, 32);
    }

    static void write(BufferedImage image, Path path) throws IOException {
        ImageWriter writer = new WebPImageWriterSpi().createWriterInstance();
        try (FileImageOutputStream output = new FileImageOutputStream(path.toFile())) {
            writer.setOutput(output);
            WebPWriteParam param = new WebPWriteParam(writer.getLocale());
            param.setCompressionMode(WebPWriteParam.MODE_EXPLICIT);
            param.setCompressionType(param.getCompressionTypes()[WebPWriteParam.LOSSLESS_COMPRESSION]);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally { writer.dispose(); }
    }
}
