package edu.UOI.plantumlcheck.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import parser.code.JavaProjectScanner;
import parser.code.ScannedJavaInfo;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Saves uploads to a temp workspace and produces a package->classes map for the UI.
 * Strategy:
 *  1) Try user's JavaProjectScanner with common method names.
 *  2) If it fails, do a lightweight text scan to extract package and class names.
 * In all cases we return a Map<String, List<String>> ready for rendering.
 */
@Service
public class JavaScanService {

    /** Tiny holder for saved workspace. */
    public static final class Workspace {
        private final Path root;
        private final List<Path> savedFiles;
        public Workspace(Path root, List<Path> savedFiles) { this.root = root; this.savedFiles = savedFiles; }
        public Path root() { return root; }
        public List<Path> savedFiles() { return savedFiles; }
    }

    /** Save source files into a unique temp workspace. */
    public Workspace saveSourceToWorkspace(MultipartFile[] sourceFiles) throws IOException {
        if (sourceFiles == null || sourceFiles.length == 0) throw new IllegalArgumentException("No source files to save");

        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
        Path root = Files.createTempDirectory("plantumlcheck-src-" + stamp + "-");

        List<Path> saved = new ArrayList<>();
        for (MultipartFile mf : sourceFiles) {
            if (mf == null || mf.isEmpty()) continue;

            String raw = (mf.getOriginalFilename() != null && !mf.getOriginalFilename().isBlank())
                    ? mf.getOriginalFilename() : "file.java";
            String safeName = Paths.get(raw).getFileName().toString();
            Path dest = root.resolve(safeName);

            Files.createDirectories(dest.getParent());
            try (InputStream in = mf.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            saved.add(dest);
        }
        return new Workspace(root, saved);
    }

    /** Public entry: produce a package->classes map; never returns null. */
    public Map<String, List<String>> buildPackageMap(Path workspaceRoot) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        try {
            List<ScannedJavaInfo> infos = tryScanner(workspaceRoot);
            if (infos != null && !infos.isEmpty()) {
                return toPackageMap(infos);
            }
        } catch (Exception e) {
            // fall through to lightweight scan
        }
        // Fallback: lightweight text scan
        Map<String, List<String>> map = lightweightScan(workspaceRoot);
        // sort class lists
        map.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        return map;
    }

    // ---------- Strategy 1: use user's JavaProjectScanner (reflection to be compatible) ----------

    private List<ScannedJavaInfo> tryScanner(Path root) throws Exception {
        JavaProjectScanner scanner = new JavaProjectScanner();

        // Try these signatures in order; comment out/add if your API differs
        // 1) List<ScannedJavaInfo> scan(File root)
        try {
            Method scan = JavaProjectScanner.class.getMethod("scan", File.class);
            Object result = scan.invoke(scanner, root.toFile());
            if (result instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<ScannedJavaInfo> out = (List<ScannedJavaInfo>) list;
                return out;
            }
        } catch (NoSuchMethodException ignore) {}

        // 2) List<ScannedJavaInfo> quickScan(File root)
        try {
            Method quickScan = JavaProjectScanner.class.getMethod("quickScan", File.class);
            Object result = quickScan.invoke(scanner, root.toFile());
            if (result instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<ScannedJavaInfo> out = (List<ScannedJavaInfo>) list;
                return out;
            }
        } catch (NoSuchMethodException ignore) {}

        // 3) Fallback per-file: ScannedJavaInfo scanFile(File javaFile)
        try {
            Method scanFile = JavaProjectScanner.class.getMethod("scanFile", File.class);
            try (Stream<Path> stream = Files.walk(root)) {
                return stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .map(Path::toFile)
                        .map(f -> {
                            try {
                                Object info = scanFile.invoke(scanner, f);
                                return (info instanceof ScannedJavaInfo s) ? s : null;
                            } catch (Exception ex) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (NoSuchMethodException ignore) {}

        // If nothing matched, indicate no result; caller will do fallback
        return List.of();
    }

    /** Convert ScannedJavaInfo list to package->classes map. */
    private Map<String, List<String>> toPackageMap(List<ScannedJavaInfo> infos) {
        Map<String, List<String>> map = new TreeMap<>();
        for (ScannedJavaInfo s : infos) {
            String pkg = safe(s.getPackageName());
            String cls = firstNonBlank(s.getClassName(), s.getFullyQualifiedName(), "UnknownClass");
            map.computeIfAbsent(pkg, k -> new ArrayList<>()).add(cls);
        }
        return map;
    }

    private static String safe(String v) { return v == null ? "" : v; }
    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return "";
    }

    // ---------- Strategy 2: lightweight scan (no dependencies) ----------

    private Map<String, List<String>> lightweightScan(Path root) {
        Map<String, List<String>> map = new TreeMap<>();
        Pattern pkgRe = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_\\.]+)\\s*;", Pattern.MULTILINE);
        Pattern clsRe = Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();

            for (Path p : javaFiles) {
                String content = readUpTo(p, 64 * 1024); // read first 64KB
                String pkg = "";
                Matcher pm = pkgRe.matcher(content);
                if (pm.find()) pkg = pm.group(1);

                // find first type declaration (simple heuristic)
                String cls = null;
                Matcher cm = clsRe.matcher(content);
                if (cm.find()) cls = cm.group(2);
                if (cls == null) cls = stripExt(p.getFileName().toString());

                map.computeIfAbsent(pkg, k -> new ArrayList<>()).add(cls);
            }
        } catch (IOException e) {
            // return what we have (maybe empty)
        }
        return map;
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static String readUpTo(Path file, int maxBytes) throws IOException {
        byte[] buf = Files.readAllBytes(file);
        int len = Math.min(buf.length, maxBytes);
        return new String(buf, 0, len, StandardCharsets.UTF_8);
    }
}
