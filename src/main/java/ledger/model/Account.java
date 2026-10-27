package ledger.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("accounts")
public record Account(
        @Id UUID id,
        UUID userId,
        String currency,
        String status
) {
}
