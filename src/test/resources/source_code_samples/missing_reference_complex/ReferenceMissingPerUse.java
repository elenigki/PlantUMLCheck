public class ReferenceMissingPerUse {

    private A a; // attribute → ASSOCIATION

    public B getB() { // return type → DEPENDENCY
        return null;
    }

    public void setC(C c) { // param → DEPENDENCY
        // no-op
    }

    public void doSomething() {
        D d = new D(); // local new → DEPENDENCY
    }
}
