package generator;

import java.util.*;
import java.util.stream.Collectors;

import model.Attribute;
import model.ClassDeclaration;
import model.ClassInfo;
import model.ClassType;
import model.IntermediateModel;
import model.Method;
import model.ModelSource;
import model.Relationship;
import model.RelationshipType;

/**
 * PlantUMLGenerator
 *
 * Generates a PlantUML class diagram script from an IntermediateModel built
 * from source code. The output is deterministic and ready to paste into the
 * PlantUML server (no syntax errors).
 */
public final class PlantUMLGenerator {

	/** Options to tweak output formatting. */
	public static final class Options {
		/** If true, omit private members. */
		public boolean excludePrivate = false;
		/** If true, omit package-private members. */
		public boolean excludePackagePrivate = false;
		/** If true, include only OFFICIAL classes (skip DUMMY). Default true. */
		public boolean onlyOfficialClasses = true;
		/**
		 * If true, include relationships only when both endpoints are included classes.
		 */
		public boolean pruneDanglingRelationships = true;
		/** When true, add conservative skinparams for better readability. */
		public boolean addSkinParams = true;
		/** When true, add header comment banner. */
		public boolean addHeaderComment = true;
	}

	private final Options options;

	public PlantUMLGenerator() {
		this(new Options());
	}

	public PlantUMLGenerator(Options options) {
		this.options = options == null ? new Options() : options;
	}

	/** Main entry point. */
	public String generate(IntermediateModel originalModel) {
	    if (originalModel == null) {
	        throw new IllegalArgumentException("model is null");
	    }

	    IntermediateModel model = withNormalizedRelationships(originalModel);

	    // DEBUG: counts
	    Map<RelationshipType, Long> counts = new EnumMap<>(RelationshipType.class);
	    for (Relationship r : model.getRelationships()) {
	        counts.merge(r.getType(), 1L, Long::sum);
	    }
	    System.out.println("[REL][gen] inputCounts=" + counts);

	    StringBuilder sb = new StringBuilder(8_192);
	    sb.append("@startuml").append('\n');

	    // Select classes to include.
	    List<ClassInfo> all = safeList(modelGetClasses(model));
	    List<ClassInfo> included = all.stream()
	            .filter(ci -> !options.onlyOfficialClasses || ci.getDeclaration() == ClassDeclaration.OFFICIAL)
	            .sorted(Comparator.comparing(PlantUMLGenerator::safeClassName, String.CASE_INSENSITIVE_ORDER))
	            .collect(Collectors.toList());

	    // Emit class/interface/enum blocks
	    for (ClassInfo ci : included) {
	        emitTypeBlock(sb, ci);
	    }

	    // Emit relationships
	    List<Relationship> rels = safeList(modelGetRelationships(model));
	    if (!rels.isEmpty()) {
	        // Create a set of included names for pruning
	        Set<String> includedNames = included.stream()
	                .map(PlantUMLGenerator::safeClassName)
	                .collect(Collectors.toCollection(LinkedHashSet::new));

	        // Sort deterministically
	        rels.sort(Comparator.comparing((Relationship r) -> safeClassName(safeSource(r)))
	                .thenComparing(r -> safeClassName(safeTarget(r)))
	                .thenComparing(r -> r.getType().name()));

	        for (Relationship r : rels) {
	            ClassInfo src = safeSource(r);
	            ClassInfo dst = safeTarget(r);
	            if (src == null || dst == null) continue;

	            String srcName = safeClassName(src);
	            String dstName = safeClassName(dst);

	            if (options.pruneDanglingRelationships) {
	                if (!includedNames.contains(srcName) || !includedNames.contains(dstName)) {
	                    continue; // skip relationships to filtered classes (e.g., DUMMY)
	                }
	            }

	            RelationshipType rt = r.getType();
	            String arrow;

	            if (isInheritance(rt)) {
	                // Left-pointing for inheritance; parent (target) first.
	                arrow = (rt == RelationshipType.GENERALIZATION) ? "<|--" : "<|..";

	                // DEBUG
	                System.out.println("[REL][emit] " + dstName + " " + arrow + " " + srcName + " (" + rt + ")");

	                sb.append(quoteIfNeeded(dstName)).append(' ')
	                  .append(arrow).append(' ')
	                  .append(quoteIfNeeded(srcName))
	                  .append('\n');
	            } else {
	                arrow = toArrow(rt);
	                if (arrow == null) continue;

	                // DEBUG
	                System.out.println("[REL][emit] " + srcName + " " + arrow + " " + dstName + " (" + rt + ")");

	                sb.append(quoteIfNeeded(srcName)).append(' ')
	                  .append(arrow).append(' ')
	                  .append(quoteIfNeeded(dstName))
	                  .append('\n');
	            }
	        } // ← closes for (Relationship r : rels)
	    }     // ← closes if (!rels.isEmpty())

	    sb.append("@enduml").append('\n');
	    return sb.toString();
	}


