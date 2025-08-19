package testutil;

import model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import comparison.CheckMode;

/** Tiny helpers to build models in tests (matches your real model). */
public final class TestModelBuilder {
    private TestModelBuilder() {}

    // ---- model ----

    /** New code-side model. */
    public static IntermediateModel codeModel() {
        return new IntermediateModel(ModelSource.SOURCE_CODE);
    }

    /** New UML-side model. */
    public static IntermediateModel umlModel() {
        return new IntermediateModel(ModelSource.PLANTUML_SCRIPT);
    }

    // ---- classes ----

    /** Creates class and adds it to model (declaration not needed in tests). */
    public static ClassInfo addClass(IntermediateModel m, String name, ClassType type) {
        ClassInfo c = new ClassInfo(name, type, (ClassDeclaration) null);
        m.addClass(c);
        return c;
    }

    /** Same as above, but lets you mark it abstract. */
    public static ClassInfo addClass(IntermediateModel m, String name, ClassType type, boolean isAbstract) {
        ClassInfo c = new ClassInfo(name, type, isAbstract, (ClassDeclaration) null);
        m.addClass(c);
        return c;
    }

    // ---- attributes ----

    /** Adds an attribute (uses real ctor with visibility). */
    public static Attribute addAttr(ClassInfo cls, String name, String type, String vis) {
        Attribute a = new Attribute(name, type, vis);
        cls.addAttribute(a);
        return a;
    }

    // ---- methods ----

    /** Adds a method with params (ArrayList per your ctor). */
    public static Method addMethod(ClassInfo cls, String name, String returnType, String vis, String... paramTypes) {
        ArrayList<String> params = (paramTypes == null)
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(paramTypes));
        Method m = new Method(name, returnType, params, vis);
        cls.addMethod(m);
        return m;
    }

    /** Adds a method with no params. */
    public static Method addMethod(ClassInfo cls, String name, String returnType, String vis) {
        Method m = new Method(name, returnType, vis);
        cls.addMethod(m);
        return m;
    }

    // ---- relationships ----

    /** Adds a relationship A -> B of given type. */
    public static Relationship addRel(IntermediateModel m, ClassInfo from, ClassInfo to, RelationshipType type) {
        Relationship r = new Relationship(from, to, type);
        m.addRelationship(r);
        return r;
    }

    /** Adds a relationship and sets per-edge strictness if needed. */
    public static Relationship addRel(IntermediateModel m, ClassInfo from, ClassInfo to, RelationshipType type, CheckMode strictness) {
        Relationship r = new Relationship(from, to, type);
        r.setStrictnessLevel(strictness);
        m.addRelationship(r);
        return r;
    }

    // ---- convenience ----

    /** Finds a class by name via model. */
    public static ClassInfo find(IntermediateModel m, String name) {
        return m.findClassByName(name);
    }
}
