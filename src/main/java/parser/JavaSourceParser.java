package parser;

import model.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaSourceParser parses one or more Java files and builds an
 * IntermediateModel with classes, attributes, and relationships.
 */
public class JavaSourceParser {

	private final IntermediateModel model;

	// Initializes empty model and warnings
	public JavaSourceParser() {
		this.model = new IntermediateModel(ModelSource.SOURCE_CODE);
	}

	// Parses one Java file
	public IntermediateModel parse(File file) throws IOException {
		return parse(Collections.singletonList(file));
	}

	// Parses a list of Java files
	public IntermediateModel parse(List<File> files) throws IOException {
		for (File f : files) {
			parseFile(f);
		}
		return model;
	}

	// Returns the model
	public IntermediateModel getIntermediateModel() {
		return model;
	}

	// Parses a single Java file
	private void parseFile(File file) throws IOException {
		List<String> rawLines = readLines(file);

		for (int i = 0; i < rawLines.size(); i++) {
			String line = cleanLine(rawLines.get(i));
			if (line.isEmpty())
				continue;

			if (isClassDeclaration(line)) {
				ClassInfo classInfo = parseClassInfo(line);
				if (classInfo == null)
					continue;

				// Collect class body lines (until braces are balanced)
				List<String> bodyLines = new ArrayList<>();
				int braceCount = countBraces(line);
				i++;

				while (i < rawLines.size() && braceCount > 0) {
					bodyLines.add(rawLines.get(i));
					braceCount += countBraces(rawLines.get(i));
					i++;
				}

				// Step 1: Remove all comments (single-line and multi-line)
				List<String> noComments = cleanComments(bodyLines);

				// Step 2: Join multi-line declarations (e.g. attributes, methods)
				List<String> logicalLines = joinLogicalLines(noComments);

				// Step 3: Split any merged statements (e.g. "} public ...")
				List<String> splitLines = splitMultipleStatements(logicalLines);

				// Parse attributes and methods from cleaned & structured lines
				parseAttributes(splitLines, classInfo);
				extractMethods(splitLines, classInfo);
			}
		}
	}

	// Counts braces to find block scope
	private int countBraces(String line) {
		int open = 0, close = 0;
		for (char c : line.toCharArray()) {
			if (c == '{')
				open++;
			if (c == '}')
				close++;
		}
		return open - close;
	}

