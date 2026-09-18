[README.md](https://github.com/user-attachments/files/32389299/README.md)
# Campus Expense & Budget Tracker

A small Java command-line application for recording student income and expenses,
setting monthly category budgets, and checking where money goes. No IDE, database,
Maven, Gradle, or external Java library is needed.

## Student details

- **Name:** Nirved Nitin Kamble
- **Registration number:** 23BCE10439
- **Programme and year:** Computer Science, 4th Year
- **University:** VIT Bhopal

## Features

- Record dated income and expenses with a category, exact decimal amount, and note.
- List all transactions or filter by month, sorted by date and ID.
- Set or replace a category's monthly spending budget.
- View income, expenses, monthly net, and category budget status.
- Delete a mistaken transaction after explicit confirmation.
- Save automatically to a local UTF-8 file and load it on the next run.
- Generate a non-interactive monthly summary suitable for terminal evaluation.

All amounts are **INR**. Categories are FOOD, TRANSPORT, STUDY, HOSTEL, LEISURE,
and OTHER. Use OTHER for general income such as an allowance. Income does not
count as spending, even if assigned to an expense category.

## 1. Set up Java

Install a **JDK 8 or newer** (a JRE alone is insufficient). A JDK includes both
`java` and `javac`. Follow the [Eclipse Temurin installation instructions](https://adoptium.net/installation)
for your operating system. Make the JDK's `bin` directory available on `PATH`, or
set `JAVA_HOME` to the JDK folder when using the provided scripts.

Verify in a new terminal:

```text
java -version
javac -version
```

Both should report the intended JDK version. No additional dependencies need to
be installed. The scripts target Java 8 language syntax and bytecode using
`-source 8 -target 8`. The source uses Java 8 APIs and is tested on Java 8.
When adding features on a newer JDK, keep testing on Java 8 if you want to retain
that compatibility; the source/target flags alone do not restrict available APIs.

## 2. Open the project

Download and extract the repository ZIP, or clone your published repository.
Change into the directory containing this README, `sources.txt`, and `src/`.
Paths below are relative to this directory. The scripts themselves work from
any current directory and resolve data paths relative to the project directory.

## 3. Build, test, and run

### Windows PowerShell

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 build
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 test
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 run
```

`-ExecutionPolicy Bypass` applies only to that PowerShell process. If your
organization blocks scripts, use the manual commands below.

### macOS or Linux

```bash
bash run.sh build
bash run.sh test
bash run.sh run
```

The test command should finish with `PASS: 28 tests`. Every test is enabled by
default; `-ea` is unnecessary. Tests use temporary directories and synthetic data.

### Manual build (any operating system)

Create `build/classes` with `mkdir -p build/classes` on macOS/Linux, or
`New-Item -ItemType Directory -Force build/classes` in PowerShell. Then:

```text
javac -source 8 -target 8 -encoding UTF-8 -Xlint:all,-options -d build/classes @sources.txt
java -cp build/classes campus.Main
```

In **PowerShell**, quote the argument file: use `'@sources.txt'` instead of
`@sources.txt`. The app creates `data/` when it starts; the first successful
change creates `data/ledger.tsv`.

## 4. Run the included example

After building, this read-only summary leaves the sample ledger unchanged:

```text
java -cp build/classes campus.Main --data sample-data --summary 2026-09
```

Expected headline results:

```text
Monthly summary: 2026-09 (INR)
Income: 8000.00
Expenses: 3595.75
Net for month: 4404.25
```

FOOD spending is 215.75 against a 200.00 budget: remaining -15.75 and
`OVER BUDGET`. HOSTEL is `AT LIMIT`, TRANSPORT/STUDY/LEISURE are `WITHIN BUDGET`,
and OTHER is `NOT SET`. August and October transactions are excluded.
The full verified output is in [docs/DEMO_OUTPUT.txt](docs/DEMO_OUTPUT.txt).

For a scripted menu demonstration:

```powershell
Get-Content sample-data/demo-input.txt | java -cp build/classes campus.Main --data sample-data
```

```bash
java -cp build/classes campus.Main --data sample-data < sample-data/demo-input.txt
```

To experiment without changing the supplied sample, copy it first:

```powershell
New-Item -ItemType Directory -Force data
Copy-Item sample-data/ledger.tsv data/ledger.tsv
```

```bash
mkdir -p data
cp sample-data/ledger.tsv data/ledger.tsv
```

Only copy into a **new or intentionally reset** data directory: those copy
commands replace an existing ledger. Then start the ordinary interactive run.

## 5. Use the menu

| Choice | Action | Example input |
|---|---|---|
| 1 | Add transaction | `2026-09-17`, `EXPENSE`, `FOOD`, `75.50`, `Lunch` |
| 2 | List transactions | `2026-09` or blank for all months |
| 3 | Set/replace budget | `2026-09`, `FOOD`, `2000` |
| 4 | Monthly summary | `2026-09` |
| 5 | Delete transaction | ID such as `1`, then `YES` |
| 0 | Exit | No additional input |

Dates use `YYYY-MM-DD`, months use `YYYY-MM`. Type and category names are
case-insensitive. Amounts must be between 0.01 and 999999999.99 with at most two
decimal places; do not type commas, signs, or currency symbols. Notes contain
1-100 characters and cannot contain tabs or other control characters.
Invalid input cancels the current action and returns to the menu. End-of-input
exits cleanly, including halfway through entering a transaction.

Budgets apply to one category in one month. They do not carry forward. A new
limit replaces that category's old limit for the month. Missing budgets are
shown as `NOT SET`. Net for month means that month's income minus expenses;
it is not a bank balance and does not include carry-over money.

## Configuration, files, and failure handling

```text
java -cp build/classes campus.Main --help
java -cp build/classes campus.Main --data "my data" --summary 2026-09
```

`--data DIRECTORY` selects the storage directory (default: `data`). Relative
paths are resolved against the process's working directory when invoking Java
directly. There are no secrets, environment files, or database settings.

- `ledger.tsv` stores both transactions and budgets in one versioned snapshot.
- The application writes a temporary file and replaces the previous ledger.
  Where supported, the replacement is atomic. The fallback replacement is not
  guaranteed to survive an interrupted write; no power-loss durability is promised.
- A data-directory lock prevents two app sessions from writing the same ledger.
  The `.tracker.lock` file may remain after exit; the operating-system lock is
  released automatically. Do not remove it while the app runs.
- Invalid stored data stops startup with an error and is not overwritten.
  Back up the ledger before repairing it. See [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md).
- A storage failure stops the application with a nonzero exit status. Keep a
  separate backup of important data; this student project has no automatic backup.
- Exit codes: `0` successful run/help/EOF, `1` storage or lock failure,
  `2` invalid command-line arguments.
- The summary does not change ledger contents, but starting the app creates
  the directory and lock file if absent. A writable data directory is required.

## Repository map

```text
README.md                       Setup, run, and submission instructions
sources.txt                     Main-source compiler argument file
run.ps1 / run.sh                 Build, run, and test entry points
src/main/java/campus/
  Main.java                     Options, lifetime, locking, exit codes
  cli/ConsoleApp.java           Menu, prompts, and printed summaries
  model/                        Transaction, Budget, Ledger, enums, Money
  service/TrackerService.java   Add/delete, budgets, totals, month filtering
  storage/                     LedgerStore interface and TSV implementation
src/test/java/campus/TrackerTests.java
sample-data/                    Synthetic ledger and menu input
docs/PROJECT_REPORT.md           Editable structured report
docs/PROJECT_REPORT.pdf          Printable structured report
docs/LEARNING_GUIDE.md           Code walkthrough and viva preparation
docs/DATA_FORMAT.md              File format and recovery guidance
docs/TEST_RESULTS.txt            Captured local test output
docs/DEMO_OUTPUT.txt             Captured sample output
.github/workflows/java.yml       Windows/Linux checks for GitHub Actions
```

## Java course alignment

| Concept | Concrete implementation |
|---|---|
| Classes, objects, constructors | `Transaction`, `Budget`, `Ledger` |
| Encapsulation and immutability | Private final fields, validation, getters, defensive copies |
| Interfaces and polymorphism | `LedgerStore`; production file store and test memory store |
| Inheritance | `DataFormatException extends IOException` |
| Collections and generics | Lists, sets for uniqueness, enum maps for category totals |
| Exception handling | Invalid input, corrupt files, I/O failures, try-with-resources |
| File I/O | UTF-8 readers/writers, temporary files, snapshot replacement |
| Date/time and arithmetic | `LocalDate`, `YearMonth`, `BigDecimal` |
| Modular design | Separate model, service, storage, and terminal packages |
| Lambdas and sorting | `removeIf`, comparator chaining, date/ID ordering |

## Prepare the VITyarthi submission

1. Read the code, run the sample and tests, and make a small change you can explain.
2. Review [the report](docs/PROJECT_REPORT.md). Verify the included student details
   and complete the faculty/section, submission date, and published repository URL.
   Follow any report format or rubric shown on your course page; those were not supplied here.
3. Create a **public** GitHub repository named `campus-expense-tracker` (or another
   name you choose). Upload the **contents of this folder**, with README.md at
   the repository root. Do not upload personal `data/` or compiled `build/` files.
4. If using Git, run the commands below from this project folder. Replace
   `YOUR_USERNAME` with your GitHub username and create an empty public repository first:

   ```text
   git init -b main
   git add .
   git commit -m "Add campus expense tracker project"
   git remote add origin https://github.com/YOUR_USERNAME/campus-expense-tracker.git
   git push -u origin main
   ```

5. Confirm the root README and source files are visible while signed out. If
   GitHub Actions is enabled, check its test results after publishing.
6. Submit only the root link, in this form:
   `https://github.com/YOUR_USERNAME/campus-expense-tracker`.
   Do not submit a `/tree/` or `/blob/` link. Upload the finalized report separately.

This package was prepared with AI assistance. Review and customize it, understand
the implementation, and describe assistance truthfully according to your course
rules. The student details were supplied by the student; no sole-authorship declaration is made.
Repository publication and platform submission are still your final steps.

## Troubleshooting and scope

- `javac` missing: install a JDK and correct `PATH` or `JAVA_HOME`.
- Unsupported source version: select a JDK supporting Java 8 targets (8, 17, and 21 are CI targets).
- Class not found: build first and run from the repository root.
- Data directory in use: close the other session and retry.
- Invalid ledger: preserve a copy and repair the indicated row or restore a backup.
- Non-ASCII notes look wrong: use a UTF-8 terminal; the stored file is always UTF-8.

Designed for one student's small local ledger. It has no authentication,
encryption, bank integration, recurring entries, transaction editing, currency
conversion, or budget removal command. Correct an entry by deleting and adding
it again. Every mutation rewrites the small file; this is intentionally not a
large-scale database design.
