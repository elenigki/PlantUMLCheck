package parser;

import model.*;
import parser.code.JavaSourceParser;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyByte;

public class JavaSourceParserTest {

	@Test
	void testSimpleClassWithAttributes() throws IOException {
		File file = new File("src/test/resources/source_code_samples/Person.java");
		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(file);

		assertEquals(1, model.getClasses().size());
		ClassInfo person = model.getClasses().get(0);
		assertEquals("Person", person.getName());
		assertEquals(3, person.getAttributes().size());

		Attribute nameAttr = person.getAttributes().get(0);
		assertEquals("name", nameAttr.getName());
		assertEquals("String", nameAttr.getType());
		assertEquals("-", nameAttr.getVisibility());
	}

	@Test
	void testImplementsAndExtendsWithAttributes() throws IOException {
		File folder = new File("src/test/resources/source_code_samples/implements_extends/");
		File[] files = folder.listFiles((dir, name) -> name.endsWith(".java"));
		assertNotNull(files);
		assertTrue(files.length >= 4); // 4 expected files

		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(List.of(files));

		assertEquals(4, model.getClasses().size());
		assertEquals(3, model.getRelationships().size());

		// Class names check
		List<String> classNames = model.getClasses().stream().map(ClassInfo::getName).toList();
		assertTrue(classNames.containsAll(List.of("Person", "Student", "Identifiable", "Serializable")));

		// Use helper method instead of stream filters
		assertRelationshipExists(model, "Student", "Person", RelationshipType.GENERALIZATION);
		assertRelationshipExists(model, "Student", "Identifiable", RelationshipType.REALIZATION);
		assertRelationshipExists(model, "Student", "Serializable", RelationshipType.REALIZATION);

		// Check attributes in Person
		ClassInfo person = model.findClassByName("Person");
		assertNotNull(person);
		assertEquals(2, person.getAttributes().size());

		Attribute name = person.getAttributes().get(0);
		assertEquals("name", name.getName());
		assertEquals("String", name.getType());
		assertEquals("-", name.getVisibility());

		Attribute age = person.getAttributes().get(1);
		assertEquals("age", age.getName());
		assertEquals("int", age.getType());
		assertEquals("#", age.getVisibility());

		// Check attributes in Student
		ClassInfo student = model.findClassByName("Student");
		assertNotNull(student);
		assertEquals(1, student.getAttributes().size());

		Attribute gpa = student.getAttributes().get(0);
		assertEquals("gpa", gpa.getName());
		assertEquals("double", gpa.getType());
		assertEquals("+", gpa.getVisibility());
	}

	@Test
	void testUserClassWithMethods() throws Exception {
		File file = new File("src/test/resources/source_code_samples/User.java");

		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(file);

		assertEquals(3, model.getClasses().size());

		ClassInfo userClass = model.findClassByName("User");
		assertNotNull(userClass);
		assertEquals(1, userClass.getAttributes().size());
		assertEquals(2, userClass.getMethods().size());

		// Check method getName()
		Method getName = userClass.getMethods().stream().filter(m -> m.getName().equals("getName")).findFirst()
				.orElse(null);
		assertNotNull(getName);
		assertEquals("Name", getName.getReturnType());

		// Check method sendMessage()
		Method send = userClass.getMethods().stream().filter(m -> m.getName().equals("sendMessage")).findFirst()
				.orElse(null);
		assertNotNull(send);
		assertEquals(1, send.getParameters().size());
		assertEquals("Message", send.getParameters().get(0));

		// Use relationship assertions
		assertRelationshipExists(model, "User", "Message", RelationshipType.ASSOCIATION);
		assertRelationshipExists(model, "User", "Name", RelationshipType.ASSOCIATION);
	}

