package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.Transaction;

import java.util.List;
import java.util.function.Function;

public interface TransactionStore {

    Money balanceOf(String accountId);

    List<Transaction> read(String accountId);

    MovementResult record(String accountId, Function<AccountState, MovementResult> decision);
}
