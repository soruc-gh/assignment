# Tiny Ledger

A small Spring Boot REST API for recording deposits and withdrawals, checking an account's
balance, and viewing its transaction history. In-memory storage, single (implicit) currency.

## Run

```bash
./gradlew bootRun     # starts on http://localhost:8080
./gradlew test        # run the test suite
```

> This repository is built up as a short series of stacked PRs
> (scaffold → domain → persistence → ledger service → REST API → docs).
> The full API reference and design notes arrive in the final docs PR.
