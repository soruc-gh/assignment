package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;

public record AccountState(Money balance) {

    public AccountState {
        if (balance == null) {
            throw new IllegalArgumentException("balance must not be null");
        }
    }
}
