package comparison;

import comparison.checks.AttributeCheck;
import comparison.checks.AttributeCheckPlus;
import comparison.checks.ClassCheck;
import comparison.checks.MethodCheck;
import comparison.checks.MethodCheckPlus;
import comparison.checks.RelationshipCheck;
import comparison.issues.Difference;
import model.ClassInfo;
import model.IntermediateModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Compares code vs UML and collects differences. */
public class ModelComparator {

    private final CheckMode mode;  // STRICT, RELAXED, RELAXED_PLUS

    public ModelComparator(CheckMode mode) {
        this.mode = mode;
    }

    public List<Difference> compare(IntermediateModel code, IntermediateModel uml) {
        List<Difference> out = new ArrayList<>();

        // ---- classes (presence/name/etc)
        out.addAll(ClassCheck.compareClasses(code, uml, mode));

        // ---- per-class member checks
        Set<String> common = ClassCheck.commonClassNames(code, uml);
        for (String cls : common) {
            ClassInfo cc = ClassCheck.classFrom(code, cls);
            ClassInfo uc = ClassCheck.classFrom(uml, cls);

            if (mode == CheckMode.RELAXED) {
                // RELAXED = strict rules for members (exact),
                // relaxation applies mainly to relationships
                out.addAll(AttributeCheck.compareAttributesInClass(cls, cc, uc, CheckMode.STRICT));
                out.addAll(MethodCheck.compareMethodsInClass(cls, cc, uc, CheckMode.STRICT));
            } else if (mode == CheckMode.RELAXED_PLUS) {
                // RELAXED_PLUS = softer on omissions, but written mismatches are errors
                out.addAll(AttributeCheckPlus.compareAttributesInClass(cls, cc, uc, mode));
                out.addAll(MethodCheckPlus.compareMethodsInClass(cls, cc, uc, mode));
            } else { // STRICT
                out.addAll(AttributeCheck.compareAttributesInClass(cls, cc, uc, mode));
                out.addAll(MethodCheck.compareMethodsInClass(cls, cc, uc, mode));
            }
        }

        // ---- relationships (shared behavior across modes)
        out.addAll(RelationshipCheck.compareRelationships(code, uml, mode));

        return out;
    }
}
