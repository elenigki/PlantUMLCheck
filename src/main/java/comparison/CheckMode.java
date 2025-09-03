package comparison;

public enum CheckMode {
    STRICT,   // exact match required
    RELAXED,  // allows compatible substitutions
    MINIMAL //// RELAXED, but even gentler for attributes / methods
}
