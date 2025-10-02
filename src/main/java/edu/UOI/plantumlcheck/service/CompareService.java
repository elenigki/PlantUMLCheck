package edu.UOI.plantumlcheck.service;

import comparison.issues.Difference;
import java.util.List;

public interface CompareService {
	
	// Comparison modes from the UI, mapped later to CheckMode in the impl
    enum Mode { STRICT, RELAXED, MINIMAL }

	// Keep last parsed Java code model (useful for UML generation later)
    model.IntermediateModel getLastCodeModel();
	
	// User selection coming from the UI (workspace, classes, mode, UML files...)
    record Selection(
            String workspaceRoot,
            List<String> selectedFqcns,
            boolean codeOnly,
            Mode mode,
            List<String> plantumlFiles
    ) {}

	// Quick summary of results: how many classes matched/differed etc.
    record Summary(
            int codeClasses,
            int umlClasses,
            int analyzedClasses,
            int matches,
            int differences
    ) {}

	// Full run result returned to UI layer
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

	// The main entrypoint: run comparison with given Selection
    RunResult run(Selection sel);
}
