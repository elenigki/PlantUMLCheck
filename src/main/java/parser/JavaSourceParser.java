package parser;

import model.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class JavaSourceParser {

	private static final Set<String> IGNORED_TYPES = Set.of(
			"int", "long", "double", "float", "boolean", "char", "byte", "short",
			"void", "String", "List", "Map", "Set", "Object"
	);

	private boolean isIgnoredType(String typeName) {
		return IGNORED_TYPES.contains(typeName);
	}

	
	// Entry point: Parses a single Java file and returns its model
	public IntermediateModel parse(File file) throws IOException {
	    List<String> rawLines = readLines(file);
	    List<String> lines = cleanComments(rawLines);

	    IntermediateModel model = parseSingleClass(lines);
	    detectDependencies(model);
	    return model;
	}


    
    // Parses multiple selected Java files and returns a complete model
    public IntermediateModel parse(List<ScannedJavaInfo> selectedClasses) throws IOException {
        IntermediateModel model = new IntermediateModel();
        for (ScannedJavaInfo info : selectedClasses) {
            File file = info.getSourceFile();
    	    List<String> rawLines = readLines(file);
    	    List<String> lines = cleanComments(rawLines);
        	IntermediateModel singleModel = parseSingleClass(lines);
            model.getClasses().addAll(singleModel.getClasses());
            model.getRelationships().addAll(singleModel.getRelationships());
        }
        detectDependencies(model);
        return model;
    }

    // Reads all lines from input
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
    
    // CLeans the code lines from any kind of comments
    private List<String> cleanComments(List<String> lines) {
        List<String> result = new ArrayList<>();
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Inside multi-line block comment
            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            // Start of block comment
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }

            // Inline block comment on the same line
            if (trimmed.contains("/*") && trimmed.contains("*/")) {
                trimmed = trimmed.replaceAll("/\\*.*?\\*/", "").trim();
            }

            // Remove inline `//` comment
            int index = trimmed.indexOf("//");
            if (index != -1) {
                trimmed = trimmed.substring(0, index).trim();
            }

            // Skip empty or comment-only lines
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }

        return result;
    }

    // Parses a single class/interface/enum
    private IntermediateModel parseSingleClass(List<String> lines) {
        IntermediateModel model = new IntermediateModel();

        // Extract class/interface/enum declaration
        ClassInfo classInfo = extractClassHeader(lines);

        if (classInfo != null) {
            model.addClass(classInfo);
            // Extract relationships (extends/implements)
            extractInheritanceAndInterfaces(lines, model, classInfo);
            // extractAttributes(lines, classInfo);
            extractAttributes(lines, classInfo);
            // extractMethods(lines, classInfo);
            extractMethods(lines, classInfo);
        }
        return model;
    }
    
    // Extracts the main type (class/interface/enum) declaration
	private ClassInfo extractClassHeader(List<String> lines) {
        String className = null;
        boolean isAbstract = false;
        ClassType classType = null;

        Pattern classPattern = Pattern.compile("(public\\s+)?(abstract\\s+)?class\\s+(\\w+)");
        Pattern interfacePattern = Pattern.compile("(public\\s+)?interface\\s+(\\w+)");
        Pattern enumPattern = Pattern.compile("(public\\s+)?enum\\s+(\\w+)");

        for (String line : lines) {
            Matcher classMatcher = classPattern.matcher(line);
            Matcher interfaceMatcher = interfacePattern.matcher(line);
            Matcher enumMatcher = enumPattern.matcher(line);

            if (classMatcher.find()) {
                className = classMatcher.group(3);
                isAbstract = classMatcher.group(2) != null;
                classType = ClassType.CLASS;
                break;
            } else if (interfaceMatcher.find()) {
                className = interfaceMatcher.group(2);
                isAbstract = true;
                classType = ClassType.INTERFACE;
                break;
            } else if (enumMatcher.find()) {
                className = enumMatcher.group(2);
                classType = ClassType.ENUM;
                break;
            }
        }
        
        if (className != null && classType != null) {
            return new ClassInfo(className, classType, isAbstract);
        }
        return null;
    }

    
	// Finds and adds extends and implements relationships
    private void extractInheritanceAndInterfaces(List<String> lines, IntermediateModel model, ClassInfo sourceClass) {
        Pattern extendsPattern = Pattern.compile("extends\\s+(\\w+)");
        Pattern implementsPattern = Pattern.compile("implements\\s+([\\w\\s,<>]+)");
        
        for (String line : lines) {
            Matcher extendsMatcher = extendsPattern.matcher(line);
            Matcher implementsMatcher = implementsPattern.matcher(line);

            if (extendsMatcher.find()) {
                String superClass = extendsMatcher.group(1);
                ClassInfo target = model.findClassByName(superClass);
                if (target == null) {
                    target = new ClassInfo(superClass, ClassType.CLASS);
                    model.addClass(target);
                }

                model.addRelationship(new Relationship(sourceClass, target, RelationshipType.GENERALIZATION));
            }

            if (implementsMatcher.find()) {
                String interfaces = implementsMatcher.group(1);
                String[] parts = interfaces.split(",");
                for (String iface : parts) {
                    String ifaceName = iface.trim();
                    ClassInfo target = new ClassInfo(ifaceName, ClassType.INTERFACE);
                    model.addClass(target);
                    model.addRelationship(new Relationship(sourceClass, target, RelationshipType.REALIZATION));
                }
            }
        }
    }
    
    // Parses and adds class attributes (fields)
	private void extractAttributes(List<String> lines, ClassInfo sourceClass) {
		Pattern attributePattern = Pattern.compile("(public|private|protected)\\s+([\\w<>]+)\\s+(\\w+)\\s*;");
		for (String line : lines) {
	        Matcher matcher = attributePattern.matcher(line);
	        if (matcher.find()) {
	            String visibility = matcher.group(1);
	            String type = matcher.group(2);
	            String name = matcher.group(3);

	            // Map Java visibility to UML
	            String umlVisibility = switch (visibility) {
	                case "public" -> "+";
	                case "private" -> "-";
	                case "protected" -> "#";
	                default -> "";
	            };

	            Attribute attribute = new Attribute(name, type, umlVisibility);
	            sourceClass.addAttribute(attribute);
	        }
	    }
	}
    
	// Parses and adds methods with visibility, return type, and parameters
	private void extractMethods(List<String> lines, ClassInfo sourceClass) {
		Pattern methodPattern = Pattern.compile(
			    "(?:(public|private|protected)\\s+)?([\\w<>\\[\\]]+)\\s+(\\w+)\\s*\\(([^)]*)\\)"
			);


	    for (String line : lines) {
	        Matcher matcher = methodPattern.matcher(line);
	        if (matcher.find()) {
	            String visibility = matcher.group(1);
	            String returnType = matcher.group(2);
	            String methodName = matcher.group(3);
	            String paramGroup = matcher.group(4).trim();

	            String umlVisibility;
	            if ("public".equals(visibility)) {
	                umlVisibility = "+";
	            } else if ("private".equals(visibility)) {
	                umlVisibility = "-";
	            } else if ("protected".equals(visibility)) {
	                umlVisibility = "#";
	            } else {
	                umlVisibility = "+"; // default when visibility is missing
	            }


	            ArrayList<String> parameters = new ArrayList<>();
	            if (!paramGroup.isEmpty()) {
	                String[] parts = paramGroup.split(",");
	                for (String param : parts) {
	                    String[] words = param.trim().split("\\s+");
	                    if (words.length >= 1) {
	                        parameters.add(words[0]); // use just the type
	                    }
	                }
	            }

	            Method method = new Method(methodName, returnType, parameters, umlVisibility);
	            sourceClass.addMethod(method);
	        }
	    }
	}
	// Locates DEPENDENCY relationships based on field and method types
	private void detectDependencies(IntermediateModel model) {
		// Loop on a copy to not get errors
		List<ClassInfo> classCopy = new ArrayList<>(model.getClasses());
		
		
	    for (ClassInfo sourceClass : classCopy) {
	        Set<String> alreadyLinked = new HashSet<>();

	        // Avoid duplicate dependencies from the same class
	        for (Relationship rel : model.getRelationships()) {
	            if (rel.getSourceClass() == sourceClass && rel.getType() == RelationshipType.DEPENDENCY) {
	                alreadyLinked.add(rel.getTargetClass().getName());
	            }
	        }

	        // Check attributes
	        for (Attribute attribute : sourceClass.getAttributes()) {
	            addDependency(attribute.getType(), sourceClass, model, alreadyLinked);
	        }

	        // Check method return types and parameters
	        for (Method method : sourceClass.getMethods()) {
	            addDependency(method.getReturnType(), sourceClass, model, alreadyLinked);
	            for (String paramType : method.getParameters()) {
	                addDependency(paramType, sourceClass, model, alreadyLinked);
	            }
	        }
	    }
	}

	// Adds a DEPENDENCY and checks if the target type exists or needs to be created
	private void addDependency(String typeString, ClassInfo sourceClass, IntermediateModel model, Set<String> alreadyLinked) {
		for (String typeName : extractAllTypeNames(typeString)) {
		    if (isIgnoredType(typeName)) continue; // Skip primitive or built-in type
		    if (!typeName.equals(sourceClass.getName())) {
		        ClassInfo targetClass = model.findClassByName(typeName);
		 		        
		     // Create placeholder if class not yet defined
		        if (targetClass == null) {
		            targetClass = new ClassInfo(typeName, ClassType.CLASS); // assume it's a class
		            model.addClass(targetClass);
		        }

		        if (!alreadyLinked.contains(typeName)) {
		            model.addRelationship(new Relationship(sourceClass, targetClass, RelationshipType.DEPENDENCY));
		            alreadyLinked.add(typeName);
		        }
		    }
		}

	}
	
	// Breaks type strings into base and generic types
	private List<String> extractAllTypeNames(String type) {
	    List<String> result = new ArrayList<>();
	    result.add(extractBaseType(type)); // e.g., List<Book> --> List
	    result.addAll(extractInnerGenericTypes(type)); // e.g., List<Book> --> Book
	    return result;
	}
	
	// Extracts outer type (e.g., List<Book> → List)
	private String extractBaseType(String type) {
	    int index = type.indexOf("<");
	    return (index != -1) ? type.substring(0, index) : type;
	}

	// Extracts inner types (e.g., Map<A, B> → A, B)
	private List<String> extractInnerGenericTypes(String type) {
	    int start = type.indexOf("<");
	    int end = type.lastIndexOf(">");
	    if (start != -1 && end != -1 && end > start) {
	        String inner = type.substring(start + 1, end);
	        return Arrays.asList(inner.split("\\s*,\\s*"));
	    }
	    return Collections.emptyList();
	}

}
