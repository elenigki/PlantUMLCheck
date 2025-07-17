package parser;

import model.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
		assertRelationshipExists(model, "User", "Message", RelationshipType.DEPENDENCY);
		assertRelationshipExists(model, "User", "Name", RelationshipType.DEPENDENCY);
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
		assertNotNull(getRelationship(model, "ReferenceMissingPerUse", "B", RelationshipType.DEPENDENCY));
		assertNotNull(getRelationship(model, "ReferenceMissingPerUse", "C", RelationshipType.DEPENDENCY));
		assertNotNull(getRelationship(model, "ReferenceMissingPerUse", "D", RelationshipType.DEPENDENCY));

		// Check warnings
		List<String> warnings = model.getWarnings();
		assertEquals(4, warnings.size());
		assertTrue(warnings.stream().anyMatch(w -> w.contains("A")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("B")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("C")));
		assertTrue(warnings.stream().anyMatch(w -> w.contains("D")));
	}

//////////////// Previous Tests ////////////////
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
		assertNotNull(getRelationship(model, "MixedExample", "Printer", RelationshipType.DEPENDENCY));
		assertNotNull(getRelationship(model, "MixedExample", "Scanner", RelationshipType.DEPENDENCY));

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

		assertNotNull(model.findClassByName("ComplexExample"));

		// ASSOCIATION from attribute
		assertRelationshipExists(model, "ComplexExample", "Book", RelationshipType.ASSOCIATION);

		// COMPOSITION from new HashSet<Section>()
		assertRelationshipExists(model, "ComplexExample", "Section", RelationshipType.COMPOSITION);

		// DEPENDENCIES from parameters and return types
		assertRelationshipExists(model, "ComplexExample", "Page", RelationshipType.DEPENDENCY);
		assertRelationshipExists(model, "ComplexExample", "Chapter", RelationshipType.DEPENDENCY);

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
