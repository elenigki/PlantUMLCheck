package comparison.rules;

// Decides if two types are equal/compatible.
public final class TypeRules {
    private TypeRules() {}

    // Strict type equality (trim + collapse spaces).
    public static boolean equalStrict(String a, String b) {
        return norm(a).equals(norm(b));
    }

    // Relaxed compatibility (wrapper/fqcn/simple, generic erasure, varargs≈array, arrays).
    public static boolean equalRelaxed(String a, String b) {
        String A = norm(a), B = norm(b);

        // normalize varargs to arrays
        A = toArrayForm(A);
        B = toArrayForm(B);

        // fast path
        if (A.equals(B)) return true;

        // erase generics and compare
        String Ae = eraseGenerics(A), Be = eraseGenerics(B);
        if (Ae.equals(Be)) return true;

        // compare simple names after erasure
        if (simple(Ae).equals(simple(Be))) return true;

        // arrays: compare base types with relaxed equality + same dims
        ArrayInfo ai = splitArray(Ae);
        ArrayInfo bi = splitArray(Be);
        if (ai.dims == bi.dims) {
            if (baseTypesRelaxedEqual(ai.base, bi.base)) return true;
        }

        // final fallback: relaxed base check (covers simple primitive/wrapper paths)
        return baseTypesRelaxedEqual(Ae, Be);
    }

    // --- helpers ---

    // Normalizes whitespace.
    public static String norm(String t) {
        if (t == null) return "";
        return t.trim().replaceAll("\\s+", "");
    }

    // Converts varargs to arrays (e.g., int... -> int[]).
    private static String toArrayForm(String t) {
        return t.replace("...", "[]");
    }

    // Removes simple generic parts (good enough for matching).
    public static String eraseGenerics(String t) {
        return t.replaceAll("<[^>]*>", "");
    }

    // Returns the simple (unqualified) name.
    public static String simple(String t) {
        int i = t.lastIndexOf('.');
        return (i < 0) ? t : t.substring(i + 1);
    }

    //Compares base types using relaxed rules.
    private static boolean baseTypesRelaxedEqual(String a, String b) {
        String A = simple(a), B = simple(b);
        if (primitiveWrapperPair(A, B)) return true;
        return A.equals(B);
    }

    // Checks primitive/wrapper pairs by simple name.
    private static boolean primitiveWrapperPair(String a, String b) {
        return pair(a,b,"int","Integer") ||
               pair(a,b,"long","Long") ||
               pair(a,b,"double","Double") ||
               pair(a,b,"float","Float") ||
               pair(a,b,"boolean","Boolean") ||
               pair(a,b,"char","Character") ||
               pair(a,b,"byte","Byte") ||
               pair(a,b,"short","Short");
    }

    private static boolean pair(String a, String b, String p, String w) {
        return (a.equals(p) && b.equals(w)) || (b.equals(p) && a.equals(w));
    }

    // Splits an array type into base + dimensions.
    private static ArrayInfo splitArray(String t) {
        int dims = 0;
        while (t.endsWith("[]")) {
            t = t.substring(0, t.length() - 2);
            dims++;
        }
        return new ArrayInfo(t, dims);
    }

    // Small holder for array info.
    private static final class ArrayInfo {
        final String base;
        final int dims;
        ArrayInfo(String base, int dims) { this.base = base; this.dims = dims; }
    }
}
