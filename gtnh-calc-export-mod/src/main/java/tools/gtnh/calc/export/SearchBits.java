package tools.gtnh.calc.export;

final class SearchBits {
    static int[] of(String... text) {
        int[] bits = new int[4];
        for (String value : text) {
            if (value == null) continue;
            int previous = -1, beforePrevious = -1;
            for (char ch : value.toCharArray()) {
                int c = ch >= '0' && ch <= '9' ? ch - '0'
                    : ch >= 'a' && ch <= 'z' ? ch - 'a' + 10
                    : ch >= 'A' && ch <= 'Z' ? ch - 'A' + 10
                    : ch >= '\u2080' && ch <= '\u2089' ? ch - '\u2080' : -1;
                if (c < 0) { previous = beforePrevious = -1; continue; }
                set(bits, 92 + c);
                if (previous >= 0) {
                    set(bits, (previous * 36 + c) % 92);
                    if (beforePrevious >= 0) set(bits, ((beforePrevious * 36 + previous) * 36 + c) % 92);
                }
                beforePrevious = previous;
                previous = c;
            }
        }
        return bits;
    }

    static void merge(int[] into, int[] other) {
        for (int i = 0; i < 4; i++) into[i] |= other[i];
    }

    private static void set(int[] bits, int bit) { bits[bit / 32] |= 1 << (bit % 32); }
}
