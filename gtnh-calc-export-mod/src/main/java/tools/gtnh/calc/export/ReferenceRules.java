package tools.gtnh.calc.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.gtnh.calc.export.ExportData.Goods;

/** Selection and text rules ported from this repository's MIT-licensed C# converter. */
final class ReferenceRules {
    private static final Set<String> BANNED = new HashSet<>(resource("item-banlist.tsv"));
    private static final Map<Character, Character> FONT = new HashMap<>();
    private static final List<String> TOOL_ENDINGS = List.of("Wrench Tip", "Spade Head", "Pickaxe Head", "Sense Blade", "Plow Head", "Buzzsaw Blade", "Sword Blade", "Axe Head", "Hoe Head", "Hammer Head", "File Head", "Shovel Head", "Drill Tip");
    static {
        for (String line : resource("font-replacements.tsv")) {
            String[] pair = line.split("\t", 2); FONT.put((char) Integer.parseInt(pair[0], 16), pair[1].charAt(0));
        }
    }
    private static List<String> resource(String name) {
        try (InputStream input = ReferenceRules.class.getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("Missing reference rules " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().filter(s -> !s.isEmpty()).toList();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    static boolean banned(Goods g) {
        if (g.fluid) return false;
        if (BANNED.contains(g.mod + "\t" + g.internal)) return true;
        if (g.mod.equals("gregtech")) return switch (g.internal) {
            case "gt.detrav.metatool.01" -> g.name.contains("Prospector's Scanner");
            case "gt.metaitem.01" -> g.name.equals("Data Orb") ? g.nbt != null && !g.nbt.isEmpty() : Set.of("Writes Research result", "Reads Research result").contains(g.name);
            case "gt.metaitem.02" -> TOOL_ENDINGS.stream().anyMatch(g.name::endsWith);
            case "gt.metaitem.98" -> g.name.equals("Steam Cell");
            default -> false;
        };
        return (g.mod.equals("miscutils") && g.internal.equals("gt.plusplus.metatool.01")) && Set.of("Angle Grinder", "Automatic Snips").contains(g.name);
    }
    static String font(String value) {
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) result.append(FONT.getOrDefault(c, c));
        return result.toString();
    }
    static void tooltip(Goods g) {
        if (!g.tooltip.isEmpty() && g.tooltip.get(0).startsWith(g.name)) g.tooltip.remove(0);
        // Match the reference's backwards scan, which leaves the last tooltip line alone.
        for (int i = g.tooltip.size() - 2; i >= 0; i--) {
            String text = g.tooltip.get(i).toLowerCase(Locale.ROOT);
            if ((text.contains("press") || text.contains("hold")) && (text.contains("ctrl") || text.contains("shift") || text.contains("control"))) g.tooltip.remove(i);
        }
        while (!g.tooltip.isEmpty() && g.tooltip.get(0).isEmpty()) g.tooltip.remove(0);
    }
}
