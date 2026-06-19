# Movie Ticket Booking System

REST API for browsing shows, holding seats, confirming bookings, and cancelling tickets with configurable pricing, discounts, and refund policies.

## Stack

- Java 17, Spring Boot 3.3
- PostgreSQL 16 + Flyway migrations
- Session-based auth (cookie)
- springdoc-openapi (Swagger UI)
- Testcontainers for integration tests

## Quick Start

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Run the application

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

### 3. Run tests

```bash
./mvnw test
```

Integration tests spin up PostgreSQL via Testcontainers automatically (requires Docker Desktop running). They are skipped when Docker is unavailable; unit tests always run.

**Demo:** Import `postman/Movie-Booking-API.json` into Postman or Bruno.

## Assumptions

| Topic | Decision |
|-------|----------|
| Auth | Session cookie; register as ADMIN or CUSTOMER |
| Hold TTL | 5 minutes; auto-released every 30s |
| Concurrency | Pessimistic row locks on `show_seats` |
| Payment | Mock CARD/UPI/WALLET; use `token_success` or `token_fail` |
| Pricing | Tier + weekend (IST); locked at hold time |
| Discount | Applied at confirm; single code per booking |
| Refund | Configurable policy by hours-before-show |
| Notifications | Async log to DB (non-blocking) |

## Core Flow (Swagger)

1. `POST /auth/register` — create admin + customer users
2. `POST /auth/login` — get session cookie
3. Admin: create city → theater → screen → seats → movie → show
4. `GET /shows/{id}/seats` — browse seat map
5. `POST /shows/{id}/holds` — hold seats
6. `POST /bookings` with `Idempotency-Key` header — confirm booking
7. `POST /bookings/{id}/cancel` — partial/full cancel

## Payment tokens (mock)

- Success: `token_success`
- Failure: `token_fail`

## Project docs

- [PLAN.md](PLAN.md) — design plan and acceptance criteria
- [AGENTS.md](AGENTS.md) — AI agent guidelines used during development
