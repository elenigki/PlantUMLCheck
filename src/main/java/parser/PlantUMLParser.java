package parser;

import model.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

// Parses UML class diagrams in PlantUML format into an intermediate model
public class PlantUMLParser {

	// Main parser method: reads file, preprocesses multi-line blocks, then parses
	// each line
	public IntermediateModel parse(File file) throws IOException {
		List<String> rawLines = readLines(file); // raw lines from .puml
		List<String> preprocessed = preprocessLines(rawLines); // unified logical lines
		return parseLines(preprocessed); // convert to model
	}

	// Reads every non-empty, trimmed line from the PlantUML script
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

	// Joins multi-line blocks like parameters and generic types into one line
	private List<String> preprocessLines(List<String> lines) {
		List<String> result = new ArrayList<>();
		StringBuilder buffer = new StringBuilder();
		int parenBalance = 0;
		int angleBalance = 0;

		// Pattern to match explicit relationship lines like A --> B
		Pattern arrowPattern = Pattern.compile("\\w+\\s+([*o]?[-.]*<?\\|?-?[-.]*>?)\\s+\\w+");

		for (String line : lines) {
			String trimmed = line.trim();

			// If the line is an explicit arrow relationship, skip buffering
			if (arrowPattern.matcher(trimmed).matches()) {
				result.add(trimmed);
				continue;
			}

			if (buffer.length() > 0)
				buffer.append(" ");
			buffer.append(trimmed);

			parenBalance += countChar(trimmed, '(') - countChar(trimmed, ')');
			angleBalance += countChar(trimmed, '<') - countChar(trimmed, '>');

			if (parenBalance == 0 && angleBalance == 0) {
				result.add(buffer.toString());
				buffer.setLength(0);
			}
		}

		if (buffer.length() > 0) {
			if (parenBalance != 0 || angleBalance != 0) {
				System.err.println("Warning: Unclosed block in UML lines -> " + buffer.toString());
			}
			result.add(buffer.toString());
		}

		return result;
	}

