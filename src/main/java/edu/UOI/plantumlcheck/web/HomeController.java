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
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final JavaScanService scanService;

    public HomeController(JavaScanService scanService) {
        this.scanService = scanService;
    }

    @GetMapping("/")
    public String home() { return "index"; }

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
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        List<String> sourceNames = (source == null) ? List.of() :
                Arrays.stream(source)
                        .filter(f -> f != null && !f.isEmpty())
                        .map(f -> f.getOriginalFilename() != null ? f.getOriginalFilename() : "file")
                        .collect(Collectors.toList());

        SelectionSummary summary = new SelectionSummary(plantumlNames, sourceNames, codeOnly);

        try {
            // 1) Save source to workspace
            Workspace ws = scanService.saveSourceToWorkspace(source);
            Path root = ws.root();

            // 2) Save PlantUML files into workspace/uml (record absolute paths)
            List<String> plantumlFiles = new ArrayList<>();
            if (!codeOnly && plantuml != null) {
                Path umlDir = root.resolve("uml");
                Files.createDirectories(umlDir);
                for (MultipartFile mf : plantuml) {
                    if (mf == null || mf.isEmpty() || mf.getOriginalFilename() == null) continue;
                    Path dest = umlDir.resolve(Paths.get(mf.getOriginalFilename()).getFileName().toString());
                    Files.write(dest, mf.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    plantumlFiles.add(dest.toAbsolutePath().toString());
                }
            }

            // 3) Build quick scan map for UI
            Map<String, List<String>> scanMap = scanService.buildPackageMap(root);

            // 4) Flash + Session
            redirect.addFlashAttribute("selectionSummary", summary);
            redirect.addFlashAttribute("workspaceRoot", root.toString());
            redirect.addFlashAttribute("scanMap", scanMap);
            redirect.addFlashAttribute("plantumlNames", plantumlNames);
            redirect.addFlashAttribute("codeOnly", codeOnly);

            session.setAttribute("selectionSummary", summary);
            session.setAttribute("workspaceRoot", root.toString());
            session.setAttribute("scanMap", scanMap);
            session.setAttribute("plantumlNames", plantumlNames);
            session.setAttribute("plantumlFiles", plantumlFiles);
            session.setAttribute("codeOnly", codeOnly);

        } catch (IOException e) {
            model.addAttribute("error", "Failed to save files: " + e.getMessage());
            return "index";
        }

        return "redirect:/select";
    }
}
