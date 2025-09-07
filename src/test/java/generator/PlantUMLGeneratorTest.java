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

    /** Normalizes the output for robust matching: trims whitespace-only lines and normalizes CR/LF. */
    private static String norm(String s) {
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
        user.addAttribute(new Attribute("name", "String", "private"));
        user.addMethod(new Method("getName", "String", "public"));

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(user);

        String uml = new PlantUMLGenerator().generate(model);
        String n = norm(uml);

        assertTrue(n.contains("@startuml"));
        assertTrue(n.contains("class User {"));
        assertTrue(n.contains("- name : String"));
        assertTrue(n.contains("+ getName() : String"));
        assertTrue(n.contains("@enduml"));
    }

    @Test
    @DisplayName("No boilerplate headers or skinparams are emitted")
    void noBoilerplate() {
        ClassInfo c = new ClassInfo("C", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(c);

        String n = norm(new PlantUMLGenerator().generate(model));
        assertFalse(n.contains("' Auto-generated from source code IntermediateModel"));
        assertFalse(n.contains("' Paste this into https://www.plantuml.com/plantuml to render"));
        assertFalse(n.contains("skinparam classAttributeIconSize 0"));
        assertFalse(n.contains("skinparam shadowing false"));
        assertFalse(n.contains("hide empty members"));
    }

    @Test
    @DisplayName("Ownership dominance: composition > aggregation > association (keep only strongest)")
    void ownershipDominance() {
        ClassInfo a = new ClassInfo("A", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo b = new ClassInfo("B", ClassType.CLASS, ClassDeclaration.OFFICIAL);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(a);
        model.addClass(b);

        // Add weaker and stronger ownership edges for the same pair
        model.addRelationship(new Relationship(a, b, RelationshipType.ASSOCIATION));
        model.addRelationship(new Relationship(a, b, RelationshipType.AGGREGATION));
        model.addRelationship(new Relationship(a, b, RelationshipType.COMPOSITION));

        String n = norm(new PlantUMLGenerator().generate(model));

        // Only composition remains
        assertTrue(n.contains("A *-- B"), "composition should remain");
        assertFalse(n.contains("A o-- B"), "aggregation should be removed");
        assertFalse(n.contains("A --> B"), "association should be removed");
    }

    @Test
    @DisplayName("Inheritance normalization: class-class extends, class-interface realizes, interface-interface extends")
    void inheritanceNormalization() {
        ClassInfo cc1 = new ClassInfo("Base", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo cc2 = new ClassInfo("Child", ClassType.CLASS, ClassDeclaration.OFFICIAL);

        ClassInfo ci1 = new ClassInfo("IShape", ClassType.INTERFACE, ClassDeclaration.OFFICIAL);
        ClassInfo ci2 = new ClassInfo("Drawable", ClassType.INTERFACE, ClassDeclaration.OFFICIAL);

        ClassInfo cls = new ClassInfo("Painter", ClassType.CLASS, ClassDeclaration.OFFICIAL);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(cc1);
        model.addClass(cc2);
        model.addClass(ci1);
        model.addClass(ci2);
        model.addClass(cls);

        // Even if mis-tagged upstream, normalization should coerce correctly
        model.addRelationship(new Relationship(cc2, cc1, RelationshipType.REALIZATION));   // should become GENERALIZATION
        model.addRelationship(new Relationship(cls, ci1, RelationshipType.GENERALIZATION)); // should become REALIZATION
        model.addRelationship(new Relationship(ci2, ci1, RelationshipType.GENERALIZATION)); // stays GENERALIZATION

        String n = norm(new PlantUMLGenerator().generate(model));

        // Class -> Class : --|>
        assertTrue(n.contains("Child --|> Base"));
        // Class -> Interface : ..|>
        assertTrue(n.contains("Painter ..|> IShape"));
        // Interface -> Interface : --|>
        assertTrue(n.contains("Drawable --|> IShape"));
    }

    @Test
    @DisplayName("Invalid inheritance combos are dropped")
    void invalidInheritanceDropped() {
        ClassInfo en = new ClassInfo("E", ClassType.ENUM, ClassDeclaration.OFFICIAL);
        ClassInfo cl = new ClassInfo("C", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo in = new ClassInfo("I", ClassType.INTERFACE, ClassDeclaration.OFFICIAL);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(en);
        model.addClass(cl);
        model.addClass(in);

        // Enum as source shouldn't create inheritance edges
        model.addRelationship(new Relationship(en, cl, RelationshipType.GENERALIZATION));
        model.addRelationship(new Relationship(en, in, RelationshipType.REALIZATION));

        String n = norm(new PlantUMLGenerator().generate(model));
        assertFalse(n.contains("E --|> C"));
        assertFalse(n.contains("E ..|> I"));
    }

    @Test
    @DisplayName("Dependencies are fully dropped from generation")
    void dependenciesDropped() {
        ClassInfo a = new ClassInfo("A", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo b = new ClassInfo("B", ClassType.CLASS, ClassDeclaration.OFFICIAL);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(a);
        model.addClass(b);
        model.addRelationship(new Relationship(a, b, RelationshipType.DEPENDENCY));

        String n = norm(new PlantUMLGenerator().generate(model));

        // No dependency arrow should exist; also no fallback arrow for this pair
        assertFalse(n.contains("A ..> B"));
        assertFalse(n.contains("A --> B"));
        assertFalse(n.contains("A o-- B"));
        assertFalse(n.contains("A *-- B"));
        assertFalse(n.contains("A --|> B"));
        assertFalse(n.contains("A ..|> B"));
    }

    @Test
    @DisplayName("Self-loop relationships are ignored")
    void selfLoopIgnored() {
        ClassInfo a = new ClassInfo("A", ClassType.CLASS, ClassDeclaration.OFFICIAL);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(a);
        model.addRelationship(new Relationship(a, a, RelationshipType.ASSOCIATION));
        model.addRelationship(new Relationship(a, a, RelationshipType.DEPENDENCY));
        model.addRelationship(new Relationship(a, a, RelationshipType.GENERALIZATION));

        String n = norm(new PlantUMLGenerator().generate(model));

        assertFalse(n.contains("A --> A"));
        assertFalse(n.contains("A ..> A"));
        assertFalse(n.contains("A --|> A"));
        assertFalse(n.contains("A ..|> A"));
        assertTrue(n.contains("class A {"));
    }

    @Test
    @DisplayName("Prunes dangling relationships with non-official classes (keeps official only)")
    void pruneDanglingToGhost() {
        ClassInfo real = new ClassInfo("RealType", ClassType.CLASS, ClassDeclaration.OFFICIAL);
        ClassInfo ghost = new ClassInfo("GhostType", ClassType.CLASS, ClassDeclaration.DUMMY);

        IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
        model.addClass(real);
        model.addClass(ghost);
        model.addRelationship(new Relationship(real, ghost, RelationshipType.ASSOCIATION));

        String n = norm(new PlantUMLGenerator().generate(model));

        assertTrue(n.contains("class RealType {"));
        assertFalse(n.contains("class GhostType {"));
        assertFalse(n.contains("RealType --> GhostType"));
    }
}
