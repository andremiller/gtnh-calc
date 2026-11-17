package tools.gtnh.calc.export;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

final class MinecraftText {
    static String plain(String text) { return text == null ? "" : text.replaceAll("§.", ""); }

    static String html(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(), part = new StringBuilder();
        Set<Character> formats = new LinkedHashSet<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                emit(out, part, formats);
                char code = Character.toLowerCase(text.charAt(++i));
                if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) formats.clear();
                if ("0123456789abcdefklmno".indexOf(code) >= 0) formats.add(code);
            } else if (c == '\n') { emit(out, part, formats); out.append("<br>"); formats.clear(); }
            else part.append(c);
        }
        emit(out, part, formats);
        return out.toString();
    }

    private static void emit(StringBuilder out, StringBuilder part, Set<Character> formats) {
        if (part.length() == 0) return;
        if (!formats.isEmpty()) out.append("<span class=\"").append(formats.stream().map(c -> "fmt-" + c).collect(Collectors.joining(" "))).append("\">");
        out.append(part.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"));
        if (!formats.isEmpty()) out.append("</span>");
        part.setLength(0);
    }
}
