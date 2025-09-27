// Save as: EnumAssocCheck.java
import java.util.*;

public class Order {
    private OrderStatus status;        // Expect: ASSOCIATION
    private List<OrderStatus> history; // Expect: ASSOCIATION

    public Order(OrderStatus s) {
        this.status = s;               // AGGREGATION path (setter/ctor) → ASSOCIATION due to enum guard
        this.history = new ArrayList<>(); // COMPOSITION path → ASSOCIATION due to enum guard (belt-and-suspenders)
    }

    public void setStatus(OrderStatus s) {
        this.status = s;               // AGGREGATION path → ASSOCIATION
    }
}

enum OrderStatus { NEW, PAID, SHIPPED }
