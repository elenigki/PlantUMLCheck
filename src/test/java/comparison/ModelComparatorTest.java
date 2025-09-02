package comparison;

import comparison.issues.Difference;
import comparison.issues.IssueKind;
import comparison.issues.IssueLevel;
import model.ClassInfo;
import model.ClassType;
import model.IntermediateModel;
import model.ModelSource;
import model.RelationshipType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static testutil.TestModelBuilder.*;

/** Smoke tests to prove wiring + basic rules work. */
public class ModelComparatorTest {

	@Test
	void emptyModels_yieldNoDifferences() {
		IntermediateModel code = codeModel();
		IntermediateModel uml = umlModel();

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertNotNull(diffs, "diff list should not be null");
		assertTrue(diffs.isEmpty(), "empty models should produce no differences");
	}

	@Test
	void matchingClassAndAttribute_strict_noDifferences() {
		// code
		IntermediateModel code = codeModel();
		ClassInfo cc = addClass(code, "Customer", ClassType.CLASS);
		addAttr(cc, "email", "String", "+");

		// uml
		IntermediateModel uml = umlModel();
		ClassInfo uc = addClass(uml, "Customer", ClassType.CLASS);
		addAttr(uc, "email", "String", "+");

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertTrue(diffs.isEmpty(), "identical class+attr should have no diffs");
	}

