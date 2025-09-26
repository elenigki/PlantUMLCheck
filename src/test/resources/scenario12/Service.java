package scenario12;

public class Service {
    public Helper process(Helper h) {
        Helper tmp = new Helper(); // local usage
        return h != null ? h : tmp; // param + return only; no field
    }
}
