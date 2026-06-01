package com.teya.tinyledger.api;

import com.teya.tinyledger.api.dto.AmountRequest;
import com.teya.tinyledger.api.dto.BalanceResponse;
import com.teya.tinyledger.api.dto.TransactionResponse;
import com.teya.tinyledger.domain.Transaction;
import com.teya.tinyledger.service.BalanceView;
import com.teya.tinyledger.service.LedgerService;
import com.teya.tinyledger.service.MovementResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable String accountId) {
        return toBalanceResponse(ledgerService.getBalance(accountId));
    }

    @PostMapping("/{accountId}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable String accountId,
            @RequestBody AmountRequest request) {

        var result = ledgerService.deposit(accountId, request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(toTransactionResponse(result));
    }

    @PostMapping("/{accountId}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable String accountId,
            @RequestBody AmountRequest request) {

        var result = ledgerService.withdraw(accountId, request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(toTransactionResponse(result));
    }

    @GetMapping("/{accountId}/transactions")
    public List<TransactionResponse> getTransactions(@PathVariable String accountId) {
        return ledgerService.getTransactions(accountId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private BalanceResponse toBalanceResponse(BalanceView view) {
        return new BalanceResponse(view.accountId(), view.balance().amount());
    }

    private TransactionResponse toTransactionResponse(MovementResult result) {
        var txn = result.transaction();
        return new TransactionResponse(
                txn.id().toString(),
                txn.accountId(),
                txn.type().name(),
                txn.money().amount(),
                result.balanceAfter().amount(),
                txn.timestamp());
    }

    private TransactionResponse toHistoryResponse(Transaction txn) {
        return new TransactionResponse(
                txn.id().toString(),
                txn.accountId(),
                txn.type().name(),
                txn.money().amount(),
                null,
                txn.timestamp());
    }
}
