package edu.UOI.plantumlcheck.service;

import comparison.issues.Difference;

import java.util.List;

public interface CompareService {

    enum Mode { STRICT, RELAXED }

    record Selection(
            String workspaceRoot,        // absolute path to workspace root where code lives
            List<String> selectedFqcns,  // FQCNs chosen on /select (e.g., com.example.Account)
            boolean codeOnly,
            Mode mode,
            List<String> plantumlFiles   // absolute paths to uploaded PlantUML files (may be empty)
    ) {}

    record Summary(
            int codeClasses, int umlClasses, int analyzedClasses,
            int matches, int differences
    ) {}

    record RunResult(
            boolean consistent,
            boolean codeOnly,
            Mode modeUsed,
            Summary summary,
            List<Difference> differences,
            String textReport,           // ReportPrinter.toText(...)
            String generatedPlantUml,    // non-null in code-only (generated UML). null in compare mode (for now)
            List<String> notices         // parser warnings etc
    ) {}

    RunResult run(Selection selection);
}
