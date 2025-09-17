# Scenarios – Expected Results

## 01_all_match
- STRICT: PASS
- RELAXED: PASS
- MINIMAL: PASS

## 02_relationship_strength_mismatch
- STRICT: ERROR (fail)
- RELAXED: WARNING (pass)
- MINIMAL: WARNING (pass)
  - Notes: Code composition vs UML association; UML includes - book : Book. (Constructors are ignored.)

## 03_uml_extra_relationship
- STRICT: ERROR (fail)
- RELAXED: WARNING (pass) (Will be false after fixing #TODO5)
- MINIMAL: WARNING (pass) (Will be false after fixing #TODO5)

## 04_code_members_omitted_in_uml
- STRICT: ERROR (fail)
- RELAXED: ERROR (fail)
- MINIMAL: PASS (suggestions/info)

## 05_member_type_mismatch
- STRICT: ERROR (fail)
- RELAXED: ERROR (fail)
- MINIMAL: ERROR (fail)

## 06_method_return_omitted
- STRICT: ERROR (fail)
- RELAXED: ERROR (fail)
- MINIMAL: PASS (suggestion)

## 07_missing_class_in_uml
- STRICT: ERROR (fail)
- RELAXED: ERROR (fail)
- MINIMAL: ERROR (fail)

## 08_uml_only_class
- STRICT: ERROR (fail)
- RELAXED: ERROR (fail)
- MINIMAL: ERROR (fail)

## 09_visibility_differences
- STRICT: ERROR (fail)
- RELAXED: ERROR (fail)
- MINIMAL: PASS (warning/suggestion)

## 10_duplicate_weaker_edges
- STRICT: PASS
- RELAXED: PASS
- MINIMAL: PASS

## 11_wrong_arrow_realization_vs_generalization
- STRICT: ERROR (fail)
- RELAXED: WARNING (pass)
- MINIMAL: WARNING (pass)

## 12_dependency_match
- STRICT: PASS
- RELAXED: PASS
- MINIMAL: PASS
