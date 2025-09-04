package edu.UOI.plantumlcheck.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ResultsController {
	
	@GetMapping("/results")
	public String showResults(HttpSession session, org.springframework.ui.Model model) {
	    Object result = session.getAttribute("lastResult");
	    if (result == null) {
	        // Session expired or user jumped straight here — send them back gracefully
	        return "redirect:/select";
	    }
	    model.addAttribute("result", result);
	    return "results";
	}


    @GetMapping("/results/report.txt")
    public ResponseEntity<String> downloadReport(HttpSession session) {
        String txt = (String) session.getAttribute("lastReportText");
        if (txt == null) txt = "No report.";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.txt")
                .body(txt);
    }

    @GetMapping("/results/uml.puml")
    public ResponseEntity<String> downloadUml(HttpSession session) {
        String puml = (String) session.getAttribute("lastGeneratedPuml");
        if (puml == null) puml = "' No generated UML available.";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=generated.puml")
                .body(puml);
    }
}