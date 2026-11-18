package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import tools.gtnh.calc.export.ExportData.*;
import tools.gtnh.calc.export.LegacyIds.Slot;
import tools.gtnh.calc.export.LegacyIds.Stack;

public class ReferenceRulesTest {
    private Goods goods(ExportData data, String id, String name) {
        Goods g = new Goods(id, false); g.name = name; g.mod = "test"; g.internal = id; data.goods.put(id, g); return g;
    }
    @Test
    public void banlistAndFontRulesMatchReferenceExamples() {
        Goods g = new Goods("test", false); g.mod = "Forestry"; g.internal = "beeDroneGE"; g.name = "Drone";
        assertTrue(ReferenceRules.banned(g));
        g.mod = "gregtech"; g.internal = "gt.metaitem.01"; g.name = "Data Orb"; g.nbt = ""; assertFalse(ReferenceRules.banned(g));
        g.nbt = "{research:1}"; assertTrue(ReferenceRules.banned(g));
        g.mod = "bartworks"; g.internal = "gt.bwMetaGeneratedtoolHeadHammer"; assertFalse(ReferenceRules.banned(g));
        assertEquals("H₀⚡", ReferenceRules.font("H\ue01a\ue00a"));
    }
    @Test
    public void prunesAlternativesAfterIdentityAndPreservesAllFluids() {
        ExportData data = new ExportData(); RawExport raw = new RawExport(data);
        Method method = raw.method("minecraft", "Crafting (Shaped)", false, 3,3,0,0,1,1,0,0);
        Goods a = goods(data, "a", "a"), b = goods(data, "b", "b");
        Goods unusedFluid = new Goods("f:test:unused", true); unusedFluid.name = "Unused"; unusedFluid.mod = "test"; unusedFluid.internal = "unused"; data.goods.put(unusedFluid.id, unusedFluid);
        Recipe recipe = new Recipe(method); recipe.gt = false;
        List<Slot> in = List.of(new Slot(4, List.of(new Stack(a, 2), new Stack(b, 2)), 1));
        raw.add("minecraft", "Crafting (Shaped)", recipe, in, List.of(), List.of(new Slot(0, List.of(new Stack(a, 1)), 1)), List.of());
        String legacy = recipe.id; raw.process(); data.prepare();
        assertEquals(legacy, recipe.id); assertSame(a, recipe.io.get(0).goods()); assertEquals(2, recipe.io.get(0).amount());
        assertEquals(4, recipe.io.get(0).slot()); assertFalse(data.goods.containsKey("b")); assertTrue(data.goods.containsKey(unusedFluid.id));
        assertArrayEquals(new int[] {3,3,0,0,1,1,0,0}, method.dimensions);
    }
    @Test
    public void rejectsBannedOutputsAndEmptyGroupsAndCleansMetadata() {
        ExportData data = new ExportData(); RawExport raw = new RawExport(data);
        Method method = raw.method("gregtech", "Test (LV)", true, 1,1,0,0,1,1,0,0);
        Goods good = goods(data, "good", "Good"), banned = goods(data, "bad", "Banned"); banned.banned = true;
        Recipe invalid = new Recipe(method), valid = new Recipe(method);
        raw.add("gregtech", "Test (LV)", invalid, List.of(new Slot(0, List.of(new Stack(banned, 1)), 1)), List.of(), List.of(new Slot(0,List.of(new Stack(good,1)),1)), List.of());
        valid.metadata.put("no_gas", 1.0); valid.metadata.put("cleanroom", 0.0); valid.metadata.put("coil_heat", 1800.0);
        raw.add("gregtech", "Test (LV)", valid, List.of(), List.of(), List.of(new Slot(0,List.of(new Stack(good,1)),1)), List.of());
        raw.process(); assertEquals(List.of(valid), data.recipes); assertEquals(Map.of("coil_heat", 1800.0), valid.metadata);
        assertEquals("Test", method.name);
    }
    @Test
    public void aspectSelectionUsesFilteredRecipesAndRetainedGoods() {
        ExportData data = new ExportData(); RawExport raw = new RawExport(data);
        Method method = raw.method("minecraft", "Crafting", true, 1,1,0,0,1,1,0,0);
        Goods a = goods(data, "a", "a"), pruned = goods(data, "b", "b");
        Goods banned = goods(data, "bad", "bad"); banned.banned = true;
        Goods machine = goods(data, "machine", "machine"); method.multi.add(machine);
        Goods container = goods(data, "container", "container"), empty = goods(data, "empty", "empty");
        container.keep = empty.keep = true; container.empty = empty;
        Goods aspect = goods(data, "aspect", "aspect"); aspect.internal = "aspect";
        Goods unused = goods(data, "unused", "unused");
        Goods fluid = new Goods("fluid", true); fluid.name = "Fluid"; fluid.internal = "fluid"; fluid.keep = true; data.goods.put(fluid.id, fluid);
        Recipe recipe = new Recipe(method);
        List<Slot> inputs = List.of(new Slot(0, List.of(new Stack(a, 1), new Stack(pruned, 1), new Stack(banned, 1)), 1));
        raw.add("minecraft", "Crafting", recipe, inputs, List.of(), List.of(new Slot(0, List.of(new Stack(a, 1)), 1)), List.of());
        String id = recipe.id; raw.process(); raw.releaseSources();
        assertEquals(Set.of(a, machine, container, empty, aspect), data.aspectCandidates());
        assertFalse(data.aspectCandidates().contains(unused)); assertEquals(id, recipe.id);
        assertTrue(raw.recipes.isEmpty()); assertTrue(raw.bases.isEmpty());
        assertSame(a, recipe.io.get(0).goods());
    }
}
