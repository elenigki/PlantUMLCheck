package parser;

import model.*;
import parser.uml.PlantUMLParser;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlatnUMLParserTest {

    // --- Declarations ---

    @Test
    public void parsesSimpleClassWithAttributeAndMethods() throws IOException {
        String uml = "@startuml\n" +
                "class Person {\n" +
                "  - String name;\n" +
                "  + getName() : String\n" +
                "  + print()\n" +
                "}\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ArrayList<ClassInfo> classes = model.getClasses();
        assertEquals(1, classes.size());

        ClassInfo person = classes.get(0);
        assertEquals("Person", person.getName());
        assertEquals(ClassType.CLASS, person.getClassType());

        ArrayList<Attribute> attrs = person.getAttributes();
        assertEquals(1, attrs.size());
        assertEquals("name", attrs.get(0).getName());
        assertEquals("String", attrs.get(0).getType());

        ArrayList<Method> methods = person.getMethods();
        assertEquals(2, methods.size());

        Method m1 = methods.get(0);
        assertEquals("getName", m1.getName());
        assertEquals("String", m1.getReturnType());

        Method m2 = methods.get(1);
        assertEquals("print", m2.getName());
        assertEquals("void", m2.getReturnType());
    }

    @Test
    public void parsesInterfacesAndInheritanceKeywords() throws IOException {
        String uml = "@startuml\n" +
                "class A\n" +
                "class B extends A\n" +
                "interface I\n" +
                "class C implements I\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        assertEquals(4, model.getClasses().size());

        ArrayList<Relationship> rels = model.getRelationships();
        assertEquals(2, rels.size());

        Relationship r1 = rels.get(0);
        assertEquals("B", r1.getSourceClass().getName());
        assertEquals("A", r1.getTargetClass().getName());
        assertEquals(RelationshipType.GENERALIZATION, r1.getType());

        Relationship r2 = rels.get(1);
        assertEquals("C", r2.getSourceClass().getName());
        assertEquals("I", r2.getTargetClass().getName());
        assertEquals(RelationshipType.REALIZATION, r2.getType());
    }

    @Test
    public void parsesEnumDeclaration() throws IOException {
        String uml = "@startuml\n" +
                "enum Direction {\n" +
                "}\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ArrayList<ClassInfo> classes = model.getClasses();
        assertEquals(1, classes.size());

        ClassInfo direction = classes.get(0);
        assertEquals("Direction", direction.getName());
        assertEquals(ClassType.ENUM, direction.getClassType());
        assertTrue(direction.getAttributes().isEmpty());
        assertTrue(direction.getMethods().isEmpty());
    }

    // --- Members (attributes/methods, visibility, params) ---

    @Test
    public void parsesAttributesAndMethodsWithoutVisibility() throws IOException {
        String uml = "@startuml\n" +
                "class Book {\n" +
                "  title : String\n" +
                "  String author\n" +
                "  + int pages\n" +
                "  getTitle() : String\n" +
                "  print()\n" +
                "}\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ClassInfo book = model.getClasses().get(0);

        ArrayList<Attribute> attrs = book.getAttributes();
        assertEquals(3, attrs.size());
        assertEquals("title", attrs.get(0).getName());
        assertEquals("String", attrs.get(0).getType());
        assertEquals("", attrs.get(0).getVisibility());

        assertEquals("author", attrs.get(1).getName());
        assertEquals("String", attrs.get(1).getType());
        assertEquals("", attrs.get(1).getVisibility());

        assertEquals("pages", attrs.get(2).getName());
        assertEquals("int", attrs.get(2).getType());
        assertEquals("+", attrs.get(2).getVisibility());

        ArrayList<Method> methods = book.getMethods();
        assertEquals(2, methods.size());

        Method m1 = methods.get(0);
        assertEquals("getTitle", m1.getName());
        assertEquals("String", m1.getReturnType());
        assertEquals("", m1.getVisibility());

        Method m2 = methods.get(1);
        assertEquals("print", m2.getName());
        assertEquals("void", m2.getReturnType());
        assertEquals("", m2.getVisibility());
    }

    @Test
    public void normalizesMethodParametersToTypesOnly() throws Exception {
        String uml = "@startuml\n" +
                "class Demo {\n" +
                "  + m1(num : int, text : String) : void\n" +
                "  + m2(int count, String label) : void\n" +
                "  + m3(int, List<String>) : void\n" +
                "}\n" +
                "@enduml\n";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ClassInfo demo = model.getClasses().stream().filter(c -> c.getName().equals("Demo")).findFirst().orElseThrow();

        Method m1 = demo.getMethods().stream().filter(m -> m.getName().equals("m1")).findFirst().orElseThrow();
        Method m2 = demo.getMethods().stream().filter(m -> m.getName().equals("m2")).findFirst().orElseThrow();
        Method m3 = demo.getMethods().stream().filter(m -> m.getName().equals("m3")).findFirst().orElseThrow();

        assertEquals(List.of("int", "String"), m1.getParameters());
        assertEquals(List.of("int", "String"), m2.getParameters());
        assertEquals(List.of("int", "List<String>"), m3.getParameters());
    }

    // --- Static and constructors ---

    @Test
    public void parsesStaticMembersWrappedWithUnderscores() throws IOException {
        String uml = "@startuml\n" +
                "class Logger {\n" +
                "  __counter : int__\n" +
                "  __getInstance() : Logger__\n" +
                "}\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ClassInfo logger = model.getClasses().stream().filter(c -> c.getName().equals("Logger")).findFirst().orElseThrow();

        Attribute counter = logger.getAttributes().stream().filter(a -> a.getName().equals("counter")).findFirst()
                .orElseThrow();
        assertEquals("int", counter.getType());
        assertTrue(counter.isStatic());

        Method getInstance = logger.getMethods().stream().filter(m -> m.getName().equals("getInstance"))
                .findFirst().orElseThrow();
        assertEquals("Logger", getInstance.getReturnType());
        assertTrue(getInstance.isStatic());
        assertTrue(getInstance.getParameters().isEmpty());
    }

    @Test
    public void keepsStaticNamesAndVisibilityPositions() throws IOException {
        String uml = "@startuml\n" +
                "class Config {\n" +
                "  __VERSION : String__\n" +
                "  path : String\n" +
                "  + __loadDefaults() : void__\n" +
                "  save(file : String) : void\n" +
                "}\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ClassInfo config = model.getClasses().stream().filter(c -> c.getName().equals("Config")).findFirst().orElseThrow();

        Attribute version = config.getAttributes().stream().filter(a -> a.getName().equals("VERSION"))
                .findFirst().orElseThrow();
        assertEquals("String", version.getType());
        assertTrue(version.isStatic());

        Attribute path = config.getAttributes().stream().filter(a -> a.getName().equals("path"))
                .findFirst().orElseThrow();
        assertEquals("String", path.getType());
        assertFalse(path.isStatic());

        Method loadDefaults = config.getMethods().stream().filter(m -> m.getName().equals("loadDefaults"))
                .findFirst().orElseThrow();
        assertEquals("void", loadDefaults.getReturnType());
        assertTrue(loadDefaults.isStatic());

        Method save = config.getMethods().stream().filter(m -> m.getName().equals("save"))
                .findFirst().orElseThrow();
        assertEquals("void", save.getReturnType());
        assertFalse(save.isStatic());
    }

    @Test
    public void ignoresConstructorsEvenWhenWrapped() throws IOException {
        String uml = "@startuml\n" +
                "class Person {\n" +
                "  __Person()__\n" +
                "  __Person(String name)__\n" +
                "  + __ID : int__\n" +
                "  - getName() : String\n" +
                "}\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ClassInfo person = model.getClasses().stream().filter(c -> c.getName().equals("Person")).findFirst().orElseThrow();

        assertFalse(person.getMethods().stream().anyMatch(m -> m.getName().equals("Person")));
        Method getName = person.getMethods().stream().filter(m -> m.getName().equals("getName")).findFirst().orElseThrow();
        assertEquals("String", getName.getReturnType());
        assertFalse(getName.isStatic());

        Attribute id = person.getAttributes().stream().filter(a -> a.getName().equals("ID")).findFirst().orElseThrow();
        assertEquals("int", id.getType());
        assertTrue(id.isStatic());
    }

    // --- Multiline and error handling ---

    @Test
    public void warnsForUnclosedMultilineBlock() throws IOException {
        String uml = "@startuml\n" +
                "class Demo {\n" +
                "  + doSomething(\n" +
                "    int a,\n" +
                "    String b\n" +
                "  : void\n" +
                "}\n" +
                "@enduml";

        File f = File.createTempFile("unclosedblock", ".puml");
        try (FileWriter writer = new FileWriter(f)) {
            writer.write(uml);
        }

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        try {
            new PlantUMLParser().parse(f);
            String output = errContent.toString();
            assertTrue(output.contains("Warning: Unclosed block in UML lines"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    public void parsesValidMultilineMethod() throws IOException {
        String uml = "@startuml\n" +
                "class Book {\n" +
                "  + getInfo(\n" +
                "      String author,\n" +
                "      int year\n" +
                "  ) : String\n" +
                "}\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ClassInfo book = model.getClasses().get(0);
        ArrayList<Method> methods = book.getMethods();
        assertEquals(1, methods.size());

        Method m = methods.get(0);
        assertEquals("getInfo", m.getName());
        assertEquals("String", m.getReturnType());
        assertEquals("+", m.getVisibility());
        assertEquals(List.of("String", "int"), m.getParameters());
    }

    @Test
    public void writesErrorForInvalidLine() throws IOException {
        String uml = "@startuml\n" +
                "class Person {\n" +
                "  + getName() : String\n" +
                "}\n" +
                "This is an invalid line!\n" +
                "@enduml";

        File f = tempPUML(uml);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        try {
            new PlantUMLParser().parse(f);
            String output = errContent.toString();
            assertTrue(output.contains("Error: This line can not be parsed ->"));
        } finally {
            System.setErr(originalErr);
        }
    }

    // --- Relationships ---

    @Test
    public void parsesOwnershipAndInheritanceRelationships() throws IOException {
        String uml = "@startuml\n" +
                "class A\n" +
                "class B\n" +
                "class C\n" +
                "class D\n" +
                "interface I\n" +
                "class E\n" +
                "class F\n" +
                "class G\n" +
                "class H\n" +
                "A -> B\n" +
                "C --> D\n" +
                "E --|> F\n" +
                "G ..|> I\n" +
                "H *-- A\n" +
                "B o-- C\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);
        List<Relationship> rels = model.getRelationships();

        assertEquals(6, rels.size());
        assertTrue(containsRelationship(rels, "A", "B", RelationshipType.ASSOCIATION));
        assertTrue(containsRelationship(rels, "C", "D", RelationshipType.ASSOCIATION));
        assertTrue(containsRelationship(rels, "E", "F", RelationshipType.GENERALIZATION));
        assertTrue(containsRelationship(rels, "G", "I", RelationshipType.REALIZATION));
        assertTrue(containsRelationship(rels, "H", "A", RelationshipType.COMPOSITION));
        assertTrue(containsRelationship(rels, "B", "C", RelationshipType.AGGREGATION));
    }

    @Test
    public void parsesReverseArrowVariants() throws IOException {
        String uml = "@startuml\n" +
                "class A\n" +
                "class B\n" +
                "class C\n" +
                "class D\n" +
                "interface I\n" +
                "class E\n" +
                "class F\n" +
                "class G\n" +
                "B <-- A\n" +
                "C <|-- D\n" +
                "I <|.. E\n" +
                "A --* B\n" +
                "F --o G\n" +
                "D <- E\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);
        List<Relationship> rels = model.getRelationships();

        assertEquals(6, rels.size());
        assertTrue(containsRelationship(rels, "A", "B", RelationshipType.ASSOCIATION));
        assertTrue(containsRelationship(rels, "D", "C", RelationshipType.GENERALIZATION));
        assertTrue(containsRelationship(rels, "E", "I", RelationshipType.REALIZATION));
        assertTrue(containsRelationship(rels, "B", "A", RelationshipType.COMPOSITION));
        assertTrue(containsRelationship(rels, "G", "F", RelationshipType.AGGREGATION));
        assertTrue(containsRelationship(rels, "E", "D", RelationshipType.ASSOCIATION));
    }

    @Test
    public void normalizesRepeatedDashesAndDotsInArrows() throws IOException {
        String uml = "@startuml\n" +
                "class A\n" +
                "class B\n" +
                "class C\n" +
                "interface I\n" +
                "class D\n" +
                "class E\n" +
                "A ---> B\n" +
                "C <... D\n" +
                "E ....|> I\n" +
                "A --* D\n" +
                "E ---o D\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);
        List<Relationship> rels = model.getRelationships();

        assertEquals(5, rels.size());
        assertTrue(containsRelationship(rels, "A", "B", RelationshipType.ASSOCIATION));
        assertTrue(containsRelationship(rels, "D", "C", RelationshipType.DEPENDENCY));
        assertTrue(containsRelationship(rels, "E", "I", RelationshipType.REALIZATION));
        assertTrue(containsRelationship(rels, "D", "A", RelationshipType.COMPOSITION));
        assertTrue(containsRelationship(rels, "D", "E", RelationshipType.AGGREGATION));
    }

    // --- Stereotypes and special cases ---

    @Test
    public void classStereotypeExternalCreatesDummy() throws Exception {
        String uml = "@startuml\n" +
                "class Customer <<external>>\n" +
                "class Order\n" +
                "@enduml";

        File f = tempPUML(uml);
        IntermediateModel model = new PlantUMLParser().parse(f);

        ClassInfo customer = model.getClasses().stream().filter(c -> c.getName().equals("Customer")).findFirst().orElseThrow();
        assertEquals(ClassType.CLASS, customer.getClassType());
        assertEquals(ClassDeclaration.DUMMY, customer.getDeclaration());

        ClassInfo order = model.getClasses().stream().filter(c -> c.getName().equals("Order")).findFirst().orElseThrow();
        assertEquals(ClassType.CLASS, order.getClassType());
        assertEquals(ClassDeclaration.OFFICIAL, order.getDeclaration());
    }

    // --- helpers ---

    private boolean containsRelationship(List<Relationship> relationships, String from, String to, RelationshipType type) {
        return relationships.stream().anyMatch(r ->
                r.getSourceClass().getName().equals(from) &&
                        r.getTargetClass().getName().equals(to) &&
                        r.getType() == type
        );
    }

    private File tempPUML(String uml) throws IOException {
        File tmp = File.createTempFile("test", ".puml");
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write(uml);
        }
        return tmp;
    }
}
