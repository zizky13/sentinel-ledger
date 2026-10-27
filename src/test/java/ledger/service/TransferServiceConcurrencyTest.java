package ledger.service;

import ledger.model.AccountBalance;
import ledger.repository.AccountBalanceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest
@Testcontainers
class TransferServiceConcurrencyTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("sentinel_ledger")
            .withUsername("sentinel")
            .withPassword("sentinel_password");

    @DynamicPropertySource
    static void configuresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountBalanceRepository balanceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setupDatabase() throws SQLException {
        jdbcTemplate.execute("TRUNCATE TABLE ledger_entries, transactions CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE account_balances, accounts CASCADE");

        userA = UUID.randomUUID();
        userB = UUID.randomUUID();

        // Insert initial accounts and balances directly
        jdbcTemplate.update("INSERT INTO accounts (id, user_id, currency, status) VALUES (?, ?, 'USD', 'ACTIVE')", userA, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO account_balances (account_id, current_balance) VALUES (?, ?)", userA, new BigDecimal("1000.0000"));

        jdbcTemplate.update("INSERT INTO accounts (id, user_id, currency, status) VALUES (?, ?, 'USD', 'ACTIVE')", userB, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO account_balances (account_id, current_balance) VALUES (?, ?)", userB, new BigDecimal("0.0000"));
    }

    @Test
    void shouldHandleConcurrentTransfersWithoutBalanceDrift() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulTransfers = new AtomicInteger(0);

        BigDecimal transferAmount = new BigDecimal("10.0000");

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    transferService.executeTransfer(userA, userB, transferAmount, UUID.randomUUID(), "Test transfer");
                    successfulTransfers.incrementAndGet();
                } catch (Exception e) {

                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        AccountBalance balanceA = balanceRepository.findById(userA).orElseThrow();
        AccountBalance balanceB = balanceRepository.findById(userB).orElseThrow();

        // Verify Balances using compareTo (returns 0 if mathematically equal)
        assertEquals(0, new BigDecimal("0.0000").compareTo(balanceA.currentBalance()));
        assertEquals(0, new BigDecimal("1000.0000").compareTo(balanceB.currentBalance()));
        assertEquals(100, successfulTransfers.get());
    }
}