	// --- Preprocessing methods ---

	/**
	 * Returns a NEW IntermediateModel with relationships normalized. Classes are
	 * reused as-is; only the relationships list is rebuilt.
	 */
	public IntermediateModel withNormalizedRelationships(IntermediateModel original) {
		if (original == null)
			return null;

		// Collect normalized edges as ArrayList directly
		ArrayList<Relationship> normalized = new ArrayList<>(normalizeRelationships(original));

		// Build a shallow copy of the model that reuses class objects but swaps
		// relationships.
		IntermediateModel cleaned = new IntermediateModel(ModelSource.SOURCE_CODE);
		cleaned.setClasses(new ArrayList<>(original.getClasses()));
		cleaned.setRelationships(normalized);

		return cleaned;
	}

	// --- INTERNALS ---

	/**
	 * Normalizes relationships so that: - DEPENDENCY is removed, - per (source ->
	 * target) and per family only the strongest remains, - inheritance family is
	 * validated/coerced based on class kinds, - ownership family uses composition >
	 * aggregation > association, - duplicates are collapsed, - self loops are
	 * ignored.
	 */
	private List<Relationship> normalizeRelationships(IntermediateModel model) {
		List<Relationship> all = model.getRelationships();
		if (all == null || all.isEmpty())
			return Collections.emptyList();

		// Strength for ownership family
		Map<RelationshipType, Integer> ownStrength = Map.of(RelationshipType.ASSOCIATION, 1,
				RelationshipType.AGGREGATION, 2, RelationshipType.COMPOSITION, 3);

		// Keyed by (srcName, tgtName)
		final class Key {
			final String s;
			final String t;

			Key(ClassInfo sc, ClassInfo tc) {
				this.s = sc == null ? "" : sc.getName();
				this.t = tc == null ? "" : tc.getName();
			}

			@Override
			public boolean equals(Object o) {
				if (this == o)
					return true;
				if (!(o instanceof Key))
					return false;
				Key k = (Key) o;
				return Objects.equals(s, k.s) && Objects.equals(t, k.t);
			}

			@Override
			public int hashCode() {
				return Objects.hash(s, t);
			}
		}

		Map<Key, RelationshipType> chosenOwnership = new HashMap<>();
		Map<Key, RelationshipType> chosenInheritance = new HashMap<>();
		Map<Key, ClassInfo[]> endpoints = new HashMap<>();

		for (Relationship r : all) {
			if (r == null)
				continue;
			ClassInfo src = r.getSourceClass();
			ClassInfo tgt = r.getTargetClass();
			if (src == null || tgt == null)
				continue;

			// Ignore self-loops
			if (safeEq(src.getName(), tgt.getName()))
				continue;

			RelationshipType type = r.getType();
			if (type == null)
				continue;

			// Drop dependency entirely
			if (type == RelationshipType.DEPENDENCY)
				continue;

			Key key = new Key(src, tgt);
			endpoints.putIfAbsent(key, new ClassInfo[] { src, tgt });

			// Ownership family
			if (type == RelationshipType.ASSOCIATION || type == RelationshipType.AGGREGATION
					|| type == RelationshipType.COMPOSITION) {

				RelationshipType current = chosenOwnership.get(key);
				if (current == null || ownStrength.get(type) > ownStrength.get(current)) {
					chosenOwnership.put(key, type);
				}
				continue;
			}

			// Inheritance family
			if (type == RelationshipType.GENERALIZATION || type == RelationshipType.REALIZATION) {
				RelationshipType normalized = normalizeInheritanceType(src, tgt);
				if (normalized == null)
					continue; // invalid combo
				// If a different inheritance type was previously stored, overwrite with the
				// valid one
				chosenInheritance.put(key, normalized);
			}
		}

		// Rebuild final list (at most 2 edges per pair: one ownership and one
		// inheritance)
		List<Relationship> result = new ArrayList<>(chosenOwnership.size() + chosenInheritance.size());
		for (Map.Entry<Key, RelationshipType> e : chosenOwnership.entrySet()) {
			ClassInfo[] ends = endpoints.get(e.getKey());
			result.add(new Relationship(ends[0], ends[1], e.getValue()));
		}
		for (Map.Entry<Key, RelationshipType> e : chosenInheritance.entrySet()) {
			ClassInfo[] ends = endpoints.get(e.getKey());
			result.add(new Relationship(ends[0], ends[1], e.getValue()));
		}
		return result;
	}

