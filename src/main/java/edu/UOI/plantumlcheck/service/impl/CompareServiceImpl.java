package edu.UOI.plantumlcheck.service.impl;

import comparison.CheckMode;
import comparison.ModelComparator;
import comparison.issues.Difference;
import comparison.issues.IssueLevel;
import comparison.report.ReportPrinter;
import edu.UOI.plantumlcheck.service.CompareService;
import generator.PlantUMLGenerator;
import model.ClassInfo;
import model.IntermediateModel;
import model.ModelSource;
import model.Relationship;
import org.springframework.stereotype.Service;
import parser.code.JavaSourceParser;
import parser.uml.PlantUMLParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class CompareServiceImpl implements CompareService {

    // Cache last parsed code model so we can export UML later if needed
    private IntermediateModel lastCodeModel;

    @Override
    public RunResult run(Selection sel) {
        try {
            // Parse Java files to code-side model
            IntermediateModel codeModel = parseSelectedJava(sel.workspaceRoot(), sel.selectedFqcns());
            this.lastCodeModel = codeModel; // <-- expose via getter
            List<String> notices = new ArrayList<>(codeModel.getWarnings());

            IntermediateModel umlModel = null;
            List<Difference> diffs = List.of();

            // Map UI mode (STRICT/RELAXED/MINIMAL) to comparator CheckMode
            CheckMode checkMode = switch (sel.mode()) {
                case STRICT  -> CheckMode.STRICT;
                case RELAXED -> CheckMode.RELAXED;
                case MINIMAL -> CheckMode.MINIMAL;
            };

			// Parse PlantUML files if not in code-only mode
            if (!sel.codeOnly()) {
                umlModel = parsePlantUmlFiles(sel.plantumlFiles());
                notices.addAll(umlModel.getWarnings());
				// Compare models
                ModelComparator cmp = new ModelComparator(checkMode);
                diffs = cmp.compare(codeModel, umlModel);
            }

            // Build summary of results
            int codeCount = sizeSafe(codeModel.getClasses());
            int umlCount  = (umlModel == null) ? 0 : sizeSafe(umlModel.getClasses());
            int analyzed  = codeCount;

            boolean hasErrors = hasLevel(diffs, IssueLevel.ERROR);

            List<Difference> nonRel = new ArrayList<>();
            for (Difference d : diffs) {
                if (!isRelationshipDiff(d)) nonRel.add(d);
            }

            boolean consistent = sel.codeOnly() ||
                    switch (checkMode) {
                        case STRICT  -> diffs.isEmpty();
                        case RELAXED -> !hasErrors && nonRel.isEmpty();
                        case MINIMAL -> !hasErrors; // pass with warnings
                    };

            Set<String> codeClassNames = classNamesOf(codeModel);
            Set<String> classesWithNonRelDiffs = classNamesMentionedInDiffs(nonRel, codeClassNames);
            int matchCount = Math.max(0, analyzed - classesWithNonRelDiffs.size());

            int diffCount = diffs.size();
            Summary sum = new Summary(codeCount, umlCount, analyzed, matchCount, diffCount);

			// Create readable report in natural languange
            String reportText = ReportPrinter.toText(diffs, checkMode);

            // Generate PlantUML only for code-only runs, straight from the official code model.
            String generatedPuml = null;
            if (sel.codeOnly()) {
                generatedPuml = new PlantUMLGenerator().generate(codeModel);
            }
			
			// Return all results back to the controller
            return new RunResult(consistent, sel.codeOnly(), sel.mode(), sum, diffs, reportText, generatedPuml, notices);

        } catch (IOException ex) {
			// Error fallback result
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

    @Override
    public IntermediateModel getLastCodeModel() {
        return lastCodeModel;
    }

    // --- helpers (file parsing, utilities) ---

    private static IntermediateModel parseSelectedJava(String root, List<String> fqcns) throws IOException {
        JavaSourceParser parser = new JavaSourceParser();
        List<File> files = new ArrayList<>();
        if (fqcns != null) {
            for (String fqcn : fqcns) {
                String rel = fqcn.contains(".") ? fqcn.replace('.', '/') + ".java" : fqcn + ".java";
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
        Set<String> out = new LinkedHashSet<>();
        for (ClassInfo c : model.getClasses()) {
            if (c != null && c.getName() != null) out.add(c.getName());
        }
        return out;
    }

    private static Set<String> classNamesMentionedInDiffs(List<Difference> diffs, Set<String> knownCodeClasses) {
        Set<String> out = new LinkedHashSet<>();
        if (diffs == null) return out;
        for (Difference d : diffs) {
            if (d == null) continue;
            String where = d.getWhere();
            if (where == null) continue;
            String cls = before(where, '#');
            if (cls == null || cls.isBlank()) cls = firstToken(where);
            if (cls != null && knownCodeClasses.contains(cls)) out.add(cls);
            else for (String k : knownCodeClasses) if (where.contains(k)) out.add(k);
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
            if (!Character.isJavaIdentifierPart(c) && c != '.' ) return s.substring(0, i);
        }
        return s;
    }
}
