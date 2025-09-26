package comparison.rules;

/** Visibility comparison helpers. */
public final class VisibilityRules {
  private VisibilityRules() {}

  /** Returns normalized visibility. Blank/null is treated as package (~). */
  public static String vis(String v) { return (v==null||v.isBlank()) ? "~" : v.trim(); }

  /** Strict equality on normalized vis. */
  public static boolean equalStrict(String a, String b) { return vis(a).equals(vis(b)); }

  /** True iff the *raw* UML visibility was omitted (null/blank). */
  public static boolean omittedRaw(String v) { return v == null || v.trim().isEmpty(); }

  /** Is left strictly more visible (publicer) than right? */
  public static boolean moreVisible(String left, String right) { return rankOf(left) < rankOf(right); }

  /** Public rank for reuse in checkers. Smaller = more public. */
  public static int rankOf(String v) {
    switch (vis(v)) {
      case "+": return 0; // public
      case "#": return 1; // protected
      case "~": return 2; // package
      case "-": return 3; // private
      default:  return 4; // unknown
    }
  }

  /**
   * @deprecated Old policy (“RELAXED allows code to be more open”) is no longer used.
   * Prefer explicit comparisons using rankOf/moreVisible and mode policy in checkers.
   */
  @Deprecated
  public static boolean okRelaxed(String umlVis, String codeVis) {
    String u=vis(umlVis), c=vis(codeVis);
    return u.equals(c) || moreVisible(c,u);
  }
}
