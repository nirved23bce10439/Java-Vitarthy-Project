package campus.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

public final class Budget {
    private final YearMonth month;
    private final Category category;
    private final BigDecimal limit;

    public Budget(YearMonth month, Category category, BigDecimal limit) {
        this.month = Objects.requireNonNull(month, "Month is required.");
        this.category = Objects.requireNonNull(category, "Category is required.");
        this.limit = Money.positive(limit);
    }

    public YearMonth getMonth() { return month; }
    public Category getCategory() { return category; }
    public BigDecimal getLimit() { return limit; }
    public String key() { return month + ":" + category; }
}
