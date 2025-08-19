package comparison.rules;

import model.Method;
import java.util.List;

/** Builds method signatures like foo(int,String). */
public final class SignatureRules {
  private SignatureRules() {}
  public static String signatureOf(Method m) {
    StringBuilder sb = new StringBuilder();
    sb.append(m.getName()).append("(");
    List<String> ps = m.getParameters();
    if (ps != null) for (int i=0;i<ps.size();i++) {
      if (i>0) sb.append(",");
      sb.append(TypeRules.norm(ps.get(i)));
    }
    sb.append(")");
    return sb.toString();
  }
}
