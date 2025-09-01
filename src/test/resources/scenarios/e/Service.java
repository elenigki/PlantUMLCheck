public class Service {
    public Repository save(Order o) { // param & return -> association
        return new Repository();
    }
    public void ping() {
        Temp t = new Temp(); // local new -> association
    }
}