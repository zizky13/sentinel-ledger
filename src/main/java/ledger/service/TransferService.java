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
    private final IdempotencyService idempotencyService;

    public TransferService(AccountBalanceRepository balanceRepository,
                           LedgerEntryRepository ledgerRepository,
                           TransactionRepository transactionRepository,
                           IdempotencyService idempotencyService) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
    }

    public void executeTransferWithIdempotency(UUID sourceId, UUID targetId, BigDecimal amount, UUID idempotencyKey, String description) {
        String keyStr = idempotencyKey.toString();
        var result = idempotencyService.tryAcquire(keyStr);

        switch (result.status()) {
            case IN_PROGRESS -> throw new IllegalStateException("Request with idempotency key " + idempotencyKey + " is aready processing.");
            case COMPLETED -> {
                // already completed, return without re-executing
                return;
            }
            case ACQUIRED -> {
                try {
                    executeTransfer(sourceId, targetId, amount, idempotencyKey, description);
                    idempotencyService.saveResponse(keyStr, "{\"status\":\"SUCCESS\"}");
                } catch (Exception e) {
                    // failure happens, release lock
                    idempotencyService.releaseLock(keyStr);
                    throw e;
                }
            }
        }
    }

    // this sequence will happen or none will be commited (transaction).
    @Transactional
    public void executeTransfer(UUID sourceId, UUID targetId, BigDecimal amount, UUID idempotencyKey, String description) {
        // lexicographical sorting to prevent deadlock
        // this works because every thread would follow this order, and when a UUID is locked first, other thread needs to wait
        UUID firstLock = sourceId.compareTo(targetId) < 0 ? sourceId : targetId;
        UUID secondLock = sourceId.compareTo(targetId) < 0 ? targetId : sourceId;

        // db row locking
        balanceRepository.findByIdForUpdate(firstLock)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstLock));
        balanceRepository.findByIdForUpdate(secondLock)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondLock));

        // check source account and balance
        AccountBalance sourceBalance = balanceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source account missing"));

        if (sourceBalance.currentBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }

        // performs the transfer
        balanceRepository.deductBalance(sourceId, amount);
        balanceRepository.addBalance(targetId, amount);

        // save the transaction
        Transaction tx = transactionRepository.save(new Transaction(idempotencyKey, description));
        ledgerRepository.save(new LedgerEntry(tx.id(), sourceId, amount.negate()));
        ledgerRepository.save(new LedgerEntry(tx.id(), targetId, amount));
    }

}
