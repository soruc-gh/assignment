package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class LedgerService {

    private final TransactionStore store;

    public LedgerService(TransactionStore store) {
        this.store = store;
    }

    public BalanceView getBalance(String accountId) {
        return new BalanceView(accountId, store.balanceOf(accountId));
    }

    public MovementResult deposit(String accountId, BigDecimal amount) {
        var money = Money.of(amount);
        return store.record(accountId, state -> Account.of(accountId, state).deposit(money));
    }

    public MovementResult withdraw(String accountId, BigDecimal amount) {
        var money = Money.of(amount);
        return store.record(accountId, state -> Account.of(accountId, state).withdraw(money));
    }

    public List<Transaction> getTransactions(String accountId) {
        return store.read(accountId).stream()
                .sorted(Comparator.comparing(Transaction::timestamp).reversed())
                .toList();
    }
}
