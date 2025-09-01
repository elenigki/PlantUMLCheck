public class Customer {
    private String name;
    private Item item; // field type -> ASSOCIATION
    public Customer(String name) { this.name = name; }
    public String getName() { return name; }
}