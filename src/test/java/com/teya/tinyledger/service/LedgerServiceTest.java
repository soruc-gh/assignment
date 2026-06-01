package com.teya.tinyledger.service;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.Transaction;
import com.teya.tinyledger.domain.TransactionType;
import com.teya.tinyledger.domain.exception.InsufficientFundsException;
import com.teya.tinyledger.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceTest {

    private static final String ACCOUNT = "ACC-TEST";
    private static final String UNKNOWN = "NO-SUCH-ACCOUNT";

    private LedgerService service;

    @BeforeEach
    void setUp() {
        service = new LedgerService(new InMemoryTransactionStore());
    }

    @Test
    void getBalance_freshAccount_returnsZero() {
        assertThat(service.getBalance(UNKNOWN).balance().amount())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void getBalance_afterDeposit_reflectsAmount() {
        service.deposit(ACCOUNT, new BigDecimal("200.00"));
        assertThat(service.getBalance(ACCOUNT).balance().amount())
                .isEqualByComparingTo("200.00");
    }

    @Test
    void deposit_createsAccountLazilyAndIncreasesBalance() {
        var result = service.deposit(ACCOUNT, new BigDecimal("200.00"));
        assertThat(result.balanceAfter().amount()).isEqualByComparingTo("200.00");
        assertThat(result.transaction().type()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void deposit_zeroAmount_throwsInvalidAmount() {
        assertThatThrownBy(() -> service.deposit(ACCOUNT, BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void deposit_negativeAmount_throwsInvalidAmount() {
        assertThatThrownBy(() -> service.deposit(ACCOUNT, new BigDecimal("-1.00")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void deposit_nullAmount_throwsInvalidAmount() {
        assertThatThrownBy(() -> service.deposit(ACCOUNT, null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void deposit_moreThanTwoDecimalPlaces_throwsInvalidAmount() {
        assertThatThrownBy(() -> service.deposit(ACCOUNT, new BigDecimal("1.234")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void withdraw_afterDeposit_decreasesBalance() {
        service.deposit(ACCOUNT, new BigDecimal("500.00"));
        var result = service.withdraw(ACCOUNT, new BigDecimal("300.00"));
        assertThat(result.balanceAfter().amount()).isEqualByComparingTo("200.00");
        assertThat(result.transaction().type()).isEqualTo(TransactionType.WITHDRAWAL);
    }

    @Test
    void withdraw_exactBalance_succeeds() {
        service.deposit(ACCOUNT, new BigDecimal("1500.00"));
        var result = service.withdraw(ACCOUNT, new BigDecimal("1500.00"));
        assertThat(result.balanceAfter().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void withdraw_freshAccount_throwsInsufficientFunds() {
        assertThatThrownBy(() -> service.withdraw(UNKNOWN, new BigDecimal("1.00")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("insufficient");
    }

    @Test
    void withdraw_beyondBalance_throwsInsufficientFunds() {
        service.deposit(ACCOUNT, new BigDecimal("5.00"));
        assertThatThrownBy(() -> service.withdraw(ACCOUNT, new BigDecimal("10.00")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("insufficient");
    }

    @Test
    void withdraw_zeroAmount_throwsInvalidAmount() {
        service.deposit(ACCOUNT, new BigDecimal("100.00"));
        assertThatThrownBy(() -> service.withdraw(ACCOUNT, BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void withdraw_negativeAmount_throwsInvalidAmount() {
        service.deposit(ACCOUNT, new BigDecimal("100.00"));
        assertThatThrownBy(() -> service.withdraw(ACCOUNT, new BigDecimal("-5.00")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void withdraw_nullAmount_throwsInvalidAmount() {
        service.deposit(ACCOUNT, new BigDecimal("100.00"));
        assertThatThrownBy(() -> service.withdraw(ACCOUNT, null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void getTransactions_freshAccount_returnsEmpty() {
        assertThat(service.getTransactions(UNKNOWN)).isEmpty();
    }

    @Test
    void getTransactions_newestFirst_ordering() {
        service.deposit(ACCOUNT, new BigDecimal("10.00"));
        service.deposit(ACCOUNT, new BigDecimal("20.00"));

        var result = service.getTransactions(ACCOUNT);
        assertThat(result).isSortedAccordingTo(Comparator.comparing(Transaction::timestamp).reversed());
    }

    @Test
    void getTransactions_returnsAll_afterDepositAndWithdraw() {
        service.deposit(ACCOUNT, new BigDecimal("10.00"));
        service.withdraw(ACCOUNT, new BigDecimal("5.00"));

        assertThat(service.getTransactions(ACCOUNT)).hasSize(2);
    }

    @Test
    void balanceView_containsCorrectAccountId() {
        service.deposit(ACCOUNT, new BigDecimal("250.50"));
        var view = service.getBalance(ACCOUNT);
        assertThat(view.accountId()).isEqualTo(ACCOUNT);
        assertThat(view.balance()).isEqualTo(Money.of(new BigDecimal("250.50")));
    }
}
