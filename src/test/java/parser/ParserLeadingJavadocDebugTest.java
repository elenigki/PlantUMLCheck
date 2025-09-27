package parser;


import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;

import org.junit.jupiter.api.Test;

import model.ClassInfo;
import model.IntermediateModel;
import parser.code.JavaSourceParser;          // <-- προσαρμόσ' το αν έχεις άλλο package
import parser.code.JavaSourcePreprocessor;   // <-- προσαρμόσ' το αν έχεις άλλο package

public class ParserLeadingJavadocDebugTest {

    // -------- Helpers (reflection) --------

    private static Object getField(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Object newInnerScanState(JavaSourceParser parser) throws Exception {
        // Βρίσκουμε την εσωτερική κλάση με όνομα που περιέχει "_ScanState"
        for (Class<?> c : parser.getClass().getDeclaredClasses()) {
            if (c.getSimpleName().contains("_ScanState")) {
                Constructor<?> ctor = c.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            }
        }
        throw new IllegalStateException("Could not find _ScanState inner class via reflection");
    }

    private static void dumpLines(List<String> lines) {
        System.out.println("== RAW LINES (" + lines.size() + ") ==");
        for (int i = 0; i < lines.size(); i++) {
            System.out.printf("%3d | %s%n", i, lines.get(i).replace("\t", "\\t"));
        }
    }

    // -------- Single-case runner --------

    private void runCase(String caseName, String source) throws Exception {
        System.out.println("\n\n==================== CASE: " + caseName + " ====================");
        File f = File.createTempFile("JavadocEat_", ".java");
        Files.writeString(f.toPath(), source);

        JavaSourceParser parser = new JavaSourceParser();

        // Πιάσε preprocessor από τον parser
        Object preprocessor = getField(parser, "preprocessor");
        assertNotNull(preprocessor, "preprocessor field is null");

        // readLines(File)
        @SuppressWarnings("unchecked")
        List<String> rawLines = (List<String>) invoke(
                preprocessor, "readLines",
                new Class<?>[]{File.class},
                f);

        dumpLines(rawLines);

        System.out.println("\n-- CLEAN + CLASS HEADER SCAN --");
        for (int i = 0; i < rawLines.size(); i++) {
            String raw = rawLines.get(i);
            String cleaned = (String) invoke(preprocessor, "cleanLine", new Class<?>[]{String.class}, raw);
            boolean isDecl;
            try {
                isDecl = (boolean) invoke(parser, "isClassDeclaration", new Class<?>[]{String.class}, cleaned);
            } catch (NoSuchMethodException nsme) {
                // Αν η μέθοδος περιμένει raw line (όχι cleaned), ξαναδοκίμασε
                isDecl = (boolean) invoke(parser, "isClassDeclaration", new Class<?>[]{String.class}, raw);
            }

            int braces = 0;
            try {
                braces = (int) invoke(parser, "countBraces", new Class<?>[]{String.class}, raw);
            } catch (NoSuchMethodException nsme) {
                // countBraces ίσως είναι σε preprocessor
                try {
                    braces = (int) invoke(preprocessor, "countBraces", new Class<?>[]{String.class}, raw);
                } catch (NoSuchMethodException nsme2) {
                    // αγνόησε αν δεν υπάρχει
                }
            }

            System.out.printf("i=%d | raw='%s'%n", i, raw);
            System.out.printf("      cleaned='%s'%n", cleaned);
            System.out.printf("      isClassDeclaration=%s, countBraces=%d, openPos=%d, closePos=%d%n",
                    isDecl, braces, raw.indexOf('{'), raw.lastIndexOf('}'));
        }

        // --- Προσομοίωση συλλογής body για το πρώτο header που βρούμε ---
        System.out.println("\n-- BODY COLLECTION DRY-RUN --");
        for (int i = 0; i < rawLines.size(); i++) {
            String cleaned = (String) invoke(preprocessor, "cleanLine", new Class<?>[]{String.class}, rawLines.get(i));
            boolean isDecl = (boolean) invoke(parser, "isClassDeclaration", new Class<?>[]{String.class}, cleaned);
            if (!isDecl) continue;

            System.out.println("Header found at line " + i + ": " + cleaned);

            String header = cleaned;
            int openPosHeader  = header.indexOf('{');
            int closePosHeader = header.lastIndexOf('}');

            List<String> bodyLines = new ArrayList<>();
            Object st = newInnerScanState(parser);

            int braceCount = 0;
            if (openPosHeader >= 0 && closePosHeader > openPosHeader) {
                bodyLines.add(header.substring(openPosHeader + 1, closePosHeader));
            } else {
                if (openPosHeader >= 0 && (closePosHeader < 0 || closePosHeader < openPosHeader)) {
                    String tail = header.substring(openPosHeader + 1);
                    if (!tail.isBlank()) {
                        int deltaTail = (int) invoke(parser, "braceDeltaIgnoringComments",
                                new Class<?>[]{String.class, st.getClass()}, tail, st);
                        braceCount += deltaTail;
                        bodyLines.add(tail);
                    }
                    braceCount += 1; // the '{'
                }

                int j = i + 1;
                boolean inside = (braceCount > 0);
                while (j < rawLines.size()) {
                    String bodyLine = rawLines.get(j);
                    int delta = (int) invoke(parser, "braceDeltaIgnoringComments",
                            new Class<?>[]{String.class, st.getClass()}, bodyLine, st);

                    if (!inside) {
                        if (bodyLine.indexOf('{') < 0 && delta <= 0) {
                            System.out.printf("  [j=%d] outside, skipping: %s%n", j, bodyLine);
                            j++;
                            continue;
                        }
                        inside = true;
                        System.out.printf("  [j=%d] entered body on: %s%n", j, bodyLine);
                    }

                    bodyLines.add(bodyLine);
                    braceCount += delta;

                    System.out.printf("  [j=%d] delta=%d, braceCount=%d, line='%s'%n",
                            j, delta, braceCount, bodyLine);

                    j++;
                    if (braceCount <= 0) {
                        System.out.println("  Body balanced at j=" + j);
                        break;
                    }
                }
            }

            System.out.println("-- BODY LINES --");
            for (String bl : bodyLines) {
                System.out.println("  · " + bl);
            }
            break; // μόνο για το πρώτο header
        }

        // --- Τρέξε κανονικά τον parser και επιβεβαίωσε ---
        IntermediateModel model = parser.parse(f);
        List<ClassInfo> classes = new ArrayList<>(model.getClasses());
        System.out.println("\n-- CLASSES FOUND (" + classes.size() + ") --");
        for (ClassInfo ci : classes) {
            System.out.println("  * " + ci.getName() + " [" + ci.getClassType() + "]");
        }

        // Μικρά checks
        ClassInfo something = classes.stream().filter(c -> "Something".equals(c.getName())).findFirst().orElse(null);
        if (something == null) {
            System.out.println("!!! Class 'Something' NOT FOUND");
        } else {
            System.out.println(">>> OK: 'Something' parsed");
        }

        // Μην fail-άρεις σκληρά — θέλουμε το debugging output πρώτα· αλλά βάλε ένα assert:
        assertNotNull(something, "Expected class 'Something' to be parsed");
    }

    // -------- Tests --------

    @Test
    void javadoc_then_class_brace_same_line() throws Exception {
        String src = """
            /** Parses PlantUML class diagrams into an IntermediateModel. */
            package demo;

            public class Something {
                // trivial body
            }
            """;
        runCase("javadoc + brace same line", src);
    }

    @Test
    void javadoc_then_class_brace_next_line() throws Exception {
        String src = """
            /** Parses PlantUML class diagrams into an IntermediateModel. */
            package demo;

            public class Something
            {
                int x;
            }
            """;
        runCase("javadoc + brace next line", src);
    }

    @Test
    void javadoc_top_of_file_no_package() throws Exception {
        String src = """
            /** file header javadoc */
            public class Something
            {
            }
            """;
        runCase("javadoc at top, no package", src);
    }

    @Test
    void block_comment_then_class() throws Exception {
        String src = """
            /* multi
               line
               block comment */
            package demo;
            public class Something {
                /* inside body */ int y = 1;
            }
            """;
        runCase("block comment before class", src);
    }
    
    @Test
    void exact_user_snippet_with_top_javadoc() throws Exception {
        String src = """
            /** Parses PlantUML class diagrams into an IntermediateModel. */

            public class Test{

            /** Reads non-empty trimmed lines. */
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
                
                /** Joins multi-line headers (params/generics) into single logical lines. */
                private List<String> preprocessLines(List<String> lines) {
                    List<String> out = new ArrayList<>();
                    StringBuilder buf = new StringBuilder();
                    int paren = 0, angle = 0;

                    // Detect explicit relationship to avoid accidental merging.
                    Pattern arrow = Pattern.compile("\\\\w+\\\\s+([*o]?[-.]*<?\\\\|?-?[-.]*>?)\\\\s+\\\\w+");

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

            }
            """;
        runCase("exact user snippet (top Javadoc + blank line + class)", src);
    }

}

