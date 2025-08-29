package edu.UOI.plantumlcheck.web;

import edu.UOI.plantumlcheck.service.JavaScanService;
import edu.UOI.plantumlcheck.service.JavaScanService.Workspace;
import edu.UOI.plantumlcheck.web.view.SelectionSummary;
import jakarta.servlet.http.HttpSession;
import parser.code.ScannedJavaInfo;

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

        // Save source files to a temp workspace
        try {
        	Workspace ws = scanService.saveSourceToWorkspace(source);
        	String root = ws.root().toString();

        	// Build the package->classes map (never null; may be empty)
        	Map<String, List<String>> scanMap = scanService.buildPackageMap(ws.root());

        	// Flash + Session (so /select will have everything)
        	redirect.addFlashAttribute("selectionSummary", summary);
        	redirect.addFlashAttribute("workspaceRoot", root);
        	redirect.addFlashAttribute("scanMap", scanMap);

        	session.setAttribute("selectionSummary", summary);
        	session.setAttribute("workspaceRoot", root);
        	session.setAttribute("scanMap", scanMap);

            // (optional) avoid storing List<Path> directly in session to keep it simple
            // session.setAttribute("workspaceFiles", ws.savedFiles().stream().map(Path::toString).toList());

        } catch (IOException e) {
            model.addAttribute("error", "Failed to save source files: " + e.getMessage());
            return "index";
        }

        return "redirect:/select";
    }

    @GetMapping("/select")
    public String selectPreview(Model model, HttpSession session) {
        // If Flash was lost, fall back to session
        if (model.getAttribute("selectionSummary") == null) {
            Object fromSession = session.getAttribute("selectionSummary");
            if (fromSession != null) model.addAttribute("selectionSummary", fromSession);
        }
        if (model.getAttribute("workspaceRoot") == null) {
            Object root = session.getAttribute("workspaceRoot");
            if (root != null) model.addAttribute("workspaceRoot", root);
        }
        if (model.getAttribute("scanMap") == null) {
            Object sm = session.getAttribute("scanMap");
            if (sm != null) model.addAttribute("scanMap", sm);
        }

        return "select";
    }
}
