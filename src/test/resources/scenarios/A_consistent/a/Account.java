package a;

public class Account implements Auditable {
    private int id;
    private double balance;

    public Account(int id) {
        this.id = id;
        this.balance = 0.0;
    }

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount > 0");
        balance += amount;
    }

    public boolean withdraw(double amount) {
        if (amount <= 0) return false;
        if (balance < amount) return false;
        balance -= amount;
        return true;
    }

    public int getId() { return id; }
    public double getBalance() { return balance; }

    @Override
    public String audit() {
        return "Account#" + id + " balance=" + balance;
    }
}
