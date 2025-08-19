package comparison.export;

import comparison.CheckMode;
import comparison.ModelComparator;
import comparison.issues.Difference;
import model.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static testutil.TestModelBuilder.*;

/** Smoke tests for PlantUmlWriter output. */
public class PlantUmlWriterTest {

    @Test
    void strict_generatesClassesMembersAndStrongestOwnership() {
        // --- code model (source of truth) ---
        IntermediateModel code = codeModel();

        ClassInfo order = addClass(code, "Order", ClassType.CLASS);
        addAttr(order, "id", "int", "+");
        addMethod(order, "total", "double", "+");

        ClassInfo line = addClass(code, "OrderLine", ClassType.CLASS);
        addAttr(line, "qty", "int", "+");

        // relationships: association + composition → strongest kept = composition
        addRel(code, order, line, RelationshipType.ASSOCIATION);
        addRel(code, order, line, RelationshipType.COMPOSITION);

        // inheritance: Realization and Generalization examples
        ClassInfo iface = addClass(code, "Repo", ClassType.INTERFACE);
        ClassInfo impl  = addClass(code, "RepoImpl", ClassType.CLASS);
        addRel(code, impl, iface, RelationshipType.REALIZATION);

        ClassInfo base = addClass(code, "Base", ClassType.CLASS);
        ClassInfo sub  = addClass(code, "Sub",  ClassType.CLASS);
        addRel(code, sub, base, RelationshipType.GENERALIZATION);

        // no UML/diffs needed for strict
        String puml = PlantUmlWriter.generate(CheckMode.STRICT, code, null, List.of());

        // class headers and members
        assertTrue(puml.contains("class Order {"));
        assertTrue(puml.contains("+id : int"));
        assertTrue(puml.contains("+total() : double"));

        assertTrue(puml.contains("class OrderLine {"));
        assertTrue(puml.contains("+qty : int"));

        // strongest ownership: composition arrow
        assertTrue(puml.contains("Order *-- OrderLine"), "should render composition as strongest ownership");

        // inheritance & realization arrows
        assertTrue(puml.contains("RepoImpl ..|> Repo"), "should render realization");
        assertTrue(puml.contains("Sub --|> Base"), "should render generalization");

        // should not include dependency arrows in strict
        assertFalse(puml.contains("..>"), "strict should not include dependency arrows");
    }

    @Test
    void relaxed_includesWarningComments_andDependencyNotes() {
        // --- code model (source of truth) ---
        IntermediateModel code = codeModel();
        ClassInfo a = addClass(code, "A", ClassType.CLASS);
        ClassInfo b = addClass(code, "B", ClassType.CLASS);
        // no relationships in code

        // --- uml model with a dependency only (author intent) ---
        IntermediateModel uml = umlModel();
        ClassInfo ua = addClass(uml, "A", ClassType.CLASS);
        ClassInfo ub = addClass(uml, "B", ClassType.CLASS);
        addRel(uml, ua, ub, RelationshipType.DEPENDENCY);

        // compare to get diffs (RELAXED turns this into a SUGGESTION)
        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        // generate relaxed PlantUML with comments
        String puml = PlantUmlWriter.generate(CheckMode.RELAXED, code, uml, diffs);

        // has the relaxed header comment block
        assertTrue(puml.contains("Notes (RELAXED)"), "should include relaxed notes header");

        // includes our suggestion/warning comments (summary text may vary slightly if you edited it)
        assertTrue(puml.contains("SUGGESTION — A -> B"), "should include suggestion line for dependency");
        assertTrue(puml.contains("Dependency may be noise") || puml.contains("Dependency"), "should mention dependency");

        // includes UML dependency as a comment (not an actual edge)
        assertTrue(puml.contains("' UML had DEPENDENCY: A ..> B"), "should keep UML dependency as comment");

        // still renders classes from code
        assertTrue(puml.contains("class A {"));
        assertTrue(puml.contains("class B {"));

        // still no live dependency edge in diagram body
        assertFalse(puml.lines().anyMatch(l -> l.strip().equals("A ..> B")), "dependency should remain commented out");
    }
}
