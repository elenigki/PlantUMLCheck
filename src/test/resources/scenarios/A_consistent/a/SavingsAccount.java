package a;

public class SavingsAccount extends Account {
    private double interestRate;

    public SavingsAccount(int id, double interestRate) {
        super(id);
        this.interestRate = interestRate;
    }

    public void addMonthlyInterest() {
        double interest = getBalance() * (interestRate / 12.0);
        deposit(interest);
    }
}
