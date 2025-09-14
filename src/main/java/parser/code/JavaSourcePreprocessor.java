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


			for (String rawLine : lines) {
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
					result.add(completedLine);
					current.setLength(0);
				}
			}

			// Add any remaining line (could be last line or error case)
			if (current.length() > 0) {
				String remaining = current.toString().trim();
				result.add(remaining);
			}


			return result;
		}

		// Splits lines that contain multiple statements (e.g. "} public ...") into
		// separate lines
		public List<String> splitMultipleStatements(List<String> lines) {
			List<String> result = new ArrayList<>();
			for (String line : lines) {
				String[] parts = line.split("(?<=\\})\\s+(?=public|private|protected)");
				for (String part : parts) {
					String trimmed = part.trim();
					if (!trimmed.isEmpty()) {
						result.add(trimmed);
					}
				}
			}
			return result;
		}
		
		// NEW: split one string into top-level statements using ';' and '}' (string/comment safe)
		public List<String> splitTopLevelStatements(String src) {
		    if (src == null || src.isEmpty()) return Collections.emptyList();
		    List<String> out = new ArrayList<>();
		    StringBuilder cur = new StringBuilder();
		    boolean inLine=false, inBlock=false, inStr=false, inChar=false, esc=false;
		    int paren=0, bracket=0, brace=0;
		    for (int i=0, n=src.length(); i<n; ) {
		        char c = src.charAt(i), nx = (i+1<n)? src.charAt(i+1) : '\0';
		        if (c=='\r' || c=='\n') { if (inLine) inLine=false; if (cur.length()>0 && cur.charAt(cur.length()-1)!=' ') cur.append(' '); i++; continue; }
		        if (inBlock) { if (c=='*' && nx=='/') { inBlock=false; i+=2; } else { i++; } continue; }
		        if (inLine) { i++; continue; }
		        if (!inStr && !inChar) { if (c=='/' && nx=='/') { inLine=true; i+=2; continue; } if (c=='/' && nx=='*') { inBlock=true; i+=2; continue; } }
		        if (!inChar && c=='"' && !inStr) { inStr=true; cur.append(c); i++; esc=false; continue; }
		        if (inStr) { cur.append(c); if (esc) esc=false; else if (c=='\\') esc=true; else if (c=='"') inStr=false; i++; continue; }
		        if (!inStr && c=='\'' && !inChar) { inChar=true; cur.append(c); i++; esc=false; continue; }
		        if (inChar) { cur.append(c); if (esc) esc=false; else if (c=='\\') esc=true; else if (c=='\'') inChar=false; i++; continue; }
		        if (c=='(') paren++; else if (c==')') paren=Math.max(0,paren-1);
		        else if (c=='[') bracket++; else if (c==']') bracket=Math.max(0,bracket-1);
		        else if (c=='{') brace++;
		        else if (c=='}') { cur.append(c); brace=Math.max(0,brace-1);
		            if (paren==0 && bracket==0 && brace==0) { String s=cur.toString().trim(); if(!s.isEmpty()) out.add(s); cur.setLength(0);
		                i++; while (i<n && Character.isWhitespace(src.charAt(i))) i++; continue; }
		            i++; continue;
		        }
		        if (c==';' && paren==0 && bracket==0 && brace==0) { cur.append(c); String s=cur.toString().trim(); if(!s.isEmpty()) out.add(s); cur.setLength(0);
		            i++; while (i<n && Character.isWhitespace(src.charAt(i))) i++; continue; }
		        cur.append(c); i++;
		    }
		    String tail=cur.toString().trim(); if(!tail.isEmpty()) out.add(tail);
		    return out;
		}

		// NEW: apply the above per logical line, flattening only when needed
		public List<String> splitTopLevelPerLine(List<String> lines) {
		    List<String> out = new ArrayList<>();
		    for (String l : lines) {
		        if (l == null) continue;
		        List<String> parts = splitTopLevelStatements(l);
		        if (parts.size() <= 1) out.add(l.trim()); else out.addAll(parts);
		    }
		    return out;
		}

		// NEW: if a method/ctor header and its '{' are on the same line, split the remainder to a new line
		public List<String> separateMemberHeaders(List<String> lines) {
		    List<String> out = new ArrayList<>();
		    for (String l : lines) {
		        if (l == null) continue;
		        String s = l.trim();
		        // quick reject: no '{' or looks like a field/stmt ending with ';'
		        if (s.indexOf('{') < 0 || s.endsWith(";")) { out.add(s); continue; }

		        // very light check: something like "... name(... ) {"
		        // (don’t try to be smart—just split at the FIRST top-level '{')
		        int paren = 0, bracket = 0, brace = 0, cut = -1;
		        boolean inStr=false,inChar=false,esc=false,inLine=false,inBlock=false;
		        for (int i=0;i<s.length();i++){
		            char c=s.charAt(i), nx=(i+1<s.length()?s.charAt(i+1):'\0');
		            if (c=='\n') { if (inLine) inLine=false; continue; }
		            if (inBlock){ if (c=='*'&&nx=='/'){inBlock=false; i++;} continue; }
		            if (inLine) continue;
		            if (!inStr && !inChar) {
		                if (c=='/'&&nx=='/'){inLine=true; i++; continue;}
		                if (c=='/'&&nx=='*'){inBlock=true; i++; continue;}
		            }
		            if (!inChar && c=='"' && !inStr){inStr=true; esc=false; continue;}
		            if (inStr){ if (esc) esc=false; else if (c=='\\') esc=true; else if (c=='"') inStr=false; continue; }
		            if (!inStr && c=='\'' && !inChar){inChar=true; esc=false; continue;}
		            if (inChar){ if (esc) esc=false; else if (c=='\\') esc=true; else if (c=='\'') inChar=false; continue;}

		            if (c=='(') paren++; else if (c==')') paren=Math.max(0,paren-1);
		            else if (c=='[') bracket++; else if (c==']') bracket=Math.max(0,bracket-1);
		            else if (c=='{') { if (paren==0 && bracket==0 && brace==0) { cut = i; break; } brace++; }
		            else if (c=='}') brace = Math.max(0, brace-1);
		        }
		        if (cut < 0) { out.add(s); continue; }                 // nothing to split
		        String head = s.substring(0, cut+1).trim();            // keep '{' with header
		        String rest = s.substring(cut+1).trim();               // move body start to next line
		        out.add(head);
		        if (!rest.isEmpty()) out.add(rest);
		    }
		    return out;
		}
		// NEW: if a top-level method/ctor header ends with '{' (and no '}' on the same line), append a '}'.
		public List<String> closeDanglingMethodHeaders(List<String> lines) {
		    List<String> out = new ArrayList<>();
		    for (String s : lines) {
		        if (s == null) continue;
		        String t = s.trim();

		        // quick rejects
		        if (t.isEmpty() || t.indexOf('{') < 0 || t.indexOf('}') >= 0) { out.add(t); continue; }
		        if (!t.endsWith("{")) { out.add(t); continue; }             // only care when it ends with '{'
		        if (t.contains(" if ") || t.startsWith("if ") ||            // avoid control blocks (defensive)
		            t.startsWith("for ") || t.contains(" for ") ||
		            t.startsWith("while ") || t.contains(" while ") ||
		            t.startsWith("switch ") || t.contains(" switch ") ||
		            t.startsWith("try ") || t.startsWith("catch ") ||
		            t.startsWith("finally ")) { out.add(t); continue; }

		        // heuristic: treat as member header when it has balanced (...) and ends with '{'
		        int paren = 0; boolean ok = false;
		        for (int i = 0; i < t.length(); i++) {
		            char c = t.charAt(i);
		            if (c == '(') paren++;
		            else if (c == ')') paren--;
		        }
		        ok = (paren == 0) && t.contains("(") && t.endsWith("{");
		        out.add(ok ? (t + "}") : t); // normalize: "header{ }" → "header{}"
		    }
		    return out;
		}

		// NEW: lightly split obvious multi-statements so regexes see clean lines
		public List<String> preSplitLight(List<String> lines) {
		    List<String> out = new ArrayList<>();
		    for (String s : lines) {
		        if (s == null) continue;
		        String t = s;

		        // split package/import chains: "...; import ..." and "...; package ..."
		        t = t.replaceAll("(?<=;)\\s+(?=package\\b|import\\b)", "\n");

		        // split after ';' when a new member/type/annotation likely starts
		        t = t.replaceAll(";\\s+(?=@?\\s*(public|protected|private|static|final|abstract|class|interface|enum|record|@))", ";\n");

		        // split after '}' when a new type/annotated decl likely follows
		        t = t.replaceAll("(?<=\\})\\s+(?=@?\\s*(public|protected|private|class|interface|enum|record|@))", "\n");

		        // flatten
		        for (String part : t.split("\\R+")) {
		            String p = part.trim();
		            if (!p.isEmpty()) out.add(p);
		        }
		    }
		    return out;
		}


		// Split method/ctor headers from their bodies: "…){ BODY" → ["…){", "BODY"]
		public List<String> splitMethodBodies(List<String> lines) {
		    List<String> out = new ArrayList<>();
		    for (String s : lines) {
		        if (s == null) continue;
		        String t = s.trim();
		        if (t.isEmpty()) { out.add(t); continue; }

		        // find top-level pattern ")\s*{" and split there (avoid control-flow)
		        int paren = 0, brace = 0, cut = -1;
		        boolean inStr=false,inChar=false,esc=false;
		        String lower = t.toLowerCase();
		        boolean looksLikeControl = lower.startsWith("if ") || lower.startsWith("for ")
		                || lower.startsWith("while ") || lower.startsWith("switch ")
		                || lower.startsWith("try ") || lower.startsWith("catch ") || lower.startsWith("finally ");

		        for (int i=0;i<t.length()-1;i++) {
		            char c = t.charAt(i), nx = t.charAt(i+1);
		            if (inStr) { if (esc) esc=false; else if (c=='\\') esc=true; else if (c=='"') inStr=false; continue; }
		            if (inChar){ if (esc) esc=false; else if (c=='\\') esc=true; else if (c=='\'') inChar=false; continue; }
		            if (c=='"') { inStr=true; esc=false; continue; }
		            if (c=='\''){ inChar=true; esc=false; continue; }

		            if (c=='(') paren++; else if (c==')') paren=Math.max(0, paren-1);
		            else if (c=='{') brace++; else if (c=='}') brace=Math.max(0, brace-1);

		            if (paren==0 && brace==0 && c==')') {
		                // skip spaces then check '{'
		                int j=i+1;
		                while (j<t.length() && Character.isWhitespace(t.charAt(j))) j++;
		                if (j<t.length() && t.charAt(j)=='{') { cut = j; break; }
		            }
		        }

		        if (!looksLikeControl && cut >= 0) {
		            String head = t.substring(0, cut+1).trim();  // include '{'
		            String rest = t.substring(cut+1).trim();     // body
		            out.add(head);
		            if (!rest.isEmpty()) out.add(rest);
		        } else {
		            out.add(t);
		        }
		    }
		    return out;
		}

}
