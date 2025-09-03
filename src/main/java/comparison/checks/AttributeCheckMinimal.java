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

            String where = className + "#" + name;

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

            // Type
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

            // Visibility (written vs omitted)
            String uVis = VisibilityRules.vis(U.getVisibility());
            String cVis = VisibilityRules.vis(C.getVisibility());
            if (!uVis.equals("~")) {
                if (!VisibilityRules.equalStrict(uVis, cVis)) {
                    out.add(new Difference(
                            IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.WARNING,
                            where,
                            "Attribute visibility differs",
                            uVis, cVis,
                            "Match UML visibility to code (or omit visibility)"
                    ));
                }
            } else {
                out.add(new Difference(
                        IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.SUGGESTION,
                        where,
                        "Attribute visibility omitted in UML",
                        "omitted", cVis,
                        "Optionally show the visibility"
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
                    className + "#" + name,
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
}
