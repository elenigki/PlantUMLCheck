package scenario01;

public class Car {
    private Engine engine;

    public Car() {
        this.engine = new Engine();
    }

    public void service() {
        this.engine = new Engine();
    }
}
