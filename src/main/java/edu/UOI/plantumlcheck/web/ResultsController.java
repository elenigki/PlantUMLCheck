package edu.UOI.plantumlcheck.web;

import edu.UOI.plantumlcheck.service.CompareService;
import edu.UOI.plantumlcheck.service.CompareService.Mode;
import edu.UOI.plantumlcheck.service.CompareService.Selection;
import edu.UOI.plantumlcheck.service.CompareService.RunResult;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
public class ResultsController {

	@Autowired
	private CompareService compareService;

	/** Results page */
	@GetMapping("/results")
	public String showResults(HttpSession session, Model model) {
		Object result = session.getAttribute("lastResult");
		if (result == null)
			return "redirect:/select";
		model.addAttribute("result", result);

		// If code-only and we don't yet have a script, generate it via the SAME service
		// pipeline
		Boolean codeOnly = (Boolean) session.getAttribute("codeOnly");
		String puml = (String) session.getAttribute("lastGeneratedPuml");
		if (Boolean.TRUE.equals(codeOnly) && (puml == null || puml.isBlank())) {
			puml = generateViaService(session);
			if (puml != null)
				session.setAttribute("lastGeneratedPuml", puml);
		}

		if (puml != null)
			model.addAttribute("generatedPlantUml", puml);
		return "results";
	}

	/** Export the comparison report as txt */
	@GetMapping("/results/report.txt")
	public ResponseEntity<String> downloadReport(HttpSession session) {
		String txt = (String) session.getAttribute("lastReportText");
		if (txt == null)
			txt = "No report.";
		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.txt").body(txt);
	}

	/** Export the last generated/corrected PlantUML */
	@GetMapping("/results/uml.puml")
	public ResponseEntity<String> downloadUml(HttpSession session) {
		String puml = (String) session.getAttribute("lastGeneratedPuml");
		if (puml == null)
			puml = "' No generated UML available.\n@startuml\n@enduml";
		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=generated.puml").body(puml);
	}

	/**
	 * Comparison mode: generate corrected UML and show it inline. Uses EXACTLY the
	 * same pipeline as code-only by running CompareService with codeOnly=true.
	 */
	@GetMapping("/results/generate-corrected")
	public String generateCorrectedAndShow(HttpSession session) {
		String puml = generateViaService(session);
		if (puml != null) {
			session.setAttribute("lastGeneratedPuml", puml);
		}
		return "redirect:/results";
	}

	// ---------- helper ----------

	/**
	 * Run the SAME pipeline the app uses for code-only:
	 * CompareService.run(Selection with codeOnly=true) This guarantees identical
	 * PlantUML output (formatting, ordering, options).
	 */
	@SuppressWarnings("unchecked")
	private String generateViaService(HttpSession session) {
		String workspaceRoot = (String) session.getAttribute("workspaceRoot");
		if (workspaceRoot == null || workspaceRoot.isBlank())
			return null;

		List<String> selectedFqcns = (List<String>) session.getAttribute("selectedFqcns");
		if (selectedFqcns == null)
			selectedFqcns = List.of();

		// Mode is ignored in code-only, but we pass something consistent
		String modeStr = (String) session.getAttribute("mode");
		Mode mode = toMode(modeStr);

		// PlantUML inputs are irrelevant for code-only
		List<String> plantumlFiles = new ArrayList<>();

		Selection sel = new Selection(workspaceRoot, selectedFqcns, true, // << codeOnly
				mode, plantumlFiles);

		RunResult rr = compareService.run(sel);
		if (rr == null)
			return null;

		// Put fresh 'result' back into session only if you want to refresh summary too;
		// otherwise, just stash the UML.
		// session.setAttribute("lastResult", rr);

		return rr.generatedPlantUml();
	}

	private static Mode toMode(String s) {
		if (s == null)
			return Mode.RELAXED;
		String t = s.trim().toUpperCase(Locale.ROOT);
		return switch (t) {
		case "STRICT" -> Mode.STRICT;
		case "RELAXED" -> Mode.RELAXED;
		case "MINIMAL" -> Mode.MINIMAL;
		default -> Mode.MINIMAL;
		};
	}
}
