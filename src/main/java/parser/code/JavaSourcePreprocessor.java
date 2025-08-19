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

	// Removes comments and trims whitespace
	public String cleanLine(String line) {
		if (line == null)
			return "";
		int commentIndex = line.indexOf("//");
		if (commentIndex != -1) {
			line = line.substring(0, commentIndex);
		}
		line = line.replace("/*", "").replace("*/", "");
		return line.trim();
	}

	// Removes // and /* */ comments from a list of lines
	public List<String> cleanComments(List<String> lines) {
		List<String> result = new ArrayList<>();
		boolean inBlockComment = false;

		for (String line : lines) {
			String trimmed = line.trim();

			if (inBlockComment) {
				if (trimmed.contains("*/")) {
					inBlockComment = false;
					// Remove up to and including */
					trimmed = trimmed.substring(trimmed.indexOf("*/") + 2).trim();
					if (trimmed.isEmpty())
						continue;
				} else {
					continue; // skip whole line
				}
			}

			if (trimmed.contains("/*")) {
				inBlockComment = true;
				// Keep part before /*
				trimmed = trimmed.substring(0, trimmed.indexOf("/*")).trim();
			}

			// Remove // comments
			if (trimmed.contains("//")) {
				trimmed = trimmed.substring(0, trimmed.indexOf("//")).trim();
			}

			if (trimmed.startsWith("@")) {
				continue; // Ignore annotations like @Override, @Deprecated, etc.
			}

			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}

		return result;
	}

	// Joins lines that belong to the same logical declaration (e.g. attributes,
		// method headers)
		public List<String> joinLogicalLines(List<String> lines) {
			List<String> result = new ArrayList<>();
			StringBuilder current = new StringBuilder();
			int parens = 0; // ()
			int angles = 0; // <>
			int braces = 0; // not strictly needed but could help

			System.out.println("\n[joinLogicalLines] --- START ---");

			for (String rawLine : lines) {
				System.out.println("[joinLogicalLines] Line in: " + rawLine);
				String line = rawLine.trim();
				if (line.isEmpty())
					continue;

				// Count open/close brackets to detect multi-line blocks
				for (char c : line.toCharArray()) {
					if (c == '(')
						parens++;
					if (c == ')')
						parens--;
					if (c == '<')
						angles++;
					if (c == '>')
						angles--;
					if (c == '{')
						braces++;
					if (c == '}')
						braces--;
				}

				current.append(line).append(" ");

				boolean isCompleteLine = line.endsWith(";") || line.endsWith("{");

				if (parens <= 0 && angles <= 0 && isCompleteLine) {
					String completedLine = current.toString().trim();
					System.out.println("[joinLogicalLines] → Completed logical line: " + completedLine);
					result.add(completedLine);
					current.setLength(0);
				}
			}

			// Add any remaining line (could be last line or error case)
			if (current.length() > 0) {
				String remaining = current.toString().trim();
				System.out.println("[joinLogicalLines] → Final leftover line: " + remaining);
				result.add(remaining);
			}

			System.out.println("[joinLogicalLines] --- END ---\n");

			return result;
		}

		// Splits lines that contain multiple statements (e.g. "} public ...") into
		// separate lines
		public List<String> splitMultipleStatements(List<String> lines) {
			List<String> result = new ArrayList<>();
			System.out.println("\n[splitMultipleStatements] --- START ---");
			for (String line : lines) {
				System.out.println("[splitMultipleStatements] Line in: " + line);
				String[] parts = line.split("(?<=\\})\\s+(?=public|private|protected)");
				for (String part : parts) {
					String trimmed = part.trim();
					if (!trimmed.isEmpty()) {
						System.out.println("[splitMultipleStatements] → Added: " + trimmed);
						result.add(trimmed);
					}
				}
			}
			System.out.println("[splitMultipleStatements] --- END ---\n");
			return result;
		}
}