	@Test
	void classMissingInUml_relaxed_reportsInfo() {
		IntermediateModel code = codeModel();
		addClass(code, "Order", ClassType.CLASS);

		IntermediateModel uml = umlModel();

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size(), "one class presence diff expected");
		Difference d = diffs.get(0);
		assertEquals(IssueKind.CLASS_MISSING_IN_UML, d.getKind());
		assertEquals(IssueLevel.INFO, d.getLevel(), "RELAXED → code-only class is INFO");
		assertEquals("Order", d.getWhere());
		assertEquals("missing", d.getUml());
		assertEquals("present", d.getCode());
	}

	@Test
	void attributeTypeMismatch_strict_isError() {
		// code: double
		IntermediateModel code = codeModel();
		ClassInfo cc = addClass(code, "Invoice", ClassType.CLASS);
		addAttr(cc, "total", "double", "+");

		// uml: String
		IntermediateModel uml = umlModel();
		ClassInfo uc = addClass(uml, "Invoice", ClassType.CLASS);
		addAttr(uc, "total", "String", "+");

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size(), "one mismatch expected");
		Difference d = diffs.get(0);
		assertEquals(IssueKind.ATTRIBUTE_MISMATCH, d.getKind());
		assertEquals(IssueLevel.ERROR, d.getLevel());
		assertEquals("Invoice.attr:total", d.getWhere());
		assertEquals("String", d.getUml());
		assertEquals("double", d.getCode());
		assertTrue(d.getSummary().toLowerCase().contains("type"), "summary should mention type mismatch");
	}

	// --- METHODS ---

	@Test
	void methodReturnTypeMismatch_strict_isError() {
		IntermediateModel code = codeModel();
		ClassInfo cc = addClass(code, "UserService", ClassType.CLASS);
		addMethod(cc, "findById", "User", "+", "int"); // User findById(int)

		IntermediateModel uml = umlModel();
		ClassInfo uc = addClass(uml, "UserService", ClassType.CLASS);
		addMethod(uc, "findById", "String", "+", "int"); // String findById(int)

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.METHOD_MISMATCH, d.getKind());
		assertEquals(IssueLevel.ERROR, d.getLevel());
		assertEquals("UserService#findById(int)", d.getWhere());
		assertEquals("String", d.getUml());
		assertEquals("User", d.getCode());
		assertTrue(d.getSummary().toLowerCase().contains("return"), "should mention return type mismatch");
	}

	// --- VISIBILITY (RELAXED) ---

	// --- RELATIONSHIPS: OWNERSHIP STRENGTH ---

	@Test
	void ownershipStrength_codeComposition_vs_umlAssociation_strict_isError() {
		IntermediateModel code = codeModel();
		ClassInfo order = addClass(code, "Order", ClassType.CLASS);
		ClassInfo line = addClass(code, "OrderLine", ClassType.CLASS);
		addRel(code, order, line, RelationshipType.COMPOSITION); // code stronger

		IntermediateModel uml = umlModel();
		ClassInfo uOrder = addClass(uml, "Order", ClassType.CLASS);
		ClassInfo uLine = addClass(uml, "OrderLine", ClassType.CLASS);
		addRel(uml, uOrder, uLine, RelationshipType.ASSOCIATION); // uml weaker

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
		assertEquals(IssueLevel.ERROR, d.getLevel());
		assertEquals("Order -> OrderLine", d.getWhere());
		assertEquals("ASSOCIATION", d.getUml());
		assertEquals("COMPOSITION", d.getCode());
		assertTrue(d.getSummary().toLowerCase().contains("ownership"), "should mention ownership");
	}

	// --- RELATIONSHIPS: DEPENDENCY POLICY ---

	@Test
	void dependencyInUml_butCodeHasStronger_relaxed_warnsToUpgrade() {
		IntermediateModel code = codeModel();
		ClassInfo a = addClass(code, "A", ClassType.CLASS);
		ClassInfo b = addClass(code, "B", ClassType.CLASS);
		addRel(code, a, b, RelationshipType.ASSOCIATION); // stronger in code

		IntermediateModel uml = umlModel();
		ClassInfo ua = addClass(uml, "A", ClassType.CLASS);
		ClassInfo ub = addClass(uml, "B", ClassType.CLASS);
		addRel(uml, ua, ub, RelationshipType.DEPENDENCY); // only dependency in UML

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
		assertEquals(IssueLevel.WARNING, d.getLevel()); // relaxed → warn
		assertEquals("A -> B", d.getWhere());
		assertEquals("DEPENDENCY", d.getUml());
		assertEquals("ASSOCIATION", d.getCode());
	}

	// --- RELATIONSHIPS: INHERITANCE/REALIZATION ---

	@Test
	void generalizationMissingInUml_strict_isError() {
		IntermediateModel code = codeModel();
		ClassInfo base = addClass(code, "Base", ClassType.CLASS);
		ClassInfo sub = addClass(code, "Sub", ClassType.CLASS);
		addRel(code, sub, base, RelationshipType.GENERALIZATION); // Sub extends Base

		IntermediateModel uml = umlModel();
		ClassInfo uBase = addClass(uml, "Base", ClassType.CLASS);
		ClassInfo uSub = addClass(uml, "Sub", ClassType.CLASS);
		// no edge in UML

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISSING_IN_UML, d.getKind());
		assertEquals(IssueLevel.ERROR, d.getLevel());
		assertEquals("Sub -> Base", d.getWhere());
		assertEquals("missing", d.getUml());
		assertEquals("GENERALIZATION", d.getCode());
	}

	// --- RELAXED PARAMETER MATCHING ---

	@Test
	void overloadedMethods_strict_paramDifference_isMissingInCode() {
		IntermediateModel code = codeModel();
		ClassInfo svc = addClass(code, "Svc", ClassType.CLASS);
		addMethod(svc, "find", "User", "+", "int");

		IntermediateModel uml = umlModel();
		ClassInfo uSvc = addClass(uml, "Svc", ClassType.CLASS);
		addMethod(uSvc, "find", "User", "+", "Integer");

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size(), "STRICT: Integer != int → UML method missing in code");
		assertEquals(IssueKind.METHOD_MISSING_IN_CODE, diffs.get(0).getKind());
	}

	@Test
	void ownership_extraWeakerEdgeInUml_noError() {
		// code: AGGREGATION
		IntermediateModel code = codeModel();
		ClassInfo a = addClass(code, "A", ClassType.CLASS);
		ClassInfo b = addClass(code, "B", ClassType.CLASS);
		addRel(code, a, b, RelationshipType.AGGREGATION);

		// uml: AGGREGATION + ASSOCIATION (duplicate weaker)
		IntermediateModel uml = umlModel();
		ClassInfo ua = addClass(uml, "A", ClassType.CLASS);
		ClassInfo ub = addClass(uml, "B", ClassType.CLASS);
		addRel(uml, ua, ub, RelationshipType.AGGREGATION);
		addRel(uml, ua, ub, RelationshipType.ASSOCIATION);

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertTrue(diffs.isEmpty(), "Extra weaker edge in UML shouldn’t cause errors");
	}

	@Test
	void realizationInCode_butGeneralizationInUml_reportsTwoIssues() {
		IntermediateModel code = codeModel();
		ClassInfo impl = addClass(code, "Impl", ClassType.CLASS);
		ClassInfo api = addClass(code, "API", ClassType.INTERFACE);
		addRel(code, impl, api, RelationshipType.REALIZATION); // class implements interface

		IntermediateModel uml = umlModel();
		ClassInfo uImpl = addClass(uml, "Impl", ClassType.CLASS);
		ClassInfo uApi = addClass(uml, "API", ClassType.INTERFACE);
		addRel(uml, uImpl, uApi, RelationshipType.GENERALIZATION); // wrong in UML

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(2, diffs.size(), "Should report missing REALIZATION and extra GENERALIZATION");
		assertTrue(
				diffs.stream()
						.anyMatch(d -> d.getKind() == IssueKind.RELATIONSHIP_MISSING_IN_UML
								&& "Impl -> API".equals(d.getWhere()) && "REALIZATION".equals(d.getCode())),
				"Missing REALIZATION in UML");
		assertTrue(
				diffs.stream()
						.anyMatch(d -> d.getKind() == IssueKind.RELATIONSHIP_MISSING_IN_CODE
								&& "Impl -> API".equals(d.getWhere()) && "GENERALIZATION".equals(d.getUml())),
				"GENERALIZATION present only in UML");
	}

	@Test
	void dependencyOnlyInUml_isSuggestion() {
		IntermediateModel code = codeModel();
		ClassInfo a = addClass(code, "A", ClassType.CLASS);
		ClassInfo b = addClass(code, "B", ClassType.CLASS);
		// no relationship in code

		IntermediateModel uml = umlModel();
		ClassInfo ua = addClass(uml, "A", ClassType.CLASS);
		ClassInfo ub = addClass(uml, "B", ClassType.CLASS);
		addRel(uml, ua, ub, RelationshipType.DEPENDENCY);

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
		assertEquals(IssueLevel.SUGGESTION, d.getLevel());
		assertEquals("A -> B", d.getWhere());
		assertEquals("DEPENDENCY", d.getUml());
	}

	// ====================== RELATIONSHIPS: EXTRA TESTS ======================

	@Test
	void ownership_umlStrongerThanCode_strict_isError() {
		// code: ASSOCIATION
		IntermediateModel code = codeModel();
		ClassInfo a = addClass(code, "Cfg", ClassType.CLASS);
		ClassInfo b = addClass(code, "Path", ClassType.CLASS);
		addRel(code, a, b, RelationshipType.ASSOCIATION);

		// uml: COMPOSITION (stronger)
		IntermediateModel uml = umlModel();
		ClassInfo ua = addClass(uml, "Cfg", ClassType.CLASS);
		ClassInfo ub = addClass(uml, "Path", ClassType.CLASS);
		addRel(uml, ua, ub, RelationshipType.COMPOSITION);

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
		assertEquals(IssueLevel.ERROR, d.getLevel());
		assertEquals("Cfg -> Path", d.getWhere());
		assertEquals("COMPOSITION", d.getUml());
		assertEquals("ASSOCIATION", d.getCode());
	}

	@Test
	void ownership_umlStrongerThanCode_relaxed_isWarning() {
		// code: ASSOCIATION
		IntermediateModel code = codeModel();
		ClassInfo a = addClass(code, "Shop", ClassType.CLASS);
		ClassInfo b = addClass(code, "Cart", ClassType.CLASS);
		addRel(code, a, b, RelationshipType.ASSOCIATION);

		// uml: AGGREGATION (stronger)
		IntermediateModel uml = umlModel();
		ClassInfo ua = addClass(uml, "Shop", ClassType.CLASS);
		ClassInfo ub = addClass(uml, "Cart", ClassType.CLASS);
		addRel(uml, ua, ub, RelationshipType.AGGREGATION);

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
		assertEquals(IssueLevel.WARNING, d.getLevel()); // relaxed → warn
		assertEquals("Shop -> Cart", d.getWhere());
		assertEquals("AGGREGATION", d.getUml());
		assertEquals("ASSOCIATION", d.getCode());
	}

	@Test
	void ownership_strongestSelected_whenMultipleOnSameSide() {
		// code: both ASSOCIATION and AGGREGATION → strongest is AGGREGATION
		IntermediateModel code = codeModel();
		ClassInfo a = addClass(code, "A1", ClassType.CLASS);
		ClassInfo b = addClass(code, "B1", ClassType.CLASS);
		addRel(code, a, b, RelationshipType.ASSOCIATION);
		addRel(code, a, b, RelationshipType.AGGREGATION);

		// uml: ASSOCIATION only
		IntermediateModel uml = umlModel();
		ClassInfo ua = addClass(uml, "A1", ClassType.CLASS);
		ClassInfo ub = addClass(uml, "B1", ClassType.CLASS);
		addRel(uml, ua, ub, RelationshipType.ASSOCIATION);

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size(), "Should compare strongest AGGREGATION vs ASSOCIATION");
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
		assertEquals("A1 -> B1", d.getWhere());
		assertEquals("ASSOCIATION", d.getUml());
		assertEquals("AGGREGATION", d.getCode());
	}

	@Test
	void noRelationshipOnEitherSide_noDifferences() {
		IntermediateModel code = codeModel();
		addClass(code, "Left", ClassType.CLASS);
		addClass(code, "Right", ClassType.CLASS);

		IntermediateModel uml = umlModel();
		addClass(uml, "Left", ClassType.CLASS);
		addClass(uml, "Right", ClassType.CLASS);

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertTrue(diffs.isEmpty(), "No edges at all → no differences");
	}

	@Test
	void codeOnlyDependency_noDifferences_perPolicy() {
		IntermediateModel code = codeModel();
		ClassInfo a = addClass(code, "Core", ClassType.CLASS);
		ClassInfo b = addClass(code, "Util", ClassType.CLASS);
		addRel(code, a, b, RelationshipType.DEPENDENCY); // advisory → ignore if UML omits

		IntermediateModel uml = umlModel();
		addClass(uml, "Core", ClassType.CLASS);
		addClass(uml, "Util", ClassType.CLASS);
		// no edge in UML

		ModelComparator cmp = new ModelComparator(CheckMode.STRICT);
		List<Difference> diffs = cmp.compare(code, uml);

		assertTrue(diffs.isEmpty(), "Code-only dependency is advisory → no diffs");
	}

	@Test
	void generalizationOnlyInUml_relaxed_isWarning() {
		IntermediateModel code = codeModel();
		ClassInfo base = addClass(code, "Base2", ClassType.CLASS);
		ClassInfo sub = addClass(code, "Sub2", ClassType.CLASS);
		// no inheritance in code

		IntermediateModel uml = umlModel();
		ClassInfo uBase = addClass(uml, "Base2", ClassType.CLASS);
		ClassInfo uSub = addClass(uml, "Sub2", ClassType.CLASS);
		addRel(uml, uSub, uBase, RelationshipType.GENERALIZATION);

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISSING_IN_CODE, d.getKind());
		assertEquals(IssueLevel.WARNING, d.getLevel()); // relaxed → warn
		assertEquals("Sub2 -> Base2", d.getWhere());
		assertEquals("GENERALIZATION", d.getUml());
		assertEquals("missing", d.getCode());
	}

	@Test
	void realizationOnlyInUml_relaxed_isWarning() {
		IntermediateModel code = codeModel();
		ClassInfo impl = addClass(code, "Impl2", ClassType.CLASS);
		ClassInfo api = addClass(code, "API2", ClassType.INTERFACE);
		// no implements in code

		IntermediateModel uml = umlModel();
		ClassInfo uImpl = addClass(uml, "Impl2", ClassType.CLASS);
		ClassInfo uApi = addClass(uml, "API2", ClassType.INTERFACE);
		addRel(uml, uImpl, uApi, RelationshipType.REALIZATION);

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.RELATIONSHIP_MISSING_IN_CODE, d.getKind());
		assertEquals(IssueLevel.WARNING, d.getLevel()); // relaxed → warn
		assertEquals("Impl2 -> API2", d.getWhere());
		assertEquals("REALIZATION", d.getUml());
		assertEquals("missing", d.getCode());
	}

	@Test
	void attributeTypeWrittenDifferent_relaxedPlus_isError() {
		// code & uml models
		IntermediateModel code = new IntermediateModel(ModelSource.SOURCE_CODE);
		IntermediateModel uml = new IntermediateModel(ModelSource.PLANTUML_SCRIPT);

		// classes
		ClassInfo cc = addClass(code, "Invoice", ClassType.CLASS);
		ClassInfo uc = addClass(uml, "Invoice", ClassType.CLASS);

		// attribute in code vs UML (UML writes a different type)
		addAttr(cc, "total", "double", "+");
		addAttr(uc, "total", "String", "+");

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED_PLUS);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.ATTRIBUTE_MISMATCH, d.getKind());
		assertEquals(IssueLevel.ERROR, d.getLevel());
		// be tolerant to exact formatting of 'where'
		assertTrue(d.getWhere().contains("Invoice"));
		assertTrue(d.getWhere().toLowerCase().contains("total"));
		assertEquals("String", d.getUml());
		assertEquals("double", d.getCode());
	}

	@Test
	void methodParamsWritten_relaxedPlus_intVsInteger_isError() {
		IntermediateModel code = new IntermediateModel(ModelSource.SOURCE_CODE);
		IntermediateModel uml = new IntermediateModel(ModelSource.PLANTUML_SCRIPT);

		ClassInfo c = addClass(code, "UserService", ClassType.CLASS);
		ClassInfo u = addClass(uml, "UserService", ClassType.CLASS);

		// code: find(int) -> User
		addMethod(c, "find", "User", "+", "int");
		// UML writes a different signature: find(Integer) -> User
		addMethod(u, "find", "User", "+", "Integer");

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED_PLUS);
		List<Difference> diffs = cmp.compare(code, uml);

		// Expect two findings:
		// 1) UML signature doesn't exist in code (ERROR)
		// 2) code overload not drawn in UML (SUGGESTION)
		assertEquals(2, diffs.size(), "Expected (ERROR for UML->code mismatch) + (SUGGESTION for code-only overload)");

		boolean sawMissingInCodeError = false;
		boolean sawMissingInUmlSuggestion = false;

		for (Difference d : diffs) {
			if (d.getKind() == IssueKind.METHOD_MISSING_IN_CODE) {
				assertEquals(IssueLevel.ERROR, d.getLevel(), "UML-specified signature absent in code should be ERROR");
				assertTrue(d.getWhere().startsWith("UserService#find("),
						"where should reference the find(..) signature");
				sawMissingInCodeError = true;
			} else if (d.getKind() == IssueKind.METHOD_MISSING_IN_UML) {
				assertEquals(IssueLevel.SUGGESTION, d.getLevel(), "code-only method should be SUGGESTION in RELAXED+");
				assertTrue(d.getWhere().startsWith("UserService#find("),
						"where should reference the find(..) signature");
				sawMissingInUmlSuggestion = true;
			}
		}

		assertTrue(sawMissingInCodeError, "Should include METHOD_MISSING_IN_CODE (ERROR)");
		assertTrue(sawMissingInUmlSuggestion, "Should include METHOD_MISSING_IN_UML (SUGGESTION)");
	}

	@Test
	void publicMethodMissingInUml_relaxedPlus_isSuggestion() {
		IntermediateModel code = new IntermediateModel(ModelSource.SOURCE_CODE);
		IntermediateModel uml = new IntermediateModel(ModelSource.PLANTUML_SCRIPT);

		ClassInfo c = addClass(code, "AccountService", ClassType.CLASS);
		ClassInfo u = addClass(uml, "AccountService", ClassType.CLASS);

		// public no-arg method present only in code
		addMethod(c, "export", "String", "+");

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED_PLUS);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.METHOD_MISSING_IN_UML, d.getKind());
		assertEquals(IssueLevel.SUGGESTION, d.getLevel());
		assertTrue(d.getWhere().startsWith("AccountService#export("));
	}
	
	@Test
    void relaxed_ownership_codeComposition_vs_umlAssociation_warning() {
        IntermediateModel code = codeModel();
        ClassInfo order = addClass(code, "Order", ClassType.CLASS);
        ClassInfo line  = addClass(code, "OrderLine", ClassType.CLASS);
        addRel(code, order, line, RelationshipType.COMPOSITION); // code stronger

        IntermediateModel uml = umlModel();
        ClassInfo uOrder = addClass(uml, "Order", ClassType.CLASS);
        ClassInfo uLine  = addClass(uml, "OrderLine", ClassType.CLASS);
        addRel(uml, uOrder, uLine, RelationshipType.ASSOCIATION); // uml weaker

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertEquals(1, diffs.size());
        Difference d = diffs.get(0);
        assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
        assertEquals(IssueLevel.WARNING, d.getLevel(), "RELAXED: ownership strength mismatch should warn");
        assertEquals("Order -> OrderLine", d.getWhere());
        assertEquals("ASSOCIATION", d.getUml());
        assertEquals("COMPOSITION", d.getCode());
    }

    // --- OWNERSHIP strength: UML stronger than code -> WARNING in RELAXED ---
    @Test
    void relaxed_ownership_codeAssociation_vs_umlAggregation_warning() {
        IntermediateModel code = codeModel();
        ClassInfo a = addClass(code, "Shop", ClassType.CLASS);
        ClassInfo b = addClass(code, "Cart", ClassType.CLASS);
        addRel(code, a, b, RelationshipType.ASSOCIATION);

        IntermediateModel uml = umlModel();
        ClassInfo ua = addClass(uml, "Shop", ClassType.CLASS);
        ClassInfo ub = addClass(uml, "Cart", ClassType.CLASS);
        addRel(uml, ua, ub, RelationshipType.AGGREGATION); // UML stronger

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertEquals(1, diffs.size());
        Difference d = diffs.get(0);
        assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
        assertEquals(IssueLevel.WARNING, d.getLevel(), "RELAXED: stronger UML ownership should warn");
        assertEquals("Shop -> Cart", d.getWhere());
        assertEquals("AGGREGATION", d.getUml());
        assertEquals("ASSOCIATION", d.getCode());
    }

    // --- UML has only dependency but code has association (stronger) -> WARNING in RELAXED ---
    @Test
    void relaxed_dependencyInUml_butCodeHasAssociation_warning() {
        IntermediateModel code = codeModel();
        ClassInfo a = addClass(code, "A", ClassType.CLASS);
        ClassInfo b = addClass(code, "B", ClassType.CLASS);
        addRel(code, a, b, RelationshipType.ASSOCIATION); // stronger in code

        IntermediateModel uml = umlModel();
        ClassInfo ua = addClass(uml, "A", ClassType.CLASS);
        ClassInfo ub = addClass(uml, "B", ClassType.CLASS);
        addRel(uml, ua, ub, RelationshipType.DEPENDENCY); // only dependency in UML

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertEquals(1, diffs.size());
        Difference d = diffs.get(0);
        assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
        assertEquals(IssueLevel.WARNING, d.getLevel());
        assertEquals("A -> B", d.getWhere());
        assertEquals("DEPENDENCY", d.getUml());
        assertEquals("ASSOCIATION", d.getCode());
    }

    // --- Inheritance present only in UML -> WARNING in RELAXED ---
    @Test
    void relaxed_generalization_onlyInUml_warning() {
        IntermediateModel code = codeModel();
        ClassInfo base = addClass(code, "Base2", ClassType.CLASS);
        ClassInfo sub  = addClass(code, "Sub2",  ClassType.CLASS);
        // no inheritance in code

        IntermediateModel uml = umlModel();
        ClassInfo uBase = addClass(uml, "Base2", ClassType.CLASS);
        ClassInfo uSub  = addClass(uml, "Sub2",  ClassType.CLASS);
        addRel(uml, uSub, uBase, RelationshipType.GENERALIZATION);

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertEquals(1, diffs.size());
        Difference d = diffs.get(0);
        assertEquals(IssueKind.RELATIONSHIP_MISSING_IN_CODE, d.getKind());
        assertEquals(IssueLevel.WARNING, d.getLevel());
        assertEquals("Sub2 -> Base2", d.getWhere());
        assertEquals("GENERALIZATION", d.getUml());
        assertEquals("missing", d.getCode());
    }

    // --- Realization present only in UML -> WARNING in RELAXED ---
    @Test
    void relaxed_realization_onlyInUml_warning() {
        IntermediateModel code = codeModel();
        ClassInfo impl = addClass(code, "Impl2", ClassType.CLASS);
        ClassInfo api  = addClass(code, "API2",  ClassType.INTERFACE);
        // no implements in code

        IntermediateModel uml = umlModel();
        ClassInfo uImpl = addClass(uml, "Impl2", ClassType.CLASS);
        ClassInfo uApi  = addClass(uml, "API2",  ClassType.INTERFACE);
        addRel(uml, uImpl, uApi, RelationshipType.REALIZATION);

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertEquals(1, diffs.size());
        Difference d = diffs.get(0);
        assertEquals(IssueKind.RELATIONSHIP_MISSING_IN_CODE, d.getKind());
        assertEquals(IssueLevel.WARNING, d.getLevel());
        assertEquals("Impl2 -> API2", d.getWhere());
        assertEquals("REALIZATION", d.getUml());
        assertEquals("missing", d.getCode());
    }

    // --- Code has REALIZATION but UML has GENERALIZATION (wrong kind) -> two WARNINGs in RELAXED ---
    @Test
    void relaxed_realization_vs_generalization_twoWarnings() {
        IntermediateModel code = codeModel();
        ClassInfo impl = addClass(code, "Impl", ClassType.CLASS);
        ClassInfo api  = addClass(code, "API",  ClassType.INTERFACE);
        addRel(code, impl, api, RelationshipType.REALIZATION); // class implements interface

        IntermediateModel uml = umlModel();
        ClassInfo uImpl = addClass(uml, "Impl", ClassType.CLASS);
        ClassInfo uApi  = addClass(uml, "API",  ClassType.INTERFACE);
        addRel(uml, uImpl, uApi, RelationshipType.GENERALIZATION); // wrong in UML

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertEquals(2, diffs.size(), "Expect missing REALIZATION + extra GENERALIZATION (both warnings)");
        assertTrue(
            diffs.stream().anyMatch(d ->
                d.getKind() == IssueKind.RELATIONSHIP_MISSING_IN_UML &&
                "Impl -> API".equals(d.getWhere()) &&
                "REALIZATION".equals(d.getCode()) &&
                d.getLevel() == IssueLevel.WARNING),
            "Missing REALIZATION in UML should be WARNING");
        assertTrue(
            diffs.stream().anyMatch(d ->
                d.getKind() == IssueKind.RELATIONSHIP_MISSING_IN_CODE &&
                "Impl -> API".equals(d.getWhere()) &&
                "GENERALIZATION".equals(d.getUml()) &&
                d.getLevel() == IssueLevel.WARNING),
            "GENERALIZATION present only in UML should be WARNING");
    }

    // --- Extra weaker edge in UML (in addition to a matching stronger edge) -> no diffs ---
    @Test
    void relaxed_extraWeakerEdgeInUml_noDiff() {
        IntermediateModel code = codeModel();
        ClassInfo a = addClass(code, "A", ClassType.CLASS);
        ClassInfo b = addClass(code, "B", ClassType.CLASS);
        addRel(code, a, b, RelationshipType.AGGREGATION);

        IntermediateModel uml = umlModel();
        ClassInfo ua = addClass(uml, "A", ClassType.CLASS);
        ClassInfo ub = addClass(uml, "B", ClassType.CLASS);
        // same stronger edge present
        addRel(uml, ua, ub, RelationshipType.AGGREGATION);
        // plus an extra weaker association
        addRel(uml, ua, ub, RelationshipType.ASSOCIATION);

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertTrue(diffs.isEmpty(), "Extra weaker edge in UML shouldn’t cause differences");
    }

    // --- When multiple strengths exist on a side, the strongest should be compared ---
    @Test
    void relaxed_strongestSelected_forComparison_warning() {
        IntermediateModel code = codeModel();
        ClassInfo a = addClass(code, "A1", ClassType.CLASS);
        ClassInfo b = addClass(code, "B1", ClassType.CLASS);
        // both ASSOCIATION and AGGREGATION → strongest is AGGREGATION
        addRel(code, a, b, RelationshipType.ASSOCIATION);
        addRel(code, a, b, RelationshipType.AGGREGATION);

        IntermediateModel uml = umlModel();
        ClassInfo ua = addClass(uml, "A1", ClassType.CLASS);
        ClassInfo ub = addClass(uml, "B1", ClassType.CLASS);
        addRel(uml, ua, ub, RelationshipType.ASSOCIATION); // weaker in UML

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertEquals(1, diffs.size());
        Difference d = diffs.get(0);
        assertEquals(IssueKind.RELATIONSHIP_MISMATCH, d.getKind());
        assertEquals(IssueLevel.WARNING, d.getLevel(), "RELAXED: ownership strength mismatch should warn");
        assertEquals("A1 -> B1", d.getWhere());
        assertEquals("ASSOCIATION", d.getUml());
        assertEquals("AGGREGATION", d.getCode());
    }

    // --- Code-only dependency is advisory -> no differences (even in RELAXED) ---
    @Test
    void relaxed_codeOnlyDependency_isIgnored() {
        IntermediateModel code = codeModel();
        ClassInfo a = addClass(code, "Core", ClassType.CLASS);
        ClassInfo b = addClass(code, "Util", ClassType.CLASS);
        addRel(code, a, b, RelationshipType.DEPENDENCY); // advisory

        IntermediateModel uml = umlModel();
        addClass(uml, "Core", ClassType.CLASS);
        addClass(uml, "Util", ClassType.CLASS);
        // no edge in UML

        List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(code, uml);

        assertTrue(diffs.isEmpty(), "Code-only dependency is advisory → no diffs in RELAXED");
    }

	
	

}
