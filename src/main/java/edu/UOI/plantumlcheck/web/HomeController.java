package edu.UOI.plantumlcheck.web;

import edu.UOI.plantumlcheck.service.JavaScanService;
import edu.UOI.plantumlcheck.service.JavaScanService.Workspace;
import edu.UOI.plantumlcheck.web.view.SelectionSummary;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final JavaScanService scanService;

    public HomeController(JavaScanService scanService) {
        this.scanService = scanService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam(name = "plantuml", required = false) MultipartFile[] plantuml,
                          @RequestParam(name = "source", required = false) MultipartFile[] source,
                          @RequestParam(name = "codeOnly", defaultValue = "false") boolean codeOnly,
                          Model model,
                          HttpSession session,
                          RedirectAttributes redirect) {

        boolean sourceEmpty = (source == null || source.length == 0 ||
                (source.length == 1 && (source[0] == null || source[0].isEmpty())));
        boolean plantumlEmpty = (plantuml == null || plantuml.length == 0 ||
                (plantuml.length == 1 && (plantuml[0] == null || plantuml[0].isEmpty())));

        if (sourceEmpty) {
            model.addAttribute("error", "Please select source code (folder or .java files).");
            return "index";
        }
        if (!codeOnly && plantumlEmpty) {
            model.addAttribute("error", "Please add PlantUML scripts or enable 'code only'.");
            return "index";
        }

        // Filenames for UI
        List<String> plantumlNames = (plantuml == null) ? List.of() :
                Arrays.stream(plantuml)
                        .filter(f -> f != null && !f.isEmpty())
                        .map(MultipartFile::getOriginalFilename)
                        .collect(Collectors.toList());

        List<String> sourceNames = (source == null) ? List.of() :
                Arrays.stream(source)
                        .filter(f -> f != null && !f.isEmpty())
                        .map(f -> f.getOriginalFilename() != null ? f.getOriginalFilename() : "file")
                        .collect(Collectors.toList());

        SelectionSummary summary = new SelectionSummary(plantumlNames, sourceNames, codeOnly);

        try {
            // Save source files to workspace + quick scan
            Workspace ws = scanService.saveSourceToWorkspace(source);
            String root = ws.root().toString();
            Map<String, List<String>> scanMap = scanService.buildPackageMap(ws.root());

            // Flash + Session (so /select works after redirect or refresh)
            redirect.addFlashAttribute("selectionSummary", summary);
            redirect.addFlashAttribute("workspaceRoot", root);
            redirect.addFlashAttribute("scanMap", scanMap);
            redirect.addFlashAttribute("plantumlNames", plantumlNames);
            redirect.addFlashAttribute("codeOnly", codeOnly);

            session.setAttribute("selectionSummary", summary);
            session.setAttribute("workspaceRoot", root);
            session.setAttribute("scanMap", scanMap);
            session.setAttribute("plantumlNames", plantumlNames);
            session.setAttribute("codeOnly", codeOnly);

        } catch (IOException e) {
            model.addAttribute("error", "Failed to save source files: " + e.getMessage());
            return "index";
        }

        return "redirect:/select";
    }

    @GetMapping("/select")
    public String selectPreview(Model model, HttpSession session) {
        // Rehydrate core attrs for refresh/direct access
        copyFromSessionIfMissing("selectionSummary", model, session);
        copyFromSessionIfMissing("workspaceRoot", model, session);
        copyFromSessionIfMissing("scanMap", model, session);

        // Plain, template-safe attributes:
        copyFromSessionIfMissing("plantumlNames", model, session);
        copyFromSessionIfMissing("codeOnly", model, session);

        return "select";
    }

    /** Convenience: if the model lacks 'key', copy it from session when present. */
    private static void copyFromSessionIfMissing(String key, Model model, HttpSession session) {
        if (model.getAttribute(key) == null) {
            Object v = session.getAttribute(key);
            if (v != null) model.addAttribute(key, v);
        }
    }
}
