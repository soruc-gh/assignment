package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.Transaction;
import com.teya.tinyledger.domain.TransactionType;
import com.teya.tinyledger.domain.exception.InsufficientFundsException;
import com.teya.tinyledger.domain.exception.InvalidAmountException;

import java.time.Instant;
import java.util.UUID;

final class Account {

    private final String accountId;
    private final Money balance;

    private Account(String accountId, Money balance) {
        this.accountId = accountId;
        this.balance = balance;
    }

    static Account of(String accountId, AccountState state) {
        return new Account(accountId, state.balance());
    }

    MovementResult deposit(Money amount) {
        requirePositive(amount);
        return new MovementResult(newEvent(TransactionType.DEPOSIT, amount), balance.plus(amount));
    }

    MovementResult withdraw(Money amount) {
        requirePositive(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(balance, amount);
        }
        return new MovementResult(newEvent(TransactionType.WITHDRAWAL, amount), balance.minus(amount));
    }

    private Transaction newEvent(TransactionType type, Money amount) {
        return new Transaction(UUID.randomUUID(), accountId, type, amount, Instant.now());
    }

    private static void requirePositive(Money amount) {
        if (amount.amount().signum() <= 0) {
            throw new InvalidAmountException(
                    "Amount must be strictly positive, got: " + amount.amount().toPlainString());
        }
    }
}
