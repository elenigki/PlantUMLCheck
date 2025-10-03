package comparison.checks;

import comparison.CheckMode;
import comparison.issues.Difference;
import comparison.issues.IssueKind;
import comparison.issues.IssueLevel;
import model.ClassInfo;
import model.IntermediateModel;

import java.util.*;

public final class ClassCheck {

	public static List<Difference> compareClasses(IntermediateModel code, IntermediateModel uml, CheckMode mode) {
		List<Difference> out = new ArrayList<>();
		Map<String, ClassInfo> codeBy = byName(code.getClasses());
		Map<String, ClassInfo> umlBy = byName(uml.getClasses());

		// UML-only classes (present in UML, missing in code)
		for (String n : onlyIn(umlBy, codeBy)) {
			out.add(new Difference(IssueKind.CLASS_MISSING_IN_CODE, IssueLevel.ERROR, n, // where
					"Class missing in source code", // summary
					"present", "missing", // uml, code
					"Add class in code or remove from UML" // tip
			));
		}

		// Code-only classes (present in code, missing in UML)
		for (String n : onlyIn(codeBy, umlBy)) {
			IssueLevel lvl = (mode == CheckMode.RELAXED) ? IssueLevel.INFO : IssueLevel.ERROR;
			out.add(new Difference(IssueKind.CLASS_MISSING_IN_UML, lvl, n, "Class missing in UML", "missing", "present",
					(mode == CheckMode.RELAXED) ? "Optionally add to UML" : "Add class to UML to match code"));
		}

		// kind/abstract checks can be added later here if you want
		return out;
	}

	// Returns class names present in both models.
	public static Set<String> commonClassNames(IntermediateModel code, IntermediateModel uml) {
		Map<String, ClassInfo> codeBy = byName(code.getClasses());
		Map<String, ClassInfo> umlBy = byName(uml.getClasses());
		Set<String> s = new LinkedHashSet<>(codeBy.keySet());
		s.retainAll(umlBy.keySet());
		return s;
	}

	// Returns the class with this name from the given model.
	public static ClassInfo classFrom(IntermediateModel m, String name) {
		if (m == null || m.getClasses() == null)
			return null;
		for (ClassInfo c : m.getClasses()) {
			if (c != null && name.equals(c.getName()))
				return c;
		}
		return null;
	}

	// --- helpers ---

	private static Map<String, ClassInfo> byName(List<ClassInfo> list) {
		Map<String, ClassInfo> m = new LinkedHashMap<>();
		if (list == null)
			return m;
		for (ClassInfo c : list) {
			if (c == null)
				continue;
			String n = c.getName();
			if (n == null || n.isBlank())
				continue;
			m.putIfAbsent(n, c); // first wins
		}
		return m;
	}

	private static Set<String> onlyIn(Map<String, ?> a, Map<String, ?> b) {
		Set<String> s = new LinkedHashSet<>(a.keySet());
		s.removeAll(b.keySet());
		return s;
	}
}
