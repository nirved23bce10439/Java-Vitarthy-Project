# Understand and explain the project

## A 15-minute walkthrough

1. Read `Main`: arguments choose a data directory and optional summary month.
   The process locks that directory, opens the store, and starts the console.
2. Read `Transaction` and `Budget`: constructors enforce invariants. Private
   final fields prevent later accidental changes.
3. Follow menu option 1 into `TrackerService.add`: it creates an object and a
   candidate list, saves a new snapshot, then commits the in-memory reference.
4. Read `LedgerStore`: only `load` and `save` are promised. The service works
   with both `FileLedgerStore` and the tests' `MemoryStore` through this interface.
5. Trace one row through `FileLedgerStore.load` and `save`. Explain why tabs in
   notes must be rejected in this deliberately simple format.
6. Read `spending` and `budgets`: an `EnumMap` stores one value per category.
7. Run tests and locate the tests for failed saves, exact decimal sums, and dates
   at the start/end of a month.

## Questions you should be able to answer

**Why use BigDecimal?** Monetary inputs are decimal strings. `BigDecimal`
preserves exact decimal addition and subtraction, unlike binary floating-point
values such as `double`. The application rejects excess precision instead of
silently rounding user input.

**Where is polymorphism?** `TrackerService` accepts a `LedgerStore`, so its calls
dispatch to a file-backed implementation in the app and an in-memory
implementation in tests. The service does not need to know which is used.

**Why no Income and Expense subclasses?** Their stored fields and validation are
the same. An enum expresses their only required difference clearly. Artificial
subclasses would add files without distinct behavior. Exception inheritance is
used where it has a purpose: malformed ledger data is a kind of I/O failure.

**Why is the ledger immutable?** It prevents unrelated code from changing loaded
data outside the service. New candidate snapshots make the save-before-commit
rule easy to follow. The cost is copying lists for each change.

**What happens when saving fails?** The exception propagates, the service keeps
the previous snapshot, and the CLI exits with status 1. Ordinary filesystem
replacement still has limitations if the process or machine fails mid-operation.

**How is a month selected?** `YearMonth.from(transaction.getDate())` must equal
the requested month. This includes every day in that month, including leap days.

**Is monthly net my account balance?** No. It is only income minus expenses
recorded in that month. The app has no opening balance or bank connection.

**Why a single snapshot file?** Transactions and budgets move together during a
save. Two independent files could get out of step after one write succeeds and
the other fails. A single file is also easy to inspect for a small project.

**What is the complexity?** Loading and writing are O(n+b), with n transactions
and b budgets. Listing filters in O(n), then sorts k selected rows in O(k log k).
Summary routines reuse the sorted list, so the current summary implementation
also incurs O(n+k log k+b) work up to constant repeated passes. Memory is O(n+b).

**Why no JUnit?** The test runner uses ordinary Java methods that throw
`AssertionError` on failure. This keeps evaluation offline and dependency-free.
JUnit would be a sensible next step for a larger project.

## Meaningful customizations to try

- Add a PRINTING category, then update sample data, report, and tests.
- Add a note search method in the service and expose it through the menu.
- Implement a budget removal operation with a test for missing budgets.
- Add a text export command that never overwrites the ledger.

For each change, write down the problem it solves, which classes changed, how
you checked it, and one limitation. Do not merely rename classes to claim
authorship. Review your course rules about assistance and follow them.

## Short demonstration sequence

1. Show successful test output.
2. Run the supplied September summary; explain the overspent FOOD budget.
3. Start a fresh data directory and add an allowance and a lunch expense.
4. Set a FOOD budget, show the summary, then restart and show persisted data.
5. Enter an invalid amount and show that it is rejected.
6. Point to the `LedgerStore` interface and explain the two implementations.
