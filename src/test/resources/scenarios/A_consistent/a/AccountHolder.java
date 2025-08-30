package a;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AccountHolder {
    private String name;
    private List<Account> accounts = new ArrayList<>();

    public AccountHolder(String name) { this.name = name; }

    public void addAccount(Account account) { accounts.add(account); }
    public String getName() { return name; }
    public List<Account> getAccounts() { return Collections.unmodifiableList(accounts); }
}
