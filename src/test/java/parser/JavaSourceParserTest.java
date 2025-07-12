package parser;

import model.*;
import java.io.*;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JavaSourceParserTest {
	@Test
	public void testSimpleClassWithFieldAndMethod() throws IOException {
	    String code = """
	        public class Book {
	            private String title;
	            public String getTitle() {
	                return title;
	            }
	        }
	    """;

	    File tempFile = createTempJavaFile("Book", code);
	    JavaSourceParser parser = new JavaSourceParser();
	    IntermediateModel model = parser.parse(tempFile);

	    assertEquals(1, model.getClasses().size());

	    ClassInfo book = model.findClassByName("Book");
	    assertNotNull(book);
	    assertEquals(ClassType.CLASS, book.getClassType());

	    assertEquals(1, book.getAttributes().size());
	    assertEquals("title", book.getAttributes().get(0).getName());
	    assertEquals("String", book.getAttributes().get(0).getType());
	    assertEquals("-", book.getAttributes().get(0).getVisibility());

	    assertEquals(1, book.getMethods().size());
	    Method method = book.getMethods().get(0);
	    assertEquals("getTitle", method.getName());
	    assertEquals("String", method.getReturnType());
	    assertEquals("+", method.getVisibility());
	    assertTrue(method.getParameters().isEmpty());
	}
	
	@Test
	public void testClassWithExtendsAndImplements() throws IOException {
	    String code = """
	        public class Book extends Document implements Printable, Serializable {
	            private String title;
	        }
	    """;

	    File tempFile = createTempJavaFile("Book", code);
	    JavaSourceParser parser = new JavaSourceParser();
	    IntermediateModel model = parser.parse(tempFile);

	    assertEquals(4, model.getClasses().size()); // Book, Document, Printable, Serializable

	    ClassInfo book = model.findClassByName("Book");
	    assertNotNull(book);
	    assertEquals(ClassType.CLASS, book.getClassType());

	    List<Relationship> relationships = model.getRelationships();
	    assertEquals(3, relationships.size());

	    assertTrue(relationships.stream().anyMatch(r ->
	        r.getSourceClass().getName().equals("Book") &&
	        r.getTargetClass().getName().equals("Document") &&
	        r.getType() == RelationshipType.GENERALIZATION));

	    assertTrue(relationships.stream().anyMatch(r ->
	        r.getSourceClass().getName().equals("Book") &&
	        r.getTargetClass().getName().equals("Printable") &&
	        r.getType() == RelationshipType.REALIZATION));

	    assertTrue(relationships.stream().anyMatch(r ->
	        r.getSourceClass().getName().equals("Book") &&
	        r.getTargetClass().getName().equals("Serializable") &&
	        r.getType() == RelationshipType.REALIZATION));
	}

	@Test
	public void testAttributeWithGenericType() throws IOException {
	    String code = """
	        public class Library {
	            private List<Book> books;
	        }
	    """;

	    File tempFile = createTempJavaFile("Library", code);
	    JavaSourceParser parser = new JavaSourceParser();

	    // Add the Book class to the model manually to simulate multi-file context
	    ScannedJavaInfo fakeBook = new ScannedJavaInfo("Book", "Book", "", ClassType.CLASS, tempFile);
	    IntermediateModel model = parser.parse(List.of(fakeBook));

	    assertEquals(2, model.getClasses().size()); // Library + Book

	    List<Relationship> rels = model.getRelationships();
	    assertEquals(1, rels.size());

	    Relationship dep = rels.get(0);
	    assertEquals("Library", dep.getSourceClass().getName());
	    assertEquals("Book", dep.getTargetClass().getName());
	    assertEquals(RelationshipType.DEPENDENCY, dep.getType());
	}
	
	@Test
	public void testSimpleInterfaceParsing() throws IOException {
	    String code = """
	        public interface Printer {
	            void print(String message);
	        }
	    """;

	    File tempFile = createTempJavaFile("Printer", code);
	    JavaSourceParser parser = new JavaSourceParser();
	    IntermediateModel model = parser.parse(tempFile);

	    assertEquals(1, model.getClasses().size());

	    ClassInfo printer = model.findClassByName("Printer");
	    assertNotNull(printer);
	    assertEquals(ClassType.INTERFACE, printer.getClassType());

	    assertEquals(1, printer.getMethods().size());

	    Method method = printer.getMethods().get(0);
	    assertEquals("print", method.getName());
	    assertEquals("void", method.getReturnType());
	    assertEquals("+", method.getVisibility()); // public method
	    assertEquals(List.of("String"), method.getParameters());
	}

	@Test
	public void testEnumParsing() throws IOException {
	    String code = """
	        public enum Status {
	            ACTIVE, INACTIVE, ARCHIVED;
	        }
	    """;

	    File tempFile = createTempJavaFile("Status", code);
	    JavaSourceParser parser = new JavaSourceParser();
	    IntermediateModel model = parser.parse(tempFile);

	    assertEquals(1, model.getClasses().size());

	    ClassInfo status = model.findClassByName("Status");
	    assertNotNull(status);
	    assertEquals(ClassType.ENUM, status.getClassType());

	    assertTrue(status.getAttributes().isEmpty());
	    assertTrue(status.getMethods().isEmpty());
	}
	
	@Test
	public void testDependencyFromMethodReturnType() throws IOException {
	    String code = """
	        public class Library {
	            public Book getFeaturedBook() {
	                return null;
	            }
	        }
	    """;

	    File tempFile = createTempJavaFile("Library", code);
	    JavaSourceParser parser = new JavaSourceParser();

	    IntermediateModel model = parser.parse(tempFile);

	    assertEquals(2, model.getClasses().size()); // Library + Book

	    ClassInfo library = model.findClassByName("Library");
	    assertNotNull(library);

	    Relationship dep = model.getRelationships().stream()
	        .filter(r -> r.getSourceClass().getName().equals("Library") &&
	                     r.getTargetClass().getName().equals("Book") &&
	                     r.getType() == RelationshipType.DEPENDENCY)
	        .findFirst()
	        .orElse(null);

	    assertNotNull(dep, "Expected a dependency from Library to Book");
	}


	@Test
	public void testMethodWithMixedTypeParameters() throws IOException {
	    String code = """
	        public class Calculator {
	            public double calculate(String operation, int a, double b, Book helper) {
	                return 0.0;
	            }
	        }
	    """;

	    File tempFile = createTempJavaFile("Calculator", code);
	    JavaSourceParser parser = new JavaSourceParser();

	    // Add Book as another class in model so dependency is recognized
	    ScannedJavaInfo fakeBook = new ScannedJavaInfo("Book", "Book", "", ClassType.CLASS, tempFile);
	    IntermediateModel model = parser.parse(List.of(fakeBook));

	    assertEquals(2, model.getClasses().size());

	    ClassInfo calc = model.findClassByName("Calculator");
	    assertNotNull(calc);

	    List<Method> methods = calc.getMethods();
	    assertEquals(1, methods.size());

	    Method method = methods.get(0);
	    assertEquals("calculate", method.getName());
	    assertEquals("double", method.getReturnType());
	    assertEquals("+", method.getVisibility());

	    // Validate parsed parameter types
	    assertEquals(List.of("String", "int", "double", "Book"), method.getParameters());

	    // Validate dependency from "Book"
	    List<Relationship> relationships = model.getRelationships();
	    assertEquals(1, relationships.size());

	    Relationship dep = relationships.get(0);
	    assertEquals("Calculator", dep.getSourceClass().getName());
	    assertEquals("Book", dep.getTargetClass().getName());
	    assertEquals(RelationshipType.DEPENDENCY, dep.getType());
	}

	
	// Helper class to not repeat steps
	private File createTempJavaFile(String className, String content) throws IOException {
	    File tempFile = File.createTempFile(className, ".java");
	    try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
	        writer.write(content);
	    }
	    tempFile.deleteOnExit();
	    return tempFile;
	}

}
