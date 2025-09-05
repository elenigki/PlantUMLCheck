package edu.UOI.plantumlcheck.web;

import edu.UOI.plantumlcheck.service.CompareService;
import edu.UOI.plantumlcheck.service.CompareService.Mode;
import edu.UOI.plantumlcheck.service.CompareService.Selection;
import edu.UOI.plantumlcheck.service.CompareService.RunResult;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class SelectionController {

    private final CompareService compareService;

    public SelectionController(CompareService compareService) {
        this.compareService = compareService;
    }

    // ---------- GET: selection screen ----------
    @GetMapping("/select")
    public String selectPreview(Model model, HttpSession session) {
        copyFromSessionIfMissing("selectionSummary", model, session);
        copyFromSessionIfMissing("workspaceRoot", model, session);
        copyFromSessionIfMissing("scanMap", model, session);
        copyFromSessionIfMissing("plantumlNames", model, session);
        copyFromSessionIfMissing("codeOnly", model, session);

        // Null-safe defaults to prevent Thymeleaf errors
        if (model.getAttribute("scanMap") == null) {
            model.addAttribute("scanMap", Map.of());
        }

        // Normalize mode for UI: STRICT / RELAXED / MINIMAL (default RELAXED)
        Object m = session.getAttribute("mode");
        Mode normalized = toMode((m instanceof String s && !s.isBlank()) ? s : null);
        model.addAttribute("mode", normalized.name());
        session.setAttribute("mode", normalized.name());

        // codeOnly fallback if not present in model
        if (model.getAttribute("codeOnly") == null) {
            boolean codeOnly = false;
            Object ss = model.getAttribute("selectionSummary");
            try {
                if (ss != null) {
                    var meth = ss.getClass().getMethod("codeOnly");
                    Object val = meth.invoke(ss);
                    if (val instanceof Boolean b) codeOnly = b;
                }
            } catch (Exception ignored) { }
            model.addAttribute("codeOnly", codeOnly);
            session.setAttribute("codeOnly", codeOnly);
        }

        return "select";
    }

    // ---------- POST: run parse/compare ----------
    @PostMapping("/select/confirm")
    public String confirmSelection(
            @RequestParam("workspaceRoot") String workspaceRoot,
            @RequestParam(value = "selectedFqcns", required = false) List<String> selectedFqcns,
            @RequestParam(value = "codeOnly") boolean codeOnly,
            @RequestParam(value = "mode", defaultValue = "RELAXED") String modeStr,
            HttpSession session,
            Model model
    ) {
        if (selectedFqcns == null) selectedFqcns = List.of();

        // Resolve PlantUML file paths (if any) from session (either full paths or just names)
        List<String> plantumlFiles = resolvePlantUmlFiles(session, workspaceRoot);

        // Normalize mode from string (STRICT / RELAXED / MINIMAL)
        Mode mode = toMode(modeStr);

        // Persist choices to session so refresh/back works
        session.setAttribute("mode", mode.name());
        session.setAttribute("codeOnly", codeOnly);

        // Run comparison
        CompareService.Selection sel = new Selection(
                workspaceRoot,
                selectedFqcns,
                codeOnly,
                mode,
                plantumlFiles
        );
     // SelectionController.java  (inside @PostMapping("/select/confirm"))
        RunResult result = compareService.run(sel);

        // Make these the single source of truth for the results page + downloads
        session.setAttribute("lastResult", result);
        session.setAttribute("lastReportText", result.reportText());      // used by /results/report.txt
        session.setAttribute("lastGeneratedPuml", result.generatedPlantUml()); // used by /results/uml.puml
        
        session.setAttribute("selectedFqcns", selectedFqcns);


        // No need to put 'result' in the model anymore.
        // PRG: redirect to the GET endpoint that renders the view.
        return "redirect:/results";

    }

    /**
     * Map incoming string to Mode.
     * Accepts STRICT / RELAXED / MINIMAL (case-insensitive).
     * Any other/legacy token is treated as MINIMAL (backward compatible, without naming it).
     */
    private static Mode toMode(String s) {
        if (s == null) return Mode.RELAXED;
        String t = s.trim().toUpperCase(Locale.ROOT);
        if ("STRICT".equals(t))  return Mode.STRICT;
        if ("RELAXED".equals(t)) return Mode.RELAXED;
        if ("MINIMAL".equals(t)) return Mode.MINIMAL;
        // Legacy/unknown tokens (e.g., prior values) => treat as MINIMAL
        return Mode.MINIMAL;
    }

    @SuppressWarnings("unchecked")
    private static List<String> resolvePlantUmlFiles(HttpSession session, String workspaceRoot) {
        // Try full paths list first
        Object filesObj = session.getAttribute("plantumlFiles");
        if (filesObj instanceof List<?> raw) {
            List<String> asStrings = raw.stream().map(Object::toString).collect(Collectors.toList());
            if (!asStrings.isEmpty()) return asStrings;
        }

        // Otherwise, try plantumlNames (filenames) and search them under the workspace
        Object namesObj = session.getAttribute("plantumlNames");
        if (namesObj instanceof List<?> nameList && !nameList.isEmpty()) {
            List<String> hits = new ArrayList<>();
            for (Object o : nameList) {
                String name = String.valueOf(o);
                try {
                    // Walk workspace for filename match
                    try (var stream = Files.walk(Paths.get(workspaceRoot))) {
                        Optional<Path> p = stream
                                .filter(pp -> pp.getFileName().toString().equals(name))
                                .findFirst();
                        p.ifPresent(path -> hits.add(path.toAbsolutePath().toString()));
                    }
                } catch (IOException ignored) {}
            }
            if (!hits.isEmpty()) return hits;
        }

        // No PlantUML inputs
        return List.of();
    }

    private static void copyFromSessionIfMissing(String key, Model model, HttpSession session) {
        if (model.getAttribute(key) == null) {
            Object v = session.getAttribute(key);
            if (v != null) model.addAttribute(key, v);
        }
    }
}
