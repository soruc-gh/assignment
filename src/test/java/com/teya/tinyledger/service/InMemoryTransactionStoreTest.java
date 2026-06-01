package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.Transaction;
import com.teya.tinyledger.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTransactionStoreTest {

    private final TransactionStore store = new InMemoryTransactionStore();

    private static Transaction deposit(String accountId, String amount) {
        return new Transaction(UUID.randomUUID(), accountId, TransactionType.DEPOSIT,
                Money.of(new BigDecimal(amount)), Instant.now());
    }

    private static Transaction withdrawal(String accountId, String amount) {
        return new Transaction(UUID.randomUUID(), accountId, TransactionType.WITHDRAWAL,
                Money.of(new BigDecimal(amount)), Instant.now());
    }

    private static MovementResult movement(Transaction txn, String balanceAfter) {
        return new MovementResult(txn, Money.of(new BigDecimal(balanceAfter)));
    }

    @Test
    void balanceOf_unknownAccount_isZero() {
        assertThat(store.balanceOf("NOPE").amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void read_unknownAccount_returnsEmpty() {
        assertThat(store.read("NOPE")).isEmpty();
    }

    @Test
    void record_persistsTheReturnedTransaction() {
        var event = deposit("ACC-1", "10.00");

        var result = store.record("ACC-1", _ -> movement(event, "10.00"));

        assertThat(result.transaction()).isEqualTo(event);
        assertThat(store.read("ACC-1")).containsExactly(event);
    }

    @Test
    void balanceOf_foldsDepositsAndWithdrawals() {
        store.record("ACC-2", _ -> movement(deposit("ACC-2", "100.00"), "100.00"));
        store.record("ACC-2", _ -> movement(deposit("ACC-2", "50.00"), "150.00"));
        store.record("ACC-2", _ -> movement(withdrawal("ACC-2", "30.00"), "120.00"));

        assertThat(store.balanceOf("ACC-2").amount()).isEqualByComparingTo("120.00");
    }

    @Test
    void record_decisionSeesCurrentBalanceAtomically() {
        store.record("ACC-3", _ -> movement(deposit("ACC-3", "5.00"), "5.00"));

        store.record("ACC-3", state -> {
            assertThat(state.balance().amount()).isEqualByComparingTo("5.00");
            return movement(deposit("ACC-3", "3.00"), "8.00");
        });

        assertThat(store.balanceOf("ACC-3").amount()).isEqualByComparingTo("8.00");
    }

    @Test
    void read_returnsImmutableSnapshot() {
        var event = deposit("ACC-4", "1.00");
        store.record("ACC-4", _ -> movement(event, "1.00"));

        var snapshot = store.read("ACC-4");
        assertThatThrownBy(() -> snapshot.add(event)).isInstanceOf(UnsupportedOperationException.class);
    }
}
