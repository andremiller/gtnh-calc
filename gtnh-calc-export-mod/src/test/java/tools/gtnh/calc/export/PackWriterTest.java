package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import tools.gtnh.calc.export.ExportData.*;

public class PackWriterTest {
    static Goods goods(ExportData data, String id, String name, boolean fluid) {
        Goods g = new Goods(id, fluid); g.name = name; g.mod = "test"; g.internal = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        g.unlocalized = "test." + g.internal; data.goods.put(id, g); return g;
    }

    static ExportData fixture() {
        ExportData d = new ExportData();
        Goods ore = goods(d, "i:test:ore:0", "Test Ore", false);
        Goods alt = goods(d, "i:test:ore:1", "Alternative Ore", false);
        Goods dust = goods(d, "i:test:dust:0", "Test Dust", false);
        dust.tooltip.add("§aRich in iron & nickel"); dust.nbt = "{quality:1}";
        Goods ingot = goods(d, "i:test:ingot:0", "Test Ingot", false);
        Goods circuit = goods(d, "i:test:circuit:1", "Circuit", false); circuit.damage = 1; circuit.unlocalized = "gt.integrated_circuit";
        Goods nitrogen = goods(d, "f:test:nitrogen", "Nitrogen", true); nitrogen.gas = true;
        Goods empty = goods(d, "i:test:cell:0", "Empty Cell", false);
        Goods filled = goods(d, "i:test:cell:1", "Nitrogen Cell", false);
        filled.containedFluid = nitrogen; filled.amount = 1000; filled.empty = empty;
        Goods macerator = goods(d, "i:test:macerator:0", "Basic Macerator", false);
        Goods ebf = goods(d, "i:test:ebf:0", "Electric Blast Furnace", false);
        d.service.add(goods(d, "i:test:book:0", "Quest Book", false));
        d.service.add(goods(d, "i:test:anvil:0", "Anvil", false));
        Method mac = new Method("macerator", "Macerator"); mac.single.add(macerator); mac.single.add(null); mac.crafter = macerator;
        Method blast = new Method("blast", "Electric Blast Furnace"); blast.multi.add(ebf); blast.crafter = ebf;
        d.methods.add(mac); d.methods.add(blast);
        Group group = new Group("o:test:ore", List.of(ore, alt)); d.groups.put(group.id(), group);
        Recipe a = new Recipe(mac); a.voltage = 8; a.ticks = 100; a.amperage = 1;
        a.io.add(new Io(1, group, 0, 1, 100)); a.io.add(new Io(3, dust, 0, 2, 100));
        a.io.add(new Io(3, ore, 1, 1, 25)); a.identify(); d.recipes.add(a);
        Recipe b = new Recipe(blast); b.voltage = 120; b.ticks = 200; b.amperage = 1; b.tier = 1; b.special = 1800;
        b.metadata.put("coil_heat", 1800.0); b.metadata.put("fraction", 1.25);
        b.io.add(new Io(0, dust, 0, 1, 100)); b.io.add(new Io(0, circuit, 1, 0, 100));
        b.io.add(new Io(0, filled, 2, 0, 100)); b.io.add(new Io(2, nitrogen, 0, 1000, 100));
        b.io.add(new Io(3, ingot, 0, 1, 100)); b.io.add(new Io(4, nitrogen, 0, 50, 50));
        b.identify(); d.recipes.add(b); d.prepare(); return d;
    }

    @Test
    public void generatesBrowserFixtureWithPatchedReferences() throws Exception {
        ExportData data = fixture(); byte[] compressed = new PackWriter().write(data);
        ByteBuffer words = ByteBuffer.wrap(new GZIPInputStream(new ByteArrayInputStream(compressed)).readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(7, words.getInt());
        int items = words.getInt(4) * 4; assertEquals(11, words.getInt(items));
        int recipes = words.getInt(20) * 4; assertEquals(2, words.getInt(recipes));
        Path output = Path.of("build/fixtures/direct-export.bin"); Files.createDirectories(output.getParent()); Files.write(output, compressed);
        Path committed = Path.of("../src/tests/fixtures/direct-export.bin");
        if (Files.exists(committed)) assertArrayEquals("Regenerate the browser fixture after intentional format changes", Files.readAllBytes(committed), compressed);
    }

    @Test
    public void legacyIdentityIgnoresTimingAndMetadataButIncludesQuantities() {
        ExportData d = fixture(); Recipe recipe = d.recipes.get(0); String original = recipe.id;
        recipe.identify(); assertEquals(original, recipe.id);
        recipe.metadata.put("new", 2.0); recipe.ticks++; recipe.identify(); assertEquals(original, recipe.id);
        Io io = recipe.io.get(0); recipe.io.set(0, new Io(io.type(), io.goods(), io.slot(), io.amount() + 1, io.probability()));
        recipe.identify(); assertNotEquals(original, recipe.id);
    }

    @Test
    public void usesCalculatorTiersAndBankersRounding() {
        assertEquals(0, ExportData.tier(8)); assertEquals(0, ExportData.tier(32)); assertEquals(1, ExportData.tier(33));
        assertEquals(2, ExportData.tier(512)); assertEquals(0, ExportData.chance(50)); assertEquals(2, ExportData.chance(150));
        assertEquals(100, ExportData.chance(10000));
    }

    @Test
    public void searchBitsAreCaseInsensitiveAndResetAcrossPunctuation() {
        assertArrayEquals(SearchBits.of("IRON"), SearchBits.of("iron"));
        int[] separate = SearchBits.of("a", "b"); assertArrayEquals(separate, SearchBits.of("a-b"));
        assertArrayEquals(SearchBits.of("H2O"), SearchBits.of("H₂O"));
    }

    @Test
    public void htmlEscapesTextAndKeepsMinecraftFormats() {
        assertEquals("<span class=\"fmt-a\">A &amp; B</span>&lt;x&gt;", MinecraftText.html("§aA & B§r<x>"));
    }

    @Test
    public void conflictMasksCompareConsumedInputsAndIgnoreCatalysts() {
        ExportData d = fixture(); Recipe original = d.recipes.stream().filter(r -> r.method.name.equals("Electric Blast Furnace")).findFirst().orElseThrow();
        Recipe other = new Recipe(original.method);
        for (Io io : original.io) if (io.type() < 3 && io.amount() > 0) other.io.add(io);
        other.identify(); d.recipes.add(other); d.prepare();
        assertEquals(1, original.conflicts);
    }
}
