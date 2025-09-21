package parser;

import model.*;
import parser.uml.PlantUMLParser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PlatnUMLParserTest {

	@Test
	public void testSimpleClassParsing() throws IOException{
		// UML script sample
		String uml = "@startuml\n" +
                "class Person {\n" +
                "  - String name;\n" +
                "  + getName() : String\n" +
                "  + print()\n" + // no return type -> should be void
                "}\n" +
                "@enduml";
		
		// Create temporary file for parsing
		File temp = File.createTempFile("test",".puml");
        try (FileWriter writer = new FileWriter(temp)) {
            writer.write(uml);
        }
        
        // Execute parser
        PlantUMLParser parser = new PlantUMLParser();
        IntermediateModel model = parser.parse(temp);
        
        // Check results
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
        assertEquals("void", m2.getReturnType()); // default to void
	}
	
	@Test
	public void testMethodsWithVariousParameterForms() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Tester {\n" +
	                 "  + noParams()\n" +
	                 "  + singleParam(int) : int\n" +
	                 "  + multiParam(String, double, int) : void\n" +
	                 "  + withGeneric(List<String>) : List<String>\n" +
	                 "}\n" +
	                 "@enduml";

	    File temp = File.createTempFile("mixedmethods", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);

	    ArrayList<ClassInfo> classes = model.getClasses();
	    assertEquals(1, classes.size());

	    ClassInfo tester = classes.get(0);
	    ArrayList<Method> methods = tester.getMethods();
	    assertEquals(4, methods.size());

	    // Method 1: noParams
	    Method m1 = methods.get(0);
	    assertEquals("noParams", m1.getName());
	    assertEquals("void", m1.getReturnType());
	    assertTrue(m1.getParameters().isEmpty());

	    // Method 2: singleParam
	    Method m2 = methods.get(1);
	    assertEquals("singleParam", m2.getName());
	    assertEquals("int", m2.getReturnType());
	    assertEquals(1, m2.getParameters().size());
	    assertEquals("int", m2.getParameters().get(0));

	    // Method 3: multiParam
	    Method m3 = methods.get(2);
	    assertEquals("multiParam", m3.getName());
	    assertEquals("void", m3.getReturnType());
	    assertEquals(List.of("String", "double", "int"), m3.getParameters());

	    // Method 4: withGeneric
	    Method m4 = methods.get(3);
	    assertEquals("withGeneric", m4.getName());
	    assertEquals("List<String>", m4.getReturnType());
	    assertEquals(1, m4.getParameters().size());
	    assertEquals("List<String>", m4.getParameters().get(0));
	}

	@Test
	public void testInvalidLineTriggersWarning() throws IOException {
	    String uml = "@startuml\n" +
                "class Person {\n" +
                "  + getName() : String\n" +
                "}\n" +
                "This is an invalid line!\n" + // intentionally invalid
                "@enduml";

	    File temp = File.createTempFile("invalid", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    // Redirect System.err to a ByteArrayOutputStream
	    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
	    PrintStream originalErr = System.err;
	    System.setErr(new PrintStream(errContent));

	    try {
	    	// Parse the file using the parser
	        PlantUMLParser parser = new PlantUMLParser();
	        IntermediateModel model = parser.parse(temp);
	        
	     // Read captured stderr output and check if the expected error message is present
	        String output = errContent.toString();
	        System.out.println(output);
	        assertTrue(output.contains("Error: This line can not be parsed ->"));
	    } finally {
	    	// Restore the original System.err
	        System.setErr(originalErr);
	    } 
	}

	@Test
	public void testInheritanceAndRealization() throws IOException {
	    String uml = "@startuml\n" +
	                 "class A\n" +
	                 "class B extends A\n" +
	                 "interface I\n" +
	                 "class C implements I\n" +
	                 "@enduml";

	    File temp = File.createTempFile("inheritance", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);

	    assertEquals(4, model.getClasses().size()); // A, B, C and I (interface I also added as ClassInfo)

	    ArrayList<Relationship> relationships = model.getRelationships();
	    assertEquals(2, relationships.size());

	    // Check inheritance
	    Relationship r1 = relationships.get(0);
	    assertEquals("B", r1.getSourceClass().getName());
	    assertEquals("A", r1.getTargetClass().getName());
	    assertEquals(RelationshipType.GENERALIZATION, r1.getType());

	    // Check realization
	    Relationship r2 = relationships.get(1);
	    assertEquals("C", r2.getSourceClass().getName());
	    assertEquals("I", r2.getTargetClass().getName());
	    assertEquals(RelationshipType.REALIZATION, r2.getType());
	}

	@Test
	public void testEnumParsing() throws IOException {
	    String uml = "@startuml\n" +
	                 "enum Direction {\n" +
	                 "}\n" +
	                 "@enduml";

	    File temp = File.createTempFile("enum", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);

	    ArrayList<ClassInfo> classes = model.getClasses();
	    assertEquals(1, classes.size());

	    ClassInfo direction = classes.get(0);
	    assertEquals("Direction", direction.getName());
	    assertEquals(ClassType.ENUM, direction.getClassType());
	    assertTrue(direction.getAttributes().isEmpty());
	    assertTrue(direction.getMethods().isEmpty());
	}
	
	@Test
	public void testAggregationAndComposition() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Car\n" +
	                 "class Engine\n" +
	                 "Car *-- Engine\n" +  // Composition
	                 "class Library\n" +
	                 "class Book\n" +
	                 "Library o-- Book\n" + // Aggregation
	                 "@enduml";

	    File temp = File.createTempFile("aggcomp", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);
	    ArrayList<Relationship> relationships = model.getRelationships();

	    assertTrue(containsRelationship(relationships, "Car", "Engine", RelationshipType.COMPOSITION));
	    assertTrue(containsRelationship(relationships, "Library", "Book", RelationshipType.AGGREGATION));
	}
	
	@Test
	public void testAttributesAndMethodsWithoutVisibility() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Book {\n" +
	                 "  title : String\n" +            // Format: name : Type
	                 "  String author\n" +             // Format: Type name
	                 "  + int pages\n" +               // Format: + Type name
	                 "  getTitle() : String\n" +       // Method without visibility
	                 "  print()\n" +                   // Method without visibility & returnType
	                 "}\n" +
	                 "@enduml";

	    File temp = File.createTempFile("novisibility", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);

	    ArrayList<ClassInfo> classes = model.getClasses();
	    assertEquals(1, classes.size());

	    ClassInfo book = classes.get(0);
	    ArrayList<Attribute> attrs = book.getAttributes();
	    assertEquals(3, attrs.size());

	    Attribute attr1 = attrs.get(0); // title : String
	    assertEquals("title", attr1.getName());
	    assertEquals("String", attr1.getType());
	    assertEquals("", attr1.getVisibility());

	    Attribute attr2 = attrs.get(1); // String author
	    assertEquals("author", attr2.getName());
	    assertEquals("String", attr2.getType());
	    assertEquals("", attr2.getVisibility());

	    Attribute attr3 = attrs.get(2); // + int pages
	    assertEquals("pages", attr3.getName());
	    assertEquals("int", attr3.getType());
	    assertEquals("+", attr3.getVisibility());

	    ArrayList<Method> methods = book.getMethods();
	    assertEquals(2, methods.size());

	    Method m1 = methods.get(0); // getTitle() : String
	    assertEquals("getTitle", m1.getName());
	    assertEquals("String", m1.getReturnType());
	    assertEquals("", m1.getVisibility());

	    Method m2 = methods.get(1); // print()
	    assertEquals("print", m2.getName());
	    assertEquals("void", m2.getReturnType());
	    assertEquals("", m2.getVisibility());
	}

	@Test
	public void testUnclosedMultiLineBlockWarning() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Demo {\n" +
	                 "  + doSomething(\n" +
	                 "    int a,\n" +
	                 "    String b\n" +  // <-- Δεν υπάρχει κλείσιμο ')'
	                 "  : void\n" +     // <-- Εσκεμμένα λάθος continuation
	                 "}\n" +
	                 "@enduml";

	    File temp = File.createTempFile("unclosedblock", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    // Capture stderr output
	    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
	    PrintStream originalErr = System.err;
	    System.setErr(new PrintStream(errContent));

	    try {
	        PlantUMLParser parser = new PlantUMLParser();
	        parser.parse(temp);

	        String output = errContent.toString();
	        assertTrue(output.contains("Warning: Unclosed block in UML lines"));
	    } finally {
	        System.setErr(originalErr);
	    }
	}

	@Test
	public void testValidMultiLineMethodParsing() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Book {\n" +
	                 "  + getInfo(\n" +
	                 "      String author,\n" +
	                 "      int year\n" +
	                 "  ) : String\n" +
	                 "}\n" +
	                 "@enduml";

	    File temp = File.createTempFile("multilinemethod", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);

	    ArrayList<ClassInfo> classes = model.getClasses();
	    assertEquals(1, classes.size());

	    ClassInfo book = classes.get(0);
	    assertEquals("Book", book.getName());

	    ArrayList<Method> methods = book.getMethods();
	    assertEquals(1, methods.size());

	    Method m = methods.get(0);
	    assertEquals("getInfo", m.getName());
	    assertEquals("String", m.getReturnType());
	    assertEquals("+", m.getVisibility());

	    List<String> expectedParams = List.of("String", "int");
	    assertEquals(expectedParams.size(), m.getParameters().size());
	    assertEquals("String", m.getParameters().get(0));
	    assertEquals("int", m.getParameters().get(1));
	}
	
	@Test
	public void testBasicRelationshipParsing() throws IOException {
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

	            "A -> B\n" +             // ASSOCIATION
	            "C --> D\n" +            // ASSOCIATION
	            "E --|> F\n" +           // GENERALIZATION
	            "G ..|> I\n" +           // REALIZATION
	            "H *-- A\n" +            // COMPOSITION
	            "B o-- C\n" +            // AGGREGATION
	            "@enduml";

	    File temp = File.createTempFile("uml-basic", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);
	    List<Relationship> relationships = model.getRelationships();

	    assertEquals(6, relationships.size());

	    assertTrue(containsRelationship(relationships, "A", "B", RelationshipType.ASSOCIATION));
	    assertTrue(containsRelationship(relationships, "C", "D", RelationshipType.ASSOCIATION));
	    assertTrue(containsRelationship(relationships, "E", "F", RelationshipType.GENERALIZATION));
	    assertTrue(containsRelationship(relationships, "G", "I", RelationshipType.REALIZATION));
	    assertTrue(containsRelationship(relationships, "H", "A", RelationshipType.COMPOSITION));
	    assertTrue(containsRelationship(relationships, "B", "C", RelationshipType.AGGREGATION));
	}
	
	@Test
	public void testReverseArrowVariants() throws IOException {
	    String uml = "@startuml\n" +
	            "class A\n" +
	            "class B\n" +
	            "class C\n" +
	            "class D\n" +
	            "interface I\n" +
	            "class E\n" +
	            "class F\n" +
	            "class G\n" +

	            "B <-- A\n" +          // Association
	            "C <|-- D\n" +         // Generalization
	            "I <|.. E\n" +         // Realization
	            "A --* B\n" +          // Composition (reverse form)
	            "F --o G\n" +          // Aggregation (reverse form)
	            "D <- E\n" +           // Association (reverse form)
	            "@enduml";

	    File temp = File.createTempFile("uml-reverse", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);
	    List<Relationship> relationships = model.getRelationships();

	    assertEquals(6, relationships.size());

	    assertTrue(containsRelationship(relationships, "A", "B", RelationshipType.ASSOCIATION));
	    assertTrue(containsRelationship(relationships, "D", "C", RelationshipType.GENERALIZATION));
	    assertTrue(containsRelationship(relationships, "E", "I", RelationshipType.REALIZATION));
	    assertTrue(containsRelationship(relationships, "B", "A", RelationshipType.COMPOSITION));
	    assertTrue(containsRelationship(relationships, "G", "F", RelationshipType.AGGREGATION));
	    assertTrue(containsRelationship(relationships, "E", "D", RelationshipType.ASSOCIATION));

	}


	@Test
	public void testDashedAndDottedArrowNormalization() throws IOException {
	    String uml = "@startuml\n" +
	            "class A\n" +
	            "class B\n" +
	            "class C\n" +
	            "interface I\n" +
	            "class D\n" +
	            "class E\n" +

	            "A ---> B\n" +           // A → B (association)
	            "C <... D\n" +           // D → C (association, reverse)
	            "E ....|> I\n" +         // E → I (realization)
	            "A --* D\n" +            // D → A (composition, reverse)
	            "E ---o D\n" +           // D ← E (aggregation, reverse)
	            "@enduml";

	    File temp = File.createTempFile("uml-norm-dir", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);
	    List<Relationship> relationships = model.getRelationships();

	    assertEquals(5, relationships.size());

	    assertTrue(containsRelationship(relationships, "A", "B", RelationshipType.ASSOCIATION));
	    assertTrue(containsRelationship(relationships, "D", "C", RelationshipType.DEPENDENCY));
	    assertTrue(containsRelationship(relationships, "E", "I", RelationshipType.REALIZATION));
	    assertTrue(containsRelationship(relationships, "D", "A", RelationshipType.COMPOSITION));
	    assertTrue(containsRelationship(relationships, "D", "E", RelationshipType.AGGREGATION));
	}
	
	@Test
	public void testAttributesWithVisibilityNameColonType() throws IOException {
	    String uml =
	        "@startuml\n" +
	        "class Person {\n" +
	        "  - name : String\n" +   // visibility + name : Type
	        "  + age  : int\n" +      // visibility + name : Type
	        "}\n" +
	        "@enduml\n";

	    // Write UML to a temp file because PlantUMLParser.parse(File) is the entry point
	    File tmp = File.createTempFile("person", ".puml");
	    try (FileWriter fw = new FileWriter(tmp)) {
	        fw.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(tmp);

	    // Find the parsed class
	    ClassInfo person = model.getClasses().stream()
	        .filter(c -> "Person".equals(c.getName()))
	        .findFirst()
	        .orElseThrow(() -> new AssertionError("Person class not parsed"));

	    // Grab attributes by name
	    Attribute nameAttr = person.getAttributes().stream()
	        .filter(a -> "name".equals(a.getName()))
	        .findFirst()
	        .orElseThrow(() -> new AssertionError("Attribute 'name' not found"));

	    Attribute ageAttr = person.getAttributes().stream()
	        .filter(a -> "age".equals(a.getName()))
	        .findFirst()
	        .orElseThrow(() -> new AssertionError("Attribute 'age' not found"));

	    // Assert parsing of type + visibility for "name : String" and "age : int"
	    assertEquals("String", nameAttr.getType(), "Type of 'name' should be String");
	    assertEquals("-", nameAttr.getVisibility(), "Visibility of 'name' should be '-'");

	    assertEquals("int", ageAttr.getType(), "Type of 'age' should be int");
	    assertEquals("+", ageAttr.getVisibility(), "Visibility of 'age' should be '+'");
	}

	@Test
	void constructorsAreIgnored() throws Exception {
	    String uml =
	        "@startuml\n" +
	        "class Person {\n" +
	        "  + Person(String name)\n" + // constructor → must be ignored
	        "  + getName() : String\n" +
	        "}\n" +
	        "@enduml\n";
	    File f = tempPUML(uml);

	    IntermediateModel model = new PlantUMLParser().parse(f);
	    ClassInfo person = model.getClasses().stream()
	        .filter(c -> c.getName().equals("Person"))
	        .findFirst().orElseThrow();

	    assertTrue(person.getMethods().stream().anyMatch(m -> m.getName().equals("getName")),
	        "Normal method should be parsed");
	    assertFalse(person.getMethods().stream().anyMatch(m -> m.getName().equals("Person")),
	        "Constructor should be ignored");
	}
	
	@Test
	public void testStaticAttributeAndMethodWrappedWithUnderscores() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Logger {\n" +
	                 "  __counter : int__\n" +            // static attribute
	                 "  __getInstance() : Logger__\n" +   // static method
	                 "}\n" +
	                 "@enduml";

	    File f = tempPUML(uml);
	    IntermediateModel model = new PlantUMLParser().parse(f);

	    ClassInfo logger = model.getClasses().stream()
	        .filter(c -> c.getName().equals("Logger"))
	        .findFirst().orElseThrow();

	    // Attribute assertions
	    Attribute counter = logger.getAttributes().stream()
	        .filter(a -> a.getName().equals("counter"))
	        .findFirst().orElseThrow(() -> new AssertionError("Static attribute 'counter' not parsed"));
	    assertEquals("int", counter.getType());
	    assertTrue(counter.isStatic(), "Attribute 'counter' should be static");

	    // Method assertions
	    Method getInstance = logger.getMethods().stream()
	        .filter(m -> m.getName().equals("getInstance"))
	        .findFirst().orElseThrow(() -> new AssertionError("Static method 'getInstance' not parsed"));
	    assertEquals("Logger", getInstance.getReturnType());
	    assertTrue(getInstance.isStatic(), "Method 'getInstance' should be static");
	    assertTrue(getInstance.getParameters().isEmpty(), "Expected no parameters for getInstance()");
	}

	@Test
	public void testStaticAttributesAndMethodsKeepNamesWithoutUnderscores() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Config {\n" +
	                 "  __VERSION : String__\n" +            // static attribute
	                 "  path : String\n" +                   // normal attribute
	                 "  + __loadDefaults() : void__\n" +       // static method
	                 "  save(file : String) : void\n" +      // normal method
	                 "}\n" +
	                 "@enduml";

	    File f = tempPUML(uml);
	    IntermediateModel model = new PlantUMLParser().parse(f);

	    ClassInfo config = model.getClasses().stream()
	        .filter(c -> c.getName().equals("Config"))
	        .findFirst().orElseThrow();

	    Attribute version = config.getAttributes().stream()
	        .filter(a -> a.getName().equals("VERSION"))
	        .findFirst().orElseThrow(() -> new AssertionError("Static attribute 'VERSION' not found"));
	    assertEquals("String", version.getType());
	    assertTrue(version.isStatic(), "VERSION should be marked static");

	    Attribute path = config.getAttributes().stream()
	        .filter(a -> a.getName().equals("path"))
	        .findFirst().orElseThrow(() -> new AssertionError("Attribute 'path' not found"));
	    assertEquals("String", path.getType());
	    assertFalse(path.isStatic(), "path should not be static");

	    Method loadDefaults = config.getMethods().stream()
	        .filter(m -> m.getName().equals("loadDefaults"))
	        .findFirst().orElseThrow(() -> new AssertionError("Static method 'loadDefaults' not found"));
	    assertEquals("void", loadDefaults.getReturnType());
	    assertTrue(loadDefaults.isStatic(), "loadDefaults should be marked static");

	    Method save = config.getMethods().stream()
	        .filter(m -> m.getName().equals("save"))
	        .findFirst().orElseThrow(() -> new AssertionError("Method 'save' not found"));
	    System.out.println("Parameter: " + save.getParameters().get(0));
	    assertEquals("void", save.getReturnType());
	    assertFalse(save.isStatic(), "save should not be static");
	}

	@Test
	public void testConstructorsAreIgnoredEvenWhenWrappedStatic() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Person {\n" +
	                 "  __Person()__\n" +                    // constructor, wrapped → must be ignored
	                 "  __Person(String name)__\n" +          // constructor with param, wrapped → ignored
	                 "  + __ID : int__\n" +                     // static attribute
	                 "  - getName() : String\n" +               // normal method
	                 "}\n" +
	                 "@enduml";

	    File f = tempPUML(uml);
	    IntermediateModel model = new PlantUMLParser().parse(f);

	    ClassInfo person = model.getClasses().stream()
	        .filter(c -> c.getName().equals("Person"))
	        .findFirst().orElseThrow();

	    // Constructors must not appear as methods
	    assertFalse(person.getMethods().stream().anyMatch(m -> m.getName().equals("Person")),
	        "Constructors should be ignored even when wrapped with __...__");

	    // Normal method present
	    Method getName = person.getMethods().stream()
	        .filter(m -> m.getName().equals("getName"))
	        .findFirst().orElseThrow(() -> new AssertionError("Method 'getName' not parsed"));
	    assertEquals("String", getName.getReturnType());
	    assertFalse(getName.isStatic(), "getName should not be static");

	    // Static attribute present and marked
	    Attribute id = person.getAttributes().stream()
	        .filter(a -> a.getName().equals("ID"))
	        .findFirst().orElseThrow(() -> new AssertionError("Static attribute 'ID' not parsed"));
	    assertEquals("int", id.getType());
	    assertTrue(id.isStatic(), "ID should be static");
	}




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
