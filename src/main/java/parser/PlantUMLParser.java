package parser;
import model.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class PlantUMLParser {
	
	// Parse the file
	public IntermediateModel parse(File file) throws IOException {
        List<String> rawLines = readLines(file);
        List<String> preprocessed = preprocessLines(rawLines);
        return parseLines(preprocessed);
	}
	
	// Split input into lines
    private List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        }
        return lines;
    }
    
    private List<String> preprocessLines(List<String> lines) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int parenBalance = 0;
        int angleBalance = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (buffer.length() > 0) buffer.append(" ");
            buffer.append(trimmed);

            parenBalance += countChar(trimmed, '(') - countChar(trimmed, ')');
            angleBalance += countChar(trimmed, '<') - countChar(trimmed, '>');

            if (parenBalance == 0 && angleBalance == 0) {
                result.add(buffer.toString());
                buffer.setLength(0);
            }
        }

        // Handle any remaining partial line
        if (buffer.length() > 0) {
        	if (parenBalance != 0 || angleBalance != 0) {
        		System.err.println("Warning: Unclosed block in UML lines -> " + buffer.toString());
        	}
        	result.add(buffer.toString());
        }
        return result;
    }


    // Utility method to count the number of occurrences of a character in a string.
    private int countChar(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    
    // Convert each line
    private IntermediateModel parseLines(List<String> lines) {
        IntermediateModel model = new IntermediateModel();
        ClassInfo currentClass = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (parseInheritance(trimmed, model) || parseRealization(trimmed, model)) {
                continue;
            }

            ClassInfo declaredClass = parseClassDeclaration(trimmed, model);
            if (declaredClass != null) {
                currentClass = declaredClass;
                continue;
            }

            declaredClass = parseInterfaceDeclaration(trimmed, model);
            if (declaredClass != null) {
                currentClass = declaredClass;
                continue;
            }

            declaredClass = parseEnumDeclaration(trimmed, model);
            if (declaredClass != null) {
                currentClass = declaredClass;
                continue;
            }

            if (currentClass != null) {
                if (parseAttribute(trimmed, currentClass) || parseMethod(trimmed, currentClass)) {
                    continue;
                }

                if (!Set.of("{", "}", "@startuml", "@enduml").contains(trimmed) &&
                    !isExplicitRelationshipLine(trimmed)) {
                    System.err.println("Error: This line can not be parsed -> " + trimmed);
                }
            } else {
                if (!Set.of("{", "}", "@startuml", "@enduml").contains(trimmed) &&
                    !isExplicitRelationshipLine(trimmed)) {
                    System.err.println("Error: This line can not be parsed -> " + trimmed);
                }
            }

        }

        parseExplicitRelationships(lines, model);
        detectDependencies(model);
        return model;
    }
    
    private ClassInfo parseClassDeclaration(String line, IntermediateModel model) {
        Matcher matcher = Pattern.compile("(abstract\\s+)?class\\s+(\\w+)").matcher(line);
        if (matcher.find()) {
            boolean isAbstract = matcher.group(1) != null;
            String name = matcher.group(2);
            ClassInfo ci = new ClassInfo(name, ClassType.CLASS, isAbstract);
            model.addClass(ci);
            return ci;
        }
        return null;
    }

    private ClassInfo parseInterfaceDeclaration(String line, IntermediateModel model) {
        Matcher matcher = Pattern.compile("interface\\s+(\\w+)").matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1);
            ClassInfo ci = new ClassInfo(name, ClassType.INTERFACE);
            model.addClass(ci);
            return ci;
        }
        return null;
    }

    private ClassInfo parseEnumDeclaration(String line, IntermediateModel model) {
        Matcher matcher = Pattern.compile("enum\\s+(\\w+)").matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1);
            ClassInfo ci = new ClassInfo(name, ClassType.ENUM);
            model.addClass(ci);
            return ci;
        }
        return null;
    }
    
    private boolean parseInheritance(String line, IntermediateModel model) {
        Matcher matcher = Pattern.compile("(?:class\\s+)?(\\w+)\\s+extends\\s+(\\w+)").matcher(line);
        if (matcher.find()) {
            String child = matcher.group(1);
            String parent = matcher.group(2);

            ClassInfo childClass = resolveOrCreateClass(model, child);
            ClassInfo parentClass = resolveOrCreateClass(model, parent);

            model.addRelationship(new Relationship(childClass, parentClass, RelationshipType.GENERALIZATION));
            return true;
        }
        return false;
    }

    private boolean parseRealization(String line, IntermediateModel model) {
        Matcher matcher = Pattern.compile("(?:class\\s+)?(\\w+)\\s+implements\\s+(\\w+)").matcher(line);
        if (matcher.find()) {
            String impl = matcher.group(1);
            String iface = matcher.group(2);

            ClassInfo implClass = resolveOrCreateClass(model, impl);
            ClassInfo ifaceClass = model.findClassByName(iface);
            if (ifaceClass == null) {
                ifaceClass = new ClassInfo(iface, ClassType.INTERFACE);
                model.addClass(ifaceClass);
            }

            model.addRelationship(new Relationship(implClass, ifaceClass, RelationshipType.REALIZATION));
            return true;
        }
        return false;
    }

    private boolean parseAttribute(String line, ClassInfo currentClass) {
        // Pattern 1: [-+#] Type name;
        Pattern standard = Pattern.compile("^([-+#])?\\s*([\\w<>]+)\\s+(\\w+);?$");
        Matcher matcher = standard.matcher(line);
        if (matcher.find()) {
            String visibility = matcher.group(1);
            if (visibility == null) visibility = "";
            String type = matcher.group(2);
            String name = matcher.group(3);
            currentClass.addAttribute(new Attribute(name, type, visibility));
            return true;
        }

        // Pattern 2: name : Type
        Pattern colon = Pattern.compile("^(\\w+)\\s*:\\s*([\\w<>]+);?$");
        matcher = colon.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1);
            String type = matcher.group(2);
            currentClass.addAttribute(new Attribute(name, type, ""));
            return true;
        }

        // Pattern 3: Type name
        Pattern typeFirst = Pattern.compile("^([\\w<>]+)\\s+(\\w+);?$");
        matcher = typeFirst.matcher(line);
        if (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            currentClass.addAttribute(new Attribute(name, type, ""));
            return true;
        }

        return false;
    }


    private boolean parseMethod(String line, ClassInfo currentClass) {
    	Matcher matcher = Pattern.compile("^([-+#])?\\s*(\\w+)\\s*\\((.*?)\\)\\s*(?::\\s*([\\w<>]+))?$")
    			.matcher(line);
    	if (matcher.find()) {
    	    String visibility = matcher.group(1);
    	    if (visibility == null) visibility = "";
    	    String name = matcher.group(2);
    	    String params = matcher.group(3);
    	    String returnType = matcher.group(4);
    	    if (returnType == null) returnType = "void";

    	    ArrayList<String> paramList = new ArrayList<>();
    	    if (!params.isEmpty()) {
    	    	for (String p : params.split("\\s*,\\s*")) {
    	    	    paramList.add(p.trim());
    	    	}

    	    }

    	    currentClass.addMethod(new Method(name, returnType, paramList, visibility));
    	    return true;
    	}

        return false;
    }

    private ClassInfo resolveOrCreateClass(IntermediateModel model, String name) {
        ClassInfo ci = model.findClassByName(name);
        if (ci == null) {
            ci = new ClassInfo(name, ClassType.CLASS);
            model.addClass(ci);
        }
        return ci;
    }
    
    private void parseExplicitRelationships(List<String> lines, IntermediateModel model) {
        Pattern relationshipPattern = Pattern.compile("(\\w+)\\s+([*o]--|[-.]*<?\\|?-?[-.]*>?)\\s+(\\w+)(\\s*:\\s*\\w+)?");

        for (String line : lines) {
            Matcher matcher = relationshipPattern.matcher(line);
            if (matcher.find()) {
                String left = matcher.group(1);
                String arrow = matcher.group(2);
                String right = matcher.group(3);

                String sourceName;
                String targetName;
                if (arrow.contains("<")) {
                    sourceName = right;
                    targetName = left;
                } else {
                    sourceName = left;
                    targetName = right;
                }

                ClassInfo source = resolveOrCreateClass(model, sourceName);
                ClassInfo target = resolveOrCreateClass(model, targetName);

                RelationshipType type;
                if (arrow.equals("*--")) {
                    type = RelationshipType.COMPOSITION;
                } else if (arrow.equals("o--")) {
                    type = RelationshipType.AGGREGATION;
                } else if (arrow.contains("|>")) {
                    type = (target.getClassType() == ClassType.INTERFACE)
                            ? RelationshipType.REALIZATION
                            : RelationshipType.GENERALIZATION;
                } else if (arrow.contains(">")) {
                    type = RelationshipType.DEPENDENCY;
                } else {
                    type = RelationshipType.ASSOCIATION;
                }

                model.addRelationship(new Relationship(source, target, type));
            }
        }
    }

    private void detectDependencies(IntermediateModel model) {
        for (ClassInfo source : model.getClasses()) {
            Set<String> alreadyLinked = new HashSet<>();
            for (Relationship r : model.getRelationships()) {
                if (r.getSourceClass() == source && r.getType() == RelationshipType.DEPENDENCY) {
                    alreadyLinked.add(r.getTargetClass().getName());
                }
            }

            for (Attribute attr : source.getAttributes()) {
                String fullType = attr.getType();
                addDependencyIfValid(source, extractBaseType(fullType), model, alreadyLinked);
                for (String inner : extractInnerGenericTypes(fullType)) {
                    addDependencyIfValid(source, extractBaseType(inner), model, alreadyLinked);
                }
            }

            for (Method method : source.getMethods()) {
                for (String param : method.getParameters()) {
                    addDependencyIfValid(source, extractBaseType(param), model, alreadyLinked);
                    for (String inner : extractInnerGenericTypes(param)) {
                        addDependencyIfValid(source, extractBaseType(inner), model, alreadyLinked);
                    }
                }

                String returnType = method.getReturnType();
                addDependencyIfValid(source, extractBaseType(returnType), model, alreadyLinked);
                for (String inner : extractInnerGenericTypes(returnType)) {
                    addDependencyIfValid(source, extractBaseType(inner), model, alreadyLinked);
                }
            }
        }
    }

    private void addDependencyIfValid(ClassInfo source, String typeName, IntermediateModel model, Set<String> alreadyLinked) {
        if (typeName.equals(source.getName())) return;
        ClassInfo target = model.findClassByName(typeName);
        if (target != null && !alreadyLinked.contains(target.getName())) {
            model.addRelationship(new Relationship(source, target, RelationshipType.DEPENDENCY));
            alreadyLinked.add(target.getName());
        }
    }

    private String extractBaseType(String type) {
        int index = type.indexOf("<");
        return (index != -1) ? type.substring(0, index) : type;
    }

    private List<String> extractInnerGenericTypes(String type) {
        int start = type.indexOf("<");
        int end = type.lastIndexOf(">");
        if (start != -1 && end != -1 && end > start) {
            String inner = type.substring(start + 1, end);
            return Arrays.asList(inner.split("\\s*,\\s*"));
        }
        return Collections.emptyList();
    }
    
    private boolean isExplicitRelationshipLine(String line) {
        return line.matches("\\w+\\s+([*o]--|[-.]*<?\\|?-?[-.]*>?)\\s+\\w+(\\s*:\\s*\\w+)?");
    }

}
