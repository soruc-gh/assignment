package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.TransactionType;
import com.teya.tinyledger.domain.exception.InsufficientFundsException;
import com.teya.tinyledger.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final String ACCOUNT = "ACC-1";

    private static Money money(String value) {
        return Money.of(new BigDecimal(value));
    }

    private static Account withBalance(String balance) {
        return Account.of(ACCOUNT, new AccountState(money(balance)));
    }

    @Test
    void deposit_returnsEventAndNewBalance() {
        var result = withBalance("100.00").deposit(money("25.00"));

        assertThat(result.transaction().type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(result.transaction().accountId()).isEqualTo(ACCOUNT);
        assertThat(result.balanceAfter().amount()).isEqualByComparingTo("125.00");
    }

    @Test
    void withdraw_withinBalance_returnsEventAndNewBalance() {
        var result = withBalance("100.00").withdraw(money("40.00"));

        assertThat(result.transaction().type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(result.balanceAfter().amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void withdraw_beyondBalance_throwsInsufficientFunds() {
        assertThatThrownBy(() -> withBalance("10.00").withdraw(money("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void withdraw_exactBalance_succeeds() {
        var result = withBalance("10.00").withdraw(money("10.00"));

        assertThat(result.balanceAfter().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void nonPositiveAmount_throwsInvalidAmount() {
        assertThatThrownBy(() -> withBalance("0.00").deposit(money("0.00")))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> withBalance("0.00").withdraw(money("-1.00")))
                .isInstanceOf(InvalidAmountException.class);
    }
}
