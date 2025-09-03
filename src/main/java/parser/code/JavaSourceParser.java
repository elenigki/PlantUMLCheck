package parser.code;

import model.*;

import static parser.code.JavaParsingUtils.*;

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
	private final JavaSourcePreprocessor preprocessor = new JavaSourcePreprocessor();

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
		List<String> rawLines = preprocessor.readLines(file);

		System.out.println("\n========= RAW LINES from: " + file.getName() + " =========");
		rawLines.forEach(System.out::println);

		for (int i = 0; i < rawLines.size(); i++) {
			String line = preprocessor.cleanLine(rawLines.get(i));
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

				System.out.println("\n--------- BODY LINES ---------");
				bodyLines.forEach(System.out::println);

				// Step 1: Remove all comments (single-line and multi-line)
				List<String> noComments = preprocessor.cleanComments(bodyLines);
				System.out.println("\n--------- AFTER cleanComments ---------");
				noComments.forEach(System.out::println);

				// Step 2: Join multi-line declarations (e.g. attributes, methods)
				List<String> logicalLines = preprocessor.joinLogicalLines(noComments);
				System.out.println("\n--------- AFTER joinLogicalLines ---------");
				logicalLines.forEach(System.out::println);

				// Step 3: Split any merged statements (e.g. "} public ...")
				List<String> splitLines = preprocessor.splitMultipleStatements(logicalLines);
				System.out.println("\n--------- AFTER splitMultipleStatements ---------");
				splitLines.forEach(System.out::println);

				// Parse attributes and methods from cleaned & structured lines
				parseAttributes(splitLines, classInfo);

				// NEW: pick up interface abstract signatures like 'void draw();'
				extractAbstractSignatures(splitLines, classInfo);

				extractMethods(splitLines, classInfo);
			}
		}
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

	// Returns existing ClassInfo or creates a new one if it doesn't exist
	private ClassInfo getOrInitializeClass(String name, ClassType type, boolean isAbstract) {
		// Remove generics like "<Book>" from the class name
		String cleaned = stripGenerics(name);

		// Skip if it's a built-in Java type (like List, Map, etc.)
		if (isJavaBuiltInType(cleaned)) {
			return null;
		}

		// Try to find existing class with the cleaned name
		Optional<ClassInfo> existing = model.getClasses().stream().filter(c -> c.getName().equals(cleaned)).findFirst();

		if (existing.isPresent()) {
			ClassInfo c = existing.get();

			// If it was a dummy, promote it to official
			if (c.getDeclaration() == ClassDeclaration.DUMMY) {
				c.setDeclaration(ClassDeclaration.OFFICIAL);
			}

			// Update with the correct type and abstract flag
			c.setClassType(type);
			c.setAbstract(isAbstract);

			// Remove any warning about this class being a dummy
			model.removeWarningsForClass(cleaned);
			return c;
		} else {
			// Create a new official class using the cleaned name
			ClassInfo c = new ClassInfo(cleaned, type, isAbstract, ClassDeclaration.OFFICIAL);
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
		System.out.println("[getOrCreateClass] Input name: " + className);

		String cleaned = stripGenerics(className);
		// Ignore built-in Java types
		if (isJavaBuiltInType(cleaned)) {
			System.out.println("[getOrCreateClass] Skipping built-in type: " + cleaned);
			return null;
		}

		// If class already exists in model, return it
		for (ClassInfo c : model.getClasses()) {
			if (c.getName().equals(cleaned)) {
				return c;
			}
		}

		// Otherwise, create a new "dummy" class and add it to the model
		ClassInfo dummy = new ClassInfo(cleaned, ClassType.CLASS, ClassDeclaration.DUMMY);
		model.addClass(dummy);
		model.addWarning("Class '" + cleaned + "' not found in source files. Added as dummy.");

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

		return new Attribute(name, normalizeType(type), visibilitySymbol(visibility));
	}


	// Parses a list of lines for attributes
	private void parseAttributes(List<String> lines, ClassInfo classInfo) {
		for (String line : lines) {
			line = preprocessor.cleanLine(line);
			if (isAttributeDeclaration(line)) {
				Attribute attr = parseAttribute(line);
				System.out.println("[parseAttributes] → line: " + line);
				System.out.println("[parseAttributes] → parsed type: " + attr.getType() + ", name: " + attr.getName());

				if (attr != null) {
					classInfo.addAttribute(attr);

					Set<String> types = extractGenericTypes(attr.getType());
					System.out.println("[parseAttributes] → extracted generic types: " + types);

					boolean isMainComposed = isComposedAttribute(line, attr.getType());
					String declared = attr.getType();

					for (String type : types) {
						System.out.println("[parseAttributes] → examining type: " + type);
						System.out.println("[parseAttributes] → declared type string: " + declared);
						if (type == null || type.isBlank())
							continue;
						String cleaned = stripGenerics(type);

						if (!isJavaBuiltInType(cleaned)) {
							System.out.println("[parseAttributes] → creating/using class: " + cleaned);
							ClassInfo target = getOrCreateClass(cleaned);
							if (target != null) {
								boolean isInComposedStructure = isMainComposed && declared.contains(cleaned);

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


	// ==========================
	// Method extraction patterns
	// ==========================

	private static final Pattern METHOD_PATTERN = Pattern.compile("^(public|protected|private)?\\s*" + // 1: visibility
			"(static\\s+)?(final\\s+)?(abstract\\s+)?\\s*" + // 2-4: modifiers
			"([\\w\\<\\>\\[\\]\\,\\s]+)\\s+" + // 5: return type (with <, >, [ ], , and whitespace)
			"(\\w+)\\s*\\(([^)]*)\\)\\s*" // 6: method name, 7: parameters
	);

	private static final Pattern METHOD_HEADER_PATTERN = Pattern
			.compile("\\b(public|private|protected)\\b.*\\(.*\\)\\s*(\\{)?\\s*$");

	// NEW: very narrow pattern for ';'-terminated abstract/interface signatures
	private static final Pattern ABSTRACT_SIGNATURE_PATTERN = Pattern.compile(
			"^\\s*(?:@[\\w.]+\\s*)*" +                     // annotations (optional)
			"(?:public|protected|private)?\\s*" +          // visibility (optional)
			"(?:(?:abstract|default|static|strictfp)\\s+)*" + // modifiers (optional)
			"([\\w\\<\\>\\[\\]\\.,\\s]+)\\s+" +           // (1) return type
			"(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)\\s*" + // (2) name
			"\\(([^)]*)\\)\\s*" +                         // (3) params
			"(?:throws\\s+[\\w\\.,\\s]+)?\\s*;"           // optional throws, then ';'
	);


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
		System.out.println("[parseMethod] Header line: " + headerLine);

		// Match method signature
		Matcher matcher = METHOD_PATTERN.matcher(headerLine);
		if (!matcher.find()) {
			if (!matcher.find()) {
				System.out.println("[parseMethod] No match for header pattern.");
				return null;
			}
			return null;
		}

		String visibilityKeyword = matcher.group(1); // public/private/etc
		String returnType = normalizeType(matcher.group(5)); // return type
		String methodName = matcher.group(6); // method name
		String paramList = matcher.group(7); // parameters inside (...)

		System.out.println("[parseMethod] Name: " + methodName);
		System.out.println("[parseMethod] Return type: " + returnType);
		System.out.println("[parseMethod] Parameters raw: " + paramList);

		boolean isConstructor = isConstructor(methodName, classInfo);
		String visibility = visibilitySymbol(visibilityKeyword != null ? visibilityKeyword : "");

		Method method = new Method(methodName, returnType, visibility);

		// Parse and track parameter types
		Map<String, String> paramNamesAndTypes = parseParameters(paramList, method, classInfo, isConstructor);
		System.out.println("[parseMethod] Parameters extracted (name → type): " + paramNamesAndTypes);

		// Add dependency from return type (if not constructor)
		if (!isConstructor) {
			if (!isConstructor) {
				System.out.println("[parseMethod] → Processing return type: " + returnType);
			}

			processReturnType(returnType, classInfo);
		}

		// Analyze method body (for composition, aggregation, etc.)
		analyzeMethodBody(methodBody, classInfo, methodName, paramNamesAndTypes);

		// Add method to class (constructors are excluded)
		if (!isConstructor) {
			System.out.println("[parseMethod] → Method added to class: " + method.getName());

			classInfo.addMethod(method);
		}

		return method;
	}

	// NEW: picks up ';'-terminated interface method signatures (no body)
	private void extractAbstractSignatures(List<String> lines, ClassInfo classInfo) {
		// Only consider interface members here to keep pattern scope tight
		if (classInfo == null || classInfo.getClassType() != ClassType.INTERFACE) {
			return;
		}

		for (String raw : lines) {
			String line = preprocessor.cleanLine(raw);

			// Must end with ';', include '(', and have no '{'
			if (!line.endsWith(";")) continue;
			if (!line.contains("(")) continue;
			if (line.contains("{")) continue;

			// In interfaces, default/static members should have bodies; skip defensively
			if (line.contains(" default ") || line.contains(" static ")) continue;

			Matcher m = ABSTRACT_SIGNATURE_PATTERN.matcher(line);
			if (!m.matches()) continue;

			String returnType = normalizeType(m.group(1).trim());
			String methodName = m.group(2);
			String params     = Optional.ofNullable(m.group(3)).orElse("").trim();

			// Determine explicit visibility if present; otherwise, interfaces default to public
			String visKeyword = null;
			Matcher vm = Pattern.compile("\\b(public|private|protected)\\b").matcher(line);
			if (vm.find()) visKeyword = vm.group(1);
			String visibility = visibilitySymbol(visKeyword == null ? "public" : visKeyword);

			System.out.println("[extractAbstractSignatures] match: " + line);
			System.out.println("[extractAbstractSignatures] name=" + methodName + ", return=" + returnType + ", params=" + params + ", vis=" + visibility);

			// Build method exactly like parseMethod does
			Method method = new Method(methodName, returnType, visibility);

			// Parameters + relationships consistent with your logic
			parseParameters(params, method, classInfo, /*isConstructor*/ false);

			// Return type relationships consistent with your logic
			processReturnType(returnType, classInfo);

			classInfo.addMethod(method);
		}
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
					String type = normalizeType(parts[0]);
					String name = parts[1];

					method.addParameter(type);
					paramMap.put(name, type);

					// Extract main inner type (e.g., Book from List<Book>)
					String inner = extractInnerType(type);
					String cleaned = stripGenerics(inner);

					if (!isConstructor && !isJavaBuiltInType(cleaned)) {
						ClassInfo dependency = getOrCreateClass(cleaned);
						if (dependency != null) {
							System.out.println("[parseParameters] Raw param: " + param);
							System.out.println("[parseParameters] → type: " + type + ", name: " + name);
							System.out.println("[parseParameters] → inner: " + inner + ", cleaned: " + cleaned);

							model.addRelationship(new Relationship(classInfo, dependency, RelationshipType.ASSOCIATION));
						}
					}

					// For constructors, still ensure class exists (as dummy if needed)
					if (isConstructor && !isJavaBuiltInType(cleaned)) {
						System.out.println("[parseParameters] → creating/using class: " + cleaned);

						getOrCreateClass(cleaned);
					}
				}
			}
		}

		return paramMap;
	}

	private void processReturnType(String returnType, ClassInfo classInfo) {
		System.out.println("[processReturnType] Raw returnType: " + returnType);

		if (returnType == null || returnType.equals("void"))
			return;

		Set<String> returnTypes = extractGenericTypesFromTypeSignature(returnType);
		System.out.println("[processReturnType] → extracted types: " + returnTypes);

		for (String type : returnTypes) {

			if (!isJavaBuiltInType(type)) {
				System.out.println("[processReturnType] → creating/using class: " + type);

				ClassInfo dependency = getOrCreateClass(type);
				if (dependency != null) {
					model.addRelationship(new Relationship(classInfo, dependency, RelationshipType.ASSOCIATION));
				}
			}
		}
	}

	// checks method body for local relationships
	private void analyzeMethodBody(String body, ClassInfo classInfo, String methodName, Map<String, String> params) {
		extractNewInstanceDependencies(body, classInfo);
		extractAggregationAssignments(body, classInfo, methodName, params);
		extractCompositionAssignments(body, classInfo);

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
		// Matches types used in 'new Type<...>()'
		Pattern pattern = Pattern.compile("new\\s+([a-zA-Z_][a-zA-Z0-9_<>]*)\\s*\\(");
		Matcher matcher = pattern.matcher(methodBody);

		while (matcher.find()) {
			String rawType = matcher.group(1);

			// Remove generic part, e.g. List<Book> → List
			String cleaned = stripGenerics(rawType);

			// Ignore built-in types like List, Map, etc.
			if (isJavaBuiltInType(cleaned)) {
				continue;
			}

			// Check if it's a field assignment like 'this.field = new Type()'
			int start = matcher.start();
			boolean isFieldAssignment = false;
			if (start >= 6) {
				String before = methodBody.substring(Math.max(0, start - 20), start);
				if (before.contains("this.")) {
					isFieldAssignment = true;
				}
			}

			// Only treat as dependency if it's not a field assignment (those are
			// compositions)
			if (!isFieldAssignment) {
				// Safely create or retrieve the target class
				ClassInfo targetClass = getOrCreateClass(cleaned);
				if (targetClass != null) {
					Relationship relationship = new Relationship(currentClass, targetClass,
							RelationshipType.ASSOCIATION);
					model.addRelationship(relationship);
				}
			}
		}
	}

	// Extracts all method declarations (with full bodies) from class lines
	private void extractMethods(List<String> lines, ClassInfo currentClass) {
		boolean collectingHeader = false;
		boolean insideMethod = false;
		int openBraces = 0;

		StringBuilder headerBuffer = new StringBuilder();
		StringBuilder methodBuffer = new StringBuilder();

		for (String line : lines) {
			System.out.println("[extractMethods] Line: " + line);

			if (!insideMethod) {

				if (!collectingHeader) {
					// Look for start of method signature (must include a visibility keyword and "("
					// )
					if (METHOD_HEADER_PATTERN.matcher(line).matches()) {
						System.out.println("[extractMethods] → Start collecting method header: " + line);

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
								System.out.println("[extractMethods] → Passing to parseMethod(...)");

								parseMethod(methodBuffer.toString(), currentClass);
								insideMethod = false;
							}
						} else {
							collectingHeader = true;
						}
					}
				} else {
					System.out.println("[extractMethods] → Continuing header: " + line);

					headerBuffer.append(line).append(" ");
					if (line.contains(")")) {
						System.out.println("[extractMethods] → Method signature complete:");
						System.out.println(headerBuffer.toString().trim());

						collectingHeader = false;
						insideMethod = true;
						openBraces = countOccurrences(line, '{') - countOccurrences(line, '}');
						methodBuffer.setLength(0);
						methodBuffer.append(headerBuffer.toString().trim()).append("\n");

						if (openBraces == 0 && !line.contains("{")) {
							continue;
						}

						if (openBraces == 0) {
							System.out.println("[extractMethods] → Passing to parseMethod(...)");
							parseMethod(methodBuffer.toString(), currentClass);
							insideMethod = false;

						}
					}
				}
			} else {
				System.out.println("[extractMethods] → Inside method body line: " + line);
				methodBuffer.append(line).append("\n");
				openBraces += countOccurrences(line, '{') - countOccurrences(line, '}');

				if (openBraces <= 0) {
					System.out.println("[extractMethods] → Full method block:");
					System.out.println(methodBuffer.toString());

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

	// Detects assignments like 'this.field = new Type()' and adds COMPOSITION
	// relationships
	private void extractCompositionAssignments(String methodBody, ClassInfo currentClass) {
		// Match expressions like: this.field = new Type(...)
		Pattern pattern = Pattern.compile("this\\.\\w+\\s*=\\s*new\\s+([a-zA-Z_][a-zA-Z0-9_<>]*)\\s*\\(");
		Matcher matcher = pattern.matcher(methodBody);

		while (matcher.find()) {
			String rawType = matcher.group(1);

			// Remove generic part from the type (e.g. List<Book> → List)
			String cleaned = stripGenerics(rawType);

			// Skip built-in Java types
			if (isJavaBuiltInType(cleaned)) {
				continue;
			}

			// Safely get or create the referenced class
			ClassInfo targetClass = getOrCreateClass(cleaned);
			if (targetClass != null) {
				Relationship relationship = new Relationship(currentClass, targetClass, RelationshipType.COMPOSITION);
				model.addRelationship(relationship);
			}
		}
	}

	// Detects assignments like 'this.field = parameter;' inside constructors or
	// setters
	// and adds AGGREGATION relationships based on parameter types
	private void extractAggregationAssignments(String methodBody, ClassInfo currentClass, String methodName,
			Map<String, String> paramNamesAndTypes) {

// Only apply this logic in constructors or simple setters
		boolean isConstructor = methodName.equals(currentClass.getName());
		boolean isSetter = methodName.startsWith("set") && paramNamesAndTypes.size() == 1;

		if (!(isConstructor || isSetter)) {
			System.out.println("not a Constrructor/Setter: " + methodName);
			return;
		}

		System.out.println("[aggregation] Total parameters: " + paramNamesAndTypes.size());

		for (Map.Entry<String, String> param : paramNamesAndTypes.entrySet()) {
			String paramName = param.getKey();
			String paramType = param.getValue();
			System.out.println("[aggregation] paramName: '" + paramName + "' , paramType: '" + paramType + "'");

// Extract all relevant types from the parameter type (e.g., List<Book> → [List, Book])
			Set<String> types = extractGenericTypes(paramType);

			for (String raw : types) {
				String cleaned = stripGenerics(raw);

// Skip Java built-in types like List, Map, etc.
				if (isJavaBuiltInType(cleaned)) {
					System.out.println("[aggregation] Skipping built-in type: " + cleaned);
					continue;
				}

// Prepare to match lines like: this.books = books;
				String patternString = "\\bthis\\.[a-zA-Z0-9_]+\\s*=\\s*" + Pattern.quote(paramName) + "\\s*;";
				String normalizedBody = methodBody.replaceAll("\\s+", " ");

				System.out.println("[aggregation] normalizedBody: " + normalizedBody);
				System.out.println("[aggregation] pattern: " + patternString);
				System.out.println("[aggregation] READY TO MATCH param: " + paramName);

				Matcher matcher = Pattern.compile(patternString).matcher(normalizedBody);

				if (matcher.find()) {
					System.out.println("[aggregation] MATCH FOUND for param: " + paramName);
					ClassInfo target = getOrCreateClass(cleaned);
					if (target != null) {
						Relationship rel = new Relationship(currentClass, target, RelationshipType.AGGREGATION);
						model.addRelationship(rel);
					}
				} else {
					System.out.println("[aggregation] NO MATCH for param: " + paramName);
				}

				System.out.println("[aggregation] methodName: " + methodName);
				System.out.println("[aggregation] parameters passed in map: " + paramNamesAndTypes);
				System.out.println("[aggregation] paramType: '" + paramType + "'");
				System.out.println("[aggregation] methodBody:\n" + methodBody);
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
}
