package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.nio.file.*;
import org.junit.Test;
import tools.gtnh.calc.export.ExportData.*;

public class RichFixtureTest {
    @Test
    public void producesNonGtAndHistoricalAliasFixture() throws Exception {
        ExportData d = PackWriterTest.fixture();
        Recipe macerator = d.recipes.stream().filter(r -> r.method.name.equals("Macerator")).findFirst().orElseThrow();
        Goods ore = d.goods.get("i:test:ore:0");
        Goods table = PackWriterTest.goods(d, "i:test:table:0", "Crafting Table", false);
        Goods gear = PackWriterTest.goods(d, "i:test:gear:0", "Gear", false);
        Goods aspect = PackWriterTest.goods(d, "i:test:aspect:0", "Ignis", false);
        Method crafting = new Method("crafting", "Crafting (Shaped)"); crafting.category = "minecraft";
        crafting.fixedDimensions = true; crafting.dimensions[0] = crafting.dimensions[1] = 3; crafting.dimensions[4] = crafting.dimensions[5] = 1;
        crafting.crafter = table; crafting.multi.add(table); d.methods.add(crafting);
        Recipe craft = new Recipe(crafting); craft.gt = false;
        craft.io.add(new Io(0, ore, 0, 1, 100)); craft.io.add(new Io(0, ore, 8, 1, 100)); craft.io.add(new Io(3, gear, 0, 1, 100)); craft.identify(); d.recipes.add(craft);
        Method aspects = new Method("aspects", "Item aspects"); aspects.category = "thaumcraft"; aspects.shapeless = true;
        aspects.crafter = table; aspects.multi.add(table); d.methods.add(aspects);
        Recipe decomposition = new Recipe(aspects); decomposition.gt = false; decomposition.id = "r:asp:" + gear.id;
        decomposition.io.add(new Io(0, gear, 0, 1, 100)); decomposition.io.add(new Io(3, aspect, 0, 2, 100)); d.recipes.add(decomposition);
        d.remaps.put("r:previous-macerator", macerator); d.prepare();
        byte[] bytes = new PackWriter().write(d); Path file = Path.of("build/fixtures/full-export.bin");
        Files.createDirectories(file.getParent()); Files.write(file, bytes);
        Path committed = Path.of("../src/tests/fixtures/full-export.bin");
        if (Files.exists(committed)) assertArrayEquals(Files.readAllBytes(committed), bytes);
    }
}
