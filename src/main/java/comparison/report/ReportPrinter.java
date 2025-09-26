package comparison.report;

import comparison.CheckMode;
import comparison.issues.Difference;
import comparison.issues.IssueLevel;

import java.util.*;
import java.util.stream.Collectors;

/** Turns differences into simple text or markdown with "Expected X; UML has Y" phrasing. */
public final class ReportPrinter {
    private ReportPrinter() {}

    private static final List<IssueLevel> SEVERITY_ORDER =
            List.of(IssueLevel.ERROR, IssueLevel.WARNING, IssueLevel.SUGGESTION, IssueLevel.INFO);

    // -------- Plain text --------
    public static String toText(List<Difference> diffs, CheckMode mode) {
        if (diffs == null) diffs = List.of();

        Map<IssueLevel, List<Difference>> byLevel = diffs.stream()
                .collect(Collectors.groupingBy(Difference::getLevel));

        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("UML ↔ Code Consistency Report").append(nl);
        sb.append("Mode: ").append(mode).append(nl);

        int errors = byLevel.getOrDefault(IssueLevel.ERROR, List.of()).size();
        int warns  = byLevel.getOrDefault(IssueLevel.WARNING, List.of()).size();
        int suggs  = byLevel.getOrDefault(IssueLevel.SUGGESTION, List.of()).size();
        int infos  = byLevel.getOrDefault(IssueLevel.INFO, List.of()).size();

        sb.append(String.format("Summary: %d ERROR, %d WARNING, %d SUGGESTION, %d INFO",
                errors, warns, suggs, infos)).append(nl);
        sb.append("Pass: ").append(errors == 0 ? "YES" : "NO").append(nl).append(nl);

        for (IssueLevel level : SEVERITY_ORDER) {
            List<Difference> group = new ArrayList<>(byLevel.getOrDefault(level, List.of()));
            if (group.isEmpty()) continue;

            sb.append("[").append(level).append("]").append(nl);
            group.sort(Comparator.comparing(Difference::getWhere)
                                 .thenComparing(d -> d.getKind().name()));
            for (Difference d : group) {
                sb.append(" - ").append(d.getWhere())
                  .append(" :: ").append(d.getKind())
                  .append(" :: ").append(d.getSummary());

                // friendlier detail
                sb.append(" | ").append(phrased(d));

                if (nz(d.getTip())) {
                    sb.append(" | TIP: ").append(d.getTip());
                }
                sb.append(nl);
            }
            sb.append(nl);
        }
        return sb.toString();
    }

    // -------- Markdown --------
    public static String toMarkdown(List<Difference> diffs, CheckMode mode) {
        if (diffs == null) diffs = List.of();
        Map<IssueLevel, List<Difference>> byLevel = diffs.stream()
                .collect(Collectors.groupingBy(Difference::getLevel));

        StringBuilder sb = new StringBuilder();
        sb.append("# UML ↔ Code Consistency Report\n");
        sb.append("**Mode:** ").append(mode).append("\n\n");

        int errors = byLevel.getOrDefault(IssueLevel.ERROR, List.of()).size();
        int warns  = byLevel.getOrDefault(IssueLevel.WARNING, List.of()).size();
        int suggs  = byLevel.getOrDefault(IssueLevel.SUGGESTION, List.of()).size();
        int infos  = byLevel.getOrDefault(IssueLevel.INFO, List.of()).size();

        sb.append(String.format("**Summary:** %d ERROR, %d WARNING, %d SUGGESTION, %d INFO  \n",
                errors, warns, suggs, infos));
        sb.append("**Pass:** ").append(errors == 0 ? "✅ YES" : "❌ NO").append("\n\n");

        for (IssueLevel level : SEVERITY_ORDER) {
            List<Difference> group = new ArrayList<>(byLevel.getOrDefault(level, List.of()));
            if (group.isEmpty()) continue;

            sb.append("## ").append(level).append("\n\n");
            sb.append("| Where | Kind | Summary | Detail | Tip |\n");
            sb.append("|---|---|---|---|---|\n");
            group.sort(Comparator.comparing(Difference::getWhere)
                                 .thenComparing(d -> d.getKind().name()));
            for (Difference d : group) {
                sb.append("| ")
                  .append(escape(d.getWhere())).append(" | ")
                  .append(escape(d.getKind().name())).append(" | ")
                  .append(escape(d.getSummary())).append(" | ")
                  .append(escape(phrased(d))).append(" | ")
                  .append(escape(z(d.getTip()))).append(" |\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // --- friendlier detail line ---
    private static String phrased(Difference d) {
        String u = z(d.getUml());
        String c = z(d.getCode());
        boolean umlMissing  = "missing".equalsIgnoreCase(u);
        boolean codeMissing = "missing".equalsIgnoreCase(c);

        switch (d.getKind()) {
            case RELATIONSHIP_MISMATCH:
                if (!umlMissing && !codeMissing) {
                    return "Expected " + c + "; UML has " + u;
                } else if (umlMissing && !codeMissing) {
                    return "Expected " + c + "; UML is missing it";
                } else if (!umlMissing && codeMissing) {
                    return "UML shows " + u + "; code has none";
                }
                break;

            case RELATIONSHIP_MISSING_IN_UML:
                return "Expected " + c + "; UML is missing it";

            case RELATIONSHIP_MISSING_IN_CODE:
                return "UML shows " + u + "; code has none";

            case ATTRIBUTE_MISMATCH:
            case METHOD_MISMATCH:
                if (!"—".equals(u) && !"—".equals(c)) {
                    return "Expected " + c + "; UML has " + u;
                } else if ("—".equals(u) && !"—".equals(c)) {
                    return "UML omits a detail; code is " + c;
                } else if (!"—".equals(u) && "—".equals(c)) {
                    return "UML specifies " + u + "; code has none";
                }
                break;

            default:
                // generic
        }
        return "Expected " + c + "; UML has " + u;
    }

    // --- utils ---
    private static boolean nz(String s) { return s != null && !s.isBlank(); }
    private static String z(String s) { return nz(s) ? s.trim() : "—"; }
    private static String escape(String s) {
        if (s == null) return "—";
        return s.replace("|", "\\|");
    }
}
