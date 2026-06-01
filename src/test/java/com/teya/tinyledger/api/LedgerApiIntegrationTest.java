package com.teya.tinyledger.api;

import com.teya.tinyledger.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static com.teya.tinyledger.domain.TransactionType.DEPOSIT;
import static com.teya.tinyledger.domain.TransactionType.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LedgerApiIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<String> jsonBody(String body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> post(String path, String body) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jsonBody(body), String.class);
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.exchange(url(path), HttpMethod.GET, null, String.class);
    }

    private ResponseEntity<String> movement(String accountId, String amount, TransactionType type) {
        var segment = type == DEPOSIT ? "deposits" : "withdrawals";
        return post(ACCOUNTS + "/" + accountId + "/" + segment, """
                {"amount": "%s"}""".formatted(amount));
    }

    private ResponseEntity<String> balance(String accountId) {
        return get(ACCOUNTS + "/" + accountId + "/balance");
    }

    private ResponseEntity<String> transactions(String accountId) {
        return get(ACCOUNTS + "/" + accountId + "/transactions");
    }

    private JsonNode parse(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertHasIdAndTimestamp(JsonNode node) {
        assertThat(node.get("transactionId").asString()).isNotBlank();
        assertThatCode(() -> UUID.fromString(node.get("transactionId").asString())).doesNotThrowAnyException();
        assertThat(node.get("timestamp").asString()).isNotBlank();
        assertThatCode(() -> Instant.parse(node.get("timestamp").asString())).doesNotThrowAnyException();
    }

    @Test
    void getBalance_freshAccount_returnsZeroBalance() throws Exception {
        var account = "ACC-" + UUID.randomUUID();

        var resp = balance(account);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(parse(resp).get("balance").asString()).isEqualTo("0.00");
    }

    @Test
    void deposit_returns201AndReportsNewBalance() throws Exception {
        var account = "ACC-" + UUID.randomUUID();

        var resp = movement(account, "100.00", DEPOSIT);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        var body = parse(resp);
        assertThat(body.get("type").asString()).isEqualTo("DEPOSIT");
        assertThat(body.get("balanceAfter").asString()).isEqualTo("100.00");
        assertHasIdAndTimestamp(body);
    }

    @Test
    void withdraw_afterDeposit_returns201AndReportsReducedBalance() throws Exception {
        var account = "ACC-" + UUID.randomUUID();
        movement(account, "100.00", DEPOSIT);

        var resp = movement(account, "30.00", WITHDRAWAL);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        var body = parse(resp);
        assertThat(body.get("type").asString()).isEqualTo("WITHDRAWAL");
        assertThat(body.get("balanceAfter").asString()).isEqualTo("70.00");
        assertHasIdAndTimestamp(body);
    }

    @Test
    void getTransactions_returnsHistoryNewestFirst() throws Exception {
        var account = "ACC-" + UUID.randomUUID();
        movement(account, "100.00", DEPOSIT);
        movement(account, "30.00", WITHDRAWAL);

        var resp = transactions(account);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        var txns = parse(resp);
        assertThat(txns.isArray()).isTrue();
        assertThat(txns.size()).isEqualTo(2);
        assertThat(txns.get(0).get("type").asString()).isEqualTo("WITHDRAWAL");
        assertThat(txns.get(1).get("type").asString()).isEqualTo("DEPOSIT");
        assertThat(txns.get(0).get("balanceAfter").isNull()).isTrue();
        assertThat(txns.get(1).get("balanceAfter").isNull()).isTrue();
        assertHasIdAndTimestamp(txns.get(0));
        assertHasIdAndTimestamp(txns.get(1));
    }

    @Test
    void balance_serializesMoneyAtScaleTwo() throws Exception {
        var account = "ACC-" + UUID.randomUUID();

        movement(account, "100.00", DEPOSIT);
        movement(account, "30.00", WITHDRAWAL);

        var resp = balance(account);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("\"balance\":\"70.00\"");
    }

    @Test
    void withdraw_onFreshAccount_returns422InsufficientFunds() throws Exception {
        var account = "ACC-" + UUID.randomUUID();
        var resp = movement(account, "1.00", WITHDRAWAL);

        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        var body = parse(resp);
        assertThat(body.get("status").asInt()).isEqualTo(422);
        assertThat(body.get("title").asString()).isEqualTo("Insufficient funds");
    }

    @Test
    void deposit_zeroAmount_returns422InvalidAmount() throws Exception {
        var account = "ACC-" + UUID.randomUUID();
        var resp = movement(account, "0", DEPOSIT);

        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        var body = parse(resp);
        assertThat(body.get("title").asString()).isEqualTo("Invalid amount");
    }

    @Test
    void deposit_tooManyDecimals_returns422InvalidAmount() throws Exception {
        var account = "ACC-" + UUID.randomUUID();
        var resp = movement(account, "1.234", DEPOSIT);

        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        var body = parse(resp);
        assertThat(body.get("title").asString()).isEqualTo("Invalid amount");
    }

    @Test
    void deposit_missingAmountField_returns422() throws Exception {
        var account = "ACC-" + UUID.randomUUID();
        var resp = post(ACCOUNTS + "/" + account + "/deposits", "{}");

        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        var body = parse(resp);
        assertThat(body.get("title").asString()).isEqualTo("Invalid amount");
    }

    @Test
    void deposit_malformedJsonBody_returns400() throws Exception {
        var account = "ACC-" + UUID.randomUUID();
        var resp = post(ACCOUNTS + "/" + account + "/deposits", "not-json");

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        var body = parse(resp);
        assertThat(body.get("title").asString()).isEqualTo("Malformed request");
    }

    @Test
    void deposit_emptyBody_returns400() throws Exception {
        var account = "ACC-" + UUID.randomUUID();
        var resp = post(ACCOUNTS + "/" + account + "/deposits", "");

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void unmappedRoute_returns404() throws Exception {
        var resp = get(ACCOUNTS);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        var body = parse(resp);
        assertThat(body.get("status").asInt()).isEqualTo(404);
    }
}
