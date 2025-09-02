package edu.UOI.plantumlcheck.service.impl;

import comparison.CheckMode;
import comparison.ModelComparator;
import comparison.issues.Difference;
import comparison.issues.IssueLevel;
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
import java.util.stream.Collectors;

@Service
public class CompareServiceImpl implements CompareService {

    @Override
    public RunResult run(Selection sel) {
        try {
            // Parse Java (selected classes)
            IntermediateModel codeModel = parseSelectedJava(sel.workspaceRoot(), sel.selectedFqcns());
            List<String> notices = new ArrayList<>(codeModel.getWarnings());

            IntermediateModel umlModel = null;
            List<Difference> diffs = List.of();

            // Map UI mode -> comparator mode (STRICT / RELAXED / RELAXED_PLUS)
            CheckMode checkMode = switch (sel.mode()) {
                case STRICT -> CheckMode.STRICT;
                case RELAXED_PLUS -> CheckMode.RELAXED_PLUS;
                case RELAXED -> CheckMode.RELAXED;
            };

            if (!sel.codeOnly()) {
                // Parse PlantUML & compare
                umlModel = parsePlantUmlFiles(sel.plantumlFiles());
                notices.addAll(umlModel.getWarnings());
                ModelComparator cmp = new ModelComparator(checkMode);
                diffs = cmp.compare(codeModel, umlModel);
            }

            // --- Roll-up / Summary ---
            int codeCount = sizeSafe(codeModel.getClasses());
            int umlCount  = (umlModel == null) ? 0 : sizeSafe(umlModel.getClasses());
            int analyzed  = codeCount; // we parsed only selected classes

            // STRICT / RELAXED / RELAXED_PLUS consistency:
            // STRICT -> any diff fails
            // RELAXED -> ERROR fails; otherwise, if there is ANY non-relationship diff (even INFO), fail.
            // RELAXED_PLUS -> only ERROR fails
            boolean hasErrors = hasLevel(diffs, IssueLevel.ERROR);

            // Partition by relationship vs non-relationship diffs
            List<Difference> nonRel = new ArrayList<>();
            List<Difference> relOnly = new ArrayList<>();
            for (Difference d : diffs) {
                if (isRelationshipDiff(d)) relOnly.add(d);
                else nonRel.add(d);
            }

            boolean consistent = sel.codeOnly() ||
                    switch (checkMode) {
                        case STRICT -> diffs.isEmpty();
                        case RELAXED -> !hasErrors && nonRel.isEmpty();  // relax mainly on relationships
                        case RELAXED_PLUS -> !hasErrors;                  // pass with warnings
                    };

            // Matches: classes with zero *non-relationship* diffs (so relationship-only diffs don't hurt RELAXED)
            Set<String> codeClassNames = classNamesOf(codeModel);
            Set<String> classesWithDiffs = classNamesMentionedInDiffs(nonRel, codeClassNames);
            int matchCount = Math.max(0, analyzed - classesWithDiffs.size());

            int diffCount = diffs.size();
            Summary sum = new Summary(codeCount, umlCount, analyzed, matchCount, diffCount);

            // Report
            String reportText = ReportPrinter.toText(diffs, checkMode);

            // Generated PlantUML in code-only mode
            String generatedPuml = null;
            if (sel.codeOnly()) {
                generatedPuml = PlantUmlWriter.generate(checkMode, codeModel, null, List.of());
            }

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

    // --- roll-up utilities ---

    private static boolean isRelationshipDiff(Difference d) {
        if (d == null || d.getKind() == null) return false;
        return d.getKind().name().startsWith("RELATIONSHIP_");
    }

    private static boolean hasLevel(List<Difference> diffs, IssueLevel lvl) {
        if (diffs == null || diffs.isEmpty()) return false;
        for (Difference d : diffs) {
            if (d != null && d.getLevel() == lvl) return true;
        }
        return false;
    }

    private static Set<String> classNamesOf(IntermediateModel model) {
        if (model == null || model.getClasses() == null) return Set.of();
        return model.getClasses().stream()
                .map(ClassInfo::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Try to attribute diffs to code classes by parsing Difference.where.
     * Expected formats include "Class#member", "Class#method(...)".
     * For relationship diffs which may not start with a class name, this is conservative.
     */
    private static Set<String> classNamesMentionedInDiffs(List<Difference> diffs, Set<String> knownCodeClasses) {
        Set<String> out = new LinkedHashSet<>();
        if (diffs == null) return out;

        for (Difference d : diffs) {
            if (d == null) continue;
            String where = d.getWhere();
            if (where == null) continue;

            String cls = before(where, '#'); // "Class#..."
            if (cls == null || cls.isBlank()) {
                // Fallback: sometimes there is no '#'
                cls = firstToken(where);
            }
            if (cls != null && knownCodeClasses.contains(cls)) {
                out.add(cls);
            } else {
                // Relationship diffs might include "A -> B"
                for (String k : knownCodeClasses) {
                    if (where.contains(k)) {
                        out.add(k);
                    }
                }
            }
        }
        return out;
    }

    private static String before(String s, char ch) {
        int i = s.indexOf(ch);
        if (i < 0) return null;
        return s.substring(0, i);
    }

    private static String firstToken(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isJavaIdentifierPart(c) && c != '.' ) {
                return s.substring(0, i);
            }
        }
        return s;
    }
}
