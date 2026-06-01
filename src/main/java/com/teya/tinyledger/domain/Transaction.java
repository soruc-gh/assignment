package com.teya.tinyledger.domain;

import java.time.Instant;
import java.util.UUID;

public record Transaction(
        UUID id,
        String accountId,
        TransactionType type,
        Money money,
        Instant timestamp
) {
    public Transaction {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (accountId == null) throw new IllegalArgumentException("accountId must not be null");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (money == null) throw new IllegalArgumentException("money must not be null");
        if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
    }
}
