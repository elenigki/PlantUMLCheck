package comparison.issues;

public final class Difference {
  private final IssueKind kind;     // what type of issue this is
  private final IssueLevel level;   // how serious it is
  private final String where;       // where it happened (e.g. "Order.attr:email")
  private final String summary;     // short human line (e.g. "Attribute type mismatch")

  // side-by-side values (fill when it helps; leave null if not applicable)
  private final String uml;         // value from UML side (e.g. "java.lang.String")
  private final String code;        // value from Code side (e.g. "String")

  // optional extra hint for the human (one-liner)
  private final String tip;         // e.g. "Consider aligning UML to Code."

  public Difference(IssueKind kind, IssueLevel level, String where,
                    String summary, String uml, String code, String tip) {
    this.kind = kind; this.level = level; this.where = where;
    this.summary = summary; this.uml = uml; this.code = code; this.tip = tip;
  }

  public IssueKind getKind()   { return kind; }
  public IssueLevel getLevel() { return level; }
  public String getWhere()     { return where; }
  public String getSummary()   { return summary; }
  public String getUml()       { return uml; }
  public String getCode()      { return code; }
  public String getTip()       { return tip; }
}
