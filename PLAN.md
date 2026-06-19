# Movie Ticket Booking System — Design Plan

## Overview

REST service for browsing shows, holding seats, confirming bookings, and cancelling tickets. Two roles: **Admin** (catalog setup) and **Customer** (browse, book, cancel).

**Stack:** Spring Boot, PostgreSQL, session auth, Flyway migrations.

---

## Design Decisions

| Topic | Choice |
|-------|--------|
| Auth | Session-based (cookie) |
| Hold duration | 5 minutes, auto-released by scheduler |
| Multi-seat hold | All-or-nothing |
| Price lock | At hold time |
| Cancellation | Partial seat cancellation allowed |
| Payment | Mock providers via Strategy pattern (CARD, UPI, WALLET) |
| Notifications | Async after booking (log/DB in v1; full queue in v2) |
| Weekend pricing | Based on show start time in IST |

---

## Architecture

Single monolith. Layered: **Controller → Service → Repository**.

```
Client → REST API → Services → PostgreSQL
                      ↓
              Payment Strategy (mock)
              Async notifications
              Scheduled jobs (hold expiry, reminders)
```

**Concurrency approach:** pessimistic row locks (`SELECT FOR UPDATE`) on `show_seats` inside transactions. Backup: optimistic `@Version` on hot rows.

**Idempotency:** client sends `Idempotency-Key` header on confirm booking. Same key returns the existing booking instead of creating a duplicate.

---

## Database (Core Tables)

11 tables. Payment and pricing config kept inline to avoid extra tables in v1.

| Table | Purpose |
|-------|---------|
| `users` | email, password, role (ADMIN / CUSTOMER) |
| `cities` | city name |
| `theaters` | belongs to city |
| `screens` | auditorium in a theater |
| `seats` | row, number, tier (REGULAR / PREMIUM) per screen |
| `movies` | title, duration |
| `shows` | movie + screen + start/end time, status |
| `show_seats` | per-show seat state: AVAILABLE / HELD / BOOKED |
| `holds` | groups held seats: user, show, expires_at, status |
| `bookings` | user, show, hold, amounts, payment fields, status |
| `booking_seats` | seats in a booking: price_paid, ACTIVE / CANCELLED |

### Key columns on `show_seats`

- `status`, `hold_id`, `held_until`, `locked_price`, `booking_id`
- Unique on `(show_id, seat_id)`

When admin creates a show, copy all screen seats into `show_seats` as AVAILABLE.

### Pricing (v1)

No separate pricing table. Rules in config/code:

```
REGULAR  + weekday (IST) → base price
REGULAR  + weekend       → higher
PREMIUM  + weekday       → higher
PREMIUM  + weekend       → highest
```

Price computed at hold time and stored on `show_seats.locked_price`.

### Payment (v1)

Fields on `bookings`: `payment_method`, `payment_status`, `provider_ref`.  
Strategy interface with mock CARD / UPI / WALLET providers (`token_success` / `token_fail`).

---

## State Flow

**Seat:** `AVAILABLE → HELD → BOOKED → AVAILABLE` (on cancel)

**Hold:** `ACTIVE → CONSUMED` (on book) or `EXPIRED / RELEASED`

**Booking:** `CONFIRMED → PARTIALLY_CANCELLED → CANCELLED`

---

## API Endpoints

### Auth
- `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`

### Admin
- CRUD: cities, theaters, screens, seats (bulk layout)
- CRUD: movies, shows (auto-generates `show_seats`)
- Manage pricing config (or app config in v1)

### Customer
- `GET /cities`, `GET /cities/{id}/theaters`
- `GET /shows?cityId&movieId&date`, `GET /shows/{id}`, `GET /shows/{id}/seats`
- `POST /shows/{id}/holds` — hold seats
- `DELETE /holds/{id}` — release hold
- `POST /bookings` — confirm from hold + payment
- `GET /bookings`, `GET /bookings/{id}`
- `POST /bookings/{id}/cancel` — partial or full cancel

