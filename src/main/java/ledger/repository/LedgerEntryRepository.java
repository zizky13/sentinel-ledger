package ledger.repository;

import ledger.model.LedgerEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface LedgerEntryRepository extends CrudRepository<LedgerEntry, UUID> {
}
