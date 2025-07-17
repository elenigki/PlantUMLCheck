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

				// Read body of the class
				List<String> bodyLines = new ArrayList<>();
				int braceCount = countBraces(line);
				i++;

				while (i < rawLines.size() && braceCount > 0) {
					String bodyLine = cleanLine(rawLines.get(i));
					bodyLines.add(bodyLine);
					braceCount += countBraces(bodyLine);
					i++;
				}

				parseAttributes(bodyLines, classInfo);
				extractMethods(bodyLines, classInfo);
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
			c.setClassType(type);
			c.setAbstract(isAbstract);
			model.removeWarningsForClass(name);

			return c;
		} else {
			ClassInfo c = new ClassInfo(name, type, isAbstract);
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

	// Returns ClassInfo from model or creates new (unless it's a built-in Java
	// type)
	private ClassInfo getOrCreateClass(String className) {
		if (isJavaBuiltInType(className)) {
			return null; // Ignore built-in Java types
		}

		for (ClassInfo c : model.getClasses()) {
			if (c.getName().equals(className)) {
				return c;
			}
		}

		ClassInfo dummy = new ClassInfo(className, ClassType.CLASS);
		System.out.println("Getting or creating class: " + className);

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
					    if (!isJavaBuiltInType(type)) {
					        ClassInfo target = getOrCreateClass(type);
					        if (target != null) {
					            boolean isInComposedStructure = isMainComposed && declared.contains(type);

					            RelationshipType relType = isInComposedStructure
					                ? RelationshipType.COMPOSITION
					                : RelationshipType.ASSOCIATION;

					            System.out.println("[ATTR] type=" + type + " | declared=" + declared +
					                " | isComposedStructure=" + isInComposedStructure);

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

	private static final Pattern METHOD_PATTERN = Pattern.compile("^(public|protected|private)?\\s*" + // visibility
			"(static\\s+)?(final\\s+)?(abstract\\s+)?" + // optional modifiers
			"([\\w<>\\[\\]]+)\\s+" + // return type
			"(\\w+)\\s*\\(([^)]*)\\)\\s*\\{?" // method name and params
	);



	// Checks if a type is a known Java built-in type
	private boolean isJavaBuiltInType(String typeName) {
		return List.of("int", "long", "double", "float", "char", "byte", "short", "boolean", "void", "Integer", "Long",
				"Double", "Float", "Character", "Byte", "Short", "Boolean", "String", "Object", "List", "Map", "Set",
				"ArrayList", "HashMap", "HashSet").contains(typeName);
	}


	// Parses a full method body string into a Method object
	private Method parseMethod(String methodBody, ClassInfo classInfo) {
		String[] lines = methodBody.split("\\n");
		if (lines.length == 0)
			return null;

		String firstLine = lines[0];
		Matcher matcher = METHOD_PATTERN.matcher(firstLine);
		if (!matcher.find())
			return null;

		String visibilityKeyword = matcher.group(1);
		String returnType = matcher.group(5);
		String methodName = matcher.group(6);
		String paramList = matcher.group(7);

		boolean isConstructor = isConstructor(methodName, classInfo);

		String visibility = visibilitySymbol(visibilityKeyword != null ? visibilityKeyword : "");
		Method method = new Method(methodName, returnType, visibility);

		// parse parameters and collect their names and types
		Map<String, String> paramNamesAndTypes = parseParameters(paramList, method, classInfo, isConstructor);

		// return type is used only for normal methods
		if (!isConstructor) {
			processReturnType(returnType, classInfo);
		}

		// analyze body for relationships
		analyzeMethodBody(methodBody, classInfo, methodName, paramNamesAndTypes);

		// add method only if it's not a constructor
		if (!isConstructor) {
			classInfo.addMethod(method);
		}

		return method;
	}

	// checks if method name equals class name
	private boolean isConstructor(String methodName, ClassInfo classInfo) {
		return methodName.equals(classInfo.getName());
	}

	// parses method parameters and adds dependencies (if not constructor)
	private Map<String, String> parseParameters(String paramList, Method method, ClassInfo classInfo,
			boolean isConstructor) {
		Map<String, String> paramMap = new HashMap<>();

		if (paramList != null && !paramList.isBlank()) {
			String[] params = paramList.split(",");
			for (String param : params) {
				String[] parts = param.trim().split("\\s+");
				if (parts.length >= 2) {
					String type = parts[0];
					String name = parts[1];

					method.addParameter(type);
					paramMap.put(name, type);

					String inner = extractInnerType(type);
					if (!isConstructor) {
						ClassInfo dependency = getOrCreateClass(inner);
						if (dependency != null) {
							model.addRelationship(new Relationship(classInfo, dependency, RelationshipType.DEPENDENCY));
						}
					} else {
						getOrCreateClass(inner); // only add dummy if needed
					}
				}
			}
		}

		return paramMap;
	}

	// adds dependency from return type (if not void)
	private void processReturnType(String returnType, ClassInfo classInfo) {
		if (returnType != null && !returnType.equals("void")) {
			String returnInner = extractInnerType(returnType);
			ClassInfo returnDependency = getOrCreateClass(returnInner);
			if (returnDependency != null) {
				model.addRelationship(new Relationship(classInfo, returnDependency, RelationshipType.DEPENDENCY));
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
		System.out.println("Return type inner: " + type);

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
					targetClass = new ClassInfo(cleanType, ClassType.CLASS);
					model.addClass(targetClass);
					model.addWarning("Class '" + cleanType + "' not found in source files. Added as dummy.");

				}

				Relationship relationship = new Relationship(currentClass, targetClass, RelationshipType.DEPENDENCY);
				model.addRelationship(relationship);
			}
		}
	}

	// Extracts all method declarations (with full bodies) from class lines
	private void extractMethods(List<String> lines, ClassInfo currentClass) {
		boolean insideMethod = false;
		int openBraces = 0;
		StringBuilder methodBuffer = new StringBuilder();

		for (String line : lines) {
			if (!insideMethod) {
				if (METHOD_PATTERN.matcher(line).find() && line.contains("{")) {
					insideMethod = true;
					openBraces = countOccurrences(line, '{') - countOccurrences(line, '}');
					methodBuffer.setLength(0);
					methodBuffer.append(line).append("\n");
				}
			} else {
				methodBuffer.append(line).append("\n");
				openBraces += countOccurrences(line, '{') - countOccurrences(line, '}');

				if (openBraces == 0) {
					insideMethod = false;
					String methodBody = methodBuffer.toString();
					parseMethod(methodBody, currentClass);
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
				targetClass = new ClassInfo(cleanType, ClassType.CLASS);
				model.addClass(targetClass);
				model.addWarning("Class '" + cleanType + "' not found in source files. Added as dummy.");
			}

			Relationship relationship = new Relationship(currentClass, targetClass, RelationshipType.COMPOSITION);
			model.addRelationship(relationship);
		}
	}

	// Detects 'this.x = x;' assignments and adds AGGREGATION if x is a method
	// parameter
	// Only in constructors or setters
	private void extractAggregationAssignments(String methodBody, ClassInfo currentClass, String methodName,
			Map<String, String> paramNamesAndTypes) {
		boolean isConstructor = methodName.equals(currentClass.getName());
		boolean isSetter = methodName.startsWith("set");

		if (!(isConstructor || isSetter)) {
			return;
		}

		for (Map.Entry<String, String> param : paramNamesAndTypes.entrySet()) {
			String paramName = param.getKey();
			String paramType = param.getValue();

			String patternString = "this\\." + Pattern.quote(paramName) + "\\s*=\\s*" + Pattern.quote(paramName)
					+ "\\s*;";
			Pattern pattern = Pattern.compile(patternString);
			Matcher matcher = pattern.matcher(methodBody);

			if (matcher.find() && !isJavaBuiltInType(paramType)) {
				ClassInfo target = model.findClassByName(paramType);
				if (target == null) {
					target = new ClassInfo(paramType, ClassType.CLASS);
					model.addClass(target);
					model.addWarning("Class '" + paramType + "' not found in source files. Added as dummy.");
				}

				Relationship rel = new Relationship(currentClass, target, RelationshipType.AGGREGATION);
				model.addRelationship(rel);
			}
		}
	}

	// Checks if an attribute includes `= new ...` → composition if it's main type or a collection
	private boolean isComposedAttribute(String line, String declaredType) {
	    if (line.contains("=") && line.contains("new")) {
	        Matcher matcher = Pattern.compile("=\\s*new\\s+([a-zA-Z_][a-zA-Z0-9_<>]*)").matcher(line);
	        if (matcher.find()) {
	            String constructedType = stripGenerics(matcher.group(1));
	            String declaredMain = stripGenerics(declaredType);

	            System.out.println("[CHECK] declared=" + declaredMain + " | constructed=" + constructedType);

	            if (constructedType.equals(declaredMain)) {
	                System.out.println("[MATCH] direct match for composition");
	                return true;
	            }

	            if (isCollectionType(declaredMain) && isCollectionType(constructedType)) {
	                System.out.println("[MATCH] collection-to-collection composition");
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