	@Test
	void testAssociationWithGenericTypes() throws Exception {
		JavaSourceParser parser = new JavaSourceParser();
		List<File> files = loadSampleFiles("simple_association");
		IntermediateModel model = parser.parse(files);

		assertEquals(2, model.getClasses().size());

		ClassInfo person = getClassByName(model, "Person");
		ClassInfo book = getClassByName(model, "Book");

		assertEquals(4, person.getAttributes().size());

		// Expecting attributes with types Book and List<Book>
		List<String> expectedAttrTypes = List.of("String", "int", "Book", "List<Book>");
		List<String> actualAttrTypes = person.getAttributes().stream().map(Attribute::getType).toList();
		assertTrue(actualAttrTypes.containsAll(expectedAttrTypes));

		// Expecting at least one ASSOCIATION from Person to Book
		long assocCount = model.getRelationships().stream().filter(r -> r.getType() == RelationshipType.ASSOCIATION)
				.filter(r -> r.getSourceClass().getName().equals("Person"))
				.filter(r -> r.getTargetClass().getName().equals("Book")).count();

		assertTrue(assocCount >= 1, "Expected at least one ASSOCIATION from Person to Book");

		// Optional: ensure max 1 if we don't want duplicate associations
		// assertEquals(1, assocCount);
	}

	@Test
	public void testEachMissingReferenceTypeSeparately() throws Exception {
		JavaSourceParser parser = new JavaSourceParser();
		List<File> files = loadSampleFiles("missing_reference_complex");
		IntermediateModel model = parser.parse(files);

		for (Relationship r : model.getRelationships()) {
			System.out.println("Source: " + r.getSourceClass().getName() + "-> Target " + r.getTargetClass().getName()
					+ " [" + r.getType() + "]");
		}

		assertNotNull(model.findClassByName("ReferenceMissingPerUse"));

		// Check relationships
		assertNotNull(getRelationship(model, "ReferenceMissingPerUse", "A", RelationshipType.ASSOCIATION));
		assertNotNull(getRelationship(model, "ReferenceMissingPerUse", "B", RelationshipType.ASSOCIATION));
		assertNotNull(getRelationship(model, "ReferenceMissingPerUse", "C", RelationshipType.ASSOCIATION));
		assertNotNull(getRelationship(model, "ReferenceMissingPerUse", "D", RelationshipType.ASSOCIATION));

		// Check warnings
		List<String> warnings = model.getWarnings();
		assertEquals(4, warnings.size());
		assertTrue(warnings.stream().anyMatch(w -> w.contains("A")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("B")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("C")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("D")));
	}

	@Test
	public void testMixedRelationships() throws Exception {
		JavaSourceParser parser = new JavaSourceParser();
		List<File> files = loadSampleFiles("mixed_relationships");
		IntermediateModel model = parser.parse(files);

		ClassInfo cls = model.findClassByName("MixedExample");

		// for (Relationship r : model.getRelationships()) {
		// System.out.printf("[%s] %s -> %s\n", r.getType(),
		// r.getSourceClass().getName(),
		// r.getTargetClass().getName());
		// }

		for (String w : model.getWarnings()) {
			System.out.println(w);
		}

		assertNotNull(cls);

		assertNotNull(getRelationship(model, "MixedExample", "Book", RelationshipType.ASSOCIATION));
		assertNotNull(getRelationship(model, "MixedExample", "Page", RelationshipType.COMPOSITION));
		assertNotNull(getRelationship(model, "MixedExample", "Chapter", RelationshipType.AGGREGATION));
		assertNotNull(getRelationship(model, "MixedExample", "Printer", RelationshipType.ASSOCIATION));
		assertNotNull(getRelationship(model, "MixedExample", "Scanner", RelationshipType.ASSOCIATION));

		// Check that missing classes are captured with warnings
		List<String> warnings = model.getWarnings();
		assertEquals(5, warnings.size());
		for (String missing : List.of("Book", "Page", "Chapter", "Printer", "Scanner")) {
			boolean found = warnings.stream().anyMatch(w -> w.contains(missing));
			assertTrue(found, "Expected warning for missing: " + missing);
		}
	}

	@Test
	public void testComplexNestedTypes() throws Exception {
		JavaSourceParser parser = new JavaSourceParser();
		List<File> files = loadSampleFiles("complex_nested");
		IntermediateModel model = parser.parse(files);

		for (Relationship r : model.getRelationships()) {
			System.out.printf("[%s] %s -> %s\n", r.getType(), r.getSourceClass().getName(),
					r.getTargetClass().getName());
		}

		for (String w : model.getWarnings()) {
			System.out.println("[WARNING]: " + w);
		}

		assertNotNull(model.findClassByName("ComplexExample"));

		// ASSOCIATION from attribute
		assertRelationshipExists(model, "ComplexExample", "Book", RelationshipType.ASSOCIATION);

		// COMPOSITION from new HashSet<Section>()
		assertRelationshipExists(model, "ComplexExample", "Section", RelationshipType.COMPOSITION);

		// DEPENDENCIES from parameters and return types
		assertRelationshipExists(model, "ComplexExample", "Page", RelationshipType.ASSOCIATION);
		assertRelationshipExists(model, "ComplexExample", "Chapter", RelationshipType.ASSOCIATION);

		// Warnings for missing referenced classes
		List<String> warnings = model.getWarnings();
		assertEquals(4, warnings.size()); // Book, Section, Page, Chapter

		assertTrue(warnings.stream().anyMatch(w -> w.contains("Book")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("Section")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("Page")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("Chapter")));
	}

