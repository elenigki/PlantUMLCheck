package edu.UOI.plantumlcheck.web;

//keep your existing: package ...;

import jakarta.servlet.http.HttpSession;
import model.IntermediateModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import generator.PlantUMLGenerator;
import parser.code.JavaSourceParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ResultsController {

	/** Results page */
	@GetMapping("/results")
	public String showResults(HttpSession session, Model model) {
		Object result = session.getAttribute("lastResult");
		if (result == null) {
			return "redirect:/select";
		}
		model.addAttribute("result", result);

		// Expose generated/corrected UML (if any) to the template
		String puml = (String) session.getAttribute("lastGeneratedPuml");
		if (puml != null) {
			model.addAttribute("generatedPlantUml", puml);
		}
		return "results";
	}

	/** Export the comparison report as txt (already used by your page) */
	@GetMapping("/results/report.txt")
	public ResponseEntity<String> downloadReport(HttpSession session) {
		String txt = (String) session.getAttribute("lastReportText");
		if (txt == null)
			txt = "No report.";
		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.txt").body(txt);
	}

	/** Export the last generated/corrected PlantUML (used by the Export button) */
	@GetMapping("/results/uml.puml")
	public ResponseEntity<String> downloadUml(HttpSession session) {
		String puml = (String) session.getAttribute("lastGeneratedPuml");
		if (puml == null)
			puml = "' No generated UML available.\n@startuml\n@enduml";
		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=generated.puml").body(puml);
	}

	/**
	 * Generate corrected UML (view-only): build from code model, strip prologue,
	 * stash to session, and return to /results so the page shows it inline.
	 */
	@GetMapping("/results/generate-corrected")
	public String generateCorrectedAndShow(HttpSession session) {
		try {
			// Prefer a stashed code model if you later store it under "codeModel"
			IntermediateModel model = null;
			Object maybe = session.getAttribute("codeModel");
			if (maybe instanceof IntermediateModel) {
				model = (IntermediateModel) maybe;
			}

			// Otherwise rebuild from user's selection under the workspace
			if (model == null) {
				String workspaceRoot = (String) session.getAttribute("workspaceRoot");
				@SuppressWarnings("unchecked")
				List<String> selectedFqcns = (List<String>) session.getAttribute("selectedFqcns");
				if (workspaceRoot == null)
					return "redirect:/select";

				List<File> files = resolveSelectedJavaFiles(workspaceRoot, selectedFqcns);
				if (files.isEmpty())
					return "redirect:/select";

				JavaSourceParser parser = new JavaSourceParser();
				model = parser.parse(files);
			}

			String uml = new PlantUMLGenerator().generate(model);
			String cleaned = stripPrologue(uml);

			session.setAttribute("lastGeneratedPuml", cleaned);
			return "redirect:/results";

		} catch (IOException e) {
			// On parser IO error, return to results; user can retry
			return "redirect:/results";
		}
	}

	/** Remove comments + skinparams + 'hide empty members' from the top */
	private static String stripPrologue(String uml) {
		// Keep @startuml/@enduml and any real content; drop comment lines and style
		// lines.
		return Arrays.stream(uml.split("\\R")).filter(line -> {
			String t = line.trim();
			if (t.startsWith("'"))
				return false; // drop comment lines (')
			if (t.startsWith("skinparam"))
				return false; // drop skinparam lines
			if (t.equalsIgnoreCase("hide empty members"))
				return false;
			return true;
		}).collect(Collectors.joining("\n"));
	}

	/** Resolve .java files for the selected FQCNs; fall back to filename match */
	private static List<File> resolveSelectedJavaFiles(String workspaceRoot, List<String> selectedFqcns)
			throws IOException {
		Path root = Paths.get(workspaceRoot);
		if (!Files.isDirectory(root))
			return List.of();

		// If selection is missing, parse everything under the workspace
		if (selectedFqcns == null || selectedFqcns.isEmpty()) {
			try (var stream = Files.walk(root)) {
				return stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
						.map(Path::toFile).collect(Collectors.toList());
			}
		}

		// Index by filename for fallback
		Map<String, Path> byFileName = new HashMap<>();
		try (var stream = Files.walk(root)) {
			stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
					.forEach(p -> byFileName.putIfAbsent(p.getFileName().toString(), p));
		}

		List<File> out = new ArrayList<>();
		for (String fqcn : selectedFqcns) {
			String rel = fqcn.replace('.', File.separatorChar) + ".java";
			Path expected = root.resolve(rel);
			if (Files.isRegularFile(expected)) {
				out.add(expected.toFile());
				continue;
			}
			String simple = fqcn.contains(".") ? fqcn.substring(fqcn.lastIndexOf('.') + 1) : fqcn;
			Path byName = byFileName.get(simple + ".java");
			if (byName != null)
				out.add(byName.toFile());
		}
		return out;
	}

}
