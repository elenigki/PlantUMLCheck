package comparison.checks;

import comparison.CheckMode;
import comparison.issues.Difference;
import comparison.issues.IssueKind;
import comparison.issues.IssueLevel;
import model.IntermediateModel;
import model.Relationship;
import model.RelationshipType;

import java.util.*;


public final class RelationshipCheck {

    // Compares relationships between code and UML.
    public static List<Difference> compareRelationships(IntermediateModel code,
                                                        IntermediateModel uml,
                                                        CheckMode mode) {
        List<Difference> out = new ArrayList<>();

        Map<String, Map<String, List<Relationship>>> codeBy = byPair(code.getRelationships());
        Map<String, Map<String, List<Relationship>>> umlBy  = byPair(uml.getRelationships());

        // walk all A->B pairs seen on either side
        Set<String> allA = new LinkedHashSet<>();
        allA.addAll(codeBy.keySet());
        allA.addAll(umlBy.keySet());

        for (String a : allA) {
            Set<String> allB = new LinkedHashSet<>();
            if (codeBy.containsKey(a)) allB.addAll(codeBy.get(a).keySet());
            if (umlBy.containsKey(a))  allB.addAll(umlBy.get(a).keySet());

            for (String b : allB) {
                List<Relationship> cEdges = codeBy.getOrDefault(a, Map.of()).getOrDefault(b, List.of());
                List<Relationship> uEdges = umlBy.getOrDefault(a, Map.of()).getOrDefault(b, List.of());

                compareInheritanceBetween(a, b, cEdges, uEdges, mode, out);
                compareOwnershipBetween(a, b, cEdges, uEdges, mode, out);
                compareDependencyBetween(a, b, cEdges, uEdges, mode, out);
            }
        }

        return out;
    }

    // Groups edges as A -> (B -> list of edges).
    static Map<String, Map<String, List<Relationship>>> byPair(List<Relationship> rels) {
        Map<String, Map<String, List<Relationship>>> m = new LinkedHashMap<>();
        if (rels == null) return m;
        for (Relationship r : rels) {
            if (r == null || r.getSourceClass() == null || r.getTargetClass() == null) continue;
            String a = ns(r.getSourceClass().getName());
            String b = ns(r.getTargetClass().getName());
            if (a.isEmpty() || b.isEmpty()) continue;
            m.computeIfAbsent(a, x -> new LinkedHashMap<>())
             .computeIfAbsent(b, x -> new ArrayList<>())
             .add(r);
        }
        return m;
    }

