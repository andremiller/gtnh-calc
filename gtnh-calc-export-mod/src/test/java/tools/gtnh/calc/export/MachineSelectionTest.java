package tools.gtnh.calc.export;

import static org.junit.Assert.*;
import java.util.ArrayList;
import org.junit.Test;
import tools.gtnh.calc.export.ExportData.*;

public class MachineSelectionTest {
    private Goods machine(String id, String name) { Goods g = new Goods(id, false); g.name = name; return g; }

    @Test
    public void steamMachinesDoNotDisplaceLvAndMvElectricMachines() {
        Method method = new Method("macerator", "Macerator");
        Goods steam = machine("steam", "Steam Macerator"), highPressure = machine("hp", "High Pressure Steam Macerator");
        Goods basic = machine("lv", "Basic Macerator"), advanced = machine("mv", "Advanced Macerator");
        ArrayList<String> report = new ArrayList<>();
        // Registration order and tier values observed in GTNH 2.9.0's first real export.
        MachineSelection.add(method, steam, true, false, 1, report);
        MachineSelection.add(method, highPressure, true, false, 2, report);
        MachineSelection.add(method, basic, true, true, 1, report);
        MachineSelection.add(method, advanced, true, true, 2, report);
        MachineSelection.finish(method);
        assertSame(basic, method.single.get(0)); assertSame(advanced, method.single.get(1));
        assertSame(basic, method.crafter); assertTrue(method.multi.contains(steam)); assertTrue(method.multi.contains(highPressure));
        assertTrue(report.isEmpty());
    }

    @Test
    public void lastElectricCrafterAtATierMatchesReferenceSelection() {
        Method method = new Method("test", "Test");
        Goods first = machine("first", "First"), last = machine("last", "Last");
        ArrayList<String> report = new ArrayList<>();
        MachineSelection.add(method, first, true, true, 1, report);
        MachineSelection.add(method, last, true, true, 1, report);
        MachineSelection.finish(method);
        assertSame(last, method.single.get(0)); assertSame(last, method.crafter); assertEquals(1, report.size());
    }

    @Test
    public void standardEbfPrecedesAdvancedAlternativesForAutomaticSelection() {
        Method method = new Method("blast", "Electric Blast Furnace");
        Goods volcanus = machine("volcanus", "Volcanus"), ebf = machine("ebf", "Electric Blast Furnace");
        MachineSelection.add(method, volcanus, false, true, -1, new ArrayList<>());
        MachineSelection.add(method, ebf, false, true, -1, new ArrayList<>());
        MachineSelection.finish(method);
        assertSame(ebf, method.crafter); assertSame(ebf, method.multi.get(0)); assertSame(volcanus, method.multi.get(1));
    }

    @Test
    public void refusesUnregisteredIdentitiesWithoutSubstringExceptions() {
        for (String invalid : new String[] {null, "null", "", ":item", "mod:"}) {
            try { RegistryName.parse(invalid); fail("Expected unsupported registry name"); }
            catch (UnsupportedOperationException expected) { assertTrue(expected.getMessage().contains("registry name")); }
        }
        assertEquals("gregtech", RegistryName.parse("gregtech:gt.blockmachines").mod());
        assertEquals("gt.blockmachines", RegistryName.parse("gregtech:gt.blockmachines").path());
    }
}
