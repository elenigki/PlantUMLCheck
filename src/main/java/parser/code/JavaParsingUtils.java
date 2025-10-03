package parser.code;

import java.util.*;
import java.util.regex.*;

public final class JavaParsingUtils {

	private JavaParsingUtils() {
	}

	// Removes generic type parameters: List<String> --> List
	public static String stripGenerics(String type) {
		return type.replaceAll("<.*?>", "");
	}

	// Checks if a type is a known Java built-in type
	public static boolean isJavaBuiltInType(String typeName) {

		return List.of("int", "long", "double", "float", "char", "byte", "short", "boolean", "void", "Integer", "Long",
				"Double", "Float", "Character", "Byte", "Short", "Boolean", "String", "Object", "List", "Map", "Set",
				"ArrayList", "HashMap", "HashSet", "Optional", "Queue", "Deque", "LinkedList", "TreeMap", "TreeSet",
				"LinkedHashMap", "LinkedHashSet", "LocalDate", "LocalTime", "LocalDateTime", "ZonedDateTime",
				"Duration", "Period", "BigDecimal", "BigInteger").contains(typeName);
	}

	// Maps visibility keyword to UML symbol
	public static String visibilitySymbol(String keyword) {
		return switch (keyword) {
		case "private" -> "-";
		case "public" -> "+";
		case "protected" -> "#";
		default -> "~";
		};
	}

	// Counts braces to find block scope
	public static int countBraces(String line) {
		int open = 0, close = 0;
		for (char c : line.toCharArray()) {
			if (c == '{')
				open++;
			if (c == '}')
				close++;
		}
		return open - close;
	}
	

	// -------------------- helpers (grouped) --------------------
	
	
	public static Set<String> extractGenericTypesFromTypeSignature(String type) {

		Set<String> result = new HashSet<>();
		String normalized = type.replaceAll("[<>]", " ").replaceAll(",", " ");

		Matcher matcher = Pattern.compile("[A-Z][a-zA-Z0-9_]*").matcher(normalized);
		while (matcher.find()) {
			String extracted = matcher.group();
			result.add(extracted);
		}
		return result;
	}

	public static String normalizeType(String type) {
		return type.replaceAll("\\s*<\\s*", "<").replaceAll("\\s*>\\s*", ">").replaceAll("\\s*,\\s*", ",").trim();
	}

	public static String normalizeMods(String mods) {
		if (mods == null)
			return " ";
		String norm = mods.trim().replaceAll("\\s+", " ");
		return " " + norm + " ";
	}

	public static boolean hasStatic(String mods) {
		return normalizeMods(mods).contains(" static ");
	}

}