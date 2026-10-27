package ledger.service;

import ledger.model.AccountBalance;
import ledger.model.LedgerEntry;
import ledger.model.Transaction;
import ledger.repository.AccountBalanceRepository;
import ledger.repository.LedgerEntryRepository;
import ledger.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {
    private final AccountBalanceRepository balanceRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final TransactionRepository transactionRepository;

    public TransferService(AccountBalanceRepository balanceRepository, LedgerEntryRepository ledgerRepository, TransactionRepository transactionRepository) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void executeTransfer(UUID sourceId, UUID targetId, BigDecimal amount, UUID idempotencyKey, String description) {
        // lexicographical sorting to prevent deadlock
        UUID firstLock = sourceId.compareTo(targetId) < 0 ? sourceId : targetId;
        UUID secondLock = sourceId.compareTo(targetId) < 0 ? targetId : sourceId;

        balanceRepository.findByIdForUpdate(firstLock)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstLock));
        balanceRepository.findById(secondLock)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondLock));

        AccountBalance sourceBalance = balanceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source account missing"));

        if (sourceBalance.currentBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }

        balanceRepository.deductBalance(sourceId, amount);
        balanceRepository.addBalance(targetId, amount);

        Transaction tx = transactionRepository.save(new Transaction(idempotencyKey, description));
        ledgerRepository.save(new LedgerEntry(tx.id(), sourceId, amount.negate()));
        ledgerRepository.save(new LedgerEntry(tx.id(), targetId, amount));
    }

}
