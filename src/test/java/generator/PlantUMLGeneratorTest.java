package generator;

import static org.junit.jupiter.api.Assertions.*;

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

	@Test
	@DisplayName("Generates simple class with fields and methods")
	void simpleClassMembers() {
		// Class
		ClassInfo user = new ClassInfo("User", ClassType.CLASS, ClassDeclaration.OFFICIAL);
		user.addAttribute(new Attribute("name", "String", "private"));
		user.addMethod(new Method("getName", "String", "public")); // updated ctor

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
		model.addRelationship(new Relationship(cc2, cc1, RelationshipType.REALIZATION)); // becomes GENERALIZATION
		model.addRelationship(new Relationship(cls, ci1, RelationshipType.GENERALIZATION)); // becomes REALIZATION
		model.addRelationship(new Relationship(ci2, ci1, RelationshipType.GENERALIZATION)); // stays GENERALIZATION

		String n = norm(new PlantUMLGenerator().generate(model));

		// New left-pointing expectations:
		assertTrue(n.contains("Base <|-- Child")); // Class -> Class
		assertTrue(n.contains("IShape <|.. Painter")); // Class -> Interface
		assertTrue(n.contains("IShape <|-- Drawable")); // Interface -> Interface
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

		// Check absence using new left-pointing style
		assertFalse(n.contains("C <|-- E"));
		assertFalse(n.contains("I <|.. E"));
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
		assertFalse(n.contains("A <|-- A"));
		assertFalse(n.contains("A <|.. A"));
		assertTrue(n.contains("class A {"));
	}

	@Test
	@DisplayName("Static members are underlined with __...__ in output")
	void staticMembersAreUnderlined() {
		ClassInfo utils = new ClassInfo("Utils", ClassType.CLASS, ClassDeclaration.OFFICIAL);

		Attribute version = new Attribute("VERSION", "String", "public");
		version.setStatic(true);
		utils.addAttribute(version);

		Method now = new Method("now", "long", "public");
		now.setStatic(true);
		utils.addMethod(now);

		IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
		model.addClass(utils);

		String n = norm(new PlantUMLGenerator().generate(model));

		assertTrue(n.contains("class Utils {"));
		assertTrue(n.contains("+ __VERSION : String__"), "Static attribute should be wrapped in __");
		assertTrue(n.contains("+ __now() : long__"), "Static method should be wrapped in __");
	}

	@Test
	@DisplayName("Static vs non-static members render correctly together")
	void mixedStaticAndNonStatic() {
		ClassInfo cfg = new ClassInfo("Config", ClassType.CLASS, ClassDeclaration.OFFICIAL);

		Attribute path = new Attribute("path", "String", "private");
		path.setStatic(false);
		cfg.addAttribute(path);

		Attribute home = new Attribute("HOME", "String", "public");
		home.setStatic(true);
		cfg.addAttribute(home);

		Method load = new Method("load", "void", "public");
		load.setStatic(false);
		cfg.addMethod(load);

		Method defaults = new Method("defaults", "Config", "public");
		defaults.setStatic(true);
		cfg.addMethod(defaults);

		IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
		model.addClass(cfg);

		String n = norm(new PlantUMLGenerator().generate(model));

		assertTrue(n.contains("- path : String"), "Non-static private attribute should render normally");
		assertTrue(n.contains("+ __HOME : String__"), "Static public attribute should be underlined");
		assertTrue(n.contains("+ load() : void"), "Non-static method should render normally");
		assertTrue(n.contains("+ __defaults() : Config__"), "Static method should be underlined");
	}

	@Test
	@DisplayName("excludePrivate also hides private static members")
	void excludePrivateHidesPrivateStatics() {
		ClassInfo logger = new ClassInfo("Logger", ClassType.CLASS, ClassDeclaration.OFFICIAL);

		Attribute counter = new Attribute("counter", "int", "private");
		counter.setStatic(true);
		logger.addAttribute(counter);

		Method init = new Method("init", "void", "private");
		init.setStatic(true);
		logger.addMethod(init);

		Method log = new Method("log", "void", "public");
		log.setStatic(false);
		logger.addMethod(log);

		IntermediateModel model = new IntermediateModel(ModelSource.SOURCE_CODE);
		model.addClass(logger);

		PlantUMLGenerator.Options opts = new PlantUMLGenerator.Options();
		opts.excludePrivate = true;
		String n = norm(new PlantUMLGenerator(opts).generate(model));

		assertTrue(n.contains("class Logger {"));
		assertFalse(n.contains("__- counter : int__"), "Private static attribute should be omitted");
		assertFalse(n.contains("__- init() : void__"), "Private static method should be omitted");
		assertTrue(n.contains("+ log() : void"), "Public non-static method should remain");
	}

	// Helper - Normalizes the output for robust matching: trims whitespace-only
	// lines
	private static String norm(String s) {
		return s.replace("\r", "").lines().map(String::trim).filter(l -> !l.isEmpty()).reduce((a, b) -> a + "\n" + b)
				.orElse("");
	}

}
