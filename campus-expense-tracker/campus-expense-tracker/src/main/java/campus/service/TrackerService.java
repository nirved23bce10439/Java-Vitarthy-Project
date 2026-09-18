package campus.service;

import campus.model.*;
import campus.storage.LedgerStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Business rules live here so the CLI and storage remain small. */
public final class TrackerService {
    private final LedgerStore store;
    private Ledger ledger;

    public TrackerService(LedgerStore store) throws IOException {
        this.store = store;
        this.ledger = store.load();
    }

    public Transaction add(LocalDate date, TransactionType type, Category category,
                           BigDecimal amount, String note) throws IOException {
        if (ledger.getNextId() == Integer.MAX_VALUE) throw new IllegalArgumentException("ID capacity reached.");
        Transaction t = new Transaction(ledger.getNextId(), date, type, category, amount, note);
        List<Transaction> updated = new ArrayList<Transaction>(ledger.getTransactions());
        updated.add(t);
        commit(new Ledger(ledger.getNextId() + 1, updated, ledger.getBudgets()));
        return t;
    }

    public void delete(int id) throws IOException {
        List<Transaction> updated = new ArrayList<Transaction>(ledger.getTransactions());
        if (!updated.removeIf(t -> t.getId() == id)) throw new IllegalArgumentException("No transaction with ID " + id + ".");
        commit(new Ledger(ledger.getNextId(), updated, ledger.getBudgets()));
    }

    public void setBudget(YearMonth month, Category category, BigDecimal limit) throws IOException {
        Budget budget = new Budget(month, category, limit);
        List<Budget> updated = new ArrayList<Budget>(ledger.getBudgets());
        updated.removeIf(b -> b.key().equals(budget.key()));
        updated.add(budget);
        commit(new Ledger(ledger.getNextId(), ledger.getTransactions(), updated));
    }

    private void commit(Ledger candidate) throws IOException {
        store.save(candidate);
        ledger = candidate; // A failed save must not change the in-memory view.
    }

    public List<Transaction> list(YearMonth month) {
        List<Transaction> result = new ArrayList<Transaction>();
        for (Transaction t : ledger.getTransactions()) {
            if (month == null || YearMonth.from(t.getDate()).equals(month)) result.add(t);
        }
        result.sort(Comparator.comparing(Transaction::getDate).thenComparingInt(Transaction::getId));
        return Collections.unmodifiableList(result);
    }

    public BigDecimal total(YearMonth month, TransactionType type) {
        BigDecimal sum = Money.ZERO;
        for (Transaction t : list(month)) {
            if (t.getType() == type) sum = sum.add(t.getAmount());
        }
        return sum;
    }

    public Map<Category, BigDecimal> spending(YearMonth month) {
        Map<Category, BigDecimal> totals = new EnumMap<Category, BigDecimal>(Category.class);
        for (Category c : Category.values()) totals.put(c, Money.ZERO);
        for (Transaction t : list(month)) {
            if (t.getType() == TransactionType.EXPENSE) {
                totals.put(t.getCategory(), totals.get(t.getCategory()).add(t.getAmount()));
            }
        }
        return totals;
    }

    public Map<Category, BigDecimal> budgets(YearMonth month) {
        Map<Category, BigDecimal> result = new EnumMap<Category, BigDecimal>(Category.class);
        for (Budget b : ledger.getBudgets()) {
            if (b.getMonth().equals(month)) result.put(b.getCategory(), b.getLimit());
        }
        return result;
    }
}
