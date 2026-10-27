package ledger.repository;

import ledger.model.AccountBalance;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceRepository extends CrudRepository<AccountBalance, UUID> {

    @Query("SELECT * FROM account_balances WHERE account_id = :accountId FOR UPDATE")
    Optional<AccountBalance> findByIdForUpdate(@Param("accountId") UUID accountId);

    @Modifying
    @Query("UPDATE account_balances SET current_balance = current_balance - :amount, updated_at = CURRENT_TIMESTAMP WHERE account_id = :accountId")
    void deductBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE account_balances SET current_balance = current_balance + :amount, updated_at = CURRENT_TIMESTAMP WHERE account_id = :accountId")
    void addBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
}
