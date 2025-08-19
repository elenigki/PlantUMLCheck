package comparison.issues;

public enum IssueLevel {
    ERROR,      // must fix to pass
    WARNING,    // notable mismatch
    INFO,       // harmless divergence
    SUGGESTION  // cosmetic improvement
}