---

## Core Flows

### Hold seats
1. Start transaction
2. Lock requested `show_seats` rows
3. If any seat is not AVAILABLE → rollback, return 409
4. Set HELD, attach `hold_id`, set `held_until = now + 5min`, compute and store `locked_price`
5. Create `holds` record, commit

### Confirm booking
1. Validate hold is ACTIVE, not expired, belongs to user
2. Lock seats again
3. Charge via payment strategy
4. On success: seats → BOOKED, hold → CONSUMED, create booking + booking_seats
5. Fire async confirmation (non-blocking)
6. Support idempotency key to prevent double confirm

### Cancel seats
1. Lock booking and related show_seats
2. Mark selected `booking_seats` as CANCELLED
3. Release those show_seats to AVAILABLE
4. Compute refund from simple policy (hours before show → refund %)
5. Update payment status, enqueue cancel notification

### Hold expiry job (every 30s)
Release seats where `status = HELD AND held_until < now()`. Mark hold EXPIRED.

---

## Edge Cases

| Case | Handling |
|------|----------|
| Two users book same seat | Row lock; second request gets 409 |
| Hold expires mid-checkout | Confirm fails with HOLD_EXPIRED |
| Double-click confirm | Idempotency-Key returns same booking |
| Payment fails | Rollback; seats stay held until expiry |
| One of N seats taken | All-or-nothing: reject entire hold |
| Partial cancel | Refund per seat; booking → PARTIALLY_CANCELLED |
| Cancel after show start | 0% refund |
| User accesses other's booking | 404 |
| Book on cancelled show | Block at hold time |
| Empty seat list | 400 validation error |
| Confirm without valid hold | 409 |

---

## Error Handling

Consistent JSON error body: `code`, `message`, optional `details`.

| Status | When |
|--------|------|
| 400 | Invalid input |
| 401 | Not logged in |
| 403 | Wrong role |
| 404 | Not found |
| 409 | Seat conflict, expired hold, invalid state |
| 402 | Payment failed |

Global `@RestControllerAdvice`. Never leak stack traces to clients.

---

## DB and Scale Best Practices (reference only)

Not implementing distributed architecture in v1. Document these for future scale:

**Database**
- Use transactions + row-level locks for seat mutations
- Index `(show_id, status)` on `show_seats`
- Unique constraint on `(show_id, seat_id)` prevents duplicate rows
- Connection pooling (HikariCP), read replicas for browse queries at scale
- Partition `bookings` by date if volume grows

**Concurrency**
- Short transactions: lock only during hold/confirm/cancel, not during payment UI wait
- Optimistic locking (`version` column) as fallback on conflict
- Hold TTL avoids indefinite seat blocking

**Idempotency**
- Store idempotency keys on bookings with unique index
- Safe retries on network failures

**Distributed (future)**
- Outbox pattern for notifications (DB row → async worker)
- Redis for hold TTL if DB sweep is too slow
- Kafka/SQS for event-driven booking confirmations
- Saga or compensating transactions for payment + booking across services
- Rate limit hold endpoint per user

---

## Implementation Order

1. Project setup, Flyway, entities, seed data
2. Auth + RBAC (session)
3. Admin catalog APIs + show creation (generates show_seats)
4. Browse + seat map
5. Hold flow with concurrency
6. Booking confirm + mock payment strategy
7. Partial cancel + refund logic
8. Schedulers (hold expiry) + async notifications
9. Integration tests (hold conflict, expiry, idempotent confirm, partial cancel)

---

## V2 (Later)

- `discount_codes` table + validate/apply API
- `refund_policies` table (configurable rules per show)
- `notifications` queue table with retry worker
- Separate `payments` and `refunds` tables for audit trail
- `pricing_rules` admin-managed table
- Per-theater timezone
- Admin bulk show cancel + notify all bookers
- Pagination, search, booking analytics
- Redis hold cache, outbox pattern, read replicas
