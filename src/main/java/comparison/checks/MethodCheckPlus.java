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

/**
 * RELAXED_PLUS method comparison:
 * - If UML WRITES return/params and they differ -> ERROR.
 * - If UML OMITS return/params -> SUGGESTION (and never an error because it’s abstracting).
 * - Name must match exactly when present.
 * - Code-only methods: SUGGESTION for public/protected, INFO otherwise.
 */
public final class MethodCheckPlus {

    private MethodCheckPlus() {}

    public static List<Difference> compareMethodsInClass(String className,
                                                         ClassInfo codeC,
                                                         ClassInfo umlC,
                                                         CheckMode mode) {
        if (mode != CheckMode.RELAXED_PLUS) {
            return List.of();
        }

        List<Difference> out = new ArrayList<>();

        List<Method> codeMethods = codeC == null ? List.of() : safeList(codeC.getMethods());
        List<Method> umlMethods  = umlC  == null ? List.of() : safeList(umlC.getMethods());

        // Build exact-signature maps for quick lookup (name + explicit param types)
        Map<String, Method> codeBySig = bySignature(codeMethods);
        Map<String, Method> umlBySig  = bySignature(umlMethods);

        // Keep track of UML entries that only specify the name (params omitted),
        // so we don't double-count "missing in UML" for overloads.
        Set<String> umlNameOnly = new LinkedHashSet<>();
        for (Method u : umlMethods) {
            String name = safe(u.getName());
            List<String> ps = u.getParameters();
            boolean paramsOmitted = (ps == null);
            if (paramsOmitted && !name.isEmpty()) {
                umlNameOnly.add(name);
            }
        }

        // --- UML -> Code
        for (Method U : umlMethods) {
            String name = safe(U.getName());
            List<String> up = U.getParameters();
            String where = className + "#" + (name.isEmpty() ? "—" : SignatureRules.signatureOf(U));

            if (name.isEmpty()) continue; // nothing to validate

            boolean paramsOmitted = (up == null);
            Method C = null;

            if (paramsOmitted) {
                // Match by NAME ONLY: any code method of that name is acceptable
                C = firstByName(codeMethods, name);
                if (C == null) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISSING_IN_CODE, IssueLevel.ERROR,
                            className + "#" + name + "(…)",
                            "Method name present in UML but not found in code",
                            "present", "missing",
                            "Remove from UML or add method in code"
                    ));
                    continue;
                }

                // Params omitted -> Suggest documenting them
                out.add(new Difference(
                        IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                        className + "#" + name + "(…)",
                        "Parameters omitted in UML",
                        "omitted", SignatureRules.signatureOf(C),
                        "Consider documenting parameter types/arity"
                ));

                // Return type: if omitted and code is non-void -> SUGGESTION; if void -> OK
                String uret = ns(U.getReturnType());
                String cret = ns(C.getReturnType());
                if (uret.isEmpty()) {
                    if (!cret.equals("void") && !cret.isEmpty()) {
                        out.add(new Difference(
                                IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                                className + "#" + name + "(…)",
                                "Return type omitted in UML",
                                "omitted", cret,
                                "Consider documenting the return type"
                        ));
                    }
                } else {
                    // UML wrote a return: cannot validate against a specific overload reliably;
                    // keep it advisory, not an error.
                    if (!TypeRules.equalStrict(uret, cret)) {
                        out.add(new Difference(
                                IssueKind.METHOD_MISMATCH, IssueLevel.WARNING,
                                className + "#" + name + "(…)",
                                "UML return type may not match every overload",
                                uret, cret,
                                "Prefer omitting or matching a specific overload"
                        ));
                    }
                }

