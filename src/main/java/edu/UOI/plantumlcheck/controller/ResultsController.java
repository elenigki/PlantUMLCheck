package edu.UOI.plantumlcheck.controller;

import jakarta.servlet.http.HttpSession;
import model.IntermediateModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import generator.PlantUMLGenerator;

@Controller
public class ResultsController {

    /** Results page */
    @GetMapping("/results")
    public String showResults(HttpSession session, Model model) {
        Object result = session.getAttribute("lastResult");
        if (result == null) return "redirect:/select";
        model.addAttribute("result", result);

        // If code-only and we don't yet have a script, generate directly from the official code model
        Boolean codeOnly = (Boolean) session.getAttribute("codeOnly");
        String puml = (String) session.getAttribute("lastGeneratedPuml");
        if (Boolean.TRUE.equals(codeOnly) && (puml == null || puml.isBlank())) {
            puml = generateFromOfficialModel(session);
            if (puml != null) session.setAttribute("lastGeneratedPuml", puml);
        }

        if (puml != null) model.addAttribute("generatedPlantUml", puml);
        return "results";
    }

    /** Export the comparison report as txt */
    @GetMapping("/results/report.txt")
    public ResponseEntity<String> downloadReport(HttpSession session) {
        String txt = (String) session.getAttribute("lastReportText");
        if (txt == null) txt = "No report.";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.txt")
                .body(txt);
    }

    /** Export the last generated PlantUML */
    @GetMapping("/results/uml.puml")
    public ResponseEntity<String> downloadUml(HttpSession session) {
        String puml = (String) session.getAttribute("lastGeneratedPuml");
        if (puml == null) puml = "' No generated UML available.\n@startuml\n@enduml";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=generated.puml")
                .body(puml);
    }

    /**
     * Comparison mode button: generate UML from the official code model and show it inline.
     * (Same behavior as code-only; no reparse, no comparator influence.)
     */
    @GetMapping("/results/generate-corrected")
    public String generateCorrectedAndShow(HttpSession session) {
        String puml = generateFromOfficialModel(session);
        if (puml != null) session.setAttribute("lastGeneratedPuml", puml);
        return "redirect:/results";
    }

    // ---------- helper ----------

    /** Always use the official code model produced by the service. */
    private String generateFromOfficialModel(HttpSession session) {
        IntermediateModel codeModel = (IntermediateModel) session.getAttribute("officialCodeModel");
        if (codeModel == null) return null; // nothing to generate

        // IMPORTANT: raw generator output (duplicates/headers exactly as the generator emits)
        return new PlantUMLGenerator().generate(codeModel);
    }
}
