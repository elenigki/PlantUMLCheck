package comparison.checks;

import comparison.CheckMode;
import comparison.issues.Difference;
import comparison.issues.IssueKind;
import comparison.issues.IssueLevel;
import comparison.rules.SignatureRules;
import comparison.rules.TypeRules;
import comparison.rules.VisibilityRules;
import model.ClassInfo;
import model.Method;

import java.util.*;

public final class MethodCheck {

	//Compares methods for one class. (Used for STRICT and RELAXED; MINIMAL uses MethodCheckMinimal)
	public static List<Difference> compareMethodsInClass(String className, ClassInfo codeC, ClassInfo umlC,
			CheckMode mode) {
		List<Difference> out = new ArrayList<>();
		Map<String, Method> cm = bySignature(codeC.getMethods()); // strict sig -> code method
		Map<String, Method> um = bySignature(umlC.getMethods()); // strict sig -> uml method

		// UML -> Code (missing in code or mismatches)
		for (String sigU : um.keySet()) {
			Method U = um.get(sigU);
			Method C = cm.get(sigU); // RELAXED == STRICT: no relaxed lookup

			if (C == null) {
				out.add(new Difference(IssueKind.METHOD_MISSING_IN_CODE, IssueLevel.ERROR, className + "#" + sigU,
						"Method present in UML but missing in source code", "present", "missing",
						"Add the method in code or remove it from UML"));
				continue;
			}

			// ---- return type check (RELAXED == STRICT for members)
			boolean retOk = TypeRules.equalStrict(C.getReturnType(), U.getReturnType());
			if (!retOk) {
				out.add(new Difference(IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
						className + "#" + SignatureRules.signatureOf(U), "Method return type mismatch",
						ns(U.getReturnType()), ns(C.getReturnType()), "Align UML return type with code"));
			}

			// ---- visibility check (directional)
			String uVis = VisibilityRules.vis(U.getVisibility());
			String cVis = VisibilityRules.vis(C.getVisibility());

			if (uVis.isEmpty()) {
				out.add(new Difference(IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
						className + "#" + SignatureRules.signatureOf(U), "Method visibility omitted in UML", "—", cVis,
						"Specify the UML visibility to match the code"));
			} else {
				int ur = rank(uVis), cr = rank(cVis);
				if (ur > cr) {
					// UML less public than code → ERROR
					out.add(new Difference(IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
							className + "#" + SignatureRules.signatureOf(U),
							"UML visibility is more restrictive than code", uVis, cVis,
							"Make UML at least as visible as the code"));
				} else if (ur < cr) {
					// UML more public than code → WARNING
					out.add(new Difference(IssueKind.METHOD_MISMATCH, IssueLevel.WARNING,
							className + "#" + SignatureRules.signatureOf(U),
							"UML visibility is less restrictive than code", uVis, cVis,
							"Consider aligning UML visibility with code"));
				}
				// equal → OK
			}

			// ---- static check (expects Method.isStatic(): Boolean)
			// Rule: if code is static and UML omits static → ERROR (STRICT/RELAXED)
			// if UML writes static and it differs from code → ERROR
			Boolean uStat = U.isStatic(); // may be null (omitted in UML)
			Boolean cStat = C.isStatic(); // expected non-null true/false on code side
			boolean codeStatic = Boolean.TRUE.equals(cStat);
			if (uStat.booleanValue() != codeStatic) {
				out.add(new Difference(IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
						className + "#" + SignatureRules.signatureOf(U),
						"Static modifier mismatch between UML and code", "static: " + uStat, "static: " + codeStatic,
						"Make UML static match the code"));
			}
		}

		// Code -> UML (missing in UML)
		for (String sigC : cm.keySet()) {
			if (um.containsKey(sigC))
				continue; // exact present

			Method C = cm.get(sigC);

			// Keep the classic suppression: if UML has same name+arity overload, skip
			// symmetric missing.
			if (umlHasSameNameArity(umlC.getMethods(), C))
				continue;

			// RELAXED == STRICT: presence mismatch is ERROR
			out.add(new Difference(IssueKind.METHOD_MISSING_IN_UML, IssueLevel.ERROR, className + "#" + sigC,
					"Method missing in UML", "missing", "present", "Add the method to UML to match the code"));
		}

		return out;
	}

	// Builds signature -> Method for quick lookups in a class.
	static Map<String, Method> bySignature(List<Method> list) {
		Map<String, Method> m = new LinkedHashMap<>();
		if (list == null)
			return m;
		for (Method me : list) {
			if (me == null)
				continue;
			m.putIfAbsent(signatureOf(me), me); // first wins
		}
		return m;
	}

	// Makes a method signature like foo(int,String).
	static String signatureOf(Method m) {
		return SignatureRules.signatureOf(m);
	}

	// True if UML has same name and arity (used to suppress symmetric missing).
	static boolean umlHasSameNameArity(List<Method> umlMethods, Method codeMethod) {
		if (umlMethods == null || codeMethod == null)
			return false;
		int cn = codeMethod.getParameters() == null ? 0 : codeMethod.getParameters().size();
		String name = safe(codeMethod.getName());
		for (Method u : umlMethods) {
			if (u == null)
				continue;
			if (!safe(u.getName()).equals(name))
				continue;
			int un = u.getParameters() == null ? 0 : u.getParameters().size();
			if (un == cn)
				return true;
		}
		return false;
	}

	// --- tiny utils ---

	private static String ns(String s) {
		return (s == null || s.isBlank()) ? "—" : s.trim();
	}

	private static String safe(String s) {
		return (s == null) ? "" : s.trim();
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
