package campus.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable object: a validated transaction cannot change after creation. */
public final class Transaction {
    private final int id;
    private final LocalDate date;
    private final TransactionType type;
    private final Category category;
    private final BigDecimal amount;
    private final String note;

    public Transaction(int id, LocalDate date, TransactionType type, Category category,
                       BigDecimal amount, String note) {
        if (id <= 0) throw new IllegalArgumentException("ID must be positive.");
        this.id = id;
        this.date = Objects.requireNonNull(date, "Date is required.");
        this.type = Objects.requireNonNull(type, "Type is required.");
        this.category = Objects.requireNonNull(category, "Category is required.");
        this.amount = Money.positive(amount);
        if (note == null || note.trim().isEmpty() || note.trim().length() > 100) {
            throw new IllegalArgumentException("Note must contain 1 to 100 characters.");
        }
        for (char c : note.toCharArray()) {
            if (Character.isISOControl(c)) throw new IllegalArgumentException("Note cannot contain control characters.");
        }
        this.note = note.trim();
    }

    public int getId() { return id; }
    public LocalDate getDate() { return date; }
    public TransactionType getType() { return type; }
    public Category getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
}
