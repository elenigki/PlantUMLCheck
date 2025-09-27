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
	private final boolean enableMultiCommand = false; // gate for experimental splitting

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

		for (int i = 0; i < rawLines.size(); i++) {
			String line = preprocessor.cleanLine(rawLines.get(i)); // clean
			if (line.isEmpty())
				continue;

			if (isClassDeclaration(line)) {
				ClassInfo classInfo = parseClassInfo(line); // header
				if (classInfo == null)
					continue;

				// collect body (handles inline "{...}" too)
				List<String> bodyLines = new ArrayList<>();
				int braceCount = countBraces(line);
				int openPos = line.indexOf('{'), closePos = line.lastIndexOf('}');
				if (braceCount == 0 && openPos >= 0 && closePos > openPos) { // inline body
					bodyLines.add(line.substring(openPos + 1, closePos));
				} else {
					i++;
					_ScanState st = new _ScanState(); // comment/string-aware state
					while (i < rawLines.size() && braceCount > 0) {
						String bodyLine = rawLines.get(i);
						bodyLines.add(bodyLine);
						braceCount += braceDeltaIgnoringComments(bodyLine, st);
						i++;
					}
				}

				// --- ENUM CONSTANTS as attributes (before any body normalization) ---

				String enumBody = String.join("\n", bodyLines);
				enumBody = enumBody.replaceFirst("\\s*\\}\\s*$", "");
				if (classInfo.getClassType() == ClassType.ENUM) {
				    addEnumConstantsToClassInfo(enumBody, classInfo);
				}


				// decide per-class if we need multi-command normalization
				boolean needMulti = enableMultiCommand && preprocessor.detectMultiCommand(bodyLines);

				// --- pipeline with debug prints ---
				// (A) optional: very conservative pre-split only when needed
				List<String> bodyStage = needMulti ? preprocessor.preSplitLight(bodyLines) : bodyLines;

				List<String> noComments = preprocessor.cleanComments(bodyStage);
				System.out.println("noComments (" + classInfo.getName() + "):");
				noComments.forEach(s -> System.out.println("  · " + s));

				// (B) optional top-level per-line split (safe) only when needed
				List<String> preExpanded = needMulti ? preprocessor.splitTopLevelPerLine(noComments) : noComments;
				System.out.println("preExpanded (" + classInfo.getName() + "):");
				preExpanded.forEach(s -> System.out.println("  · " + s));

				List<String> logicalLines = preprocessor.joinLogicalLines(preExpanded);
				System.out.println("logicalLines (" + classInfo.getName() + "):");
				logicalLines.forEach(s -> System.out.println("  · " + s));

				List<String> expanded = needMulti ? preprocessor.splitTopLevelPerLine(logicalLines) : logicalLines;
				System.out.println("expanded (" + classInfo.getName() + "):");
				expanded.forEach(s -> System.out.println("  · " + s));

				// (C) optional header/body normalizers only when needed
				List<String> normalized = needMulti ? preprocessor.separateMemberHeaders(expanded) : expanded;
				System.out.println("normalized (" + classInfo.getName() + "):");
				normalized.forEach(s -> System.out.println("  · " + s));

				List<String> methodSplit = needMulti ? preprocessor.splitMethodBodies(normalized) : normalized;
				System.out.println("methodSplit (" + classInfo.getName() + "):");
				methodSplit.forEach(s -> System.out.println("  · " + s));

				// List<String> completed = needMulti ?
				// preprocessor.closeDanglingMethodHeaders(methodSplit) : methodSplit;
				List<String> completed = methodSplit;
				System.out.println("completed (" + classInfo.getName() + "):");
				completed.forEach(s -> System.out.println("  · " + s));

				// (D) legacy splitter always last
				List<String> splitLines = preprocessor.splitMultipleStatements(completed);
				System.out.println("splitLines FINAL (" + classInfo.getName() + "):");
				splitLines.forEach(s -> System.out.println("  · " + s));

				System.out.println("needMulti=" + needMulti + "  enableMultiCommand=" + enableMultiCommand);
				System.out.println("--------------------------------------------");

				// parse
				parseAttributes(splitLines, classInfo);
				extractAbstractSignatures(splitLines, classInfo);
				extractMethods(splitLines, classInfo);

				for (Relationship r : model.getRelationships()) {
					if (r.getSourceClass().getClassType() == ClassType.ENUM
							|| r.getTargetClass().getClassType() == ClassType.ENUM) {
						r.setType(RelationshipType.ASSOCIATION);
					}
				}

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

		String cleaned = stripGenerics(className);
		// Ignore built-in Java types
		if (isJavaBuiltInType(cleaned)) {
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
		Pattern pattern = Pattern.compile("^\\s*(private|protected|public)\\s*" // 1: visibility
				+ "((?:\\w+\\s+)*)" // 2: mods chunk (could contain 'static', or be empty)
				+ "([\\w<>,\\s\\[\\]]+)\\s+" // 3: type
				+ "(\\w+)\\s*(=.*)?;\\s*$" // 4: name
		);

		Matcher matcher = pattern.matcher(line);

		if (!matcher.find()) {
			return null;
		}

		String visibility = matcher.group(1);
		String modsRaw = matcher.group(2); // could be "", "static ", "final static ", etc.
		String type = matcher.group(3).trim();
		String name = matcher.group(4).trim();

		Attribute attr = new Attribute(name, normalizeType(type), visibilitySymbol(visibility));
		attr.setStatic(hasStatic(modsRaw));
		return attr;
	}

	// Parses a list of lines for attributes
	private void parseAttributes(List<String> lines, ClassInfo classInfo) {
		for (String line : lines) {
			line = preprocessor.cleanLine(line);
			if (isAttributeDeclaration(line)) {
				Attribute attr = parseAttribute(line);
				if (attr != null) {
					classInfo.addAttribute(attr);

					Set<String> types = extractGenericTypes(attr.getType());

					boolean isMainComposed = isComposedAttribute(line, attr.getType());

					if (attr.isStatic()) {
						isMainComposed = false; // static fields never become composition
					}

					String declared = attr.getType();

					for (String type : types) {
						if (type == null || type.isBlank())
							continue;
						String cleaned = stripGenerics(type);

						if (!isJavaBuiltInType(cleaned)) {
							ClassInfo target = getOrCreateClass(cleaned);
							if (target != null) {
								boolean isInComposedStructure = isMainComposed && declared.contains(cleaned);

								// Enums must never produce COMPOSITION/AGGREGATION → force ASSOCIATION
								boolean enumInvolved = classInfo.getClassType() == ClassType.ENUM
										|| target.getClassType() == ClassType.ENUM;
								RelationshipType relType = enumInvolved ? RelationshipType.ASSOCIATION
										: (isInComposedStructure ? RelationshipType.COMPOSITION
												: RelationshipType.ASSOCIATION);

								model.addRelationship(new Relationship(classInfo, target, relType));
							}
						}
					}

				}
			}
		}
	}

	// Collect enum constants and store them in ClassInfo.enumConstants (not as attributes)
	private static void addEnumConstantsToClassInfo(String enumBody, ClassInfo enumInfo) {
	    String header = enumConstantsHeader(enumBody);
	    if (header == null || header.isBlank()) return;

	    for (String token : splitTopLevelByComma(header)) {
	        String name = enumConstantName(token);
	        if (name == null) continue;
	        enumInfo.addEnumConstants(name); // <<— uses your new API
	    }
	}


	// ==========================
	// Method extraction patterns
	// ==========================

	private static final Pattern METHOD_PATTERN = Pattern.compile("(public|protected|private)?\\s*" // (1)
			+ "((?:\\w+\\s+)*)\\s*" // (2) mods chunk (we'll only look for 'static')
			+ "([\\w\\<\\>\\[\\]\\,\\s]+)\\s+" // (3) return type
			+ "(\\w+)\\s*\\(([^)]*)\\)\\s*" // (4) name, (5) params
	);

	private static final Pattern METHOD_HEADER_PATTERN = Pattern
			.compile("\\b(public|private|protected)\\b.*\\(.*\\)\\s*(\\{)?\\s*$");

	// NEW: very narrow pattern for ';'-terminated abstract/interface signatures
	private static final Pattern ABSTRACT_SIGNATURE_PATTERN = Pattern.compile("^\\s*(?:@[\\w.]+\\s*)*" + // annotations
																											// (optional)
			"(?:public|protected|private)?\\s*" + // visibility (optional)
			"(?:(?:abstract|default|static|strictfp)\\s+)*" + // modifiers (optional)
			"([\\w\\<\\>\\[\\]\\.,\\s]+)\\s+" + // (1) return type
			"(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)\\s*" + // (2) name
			"\\(([^)]*)\\)\\s*" + // (3) params
			"(?:throws\\s+[\\w\\.,\\s]+)?\\s*;" // optional throws, then ';'
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

		// Match method signature
		Matcher matcher = METHOD_PATTERN.matcher(headerLine);
		if (!matcher.find()) {
			return null;
		}

		String visibilityKeyword = matcher.group(1);
		String modsRaw = matcher.group(2);
		String returnType = normalizeType(matcher.group(3));
		String methodName = matcher.group(4);
		String paramList = matcher.group(5);

		// === NEW: completely ignore main method ===
		if ("main".equals(methodName) && "void".equals(returnType)) {
			return null;
		}

		boolean isConstructor = isConstructor(methodName, classInfo);
		String visibility = visibilitySymbol(visibilityKeyword != null ? visibilityKeyword : "");
		Method method = new Method(methodName, returnType, visibility);
		method.setStatic(hasStatic(modsRaw));

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

	// NEW: picks up ';'-terminated interface method signatures (no body)
	private void extractAbstractSignatures(List<String> lines, ClassInfo classInfo) {
		// Only consider interface members here to keep pattern scope tight
		if (classInfo == null || classInfo.getClassType() != ClassType.INTERFACE) {
			return;
		}

		for (String raw : lines) {
			String line = preprocessor.cleanLine(raw);

			// Must end with ';', include '(', and have no '{'
			if (!line.endsWith(";"))
				continue;
			if (!line.contains("("))
				continue;
			if (line.contains("{"))
				continue;

			// In interfaces, default/static members should have bodies; skip defensively
			if (line.contains(" default ") || line.contains(" static "))
				continue;

			Matcher m = ABSTRACT_SIGNATURE_PATTERN.matcher(line);
			if (!m.matches())
				continue;

			String returnType = normalizeType(m.group(1).trim());
			String methodName = m.group(2);
			String params = Optional.ofNullable(m.group(3)).orElse("").trim();

			// Determine explicit visibility if present; otherwise, interfaces default to
			// public
			String visKeyword = null;
			Matcher vm = Pattern.compile("\\b(public|private|protected)\\b").matcher(line);
			if (vm.find())
				visKeyword = vm.group(1);
			String visibility = visibilitySymbol(visKeyword == null ? "public" : visKeyword);

			// Build method exactly like parseMethod does
			Method method = new Method(methodName, returnType, visibility);

			// Parameters + relationships consistent with your logic
			parseParameters(params, method, classInfo, /* isConstructor */ false);

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

							model.addRelationship(
									new Relationship(classInfo, dependency, RelationshipType.ASSOCIATION));
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

			if (!insideMethod) {

				if (!collectingHeader) {
					// Look for start of method signature (must include a visibility keyword and "("
					// )
					if (METHOD_HEADER_PATTERN.matcher(line).matches()) {

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

	// Detects "this.field = new Type(...)" and adds COMPOSITION unless the field is
	// static
	private void extractCompositionAssignments(String methodBody, ClassInfo currentClass) {
		Pattern pattern = Pattern.compile("this\\.(\\w+)\\s*=\\s*new\\s+([a-zA-Z_][a-zA-Z0-9_<>]*)\\s*\\(");
		Matcher matcher = pattern.matcher(methodBody);

		while (matcher.find()) {
			String fieldName = matcher.group(1);
			String rawType = matcher.group(2);
			String cleaned = stripGenerics(rawType); // e.g. List<Book> → List

			if (isJavaBuiltInType(cleaned))
				continue;

			Attribute fld = findAttributeByName(currentClass, fieldName);
			ClassInfo targetClass = getOrCreateClass(cleaned);
			if (targetClass == null)
				continue;

			RelationshipType rt = (fld != null && fld.isStatic()) ? RelationshipType.ASSOCIATION // static fields never
																									// imply composition
					: RelationshipType.COMPOSITION;
			// Enums: always ASSOCIATION (no composition with enums)
			if (currentClass.getClassType() == ClassType.ENUM
					|| (targetClass != null && targetClass.getClassType() == ClassType.ENUM)) {
				rt = RelationshipType.ASSOCIATION;
			}

			model.addRelationship(new Relationship(currentClass, targetClass, rt));
		}
	}

	// Detects "this.field = <param>;" inside constructors or simple setters and
	// adds AGGREGATION unless the field is static
	private void extractAggregationAssignments(String methodBody, ClassInfo currentClass, String methodName,
			Map<String, String> paramNamesAndTypes) {

		boolean isConstructor = methodName.equals(currentClass.getName());
		boolean isSetter = methodName.startsWith("set") && paramNamesAndTypes.size() == 1;
		if (!(isConstructor || isSetter))
			return;

		String normalizedBody = methodBody.replaceAll("\\s+", " ");

		for (Map.Entry<String, String> param : paramNamesAndTypes.entrySet()) {
			String paramName = param.getKey();
			String paramType = param.getValue();

			// e.g., List<Book> → [List, Book]
			Set<String> types = extractGenericTypes(paramType);
			for (String raw : types) {
				String cleaned = stripGenerics(raw);
				if (isJavaBuiltInType(cleaned))
					continue;

				ClassInfo target = getOrCreateClass(cleaned);
				if (target == null)
					continue;

				// capture the field name; scan ALL matches, not just the first
				Pattern aggPattern = Pattern.compile("\\bthis\\.(\\w+)\\s*=\\s*" + Pattern.quote(paramName) + "\\s*;");
				Matcher m = aggPattern.matcher(normalizedBody);
				while (m.find()) {
					String fieldName = m.group(1);
					Attribute fld = findAttributeByName(currentClass, fieldName);

					RelationshipType rt = (fld != null && fld.isStatic()) ? RelationshipType.ASSOCIATION // static
																											// fields
																											// never
																											// imply
																											// aggregation
							: RelationshipType.AGGREGATION;
					// Enums: always ASSOCIATION (no aggregation with enums)
					if (currentClass.getClassType() == ClassType.ENUM
							|| (target != null && target.getClassType() == ClassType.ENUM)) {
						rt = RelationshipType.ASSOCIATION;
					}

					model.addRelationship(new Relationship(currentClass, target, rt));
				}
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

	// comment/string-aware scan state
	private static final class _ScanState {
		boolean inBlock = false, inStr = false, inChar = false, esc = false;
	}

	// net { } delta for one line, ignoring /*...*/, //..., "..." and '...'
	private static int braceDeltaIgnoringComments(String s, _ScanState st) {
		if (s == null)
			return 0;
		int delta = 0;
		for (int i = 0, n = s.length(); i < n; i++) {
			char c = s.charAt(i), nx = (i + 1 < n) ? s.charAt(i + 1) : '\0';

			if (st.inBlock) {
				if (c == '*' && nx == '/') {
					st.inBlock = false;
					i++;
				}
				continue;
			}
			if (!st.inStr && !st.inChar && c == '/' && nx == '*') {
				st.inBlock = true;
				i++;
				continue;
			}
			if (!st.inStr && !st.inChar && c == '/' && nx == '/')
				break;

			if (!st.inChar && c == '"' && !st.inStr) {
				st.inStr = true;
				st.esc = false;
				continue;
			}
			if (st.inStr) {
				if (st.esc)
					st.esc = false;
				else if (c == '\\')
					st.esc = true;
				else if (c == '"')
					st.inStr = false;
				continue;
			}

			if (!st.inStr && c == '\'' && !st.inChar) {
				st.inChar = true;
				st.esc = false;
				continue;
			}
			if (st.inChar) {
				if (st.esc)
					st.esc = false;
				else if (c == '\\')
					st.esc = true;
				else if (c == '\'')
					st.inChar = false;
				continue;
			}

			if (c == '{')
				delta++;
			else if (c == '}')
				delta--;
		}
		return delta;
	}

	// Finds an attribute by simple name in the current class
	private Attribute findAttributeByName(ClassInfo clazz, String name) {
		for (Attribute a : clazz.getAttributes()) {
			if (a.getName().equals(name))
				return a;
		}
		return null;
	}

	// Returns the substring of the enum body up to the first top-level ';' (or
	// whole body if none)
	private static String enumConstantsHeader(String enumBody) {
		int depthPar = 0, depthBr = 0;
		for (int i = 0; i < enumBody.length(); i++) {
			char c = enumBody.charAt(i);
			if (c == '(')
				depthPar++;
			else if (c == ')')
				depthPar--;
			else if (c == '{')
				depthBr++;
			else if (c == '}')
				depthBr--;
			else if (c == ';' && depthPar == 0 && depthBr == 0) {
				return enumBody.substring(0, i);
			}
		}
		return enumBody; // no ';' → constants-only enum
	}

	// Split by commas at top-level (ignoring commas inside (...) or {...})
	private static List<String> splitTopLevelByComma(String s) {
		List<String> parts = new ArrayList<>();
		int depthPar = 0, depthBr = 0, start = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '(')
				depthPar++;
			else if (c == ')')
				depthPar--;
			else if (c == '{')
				depthBr++;
			else if (c == '}')
				depthBr--;
			else if (c == ',' && depthPar == 0 && depthBr == 0) {
				parts.add(s.substring(start, i).trim());
				start = i + 1;
			}
		}
		String last = s.substring(start).trim();
		if (!last.isEmpty())
			parts.add(last);
		return parts;
	}

	// Extract the constant name from a token like: "NEW", "PAID(1)", "SHIP { ... }"
	private static String enumConstantName(String token) {
		String t = token.trim();
		if (t.isEmpty())
			return null;
		// name ends before first '(' or '{'
		int cut = t.length();
		int p = t.indexOf('(');
		if (p >= 0)
			cut = Math.min(cut, p);
		int b = t.indexOf('{');
		if (b >= 0)
			cut = Math.min(cut, b);
		// also guard if there is trailing comment or spaces
		String name = t.substring(0, cut).trim();
		// remove optional trailing comma remnants or semicolons if any
		if (name.endsWith(",") || name.endsWith(";"))
			name = name.substring(0, name.length() - 1).trim();
		// basic validity: Java identifier (simplified)
		return name.matches("[A-Za-z_$][A-Za-z0-9_$]*") ? name : null;
	}

}
