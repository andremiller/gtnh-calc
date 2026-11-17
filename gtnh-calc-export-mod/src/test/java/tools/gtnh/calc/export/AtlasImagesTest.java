package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import com.luciad.imageio.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import org.junit.Test;

public class AtlasImagesTest {
    @Test
    public void encodesTransparentAtlasAndPreservesTileCoordinates() throws Exception {
        BufferedImage atlas = AtlasImages.atlas(257);
        BufferedImage tile = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        tile.setRGB(5, 6, 0xff123456); tile.setRGB(7, 8, 0x8044aa22);
        AtlasImages.place(atlas, 256, tile);
        Path path = Files.createTempFile("calculator-atlas-", ".webp");
        try {
            AtlasImages.write(atlas, path);
            ImageReader reader = new WebPImageReaderSpi().createReaderInstance();
            try (FileImageInputStream input = new FileImageInputStream(path.toFile())) {
                reader.setInput(input); BufferedImage decoded = reader.read(0);
                assertEquals(8192, decoded.getWidth()); assertEquals(64, decoded.getHeight());
                assertEquals(0, decoded.getRGB(0, 0)); assertEquals(0xff123456, decoded.getRGB(5, 38));
                assertEquals(0x8044aa22, decoded.getRGB(7, 40));
            } finally { reader.dispose(); }
        } finally { Files.deleteIfExists(path); }
    }

    @Test
    public void boxFilterAveragesPremultipliedAlpha() {
        BufferedImage input = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        input.setRGB(0, 0, 0xffff0000);
        assertEquals(0x40ff0000, AtlasImages.halfSize(input).getRGB(0, 0));
    }
}
