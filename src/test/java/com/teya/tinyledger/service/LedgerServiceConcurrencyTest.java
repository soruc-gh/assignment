package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerServiceConcurrencyTest {

    private static final String ACCOUNT_ID = "ACC-CONCURRENT";
    private static final int THREAD_COUNT = 50;
    private static final BigDecimal WITHDRAWAL = new BigDecimal("100.00");
    private static final int EXPECTED_SUCCESSES = 15;

    @Test
    void concurrentWithdrawals_neverOverdraw_exactSuccessCount() throws InterruptedException {
        var store = new InMemoryTransactionStore();
        var service = new LedgerService(store);

        service.deposit(ACCOUNT_ID, new BigDecimal("1500.00"));

        var successCount = new AtomicInteger(0);
        var rejectedCount = new AtomicInteger(0);

        var ready = new CountDownLatch(THREAD_COUNT);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(THREAD_COUNT);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        service.withdraw(ACCOUNT_ID, WITHDRAWAL);
                        successCount.incrementAndGet();
                    } catch (InsufficientFundsException _) {
                        rejectedCount.incrementAndGet();
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
        }

        assertThat(successCount.get() + rejectedCount.get())
                .as("All threads must have either succeeded or been rejected")
                .isEqualTo(THREAD_COUNT);

        assertThat(successCount.get())
                .as("Exactly floor(1500 / 100) = 15 withdrawals should succeed")
                .isEqualTo(EXPECTED_SUCCESSES);

        var finalBalance = service.getBalance(ACCOUNT_ID);

        assertThat(finalBalance.balance().amount())
                .as("Final balance must be exactly 0.00 after 15 * 100.00 withdrawn from 1500.00")
                .isEqualByComparingTo("0.00");

        assertThat(finalBalance.balance().amount())
                .as("Account must never be overdrawn")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
}
