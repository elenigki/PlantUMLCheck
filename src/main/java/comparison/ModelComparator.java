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

        // ---- classes (presence/name/etc). You said this is already implemented as desired.
        out.addAll(ClassCheck.compareClasses(code, uml, mode));

        // collect common class names to run member checks
        Set<String> common = ClassCheck.commonClassNames(code, uml);

        for (String cls : common) {
            ClassInfo cc = ClassCheck.classFrom(code, cls);
            ClassInfo uc = ClassCheck.classFrom(uml, cls);

            if (mode == CheckMode.RELAXED_PLUS) {
                // RELAXED+ path: use the new, softer member checkers
                out.addAll(AttributeCheckPlus.compareAttributesInClass(cls, cc, uc, mode));
                out.addAll(MethodCheckPlus.compareMethodsInClass(cls, cc, uc, mode));
            } else {
                // legacy STRICT/RELAXED behavior (unchanged)
                out.addAll(AttributeCheck.compareAttributesInClass(cls, cc, uc, mode));
                out.addAll(MethodCheck.compareMethodsInClass(cls, cc, uc, mode));
            }
        }

        // ---- relationships: always use the existing RelationshipCheck (no PLUS variant)
        out.addAll(RelationshipCheck.compareRelationships(code, uml, mode));

        return out;
    }
}
