package edu.UOI.plantumlcheck.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import edu.UOI.plantumlcheck.service.CompareService;
import edu.UOI.plantumlcheck.service.CompareService.Selection;
import edu.UOI.plantumlcheck.service.CompareService.Mode;
import edu.UOI.plantumlcheck.service.CompareService.RunResult;
import model.IntermediateModel;

import java.util.List;

@Controller
public class SelectionController {

    private final CompareService compareService;

    public SelectionController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping("/select")
    public String showSelect(HttpSession session, Model model) {
        // Left-hand tree data
        model.addAttribute("scanMap", session.getAttribute("scanMap"));

        // Mode / flow flags
        model.addAttribute("codeOnly", session.getAttribute("codeOnly"));
        model.addAttribute("mode", session.getAttribute("mode")); // to preselect radios
        model.addAttribute("workspaceRoot", session.getAttribute("workspaceRoot"));

        // ✅ PlantUML filenames for the right-hand panel (UI only)
        @SuppressWarnings("unchecked")
        List<String> plantumlNames = (List<String>) session.getAttribute("plantumlNames");
        model.addAttribute("plantumlNames", plantumlNames);

        return "select";
    }

    @PostMapping("/select/confirm")
    public String confirmSelection(
            HttpSession session,
            @RequestParam(name = "selectedFqcns", required = false) List<String> selectedFqcns,
            @RequestParam(name = "mode", required = false, defaultValue = "RELAXED") String modeStr
    ) {
        String workspaceRoot = (String) session.getAttribute("workspaceRoot");
        Boolean codeOnly = (Boolean) session.getAttribute("codeOnly");
        if (codeOnly == null) codeOnly = Boolean.FALSE;

        // Keep for later regeneration and debugging
        session.setAttribute("selectedFqcns", selectedFqcns);
        session.setAttribute("mode", modeStr);

        Mode mode = Mode.valueOf(modeStr.toUpperCase());

        @SuppressWarnings("unchecked")
        List<String> plantumlFiles = (List<String>) session.getAttribute("plantumlFiles");

        Selection sel = new Selection(
                workspaceRoot,
                selectedFqcns != null ? selectedFqcns : List.of(),
                codeOnly,
                mode,
                plantumlFiles // may be null / unused in code-only
        );

        RunResult result = compareService.run(sel);

        // Store result for /results view
        session.setAttribute("lastResult", result);
        session.setAttribute("lastReportText", result.reportText());
        if (result.generatedPlantUml() != null) {
            session.setAttribute("lastGeneratedPuml", result.generatedPlantUml());
        }

        // Stash the official code model parsed by the service (authoritative)
        IntermediateModel official = compareService.getLastCodeModel();
        session.setAttribute("officialCodeModel", official);

        return "redirect:/results";
    }
}