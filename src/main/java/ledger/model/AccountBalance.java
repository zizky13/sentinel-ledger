package ledger.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Table("account_balances")
public record AccountBalance(
        @Id UUID accountId,
        BigDecimal currentBalance
) {
    public AccountBalance add(BigDecimal amount) {
        return new AccountBalance(this.accountId, this.currentBalance.add(amount));
    }

    public AccountBalance subtract(BigDecimal amount) {
        return new AccountBalance(this.accountId, this.currentBalance.subtract(amount));
    }
}
