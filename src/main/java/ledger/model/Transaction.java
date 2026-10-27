package ledger.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("transactions")
public record Transaction(
        @Id UUID id,
        UUID idempotencyKey,
        String description,
        String status
) {
    public Transaction(UUID idempotencyKey, String description) {
        this(UUID.randomUUID(), idempotencyKey, description, "COMMITED");
    }
}
