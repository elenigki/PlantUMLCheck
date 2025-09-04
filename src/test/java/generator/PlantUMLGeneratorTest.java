package generator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import model.Attribute;
import model.ClassDeclaration;
import model.ClassInfo;
import model.ClassType;
import model.IntermediateModel;
import model.Method;
import model.ModelSource;
import model.Relationship;
import model.RelationshipType;

public class PlantUMLGeneratorTest {

    private static String norm(String s) {
        // Trim lines and drop empties for resilient matching
        return s.replace("\r", "")
                .lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    @Test
    @DisplayName("Generates simple class with fields and methods")
    void simpleClassMembers() {
        // Class
        ClassInfo user = new ClassInfo("User", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        user.addAttribute(new Attribute("id", "Long", "-"));
        user.addAttribute(new Attribute("name", "String", "+"));

        Method fullName = new Method("fullName", "String", "+");
        Method touch = new Method("touch", "void", "-");
        user.addMethod(fullName);
        user.addMethod(touch);

        // Model
        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(user);

        // Generate
        String uml = new PlantUMLGenerator().generate(model);
        String n = norm(uml);

        assertTrue(n.contains("class User {"));
        assertTrue(n.contains("- id : Long"));
        assertTrue(n.contains("+ name : String"));
        assertTrue(n.contains("+ fullName() : String"));
        assertTrue(n.contains("- touch() : void"));
        assertTrue(n.endsWith("@enduml"));
    }

    @Test
    @DisplayName("Handles interface realization and class inheritance")
    void interfaceAndInheritance() {
        ClassInfo service = new ClassInfo("Service", ClassType.INTERFACE, ClassDeclaration.OFFICIAL);
        service.addMethod(new Method("execute", "void", "+"));

        ClassInfo base = new ClassInfo("BaseTask", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        base.setAbstract(true);

        ClassInfo task = new ClassInfo("ConcreteTask", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        task.addMethod(new Method("execute", "void", "+"));

        Relationship realizes = new Relationship(task, service, RelationshipType.REALIZATION);
        Relationship extendsRel = new Relationship(task, base, RelationshipType.GENERALIZATION);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(service);
        model.addClass(base);
        model.addClass(task);
        model.addRelationship(realizes);
        model.addRelationship(extendsRel);

        String uml = new PlantUMLGenerator().generate(model);
        String n = norm(uml);

        // Blocks
        assertTrue(n.contains("interface Service {"));
        assertTrue(n.contains("abstract class BaseTask {"));
        assertTrue(n.contains("class ConcreteTask {"));

        // Arrows
        assertTrue(n.contains("ConcreteTask ..|> Service"));
        assertTrue(n.contains("ConcreteTask --|> BaseTask"));
    }

    @Test
    @DisplayName("Renders association, aggregation, composition, and dependency arrows")
    void relationshipArrows() {
        ClassInfo a = new ClassInfo("A", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo b = new ClassInfo("B", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo c = new ClassInfo("C", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo d = new ClassInfo("D", ClassType.CLASS, ClassDeclaration.OFFICIAL);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(a); model.addClass(b); model.addClass(c); model.addClass(d);

        model.addRelationship(new Relationship(a, b, RelationshipType.ASSOCIATION));
        model.addRelationship(new Relationship(a, c, RelationshipType.AGGREGATION));
        model.addRelationship(new Relationship(a, d, RelationshipType.COMPOSITION));
        model.addRelationship(new Relationship(d, a, RelationshipType.DEPENDENCY));

        String uml = new PlantUMLGenerator().generate(model);
        String n = norm(uml);

        assertTrue(n.contains("A --> B"));
        assertTrue(n.contains("A o-- C"));
        assertTrue(n.contains("A *-- D"));
        assertTrue(n.contains("D ..> A"));
    }

    @Test
    @DisplayName("Quotes class names when needed and includes parameter types")
    void quotesAndParams() {
        ClassInfo withSpace = new ClassInfo("Order Item", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        withSpace.addAttribute(new Attribute("label", "String", "+"));
        Method rename = new Method("rename", "void", "+");
        rename.addParameter("java.lang.String");  // only types are kept in your Method model
        withSpace.addMethod(rename);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(withSpace);

        String uml = new PlantUMLGenerator().generate(model);
        String n = norm(uml);

        assertTrue(n.contains("class \"Order Item\" {"));
        assertTrue(n.contains("+ label : String"));
        assertTrue(n.contains("+ rename(java.lang.String) : void"));
    }

    @Test
    @DisplayName("Skips DUMMY classes and prunes dangling relationships by default")
    void prunesDummyAndDangling() {
        ClassInfo real = new ClassInfo("RealType", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo ghost = new ClassInfo("GhostType", ClassType.CLASS, ClassDeclaration.DUMMY);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(real);
        model.addClass(ghost);
        model.addRelationship(new Relationship(real, ghost, RelationshipType.ASSOCIATION));

        // Default generator options: only OFFICIAL classes, and prune dangling relationships.
        String uml = new PlantUMLGenerator().generate(model);
        String n = norm(uml);

        assertTrue(n.contains("class RealType {"));
        assertFalse(n.contains("class GhostType {"));
        assertFalse(n.contains("RealType --> GhostType"));
    }
}
