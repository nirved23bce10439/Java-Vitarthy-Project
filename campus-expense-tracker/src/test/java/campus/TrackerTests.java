package campus;

import campus.model.*;
import campus.service.TrackerService;
import campus.storage.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

/** Dependency-free behavior tests. Assertions run without the JVM's -ea flag. */
public final class TrackerTests {
    private static int passed;
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);
    private static final List<Path> temporaryDirectories = new ArrayList<Path>();

    private interface Test { void run() throws Exception; }

    private static final class MemoryStore implements LedgerStore {
        private Ledger data = Ledger.empty();
        private boolean fail;
        public Ledger load() { return data; }
        public void save(Ledger candidate) throws IOException {
            if (fail) throw new IOException("Simulated disk failure");
            data = candidate;
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            runTests();
            System.out.println("PASS: " + passed + " tests");
        } finally {
            for (Path dir : temporaryDirectories) {
                try (Stream<Path> paths = Files.walk(dir)) {
                    for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
                }
            }
        }
    }

    private static void runTests() throws Exception {
        test("decimal arithmetic is exact", () -> {
            TrackerService s = service();
            add(s, "0.10"); add(s, "0.20");
            equal("0.30", s.total(SEPTEMBER, TransactionType.EXPENSE).toPlainString());
        });
        test("invalid monetary text rejected", () -> {
            for (String value : new String[]{"0", "-1", "1.234", "1e3", "NaN", "1,000", "", "1000000000", ".50"}) {
                expect(IllegalArgumentException.class, () -> Money.parse(value));
            }
            equal("12.50", Money.parse(" 12.5 ").toPlainString());
        });
        test("domain amount validation", () -> {
            expect(IllegalArgumentException.class, () -> Money.positive(new BigDecimal("1.001")));
            expect(IllegalArgumentException.class, () -> Money.positive(null));
            expect(IllegalArgumentException.class, () -> Money.positive(new BigDecimal("1000000000")));
        });
        test("notes reject blanks controls and excessive length", () -> {
            for (String note : new String[]{" ", "bad\tnote", "bad\nnote", String.join("", Collections.nCopies(101, "x"))}) {
                expect(IllegalArgumentException.class, () -> new Transaction(1, DATE, TransactionType.EXPENSE, Category.FOOD, Money.parse("1"), note));
            }
        });
        test("calendar rejects invalid dates", () -> {
            expect(DateTimeException.class, () -> LocalDate.parse("2026-02-29"));
            equal(LocalDate.of(2024, 2, 29), LocalDate.parse("2024-02-29"));
        });
        test("empty month returns zero", () -> {
            equal("0.00", service().total(SEPTEMBER, TransactionType.EXPENSE).toPlainString());
        });
        test("month boundaries and income exclusion", () -> {
            TrackerService s = service();
            s.add(DATE.minusDays(1), TransactionType.EXPENSE, Category.FOOD, Money.parse("5"), "August");
            add(s, "10");
            s.add(LocalDate.of(2026, 9, 30), TransactionType.EXPENSE, Category.FOOD, Money.parse("20"), "End of month");
            s.add(DATE.plusMonths(1), TransactionType.EXPENSE, Category.FOOD, Money.parse("7"), "October");
            s.add(DATE, TransactionType.INCOME, Category.FOOD, Money.parse("100"), "Refund");
            equal("30.00", s.spending(SEPTEMBER).get(Category.FOOD).toPlainString());
            equal("100.00", s.total(SEPTEMBER, TransactionType.INCOME).toPlainString());
            equal(3, s.list(SEPTEMBER).size());
        });
        test("list sorted by date then ID", () -> {
            TrackerService s = service();
            s.add(DATE.plusDays(1), TransactionType.EXPENSE, Category.FOOD, Money.parse("1"), "Later");
            add(s, "2"); add(s, "3");
            equal(2, s.list(null).get(0).getId()); equal(3, s.list(null).get(1).getId());
            expect(UnsupportedOperationException.class, () -> s.list(null).clear());
        });
        test("budget replacement and month isolation", () -> {
            TrackerService s = service();
            s.setBudget(SEPTEMBER, Category.FOOD, Money.parse("100"));
            s.setBudget(SEPTEMBER, Category.FOOD, Money.parse("120"));
            s.setBudget(SEPTEMBER.plusMonths(1), Category.FOOD, Money.parse("90"));
            equal(1, s.budgets(SEPTEMBER).size());
            equal("120.00", s.budgets(SEPTEMBER).get(Category.FOOD).toPlainString());
            equal("90.00", s.budgets(SEPTEMBER.plusMonths(1)).get(Category.FOOD).toPlainString());
        });
        test("deletion preserves next ID across restart", () -> {
            MemoryStore store = new MemoryStore(); TrackerService s = new TrackerService(store);
            add(s, "1"); s.delete(1);
            TrackerService restarted = new TrackerService(store);
            equal(2, add(restarted, "1").getId());
            expect(IllegalArgumentException.class, () -> restarted.delete(99));
        });
        test("failed add does not commit or consume ID", () -> {
            MemoryStore store = new MemoryStore(); TrackerService s = new TrackerService(store); store.fail = true;
            expect(IOException.class, () -> add(s, "1")); equal(0, s.list(null).size());
            store.fail = false; equal(1, add(s, "1").getId());
        });
        test("failed delete and budget update preserve state", () -> {
            MemoryStore store = new MemoryStore(); TrackerService s = new TrackerService(store); add(s, "1");
            s.setBudget(SEPTEMBER, Category.FOOD, Money.parse("10")); store.fail = true;
            expect(IOException.class, () -> s.delete(1));
            expect(IOException.class, () -> s.setBudget(SEPTEMBER, Category.FOOD, Money.parse("20")));
            equal(1, s.list(null).size()); equal("10.00", s.budgets(SEPTEMBER).get(Category.FOOD).toPlainString());
        });
        test("snapshot protects source collections", () -> {
            List<Transaction> source = new ArrayList<Transaction>(); Ledger l = new Ledger(1, source, Collections.<Budget>emptyList());
            source.add(new Transaction(1, DATE, TransactionType.INCOME, Category.OTHER, Money.parse("1"), "Gift"));
            equal(0, l.getTransactions().size());
        });
        test("missing file loads empty", () -> equal(0, new FileLedgerStore(temp().resolve("ledger.tsv")).load().getTransactions().size()));
        test("UTF-8 file round trip and replacement", () -> {
            FileLedgerStore store = new FileLedgerStore(temp().resolve("nested/ledger.tsv")); TrackerService s = new TrackerService(store);
            s.add(DATE, TransactionType.EXPENSE, Category.STUDY, Money.parse("25.75"), "Caf\u00e9, \"notes\"");
            s.setBudget(SEPTEMBER, Category.STUDY, Money.parse("50"));
            TrackerService restarted = new TrackerService(store);
            equal("Caf\u00e9, \"notes\"", restarted.list(null).get(0).getNote());
            equal("50.00", restarted.budgets(SEPTEMBER).get(Category.STUDY).toPlainString());
            restarted.delete(1); equal(0, store.load().getTransactions().size()); equal(2, store.load().getNextId());
        });
        test("corrupt rows and headers rejected without changes", () -> {
            String[] invalid = {"", "WRONG\t1\n", "CEBT1\tx\n", "CEBT1\t0\n", "CEBT1\t1\nX\tbad\n",
                "CEBT1\t2\nT\t1\t2026-02-30\tEXPENSE\tFOOD\t1.00\tnote\n",
                "CEBT1\t2\nT\t1\t2026-09-01\tEXPENSE\tBAD\t1.00\tnote\n",
                "CEBT1\t2\nT\t1\t2026-09-01\tEXPENSE\tFOOD\t-1\tnote\n"};
            Path file = temp().resolve("ledger.tsv");
            for (String text : invalid) {
                Files.write(file, text.getBytes(StandardCharsets.UTF_8));
                expect(DataFormatException.class, () -> new FileLedgerStore(file).load());
                equal(text, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            }
        });
        test("duplicate IDs and invalid next ID rejected", () -> {
            String row = "T\t1\t2026-09-01\tEXPENSE\tFOOD\t1.00\tnote\n";
            corrupt("CEBT1\t2\n" + row + row); corrupt("CEBT1\t1\n" + row);
        });
        test("duplicate budgets rejected", () -> {
            String row = "B\t2026-09\tFOOD\t10.00\n"; corrupt("CEBT1\t1\n" + row + row);
        });
        test("file save failure leaves destination intact", () -> {
            Path destination = temp().resolve("ledger.tsv"); Files.createDirectory(destination);
            Files.write(destination.resolve("keep.txt"), Arrays.asList("keep"), StandardCharsets.UTF_8);
            expect(IOException.class, () -> new FileLedgerStore(destination).save(Ledger.empty()));
            check(Files.exists(destination.resolve("keep.txt")), "Existing destination changed");
        });
        test("sample totals and budget statuses", () -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            equal(0, execute(out, "", "--data", "sample-data", "--summary", "2026-09"));
            String text = out.toString("UTF-8");
            for (String expected : new String[]{"Income: 8000.00", "Expenses: 3595.75", "Net for month: 4404.25", "-15.75", "OVER BUDGET", "AT LIMIT", "WITHIN BUDGET", "NOT SET"}) {
                check(text.contains(expected), "Missing " + expected);
            }
        });
        test("CLI invalid options return status two", () -> {
            for (String[] options : new String[][]{{"--unknown"}, {"--data"}, {"--summary"}, {"--summary", "2026-13"}, {"--data", ""}}) {
                equal(2, execute(new ByteArrayOutputStream(), "", options));
            }
        });
        test("CLI help works without creating data", () -> {
            Path folder = temp().resolve("unused");
            equal(0, execute(new ByteArrayOutputStream(), "", "--data", folder.toString(), "--help"));
            check(Files.notExists(folder), "Help created a data directory");
        });
        test("interactive create budget summary and restart", () -> {
            Path dir = temp(); ByteArrayOutputStream out = new ByteArrayOutputStream();
            String input = "1\n2026-09-01\nexpense\nfood\n12.50\nLunch\n3\n2026-09\nfood\n10\n4\n2026-09\n0\n";
            equal(0, execute(out, input, "--data", dir.toString()));
            check(out.toString("UTF-8").contains("OVER BUDGET"), "Missing budget warning");
            out.reset(); equal(0, execute(out, "", "--data", dir.toString(), "--summary", "2026-09"));
            check(out.toString("UTF-8").contains("Expenses: 12.50"), "Data not persisted");
        });
        test("interactive invalid input recovers", () -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            equal(0, execute(out, "9\n1\nwrong-date\n4\n2026-09\n0\n", "--data", temp().toString()));
            check(out.toString("UTF-8").contains("Input error:"), "Missing input error");
            check(out.toString("UTF-8").contains("Expenses: 0.00"), "Did not recover");
        });
        test("EOF during add leaves no partial transaction", () -> {
            Path dir = temp(); equal(0, execute(new ByteArrayOutputStream(), "1\n2026-09-01\n", "--data", dir.toString()));
            equal(0, new FileLedgerStore(dir.resolve("ledger.tsv")).load().getTransactions().size());
        });
        test("delete confirmation cancels then deletes", () -> {
            Path dir = temp(); TrackerService s = new TrackerService(new FileLedgerStore(dir.resolve("ledger.tsv"))); add(s, "10");
            equal(0, execute(new ByteArrayOutputStream(), "5\n1\nno\n0\n", "--data", dir.toString()));
            equal(1, new FileLedgerStore(dir.resolve("ledger.tsv")).load().getTransactions().size());
            equal(0, execute(new ByteArrayOutputStream(), "5\n1\nYES\n0\n", "--data", dir.toString()));
            equal(0, new FileLedgerStore(dir.resolve("ledger.tsv")).load().getTransactions().size());
        });
        test("CLI corrupt file returns one without overwriting", () -> {
            Path dir = temp(); Path file = dir.resolve("ledger.tsv"); Files.write(file, "broken".getBytes(StandardCharsets.UTF_8));
            equal(1, execute(new ByteArrayOutputStream(), "0\n", "--data", dir.toString()));
            equal("broken", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        });
        test("directory lock rejects simultaneous session", () -> {
            Path dir = temp();
            try (FileChannel channel = FileChannel.open(dir.resolve(".tracker.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                check(lock.isValid(), "Test lock not acquired");
                equal(1, execute(new ByteArrayOutputStream(), "0\n", "--data", dir.toString()));
            }
            equal(0, execute(new ByteArrayOutputStream(), "0\n", "--data", dir.toString()));
        });
    }

    private static TrackerService service() throws IOException { return new TrackerService(new MemoryStore()); }
    private static Transaction add(TrackerService s, String amount) throws IOException {
        return s.add(DATE, TransactionType.EXPENSE, Category.FOOD, Money.parse(amount), "Lunch");
    }
    private static Path temp() throws IOException {
        Path dir = Files.createTempDirectory("campus-tracker-test-"); temporaryDirectories.add(dir); return dir;
    }
    private static void corrupt(String text) throws Exception {
        Path file = temp().resolve("ledger.tsv"); Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        expect(DataFormatException.class, () -> new FileLedgerStore(file).load());
    }
    private static int execute(ByteArrayOutputStream out, String input, String... args) throws Exception {
        PrintStream stream = new PrintStream(out, true, "UTF-8");
        return Main.execute(args, new StringReader(input), stream, stream);
    }
    private static void test(String name, Test action) throws Exception {
        try { action.run(); passed++; System.out.println("PASS " + name); }
        catch (Exception | AssertionError e) { throw new AssertionError("FAIL " + name, e); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) {
        check(Objects.equals(expected, actual), "Expected " + expected + ", got " + actual);
    }
    private static void expect(Class<? extends Throwable> type, Test action) throws Exception {
        try { action.run(); } catch (Throwable e) {
            if (type.isInstance(e)) return;
            throw new AssertionError("Expected " + type.getSimpleName() + ", got " + e, e);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
