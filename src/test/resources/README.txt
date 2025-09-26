# Scenarios (v5) – Expected Results & Reasons

**Modes:** STRICT, RELAXED, MINIMAL

---
## 01_all_match
- STRICT: PASS
- RELAXED: PASS
- MINIMAL: PASS
Notes: Aggregation via setter (`Customer o-- Order`) + Realization (`CardGateway` implements `PaymentGateway`).

---
## 02_relationship_strength_mismatch
- STRICT: **FAIL** — Relationship strength mismatch.
  - Code: **COMPOSITION** (field assigned `new Book()`).
  - UML: **ASSOCIATION** (`Library --> Book`).
- RELAXED: PASS — **WARNING**.
- MINIMAL: PASS — **WARNING**.

---
## 03_uml_extra_relationship
- STRICT: **FAIL** — UML-only relation.
  - UML adds `Manager --> Logger` not present in code.
- RELAXED: PASS — **WARNING**.
- MINIMAL: PASS — **WARNING**.

---
## 04_code_members_omitted_in_uml
- STRICT: **FAIL** — Members missing in UML.
- RELAXED: **FAIL** — Members behave like STRICT.
- MINIMAL: PASS — omissions become **SUGGESTION/INFO** (public/protected ⇒ suggestion, package/private ⇒ info).

---
## 05_member_type_mismatch
- STRICT: **FAIL** — Return type mismatch (`String` in UML vs `int` in code).
- RELAXED: **FAIL**
- MINIMAL: **FAIL**

---
## 06_method_return_omitted
- STRICT: **FAIL** — Return type omitted in UML.
- RELAXED: **FAIL**
- MINIMAL: PASS — **SUGGESTION** to add return type.

---
## 07_missing_class_in_uml
- STRICT: **FAIL** — Code-only class (`Server`) not found in UML.
- RELAXED: **FAIL**
- MINIMAL: **FAIL**

---
## 08_uml_only_class
- STRICT: **FAIL** — UML-only class (`Phantom`).
- RELAXED: **FAIL**
- MINIMAL: **FAIL**

---
## 09_visibility_differences
- STRICT: **FAIL** — UML more public than code (package-private in code).
- RELAXED: **FAIL**
- MINIMAL: PASS — **WARNING/SUGGESTION** (directional rule).

---
## 10_duplicate_weaker_edges
- STRICT: PASS
- RELAXED: PASS
- MINIMAL: PASS
Notes: UML lists both `*--` and `-->`; comparator keeps **one strongest per family**; extra weaker edges ignored.

---
## 11_wrong_arrow_realization_vs_generalization
- STRICT: **FAIL** — Wrong relationship kind (UML uses **Generalization** but code is **Realization**).
- RELAXED: PASS — **WARNING**.
- MINIMAL: PASS — **WARNING**.

---
## 12_dependency_vs_association_policy (UPDATED)
- STRICT: PASS — **WARNING** because UML uses **Dependency** (`..>`) while code usage (param/return/local) normalizes to **ASSOCIATION** by policy.
- RELAXED: PASS — **SUGGESTION** to switch UML to association.
- MINIMAL: PASS — **SUGGESTION** likewise.

---
## 13_static_field_is_association
- STRICT: **FAIL** — UML shows **COMPOSITION** but rule says static field ⇒ **ASSOCIATION** only.
- RELAXED: PASS — **WARNING** (same family, stronger in UML).
- MINIMAL: PASS — **WARNING**.

---
## 14_code_only_relationship
- STRICT: **FAIL** — Code-only relation (association in code; UML omits edge).
- RELAXED: PASS — **WARNING** (missing UML edge).
- MINIMAL: PASS — **WARNING**.

---

### Parser formatting constraints satisfied
- Java methods are multi-line (no single-line bodies).
- No multiple declarations on one line.
- PlantUML uses supported syntax: attributes `name : Type`, methods `name(params) : ReturnType`, static with `__...__`.
- Packages ignored by comparison; Java files still placed under `package scenarioXX;` for isolation.
