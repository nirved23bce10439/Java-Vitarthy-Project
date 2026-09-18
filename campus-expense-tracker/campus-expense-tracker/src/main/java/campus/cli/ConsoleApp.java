package campus.cli;

import campus.model.*;
import campus.service.TrackerService;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ConsoleApp {
    private final TrackerService service;
    private final BufferedReader input;
    private final PrintStream output;

    public ConsoleApp(TrackerService service, Reader input, PrintStream output) {
        this.service = service;
        this.input = new BufferedReader(input);
        this.output = output;
    }

    public void run() throws IOException {
        output.println("Campus Expense & Budget Tracker | Currency: INR");
        while (true) {
            output.println("\n1 Add transaction  2 List transactions  3 Set budget");
            output.println("4 Monthly summary  5 Delete transaction  0 Exit");
            try {
                String choice = ask("Choice: ");
                switch (choice) {
                    case "1": add(); break;
                    case "2":
                        String filter = ask("Month (YYYY-MM), or blank for all: ");
                        printTransactions(filter.isEmpty() ? null : YearMonth.parse(filter));
                        break;
                    case "3":
                        YearMonth month = YearMonth.parse(ask("Budget month (YYYY-MM): "));
                        Category category = category();
                        BigDecimal limit = Money.parse(ask("Limit (INR): "));
                        service.setBudget(month, category, limit);
                        output.println("Budget saved.");
                        break;
                    case "4": printSummary(YearMonth.parse(ask("Month (YYYY-MM): "))); break;
                    case "5":
                        int id = Integer.parseInt(ask("Transaction ID: "));
                        if (ask("Delete ID " + id + "? Type YES: ").equals("YES")) {
                            service.delete(id);
                            output.println("Transaction deleted.");
                        } else output.println("Deletion cancelled.");
                        break;
                    case "0": output.println("Goodbye. All successful changes are saved."); return;
                    default: output.println("Choose a menu number from 0 to 5.");
                }
            } catch (EOFException e) {
                output.println("\nInput closed. Goodbye.");
                return;
            } catch (IllegalArgumentException | DateTimeException e) {
                output.println("Input error: " + e.getMessage());
            } catch (IOException e) {
                output.println("Storage/input error: " + e.getMessage());
                throw e; // Stop rather than continue after uncertain storage or input failure.
            }
        }
    }

    private void add() throws IOException {
        LocalDate date = LocalDate.parse(ask("Date (YYYY-MM-DD): "));
        TransactionType type = TransactionType.valueOf(ask("Type (INCOME/EXPENSE): ").toUpperCase(Locale.ROOT));
        Category category = category();
        BigDecimal amount = Money.parse(ask("Amount (INR): "));
        String note = ask("Note (1-100 characters): ");
        Transaction t = service.add(date, type, category, amount, note);
        output.println("Saved transaction #" + t.getId() + ".");
    }

    private Category category() throws IOException {
        return Category.valueOf(ask("Category " + Arrays.toString(Category.values()) + ": ").toUpperCase(Locale.ROOT));
    }

    private String ask(String prompt) throws IOException {
        output.print(prompt);
        output.flush();
        String text = input.readLine();
        if (text == null) throw new EOFException();
        return text.trim();
    }

    public void printTransactions(YearMonth month) {
        List<Transaction> transactions = service.list(month);
        if (transactions.isEmpty()) { output.println("No transactions found."); return; }
        output.printf("%-5s %-10s %-7s %-10s %14s  %s%n", "ID", "DATE", "TYPE", "CATEGORY", "INR", "NOTE");
        for (Transaction t : transactions) {
            output.printf("%-5d %-10s %-7s %-10s %14s  %s%n", t.getId(), t.getDate(), t.getType(),
                t.getCategory(), t.getAmount().toPlainString(), t.getNote());
        }
    }

    public void printSummary(YearMonth month) {
        BigDecimal income = service.total(month, TransactionType.INCOME);
        BigDecimal expense = service.total(month, TransactionType.EXPENSE);
        output.println("Monthly summary: " + month + " (INR)");
        output.println("Income: " + income.toPlainString());
        output.println("Expenses: " + expense.toPlainString());
        output.println("Net for month: " + income.subtract(expense).toPlainString());
        output.printf("%-10s %14s %14s %14s  %s%n", "CATEGORY", "SPENT", "BUDGET", "REMAINING", "STATUS");
        Map<Category, BigDecimal> spending = service.spending(month);
        Map<Category, BigDecimal> budgets = service.budgets(month);
        for (Category category : Category.values()) {
            BigDecimal spent = spending.get(category);
            BigDecimal limit = budgets.get(category);
            if (limit == null) {
                output.printf("%-10s %14s %14s %14s  %s%n", category, spent, "-", "-", "NOT SET");
            } else {
                BigDecimal remaining = limit.subtract(spent);
                String status = remaining.signum() < 0 ? "OVER BUDGET" : remaining.signum() == 0 ? "AT LIMIT" : "WITHIN BUDGET";
                output.printf("%-10s %14s %14s %14s  %s%n", category, spent, limit, remaining, status);
            }
        }
    }
}
