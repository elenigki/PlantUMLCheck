package parser;

import model.*;
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
	public void testInferredDependenciesMultipleClasses() throws IOException {
	    String uml = "@startuml\n" +
	                 "class Author\n" +
	                 "class Book\n" +
	                 "class Library\n" +
	                 "class Publisher\n" +
	                 "class Reader\n" +
	                 "\n" +
	                 "class Catalog {\n" +
	                 "  - List<Book> books\n" +                        // Catalog → Book
	                 "  - Publisher publisher\n" +                     // Catalog → Publisher
	                 "  + findBook(String title) : Book\n" +           // Book as return type
	                 "}\n" +
	                 "\n" +
	                 "class Library {\n" +
	                 "  - Catalog catalog\n" +                          // Library → Catalog
	                 "  + getBooksBy(Author) : List<Book>\n" +         // Author + Book (nested)
	                 "}\n" +
	                 "\n" +
	                 "class Reader {\n" +
	                 "  - List<Book> borrowedBooks\n" +                // Reader → Book
	                 "  + borrow(Book book) : void\n" +                // Book as param
	                 "  + favoriteAuthor() : Author\n" +               // Author as return
	                 "}\n" +
	                 "@enduml";

	    File temp = File.createTempFile("multiDeps", ".puml");
	    try (FileWriter writer = new FileWriter(temp)) {
	        writer.write(uml);
	    }

	    PlantUMLParser parser = new PlantUMLParser();
	    IntermediateModel model = parser.parse(temp);
	    ArrayList<Relationship> relationships = model.getRelationships();

	    // Check various key dependencies
	    assertTrue(containsDependency(relationships, "Catalog", "Book"));
	    assertTrue(containsDependency(relationships, "Catalog", "Publisher"));
	    assertTrue(containsDependency(relationships, "Catalog", "Book")); // via return
	    assertTrue(containsDependency(relationships, "Library", "Catalog"));
	    assertTrue(containsDependency(relationships, "Library", "Author"));
	    assertTrue(containsDependency(relationships, "Library", "Book"));
	    assertTrue(containsDependency(relationships, "Reader", "Book"));
	    assertTrue(containsDependency(relationships, "Reader", "Author"));
	}

	/**
	 * Utility to check if a dependency exists between two class names.
	 */
	private boolean containsDependency(List<Relationship> relationships, String from, String to) {
	    return relationships.stream().anyMatch(r ->
	        r.getType() == RelationshipType.DEPENDENCY &&
	        r.getSourceClass().getName().equals(from) &&
	        r.getTargetClass().getName().equals(to)
	    );
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

	    List<String> expectedParams = List.of("String author", "int year");
	    assertEquals(expectedParams.size(), m.getParameters().size());
	    assertEquals("String author", m.getParameters().get(0));
	    assertEquals("int year", m.getParameters().get(1));
	}


	private boolean containsRelationship(List<Relationship> relationships, String from, String to, RelationshipType type) {
	    return relationships.stream().anyMatch(r ->
	        r.getSourceClass().getName().equals(from) &&
	        r.getTargetClass().getName().equals(to) &&
	        r.getType() == type
	    );
	}


}
