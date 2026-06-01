package com.teya.tinyledger.domain.exception;

import com.teya.tinyledger.domain.Money;

public class InsufficientFundsException extends RuntimeException {

    private final Money balance;
    private final Money requested;

    public InsufficientFundsException(Money balance, Money requested) {
        super("Balance " + balance + " is insufficient for withdrawal " + requested);
        this.balance = balance;
        this.requested = requested;
    }

    public Money getBalance() {
        return balance;
    }

    public Money getRequested() {
        return requested;
    }
}
