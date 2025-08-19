package comparison.rules;

/** Visibility comparison helpers. */
public final class VisibilityRules {
  private VisibilityRules() {}
  public static String vis(String v) { return (v==null||v.isBlank()) ? "~" : v.trim(); }

  public static boolean equalStrict(String a, String b) { return vis(a).equals(vis(b)); }

  public static boolean okRelaxed(String umlVis, String codeVis) {
    String u=vis(umlVis), c=vis(codeVis);
    return u.equals(c) || moreVisible(c,u); // allow code to be more open
  }

  public static boolean moreVisible(String left, String right) { return rank(left) < rank(right); }

  private static int rank(String v) {
    switch (vis(v)) {
      case "+": return 0; // public
      case "#": return 1; // protected
      case "~": return 2; // package
      case "-": return 3; // private
      default:  return 4;
    }
  }
}
