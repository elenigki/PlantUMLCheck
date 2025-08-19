package comparison.issues;

/** Small helpers to create consistent Difference lines. */
public final class IssueFactory {
  private IssueFactory() {}

  private static final String MISSING = "missing";
  private static String ns(String s) { return (s == null || s.isBlank()) ? "—" : s.trim(); }

  public static Difference classMissingInUml(String name) {
    return new Difference(IssueKind.CLASS_MISSING_IN_UML, IssueLevel.ERROR, name,
        "Class missing in UML", MISSING, "present", "Add class to UML");
  }
  public static Difference classMissingInCode(String name) {
    return new Difference(IssueKind.CLASS_MISSING_IN_CODE, IssueLevel.ERROR, name,
        "Class missing in source code", "present", MISSING, "Add class in code or remove from UML");
  }

  public static Difference attrMissingInUml(String cls, String name, boolean relaxed) {
    return new Difference(IssueKind.ATTRIBUTE_MISSING_IN_UML, relaxed?IssueLevel.INFO:IssueLevel.ERROR,
        cls + ".attr:" + name, "Attribute missing in UML", MISSING, "present",
        relaxed? "Optionally add to UML" : "Add to UML to match code");
  }
  public static Difference attrMissingInCode(String cls, String name) {
    return new Difference(IssueKind.ATTRIBUTE_MISSING_IN_CODE, IssueLevel.ERROR,
        cls + ".attr:" + name, "Attribute missing in source code", "present", MISSING,
        "Add attribute in code or remove from UML");
  }
  public static Difference attrTypeMismatch(String cls, String name, String umlType, String codeType) {
    return new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR,
        cls + ".attr:" + name, "Attribute type mismatch", ns(umlType), ns(codeType),
        "Align UML type with code");
  }
  public static Difference attrVisibilityStrict(String cls, String name, String umlVis, String codeVis) {
    return new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.ERROR,
        cls + ".attr:" + name, "Attribute visibility mismatch", ns(umlVis), ns(codeVis),
        "Match UML and code visibility");
  }
  public static Difference attrVisibilityWeakerInCode(String cls, String name, String umlVis, String codeVis) {
    return new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.WARNING,
        cls + ".attr:" + name, "Code visibility is weaker than UML", ns(umlVis), ns(codeVis),
        "Consider widening code or relaxing UML");
  }
  public static Difference attrVisibilityStrongerInCode(String cls, String name, String umlVis, String codeVis) {
    return new Difference(IssueKind.ATTRIBUTE_MISMATCH, IssueLevel.INFO,
        cls + ".attr:" + name, "Code visibility is stronger than UML", ns(umlVis), ns(codeVis),
        "Optionally align UML to code");
  }

  // similar factories will be added later for methods/relationships
}
