package parser.code;

import java.io.*;
import java.util.*;

public class JavaSourcePreprocessor {

    // Reads all lines of a file
    public List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // Removes end-of-line // and naive /* */ markers on the same line, then trims
    public String cleanLine(String line) {
        if (line == null) return "";
        int commentIndex = line.indexOf("//");
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex);
        }
        line = line.replace("/*", "").replace("*/", "");
        return line.trim();
    }

    // Removes // and /* ... */ comments from a list of lines; skips annotation-only lines
    public List<String> cleanComments(List<String> lines) {
        List<String> result = new ArrayList<>();
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = (line == null) ? "" : line.trim();
            if (trimmed.isEmpty()) continue;

            if (inBlockComment) {
                int end = trimmed.indexOf("*/");
                if (end >= 0) {
                    inBlockComment = false;
                    trimmed = trimmed.substring(end + 2).trim();
                    if (trimmed.isEmpty()) continue;
                } else {
                    continue;
                }
            }

            int start = trimmed.indexOf("/*");
            if (start >= 0) {
                int end = trimmed.indexOf("*/", start + 2);
                if (end >= 0) {
                    // comment starts and ends on same line
                    trimmed = (trimmed.substring(0, start) + " " + trimmed.substring(end + 2)).trim();
                } else {
                    // starts block comment; drop the rest
                    inBlockComment = true;
                    trimmed = trimmed.substring(0, start).trim();
                }
            }

            int slashes = trimmed.indexOf("//");
            if (slashes >= 0) {
                trimmed = trimmed.substring(0, slashes).trim();
            }

            // Ignore pure annotation lines to avoid polluting member parsing
            if (trimmed.startsWith("@")) continue;

            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    // Joins physical lines that belong to the same logical declaration
    public List<String> joinLogicalLines(List<String> lines) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parens = 0, angles = 0, braces = 0;

        for (String rawLine : lines) {
            String line = (rawLine == null) ? "" : rawLine.trim();
            if (line.isEmpty()) continue;

            for (char c : line.toCharArray()) {
                if (c == '(') parens++;
                if (c == ')') parens--;
                if (c == '<') angles++;
                if (c == '>') angles--;
                if (c == '{') braces++;
                if (c == '}') braces--;
            }

            current.append(line).append(' ');
            boolean isCompleteLine = line.endsWith(";") || line.endsWith("{");

            if (parens <= 0 && angles <= 0 && isCompleteLine) {
                String completedLine = current.toString().trim();
                if (!completedLine.isEmpty()) result.add(completedLine);
                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            String remaining = current.toString().trim();
            if (!remaining.isEmpty()) result.add(remaining);
        }

        return result;
    }

    // Final normalization: split lines like "} public ..." into separate statements
    public List<String> splitMultipleStatements(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line == null) continue;
            String[] parts = line.split("(?<=\\})\\s+(?=public|private|protected)");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) result.add(trimmed);
            }
        }
        return result;
    }
}