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

/** Attribute presence and contracts inside one class. */
public final class AttributeCheck {

    /** Compares attributes for one class. */
    public static List<Difference> compareAttributesInClass(String className,
                                                            ClassInfo codeC,
                                                            ClassInfo umlC,
                                                            CheckMode mode) {
        List<Difference> out = new ArrayList<>();
        Map<String, Attribute> ca = byName(codeC.getAttributes());
        Map<String, Attribute> ua = byName(umlC.getAttributes());

        // UML -> Code (missing in code or mismatches)
        for (String name : ua.keySet()) {
            Attribute U = ua.get(name);
            Attribute C = ca.get(name);

            if (C == null) {
                out.add(new Difference(
                    IssueKind.ATTRIBUTE_MISSING_IN_CODE,
                    IssueLevel.ERROR,
                    className + ".attr:" + name,
                    "Attribute missing in source code",
                    "present", "missing",
                    "Add attribute in code or remove from UML"
                ));
                continue;
            }

            // type check
            boolean typeOk = (mode == CheckMode.STRICT)
                    ? TypeRules.equalStrict(C.getType(), U.getType())
                    : TypeRules.equalRelaxed(C.getType(), U.getType());

            if (!typeOk) {
                out.add(new Difference(
                    IssueKind.ATTRIBUTE_MISMATCH,
                    IssueLevel.ERROR,
                    className + ".attr:" + name,
                    "Attribute type mismatch",
                    ns(U.getType()), ns(C.getType()),
                    "Align UML type with code"
                ));
            }

            // visibility check
            String uVis = VisibilityRules.vis(U.getVisibility());
            String cVis = VisibilityRules.vis(C.getVisibility());

            if (mode == CheckMode.STRICT) {
                if (!VisibilityRules.equalStrict(uVis, cVis)) {
                    out.add(new Difference(
                        IssueKind.ATTRIBUTE_MISMATCH,
                        IssueLevel.ERROR,
                        className + ".attr:" + name,
                        "Attribute visibility mismatch",
                        uVis, cVis,
                        "Match UML and code visibility"
                    ));
                }
            } else {
                if (!VisibilityRules.okRelaxed(uVis, cVis)) {
                    // code is less visible than UML → warning
                    out.add(new Difference(
                        IssueKind.ATTRIBUTE_MISMATCH,
                        IssueLevel.WARNING,
                        className + ".attr:" + name,
                        "Code visibility is weaker than UML",
                        uVis, cVis,
                        "Consider widening code or relaxing UML"
                    ));
                } else if (!uVis.equals(cVis) && VisibilityRules.moreVisible(cVis, uVis)) {
                    // code is more visible than UML → info
                    out.add(new Difference(
                        IssueKind.ATTRIBUTE_MISMATCH,
                        IssueLevel.INFO,
                        className + ".attr:" + name,
                        "Code visibility is stronger than UML",
                        uVis, cVis,
                        "Optionally align UML to code"
                    ));
                }
            }
        }

        // Code -> UML (missing in UML)
        for (String name : ca.keySet()) {
            if (!ua.containsKey(name)) {
                IssueLevel lvl = (mode == CheckMode.RELAXED) ? IssueLevel.INFO : IssueLevel.ERROR;
                out.add(new Difference(
                    IssueKind.ATTRIBUTE_MISSING_IN_UML,
                    lvl,
                    className + ".attr:" + name,
                    "Attribute missing in UML",
                    "missing", "present",
                    (mode == CheckMode.RELAXED) ? "Optionally add to UML" : "Add to UML to match code"
                ));
            }
        }

        return out;
    }

    /** Builds name -> Attribute for quick lookups in a class. */
    static Map<String, Attribute> byName(List<Attribute> list) {
        Map<String, Attribute> m = new LinkedHashMap<>();
        if (list == null) return m;
        for (Attribute a : list) {
            if (a == null) continue;
            String n = a.getName();
            if (n == null) continue;
            m.putIfAbsent(n, a); // first wins
        }
        return m;
    }

    private static String ns(String s) { return (s == null || s.isBlank()) ? "—" : s.trim(); }
}