	// Reads all lines of a file
	private List<String> readLines(File file) throws IOException {
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		}
		return lines;
	}

	// Removes comments and trims whitespace
	private String cleanLine(String line) {
		if (line == null)
			return "";
		int commentIndex = line.indexOf("//");
		if (commentIndex != -1) {
			line = line.substring(0, commentIndex);
		}
		line = line.replace("/*", "").replace("*/", "");
		return line.trim();
	}

	// Removes // and /* */ comments from a list of lines
	private List<String> cleanComments(List<String> lines) {
		List<String> result = new ArrayList<>();
		boolean inBlockComment = false;

		for (String line : lines) {
			String trimmed = line.trim();

			if (inBlockComment) {
				if (trimmed.contains("*/")) {
					inBlockComment = false;
					// Remove up to and including */
					trimmed = trimmed.substring(trimmed.indexOf("*/") + 2).trim();
					if (trimmed.isEmpty())
						continue;
				} else {
					continue; // skip whole line
				}
			}

			if (trimmed.contains("/*")) {
				inBlockComment = true;
				// Keep part before /*
				trimmed = trimmed.substring(0, trimmed.indexOf("/*")).trim();
			}

			// Remove // comments
			if (trimmed.contains("//")) {
				trimmed = trimmed.substring(0, trimmed.indexOf("//")).trim();
			}

			if (trimmed.startsWith("@")) {
				continue; // Ignore annotations like @Override, @Deprecated, etc.
			}

			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}

		return result;
	}

	// Pattern to detect class, interface, enum
	private static final Pattern CLASS_PATTERN = Pattern.compile(
			"(?:public\\s+|protected\\s+|private\\s+)?(?:abstract\\s+)?(?:final\\s+)?(class|interface|enum)\\s+(\\w+)");

	// Checks if a line contains a class/interface/enum
	private boolean isClassDeclaration(String line) {
		return CLASS_PATTERN.matcher(line).find();
	}

	private static final Pattern CLASS_DECLARATION_PATTERN = Pattern
			.compile("(?:public\\s+|protected\\s+|private\\s+)?(?:abstract\\s+)?(?:final\\s+)?"
					+ "(class|interface|enum)\\s+(\\w+)" // group 1: type, group 2: name
					+ "(?:\\s+extends\\s+([\\w<>]+))?" // group 3: extends
					+ "(?:\\s+implements\\s+([\\w<>,\\s]+))?" // group 4: implements
			);

	// Parses class/interface/enum declaration and adds it to the model
	private ClassInfo parseClassInfo(String line) {
		Matcher matcher = CLASS_DECLARATION_PATTERN.matcher(line);
		if (!matcher.find())
			return null;

		String kind = matcher.group(1);
		String name = matcher.group(2);
		String extended = matcher.group(3);
		String implemented = matcher.group(4);

		ClassType type = switch (kind) {
		case "class" -> ClassType.CLASS;
		case "interface" -> ClassType.INTERFACE;
		case "enum" -> ClassType.ENUM;
		default -> null;
		};

		boolean isAbstract = line.contains("abstract");

		ClassInfo classInfo = getOrInitializeClass(name, type, isAbstract);
		handleExtends(extended, classInfo, type);
		handleImplements(implemented, classInfo);

		return classInfo;
	}

	// Returns existing ClassInfo or initializes a new one
	private ClassInfo getOrInitializeClass(String name, ClassType type, boolean isAbstract) {
		Optional<ClassInfo> existing = model.getClasses().stream().filter(c -> c.getName().equals(name)).findFirst();

		if (existing.isPresent()) {
			ClassInfo c = existing.get();
		    if (c.getDeclaration() == ClassDeclaration.DUMMY) {
		        c.setDeclaration(ClassDeclaration.OFFICIAL);
		    }
			c.setClassType(type);
			c.setAbstract(isAbstract);
			model.removeWarningsForClass(name);

			return c;
		} else {
			ClassInfo c = new ClassInfo(name, type, isAbstract, ClassDeclaration.OFFICIAL);
			model.addClass(c);
			return c;
		}
	}

	// Processes extends clause and adds generalization relationships
	private void handleExtends(String extended, ClassInfo classInfo, ClassType type) {
		if (extended == null || extended.isBlank())
			return;

		if (type == ClassType.INTERFACE) {
			String[] interfaces = extended.split(",");
			for (String iface : interfaces) {
				String superName = stripGenerics(iface.trim());
				if (!superName.isEmpty()) {
					ClassInfo parent = getOrCreateClass(superName);
					model.addRelationship(new Relationship(classInfo, parent, RelationshipType.GENERALIZATION));
				}
			}
		} else {
			String superName = stripGenerics(extended.trim());
			ClassInfo parent = getOrCreateClass(superName);
			model.addRelationship(new Relationship(classInfo, parent, RelationshipType.GENERALIZATION));
		}
	}

	// Processes implements clause and adds realization relationships
	private void handleImplements(String implemented, ClassInfo classInfo) {
		if (implemented == null || implemented.isBlank())
			return;

		String[] interfaces = implemented.split(",");
		for (String iface : interfaces) {
			String ifaceName = stripGenerics(iface.trim());
			if (!ifaceName.isEmpty()) {
				ClassInfo impl = getOrCreateClass(ifaceName);
				model.addRelationship(new Relationship(classInfo, impl, RelationshipType.REALIZATION));
			}
		}
	}

	// Returns ClassInfo from model or creates a new one (unless it's a built-in
	// Java type)
	private ClassInfo getOrCreateClass(String className) {

		// Ignore built-in Java types
		if (isJavaBuiltInType(className)) {
			return null;
		}

		// If class already exists in model, return it
		for (ClassInfo c : model.getClasses()) {
			if (c.getName().equals(className)) {
				return c;
			}
		}

		// Otherwise, create a new "dummy" class and add it to the model
		ClassInfo dummy = new ClassInfo(className, ClassType.CLASS, ClassDeclaration.DUMMY);
		model.addClass(dummy);
		model.addWarning("Class '" + className + "' not found in source files. Added as dummy.");

		return dummy;
	}

	// Checks if line looks like an attribute
	private boolean isAttributeDeclaration(String line) {
		return line.matches(".*(private|protected|public)\\s+.+\\s+\\w+(\\s*=.+)?;");
	}

	// Parses one field into an Attribute object
	private Attribute parseAttribute(String line) {
		// Supports lines like: private Map<String, List<Book>> bookIndex;
		Pattern pattern = Pattern.compile("(private|protected|public)\\s+([\\w<>,\\s\\[\\]]+)\\s+(\\w+)\\s*(=.*)?;");
		Matcher matcher = pattern.matcher(line);

		if (!matcher.find()) {
			return null;
		}

		String visibility = matcher.group(1);
		String type = matcher.group(2).trim(); // full generic type
		String name = matcher.group(3).trim();

		return new Attribute(name, type, visibilitySymbol(visibility));
	}

	// Maps visibility keyword to UML symbol
	private String visibilitySymbol(String keyword) {
		return switch (keyword) {
		case "private" -> "-";
		case "public" -> "+";
		case "protected" -> "#";
		default -> "~";
		};
	}

	// Parses a list of lines for attributes
	private void parseAttributes(List<String> lines, ClassInfo classInfo) {
		for (String line : lines) {
			line = cleanLine(line);
			if (isAttributeDeclaration(line)) {
				Attribute attr = parseAttribute(line);
				if (attr != null) {
					classInfo.addAttribute(attr);

					Set<String> types = extractGenericTypes(attr.getType());

					boolean isMainComposed = isComposedAttribute(line, attr.getType());
					String declared = attr.getType();

					for (String type : types) {
						String cleaned = stripGenerics(type).trim();

						if (!isJavaBuiltInType(type)) {
							ClassInfo target = getOrCreateClass(type);
							if (target != null) {
								boolean isInComposedStructure = isMainComposed && declared.contains(type);

								RelationshipType relType = isInComposedStructure ? RelationshipType.COMPOSITION
										: RelationshipType.ASSOCIATION;

								model.addRelationship(new Relationship(classInfo, target, relType));
							}
						}
					}

				}
			}
		}
	}

	// Removes generic type parameters: List<String> → List
	private String stripGenerics(String type) {
		return type.replaceAll("<.*?>", "");
	}

	private static final Pattern METHOD_PATTERN = Pattern.compile("^(public|protected|private)?\\s*" + // 1: visibility
			"(static\\s+)?(final\\s+)?(abstract\\s+)?\\s*" + // 2-4: modifiers
			"([\\w\\<\\>\\[\\]\\,\\s]+)\\s+" + // 5: return type (with <, >, [ ], , and whitespace)
			"(\\w+)\\s*\\(([^)]*)\\)\\s*" // 6: method name, 7: parameters
	);

	private static final Pattern METHOD_HEADER_PATTERN = Pattern.compile("\\b(public|private|protected)\\b.*\\(.*\\)");

	// Checks if a type is a known Java built-in type
	private boolean isJavaBuiltInType(String typeName) {
		return List.of("int", "long", "double", "float", "char", "byte", "short", "boolean", "void", "Integer", "Long",
				"Double", "Float", "Character", "Byte", "Short", "Boolean", "String", "Object", "List", "Map", "Set",
				"ArrayList", "HashMap", "HashSet", "Optional", "Queue", "Deque", "LinkedList", "TreeMap", "TreeSet",
				"LinkedHashMap", "LinkedHashSet", "LocalDate", "LocalTime", "LocalDateTime", "ZonedDateTime",
				"Duration", "Period", "BigDecimal", "BigInteger").contains(typeName);
	}

	// Parses a full method body string into a Method object
	private Method parseMethod(String methodBody, ClassInfo classInfo) {
		String[] lines = methodBody.split("\\n");
		if (lines.length == 0)
			return null;

		StringBuilder headerBuilder = new StringBuilder();

		// Join all lines of the header into a single string
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty())
				continue;

			// Skip annotation lines
			if (trimmed.startsWith("@"))
				continue;

			headerBuilder.append(trimmed).append(" ");

			// Break after opening brace (assumes signature ends there)
			if (trimmed.contains("{"))
				break;
		}

		String headerLine = headerBuilder.toString().trim();

		// Match method signature
		Matcher matcher = METHOD_PATTERN.matcher(headerLine);
		if (!matcher.find())
			return null;

		String visibilityKeyword = matcher.group(1); // public/private/etc
		String returnType = matcher.group(5); // return type
		String methodName = matcher.group(6); // method name
		String paramList = matcher.group(7); // parameters inside (...)

		boolean isConstructor = isConstructor(methodName, classInfo);
		String visibility = visibilitySymbol(visibilityKeyword != null ? visibilityKeyword : "");

		Method method = new Method(methodName, stripGenerics(returnType), visibility);

		// Parse and track parameter types
		Map<String, String> paramNamesAndTypes = parseParameters(paramList, method, classInfo, isConstructor);

		// Add dependency from return type (if not constructor)
		if (!isConstructor) {
			processReturnType(returnType, classInfo);
		}

		// Analyze method body (for composition, aggregation, etc.)
		analyzeMethodBody(methodBody, classInfo, methodName, paramNamesAndTypes);

		// Add method to class (constructors are excluded)
		if (!isConstructor) {
			classInfo.addMethod(method);
		}

		return method;
	}

	// checks if method name equals class name
	private boolean isConstructor(String methodName, ClassInfo classInfo) {
		return methodName.equals(classInfo.getName());
	}

	// Parses method parameters and adds dependencies (unless it's a constructor)
	private Map<String, String> parseParameters(String paramList, Method method, ClassInfo classInfo,
			boolean isConstructor) {

		Map<String, String> paramMap = new HashMap<>();

		if (paramList != null && !paramList.isBlank()) {
			String[] params = paramList.split(",");

			for (String param : params) {
				String[] parts = param.trim().split("\\s+");

				// Must have type and name
				if (parts.length >= 2) {
					String type = parts[0];
					String name = parts[1];

					method.addParameter(type);
					paramMap.put(name, type);

					// Extract main inner type (e.g., Book from List<Book>)
					String inner = extractInnerType(type);
					String cleaned = stripGenerics(inner);

					if (!isConstructor && !isJavaBuiltInType(cleaned)) {
						ClassInfo dependency = getOrCreateClass(cleaned);
						if (dependency != null) {
							model.addRelationship(new Relationship(classInfo, dependency, RelationshipType.DEPENDENCY));
						}
					}

					// For constructors, still ensure class exists (as dummy if needed)
					if (isConstructor && !isJavaBuiltInType(cleaned)) {
						getOrCreateClass(cleaned);
					}
				}
			}
		}

		return paramMap;
	}

	private void processReturnType(String returnType, ClassInfo classInfo) {
		if (returnType == null || returnType.equals("void"))
			return;

		Set<String> returnTypes = extractGenericTypesFromTypeSignature(returnType);

		for (String type : returnTypes) {

			if (!isJavaBuiltInType(type)) {
				ClassInfo dependency = getOrCreateClass(type);
				if (dependency != null) {
					model.addRelationship(new Relationship(classInfo, dependency, RelationshipType.DEPENDENCY));
				}
			}
		}
	}

	// checks method body for local relationships
	private void analyzeMethodBody(String body, ClassInfo classInfo, String methodName, Map<String, String> params) {
		extractNewInstanceDependencies(body, classInfo);
		extractCompositionAssignments(body, classInfo);
		extractAggregationAssignments(body, classInfo, methodName, params);
	}

	// Extracts inner type from generics: List<People> → People
	private String extractInnerType(String type) {
		Matcher m = Pattern.compile("<\\s*(\\w+)\\s*>").matcher(type);
		if (m.find()) {
			return m.group(1);
		}

		return type;
	}

	// Detects 'new SomeType()' usages inside method bodies and adds DEPENDENCY
	// relationships
	private void extractNewInstanceDependencies(String methodBody, ClassInfo currentClass) {
		Pattern pattern = Pattern.compile("new\\s+([a-zA-Z_][a-zA-Z0-9_<>]*)\\s*\\(");
		Matcher matcher = pattern.matcher(methodBody);

		while (matcher.find()) {
			String rawType = matcher.group(1);

			// Remove generics, e.g., List<Book> → List
			String cleanType = rawType.contains("<") ? rawType.substring(0, rawType.indexOf("<")) : rawType;

			if (isJavaBuiltInType(cleanType)) {
				continue;
			}

			// Check if it's part of 'this.x = new Type()' (likely composition, not
			// dependency)
			int start = matcher.start();
			boolean isFieldAssignment = false;
			if (start >= 6) {
				String before = methodBody.substring(Math.max(0, start - 20), start);
				if (before.contains("this.")) {
					isFieldAssignment = true;
				}
			}

			if (!isFieldAssignment) {
				// Ensure the target class exists
				ClassInfo targetClass = model.findClassByName(cleanType);
				if (targetClass == null) {
					targetClass = new ClassInfo(cleanType, ClassType.CLASS, ClassDeclaration.DUMMY);
					model.addClass(targetClass);
					model.addWarning("Class '" + cleanType + "' not found in source files. Added as dummy.");

				}

				Relationship relationship = new Relationship(currentClass, targetClass, RelationshipType.DEPENDENCY);
				model.addRelationship(relationship);
			}
		}
	}

	// Extracts all method declarations (with full bodies) from class lines
	// Extracts all method declarations (with full bodies) from class lines
	private void extractMethods(List<String> lines, ClassInfo currentClass) {
		boolean collectingHeader = false;
		boolean insideMethod = false;
		int openBraces = 0;

		StringBuilder headerBuffer = new StringBuilder();
		StringBuilder methodBuffer = new StringBuilder();

		for (String l : lines) {
		}

		for (String line : lines) {
			if (!insideMethod) {

				if (line.matches(".*(public|private|protected).*\\(.*")) {
				}

				if (!collectingHeader) {
					// Look for start of method signature (must include a visibility keyword and "("
					// )
					if (line.matches(".*(public|private|protected).*\\(.*")) {
						headerBuffer.setLength(0);
						headerBuffer.append(line).append(" ");

						// If line contains closing parenthesis, start body tracking
						if (line.contains(")")) {

							collectingHeader = false;
							insideMethod = true;
							openBraces = countOccurrences(line, '{') - countOccurrences(line, '}');
							methodBuffer.setLength(0);
							methodBuffer.append(headerBuffer.toString().trim()).append("\n");

							if (openBraces == 0 && !line.contains("{")) {
								continue; // Probably abstract or interface method, skip
							}

							if (openBraces == 0) {
								parseMethod(methodBuffer.toString(), currentClass);
								insideMethod = false;
							}
						} else {
							collectingHeader = true;
						}
					}
				} else {
					headerBuffer.append(line).append(" ");
					if (line.contains(")")) {
						collectingHeader = false;
						insideMethod = true;
						openBraces = countOccurrences(line, '{') - countOccurrences(line, '}');
						methodBuffer.setLength(0);
						methodBuffer.append(headerBuffer.toString().trim()).append("\n");

						if (openBraces == 0 && !line.contains("{")) {
							continue;
						}

						if (openBraces == 0) {
							parseMethod(methodBuffer.toString(), currentClass);
							insideMethod = false;

						}
					}
				}
			} else {
				methodBuffer.append(line).append("\n");
				openBraces += countOccurrences(line, '{') - countOccurrences(line, '}');

				if (openBraces <= 0) {

					parseMethod(methodBuffer.toString(), currentClass);
					insideMethod = false;
					openBraces = 0;
				}
			}
		}
	}

	private int countOccurrences(String s, char c) {
		return (int) s.chars().filter(ch -> ch == c).count();
	}

	// Detects assignments like 'this.x = new Type()' and adds COMPOSITION
	// relationships
	private void extractCompositionAssignments(String methodBody, ClassInfo currentClass) {
		Pattern pattern = Pattern.compile("this\\.\\w+\\s*=\\s*new\\s+([a-zA-Z_][a-zA-Z0-9_<>]*)\\s*\\(");
		Matcher matcher = pattern.matcher(methodBody);

		while (matcher.find()) {
			String rawType = matcher.group(1);

			// Remove generics, e.g., List<Book> → List
			String cleanType = rawType.contains("<") ? rawType.substring(0, rawType.indexOf("<")) : rawType;

			if (isJavaBuiltInType(cleanType)) {
				continue;
			}

			// Ensure the class exists
			ClassInfo targetClass = model.findClassByName(cleanType);
			if (targetClass == null) {
				targetClass = new ClassInfo(cleanType, ClassType.CLASS, ClassDeclaration.DUMMY);
				model.addClass(targetClass);
				model.addWarning("Class '" + cleanType + "' not found in source files. Added as dummy.");
			}

			Relationship relationship = new Relationship(currentClass, targetClass, RelationshipType.COMPOSITION);
			model.addRelationship(relationship);
		}
	}

	// Detects 'this.x = x;' and adds AGGREGATION if x is method parameter
	// Applies in constructors and setters
	private void extractAggregationAssignments(String methodBody, ClassInfo currentClass, String methodName,
			Map<String, String> paramNamesAndTypes) {
		boolean isConstructor = methodName.equals(currentClass.getName());
		boolean isSetter = methodName.startsWith("set") && paramNamesAndTypes.size() == 1;

		if (!(isConstructor || isSetter)) {
			return;
		}

		for (Map.Entry<String, String> param : paramNamesAndTypes.entrySet()) {
			String paramName = param.getKey();
			String paramType = param.getValue();

			// Accept both: this.chapter = chapter;
			// OR: this.mainChapter = chapter;
			String patternString = "this\\.[a-zA-Z0-9_]+\\s*=\\s*" + Pattern.quote(paramName) + "\\s*;";
			Pattern pattern = Pattern.compile(patternString);
			Matcher matcher = pattern.matcher(methodBody);

			if (matcher.find() && !isJavaBuiltInType(paramType)) {
				ClassInfo target = model.findClassByName(paramType);
				if (target == null) {
					target = new ClassInfo(paramType, ClassType.CLASS, ClassDeclaration.DUMMY);
					model.addClass(target);
					model.addWarning("Class '" + paramType + "' not found in source files. Added as dummy.");
				}

				Relationship rel = new Relationship(currentClass, target, RelationshipType.AGGREGATION);
				model.addRelationship(rel);
			}
		}
	}

	// Checks if an attribute includes `= new ...` → composition if it's main type
	// or a collection
	private boolean isComposedAttribute(String line, String declaredType) {
		if (line.contains("=") && line.contains("new")) {
			Matcher matcher = Pattern.compile("=\\s*new\\s+([a-zA-Z_][a-zA-Z0-9_<>]*)").matcher(line);
			if (matcher.find()) {
				String constructedType = stripGenerics(matcher.group(1));
				String declaredMain = stripGenerics(declaredType);

				if (constructedType.equals(declaredMain)) {

					return true;
				}

				if (isCollectionType(declaredMain) && isCollectionType(constructedType)) {

					return true;
				}
			}
		}
		return false;
	}

	// Checks if a type is a known collection type
	private boolean isCollectionType(String type) {
		return List.of("List", "Set", "Map", "HashSet", "ArrayList", "HashMap").contains(type);
	}

	// Extracts all type names from generic declarations, including nested
	private Set<String> extractGenericTypes(String type) {
		Set<String> types = new HashSet<>();
		type = type.replaceAll("[<>]", " ");
		type = type.replaceAll(",", " ");

		Matcher matcher = Pattern.compile("[A-Z][a-zA-Z0-9_]*").matcher(type);
		while (matcher.find()) {
			String t = matcher.group();
			types.add(t);
		}
		return types;
	}

	// Joins lines that belong to the same logical declaration (e.g. attributes,
	// method headers)
	private List<String> joinLogicalLines(List<String> lines) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int parens = 0; // ()
		int angles = 0; // <>
		int braces = 0; // not strictly needed but could help

		for (String rawLine : lines) {
			String line = rawLine.trim();
			if (line.isEmpty())
				continue;

			// Count open/close brackets to detect multi-line blocks
			for (char c : line.toCharArray()) {
				if (c == '(')
					parens++;
				if (c == ')')
					parens--;
				if (c == '<')
					angles++;
				if (c == '>')
					angles--;
				if (c == '{')
					braces++;
				if (c == '}')
					braces--;
			}

			current.append(line).append(" ");

			boolean isCompleteLine = line.endsWith(";") || line.endsWith("{");

			if (parens <= 0 && angles <= 0 && isCompleteLine) {
				result.add(current.toString().trim());
				current.setLength(0);
			}
		}

		// Add any remaining line (could be last line or error case)
		if (current.length() > 0) {
			result.add(current.toString().trim());
		}

		return result;
	}

	// Splits lines that contain multiple statements (e.g. "} public ...") into
	// separate lines
	private List<String> splitMultipleStatements(List<String> lines) {
		List<String> result = new ArrayList<>();
		for (String line : lines) {
			String[] parts = line.split("(?<=\\})\\s+(?=public|private|protected)");
			for (String part : parts) {
				if (!part.trim().isEmpty()) {
					result.add(part.trim());
				}
			}
		}
		return result;
	}

	private Set<String> extractGenericTypesFromTypeSignature(String type) {
		Set<String> result = new HashSet<>();
		type = type.replaceAll("[<>]", " ");
		type = type.replaceAll(",", " ");
		Matcher matcher = Pattern.compile("[A-Z][a-zA-Z0-9_]*").matcher(type);
		while (matcher.find()) {
			result.add(matcher.group());
		}
		return result;
	}

}
