package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import tools.gtnh.calc.export.ExportData.*;

public class PreviousPackTest {
    private PreviousPack previous(ExportData data) throws Exception {
        return PreviousPack.decode(new GZIPInputStream(new ByteArrayInputStream(new PackWriter().write(data))).readAllBytes());
    }
    private Recipe first(ExportData d) { return d.recipes.get(0); }

    @Test
    public void unchangedRecipesNeedNoAliases() throws Exception {
        ExportData d = PackWriterTest.fixture(); previous(d).apply(d); assertTrue(d.remaps.isEmpty());
    }
    @Test
    public void matchesChangedQuantityAndCarriesAliasesForward() throws Exception {
        ExportData old = PackWriterTest.fixture(); String oldId = first(old).id;
        old.remaps.put("r:ancient", first(old)); PreviousPack prior = previous(old);
        ExportData current = PackWriterTest.fixture(); Recipe r = first(current);
        Io entry = r.io.get(0); r.io.set(0, new Io(entry.type(), entry.goods(), entry.slot(), entry.amount() + 2, entry.probability())); r.identify();
        prior.apply(current); assertSame(r, current.remaps.get(oldId)); assertSame(r, current.remaps.get("r:ancient"));
        assertTrue(current.report.stream().anyMatch(s -> s.contains("fallback matches=1")));
    }
    @Test
    public void changedRecipeIdsMatchWithoutRestoringDeletedRecipes() throws Exception {
        ExportData old = PackWriterTest.fixture(); PreviousPack prior = previous(old); String id = first(old).id;
        ExportData current = PackWriterTest.fixture(); first(current).id = "r:new"; prior.apply(current);
        assertSame(first(current), current.remaps.get(id));
        current = PackWriterTest.fixture(); current.recipes.clear(); prior.apply(current); assertTrue(current.remaps.isEmpty());
    }
    @Test
    public void namespacesAndZeroCountCatalystsUseReferenceFallbacks() throws Exception {
        ExportData old = PackWriterTest.fixture(); Recipe oldRecipe = old.recipes.stream().filter(r -> r.method.name.equals("Electric Blast Furnace")).findFirst().orElseThrow();
        Io source = oldRecipe.io.get(0); Goods original = (Goods) source.goods();
        Goods renamed = new Goods(original.id.replace("i:test:", "i:oldmod:"), false);
        renamed.name = original.name; renamed.mod = "oldmod"; renamed.internal = original.internal;
        old.goods.put(renamed.id, renamed); oldRecipe.io.set(0, new Io(source.type(), renamed, source.slot(), source.amount(), source.probability())); oldRecipe.id = "r:old";
        PreviousPack prior = previous(old); ExportData current = PackWriterTest.fixture();
        Recipe r = current.recipes.stream().filter(x -> x.method.name.equals("Electric Blast Furnace")).findFirst().orElseThrow();
        Goods corrected = new Goods(original.id.replace("i:test:", "i:gregtech:"), false);
        corrected.mod = "gregtech"; corrected.internal = original.internal; corrected.name = original.name; current.goods.put(corrected.id, corrected);
        Io io = r.io.get(0); r.io.set(0, new Io(io.type(), corrected, io.slot(), io.amount(), io.probability()));
        r.io.removeIf(p -> p.type() < 3 && p.amount() == 0); r.id = "r:current";
        prior.apply(current); assertSame(r, current.remaps.get("r:old"));
    }
    @Test
    public void ambiguousMatchesUseStableLastRecipeAndReportThem() throws Exception {
        ExportData old = PackWriterTest.fixture(); String id = first(old).id; PreviousPack prior = previous(old);
        ExportData current = PackWriterTest.fixture(); Recipe r = first(current); r.id = "r:a";
        Recipe duplicate = new Recipe(r.method); duplicate.io.addAll(r.io); duplicate.id = "r:z"; current.recipes.add(duplicate);
        prior.apply(current); assertSame(duplicate, current.remaps.get(id)); assertTrue(current.report.stream().anyMatch(s -> s.startsWith("REMAP ambiguous")));
    }
    @Test
    public void acceptsVersionSixRecipeLayoutAndRejectsBadPointersAndVersions() throws Exception {
        ExportData data = PackWriterTest.fixture(); byte[] bytes = new GZIPInputStream(new ByteArrayInputStream(new PackWriter().write(data))).readAllBytes();
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); int recipes = b.getInt(20);
        for (int i = 0; i < b.getInt(recipes * 4); i++) {
            int p = b.getInt((recipes + 1 + i) * 4);
            for (int j = 0; j < 4; j++) b.putInt((p + j) * 4, b.getInt((p + 4 + j) * 4));
        }
        b.putInt(0, 6); assertEquals(2, PreviousPack.decode(bytes).recipes.size());
        b.putInt(0, 1); expectInvalid(bytes);
        b.putInt(0, 7); b.putInt(20, Integer.MAX_VALUE); expectInvalid(bytes);
        expectInvalid(new byte[8]);
    }
    private void expectInvalid(byte[] bytes) throws Exception {
        try { PreviousPack.decode(bytes); fail("Expected invalid previous export"); }
        catch (IOException expected) { assertFalse(expected.getMessage().isEmpty()); }
    }
}