	// Counts how many times a specific character (e.g. '(') appears in a string
	private int countChar(String str, char c) {
		int count = 0;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) == c) {
				count++;
			}
		}
		return count;
	}

	// Parses all logical UML lines into model elements using modular handlers
	private IntermediateModel parseLines(List<String> lines) {
		IntermediateModel model = new IntermediateModel(ModelSource.PLANTUML_SCRIPT);
		ClassInfo currentClass = null;

		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty())
				continue;

			// Handle generalization (e.g. "B extends A")
			if (parseInheritance(trimmed, model) || parseRealization(trimmed, model)) {
				continue;
			}

			// Handle class/interface/enum declarations
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

			// Handle attributes and methods within class blocks
			if (currentClass != null) {
				if (parseAttribute(trimmed, currentClass) || parseMethod(trimmed, currentClass)) {
					continue;
				}

				// Unrecognized line inside class block
				if (!Set.of("{", "}", "@startuml", "@enduml").contains(trimmed)
						&& !isExplicitRelationshipLine(trimmed)) {
					System.err.println("Error: This line can not be parsed -> " + trimmed);
				}
			} else {
				// Unrecognized line outside class block
				if (!Set.of("{", "}", "@startuml", "@enduml").contains(trimmed)
						&& !isExplicitRelationshipLine(trimmed)) {
					System.err.println("Error: This line can not be parsed -> " + trimmed);
				}
			}
		}

		// Handle explicit arrows like A --> B, A *-- B, etc.
		parseExplicitRelationships(lines, model);

		return model;
	}

	// Handles class declarations with or without the "abstract" modifier.
	private ClassInfo parseClassDeclaration(String line, IntermediateModel model) {
		// Matches: "class Car", "abstract class Shape"
		Matcher matcher = Pattern.compile("(abstract\\s+)?class\\s+(\\w+)").matcher(line);
		if (matcher.find()) {
			boolean isAbstract = matcher.group(1) != null;
			String name = matcher.group(2);

			ClassInfo existing = model.findClassByName(name);
			if (existing != null) {
				existing.setClassType(ClassType.CLASS);
				existing.setAbstract(isAbstract);
				if(existing.getDeclaration() == ClassDeclaration.DUMMY) {
					existing.setDeclaration( ClassDeclaration.OFFICIAL);
					model.removeWarningsForClass(name);
				}
				return existing;
			}

			ClassInfo ci = new ClassInfo(name, ClassType.CLASS, isAbstract, ClassDeclaration.OFFICIAL);
			model.addClass(ci);
			return ci;
		}
		return null;
	}

	// Handles interface declarations like "interface Drawable"
	private ClassInfo parseInterfaceDeclaration(String line, IntermediateModel model) {
		// Matches: "interface Drawable"
		Matcher matcher = Pattern.compile("interface\\s+(\\w+)").matcher(line);
		if (matcher.find()) {
			String name = matcher.group(1);

			ClassInfo existing = model.findClassByName(name);
			if (existing != null) {
				existing.setClassType(ClassType.INTERFACE);
				existing.setAbstract(true); // optional: interfaces are abstract
				if(existing.getDeclaration() == ClassDeclaration.DUMMY) {
					existing.setDeclaration( ClassDeclaration.OFFICIAL);
					model.removeWarningsForClass(name);
				}
				return existing;
			}

			ClassInfo ci = new ClassInfo(name, ClassType.INTERFACE, true, ClassDeclaration.OFFICIAL);
			model.addClass(ci);
			return ci;
		}
		return null;
	}

	// Handles enum declarations like "enum Direction"
	private ClassInfo parseEnumDeclaration(String line, IntermediateModel model) {
		// Matches: "enum Direction"
		Matcher matcher = Pattern.compile("enum\\s+(\\w+)").matcher(line);
		if (matcher.find()) {
			String name = matcher.group(1);

			ClassInfo existing = model.findClassByName(name);
			if (existing != null) {
				existing.setClassType(ClassType.ENUM);
				existing.setAbstract(false); // enums aren't abstract
				if(existing.getDeclaration() == ClassDeclaration.DUMMY) {
					existing.setDeclaration( ClassDeclaration.OFFICIAL);
					model.removeWarningsForClass(name);
				}
				return existing;
			}

			ClassInfo ci = new ClassInfo(name, ClassType.ENUM, ClassDeclaration.OFFICIAL);
			model.addClass(ci);
			return ci;
		}
		return null;
	}

	// Handles inheritance declarations like "class B extends A"
	private boolean parseInheritance(String line, IntermediateModel model) {
		// Matches: "class B extends A", "B extends A"
		Matcher matcher = Pattern.compile("(?:class\\s+)?(\\w+)\\s+extends\\s+(\\w+)").matcher(line);
		if (matcher.find()) {
			String childName = matcher.group(1);
			String parentName = matcher.group(2);

			ClassInfo child = resolveOrCreateClass(model, childName, ClassType.CLASS);
			ClassInfo parent = resolveOrCreateClass(model, parentName, ClassType.CLASS);

			model.addRelationship(new Relationship(child, parent, RelationshipType.GENERALIZATION));
			return true;
		}
		return false;
	}

	// Handles realization declarations like "class C implements Interface"
	private boolean parseRealization(String line, IntermediateModel model) {
		// Matches: "class C implements Drawable", "C implements Drawable"
		Matcher matcher = Pattern.compile("(?:class\\s+)?(\\w+)\\s+implements\\s+(\\w+)").matcher(line);
		if (matcher.find()) {
			String implName = matcher.group(1);
			String ifaceName = matcher.group(2);

			ClassInfo impl = resolveOrCreateClass(model, implName, ClassType.CLASS);
			ClassInfo iface = resolveOrCreateClass(model, ifaceName, ClassType.INTERFACE);

			model.addRelationship(new Relationship(impl, iface, RelationshipType.REALIZATION));
			return true;
		}
		return false;
	}

	// Parses UML relationships like ->, -->, <-, <--, --|>, *--, o--, etc.
	private void parseExplicitRelationships(List<String> lines, IntermediateModel model) {
	    // Pattern for forward arrows (e.g. A --> B, A --|> B, A *-- B)
	    Pattern rightArrowPattern = Pattern.compile(
	        "(\\w+)\\s+([*o]?[-.]+\\|?>?|[-.]+\\|?>?[*o]?)\\s+(\\w+)(\\s*:\\s*\\w+)?"
	    );

	    // Pattern for reverse arrows (e.g. B <-- A, B <|-- A, B --o A)
	    Pattern leftArrowPattern = Pattern.compile(
	        "(\\w+)\\s+((<\\|?[-.]+)|([-.]+\\|?>?|[-.]+[*o]))\\s+(\\w+)(\\s*:\\s*\\w+)?"
	    );

	    for (String line : lines) {
	        Matcher matcher = rightArrowPattern.matcher(line);
	        String sourceName, targetName, rawArrow = null;

	        if (matcher.find()) {
	            // Forward direction: A --> B
	            sourceName = matcher.group(1);
	            rawArrow = matcher.group(2);
	            targetName = matcher.group(3);
	        } else {
	            matcher = leftArrowPattern.matcher(line);
	            if (matcher.find()) {
	                // Reverse direction: B <-- A → flip source and target
	                targetName = matcher.group(1);
	                rawArrow = matcher.group(2);
	                sourceName = matcher.group(5);
	            } else {
	                // Not a valid relationship line
	                continue;
	            }
	        }

	        // Ignore bidirectional arrows like <-->
	        if (rawArrow.contains("<") && rawArrow.contains(">")) {
	            System.err.println("Error: Bidirectional relationships are not supported -> " + line);
	            continue;
	        }

	        // Normalize repeated dashes or dots (e.g. ---> becomes -->)
	        String arrow = rawArrow
	            .replaceAll("-{2,}", "--")
	            .replaceAll("\\.{2,}", "..");

	        // Fix direction for * and o: the class next to * or o is the source (owner)
	        if (arrow.contains("*") || arrow.contains("o")) {
	            if (arrow.endsWith("*") || arrow.endsWith("o")) {
	                String temp = sourceName;
	                sourceName = targetName;
	                targetName = temp;
	            }
	        }

	        // Create or reuse source and target classes
	        ClassInfo source = resolveOrCreateClass(model, sourceName, ClassType.CLASS);
	        ClassInfo target = resolveOrCreateClass(model, targetName, ClassType.CLASS);

	        // Determine the type of relationship based on the arrow
	        RelationshipType type;
	        if (arrow.contains("*")) {
	            type = RelationshipType.COMPOSITION;
	        } else if (arrow.contains("o")) {
	            type = RelationshipType.AGGREGATION;
	        } else if (arrow.contains("|>") || arrow.contains("<|")) {
	            // Distinguish realization vs generalization
	            if (target.getClassType() == ClassType.INTERFACE || source.getClassType() == ClassType.INTERFACE) {
	                type = RelationshipType.REALIZATION;
	            } else {
	                type = RelationshipType.GENERALIZATION;
	            }
	        } else if (
	            arrow.equals("->") ||
	            arrow.equals("<-") ||
	            arrow.equals("-")
	        ) {
	            type = RelationshipType.ASSOCIATION;
	        } else if (
	            arrow.contains("..") ||
	            arrow.matches("-{3,}>") || arrow.matches("<-{3,}>") ||
	            arrow.equals("-->") || arrow.equals("<--")
	        ) {
	            type = RelationshipType.DEPENDENCY;
	        } else {
	            type = RelationshipType.ASSOCIATION; // fallback
	        }

	        model.addRelationship(new Relationship(source, target, type));
	    }
	}


	// Handles attribute declarations in various UML styles.
	private boolean parseAttribute(String line, ClassInfo currentClass) {
		// Matches: "+ String name", "- int age"
		Pattern standard = Pattern.compile("^([-+#])?\\s*([\\w<>]+)\\s+(\\w+);?$");
		Matcher matcher = standard.matcher(line);
		if (matcher.find()) {
			String visibility = matcher.group(1);
			if (visibility == null)
				visibility = "";
			String type = matcher.group(2);
			String name = matcher.group(3);
			currentClass.addAttribute(new Attribute(name, type, visibility));
			return true;
		}

		// Matches: "name : Type"
		Pattern colon = Pattern.compile("^(\\w+)\\s*:\\s*([\\w<>]+);?$");
		matcher = colon.matcher(line);
		if (matcher.find()) {
			String name = matcher.group(1);
			String type = matcher.group(2);
			currentClass.addAttribute(new Attribute(name, type, ""));
			return true;
		}

		// Matches: "Type name"
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

	// Handles method declarations with or without visibility and return type.
	private boolean parseMethod(String line, ClassInfo currentClass) {
		// Matches: "+ getName() : String", "print()", "getInfo(String, int)"
		Matcher matcher = Pattern.compile("^([-+#])?\\s*(\\w+)\\s*\\((.*?)\\)\\s*(?::\\s*([\\w<>]+))?$").matcher(line);
		if (matcher.find()) {
			String visibility = matcher.group(1);
			if (visibility == null)
				visibility = "";
			String name = matcher.group(2);
			String params = matcher.group(3);
			String returnType = matcher.group(4);
			if (returnType == null)
				returnType = "void";

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

	// Checks if a line contains an explicit UML arrow relationship
	// Example: "Car *-- Engine", "Library o-- Book"
	private boolean isExplicitRelationshipLine(String line) {
	    return line.matches("\\w+\\s+([*o]?[-.]+\\|?>?|<\\|?[-.]+|[-.]+[*o])\\s+\\w+(\\s*:\\s*\\w+)?");
	}

	
	// Ensures the class exists in the model. If not, creates it with the given default type.
	private ClassInfo resolveOrCreateClass(IntermediateModel model, String name, ClassType defaultType) {
		ClassInfo ci = model.findClassByName(name);
		if (ci != null)
			return ci;

		ci = new ClassInfo(name, defaultType, ClassDeclaration.DUMMY);
		model.addClass(ci);
		model.addWarning("Class '" + name + "' not found in UML declarations. Added as dummy.");
		return ci;
	}



}
