package scenarios;

import org.junit.jupiter.api.*;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// ===== Adjust these imports to your actual packages =====
import model.IntermediateModel;
import parser.code.JavaSourceParser;
import parser.uml.PlantUMLParser;
import comparison.ModelComparator;
import comparison.CheckMode;
import comparison.issues.Difference;
import comparison.issues.IssueLevel;
// ========================================================

/**
 * Handmade-style runner for the scenarios pack.
 * Scenarios live under: src/test/resources/scenarios/
 *
 * Robust Java parser wiring:
 * - tries JavaSourceParser#parse(List<File>) first (when present)
 * - otherwise uses JavaSourceParser#parse(File dir)
 */
public class ScenariosTest {

    private static final Path BASE;

    static {
        try {
            // Resolve /scenarios on the TEST classpath (never depends on working dir)
            var url = Objects.requireNonNull(
                    ScenariosTest.class.getClassLoader().getResource("scenarios"),
                    "Cannot locate 'scenarios' in test resources");
            BASE = Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to locate scenarios directory from classpath", e);
        }
    }

    private enum Outcome { PASS, FAIL }

    /** Expected outcomes per scenario per mode (hand-written on purpose). */
    private static final Map<String, Map<CheckMode, Outcome>> EXPECTED = new LinkedHashMap<>();
    static {
        put("01_all_match",                           PASS(), PASS(), PASS());
        put("02_relationship_strength_mismatch",      FAIL(), PASS(), PASS());
        put("03_uml_extra_relationship",              FAIL(), PASS(), PASS());
        put("04_code_members_omitted_in_uml",         FAIL(), FAIL(), PASS());
        put("05_member_type_mismatch",                FAIL(), FAIL(), FAIL());
        put("06_method_return_omitted",               FAIL(), FAIL(), PASS());
        put("07_missing_class_in_uml",                FAIL(), FAIL(), FAIL());
        put("08_uml_only_class",                      FAIL(), FAIL(), FAIL());
        put("09_visibility_differences",              FAIL(), FAIL(), PASS());
        put("10_duplicate_weaker_edges",              PASS(), PASS(), PASS());
        put("11_wrong_arrow_realization_vs_generalization", FAIL(), PASS(), PASS());
        put("12_dependency_match",                    PASS(), PASS(), PASS());
    }
    private static void put(String name, Outcome strict, Outcome relaxed, Outcome minimal) {
        Map<CheckMode, Outcome> map = new EnumMap<>(CheckMode.class);
        map.put(CheckMode.STRICT, strict);
        map.put(CheckMode.RELAXED, relaxed);
        map.put(CheckMode.MINIMAL, minimal);
        EXPECTED.put(name, map);
    }
    private static Outcome PASS() { return Outcome.PASS; }
    private static Outcome FAIL() { return Outcome.FAIL; }

    @Test
    void runAllScenarios() throws Exception {
        assertTrue(Files.isDirectory(BASE), "Scenarios folder not found: " + BASE);

        List<String> failures = new ArrayList<>();

        for (Path scenarioDir : listScenarioDirs(BASE)) {
            String folder = scenarioDir.getFileName().toString(); // scenario01 ..
            String scenarioName = mapScenario(folder);
            Path umlFile = scenarioDir.resolve(folder + ".puml");

            // --- collect all .java files in the scenario folder ---
            List<File> javaFiles = Files.list(scenarioDir)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            // --- parse code model (robustly handle either parser signature) ---
            IntermediateModel codeModel = parseJavaModel(javaFiles, scenarioDir.toFile());

            // --- parse UML ---
            PlantUMLParser umlParser = new PlantUMLParser();
            IntermediateModel umlModel = umlParser.parse(umlFile.toFile());

            // --- compare in all modes ---
            for (CheckMode mode : List.of(CheckMode.STRICT, CheckMode.RELAXED, CheckMode.MINIMAL)) {
                ModelComparator comparator = new ModelComparator(mode);
                List<Difference> diffs = comparator.compare(codeModel, umlModel);

                boolean hasError = diffs.stream().anyMatch(d -> d.getLevel() == IssueLevel.ERROR);

                System.out.println("--------------------------------------------------");
                System.out.println("Scenario: " + scenarioName + " | Mode: " + mode);
                if (diffs.isEmpty()) {
                    System.out.println("(no differences)");
                } else {
                    diffs.forEach(d -> System.out.println(format(d)));
                }

                Outcome expected = EXPECTED.get(scenarioName).get(mode);
                if (expected == Outcome.PASS && hasError) {
                    failures.add(scenarioName + " [" + mode + "] expected PASS but found ERROR(s).");
                }
                if (expected == Outcome.FAIL && !hasError) {
                    failures.add(scenarioName + " [" + mode + "] expected FAIL (ERROR) but found none.");
                }
            }
        }

        if (!failures.isEmpty()) {
            fail("Scenario mismatches:\n - " + String.join("\n - ", failures));
        }
    }

    // --- helpers ---

    private static IntermediateModel parseJavaModel(List<File> javaFiles, File scenarioDir) throws Exception {
        JavaSourceParser parser = new JavaSourceParser();

        // Prefer parse(List<File>) if present
        try {
            var m = JavaSourceParser.class.getMethod("parse", List.class);
            @SuppressWarnings("unchecked")
            IntermediateModel model = (IntermediateModel) m.invoke(parser, javaFiles);
            return model;
        } catch (NoSuchMethodException ignore) {
            // Fall back to parse(File) — and pass the scenario directory
            var m = JavaSourceParser.class.getMethod("parse", File.class);
            return (IntermediateModel) m.invoke(parser, scenarioDir);
        }
    }

    private static List<Path> listScenarioDirs(Path base) throws Exception {
        try (var st = Files.list(base)) {
            return st.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        }
    }

    private static String mapScenario(String folder) {
        return switch (folder) {
            case "scenario01" -> "01_all_match";
            case "scenario02" -> "02_relationship_strength_mismatch";
            case "scenario03" -> "03_uml_extra_relationship";
            case "scenario04" -> "04_code_members_omitted_in_uml";
            case "scenario05" -> "05_member_type_mismatch";
            case "scenario06" -> "06_method_return_omitted";
            case "scenario07" -> "07_missing_class_in_uml";
            case "scenario08" -> "08_uml_only_class";
            case "scenario09" -> "09_visibility_differences";
            case "scenario10" -> "10_duplicate_weaker_edges";
            case "scenario11" -> "11_wrong_arrow_realization_vs_generalization";
            case "scenario12" -> "12_dependency_match";
            default -> folder;
        };
    }

    private static String format(Difference d) {
        String uml = (d.getUml() == null || d.getUml().isBlank()) ? "-" : d.getUml();
        String code = (d.getCode() == null || d.getCode().isBlank()) ? "-" : d.getCode();
        String tip = (d.getTip() == null || d.getTip().isBlank()) ? "" : " TIP: " + d.getTip();
        return d.getLevel() + " | " + d.getKind() +
                (blank(d.getWhere()) ? "" : " @ " + d.getWhere()) +
                (blank(d.getSummary()) ? "" : " — " + d.getSummary()) +
                " [UML: " + uml + " | CODE: " + code + "]" + tip;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
