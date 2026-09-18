package campus;

import campus.cli.ConsoleApp;
import campus.service.TrackerService;
import campus.storage.FileLedgerStore;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.YearMonth;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        int status = execute(args, new InputStreamReader(System.in, StandardCharsets.UTF_8), System.out, System.err);
        if (status != 0) System.exit(status);
    }

    /** Return codes make both shell automation and integration tests straightforward. */
    public static int execute(String[] args, Reader input, PrintStream output, PrintStream error) {
        Path data = Paths.get("data");
        YearMonth summary = null;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--help":
                        output.println("Usage: campus.Main [--data DIRECTORY] [--summary YYYY-MM]");
                        output.println("Without --summary, starts the interactive menu. All amounts are INR.");
                        return 0;
                    case "--data":
                        if (++i == args.length) throw new IllegalArgumentException("--data needs a directory.");
                        if (args[i].trim().isEmpty()) throw new IllegalArgumentException("Data directory cannot be blank.");
                        data = Paths.get(args[i]); break;
                    case "--summary":
                        if (++i == args.length) throw new IllegalArgumentException("--summary needs YYYY-MM.");
                        summary = YearMonth.parse(args[i]); break;
                    default: throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            error.println("Argument error: " + e.getMessage());
            return 2;
        }

        try {
            Files.createDirectories(data);
            // Retain the lock file after exit; the operating-system lock is released on close.
            try (FileChannel channel = FileChannel.open(data.resolve(".tracker.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IOException("This data directory is already in use.");
                TrackerService service = new TrackerService(new FileLedgerStore(data.resolve("ledger.tsv")));
                ConsoleApp app = new ConsoleApp(service, input, output);
                if (summary == null) app.run(); else app.printSummary(summary);
            }
            return 0;
        } catch (IOException | OverlappingFileLockException e) {
            error.println("Cannot continue: " + e.getMessage());
            return 1;
        }
    }
}
