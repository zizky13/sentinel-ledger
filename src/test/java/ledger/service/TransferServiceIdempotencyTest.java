package ledger.service;


import ledger.model.AccountBalance;
import ledger.repository.AccountBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
public class TransferServiceIdempotencyTest {
    // postgre container
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("sentinel_ledger")
            .withUsername("sentinel")
            .withPassword("sentinel_password");

    // redis container
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // DB Props
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        // Redis Props
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setupDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE ledger_entries, transactions CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE account_balances, accounts CASCADE");

        // Clear Redis (Optional but good practice for isolated tests)
        // redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        userA = UUID.randomUUID();
        userB = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO accounts (id, user_id, currency, status) VALUES (?, ?, 'USD', 'ACTIVE')", userA, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO account_balances (account_id, current_balance) VALUES (?, ?)", userA, new BigDecimal("1000.0000"));

        jdbcTemplate.update("INSERT INTO accounts (id, user_id, currency, status) VALUES (?, ?, 'USD', 'ACTIVE')", userB, UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO account_balances (account_id, current_balance) VALUES (?, ?)", userB, new BigDecimal("0.0000"));
    }

    @Test
    void shouldRejectConcurrentDuplicateRequestsUsingIdempotency() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successfulTransfers = new AtomicInteger(0);
        AtomicInteger rejectedTransfers = new AtomicInteger(0);

        BigDecimal transferAmount = new BigDecimal("50.0000");

        UUID sharedIdempotencyKey = UUID.randomUUID();

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    transferService.executeTransferWithIdempotency(
                            userA, userB, transferAmount, sharedIdempotencyKey, "Test Idempotency Transfer"
                    );

                    successfulTransfers.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("already processing")) {
                        rejectedTransfers.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Unexpected error :" + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);

        AccountBalance balanceA = accountBalanceRepository.findById(userA).orElseThrow();
        AccountBalance balanceB = accountBalanceRepository.findById(userB).orElseThrow();

        assertEquals(0, new BigDecimal("950.0000").compareTo(balanceA.currentBalance()));
        assertEquals(0, new BigDecimal("50.0000").compareTo(balanceB.currentBalance()));

        assertEquals(1, successfulTransfers.get(), "Only one transfer could succeed");
    }
}