	@Test
	public void testSetterAggregationDetection() throws Exception {
		JavaSourceParser parser = new JavaSourceParser();
		List<File> files = loadSampleFiles("setter");
		IntermediateModel model = parser.parse(files);

		for (Relationship r : model.getRelationships()) {
			System.out.printf("[%s] %s -> %s\n", r.getType(), r.getSourceClass().getName(),
					r.getTargetClass().getName());
		}

		assertNotNull(model.findClassByName("SetterAggregationExample"));

		assertRelationshipExists(model, "SetterAggregationExample", "Chapter", RelationshipType.AGGREGATION);
		assertNull(getRelationship(model, "SetterAggregationExample", "Book", RelationshipType.AGGREGATION));

	}

	@Test
	void testMultilineMethodAndAttribute() throws Exception {
		File file = new File("src/test/resources/source_code_samples/MultilineExample.java");
		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(file);

		assertEquals(1, model.getClasses().size());

		ClassInfo cls = model.getClasses().get(0);
		assertEquals("MultilineExample", cls.getName());

		// Check attribute
		assertEquals(1, cls.getAttributes().size());
		Attribute attr = cls.getAttributes().get(0);
		assertEquals("name", attr.getName());
		assertEquals("String", attr.getType());

		// Check method
		assertEquals(1, cls.getMethods().size());
		Method method = cls.getMethods().get(0);
		assertEquals("setName", method.getName());
		assertEquals("void", method.getReturnType());
		assertTrue(method.getParameters().contains("String"));
	}

	@Test
	void testAdvancedMultilineParsing() throws Exception {
		File file = new File("src/test/resources/source_code_samples/AdvancedMultilineExample.java");
		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(file);

		for (ClassInfo c : model.getClasses()) {
			System.out.println("Class: " + c.getName() + " , Declaration: " + c.getDeclaration());
			for (Attribute a : c.getAttributes()) {
				if (!a.getName().isEmpty()) {
					System.out.println("Attributes: " + a.getName() + ", Type: " + a.getType());
				}
			}

		}

		for (Relationship r : model.getRelationships()) {
			System.out.println("[" + r.getType() + "] Source: " + r.getSourceClass().getName() + " Target: "
					+ r.getTargetClass().getName());
		}

		// ======= Class Count =======
		assertEquals(3, model.getClasses().size(), "Expected 1 official class and 2 dummy classes.");

		long officialCount = model.getClasses().stream().filter(c -> c.getDeclaration() == ClassDeclaration.OFFICIAL)
				.count();
		long dummyCount = model.getClasses().stream().filter(c -> c.getDeclaration() == ClassDeclaration.DUMMY).count();

		assertEquals(1, officialCount, "Expected 1 official class.");
		assertEquals(2, dummyCount, "Expected 2 dummy classes (Book, Page).");

		// ======= Class Info =======
		ClassInfo cls = model.findClassByName("AdvancedMultilineExample");
		assertNotNull(cls, "Class 'AdvancedMultilineExample' should exist.");
		assertEquals(ClassDeclaration.OFFICIAL, cls.getDeclaration());

		// ======= Class Names and Declarations =======
		Map<String, ClassDeclaration> expectedClasses = Map.of("AdvancedMultilineExample", ClassDeclaration.OFFICIAL,
				"Book", ClassDeclaration.DUMMY, "Page", ClassDeclaration.DUMMY);

		for (Map.Entry<String, ClassDeclaration> entry : expectedClasses.entrySet()) {
			String className = entry.getKey();
			ClassDeclaration expectedDeclaration = entry.getValue();

			ClassInfo classInfo = model.findClassByName(className);
			assertNotNull(classInfo, "Expected class: " + className);
			assertEquals(expectedDeclaration, classInfo.getDeclaration(),
					"Class " + className + " should be declared as " + expectedDeclaration);
		}

		// ======= Attributes =======
		List<Attribute> attributes = cls.getAttributes();
		assertEquals(1, attributes.size(), "Expected one attribute.");
		Attribute booksAttr = attributes.get(0);
		assertEquals("books", booksAttr.getName());
		assertEquals("List<Book>", booksAttr.getType());
		assertEquals("-", booksAttr.getVisibility());

		// ======= Methods =======
		List<Method> methods = cls.getMethods();
		assertEquals(2, methods.size(), "Expected two methods.");

		Method getPageMap = methods.stream().filter(m -> m.getName().equals("getPageMap")).findFirst().orElse(null);
		assertNotNull(getPageMap);
		assertEquals("Map<String,List<Page>>", getPageMap.getReturnType().replaceAll("\\s+", ""));
		assertEquals("+", getPageMap.getVisibility());
		assertEquals(List.of("String"), getPageMap.getParameters());

		Method setBooks = methods.stream().filter(m -> m.getName().equals("setBooks")).findFirst().orElse(null);
		assertNotNull(setBooks);
		assertEquals("void", setBooks.getReturnType());
		assertEquals("+", setBooks.getVisibility());
		assertEquals(List.of("List<Book>"), setBooks.getParameters());

		// ======= Relationships =======
		assertRelationshipExists(model, "AdvancedMultilineExample", "Book", RelationshipType.COMPOSITION);
		assertRelationshipExists(model, "AdvancedMultilineExample", "Book", RelationshipType.AGGREGATION);
		assertRelationshipExists(model, "AdvancedMultilineExample", "Page", RelationshipType.ASSOCIATION);

		// ======= Dummy Class Warnings =======
		List<String> warnings = model.getWarnings();
		assertEquals(2, warnings.size(), "Expected 2 warnings for dummy classes.");
		assertTrue(warnings.stream().anyMatch(w -> w.contains("Book")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("Page")));
	}

