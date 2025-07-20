package parser;

import java.util.*;
import java.util.regex.*;

public final class JavaParsingUtils {

	private JavaParsingUtils() {
		// static-only class
	}

	// Removes generic type parameters: List<String> → List
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

	public static Set<String> extractGenericTypesFromTypeSignature(String type) {

		System.out.println("\n[extractGenericTypesFromTypeSignature] Input type: " + type);
		Set<String> result = new HashSet<>();
		String normalized = type.replaceAll("[<>]", " ").replaceAll(",", " ");
		System.out.println("[extractGenericTypesFromTypeSignature] Normalized: " + normalized);

		Matcher matcher = Pattern.compile("[A-Z][a-zA-Z0-9_]*").matcher(normalized);
		while (matcher.find()) {
			String extracted = matcher.group();
			result.add(extracted);
			System.out.println("[extractGenericTypesFromTypeSignature] → Found: " + extracted);
		}
		System.out.println("[extractGenericTypesFromTypeSignature] Final result: " + result);
		return result;
	}

	public static String normalizeType(String type) {
		return type.replaceAll("\\s*<\\s*", "<").replaceAll("\\s*>\\s*", ">").replaceAll("\\s*,\\s*", ",").trim();
	}

}
