# Movie Ticket Booking System — Design Plan

## Overview

REST service for browsing shows, holding seats, confirming bookings, and cancelling tickets. Two roles: **Admin** (catalog + policy setup) and **Customer** (browse, book, cancel).

**Stack:** Spring Boot 3, PostgreSQL, Flyway, session auth (cookie), Testcontainers for integration tests.

---

## Design Decisions

| Topic | Choice |
|-------|--------|
| Auth | Session-based (cookie); BCrypt passwords; `@PreAuthorize` RBAC |
| Hold duration | 5 min; scheduler releases expired holds every 30s |
| Multi-seat hold | All-or-nothing |
| Price lock | At hold time (tier + weekend + discount applied at confirm) |
| Discount | Single code per booking; validated at confirm; stored on booking |
| Cancellation | Partial seat cancel allowed |
| Refund policy | Admin-configurable rules (hours-before-show → refund %) |
| Payment | Mock Strategy pattern (CARD, UPI, WALLET); `token_success` / `token_fail` |
| Notifications | `@Async` after confirm/cancel/reminder; persisted log table (non-blocking) |
| Weekend pricing | Show start time in IST |
| API docs | springdoc-openapi (Swagger UI) for manual testing only |

---

## Architecture

Single monolith. **Controller → Service → Repository**.

```
Client → REST API → Services → PostgreSQL
                      ↓
              Payment Strategy (mock)
              @Async notifications
              Schedulers (hold expiry, show reminders)
```

**Concurrency:** pessimistic row locks (`SELECT FOR UPDATE`) on `show_seats` inside short transactions. `@Version` on `show_seats` as safety net.

**Idempotency:** `Idempotency-Key` header on `POST /bookings`. Same key + same payload → return existing booking (201/200).

---

## Database (13 tables)

| Table | Purpose |
|-------|---------|
| `users` | email, password_hash, role (ADMIN / CUSTOMER) |
| `cities` | name |
| `theaters` | city_id |
| `screens` | theater_id, name |
| `seats` | screen_id, row, number, tier (REGULAR / PREMIUM) |
| `movies` | title, duration_minutes |
| `shows` | movie_id, screen_id, start/end, status (SCHEDULED / CANCELLED) |
| `show_seats` | per-show seat state |
| `holds` | user, show, expires_at, status |
| `bookings` | user, show, hold, amounts, discount, payment, idempotency_key |
| `booking_seats` | seat, price_paid, refund_amount, status |
| `discount_codes` | code, type (PERCENT / FLAT), value, max_uses, valid_until, active |
| `refund_policies` | name, rules JSON: `[{hoursBeforeShow, refundPercent}]` |
| `notifications` | user_id, booking_id, type, channel, status, payload (audit + demo) |

### `show_seats` key columns

`status` (AVAILABLE / HELD / BOOKED), `hold_id`, `held_until`, `locked_base_price`, `booking_id`  
Unique: `(show_id, seat_id)` · Index: `(show_id, status)`

Show creation copies all screen seats → `show_seats` as AVAILABLE.

### Pricing (computed at hold, finalized at confirm)

```
base = tier price (REGULAR/PREMIUM) × weekend multiplier (IST)
discount applied at confirm → stored as booking.discount_amount
final = sum(locked_base_price) - discount
```

Tier prices + weekend multiplier in `application.yml` (admin API to update in v1).

### Refund (at cancel)

Lookup show's `refund_policy_id` → pick rule by `hoursBeforeShow` → `refund = price_paid × percent`. After show start → 0%.

---

## State Machines

| Entity | Transitions |
|--------|-------------|
| Seat | AVAILABLE → HELD → BOOKED → AVAILABLE |
| Hold | ACTIVE → CONSUMED \| EXPIRED \| RELEASED |
| Booking | CONFIRMED → PARTIALLY_CANCELLED → CANCELLED |
| Payment | PENDING → SUCCESS \| FAILED |
| Notification | PENDING → SENT \| FAILED |

---

## API Endpoints

### Auth
`POST /auth/register` · `POST /auth/login` · `POST /auth/logout` · `GET /auth/me`

### Admin (role ADMIN)
- CRUD: cities, theaters, screens, seats (bulk layout)
- CRUD: movies, shows (auto-generates `show_seats`)
- `PUT /admin/pricing` — tier + weekend config
- CRUD: `discount_codes`, `refund_policies`
- `PATCH /shows/{id}/cancel` — cancel show, release seats, notify bookers

### Customer (role CUSTOMER)
- Browse: cities, theaters, shows (filter by city/movie/date), show detail, seat map
- `POST /shows/{id}/holds` · `DELETE /holds/{id}`
- `POST /bookings` (body: holdId, paymentMethod, paymentToken, discountCode?, header: Idempotency-Key)
- `GET /bookings` · `GET /bookings/{id}`
- `POST /bookings/{id}/cancel` (body: seatIds[])

---

## Core Flows

### Hold
1. Tx: lock `show_seats` FOR UPDATE
2. All AVAILABLE? else 409
3. Set HELD, attach hold, `held_until = now+5m`, store `locked_base_price`
4. Create `holds` ACTIVE · commit

### Confirm
1. Validate hold (ACTIVE, not expired, owner)
2. Tx: re-lock seats, validate still HELD under this hold
3. Apply discount if code valid
4. Payment strategy charge
5. Success: seats BOOKED, hold CONSUMED, create booking + booking_seats
6. `@Async` confirmation notification (API returns immediately)
7. Idempotency: duplicate key → return existing booking

### Cancel (partial/full)
1. Tx: lock booking + seats
2. Mark selected booking_seats CANCELLED, release show_seats
3. Compute per-seat refund from policy
4. Update booking status, `@Async` cancel notification