	@Test
	void parses_AllOnOneLine_ClassBodyAndMembers() throws IOException {
		String src = """
				package scenario01; public class Customer { private Order order; public void setOrder(Order order){ this.order = order; } public Order getOrder(){ return order; } }
				""";
		Path f = java.nio.file.Files.createTempFile("one_line_customer", ".java");
		java.nio.file.Files.writeString(f, src);
		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(List.of(f.toFile()));

		ClassInfo c = getClassByName(model, "Customer");
		assertEquals(1, c.getAttributes().size());
		assertEquals("Order", c.getAttributes().get(0).getType());
		assertEquals(2, c.getMethods().size());
		assertTrue(c.getMethods().stream().anyMatch(m -> m.getName().equals("setOrder")
				&& m.getParameters().equals(List.of("Order")) && "void".equalsIgnoreCase(m.getReturnType())));
		assertTrue(c.getMethods().stream().anyMatch(m -> m.getName().equals("getOrder") && m.getParameters().isEmpty()
				&& "Order".equals(m.getReturnType())));
		assertRelationshipExists(model, "Customer", "Order", RelationshipType.ASSOCIATION);
	}

	@Test
	void parses_MixedSameLineAndNewlines_InClassBody() throws IOException {
		String src = """
				package scenario01; public class Customer {
				    private Order order; public void setOrder(Order order){ this.order = order; }
				    public Order getOrder(){ return order; }
				}
				""";
		Path f = java.nio.file.Files.createTempFile("mixed_lines_customer", ".java");
		java.nio.file.Files.writeString(f, src);
		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(List.of(f.toFile()));

		ClassInfo c = getClassByName(model, "Customer");
		assertEquals(1, c.getAttributes().size());
		assertEquals(2, c.getMethods().size());
		assertRelationshipExists(model, "Customer", "Order", RelationshipType.ASSOCIATION);
	}

	@Test
	void doesNotSplit_ForLoopsOrStrings() throws IOException {
		String src = """
				package p; public class LoopHolder {
				    public void run(int n){
				        for (int i = 0; i < n; i++) { String s = "a;b}c"; }
				    }
				}
				""";
		Path f = java.nio.file.Files.createTempFile("for_loop_strings", ".java");
		java.nio.file.Files.writeString(f, src);
		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(List.of(f.toFile()));

		ClassInfo c = getClassByName(model, "LoopHolder");
		assertEquals(1, c.getMethods().size());
		assertTrue(c.getMethods().get(0).getName().equals("run"));
		assertEquals(List.of("int"), c.getMethods().get(0).getParameters());
	}

