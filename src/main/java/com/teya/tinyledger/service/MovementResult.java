package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.Transaction;

public record MovementResult(Transaction transaction, Money balanceAfter) {}
