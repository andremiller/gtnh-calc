package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;
import org.junit.Test;

public class BinaryWriterTest {
    @Test
    public void roundTripsWordsReferencesAndUtf8() throws Exception {
        BinaryWriter writer = new BinaryWriter();
        int header = writer.reserve(3);
        int text = writer.string(" é😀\r ");
        writer.set(header, 7);
        writer.set(header + 1, text);
        writer.set(header + 2, writer.array(-1, text));
        assertEquals(-1, writer.string(null));
        assertEquals(text, writer.string("é😀"));
        byte[] bytes = new GZIPInputStream(new ByteArrayInputStream(writer.gzip())).readAllBytes();
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(7, data.getInt(0));
        assertEquals(6, data.getInt(text * 4));
        assertEquals("é😀", new String(bytes, text * 4 + 4, 6, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0, bytes[text * 4 + 10]);
        assertEquals(2, data.getInt(data.getInt(8) * 4));
    }
}
