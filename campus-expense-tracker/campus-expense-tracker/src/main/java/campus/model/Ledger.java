package campus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One immutable snapshot contains both transactions and budgets. */
public final class Ledger {
    private final int nextId;
    private final List<Transaction> transactions;
    private final List<Budget> budgets;

    public Ledger(int nextId, List<Transaction> transactions, List<Budget> budgets) {
        if (nextId <= 0) throw new IllegalArgumentException("Next ID must be positive.");
        Set<Integer> ids = new HashSet<Integer>();
        for (Transaction t : transactions) {
            if (t.getId() >= nextId || !ids.add(t.getId())) {
                throw new IllegalArgumentException("Duplicate ID or invalid next ID.");
            }
        }
        Set<String> keys = new HashSet<String>();
        for (Budget b : budgets) {
            if (!keys.add(b.key())) throw new IllegalArgumentException("Duplicate monthly category budget.");
        }
        this.nextId = nextId;
        this.transactions = Collections.unmodifiableList(new ArrayList<Transaction>(transactions));
        this.budgets = Collections.unmodifiableList(new ArrayList<Budget>(budgets));
    }

    public static Ledger empty() {
        return new Ledger(1, Collections.<Transaction>emptyList(), Collections.<Budget>emptyList());
    }
    public int getNextId() { return nextId; }
    public List<Transaction> getTransactions() { return transactions; }
    public List<Budget> getBudgets() { return budgets; }
}
