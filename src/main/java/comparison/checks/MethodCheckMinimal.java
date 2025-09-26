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
 * MINIMAL member rules (formerly RELAXED_PLUS):
 * - If UML WRITES return/params and they differ -> ERROR.
 * - If UML OMITS return/params -> SUGGESTION (abstraction is allowed).
 * - Name must match exactly when present.
 * - Code-only methods: SUGGESTION for public/protected, INFO otherwise.
 * - Static: if UML writes it, it must match; if omitted and code is static => SUGGESTION.
 */
public final class MethodCheckMinimal {

    private MethodCheckMinimal() {}

    public static List<Difference> compareMethodsInClass(String className,
                                                         ClassInfo codeC,
                                                         ClassInfo umlC,
                                                         CheckMode mode) {
        if (mode != CheckMode.MINIMAL) {
            return List.of();
        }

        List<Difference> out = new ArrayList<>();

        List<Method> codeMethods = codeC == null ? List.of() : safeList(codeC.getMethods());
        List<Method> umlMethods  = umlC  == null ? List.of() : safeList(umlC.getMethods());

        Map<String, Method> codeBySig = bySignature(codeMethods);
        Map<String, Method> umlBySig  = bySignature(umlMethods);

        // Track UML methods that specify only a name (params omitted)
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
            if (name.isEmpty()) continue;

            List<String> up = U.getParameters();
            boolean paramsOmitted = (up == null);

            if (paramsOmitted) {
                Method C = firstByName(codeMethods, name);
                String whereNameOnly = className + "#" + name + "(…)";

                if (C == null) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISSING_IN_CODE, IssueLevel.ERROR,
                            whereNameOnly,
                            "Method name present in UML but not found in code",
                            "present", "missing",
                            "Remove from UML or add method in code"
                    ));
                    continue;
                }

                // Params omitted -> SUGGESTION
                out.add(new Difference(
                        IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                        whereNameOnly,
                        "Parameters omitted in UML",
                        "omitted", SignatureRules.signatureOf(C),
                        "Consider documenting parameter types/arity"
                ));

                // Return type handling when name-only (ambiguous overloads)
                String uret = ns(U.getReturnType());
                String cret = ns(C.getReturnType());
                if (uret.isEmpty()) {
                    if (!cret.equals("void") && !cret.isEmpty()) {
                        out.add(new Difference(
                                IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                                whereNameOnly,
                                "Return type omitted in UML",
                                "omitted", cret,
                                "Consider documenting the return type"
                        ));
                    }
                } else if (!TypeRules.equalStrict(uret, cret)) {
                    // Ambiguous due to omitted params -> keep it a SUGGESTION, not an ERROR
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                            whereNameOnly,
                            "UML return type may not match the code overload",
                            uret, cret,
                            "Prefer omitting or matching a specific overload"
                    ));
                }

                // Visibility (directional) when name-only — detect omission BEFORE normalizing
                String cVis = VisibilityRules.vis(C.getVisibility());
                if (omitted(U.getVisibility())) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                            whereNameOnly,
                            "Method visibility omitted in UML",
                            "omitted", cVis,
                            "Optionally show the visibility"
                    ));
                } else {
                    String uVis = VisibilityRules.vis(U.getVisibility());
                    int ur = rank(uVis), cr = rank(cVis);
                    if (ur > cr) {
                        out.add(new Difference(
                                IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
                                whereNameOnly,
                                "UML visibility is more restrictive than code",
                                uVis, cVis,
                                "Make UML at least as visible as the code"
                        ));
                    } else if (ur < cr) {
                        out.add(new Difference(
                                IssueKind.METHOD_MISMATCH, IssueLevel.WARNING,
                                whereNameOnly,
                                "UML visibility is less restrictive than code",
                                uVis, cVis,
                                "Consider aligning UML visibility with code"
                        ));
                    }
                }

                // Static (name-only): if omitted and code static => SUGGESTION; if written and differs => ERROR
                Boolean uStat = U.isStatic();   // may be null if omitted in UML
                Boolean cStat = C.isStatic();   // expected non-null on code side
                boolean codeStatic = Boolean.TRUE.equals(cStat);
                if (uStat == null) {
                    if (codeStatic) {
                        out.add(new Difference(
                                IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                                whereNameOnly,
                                "Static modifier omitted in UML for a static method",
                                "static: omitted", "static: true",
                                "Mark the UML method as static"
                        ));
                    }
                } else if (uStat.booleanValue() != codeStatic) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
                            whereNameOnly,
                            "Static modifier mismatch between UML and code",
                            "static: " + uStat, "static: " + codeStatic,
                            "Make UML static match the code"
                    ));
                }

                continue;
            }

            // Params written: exact signature must exist
            String sigU = SignatureRules.signatureOf(U);
            Method C = codeBySig.get(sigU);
            String where = className + "#" + sigU;

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

            // Return written must match exactly
            String uret = ns(U.getReturnType());
            String cret = ns(C.getReturnType());
            if (!uret.isEmpty()) {
                if (!TypeRules.equalStrict(uret, cret)) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
                            where,
                            "Return type differs (MINIMAL requires exact match when written)",
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

            // Visibility (directional) — detect omission BEFORE normalizing
            String cVis = VisibilityRules.vis(C.getVisibility());
            if (omitted(U.getVisibility())) {
                out.add(new Difference(
                        IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                        where,
                        "Method visibility omitted in UML",
                        "omitted", cVis,
                        "Optionally show the visibility"
                ));
            } else {
                String uVis = VisibilityRules.vis(U.getVisibility());
                int ur = rank(uVis), cr = rank(cVis);
                if (ur > cr) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
                            where,
                            "UML visibility is more restrictive than code",
                            uVis, cVis,
                            "Make UML at least as visible as the code"
                    ));
                } else if (ur < cr) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.WARNING,
                            where,
                            "UML visibility is less restrictive than code",
                            uVis, cVis,
                            "Consider aligning UML visibility with code"
                    ));
                }
            }

            // Static (expects Method.isStatic(): Boolean or boxed Boolean)
            Boolean uStat = U.isStatic();   // may be null (omitted in UML)
            Boolean cStat = C.isStatic();   // expected non-null
            boolean codeStatic = Boolean.TRUE.equals(cStat);

            if (uStat == null) {
                if (codeStatic) {
                    out.add(new Difference(
                            IssueKind.METHOD_MISMATCH, IssueLevel.SUGGESTION,
                            where,
                            "Static modifier omitted in UML for a static method",
                            "static: omitted", "static: true",
                            "Mark the UML method as static"
                    ));
                }
            } else if (uStat.booleanValue() != codeStatic) {
                out.add(new Difference(
                        IssueKind.METHOD_MISMATCH, IssueLevel.ERROR,
                        where,
                        "Static modifier mismatch between UML and code",
                        "static: " + uStat, "static: " + codeStatic,
                        "Make UML static match the code"
                ));
            }
        }

        // --- Code -> UML (missing in UML)
        Set<String> matchedExact = umlBySig.keySet();
        for (Method C : codeMethods) {
            String sigC = SignatureRules.signatureOf(C);
            String name = safe(C.getName());
            if (matchedExact.contains(sigC)) continue;
            if (umlNameOnly.contains(name)) continue;

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
            String sig = SignatureRules.signatureOf(me);
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

    // treat raw omission before normalizing
    private static boolean omitted(String v) { return v == null || v.trim().isEmpty(); }

    // smaller is "more public": + (0) < # (1) < ~ (2) < - (3)
    private static int rank(String v) {
        if (v == null || v.isBlank()) return 4;
        String t = v.trim();
        return switch (t) {
            case "+" -> 0;   // public
            case "#" -> 1;   // protected
            case "~" -> 2;   // package
            case "-" -> 3;   // private
            default -> 4;    // unknown/other
        };
    }
}