	/**
	 * Validates + coerces inheritance type for (src -> tgt) purely from ClassType:
	 * - CLASS -> CLASS => GENERALIZATION (extends) - CLASS -> INTERFACE =>
	 * REALIZATION (implements) - INTERFACE -> INTERFACE => GENERALIZATION (extends)
	 * - Anything else => invalid (null)
	 */
	private RelationshipType normalizeInheritanceType(ClassInfo src, ClassInfo tgt) {
		if (src == null || tgt == null)
			return null;
		ClassType s = src.getClassType();
		ClassType t = tgt.getClassType();
		if (s == null || t == null)
			return null;

		switch (s) {
		case CLASS:
			if (t == ClassType.CLASS)
				return RelationshipType.GENERALIZATION;
			if (t == ClassType.INTERFACE)
				return RelationshipType.REALIZATION;
			return null;
		case INTERFACE:
			if (t == ClassType.INTERFACE)
				return RelationshipType.GENERALIZATION;
			return null;
		default:
			return null; // enums/others not treated as inheritance sources here
		}
	}

	private boolean safeEq(String a, String b) {
		return Objects.equals(a, b);
	}

	// --- Helpers for class blocks ---

	private void emitTypeBlock(StringBuilder sb, ClassInfo ci) {
		String name = safeClassName(ci);

		ClassType kind = ci.getClassType();
		boolean isAbstract = safeIsAbstract(ci);

		String headerKeyword;
		switch (kind) {
		case INTERFACE:
			headerKeyword = "interface";
			break;
		case ENUM:
			headerKeyword = "enum";
			break;
		case CLASS:
		default:
			headerKeyword = isAbstract ? "abstract class" : "class";
			break;
		}

		sb.append(headerKeyword).append(' ').append(quoteIfNeeded(name)).append(" {").append('\n');

		// Attributes
		for (Attribute a : safeList(ci.getAttributes())) {
			if (shouldSkipVisibility(a.getVisibility()))
				continue;

			String vis = normalizeVisibility(a.getVisibility());
			String type = safeType(a.getType());
			String attrName = safe(a.getName());

			sb.append("  ").append(vis).append(' ').append(attrName);

			if (!type.isEmpty()) {
				sb.append(" : ").append(type);
			}
			sb.append('\n');
		}

		// Methods
		for (Method m : safeList(ci.getMethods())) {
			if (shouldSkipVisibility(m.getVisibility()))
				continue;

			String vis = normalizeVisibility(m.getVisibility());
			String methodName = safe(m.getName());
			String returnType = safeType(m.getReturnType());
			List<String> params = safeList(m.getParameters());

			String joinedParams = params.stream().map(PlantUMLGenerator::safeType).collect(Collectors.joining(", "));

			sb.append("  ").append(vis).append(' ').append(methodName).append('(').append(joinedParams).append(')');

			if (!returnType.isEmpty()) {
				sb.append(" : ").append(returnType);
			}
			sb.append('\n');
		}

		sb.append("}").append('\n');
	}

