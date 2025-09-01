package edu.UOI.plantumlcheck.web;

import edu.UOI.plantumlcheck.service.CompareService;
import edu.UOI.plantumlcheck.service.CompareService.Mode;
import edu.UOI.plantumlcheck.service.CompareService.Selection;
import edu.UOI.plantumlcheck.service.CompareService.RunResult;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
public class SelectionController {

    private final CompareService compareService;

    public SelectionController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping("/select")
    public String selectPreview(Model model, HttpSession session) {
        copyIfMissing("selectionSummary", model, session);
        copyIfMissing("workspaceRoot", model, session);
        copyIfMissing("scanMap", model, session);
        copyIfMissing("plantumlNames", model, session);
        copyIfMissing("codeOnly", model, session);
        return "select";
    }

    @PostMapping("/select/confirm")
    public String confirm(@RequestParam("codeOnly") boolean codeOnly,
                          @RequestParam("mode") String mode,
                          @RequestParam(value = "cls", required = false) List<String> selectedFqcns,
                          HttpSession session,
                          Model model) {

        if (selectedFqcns == null || selectedFqcns.isEmpty()) {
            model.addAttribute("error", "Please choose at least one class.");
            return "select";
        }

        String workspaceRoot = (String) session.getAttribute("workspaceRoot");
        if (workspaceRoot == null) {
            model.addAttribute("error", "Workspace expired. Please re-upload files.");
            return "index";
        }

        @SuppressWarnings("unchecked")
        List<String> plantumlFiles = (List<String>) session.getAttribute("plantumlFiles");
        if (plantumlFiles == null) plantumlFiles = new ArrayList<>();

        // Remember for results header
        session.setAttribute("codeOnly", codeOnly);
        session.setAttribute("mode", mode);

        Selection sel = new Selection(
                workspaceRoot,
                selectedFqcns,
                codeOnly,
                "RELAXED".equalsIgnoreCase(mode) ? Mode.RELAXED : Mode.STRICT,
                plantumlFiles
        );

        RunResult result = compareService.run(sel);

        // keep for downloads
        session.setAttribute("lastReportText", result.textReport());
        session.setAttribute("lastGeneratedPuml", result.generatedPlantUml());

        model.addAttribute("result", result);
        return "results";
    }

    private static void copyIfMissing(String key, Model model, HttpSession session) {
        if (model.getAttribute(key) == null) {
            Object v = session.getAttribute(key);
            if (v != null) model.addAttribute(key, v);
        }
    }
}
