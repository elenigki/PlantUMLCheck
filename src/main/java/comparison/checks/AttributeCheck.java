package comparison.checks;

import comparison.CheckMode;
import comparison.issues.Difference;
import comparison.issues.IssueKind;
import comparison.issues.IssueLevel;
import comparison.rules.TypeRules;
import comparison.rules.VisibilityRules;
import model.Attribute;
import model.ClassInfo;

import java.util.*;

public final class AttributeCheck {

	// Compares attributes for one class. (Used for STRICT and RELAXED; MINIMAL uses AttributeCheckMinimal)
	public static List<Difference> compareAttributesInClass(String className, ClassInfo codeC, ClassInfo umlC,
			CheckMode mode) {
		List<Difference> out = new ArrayList<>();
		Map<String, Attribute> ca = byName(codeC.getAttributes());
		Map<String, Attribute> ua = byName(umlC.getAttributes());

		// UML -> Code (missing in code or mismatches)
		for (String name : ua.keySet()) {
			Attribute U = ua.get(name);
			Attribute C = ca.get(name);

			if (C == null) {
				out.add(new Difference(IssueKind.ATTRIBUTE_MISSING_IN_CODE, IssueLevel.ERROR,
						className + ".attr:" + name, "Attribute present in UML but missing in source code", "present",
						"missing", "Add the attribute in code or remove it from UML"));
				continue;
			}

			// ---- type check (RELAXED == STRICT for members)
			boolean typeOk = TypeRules.equalStrict(C.getType(), U.getType());
			if (!typeOk) {
				out.add(new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR, className + ".attr:" + name,
						"Attribute type mismatch", ns(U.getType()), ns(C.getType()), "Align UML type with code"));
			}

			// ---- visibility check (directional)
			String uVis = VisibilityRules.vis(U.getVisibility());
			String cVis = VisibilityRules.vis(C.getVisibility());

			if (uVis.isEmpty()) {
				// In STRICT/RELAXED, visibility omission is ERROR
				out.add(new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR, className + ".attr:" + name,
						"Attribute visibility omitted in UML", "—", cVis,
						"Specify the UML visibility to match the code"));
			} else {
				int ur = rank(uVis), cr = rank(cVis);
				if (ur > cr) {
					// UML is less public than code --> ERROR
					out.add(new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR, className + ".attr:" + name,
							"UML visibility is more restrictive than code", uVis, cVis,
							"Make UML at least as visible as the code"));
				} else if (ur < cr) {
					// UML is more public than code --> WARNING
					out.add(new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.WARNING,
							className + ".attr:" + name, "UML visibility is less restrictive than code", uVis, cVis,
							"Consider aligning UML visibility with code"));
				}
				// equal --> OK
			}

			// ---- static check (expects Attribute#getIsStatic(): Boolean)
			// Rule: if code is static and UML omits static --> ERROR (STRICT/RELAXED)
			// if UML writes static and it differs from code --> ERROR
			Boolean uStat = U.isStatic(); // may be null (omitted in UML)
			Boolean cStat = C.isStatic(); // expected non-null true/false on code side
			boolean codeStatic = Boolean.TRUE.equals(cStat);

			if (uStat.booleanValue() != codeStatic) {
				out.add(new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR, className + ".attr:" + name,
						"Static modifier mismatch between UML and code", "static: " + uStat, "static: " + codeStatic,
						"Make UML static match the code"));
			}
		}

		// Code --> UML (missing in UML) — RELAXED == STRICT for members
		for (String name : ca.keySet()) {
			if (!ua.containsKey(name)) {
				out.add(new Difference(IssueKind.ATTRIBUTE_MISSING_IN_UML, IssueLevel.ERROR,
						className + ".attr:" + name, "Attribute missing in UML", "missing", "present",
						"Add the attribute to UML to match the code"));
			}
		}

		return out;
	}

	// Builds name --> Attribute for quick lookups in a class.
	static Map<String, Attribute> byName(List<Attribute> list) {
		Map<String, Attribute> m = new LinkedHashMap<>();
		if (list == null)
			return m;
		for (Attribute a : list) {
			if (a == null)
				continue;
			String n = a.getName();
			if (n == null)
				continue;
			m.putIfAbsent(n, a); // first wins
		}
		return m;
	}

	private static String ns(String s) {
		return (s == null || s.isBlank()) ? "—" : s.trim();
	}

	// smaller is "more public": + (0) < # (1) < ~ (2) < - (3)
	private static int rank(String v) {
		if (v == null || v.isBlank())
			return 4;
		String t = v.trim();
		return switch (t) {
		case "+" -> 0; // public
		case "#" -> 1; // protected
		case "~" -> 2; // package
		case "-" -> 3; // private
		default -> 4; // unknown
		};
	}
}
