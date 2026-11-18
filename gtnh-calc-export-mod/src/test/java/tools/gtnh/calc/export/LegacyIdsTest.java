package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import org.junit.Test;
import tools.gtnh.calc.export.ExportData.Goods;
import tools.gtnh.calc.export.LegacyIds.Slot;
import tools.gtnh.calc.export.LegacyIds.Stack;

public class LegacyIdsTest {
    @Test
    public void reproducesElevenRealLegacyIdsAcrossAllSourceCategories() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("legacy-id-goldens.json")) {
            JsonArray examples = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray();
            assertEquals(11, examples.size());
            for (JsonElement element : examples) {
                JsonObject example = element.getAsJsonObject(); List<List<Slot>> slots = new ArrayList<>();
                for (int i = 0; i < 4; i++) slots.add(new ArrayList<>());
                for (JsonElement e : example.getAsJsonArray("entries")) {
                    JsonObject entry = e.getAsJsonObject(), value = entry.getAsJsonObject("goods");
                    int field = entry.get("field").getAsInt(); Goods goods = new Goods("golden", field == 3 || field == 5);
                    goods.mod = value.get("mod").getAsString(); goods.internal = value.get("internal").getAsString();
                    goods.damage = value.get("damage").getAsInt(); goods.nbt = value.get("nbt").getAsString();
                    slots.get(field - 2).add(new Slot(entry.get("slot").getAsInt(), List.of(new Stack(goods, value.get("amount").getAsInt())), entry.get("probability").getAsDouble()));
                }
                assertEquals(example.get("id").getAsString(), LegacyIds.recipe(example.get("category").getAsString(), example.get("type").getAsString(), slots.get(0), slots.get(1), slots.get(2), slots.get(3)));
            }
        }
    }
    @Test
    public void groupOrderDoesNotAffectIdentityButAlternativesAndQuantitiesDo() {
        Goods a = new Goods("a", false); a.mod = "test"; a.internal = "a";
        Goods b = new Goods("b", false); b.mod = "test"; b.internal = "b";
        String original = LegacyIds.group(List.of(new Stack(a, 1), new Stack(b, 1)));
        assertEquals(original, LegacyIds.group(List.of(new Stack(b, 1), new Stack(a, 1))));
        assertNotEquals(original, LegacyIds.group(List.of(new Stack(a, 2), new Stack(b, 1))));
        assertNotEquals(original, LegacyIds.group(List.of(new Stack(a, 1))));
    }
}
