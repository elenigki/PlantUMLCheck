package comparison;

import comparison.checks.AttributeCheck;
import comparison.checks.AttributeCheckMinimal;
import comparison.checks.ClassCheck;
import comparison.checks.MethodCheck;
import comparison.checks.MethodCheckMinimal;
import comparison.checks.RelationshipCheck;
import comparison.issues.Difference;
import model.ClassInfo;
import model.IntermediateModel;
import model.Relationship;
import model.RelationshipType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Compares code vs UML and collects differences.
public class ModelComparator {

    private final CheckMode mode;  // STRICT, RELAXED, MINIMAL

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

            if (mode == CheckMode.MINIMAL) {
                // MINIMAL (formerly RELAXED_PLUS): omissions allowed; written mismatches are errors
                out.addAll(AttributeCheckMinimal.compareAttributesInClass(cls, cc, uc, mode));
                out.addAll(MethodCheckMinimal.compareMethodsInClass(cls, cc, uc, mode));
            } else {
                // STRICT and RELAXED use the legacy checkers with their own internal rules
                out.addAll(AttributeCheck.compareAttributesInClass(cls, cc, uc, mode));
                out.addAll(MethodCheck.compareMethodsInClass(cls, cc, uc, mode));
            }
        }

        // ---- relationships (shared behavior across modes)
        out.addAll(RelationshipCheck.compareRelationships(code, uml, mode));

        return out;
    }
}
