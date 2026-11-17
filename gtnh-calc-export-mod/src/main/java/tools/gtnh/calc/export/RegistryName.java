package tools.gtnh.calc.export;

/** A registry identity must contain an owner and a path; String.valueOf(null) is not an identity. */
record RegistryName(String mod, String path) {
    static RegistryName parse(String value) {
        int colon = value == null ? -1 : value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1)
            throw new UnsupportedOperationException("Missing or malformed registry name: " + value);
        return new RegistryName(value.substring(0, colon), value.substring(colon + 1));
    }

    @Override
    public String toString() { return mod + ":" + path; }
}
