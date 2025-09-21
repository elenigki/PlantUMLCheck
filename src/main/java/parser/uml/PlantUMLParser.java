package parser.uml;

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
	            arrow.matches("\\.{2,}>") ||   // ..>
	            arrow.matches("<\\.{2,}")      // <..
	        ) {
	            // Dotted arrows are dependency
	            type = RelationshipType.DEPENDENCY;
	        } else if (
	            arrow.matches("-+>") ||        // ->, --> (normalized to -->)
	            arrow.matches("<-+") ||        // <-, <--
	            arrow.matches("-+")            // -, -- (undirected solid line)
	        ) {
	            // Solid arrows/lines are association
	            type = RelationshipType.ASSOCIATION;
	        } else {
	            type = RelationshipType.ASSOCIATION; // fallback
	        }

	        model.addRelationship(new Relationship(source, target, type));
	    }
	}


	// Handles attribute declarations in various UML styles, supports __...__ static wrapper
	private boolean parseAttribute(String line, ClassInfo currentClass) {
	    // Detect and strip __...__ wrapper to mark static
	    boolean isStatic = false;
	    String src = line.trim();
	    if (src.startsWith("__") && src.endsWith("__") && src.length() >= 4) {
	        isStatic = true;
	        src = src.substring(2, src.length() - 2).trim();
	    }

	    // 1) visibility + Type name   e.g. "+ String name", "- int age"
	    Pattern visTypeName = Pattern.compile("^([-+#])?\\s*([\\w<>.\\[\\]]+)\\s+(\\w+)\\s*;?$");
	    Matcher m = visTypeName.matcher(src);
	    if (m.find()) {
	        String visibility = m.group(1) == null ? "" : m.group(1);
	        String type = m.group(2);
	        String name = m.group(3);
	        Attribute attr = new Attribute(name, type, visibility);
	        attr.setStatic(isStatic);
	        currentClass.addAttribute(attr);
	        return true;
	    }

	    // 2) visibility + name : Type e.g. "- name : String", "+ age : int"
	    Pattern visNameColonType = Pattern.compile("^([-+#])\\s*(\\w+)\\s*:\\s*([\\w<>.\\[\\]]+)\\s*;?$");
	    m = visNameColonType.matcher(src);
	    if (m.find()) {
	        String visibility = m.group(1);
	        String name = m.group(2);
	        String type = m.group(3);
	        Attribute attr = new Attribute(name, type, visibility);
	        attr.setStatic(isStatic);
	        currentClass.addAttribute(attr);
	        return true;
	    }

	    // 3) name : Type              e.g. "name : String"
	    Pattern nameColonType = Pattern.compile("^(\\w+)\\s*:\\s*([\\w<>.\\[\\]]+)\\s*;?$");
	    m = nameColonType.matcher(src);
	    if (m.find()) {
	        String name = m.group(1);
	        String type = m.group(2);
	        Attribute attr = new Attribute(name, type, "");
	        attr.setStatic(isStatic);
	        currentClass.addAttribute(attr);
	        return true;
	    }

	    // 4) Type name                e.g. "String name"
	    Pattern typeName = Pattern.compile("^([\\w<>.\\[\\]]+)\\s+(\\w+)\\s*;?$");
	    m = typeName.matcher(src);
	    if (m.find()) {
	        String type = m.group(1);
	        String name = m.group(2);
	        Attribute attr = new Attribute(name, type, "");
	        attr.setStatic(isStatic);
	        currentClass.addAttribute(attr);
	        return true;
	    }

	    return false;
	}



	// Handles method declarations with or without visibility and return type, with optional __...__ static wrapper
	private boolean parseMethod(String line, ClassInfo currentClass) {
	    // Detect and strip __...__ wrapper to mark static
	    boolean isStatic = false;
	    String src = line.trim();
	    Matcher staticWrap = Pattern.compile("^__\\s*(.*?)\\s*__$").matcher(src);
	    if (staticWrap.find()) {
	        isStatic = true;
	        src = staticWrap.group(1).trim(); // inner method text
	    }

	    // 1) Original style: "+ doThing(int, String) : void"
	    //    (kept first so existing diagrams keep working)
	    Matcher m1 = Pattern.compile(
	        "^([-+#~])?\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\((.*?)\\)\\s*(?::\\s*([\\w.$<>\\[\\]., ?]+))?$"
	    ).matcher(src);
	    if (m1.find()) {
	        String visibility = m1.group(1) == null ? "" : m1.group(1);
	        String name       = m1.group(2);
	        String paramsRaw  = m1.group(3);
	        String returnType = m1.group(4) == null ? "void" : m1.group(4).trim();

	        // Skip constructors
	        if (name.equals(currentClass.getName())) {
	            return true; // consume but do not add
	        }

	        ArrayList<String> paramList = new ArrayList<>();
	        if (paramsRaw != null && !paramsRaw.isEmpty()) {
	            for (String p : paramsRaw.split("\\s*,\\s*")) {
	                if (!p.isEmpty()) paramList.add(p.trim());
	            }
	        }
	        Method method = new Method(name, returnType, paramList, visibility);
	        method.setStatic(isStatic);
	        currentClass.addMethod(method);
	        return true;
	    }

	    // 2) Java-like style: "+ void service()", "+ List<String> findAll()"
	    Matcher m2 = Pattern.compile(
	        "^([-+#~])?\\s*([\\w.$<>\\[\\]., ?]+)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\((.*?)\\)\\s*;?$"
	    ).matcher(src);
	    if (m2.find()) {
	        String visibility = m2.group(1) == null ? "" : m2.group(1);
	        String returnType = m2.group(2).trim();
	        String name       = m2.group(3);
	        String paramsRaw  = m2.group(4);

	        // Skip constructors
	        if (name.equals(currentClass.getName())) {
	            return true; // consume but do not add
	        }

	        ArrayList<String> paramList = new ArrayList<>();
	        if (paramsRaw != null && !paramsRaw.isEmpty()) {
	            for (String p : paramsRaw.split("\\s*,\\s*")) {
	                if (!p.isEmpty()) paramList.add(p.trim());
	            }
	        }
	        Method method = new Method(name, returnType, paramList, visibility);
	        method.setStatic(isStatic);
	        currentClass.addMethod(method);
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
