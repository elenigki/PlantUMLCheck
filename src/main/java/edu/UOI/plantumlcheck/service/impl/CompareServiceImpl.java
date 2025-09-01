package edu.UOI.plantumlcheck.service.impl;

import comparison.CheckMode;
import comparison.ModelComparator;
import comparison.issues.Difference;
import comparison.report.ReportPrinter;
import comparison.export.PlantUmlWriter;
import edu.UOI.plantumlcheck.service.CompareService;
import model.ClassInfo;
import model.IntermediateModel;
import model.ModelSource;
import model.Relationship;
import parser.code.JavaSourceParser;
import parser.uml.PlantUMLParser;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class CompareServiceImpl implements CompareService {

    @Override
    public RunResult run(Selection sel) {
        try {
            // 1) Parse Java for selected classes
            IntermediateModel codeModel = parseSelectedJava(sel.workspaceRoot(), sel.selectedFqcns());
            List<String> notices = new ArrayList<>(codeModel.getWarnings());

            IntermediateModel umlModel = null;
            List<Difference> diffs = List.of();
            CheckMode checkMode = (sel.mode() == Mode.RELAXED) ? CheckMode.RELAXED : CheckMode.STRICT;

            // 2) Parse PlantUML + compare (when not code-only)
            if (!sel.codeOnly()) {
                umlModel = parsePlantUmlFiles(sel.plantumlFiles());
                notices.addAll(umlModel.getWarnings());
                ModelComparator cmp = new ModelComparator(checkMode);
                diffs = cmp.compare(codeModel, umlModel);
            }

            // 3) Summary stats
            int codeCount = sizeSafe(codeModel.getClasses());
            int umlCount  = (umlModel == null) ? 0 : sizeSafe(umlModel.getClasses());
            int analyzed  = codeCount; // we parsed only selected classes
            int diffCount = diffs.size();
            int matchCount = Math.max(0, analyzed - diffCount);

            Summary sum = new Summary(codeCount, umlCount, analyzed, matchCount, diffCount);

            // 4) Report
            String reportText = ReportPrinter.toText(diffs, checkMode);

            // 5) Generated PlantUML in code-only mode
            String generatedPuml = null;
            if (sel.codeOnly()) {
                generatedPuml = PlantUmlWriter.generate(checkMode, codeModel, null, List.of());
            }

            boolean consistent = sel.codeOnly() || diffs.isEmpty();

            return new RunResult(consistent, sel.codeOnly(), sel.mode(), sum, diffs, reportText, generatedPuml, notices);

        } catch (IOException ex) {
            return new RunResult(
                    false, sel.codeOnly(), sel.mode(),
                    new Summary(0,0,0,0,0),
                    List.of(),
                    "ERROR: " + ex.getMessage(),
                    null,
                    List.of("I/O error: " + ex.getMessage())
            );
        }
    }

    // --- helpers ---

    private static IntermediateModel parseSelectedJava(String root, List<String> fqcns) throws IOException {
        JavaSourceParser parser = new JavaSourceParser();
        List<File> files = new ArrayList<>();

        for (String fqcn : fqcns) {
            String rel = fqcn.contains(".")
                    ? fqcn.replace('.', '/') + ".java"
                    : fqcn + ".java";
            Path p = Paths.get(root).resolve(rel);

            if (!Files.exists(p)) {
                // fallback: scan for a filename match (handles default package/alternate layout)
                try (var stream = Files.walk(Paths.get(root))) {
                    Optional<Path> hit = stream
                            .filter(pp -> pp.getFileName().toString().equals(simpleName(fqcn) + ".java"))
                            .findFirst();
                    if (hit.isPresent()) p = hit.get();
                }
            }
            if (Files.exists(p)) files.add(p.toFile());
        }
        return parser.parse(files);
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return (idx >= 0) ? fqcn.substring(idx + 1) : fqcn;
    }

    private static IntermediateModel parsePlantUmlFiles(List<String> paths) throws IOException {
        IntermediateModel combined = new IntermediateModel(ModelSource.PLANTUML_SCRIPT);
        PlantUMLParser parser = new PlantUMLParser();

        if (paths != null) {
            for (String s : paths) {
                if (s == null || s.isBlank()) continue;
                IntermediateModel m = parser.parse(new File(s));
                if (m.getClasses() != null) for (ClassInfo c : m.getClasses()) combined.addClass(c);
                if (m.getRelationships() != null) for (Relationship r : m.getRelationships()) combined.addRelationship(r);
                combined.getWarnings().addAll(m.getWarnings());
            }
        }
        return combined;
    }

    private static int sizeSafe(List<?> list) { return (list == null) ? 0 : list.size(); }
}
