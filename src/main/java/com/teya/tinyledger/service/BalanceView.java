package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;

public record BalanceView(String accountId, Money balance) {}