	@Test
	void parses_PackageImportChainFollowedByType() throws IOException {
		String src = """
				package p.q; import java.util.*; import java.io.*; public class C { public List<String> xs; }
				""";
		Path f = java.nio.file.Files.createTempFile("pkg_import_chain", ".java");
		java.nio.file.Files.writeString(f, src);
		JavaSourceParser parser = new JavaSourceParser();
		IntermediateModel model = parser.parse(List.of(f.toFile()));

		ClassInfo c = getClassByName(model, "C");
		assertEquals(1, c.getAttributes().size());
		assertEquals("List<String>", c.getAttributes().get(0).getType());
	}
	
	@Test
	void parses_HeavilyMixedFormatting_WithComments_AndGenerics() throws IOException {
	    String src = """
        package demo; import java.util.*; public class Customer {
            // field + method header on one physical line:
            private List<Order> orders = new ArrayList<>(); public void addOrder(Order o){ /* start */ this.orders.add(o); /* } ; */ }
            /* multi
               line comment with } and ; inside */
            private Map<String,Integer> stats = new HashMap<>(); // end-of-line comment ; }

            // header on its own line, body starts on same line:
            public int totalOrders(){ int c = 0; for (int i=0; i<orders.size(); i++) { c++; } return c; }

            @Deprecated public List<Order> getOrders() { return orders; } // annotation + inline body
        }
	        """;
	    Path f = java.nio.file.Files.createTempFile("customer_mixed_big", ".java");
	    java.nio.file.Files.writeString(f, src);
	    JavaSourceParser parser = new JavaSourceParser();
	    IntermediateModel model = parser.parse(List.of(f.toFile()));
	    
	    for(Attribute a: model.findClassByName("Customer").getAttributes()) {
	    	System.out.println("Attribute: " + a.getName() + ", Type: " + a.getType() + ", Visibility: " + a.getVisibility());
	    }
	    for(Method m: model.findClassByName("Customer").getMethods()) {
	    	System.out.println("Method: " + m.getName() + ", ReturnType: " + m.getReturnType() + ", Visibility: " + m.getVisibility());
	    }

	    ClassInfo c = getClassByName(model, "Customer");
	    assertNotNull(c);

	    // attributes
	    assertTrue(c.getAttributes().stream().anyMatch(a -> a.getName().equals("orders")));
	    assertTrue(c.getAttributes().stream().anyMatch(a -> a.getName().equals("stats")));

	    // methods by name (don’t depend on parameter type rendering)
	    assertTrue(c.getMethods().stream().anyMatch(m -> m.getName().equals("addOrder")));
	    assertTrue(c.getMethods().stream().anyMatch(m -> m.getName().equals("totalOrders")));
	    assertTrue(c.getMethods().stream().anyMatch(m -> m.getName().equals("getOrders")));

	    // relationship via List<Order>
	    assertRelationshipExists(model, "Customer", "Order", RelationshipType.ASSOCIATION);
	}



	// Helper to find a specific relationship between two classes
	private Relationship getRelationship(IntermediateModel model, String source, String target, RelationshipType type) {
		return model
				.getRelationships().stream().filter(r -> r.getType() == type
						&& r.getSourceClass().getName().equals(source) && r.getTargetClass().getName().equals(target))
				.findFirst().orElse(null);
	}

	private List<File> loadSampleFiles(String folderName) {
		File folder = Path.of("src/test/resources/source_code_samples", folderName).toFile();
		File[] files = folder.listFiles((dir, name) -> name.endsWith(".java"));
		return files != null ? List.of(files) : List.of();
	}

	private void assertRelationshipExists(IntermediateModel model, String source, String target,
			RelationshipType type) {
		Relationship rel = getRelationship(model, source, target, type);
		assertNotNull(rel, "Expected " + type + " from " + source + " to " + target + " not found.");
	}

	private ClassInfo getClassByName(IntermediateModel model, String name) {
		return model.getClasses().stream().filter(c -> c.getName().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError("Class not found: " + name));
	}

}
