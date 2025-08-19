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

/** Method presence and contracts inside one class. */
public final class MethodCheck {

    /** Compares methods for one class. */
    public static List<Difference> compareMethodsInClass(String className,
                                                         ClassInfo codeC,
                                                         ClassInfo umlC,
                                                         CheckMode mode) {
        List<Difference> out = new ArrayList<>();
        Map<String, Method> cm = bySignature(codeC.getMethods()); // strict sig -> code method
        Map<String, Method> um = bySignature(umlC.getMethods());  // strict sig -> uml method

        // UML -> Code (missing in code or mismatches)
        for (String sigU : um.keySet()) {
            Method U = um.get(sigU);
            Method C = cm.get(sigU);

            // relaxed: try to find a compatible overload when exact sig not found
            if (C == null && mode == CheckMode.RELAXED) {
                C = findRelaxedMatch(codeC.getMethods(), U);
            }

            if (C == null) {
                out.add(new Difference(
                        IssueKind.METHOD_MISSING_IN_CODE, IssueLevel.ERROR,
                        className + "#" + sigU,                // where
                        "Method missing in source code",        // summary
                        "present", "missing",                   // uml, code
                        "Add method in code or remove from UML" // tip
                ));
                continue;
            }

            // return type check
            boolean retOk = (mode == CheckMode.STRICT)
                    ? TypeRules.equalStrict(C.getReturnType(), U.getReturnType())
                    : TypeRules.equalRelaxed(C.getReturnType(), U.getReturnType());
            if (!retOk) {
                out.add(new Difference(
                        IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
                        className + "#" + SignatureRules.signatureOf(U),
                        "Method return type mismatch",
                        ns(U.getReturnType()), ns(C.getReturnType()),
                        "Align UML return type with code"
                ));
            }

            // visibility check
            String uVis = VisibilityRules.vis(U.getVisibility());
            String cVis = VisibilityRules.vis(C.getVisibility());

            if (mode == CheckMode.STRICT) {
                if (!VisibilityRules.equalStrict(uVis, cVis)) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
                            className + "#" + SignatureRules.signatureOf(U),
                            "Method visibility mismatch",
                            uVis, cVis,
                            "Match UML and code visibility"
                    ));
                }
            } else {
                if (!VisibilityRules.okRelaxed(uVis, cVis)) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.WARNING,
                            className + "#" + SignatureRules.signatureOf(U),
                            "Code visibility is weaker than UML",
                            uVis, cVis,
                            "Consider widening code or relaxing UML"
                    ));
                } else if (!uVis.equals(cVis) && VisibilityRules.moreVisible(cVis, uVis)) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.INFO,
                            className + "#" + SignatureRules.signatureOf(U),
                            "Code visibility is stronger than UML",
                            uVis, cVis,
                            "Optionally align UML to code"
                    ));
                }
            }
        }

        // Code -> UML (suppress symmetric "missing" for related overloads)
        for (String sigC : cm.keySet()) {
            if (um.containsKey(sigC)) continue; // exact present

            Method C = cm.get(sigC);

            if (mode == CheckMode.RELAXED) {
                // relaxed: skip if a compatible overload exists on UML
                if (umlHasAnyRelaxedMatch(umlC.getMethods(), C)) continue;
                // relaxed: also skip if same name+arity exists (already matched another overload)
                if (umlHasSameNameArity(umlC.getMethods(), C)) continue;
            } else {
                // strict: skip symmetric missing if UML has same name+arity overload
                if (umlHasSameNameArity(umlC.getMethods(), C)) continue;
            }

            IssueLevel lvl = (mode == CheckMode.RELAXED) ? IssueLevel.INFO : IssueLevel.ERROR;
            out.add(new Difference(
                    IssueKind.METHOD_MISSING_IN_UML, lvl,
                    className + "#" + sigC,
                    "Method missing in UML",
                    "missing", "present",
                    (mode == CheckMode.RELAXED) ? "Optionally add to UML" : "Add to UML to match code"
            ));
        }

        return out;
    }

    /** Builds signature -> Method for quick lookups in a class. */
    static Map<String, Method> bySignature(List<Method> list) {
        Map<String, Method> m = new LinkedHashMap<>();
        if (list == null) return m;
        for (Method me : list) {
            if (me == null) continue;
            m.putIfAbsent(signatureOf(me), me); // first wins
        }
        return m;
    }

    /** Makes a method signature like foo(int,String). */
    static String signatureOf(Method m) {
        return SignatureRules.signatureOf(m);
    }

    // --- relaxed matching helpers ---

    /** Finds a code method that matches UML by name+arity and relaxed param types. */
    static Method findRelaxedMatch(List<Method> codeMethods, Method umlMethod) {
        if (codeMethods == null || umlMethod == null) return null;
        for (Method c : codeMethods) {
            if (c == null) continue;
            if (!safe(c.getName()).equals(safe(umlMethod.getName()))) continue;
            List<String> cp = c.getParameters();
            List<String> up = umlMethod.getParameters();
            int cn = cp == null ? 0 : cp.size();
            int un = up == null ? 0 : up.size();
            if (cn != un) continue;
            boolean allOk = true;
            for (int i = 0; i < cn; i++) {
                if (!TypeRules.equalRelaxed(cp.get(i), up.get(i))) { allOk = false; break; }
            }
            if (allOk) return c;
        }
        return null;
    }

    /** True if UML has any method matching this code method under relaxed rules. */
    static boolean umlHasAnyRelaxedMatch(List<Method> umlMethods, Method codeMethod) {
        return umlHasRelaxedMatch(umlMethods, codeMethod);
    }

    /** True if UML has same name and arity (used to suppress symmetric missing). */
    static boolean umlHasSameNameArity(List<Method> umlMethods, Method codeMethod) {
        if (umlMethods == null || codeMethod == null) return false;
        int cn = codeMethod.getParameters() == null ? 0 : codeMethod.getParameters().size();
        String name = safe(codeMethod.getName());
        for (Method u : umlMethods) {
            if (u == null) continue;
            if (!safe(u.getName()).equals(name)) continue;
            int un = u.getParameters() == null ? 0 : u.getParameters().size();
            if (un == cn) return true;
        }
        return false;
    }

    /** True if UML has a relaxed match for the given code method. */
    static boolean umlHasRelaxedMatch(List<Method> umlMethods, Method codeMethod) {
        if (umlMethods == null || codeMethod == null) return false;
        for (Method u : umlMethods) {
            if (u == null) continue;
            if (!safe(u.getName()).equals(safe(codeMethod.getName()))) continue;
            List<String> cp = codeMethod.getParameters();
            List<String> up = u.getParameters();
            int cn = cp == null ? 0 : cp.size();
            int un = up == null ? 0 : up.size();
            if (cn != un) continue;
            boolean allOk = true;
            for (int i = 0; i < cn; i++) {
                if (!TypeRules.equalRelaxed(cp.get(i), up.get(i))) { allOk = false; break; }
            }
            if (allOk) return true;
        }
        return false;
    }

    // --- tiny utils ---

    private static String ns(String s) { return (s == null || s.isBlank()) ? "—" : s.trim(); }
    private static String safe(String s) { return (s == null) ? "" : s.trim(); }
}
