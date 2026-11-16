package com.englishprep.api.identity.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.token.RefreshToken;
import com.englishprep.api.identity.infrastructure.member.JpaMemberRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Many requests use the same refresh token at the same moment: exactly one may win. This needs real
 * concurrent transactions, so the test is NOT wrapped in a rolled-back transaction; it commits its own
 * rows and deletes them afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenRaceTest {

    private static final String HASH = "c".repeat(64);
    private static final int REQUESTS = 8;

    @Autowired
    private JpaRefreshTokenRepository repository;

    @Autowired
    private JpaMemberRepository members;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeCommittedRows() {
        jdbc.update("delete from member"); // refresh_token rows go with it (ON DELETE CASCADE)
        jdbc.update("delete from person");
    }

    @Test
    void onlyOneOfManySimultaneousRequestsRevokesTheToken() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Member member = members.save(
                    Member.register("Alice", "Nguyen", "alice", "alice@example.com", "hash", "UTC"));
            Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
            repository.save(RefreshToken.issue(member.id(), HASH, expiresAt));
        });

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CyclicBarrier start = new CyclicBarrier(REQUESTS);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            Callable<Boolean> request = () -> {
                start.await(); // release all requests at once
                return new TransactionTemplate(transactionManager)
                        .execute(status -> repository.revokeIfActive(HASH, Instant.now()));
            };
            results.add(pool.submit(request));
        }
        long winners = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                winners++;
            }
        }
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
    }
}
