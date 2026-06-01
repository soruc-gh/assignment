# Tiny Ledger

A small REST API that records deposits and withdrawals, reports the current balance, and serves the
transaction history. Any account id is valid — an account that hasn't been written to is an empty,
zero-balance ledger, created on its first deposit. Single (implicit) currency, fully in-memory — all
state is lost on restart.

## Contents

- [Prerequisites](#prerequisites)
- [Running the application](#running-the-application)
- [Run with Docker](#run-with-docker)
- [Running the tests](#running-the-tests)
- [Accounts](#accounts)
- [API](#api)
- [Design decisions](#design-decisions)
- [Project structure](#project-structure)

---

## Prerequisites

**Java 25** must be installed — the build targets a Java 25 toolchain. Gradle itself is **not** required:
the project ships the Gradle wrapper (`./gradlew`).

> If the build cannot locate a Java 25 toolchain automatically, set `JAVA_HOME` to a JDK 25 installation.

> Prefer not to install Java 25? Build and run everything with **Docker** instead — see
> [Run with Docker](#run-with-docker); Java and Gradle are then not needed on your machine.

---

## Running the application

```bash
./gradlew bootRun
```

The server starts on **port 8080**. There are no predefined accounts — any account id works and starts
empty (see [Accounts](#accounts)).

## Run with Docker

No local Java or Gradle is required — only Docker. The image builds the boot jar inside a multi-stage
build (the committed Gradle wrapper on Amazon Corretto) and runs it on a thin `amazoncorretto:25-alpine`
base:

```bash
docker build -t tiny-ledger .
docker run --rm -p 8080:8080 tiny-ledger
```

The API is then available at `http://localhost:8080` (see [API](#api)).

## Running the tests

```bash
./gradlew test     # or: ./gradlew build  (compile + test)
```

The suite is layered (unit → `@WebMvcTest` controller slice → real-HTTP e2e); see
[Design decisions](#design-decisions) → Testing strategy.

---

## Accounts

There is no account-creation endpoint (out of scope) and no predefined accounts. **Any account id is
valid:** an account that has never been written to reads as an empty ledger with a `0.00` balance, and it
is created lazily on its first deposit. (Only an unknown *route* returns 404 — never an unknown account.)
All state is in memory and lost on restart.

---

## API

Base path: `/api/v1/accounts`

| Method | Path | Purpose | Success |
|---|---|---|---|
| GET | `/{id}/balance` | Current balance | 200 |
| POST | `/{id}/deposits` | Record a deposit | 201 |
| POST | `/{id}/withdrawals` | Record a withdrawal | 201 |
| GET | `/{id}/transactions` | Full history, newest first | 200 |

### Examples

```bash
# Deposit into a (new or existing) account -> 201, account created on first deposit
curl -s -X POST http://localhost:8080/api/v1/accounts/ACC-1/deposits \
  -H "Content-Type: application/json" -d '{"amount": "100.00"}'
# {"transactionId":"...","accountId":"ACC-1","type":"DEPOSIT","amount":"100.00","balanceAfter":"100.00","timestamp":"..."}

# Balance (an unseen account reads as 0.00)
curl -s http://localhost:8080/api/v1/accounts/ACC-1/balance
# {"accountId":"ACC-1","balance":"100.00"}

# Withdrawal -> 201
curl -s -X POST http://localhost:8080/api/v1/accounts/ACC-1/withdrawals \
  -H "Content-Type: application/json" -d '{"amount": "50.00"}'

# Withdraw more than the balance -> 422 Insufficient funds
curl -s -X POST http://localhost:8080/api/v1/accounts/ACC-1/withdrawals \
  -H "Content-Type: application/json" -d '{"amount": "9999.00"}'

# Invalid amount (<= 0 or > 2 decimal places) -> 422 Invalid amount
curl -s -X POST http://localhost:8080/api/v1/accounts/ACC-1/deposits \
  -H "Content-Type: application/json" -d '{"amount": "0"}'

# History (newest first)
curl -s http://localhost:8080/api/v1/accounts/ACC-1/transactions
```

`balanceAfter` is populated on the deposit/withdrawal responses but is `null` in history entries — a
per-entry running balance is only well-defined over the full ordered log, so returning a value there
would be misleading.

---

## Design decisions

### Architecture & persistence

**Swappable persistence port.** `TransactionStore` is an interface (port); `InMemoryTransactionStore` is its
only adapter today. It exposes `balanceOf`, `read` (history), and an atomic `record(accountId, decide)`
unit-of-work whose decision receives an `AccountState` (a balance snapshot), not the raw event list — so
*how* the balance is produced stays the adapter's private detail. `LedgerService` is a thin facade over the
port.

**Ready for a relational database.** A DB is one new adapter: `balanceOf` → `SELECT`, `record` → a
transaction (`SELECT … FOR UPDATE`, decide, `INSERT`/`UPDATE`). Because the decision already consumes a
balance snapshot, `domain`, `LedgerService`, and `api` are untouched. Deferred (in-memory only): schema
migrations, a maintained balance column to make `balanceOf` O(1), and DB-backed idempotency.

**Balance is derived from an append-only log.** No mutable balance field — the in-memory adapter folds an
account's deposits and withdrawals to a `0.00`-based total, so the history fully explains the balance.

**Concurrency — per-account locking.** Each account holds a `ReentrantReadWriteLock`. `record(...)` runs the
load→decide→append under the write lock, so two concurrent withdrawals can't both pass the funds check;
balance/history reads share the read lock; different accounts never block each other. In-process only
(single JVM) — a DB adapter would use `SELECT … FOR UPDATE`. Proven by `LedgerServiceConcurrencyTest`.

### Domain & money

**A `Money` value object.** Amounts are never bare `BigDecimal`s — they flow through `Money` (exact decimal,
scale 2), the single seam for adding currency later.

**Money is a JSON string, not a number.** Every monetary value serializes as a quoted decimal string (e.g.
`"100.00"`) via `@JsonFormat(shape = STRING)`. JSON numbers are IEEE-754 doubles in many clients (e.g.
JavaScript's `JSON.parse`), so `100.00` loses scale (→ `100`) and large values lose precision past 2^53. The
server still parses to `BigDecimal`, so validation (≤ 2 decimals, positive) is unchanged.

**Single currency.** One implicit currency (no currency code). Adding currencies means a `Currency` field on
`Money` and the DTOs — a localized change.

### API contract

**Lazy, open-ended accounts.** No account-creation endpoint and no predefined accounts: any id is valid, an
unseen account reads as `0.00`, and it's created on first deposit. Only an unknown *route* is a 404.

**Invariants live in the `Account` aggregate.** `Account` takes the current balance (the `AccountState`
snapshot) and *decides* a movement — rejecting it, or producing a new transaction — before anything is
persisted. The decision is pure and runs under the write lock, so the no-overdraft / positive-amount
(≤ 2 dp) rules sit in one place and can't be raced; an invalid amount surfaces as `422`.

**Separate `/deposits` and `/withdrawals`.** Distinct operations with different rules (a withdrawal can be
rejected for insufficient funds) get intention-revealing URLs rather than one `POST /transactions` with a
type discriminator.

### Testing strategy

A test pyramid: fast **unit** tests for the domain, `Account`, the store, and `LedgerService`
(against the real in-memory store), plus a concurrency test; a **`@WebMvcTest` controller slice**
(`LedgerControllerTest`) that checks web concerns with the service mocked; and a **real-HTTP end-to-end**
test (`LedgerApiIntegrationTest`, `@SpringBootTest(RANDOM_PORT)`) that drives the running app over the wire
and pins the JSON contract (string money, id/timestamp present, newest-first history).

### Out of scope — would add in production

| Concern | Now | In production |
|---|---|---|
| Idempotency | not implemented; POSTs aren't retry-safe | required `Idempotency-Key`, stored + replayed |
| History pagination / filtering | full list, newest first | cursor or page/size; filter by type/date |
| Currency | single implicit | `Currency` on `Money` + DTOs |
| Auth, logging/monitoring, persistence | excluded | auth filter, structured logs/metrics, DB adapter |

---

## Project structure

```
domain/   Money, Transaction, TransactionType, exception/ (InsufficientFunds, InvalidAmount)
service/  TransactionStore           persistence port — balanceOf + read(history) + atomic record(...)
          InMemoryTransactionStore   the adapter: per-account RW locks, folds events to a balance, lazy accounts
          AccountState               balance snapshot handed to the Account aggregate
          Account                    the account aggregate: invariants + the new transaction
          LedgerService              thin facade (depends on the port) + BalanceView, MovementResult
api/      LedgerController, request/response DTOs, GlobalExceptionHandler
```
