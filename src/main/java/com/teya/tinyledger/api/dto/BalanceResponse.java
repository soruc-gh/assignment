package com.teya.tinyledger.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record BalanceResponse(String accountId, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {}
