package comparison.report;

import comparison.CheckMode;
import comparison.issues.Difference;
import comparison.issues.IssueLevel;
import comparison.issues.IssueKind;

import java.util.*;
import java.util.stream.Collectors;

/** Turns differences into simple text or markdown. */
public final class ReportPrinter {
    private ReportPrinter() {}

    // Prints a plain text report
    public static String toText(List<Difference> diffs, CheckMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("UML Consistency Report").append("\n");
        sb.append("Mode: ").append(mode).append("\n");
        sb.append("Total: ").append(n(diffs.size())).append("\n\n");

        Map<IssueLevel, List<Difference>> byLevel = groupByLevel(diffs);

        for (IssueLevel level : sortedLevels()) {
            List<Difference> list = byLevel.getOrDefault(level, List.of());
            if (list.isEmpty()) continue;
            sb.append(level.name()).append(" (").append(list.size()).append(")").append("\n");
            for (Difference d : sort(list)) {
                sb.append(" - ").append(oneLine(d)).append("\n");
            }
            sb.append("\n");
        }
        if (diffs.isEmpty()) sb.append("✔ UML passed the check.\n");
        return sb.toString();
    }

    // Prints a markdown report
    public static String toMarkdown(List<Difference> diffs, CheckMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("# UML Consistency Report\n\n");
        sb.append("**Mode:** ").append(mode).append("  \n");
        sb.append("**Total issues:** ").append(diffs.size()).append("\n\n");

        Map<IssueLevel, List<Difference>> byLevel = groupByLevel(diffs);

        for (IssueLevel level : sortedLevels()) {
            List<Difference> list = byLevel.getOrDefault(level, List.of());
            if (list.isEmpty()) continue;

            sb.append("## ").append(level.name()).append(" (").append(list.size()).append(")\n\n");
            sb.append("| Where | Kind | Summary | UML | Code | Tip |\n");
            sb.append("|---|---|---|---|---|---|\n");
            for (Difference d : sort(list)) {
                sb.append("| ")
                  .append(escape(d.getWhere())).append(" | ")
                  .append(escape(d.getKind().name())).append(" | ")
                  .append(escape(d.getSummary())).append(" | ")
                  .append(escape(nv(d.getUml()))).append(" | ")
                  .append(escape(nv(d.getCode()))).append(" | ")
                  .append(escape(nv(d.getTip()))).append(" |\n");
            }
            sb.append("\n");
        }

        if (diffs.isEmpty()) {
            sb.append("> ✅ UML passed the check.\n");
        }
        return sb.toString();
    }

    // --- tiny helpers ---

    private static String oneLine(Difference d) {
        // e.g. "Customer.attr:email — ATTRIBUTE_MISMATCH — Attribute type mismatch — UML:String | CODE:double"
        return d.getWhere() + " — " + d.getKind() + " — " + d.getSummary()
                + " — UML:" + nv(d.getUml()) + " | CODE:" + nv(d.getCode());
    }

    private static Map<IssueLevel, List<Difference>> groupByLevel(List<Difference> diffs) {
        return diffs.stream().collect(Collectors.groupingBy(Difference::getLevel, Collectors.toList()));
    }

    private static List<Difference> sort(List<Difference> in) {
        return in.stream()
                .sorted(Comparator
                        .comparing(Difference::getKind, Comparator.comparing(Enum::name))
                        .thenComparing(Difference::getWhere))
                .collect(Collectors.toList());
    }

    private static List<IssueLevel> sortedLevels() {
        // order: ERROR, WARNING, INFO, SUGGESTION
        return List.of(IssueLevel.ERROR, IssueLevel.WARNING, IssueLevel.INFO, IssueLevel.SUGGESTION);
    }

    private static String nv(String s) { return (s == null || s.isBlank()) ? "—" : s.trim(); }
    private static String n(int i) { return String.valueOf(i); }
    private static String escape(String s) {
        if (s == null) return "—";
        return s.replace("|","\\|");
    }
    
    /***
     * Example usage (CLI or test)
      ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(codeModel, umlModel);

		System.out.println(ReportPrinter.toText(diffs, CheckMode.RELAXED));
		// or
		String md = ReportPrinter.toMarkdown(diffs, CheckMode.RELAXED);
		// write md to a file if you like

     * 
     */
}
