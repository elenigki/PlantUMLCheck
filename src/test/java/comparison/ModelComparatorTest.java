package comparison;

import comparison.issues.Difference;
import comparison.issues.IssueKind;
import comparison.issues.IssueLevel;
import model.ClassInfo;
import model.ClassType;
import model.IntermediateModel;
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

	@Test
	void methodMissingInUml_relaxed_isInfo() {
		IntermediateModel code = codeModel();
		ClassInfo cc = addClass(code, "Auth", ClassType.CLASS);
		addMethod(cc, "login", "boolean", "+", "String", "String");

		IntermediateModel uml = umlModel();
		addClass(uml, "Auth", ClassType.CLASS);

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.METHOD_MISSING_IN_UML, d.getKind());
		assertEquals(IssueLevel.INFO, d.getLevel()); // relaxed policy
		assertEquals("Auth#login(String,String)", d.getWhere());
		assertEquals("missing", d.getUml());
		assertEquals("present", d.getCode());
	}

	// --- VISIBILITY (RELAXED) ---

	@Test
	void attributeVisibility_moreVisibleInCode_relaxed_isInfo() {
		IntermediateModel code = codeModel();
		ClassInfo cc = addClass(code, "Cfg", ClassType.CLASS);
		addAttr(cc, "path", "String", "+"); // code: public

		IntermediateModel uml = umlModel();
		ClassInfo uc = addClass(uml, "Cfg", ClassType.CLASS);
		addAttr(uc, "path", "String", "~"); // uml: package

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.ATTRIBUTE_MISMATCH, d.getKind());
		assertEquals(IssueLevel.INFO, d.getLevel()); // more visible in code → info
		assertEquals("Cfg.attr:path", d.getWhere());
		assertEquals("~", d.getUml());
		assertEquals("+", d.getCode());
	}

	@Test
	void methodVisibility_lessVisibleInCode_relaxed_isWarning() {
		IntermediateModel code = codeModel();
		ClassInfo cc = addClass(code, "Repo", ClassType.CLASS);
		addMethod(cc, "save", "void", "-", "Object"); // code: private

		IntermediateModel uml = umlModel();
		ClassInfo uc = addClass(uml, "Repo", ClassType.CLASS);
		addMethod(uc, "save", "void", "+", "Object"); // uml: public

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertEquals(1, diffs.size());
		Difference d = diffs.get(0);
		assertEquals(IssueKind.METHOD_MISMATCH, d.getKind());
		assertEquals(IssueLevel.WARNING, d.getLevel()); // less visible in code → warning
		assertEquals("Repo#save(Object)", d.getWhere());
		assertEquals("+", d.getUml());
		assertEquals("-", d.getCode());
	}

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
	void relaxed_params_int_vs_Integer_matches_noDiff() {
		IntermediateModel code = codeModel();
		ClassInfo c = addClass(code, "Svc", ClassType.CLASS);
		addMethod(c, "find", "User", "+", "int"); // code: int

		IntermediateModel uml = umlModel();
		ClassInfo u = addClass(uml, "Svc", ClassType.CLASS);
		addMethod(u, "find", "User", "+", "Integer"); // uml: Integer

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertTrue(diffs.isEmpty(), "RELAXED: int ≈ Integer should match");
	}

	@Test
	void relaxed_params_varargs_vs_array_matches_noDiff() {
		IntermediateModel code = codeModel();
		ClassInfo c = addClass(code, "Maths", ClassType.CLASS);
		addMethod(c, "sum", "int", "+", "int", "int..."); // code: int, int...

		IntermediateModel uml = umlModel();
		ClassInfo u = addClass(uml, "Maths", ClassType.CLASS);
		addMethod(u, "sum", "int", "+", "int", "int[]"); // uml: int, int[]

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertTrue(diffs.isEmpty(), "RELAXED: varargs ≈ array should match");
	}

	@Test
	void relaxed_params_genericErasure_and_fqcn_simple_matches_noDiff() {
		IntermediateModel code = codeModel();
		ClassInfo c = addClass(code, "Proc", ClassType.CLASS);
		addMethod(c, "process", "void", "+", "java.util.List<String>");

		IntermediateModel uml = umlModel();
		ClassInfo u = addClass(uml, "Proc", ClassType.CLASS);
		addMethod(u, "process", "void", "+", "List<T>");

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertTrue(diffs.isEmpty(), "RELAXED: List<String> ≈ List<T> by erasure + simple names");
	}

	@Test
	void overloadedMethods_relaxed_paramEquivalence_picksCorrectOverload() {
		IntermediateModel code = codeModel();
		ClassInfo svc = addClass(code, "Svc", ClassType.CLASS);
		addMethod(svc, "find", "User", "+", "int"); // #1
		addMethod(svc, "find", "User", "+", "String"); // #2

		IntermediateModel uml = umlModel();
		ClassInfo uSvc = addClass(uml, "Svc", ClassType.CLASS);
		addMethod(uSvc, "find", "User", "+", "Integer"); // should match #1 via relaxed rules

		ModelComparator cmp = new ModelComparator(CheckMode.RELAXED);
		List<Difference> diffs = cmp.compare(code, uml);

		assertTrue(diffs.isEmpty(), "RELAXED: Integer should match int overload");
	}

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
	void attributeGeneric_strict_mismatch_vs_relaxed_ok() {
		// code: java.util.List<Order>
		IntermediateModel code = codeModel();
		ClassInfo c = addClass(code, "Repo", ClassType.CLASS);
		addAttr(c, "orders", "java.util.List<model.Order>", "+");

		// uml: List<T>
		IntermediateModel uml = umlModel();
		ClassInfo u = addClass(uml, "Repo", ClassType.CLASS);
		addAttr(u, "orders", "List<T>", "+");

		// STRICT → mismatch
		ModelComparator cmpStrict = new ModelComparator(CheckMode.STRICT);
		List<Difference> dStrict = cmpStrict.compare(code, uml);
		assertEquals(1, dStrict.size());
		assertEquals(IssueKind.ATTRIBUTE_MISMATCH, dStrict.get(0).getKind());

		// RELAXED → OK (erasure + simple names)
		ModelComparator cmpRelax = new ModelComparator(CheckMode.RELAXED);
		List<Difference> dRelax = cmpRelax.compare(code, uml);
		assertTrue(dRelax.isEmpty(), "RELAXED: List<...> ≈ List<T>");
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

}
