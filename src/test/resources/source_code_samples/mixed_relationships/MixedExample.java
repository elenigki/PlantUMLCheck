public class MixedExample {

    private Book book; // ASSOCIATION
    private Page page = new Page(); // COMPOSITION
    private Chapter ch; // AGGREGATION

    public MixedExample(Chapter ch) {
        this.ch = ch;
    }

    public void print(Printer p) { // DEPENDENCY
        Scanner sc = new Scanner(); // DEPENDENCY
    }
}
