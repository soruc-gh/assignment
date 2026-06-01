package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

@Component
class InMemoryTransactionStore implements TransactionStore {

    private record AccountStream(ReentrantReadWriteLock lock, List<Transaction> events) {}

    private final Map<String, AccountStream> streams = new ConcurrentHashMap<>();

    @Override
    public Money balanceOf(String accountId) {
        var stream = streams.get(accountId);
        if (stream == null) {
            return Money.of(BigDecimal.ZERO);
        }
        stream.lock().readLock().lock();
        try {
            return fold(stream.events());
        } finally {
            stream.lock().readLock().unlock();
        }
    }

    @Override
    public List<Transaction> read(String accountId) {
        var stream = streams.get(accountId);
        if (stream == null) {
            return List.of();
        }
        stream.lock().readLock().lock();
        try {
            return List.copyOf(stream.events());
        } finally {
            stream.lock().readLock().unlock();
        }
    }

    @Override
    public MovementResult record(String accountId, Function<AccountState, MovementResult> decision) {
        var stream = streams.computeIfAbsent(
                accountId, _ -> new AccountStream(new ReentrantReadWriteLock(), new ArrayList<>()));
        stream.lock().writeLock().lock();
        try {
            var result = decision.apply(new AccountState(fold(stream.events())));
            stream.events().add(result.transaction());
            return result;
        } finally {
            stream.lock().writeLock().unlock();
        }
    }

    private static Money fold(List<Transaction> events) {
        var total = events.stream()
                .map(event -> switch (event.type()) {
                    case DEPOSIT -> event.money().amount();
                    case WITHDRAWAL -> event.money().amount().negate();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Money.of(total);
    }
}
