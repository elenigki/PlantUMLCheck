package comparison;

import comparison.checks.*;
import comparison.issues.*;
import model.*;
import java.util.*;

/** Compares code vs UML and collects differences. */

public class ModelComparator {
	
	private final CheckMode mode;  // STRICT or RELAXED

	public ModelComparator(CheckMode mode) {
		this.mode = mode;
	}
	
    /** Runs the whole comparison. */
    public List<Difference> compare(IntermediateModel code, IntermediateModel uml) {
        List<Difference> out = new ArrayList<>();
        if (code == null || uml == null) return out;

        // classes (presence + props)
        out.addAll(ClassCheck.compareClasses(code, uml, mode));

        // attributes / methods for classes present on both sides
        Set<String> common = ClassCheck.commonClassNames(code, uml);
        for (String cls : common) {
            ClassInfo cc = ClassCheck.classFrom(code, cls);
            ClassInfo uc = ClassCheck.classFrom(uml, cls);

            out.addAll(AttributeCheck.compareAttributesInClass(cls, cc, uc, mode));
            out.addAll(MethodCheck.compareMethodsInClass(cls, cc, uc, mode));
        }

        // relationships (later)
        out.addAll(RelationshipCheck.compareRelationships(code, uml, mode));

        return out;
    }
}
