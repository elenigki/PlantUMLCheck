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

/**
 * MINIMAL member rules (formerly RELAXED_PLUS):
 * - Code is the ground truth.
 * - If UML WRITES a detail and it differs => ERROR.
 * - If UML OMITS a detail => SUGGESTION (or INFO for non-API).
 * - Name must match exactly when present (no fuzzy).
 * - Static: if UML writes it, it must match; if omitted and code is static => SUGGESTION.
 */
public final class AttributeCheckMinimal {

    private AttributeCheckMinimal() {}

    /** Compare attributes for one class under MINIMAL rules. */
    public static List<Difference> compareAttributesInClass(String className,
                                                            ClassInfo codeC,
                                                            ClassInfo umlC,
                                                            CheckMode mode) {
        if (mode != CheckMode.MINIMAL) {
            return List.of();
        }

        List<Difference> out = new ArrayList<>();
        Map<String, Attribute> cm = byName(codeC == null ? null : codeC.getAttributes());
        Map<String, Attribute> um = byName(umlC == null ? null : umlC.getAttributes());

        // --- UML -> Code (claims in UML must exist & match if written)
        for (String name : um.keySet()) {
            Attribute U = um.get(name);
            Attribute C = cm.get(name);

            String where = className + ".attr:" + name;

            if (C == null) {
                out.add(new Difference(
                        IssueKind.ATTRIBUTE_MISSING_IN_CODE, IssueLevel.ERROR,
                        where,
                        "Attribute present in UML but missing in code",
                        "present", "missing",
                        "Remove from UML or add to code"
                ));
                continue;
            }

            // ---- Type
            String ut = ns(U.getType());
            String ct = ns(C.getType());
            if (!ut.isEmpty()) {
                if (!TypeRules.equalStrict(ut, ct)) {
                    out.add(new Difference(
                            IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR,
                            where,
                            "Attribute type differs (MINIMAL requires exact match when written)",
                            ut, ct,
                            "Align the UML type to the code type"
                    ));
                }
            } else {
                out.add(new Difference(
                        IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.SUGGESTION,
                        where,
                        "Attribute type omitted in UML",
                        "omitted", ct,
                        "Consider documenting the type in UML"
                ));
            }

         // ---- Visibility (directional)
            String cVis = VisibilityRules.vis(C.getVisibility());
            if (omitted(U.getVisibility())) {
                out.add(new Difference(
                        IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.SUGGESTION,
                        where,
                        "Attribute visibility omitted in UML",
                        "omitted", cVis,
                        "Optionally show the visibility"
                ));
            } else {
                String uVis = VisibilityRules.vis(U.getVisibility());
                int ur = rank(uVis), cr = rank(cVis);
                if (ur > cr) {
                    out.add(new Difference(
                            IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR,
                            where,
                            "UML visibility is more restrictive than code",
                            uVis, cVis,
                            "Make UML at least as visible as the code"
                    ));
                } else if (ur < cr) {
                    out.add(new Difference(
                            IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.WARNING,
                            where,
                            "UML visibility is less restrictive than code",
                            uVis, cVis,
                            "Consider aligning UML visibility with code"
                    ));
                }
            }


            // ---- Static (expects Attribute.isStatic(): Boolean; UML may be null)
            Boolean uStat = U.isStatic();      // may be null (omitted in UML)
            Boolean cStat = C.isStatic();      // expected non-null true/false on code side
            boolean codeStatic = Boolean.TRUE.equals(cStat);


  
            if (uStat.booleanValue() != codeStatic) {
                out.add(new Difference(
                        IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR,
                        where,
                        "Static modifier mismatch between UML and code",
                        "static: " + uStat, "static: " + codeStatic,
                        "Make UML static match the code"
                ));
            }
        }

        // --- Code -> UML (omissions are OK; suggest documenting public/protected)
        for (String name : cm.keySet()) {
            if (um.containsKey(name)) continue; // already handled
            Attribute C = cm.get(name);
            String vis = VisibilityRules.vis(C.getVisibility());
            IssueLevel lvl = (vis.equals("+") || vis.equals("#")) ? IssueLevel.SUGGESTION : IssueLevel.INFO;

            out.add(new Difference(
                    IssueKind.ATTRIBUTE_MISSING_IN_UML, lvl,
                    className + ".attr:" + name,
                    "Attribute missing in UML",
                    "missing", "present",
                    (lvl == IssueLevel.SUGGESTION) ? "Consider adding public/protected attribute to UML"
                                                   : "OK to omit non-API details"
            ));
        }

        return out;
    }

    // --- helpers ---

    static Map<String, Attribute> byName(List<Attribute> list) {
        Map<String, Attribute> m = new LinkedHashMap<>();
        if (list == null) return m;
        for (Attribute a : list) {
            if (a == null) continue;
            String n = a.getName();
            if (n == null) continue;
            m.putIfAbsent(n, a);
        }
        return m;
    }

    private static String ns(String s) { return (s == null || s.isBlank()) ? "" : s.trim(); }

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
    
    private static boolean omitted(String v) { return v == null || v.trim().isEmpty(); }

}
