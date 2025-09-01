public class Car {
    private Engine engine;
    public Car() {
        this.engine = new Engine(); // composition: new assigned to field
    }
    public void service() {
        this.engine = new Engine(); // composition again
    }
}