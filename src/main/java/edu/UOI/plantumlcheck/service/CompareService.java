package edu.UOI.plantumlcheck.service;

import comparison.issues.Difference;
import java.util.List;

public interface CompareService {

    enum Mode { STRICT, RELAXED, MINIMAL } // formerly RELAXED_PLUS

    record Selection(
            String workspaceRoot,
            List<String> selectedFqcns,
            boolean codeOnly,
            Mode mode,
            List<String> plantumlFiles
    ) {}

    record Summary(
            int codeClasses,
            int umlClasses,
            int analyzedClasses,
            int matches,
            int differences
    ) {}

    record RunResult(
            boolean consistent,
            boolean codeOnly,
            Mode modeUsed,
            Summary summary,
            List<Difference> differences,
            String reportText,
            String generatedPlantUml,
            List<String> notices
    ) {}

    RunResult run(Selection sel);
}
