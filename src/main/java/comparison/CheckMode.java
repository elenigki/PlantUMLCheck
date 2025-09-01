package comparison;

public enum CheckMode {
    STRICT,   // exact match required
    RELAXED,  // allows compatible substitutions
    RELAXED_PLUS //// RELAXED, but even gentler for attributes / methods
}
