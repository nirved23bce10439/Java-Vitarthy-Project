# Storage format and recovery

`ledger.tsv` is a UTF-8 text file. Fields are separated by literal tabs, not
commas. Every record occupies one line. Blank lines are invalid. LF and CRLF
line endings are accepted.

The first line contains the format version and next unused ID:

```text
CEBT1<TAB>10
```

A transaction has seven fields:

```text
T<TAB>id<TAB>date<TAB>type<TAB>category<TAB>amount<TAB>note
```

A budget has four fields:

```text
B<TAB>month<TAB>category<TAB>limit
```

`<TAB>` above is explanatory notation. The sample file contains actual tabs.
Transaction IDs must be positive, unique, and less than the header's next ID.
Deleting the highest ID does not decrement the header: IDs are never reused in
an existing ledger. Each `(month, category)` budget pair must be unique.

Dates use ISO `LocalDate` text; months use ISO `YearMonth` text. Types and
categories must match their enum names exactly in the file. Amounts use plain
decimal text, a positive value, and at most two decimal places. Commas, quotation
marks, and Unicode text are valid in notes. Tabs, line breaks, and ISO control
characters are not. Notes are trimmed and limited to 100 Java characters.

Loading validates the entire file before a usable service is constructed. A
bad row produces `DataFormatException`, which is an `IOException` subtype.
Cross-record problems such as duplicate IDs may be reported near the last row,
because snapshot validation occurs after parsing all rows.

Saving creates a temporary file in the same directory, closes its writer, and
attempts an atomic replacement. If the filesystem does not support that move,
the application falls back to ordinary replacement. The service adopts the new
in-memory snapshot only after `save` returns successfully. This is useful error
handling, not a database transaction or a guarantee against power loss.

## Recovery procedure

1. Stop all tracker sessions using that directory.
2. Copy the full data directory to a separate backup location.
3. Inspect the error's line number and compare the row with the schema above.
4. Repair the copy or restore a known-good ledger. Do not discard real records
   just to suppress an error.
5. Run a summary against the repaired directory and check its totals before reuse.

The `.tracker.lock` file has no ledger information. Its existence alone does
not indicate a running app. The operating system manages the active file lock.
Do not edit ledger files externally or remove the lock file while a session runs.
