package campus.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Monetary input is decimal text; binary floating point is never used. */
public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private Money() { }

    public static BigDecimal parse(String text) {
        if (text == null || !text.trim().matches("[0-9]{1,9}(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Use a positive amount with up to 9 whole digits and 2 decimal places.");
        }
        return positive(new BigDecimal(text.trim()));
    }

    public static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(new BigDecimal("999999999.99")) > 0) {
            throw new IllegalArgumentException("Amount must be between 0.01 and 999999999.99.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount must have at most two decimal places.", e);
        }
    }
}
