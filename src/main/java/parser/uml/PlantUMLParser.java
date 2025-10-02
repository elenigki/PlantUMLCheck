package parser.uml;

import model.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class PlantUMLParser {
	private final IntermediateModel model;

	
	public PlantUMLParser(){
		this.model = new IntermediateModel(ModelSource.PLANTUML_SCRIPT);
	}
    // Entry point: read, preprocess multi-line parts, then parse.
    public IntermediateModel parse(File file) throws IOException {
        List<String> raw = readLines(file);
        List<String> logical = preprocessLines(raw);
        return parseLines(logical);
    }

    // Reads non-empty trimmed lines.
    private List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (!t.isEmpty()) lines.add(t);
            }
        }
        return lines;
    }

    // Joins multi-line headers (params/generics) into single logical lines.
    private List<String> preprocessLines(List<String> lines) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int paren = 0, angle = 0;

        // Detect explicit relationship to avoid accidental merging.
        Pattern arrow = Pattern.compile("\\w+\\s+([*o]?[-.]*<?\\|?-?[-.]*>?)\\s+\\w+");

        for (String line : lines) {
            String t = line.trim();

            if (arrow.matcher(t).matches()) { // keep relationship lines as-is
                out.add(t);
                continue;
            }

            if (buf.length() > 0) buf.append(' ');
            buf.append(t);

            paren += countChar(t, '(') - countChar(t, ')');
            angle += countChar(t, '<') - countChar(t, '>');

            if (paren == 0 && angle == 0) {
                out.add(buf.toString());
                buf.setLength(0);
            }
        }

        if (buf.length() > 0) {
            if (paren != 0 || angle != 0) {
                System.err.println("Warning: Unclosed block in UML lines -> " + buf.toString());
            }
            out.add(buf.toString());
        }
        return out;
    }

    // Parses logical lines into classes, members, and relationships.
    private IntermediateModel parseLines(List<String> lines) {
        ClassInfo current = null;

        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;

            // Keywords for inheritance via text (extends/implements).
            if (parseInheritance(t, model) || parseRealization(t, model)) continue;

            // Declarations.
            ClassInfo decl = parseClassDeclaration(t, model);
            if (decl != null) { current = decl; continue; }
            decl = parseInterfaceDeclaration(t, model);
            if (decl != null) { current = decl; continue; }
            decl = parseEnumDeclaration(t, model);
            if (decl != null) { current = decl; continue; }

            // Members inside current class.
            if (current != null) {
                if (parseAttribute(t, current) || parseMethod(t, current)) continue;
                if (!Set.of("{", "}", "@startuml", "@enduml").contains(t) && !isExplicitRelationshipLine(t)) {
                    System.err.println("Error: This line can not be parsed -> " + t);
                }
            } else {
                if (!Set.of("{", "}", "@startuml", "@enduml").contains(t) && !isExplicitRelationshipLine(t)) {
                    System.err.println("Error: This line can not be parsed -> " + t);
                }
            }
        }

        // Arrows like A --> B, A *-- B, etc.
        parseExplicitRelationships(lines, model);
        return model;
    }

    // Parses "class X" with optional "abstract" and optional "<<external>>".
    private ClassInfo parseClassDeclaration(String line, IntermediateModel model) {
        Matcher m = Pattern.compile("(abstract\\s+)?class\\s+(\\w+)\\s*(?:<<\\s*([\\w-]+)\\s*>>)?").matcher(line);
        if (!m.find()) return null;

        boolean isAbstract = m.group(1) != null;
        String name = m.group(2);
        String ster = m.group(3);
        boolean isExternal = ster != null && "external".equalsIgnoreCase(ster);

        ClassInfo existing = model.findClassByName(name);
        if (existing != null) {
            existing.setClassType(ClassType.CLASS);
            existing.setAbstract(isAbstract);
            if (isExternal) {
                existing.setDeclaration(ClassDeclaration.DUMMY); // explicit external stays dummy
            } else {
                if (existing.getDeclaration() == ClassDeclaration.DUMMY) {
                    existing.setDeclaration(ClassDeclaration.OFFICIAL);
                    model.removeWarningsForClass(name);
                } else {
                    existing.setDeclaration(ClassDeclaration.OFFICIAL);
                }
            }
            return existing;
        }

        ClassDeclaration decl = isExternal ? ClassDeclaration.DUMMY : ClassDeclaration.OFFICIAL;
        ClassInfo ci = new ClassInfo(name, ClassType.CLASS, isAbstract, decl);
        model.addClass(ci);
        return ci;
    }

    // Parses "interface X".
    private ClassInfo parseInterfaceDeclaration(String line, IntermediateModel model) {
        Matcher m = Pattern.compile("interface\\s+(\\w+)").matcher(line);
        if (!m.find()) return null;

        String name = m.group(1);
        ClassInfo existing = model.findClassByName(name);
        if (existing != null) {
            existing.setClassType(ClassType.INTERFACE);
            existing.setAbstract(true);
            if (existing.getDeclaration() == ClassDeclaration.DUMMY) {
                existing.setDeclaration(ClassDeclaration.OFFICIAL);
                model.removeWarningsForClass(name);
            }
            return existing;
        }
        ClassInfo ci = new ClassInfo(name, ClassType.INTERFACE, true, ClassDeclaration.OFFICIAL);
        model.addClass(ci);
        return ci;
    }

    // Parses "enum X".
    private ClassInfo parseEnumDeclaration(String line, IntermediateModel model) {
        Matcher m = Pattern.compile("enum\\s+(\\w+)").matcher(line);
        if (!m.find()) return null;

        String name = m.group(1);
        ClassInfo existing = model.findClassByName(name);
        if (existing != null) {
            existing.setClassType(ClassType.ENUM);
            existing.setAbstract(false);
            if (existing.getDeclaration() == ClassDeclaration.DUMMY) {
                existing.setDeclaration(ClassDeclaration.OFFICIAL);
                model.removeWarningsForClass(name);
            }
            return existing;
        }
        ClassInfo ci = new ClassInfo(name, ClassType.ENUM, ClassDeclaration.OFFICIAL);
        model.addClass(ci);
        return ci;
    }

    // Parses "X extends Y".
    private boolean parseInheritance(String line, IntermediateModel model) {
        Matcher m = Pattern.compile("(?:class\\s+)?(\\w+)\\s+extends\\s+(\\w+)").matcher(line);
        if (!m.find()) return false;

        ClassInfo child = resolveOrCreateClass(model, m.group(1), ClassType.CLASS);
        ClassInfo parent = resolveOrCreateClass(model, m.group(2), ClassType.CLASS);
        model.addRelationship(new Relationship(child, parent, RelationshipType.GENERALIZATION));
        return true;
    }

    // Parses "X implements I".
    private boolean parseRealization(String line, IntermediateModel model) {
        Matcher m = Pattern.compile("(?:class\\s+)?(\\w+)\\s+implements\\s+(\\w+)").matcher(line);
        if (!m.find()) return false;

        ClassInfo impl = resolveOrCreateClass(model, m.group(1), ClassType.CLASS);
        ClassInfo iface = resolveOrCreateClass(model, m.group(2), ClassType.INTERFACE);
        model.addRelationship(new Relationship(impl, iface, RelationshipType.REALIZATION));
        return true;
    }

    // Parses explicit arrows like ->, -->, <-, <--, --|>, *--, o--, ..>, etc.
    private void parseExplicitRelationships(List<String> lines, IntermediateModel model) {
        Pattern right = Pattern.compile("(\\w+)\\s+([*o]?[-.]+\\|?>?|[-.]+\\|?>?[*o]?)\\s+(\\w+)(\\s*:\\s*\\w+)?");
        Pattern left  = Pattern.compile("(\\w+)\\s+((<\\|?[-.]+)|([-.]+\\|?>?|[-.]+[*o]))\\s+(\\w+)(\\s*:\\s*\\w+)?");

        for (String line : lines) {
            Matcher m = right.matcher(line);
            String srcName, dstName, raw = null;

            if (m.find()) {
                srcName = m.group(1);
                raw     = m.group(2);
                dstName = m.group(3);
            } else {
                m = left.matcher(line);
                if (!m.find()) continue;
                dstName = m.group(1);
                raw     = m.group(2);
                srcName = m.group(5);
            }

            if (raw.contains("<") && raw.contains(">")) {
                System.err.println("Error: Bidirectional relationships are not supported -> " + line);
                continue;
            }

            String arrow = raw.replaceAll("-{2,}", "--").replaceAll("\\.{2,}", "..");

            if (arrow.contains("*") || arrow.contains("o")) { // owner is next to * or o
                if (arrow.endsWith("*") || arrow.endsWith("o")) {
                    String tmp = srcName; srcName = dstName; dstName = tmp;
                }
            }

            ClassInfo src = resolveOrCreateClass(model, srcName, ClassType.CLASS);
            ClassInfo dst = resolveOrCreateClass(model, dstName, ClassType.CLASS);

            RelationshipType type;
            if (arrow.contains("*")) {
                type = RelationshipType.COMPOSITION;
            } else if (arrow.contains("o")) {
                type = RelationshipType.AGGREGATION;
            } else if (arrow.contains("|>") || arrow.contains("<|")) {
                if (dst.getClassType() == ClassType.INTERFACE || src.getClassType() == ClassType.INTERFACE) {
                    type = RelationshipType.REALIZATION;
                } else {
                    type = RelationshipType.GENERALIZATION;
                }
            } else if (arrow.matches("\\.{2,}>") || arrow.matches("<\\.{2,}")) {
                type = RelationshipType.DEPENDENCY; // dotted arrows
            } else if (arrow.matches("-+>") || arrow.matches("<-+") || arrow.matches("-+")) {
                type = RelationshipType.ASSOCIATION; // solid arrows/lines
            } else {
                type = RelationshipType.ASSOCIATION; // fallback
            }

            model.addRelationship(new Relationship(src, dst, type));
        }
    }

    // Parses attributes (supports visibility outside __ and whole-line __).
    private boolean parseAttribute(String line, ClassInfo currentClass) {
        String s = line.trim();
        boolean isStatic = false;

        String leadingVis = "";
        if (!s.isEmpty() && "+-#~".indexOf(s.charAt(0)) >= 0) {
            leadingVis = String.valueOf(s.charAt(0));
            s = s.substring(1).trim();
        }
        if (s.startsWith("__") && s.endsWith("__") && s.length() >= 4) {
            isStatic = true;
            s = s.substring(2, s.length() - 2).trim();
        }

        Pattern typeName = Pattern.compile("^([+\\-#~])?\\s*([\\w.$<>\\[\\]?]+)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*;?$");
        Matcher m = typeName.matcher(s);
        if (m.find()) {
            String vis = !leadingVis.isEmpty() ? leadingVis : (m.group(1) == null ? "" : m.group(1));
            String type = m.group(2);
            String name = m.group(3);
            Attribute a = new Attribute(name, type, vis);
            a.setStatic(isStatic);
            currentClass.addAttribute(a);
            return true;
        }

        Pattern nameColonType = Pattern.compile("^([+\\-#~])?\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*([\\w.$<>\\[\\]?]+)\\s*;?$");
        m = nameColonType.matcher(s);
        if (m.find()) {
            String vis = !leadingVis.isEmpty() ? leadingVis : (m.group(1) == null ? "" : m.group(1));
            String name = m.group(2);
            String type = m.group(3);
            Attribute a = new Attribute(name, type, vis);
            a.setStatic(isStatic);
            currentClass.addAttribute(a);
            return true;
        }
        return false;
    }

    // Parses methods (supports visibility outside __, whole-line __, and params as types).
    private boolean parseMethod(String line, ClassInfo currentClass) {
        String s = line.trim();
        boolean isStatic = false;

        String leadingVis = "";
        if (!s.isEmpty() && "+-#~".indexOf(s.charAt(0)) >= 0) {
            leadingVis = String.valueOf(s.charAt(0));
            s = s.substring(1).trim();
        }
        if (s.startsWith("__") && s.endsWith("__") && s.length() >= 4) {
            isStatic = true;
            s = s.substring(2, s.length() - 2).trim();
        }

        // UML-style: name(params) : ReturnType
        Matcher m1 = Pattern.compile(
            "^([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\((.*?)\\)\\s*(?::\\s*([\\w.$<>\\[\\]., ?]+))?\\s*;?$"
        ).matcher(s);
        if (m1.find()) {
            String name = m1.group(1);
            String paramsRaw = m1.group(2) == null ? "" : m1.group(2);
            String returnType = m1.group(3) == null ? "void" : m1.group(3).trim();
            String visibility = leadingVis;

            if (name.equals(currentClass.getName())) return true; // skip constructors

            ArrayList<String> params = new ArrayList<>();
            if (!paramsRaw.isEmpty()) {
                for (String p : paramsRaw.split("\\s*,\\s*")) {
                    if (p.isEmpty()) continue;
                    String typeOnly = extractParamType(p);
                    if (!typeOnly.isEmpty()) params.add(typeOnly);
                }
            }
            Method m = new Method(name, returnType, params, visibility);
            m.setStatic(isStatic);
            currentClass.addMethod(m);
            return true;
        }

        // Java-like: ReturnType name(params)
        Matcher m2 = Pattern.compile(
            "^([\\w.$<>\\[\\]., ?]+)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\((.*?)\\)\\s*;?$"
        ).matcher(s);
        if (m2.find()) {
            String returnType = m2.group(1).trim();
            String name = m2.group(2);
            String paramsRaw = m2.group(3) == null ? "" : m2.group(3);
            String visibility = leadingVis;

            if (name.equals(currentClass.getName())) return true; // skip constructors

            ArrayList<String> params = new ArrayList<>();
            if (!paramsRaw.isEmpty()) {
                for (String p : paramsRaw.split("\\s*,\\s*")) {
                    if (p.isEmpty()) continue;
                    String typeOnly = extractParamType(p);
                    if (!typeOnly.isEmpty()) params.add(typeOnly);
                }
            }
            Method m = new Method(name, returnType, params, visibility);
            m.setStatic(isStatic);
            currentClass.addMethod(m);
            return true;
        }
        return false;
    }

    // -------------------- helpers (grouped) --------------------

    // True if line looks like an explicit relationship arrow.
    private boolean isExplicitRelationshipLine(String line) {
        return line.matches("\\w+\\s+([*o]?[-.]+\\|?>?|<\\|?[-.]+|[-.]+[*o])\\s+\\w+(\\s*:\\s*\\w+)?");
    }

    // Returns an existing class or creates a DUMMY one of given default type.
    private ClassInfo resolveOrCreateClass(IntermediateModel model, String name, ClassType fallback) {
        ClassInfo ci = model.findClassByName(name);
        if (ci != null) return ci;
        ci = new ClassInfo(name, fallback, ClassDeclaration.DUMMY);
        model.addClass(ci);
        model.addWarning("Class '" + name + "' not found in UML declarations. Added as dummy.");
        return ci;
    }

    // Counts a specific character. */
    private int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    // Returns only the parameter type from "name : Type" | "Type name" | "Type".
    private String extractParamType(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";

        Matcher mColon = Pattern.compile("^([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*(.+)$").matcher(s);
        if (mColon.find()) return mColon.group(2).trim();

        String[] parts = s.split("\\s+");
        return parts.length >= 1 ? parts[0].trim() : s;
    }
}
