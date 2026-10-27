package ledger.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Table("ledger_entries")
public record LedgerEntry(
        @Id UUID id,
        UUID transactionId,
        UUID accountId,
        BigDecimal amount
) {
    public LedgerEntry(UUID transactionId, UUID accountId, BigDecimal amount) {
        this(UUID.randomUUID(), transactionId, accountId, amount);
    }
}
