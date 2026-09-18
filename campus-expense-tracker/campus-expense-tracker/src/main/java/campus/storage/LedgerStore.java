package campus.storage;

import campus.model.Ledger;
import java.io.IOException;

/** The service depends on this contract, not on a particular file format. */
public interface LedgerStore {
    Ledger load() throws IOException;
    void save(Ledger ledger) throws IOException;
}