### Schedulers
- **Hold expiry** (30s): HELD + past `held_until` → AVAILABLE, hold EXPIRED
- **Reminders** (hourly): shows starting in ~2h → reminder notification to confirmed bookers (skip if already sent)

---

## Error Handling

JSON: `{ "code", "message", "details?" }` via `@RestControllerAdvice`

| Status | When |
|--------|------|
| 400 | Validation, invalid discount, empty seat list |
| 401 | Not authenticated |
| 403 | Wrong role |
| 404 | Not found / not owner (no existence leak) |
| 409 | Seat conflict, expired hold, invalid state |
| 402 | Payment failed |

---

## Testing — Closed Loop

Tests run against real PostgreSQL (Testcontainers). Each scenario: **setup → act → assert DB state + HTTP response + side effects**.

### Unit tests (no DB)

| Area | Cases |
|------|-------|
| PricingService | weekday/weekend × REGULAR/PREMIUM; IST boundary (Fri→Sat midnight) |
| DiscountService | valid/ expired/ max-uses-exceeded/ inactive code |
| RefundService | policy tiers; 0% after show start; partial seat amounts |
| PaymentStrategy | token_success → SUCCESS; token_fail → FAILED |
| State guards | invalid transitions rejected |

### Integration tests (Testcontainers + MockMvc)

| # | Scenario | Assert |
|---|----------|--------|
| 1 | Admin creates city→theater→screen→seats→movie→show | show_seats count = screen seat count, all AVAILABLE |
| 2 | Customer browses shows by city/date | 200, filtered list |
| 3 | Hold 2 seats happy path | 201, seats HELD, hold ACTIVE, prices locked |
| 4 | **Concurrent hold same seat** | 2 threads: exactly 1 succeeds (201), 1 fails (409) |
| 5 | Hold with 1 seat already HELD | all-or-nothing 409, no partial hold |
| 6 | Release hold | seats AVAILABLE, hold RELEASED |
| 7 | Confirm with token_success | booking CONFIRMED, seats BOOKED, payment SUCCESS |
| 8 | Confirm with token_fail | 402, seats still HELD, no booking row |
| 9 | **Idempotent confirm** | same Idempotency-Key twice → 1 booking row |
| 10 | Confirm expired hold | 409 HOLD_EXPIRED |
| 11 | Confirm with valid discount | discount_amount correct, final total correct |
| 12 | Confirm with invalid discount | 400 |
| 13 | **Hold expiry job** | advance clock / short TTL → seats AVAILABLE, hold EXPIRED |
| 14 | Partial cancel (2 of 3 seats) | PARTIALLY_CANCELLED, refund per policy, seats released |
| 15 | Full cancel | CANCELLED, all seats AVAILABLE |
| 16 | Cancel after show start | 0 refund |
| 17 | Customer cannot read other's booking | 404 |
| 18 | Customer cannot call admin API | 403 |
| 19 | Async notification | confirm returns before notification completes; notification row SENT |
| 20 | Reminder job | booking exists, show in window → reminder notification created |

### Concurrency test pattern

```java
// ExecutorService: N threads POST hold on same seatIds
// CountDownLatch start gate → assert exactly 1 success
```

Use `@Sql` or test fixtures for seed data. `@Transactional` **off** for concurrency tests.

---

## Acceptance Criteria

### Auth & RBAC
- [ ] Register/login/logout works; session cookie required for protected routes
- [ ] ADMIN vs CUSTOMER enforced on all admin/customer endpoints

### Catalog (Admin)
- [ ] Full CRUD chain: city → theater → screen → seats → movie → show
- [ ] Creating show materializes all show_seats
- [ ] Admin can manage discount codes and refund policies

### Browse (Customer)
- [ ] List shows with city/movie/date filters
- [ ] Seat map reflects live status (AVAILABLE / HELD / BOOKED)

### Booking
- [ ] Hold locks seats for 5 min with price locked at hold time
- [ ] Concurrent holds on same seat: no double allocation
- [ ] Confirm charges mock payment, creates booking, marks seats BOOKED
- [ ] Idempotent confirm prevents duplicate bookings
- [ ] Discount code applied correctly at confirm
- [ ] Payment failure does not create booking; seats remain held

### Cancel & Refund
- [ ] Partial and full cancel supported
- [ ] Refund % follows configured policy by hours-before-show
- [ ] Cancelled seats become AVAILABLE again

### Background jobs
- [ ] Expired holds auto-release seats
- [ ] Reminder notifications sent for upcoming shows
- [ ] Confirm/cancel API responses not blocked by notification delivery

### Quality
- [ ] Input validation on all write endpoints
- [ ] Consistent error JSON with correct HTTP status codes
- [ ] README documents assumptions, how to run, test commands
- [ ] All integration tests above pass locally

---

## Implementation Order

1. Boot + Flyway + entities + docker-compose Postgres
2. Auth + RBAC + global exception handler
3. Admin catalog APIs + show → show_seats generation
4. Pricing + discount + refund services (unit tests)
5. Browse + seat map APIs
6. Hold flow + concurrency integration test
7. Confirm + mock payment + idempotency + discount
8. Cancel + refund policy
9. Schedulers (hold expiry, reminders) + async notifications
10. Remaining integration tests + README

---

## Out of Scope (v1)

UI/frontend, Docker deploy/CI/CD, microservices, OAuth/SSO, Redis/Kafka, real payment gateway, email/SMS delivery (log to DB only).

## V2 (Later)

Separate `payments`/`refunds` audit tables, outbox + retry worker, Redis hold cache, read replicas, pagination/search, per-theater timezone.