                // Visibility
                String uVis = VisibilityRules.vis(U.getVisibility());
                String cVis = VisibilityRules.vis(C.getVisibility());
                if (!uVis.equals("~")) {
                    if (!VisibilityRules.equalStrict(uVis, cVis)) {
                        out.add(new Difference(
                                IssueKind.METHOD_MISMATCH, IssueLevel.WARNING,
                                className + "#" + name + "(…)",
                                "Method visibility differs",
                                uVis, cVis,
                                "Match UML visibility to code or omit visibility"
                        ));
                    }
                } else {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                            className + "#" + name + "(…)",
                            "Method visibility omitted in UML",
                            "omitted", cVis,
                            "Optionally show the visibility"
                    ));
                }
                continue;
            }

            // Params are WRITTEN: require exact signature match
            String sigU = SignatureRules.signatureOf(U);
            C = codeBySig.get(sigU);
            if (C == null) {
                out.add(new Difference(
                        IssueKind.METHOD_MISSING_IN_CODE, IssueLevel.ERROR,
                        where,
                        "Method (signature) present in UML but missing in code",
                        "present", "missing",
                        "Remove from UML or add method with this signature in code"
                ));
                continue;
            }

            // Return type WRITTEN: must exactly match
            String uret = ns(U.getReturnType());
            String cret = ns(C.getReturnType());
            if (!uret.isEmpty()) {
                if (!TypeRules.equalStrict(uret, cret)) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
                            where,
                            "Return type differs (RELAXED+ requires exact match when written)",
                            uret, cret,
                            "Align UML return type to code"
                    ));
                }
            } else {
                if (!cret.equals("void") && !cret.isEmpty()) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                            className + "#" + name + "(…)",
                            "Return type omitted in UML",
                            "omitted", cret,
                            "Consider documenting the return type"
                    ));
                }
            }

            // Visibility
            String uVis = VisibilityRules.vis(U.getVisibility());
            String cVis = VisibilityRules.vis(C.getVisibility());
            if (!uVis.equals("~")) {
                if (!VisibilityRules.equalStrict(uVis, cVis)) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.WARNING,
                            where,
                            "Method visibility differs",
                            uVis, cVis,
                            "Match UML visibility to code (or omit visibility)"
                    ));
                }
            } else {
                out.add(new Difference(
                        IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                        where,
                        "Method visibility omitted in UML",
                        "omitted", cVis,
                        "Optionally show the visibility"
                ));
            }
        }

        // --- Code -> UML (missing in UML)
        // If UML had a name-only declaration for a method, do not flag each overload as missing.
        Set<String> matchedExact = umlBySig.keySet();

        for (Method C : codeMethods) {
            String sigC = SignatureRules.signatureOf(C);
            String name = safe(C.getName());
            if (matchedExact.contains(sigC)) continue;
            if (umlNameOnly.contains(name)) continue; // name-level acknowledgement exists

            String vis = VisibilityRules.vis(C.getVisibility());
            IssueLevel lvl = (vis.equals("+") || vis.equals("#")) ? IssueLevel.SUGGESTION : IssueLevel.INFO;

            out.add(new Difference(
                    IssueKind.METHOD_MISSING_IN_UML, lvl,
                    className + "#" + sigC,
                    "Method missing in UML",
                    "missing", "present",
                    (lvl == IssueLevel.SUGGESTION) ? "Consider adding public/protected method to UML"
                                                   : "OK to omit non-API details"
            ));
        }

        return out;
    }

    // --- helpers ---

    static Map<String, Method> bySignature(List<Method> list) {
        Map<String, Method> m = new LinkedHashMap<>();
        if (list == null) return m;
        for (Method me : list) {
            if (me == null) continue;
            String sig = SignatureRules.signatureOf(me); // uses normalized types
            m.putIfAbsent(sig, me);
        }
        return m;
    }

    static Method firstByName(List<Method> list, String name) {
        if (list == null) return null;
        for (Method me : list) {
            if (me == null) continue;
            if (safe(me.getName()).equals(name)) return me;
        }
        return null;
    }

    static List<Method> safeList(List<Method> list) {
        return (list == null) ? List.of() : list;
    }

    private static String ns(String s) { return (s == null || s.isBlank()) ? "" : s.trim(); }
    private static String safe(String s) { return (s == null) ? "" : s.trim(); }
}