	// --- Relationship arrow mapping ---
	private static String toArrow(RelationshipType type) {
		if (type == null)
			return null;
		switch (type) {
		case GENERALIZATION:
			return "--|>";
		case REALIZATION:
			return "..|>";
		case ASSOCIATION:
			return "-->";
		case AGGREGATION:
			return "o--";
		case COMPOSITION:
			return "*--";
		case DEPENDENCY:
			return "..>";
		default:
			return null;
		}
	}

	// --- Visibility helpers ---
	private boolean shouldSkipVisibility(String visRaw) {
		String v = normalizeVisibility(visRaw);
		if (options.excludePrivate && "-".equals(v))
			return true;
		if (options.excludePackagePrivate && "~".equals(v))
			return true;
		return false;
	}

	private static String normalizeVisibility(String visRaw) {
		if (visRaw == null || visRaw.isBlank())
			return "~";
		String t = visRaw.trim();
		if (t.equals("+") || t.equals("-") || t.equals("#") || t.equals("~"))
			return t;
		// Map common Java keywords
		switch (t.toLowerCase(Locale.ROOT)) {
		case "public":
			return "+";
		case "private":
			return "-";
		case "protected":
			return "#";
		case "package": // fallthrough
		case "default":
			return "~";
		default:
			return "~";
		}
	}

	// --- Safe getters and null guards ---
	private static List<ClassInfo> modelGetClasses(IntermediateModel m) {
		try {
			return m.getClasses();
		} catch (Throwable t) {
			// Fallback: empty
			return Collections.emptyList();
		}
	}

	private static List<Relationship> modelGetRelationships(IntermediateModel m) {
		try {
			return m.getRelationships();
		} catch (Throwable t) {
			return Collections.emptyList();
		}
	}

	private static <T> List<T> safeList(List<T> list) {
		return list == null ? Collections.emptyList() : list;
	}

	private static String safeClassName(ClassInfo ci) {
		try {
			String n = ci.getName();
			return n == null ? "" : n;
		} catch (Throwable t) {
			return "";
		}
	}

	private static boolean safeIsAbstract(ClassInfo ci) {
		try {
			return ci.isAbstract();
		} catch (Throwable t) {
			return false;
		}
	}

	private static ClassInfo safeSource(Relationship r) {
		try {
			return r.getSourceClass();
		} catch (Throwable t) {
			return null;
		}
	}

	private static ClassInfo safeTarget(Relationship r) {
		try {
			return r.getTargetClass();
		} catch (Throwable t) {
			return null;
		}
	}

	// --- Escaping & sanitization ---
	private static String safe(String s) {
		return s == null ? "" : s.trim();
	}

	private static String safeType(String s) {
		String t = safe(s);
		// PlantUML is fine with generics like List<String>; just escape quotes.
		return t.replace("\"", "\\\"");
	}

	private static String quoteIfNeeded(String name) {
		String n = safe(name);
		if (n.isEmpty())
			return "\"\"";
		// Quote when it contains spaces or punctuation that could confuse the parser
		if (!n.matches("[A-Za-z_][A-Za-z0-9_$.]*")) {
			return "\"" + n.replace("\"", "\\\"") + "\"";
		}
		return n;
	}
	
	private static boolean isInheritance(RelationshipType t) {
	    return t == RelationshipType.GENERALIZATION || t == RelationshipType.REALIZATION;
	}

}