    // Compares inheritance/contract (extends/implements) for one A->B pair.
    static void compareInheritanceBetween(String a, String b,
                                          List<Relationship> codeEdges,
                                          List<Relationship> umlEdges,
                                          CheckMode mode,
                                          List<Difference> out) {
        boolean codeGen  = has(codeEdges, RelationshipType.GENERALIZATION);
        boolean umlGen   = has(umlEdges, RelationshipType.GENERALIZATION);
        boolean codeReal = has(codeEdges, RelationshipType.REALIZATION);
        boolean umlReal  = has(umlEdges, RelationshipType.REALIZATION);

        // GENERALIZATION (extends)
        if (codeGen && !umlGen) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISSING_IN_UML,
                (mode == CheckMode.STRICT) ? IssueLevel.ERROR : IssueLevel.WARNING,
                a + " -> " + b,
                "Inheritance: expected GENERALIZATION; UML is missing it",
                "missing", "GENERALIZATION",
                "Add extends edge to UML"
            ));
        } else if (!codeGen && umlGen) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISSING_IN_CODE,
                IssueLevel.ERROR, // UML-only in family => ERROR in all modes
                a + " -> " + b,
                "Inheritance: UML shows GENERALIZATION; code has none",
                "GENERALIZATION", "missing",
                "Remove or fix extends in UML"
            ));
        }

        // REALIZATION (implements)
        if (codeReal && !umlReal) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISSING_IN_UML,
                (mode == CheckMode.STRICT) ? IssueLevel.ERROR : IssueLevel.WARNING,
                a + " -> " + b,
                "Inheritance: expected REALIZATION; UML is missing it",
                "missing", "REALIZATION",
                "Add implements edge to UML"
            ));
        } else if (!codeReal && umlReal) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISSING_IN_CODE,
                IssueLevel.ERROR, // UML-only in family => ERROR in all modes
                a + " -> " + b,
                "Inheritance: UML shows REALIZATION; code has none",
                "REALIZATION", "missing",
                "Remove or fix implements in UML"
            ));
        }
    }

    // Compares ownership (composition > aggregation > association) for one A->B pair.
    static void compareOwnershipBetween(String a, String b,
                                        List<Relationship> codeEdges,
                                        List<Relationship> umlEdges,
                                        CheckMode mode,
                                        List<Difference> out) {
        RelationshipType codeOwn = strongestOwnership(codeEdges);
        RelationshipType umlOwn  = strongestOwnership(umlEdges);

        String where = a + " -> " + b;

        // nothing on both sides → done
        if (codeOwn == null && umlOwn == null) return;

        // only UML has ownership
        if (codeOwn == null && umlOwn != null) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISMATCH,
                IssueLevel.ERROR, // UML-only in family => ERROR in all modes
                where,
                "Ownership: UML shows " + umlOwn.name() + " but code has none",
                umlOwn.name(), "missing",
                "Remove from UML or reflect it in code"
            ));
            return;
        }

        // only code has ownership
        if (codeOwn != null && umlOwn == null) {
            // If UML also has a DEPENDENCY, let the dependency check handle it (avoid duplicate)
            boolean umlHasDependency = has(umlEdges, RelationshipType.DEPENDENCY);
            if (umlHasDependency) return;

            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISSING_IN_UML,
                (mode == CheckMode.STRICT) ? IssueLevel.ERROR : IssueLevel.WARNING,
                where,
                "Ownership: expected " + codeOwn.name() + "; UML is missing it",
                "missing", codeOwn.name(),
                "Add ownership to UML"
            ));
            return;
        }

        // both have: compare strength
        int c = strength(codeOwn);
        int u = strength(umlOwn);
        if (c > u) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISMATCH,
                (mode == CheckMode.STRICT) ? IssueLevel.ERROR : IssueLevel.WARNING,
                where,
                "Ownership: expected " + codeOwn.name() + "; UML has " + umlOwn.name(),
                umlOwn.name(), codeOwn.name(),
                "Upgrade UML ownership to match code"
            ));
        } else if (c < u) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISMATCH,
                (mode == CheckMode.STRICT) ? IssueLevel.ERROR : IssueLevel.WARNING,
                where,
                "Ownership: expected " + codeOwn.name() + "; UML has " + umlOwn.name(),
                umlOwn.name(), codeOwn.name(),
                "Downgrade UML ownership to match code"
            ));
        }
        // equal → OK
    }

    // Handles dependency policy.
    static void compareDependencyBetween(String a, String b,
                                         List<Relationship> codeEdges,
                                         List<Relationship> umlEdges,
                                         CheckMode mode,
                                         List<Difference> out) {
        boolean codeDep = has(codeEdges, RelationshipType.DEPENDENCY);
        boolean umlDep  = has(umlEdges, RelationshipType.DEPENDENCY);

        RelationshipType codeOwn = strongestOwnership(codeEdges);
        RelationshipType umlOwn  = strongestOwnership(umlEdges);

        String where = a + " -> " + b;

        // UML has only dependency but code has stronger → underclaimed
        if (umlDep && codeOwn != null) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISMATCH,
                (mode == CheckMode.STRICT) ? IssueLevel.ERROR : IssueLevel.SUGGESTION,
                where,
                "Dependency: expected " + codeOwn.name() + "; UML has DEPENDENCY",
                "DEPENDENCY", codeOwn.name(),
                "Upgrade UML to " + codeOwn.name()
            ));
            return;
        }

        // UML-only dependency (no stronger in code and code has no dependency) → ERROR in all modes
        if (umlDep && codeOwn == null && !codeDep) {
            out.add(new Difference(
                IssueKind.RELATIONSHIP_MISMATCH,
                IssueLevel.ERROR,
                where,
                "Dependency: UML shows DEPENDENCY; code has none",
                "DEPENDENCY", "missing",
                "Remove or reflect a stronger intended relation in code"
            ));
            return;
        }

        // Code-only dependency (UML omitted) → OK (ignored) in all modes
        // Intentionally no difference emitted
    }

    // --- small helpers ---

    private static boolean has(List<Relationship> edges, RelationshipType t) {
        if (edges == null) return false;
        for (Relationship r : edges) if (r != null && r.getType() == t) return true;
        return false;
    }

    private static RelationshipType strongestOwnership(List<Relationship> edges) {
        RelationshipType best = null;
        int bestRank = 0;
        if (edges == null) return null;
        for (Relationship r : edges) {
            if (r == null) continue;
            int rank = strength(r.getType());
            if (rank > bestRank) { best = r.getType(); bestRank = rank; }
        }
        return best;
    }

    private static int strength(RelationshipType t) {
        if (t == null) return 0;
        switch (t) {
            case COMPOSITION: return 3;
            case AGGREGATION: return 2;
            case ASSOCIATION: return 1;
            default: return 0; // GENERALIZATION/REALIZATION/DEPENDENCY not in ownership ladder
        }
    }

    private static String ns(String s) { return (s == null || s.isBlank()) ? "" : s.trim(); }
}
