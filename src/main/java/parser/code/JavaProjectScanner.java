package parser.code;

import model.ClassType;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class JavaProjectScanner {

    // Regex patterns for class header extraction
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([\\w\\.]+);");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b(public\\s+)?(abstract\\s+)?class\\s+(\\w+)");
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("\\b(public\\s+)?interface\\s+(\\w+)");
    private static final Pattern ENUM_PATTERN = Pattern.compile("\\b(public\\s+)?enum\\s+(\\w+)");

    // Main entry point: recursively scan a directory or file
    public List<ScannedJavaInfo> scan(File root) throws IOException {
        List<ScannedJavaInfo> result = new ArrayList<>();
        scanDirectory(root, result);
        return result;
    }

    private void scanDirectory(File fileOrDir, List<ScannedJavaInfo> result) throws IOException {
        if (fileOrDir.isDirectory()) {
            for (File f : Objects.requireNonNull(fileOrDir.listFiles())) {
                scanDirectory(f, result);
            }
        } else if (fileOrDir.getName().endsWith(".java")) {
            ScannedJavaInfo info = extractMetadata(fileOrDir);
            if (info != null) result.add(info);
        }
    }

    // Extract class/interface/enum and package from the Java file
    private ScannedJavaInfo extractMetadata(File javaFile) throws IOException {
        String packageName = "";
        String className = null;
        ClassType classType = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(javaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("package")) {
                    Matcher m = PACKAGE_PATTERN.matcher(line);
                    if (m.find()) packageName = m.group(1);
                } else if (line.contains("class")) {
                    Matcher m = CLASS_PATTERN.matcher(line);
                    if (m.find()) {
                        className = m.group(3);
                        classType = ClassType.CLASS;
                        break;
                    }
                } else if (line.contains("interface")) {
                    Matcher m = INTERFACE_PATTERN.matcher(line);
                    if (m.find()) {
                        className = m.group(2);
                        classType = ClassType.INTERFACE;
                        break;
                    }
                } else if (line.contains("enum")) {
                    Matcher m = ENUM_PATTERN.matcher(line);
                    if (m.find()) {
                        className = m.group(2);
                        classType = ClassType.ENUM;
                        break;
                    }
                }
            }
        }

        if (className != null && classType != null) {
            String fqName = packageName.isEmpty() ? className : packageName + "." + className;
            return new ScannedJavaInfo(fqName, className, packageName, classType, javaFile);
        }
        return null;
    }
}