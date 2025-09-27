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
		if (line == null) return "";
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
					trimmed = trimmed.substring(trimmed.indexOf("*/") + 2).trim();
					if (trimmed.isEmpty()) continue;
				} else {
					continue;
				}
			}

			if (trimmed.contains("/*")) {
				inBlockComment = true;
				trimmed = trimmed.substring(0, trimmed.indexOf("/*")).trim();
			}

			if (trimmed.contains("//")) {
				trimmed = trimmed.substring(0, trimmed.indexOf("//")).trim();
			}

			if (trimmed.startsWith("@")) {
				continue; // Ignore annotations
			}

			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}
		return result;
	}

	// Joins lines that belong to the same logical declaration
	public List<String> joinLogicalLines(List<String> lines) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int parens = 0, angles = 0, braces = 0;

		for (String rawLine : lines) {
			String line = rawLine.trim();
			if (line.isEmpty()) continue;

			for (char c : line.toCharArray()) {
				if (c == '(') parens++;
				if (c == ')') parens--;
				if (c == '<') angles++;
				if (c == '>') angles--;
				if (c == '{') braces++;
				if (c == '}') braces--;
			}

			current.append(line).append(" ");
			boolean isCompleteLine = line.endsWith(";") || line.endsWith("{");

			if (parens <= 0 && angles <= 0 && isCompleteLine) {
				String completedLine = current.toString().trim();
				result.add(completedLine);
				current.setLength(0);
			}
		}

		if (current.length() > 0) {
			String remaining = current.toString().trim();
			result.add(remaining);
		}
		return result;
	}

	// Splits lines like "} public ..." into separate lines
	public List<String> splitMultipleStatements(List<String> lines) {
		List<String> result = new ArrayList<>();
		for (String line : lines) {
			String[] parts = line.split("(?<=\\})\\s+(?=public|private|protected)");
			for (String part : parts) {
				String trimmed = part.trim();
				if (!trimmed.isEmpty()) result.add(trimmed);
			}
		}
		return result;
	}

	// Split one string into top-level statements using ';' and '}' (string/comment safe)
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

	// Apply the above per logical line
	public List<String> splitTopLevelPerLine(List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String l : lines) {
			if (l == null) continue;
			List<String> parts = splitTopLevelStatements(l);
			if (parts.size() <= 1) out.add(l.trim()); else out.addAll(parts);
		}
		return out;
	}

	// Safer: split only real member headers (has '('), not control-flow, not class decls
	public List<String> separateMemberHeaders(List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String l : lines) {
			if (l == null) continue;
			String s = l.trim();
			if (s.isEmpty() || s.endsWith(";")) { out.add(s); continue; }
			String lower = s.toLowerCase(Locale.ROOT);
			if (!s.contains("(")) { out.add(s); continue; }
			if (lower.startsWith("if ") || lower.startsWith("for ") ||
				lower.startsWith("while ") || lower.startsWith("switch ") ||
				lower.startsWith("try ") || lower.startsWith("catch ") || lower.startsWith("finally ") ||
				lower.startsWith("class ") || lower.contains(" class ")) {
				out.add(s); continue;
			}

			int paren=0, bracket=0, brace=0, cut=-1;
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
			if (cut < 0) { out.add(s); continue; }
			String head = s.substring(0, cut+1).trim();
			String rest = s.substring(cut+1).trim();
			out.add(head);
			if (!rest.isEmpty()) out.add(rest);
		}
		return out;
	}

	// Keep but avoid fabricating braces unless you explicitly call it from the parser
	public List<String> closeDanglingMethodHeaders(List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String s : lines) {
			if (s == null) continue;
			String t = s.trim();
			if (t.isEmpty() || t.indexOf('{') < 0 || t.indexOf('}') >= 0) { out.add(t); continue; }
			if (!t.endsWith("{")) { out.add(t); continue; }
			String lower = t.toLowerCase(Locale.ROOT);
			if (lower.startsWith("if ") || lower.startsWith("for ") ||
				lower.startsWith("while ") || lower.startsWith("switch ") ||
				lower.startsWith("try ") || lower.startsWith("catch ") || lower.startsWith("finally ")) {
				out.add(t); continue;
			}
			int paren = 0; boolean ok = false;
			for (int i = 0; i < t.length(); i++) {
				char c = t.charAt(i);
				if (c == '(') paren++; else if (c == ')') paren--;
			}
			ok = (paren == 0) && t.contains("(") && t.endsWith("{");
			out.add(ok ? (t + "}") : t);
		}
		return out;
	}

	// Detect if body likely contains multi-commands
	public boolean detectMultiCommand(List<String> lines) {
		if (lines == null || lines.isEmpty()) return false;
		for (String s : lines) {
			if (s == null) continue;
			String t = s.trim();
			if (t.isEmpty()) continue;
			if (t.matches(".*;\\s*@?\\s*(?:package|import|public|protected|private|class|interface|enum|record)\\b.*")) return true;
			if (t.matches(".*\\)\\s*\\{\\s*\\S.*")) return true;
			if (t.matches(".*\\}\\s*@?\\s*(?:public|protected|private|class|interface|enum|record)\\b.*")) return true;
		}
		return false;
	}
	
	

	// Conservative pre-split: only very safe boundaries
	public List<String> preSplitLight(List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String s : lines) {
			if (s == null) continue;
			String t = s;
			t = t.replaceAll("(?<=;)\\s+(?=package\\b|import\\b)", "\n"); // package/import chains
			t = t.replaceAll("(?<=\\})\\s+(?=@?\\s*(public|protected|private|class|interface|enum|record|@))", "\n"); // } public ...
			for (String part : t.split("\\R+")) {
				String p = part.trim();
				if (!p.isEmpty()) out.add(p);
			}
		}
		return out;
	}

	// Safer: split header/body only if there is real inline body code
	public List<String> splitMethodBodies(List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String s : lines) {
			if (s == null) continue;
			String t = s.trim();
			if (t.isEmpty()) { out.add(t); continue; }

			String lower = t.toLowerCase(Locale.ROOT);
			if (!t.contains("(")) { out.add(t); continue; }
			if (lower.startsWith("if ") || lower.startsWith("for ") ||
				lower.startsWith("while ") || lower.startsWith("switch ") ||
				lower.startsWith("try ") || lower.startsWith("catch ") || lower.startsWith("finally ")) {
				out.add(t); continue;
			}

			int paren=0, brace=0, cut=-1;
			boolean inStr=false,inChar=false,esc=false;
			for (int i=0;i<t.length()-1;i++) {
				char c=t.charAt(i), nx=t.charAt(i+1);
				if (inStr){ if (esc) esc=false; else if (c=='\\') esc=true; else if (c=='"') inStr=false; continue; }
				if (inChar){ if (esc) esc=false; else if (c=='\\') esc=true; else if (c=='\'') inChar=false; continue; }
				if (c=='"'){ inStr=true; esc=false; continue; }
				if (c=='\''){ inChar=true; esc=false; continue; }
				if (c=='(') paren++;
				else if (c==')') paren=Math.max(0,paren-1);
				else if (c=='{') brace++;
				else if (c=='}') brace=Math.max(0,brace-1);
				if (paren==0 && brace==0 && c==')') {
					int j=i+1; while (j<t.length() && Character.isWhitespace(t.charAt(j))) j++;
					if (j<t.length() && t.charAt(j)=='{') { cut=j; break; }
				}
			}
			if (cut < 0) { out.add(t); continue; }

			String head = t.substring(0, cut+1).trim();
			String rest = t.substring(cut+1).trim();
			boolean hasStmt = rest.contains(";");
			if (hasStmt) {
				out.add(head);
				if (!rest.isEmpty()) out.add(rest);
			} else {
				out.add(t);
			}
		}
		return out;
	}
	
	
}