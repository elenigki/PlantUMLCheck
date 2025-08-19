package comparison.export;

import comparison.CheckMode;
import comparison.issues.Difference;
import comparison.issues.IssueLevel;
import model.*;

import java.util.*;

/** Builds a PlantUML script from an IntermediateModel. */
public final class PlantUmlWriter {
    private PlantUmlWriter() {}

    /** Creates the PlantUML text. */
    public static String generate(CheckMode mode,
                                  IntermediateModel codeModel,
                                  IntermediateModel umlModel,      // optional (for relaxed notes)
                                  List<Difference> diffs) {        // differences (for comments)
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");

        // In relaxed mode we add warnings/suggestions as comments
        if (mode == CheckMode.RELAXED && diffs != null && !diffs.isEmpty()) {
            sb.append("' Notes (RELAXED): suggestions & warnings kept as comments\n");
            for (Difference d : diffs) {
                if (d.getLevel() == IssueLevel.WARNING || d.getLevel() == IssueLevel.SUGGESTION) {
                    sb.append("' ").append(d.getLevel()).append(" — ")
                      .append(d.getWhere()).append(" — ").append(d.getSummary()).append("\n");
                }
            }
            sb.append("\n");
        }

        // Classes and their members
        for (ClassInfo c : codeModel.getClasses()) {
            if (c == null) continue;
            sb.append(renderClassHeader(c)).append(" {\n");
            for (Attribute a : c.getAttributes()) {
                if (a == null) continue;
                sb.append("  ").append(vis(a.getVisibility()))
                  .append(a.getName()).append(" : ").append(nv(a.getType())).append("\n");
            }
            for (Method m : c.getMethods()) {
                if (m == null) continue;
                sb.append("  ").append(vis(m.getVisibility()))
                  .append(m.getName()).append("(").append(params(m.getParameters())).append(")");
                String rt = nv(m.getReturnType());
                if (!rt.isEmpty() && !"void".equals(rt)) sb.append(" : ").append(rt);
                sb.append("\n");
            }
            sb.append("}\n");
        }
        sb.append("\n");

        // Ownership edges (keep the strongest only)
        Map<String, Map<String, RelationshipType>> strong = strongestOwnershipPerPair(codeModel.getRelationships());
        for (var eFrom : strong.entrySet()) {
            String from = eFrom.getKey();
            for (var eTo : eFrom.getValue().entrySet()) {
                String to = eTo.getKey();
                RelationshipType t = eTo.getValue();
                sb.append(renderOwnership(from, to, t)).append("\n");
            }
        }

        // Inheritance & realization (render all)
        for (Relationship r : codeModel.getRelationships()) {
            if (r == null) continue;
            String a = safeName(r.getSourceClass()), b = safeName(r.getTargetClass());
            if (a.isEmpty() || b.isEmpty()) continue;
            if (r.getType() == RelationshipType.GENERALIZATION) {
                sb.append(a).append(" --|> ").append(b).append("\n");
            } else if (r.getType() == RelationshipType.REALIZATION) {
                sb.append(a).append(" ..|> ").append(b).append("\n");
            }
        }

        // In relaxed mode, keep UML dependencies as comments (to preserve intent)
        if (mode == CheckMode.RELAXED && umlModel != null && umlModel.getRelationships() != null) {
            for (Relationship r : umlModel.getRelationships()) {
                if (r == null) continue;
                if (r.getType() == RelationshipType.DEPENDENCY) {
                    String a = safeName(r.getSourceClass()), b = safeName(r.getTargetClass());
                    if (!a.isEmpty() && !b.isEmpty()) {
                        sb.append("' UML had DEPENDENCY: ").append(a).append(" ..> ").append(b).append("\n");
                    }
                }
            }
        }

        sb.append("@enduml\n");
        return sb.toString();
    }

    // Renders the class header line
    private static String renderClassHeader(ClassInfo c) {
        String name = esc(c.getName());
        switch (c.getClassType()) {
            case INTERFACE: return "interface " + name;
            case ENUM:      return "enum " + name;
            default:        return c.isAbstract() ? "abstract class " + name : "class " + name;
        }
    }

    // Picks the strongest ownership per (A,B)
    private static Map<String, Map<String, RelationshipType>> strongestOwnershipPerPair(List<Relationship> rels) {
        Map<String, Map<String, RelationshipType>> map = new LinkedHashMap<>();
        if (rels == null) return map;
        for (Relationship r : rels) {
            if (r == null) continue;
            RelationshipType t = r.getType();
            if (t != RelationshipType.ASSOCIATION &&
                t != RelationshipType.AGGREGATION &&
                t != RelationshipType.COMPOSITION) continue;
            String a = safeName(r.getSourceClass()), b = safeName(r.getTargetClass());
            if (a.isEmpty() || b.isEmpty()) continue;
            RelationshipType cur = map.computeIfAbsent(a, k -> new LinkedHashMap<>()).get(b);
            if (cur == null || strength(t) > strength(cur)) {
                map.get(a).put(b, t);
            }
        }
        return map;
    }

    // Renders ownership edge
    private static String renderOwnership(String from, String to, RelationshipType t) {
        switch (t) {
            case COMPOSITION: return from + " *-- " + to; // composition
            case AGGREGATION: return from + " o-- " + to; // aggregation
            default:          return from + " -- "  + to; // association
        }
    }

    // Visibility symbol (keeps + - # ~ as-is)
    private static String vis(String v) {
        return (v == null || v.isBlank()) ? "~" : v.trim();
    }

    // Joins parameter types
    private static String params(List<String> ps) {
        if (ps == null || ps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ps.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(nv(ps.get(i)));
        }
        return sb.toString();
    }

    // Safe class name from ClassInfo
    private static String safeName(ClassInfo c) {
        return (c == null || c.getName() == null) ? "" : esc(c.getName());
    }

    // Basic escape for quotes
    private static String esc(String s) {
        return s.replace("\"", "\\\"");
    }

    // Ownership strength
    private static int strength(RelationshipType t) {
        switch (t) {
            case COMPOSITION: return 3;
            case AGGREGATION: return 2;
            case ASSOCIATION: return 1;
            default: return 0;
        }
    }

    // Null→empty helper
    private static String nv(String s) { return (s == null) ? "" : s; }
}


// How to use it
//List<Difference> diffs = new ModelComparator(CheckMode.RELAXED).compare(codeModel, umlModel);
//String puml = PlantUmlWriter.generate(CheckMode.RELAXED, codeModel, umlModel, diffs);
// write puml to file or render in a viewer