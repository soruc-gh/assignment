package com.teya.tinyledger.api;

import com.teya.tinyledger.domain.Money;
import com.teya.tinyledger.domain.Transaction;
import com.teya.tinyledger.domain.TransactionType;
import com.teya.tinyledger.domain.exception.InsufficientFundsException;
import com.teya.tinyledger.domain.exception.InvalidAmountException;
import com.teya.tinyledger.service.BalanceView;
import com.teya.tinyledger.service.LedgerService;
import com.teya.tinyledger.service.MovementResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LedgerController.class)
@Import(JacksonConfig.class)
class LedgerControllerTest {

    private static final UUID FIXED_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LedgerService ledgerService;

    @Test
    void getBalance_returns200WithMappedBody() throws Exception {
        when(ledgerService.getBalance(eq("ACC-1")))
                .thenReturn(new BalanceView("ACC-1", Money.of(new BigDecimal("12.50"))));

        mockMvc.perform(get("/api/v1/accounts/ACC-1/balance"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"accountId": "ACC-1", "balance": "12.50"}""", JsonCompareMode.STRICT));
    }

    @Test
    void deposit_returns201AndMapsResult() throws Exception {
        var txn = new Transaction(FIXED_ID, "ACC-1", TransactionType.DEPOSIT,
                Money.of(new BigDecimal("100.00")), FIXED_TIME);
        when(ledgerService.deposit(eq("ACC-1"), eq(new BigDecimal("100.00"))))
                .thenReturn(new MovementResult(txn, Money.of(new BigDecimal("100.00"))));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "100.00"}"""))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {
                          "transactionId": "11111111-1111-1111-1111-111111111111",
                          "accountId": "ACC-1",
                          "type": "DEPOSIT",
                          "amount": "100.00",
                          "balanceAfter": "100.00",
                          "timestamp": "2024-01-01T00:00:00.000Z"
                        }""", JsonCompareMode.STRICT));

        verify(ledgerService).deposit(eq("ACC-1"), eq(new BigDecimal("100.00")));
    }

    @Test
    void withdraw_returns201AndMapsResult() throws Exception {
        var txn = new Transaction(FIXED_ID, "ACC-1", TransactionType.WITHDRAWAL,
                Money.of(new BigDecimal("30.00")), FIXED_TIME);
        when(ledgerService.withdraw(eq("ACC-1"), eq(new BigDecimal("30.00"))))
                .thenReturn(new MovementResult(txn, Money.of(new BigDecimal("70.00"))));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "30.00"}"""))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {
                          "transactionId": "11111111-1111-1111-1111-111111111111",
                          "accountId": "ACC-1",
                          "type": "WITHDRAWAL",
                          "amount": "30.00",
                          "balanceAfter": "70.00",
                          "timestamp": "2024-01-01T00:00:00.000Z"
                        }""", JsonCompareMode.STRICT));

        verify(ledgerService).withdraw(eq("ACC-1"), eq(new BigDecimal("30.00")));
    }

    @Test
    void getTransactions_returns200_mapsHistoryWithNullBalanceAfter() throws Exception {
        var txn1 = new Transaction(FIXED_ID, "ACC-1", TransactionType.DEPOSIT,
                Money.of(new BigDecimal("50.00")), FIXED_TIME);
        var txn2 = new Transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "ACC-1", TransactionType.WITHDRAWAL,
                Money.of(new BigDecimal("10.00")), FIXED_TIME.plusSeconds(1));
        when(ledgerService.getTransactions(eq("ACC-1")))
                .thenReturn(List.of(txn1, txn2));

        mockMvc.perform(get("/api/v1/accounts/ACC-1/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {
                            "transactionId": "11111111-1111-1111-1111-111111111111",
                            "accountId": "ACC-1",
                            "type": "DEPOSIT",
                            "amount": "50.00",
                            "balanceAfter": null,
                            "timestamp": "2024-01-01T00:00:00.000Z"
                          },
                          {
                            "transactionId": "22222222-2222-2222-2222-222222222222",
                            "accountId": "ACC-1",
                            "type": "WITHDRAWAL",
                            "amount": "10.00",
                            "balanceAfter": null,
                            "timestamp": "2024-01-01T00:00:01.000Z"
                          }
                        ]""", JsonCompareMode.STRICT));
    }

    @Test
    void deposit_serviceThrowsInvalidAmount_returns422() throws Exception {
        when(ledgerService.deposit(eq("ACC-1"), eq(new BigDecimal("0"))))
                .thenThrow(new InvalidAmountException("Amount must be positive"));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "0"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Invalid amount"));
    }

    @Test
    void withdraw_serviceThrowsInsufficientFunds_returns422() throws Exception {
        var balance = Money.of(new BigDecimal("0.00"));
        var requested = Money.of(new BigDecimal("50.00"));
        when(ledgerService.withdraw(eq("ACC-1"), eq(new BigDecimal("50.00"))))
                .thenThrow(new InsufficientFundsException(balance, requested));

        mockMvc.perform(post("/api/v1/accounts/ACC-1/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "50.00"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Insufficient funds"));
    }

    @Test
    void deposit_malformedJsonBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/ACC-1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(ledgerService);
    }

    @Test
    void unmappedRoute_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
