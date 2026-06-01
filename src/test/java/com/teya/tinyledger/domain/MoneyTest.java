package com.teya.tinyledger.domain;

import com.teya.tinyledger.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void of_normalisesToScaleTwo() {
        assertThat(Money.of(new BigDecimal("10")).amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of(new BigDecimal("10")).toString()).isEqualTo("10.00");
    }

    @Test
    void of_rejectsNull() {
        assertThatThrownBy(() -> Money.of(null)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void of_rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("1.234")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void plus_addsAmounts() {
        var result = Money.of(new BigDecimal("10.00")).plus(Money.of(new BigDecimal("3.50")));
        assertThat(result.amount()).isEqualByComparingTo("13.50");
    }

    @Test
    void minus_subtractsAmounts() {
        var result = Money.of(new BigDecimal("10.00")).minus(Money.of(new BigDecimal("3.50")));
        assertThat(result.amount()).isEqualByComparingTo("6.50");
    }

    @Test
    void isLessThan_comparesAmounts() {
        var three = Money.of(new BigDecimal("3.00"));
        var ten = Money.of(new BigDecimal("10.00"));
        assertThat(three.isLessThan(ten)).isTrue();
        assertThat(ten.isLessThan(three)).isFalse();
    }
}
