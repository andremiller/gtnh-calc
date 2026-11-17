package tools.gtnh.calc.export;

import java.util.List;
import java.util.Objects;
import tools.gtnh.calc.export.ExportData.Goods;
import tools.gtnh.calc.export.ExportData.Method;

/** Only electric singleblocks occupy the calculator's voltage-indexed slots. */
final class MachineSelection {
    static void add(Method method, Goods machine, boolean singleblock, boolean electric, int gtTier, List<String> report) {
        if (singleblock && electric) {
            int tier = gtTier - 1;
            if (tier < 0 || tier > 13) {
                report.add("Machine warning: unsupported electric singleblock tier " + machine.id);
                return;
            }
            while (method.single.size() <= tier) method.single.add(null);
            if (method.single.get(tier) == null) method.single.set(tier, machine);
            else report.add("Machine warning: additional electric singleblock at tier " + tier + ": " + machine.id);
        } else if (!method.multi.contains(machine)) {
            // The version-7 'multiblocks' list also holds steam singleblocks with their own calculator rules.
            method.multi.add(machine);
        }
    }

    static void finish(Method method) {
        method.crafter = method.single.stream().filter(Objects::nonNull).findFirst()
            .orElseGet(() -> method.multi.stream().filter(g -> method.name.equals(g.name)).findFirst()
                .orElse(method.multi.isEmpty() ? null : method.multi.get(0)));
        if (method.crafter == null) throw new IllegalStateException("No machine associated with " + method.name);
        // The solver chooses the first alternative for maps without electric singleblocks.
        if (method.multi.remove(method.crafter)) method.multi.add(0, method.crafter);
    }
}
