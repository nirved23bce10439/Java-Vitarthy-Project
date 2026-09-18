package campus.storage;

import campus.model.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** UTF-8 TSV snapshot. Malformed data is rejected, never silently skipped. */
public final class FileLedgerStore implements LedgerStore {
    private final Path file;

    public FileLedgerStore(Path file) { this.file = file.toAbsolutePath().normalize(); }

    @Override
    public Ledger load() throws IOException {
        if (Files.notExists(file)) return Ledger.empty();
        int lineNumber = 1;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) throw new IllegalArgumentException("Missing header.");
            String[] h = header.split("\t", -1);
            if (h.length != 2 || !h[0].equals("CEBT1")) throw new IllegalArgumentException("Expected CEBT1 header.");
            int nextId = Integer.parseInt(h[1]);
            List<Transaction> transactions = new ArrayList<Transaction>();
            List<Budget> budgets = new ArrayList<Budget>();
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] f = line.split("\t", -1);
                if (f.length == 7 && f[0].equals("T")) {
                    transactions.add(new Transaction(Integer.parseInt(f[1]), LocalDate.parse(f[2]),
                        TransactionType.valueOf(f[3]), Category.valueOf(f[4]), Money.parse(f[5]), f[6]));
                } else if (f.length == 4 && f[0].equals("B")) {
                    budgets.add(new Budget(YearMonth.parse(f[1]), Category.valueOf(f[2]), Money.parse(f[3])));
                } else {
                    throw new IllegalArgumentException("Invalid record type or field count.");
                }
            }
            return new Ledger(nextId, transactions, budgets);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw new DataFormatException("Invalid ledger at/near line " + lineNumber + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void save(Ledger ledger) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "ledger-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write("CEBT1\t" + ledger.getNextId());
                writer.newLine();
                for (Transaction t : ledger.getTransactions()) {
                    writer.write("T\t" + t.getId() + "\t" + t.getDate() + "\t" + t.getType() + "\t"
                        + t.getCategory() + "\t" + t.getAmount().toPlainString() + "\t" + t.getNote());
                    writer.newLine();
                }
                for (Budget b : ledger.getBudgets()) {
                    writer.write("B\t" + b.getMonth() + "\t" + b.getCategory() + "\t" + b.getLimit().toPlainString());
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Some filesystems lack atomic moves; document the weaker crash guarantee.
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
