CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE cities (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE theaters (
    id          BIGSERIAL PRIMARY KEY,
    city_id     BIGINT       NOT NULL REFERENCES cities(id),
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (city_id, name)
);

CREATE TABLE screens (
    id          BIGSERIAL PRIMARY KEY,
    theater_id  BIGINT       NOT NULL REFERENCES theaters(id),
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (theater_id, name)
);

CREATE TABLE seats (
    id           BIGSERIAL PRIMARY KEY,
    screen_id    BIGINT      NOT NULL REFERENCES screens(id),
    row_label    VARCHAR(10) NOT NULL,
    seat_number  INT         NOT NULL,
    tier         VARCHAR(20) NOT NULL CHECK (tier IN ('REGULAR', 'PREMIUM')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (screen_id, row_label, seat_number)
);

CREATE TABLE movies (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    duration_minutes INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE refund_policies (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    rules       JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE shows (
    id               BIGSERIAL PRIMARY KEY,
    movie_id         BIGINT       NOT NULL REFERENCES movies(id),
    screen_id        BIGINT       NOT NULL REFERENCES screens(id),
    refund_policy_id BIGINT       REFERENCES refund_policies(id),
    start_time       TIMESTAMPTZ  NOT NULL,
    end_time         TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE holds (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    show_id     BIGINT       NOT NULL REFERENCES shows(id),
    expires_at  TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'EXPIRED', 'RELEASED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE discount_codes (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    type         VARCHAR(20)  NOT NULL CHECK (type IN ('PERCENT', 'FLAT')),
    value        NUMERIC(10, 2) NOT NULL,
    max_uses     INT,
    uses_count   INT          NOT NULL DEFAULT 0,
    valid_until  TIMESTAMPTZ,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE bookings (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT         NOT NULL REFERENCES users(id),
    show_id          BIGINT         NOT NULL REFERENCES shows(id),
    hold_id          BIGINT         NOT NULL REFERENCES holds(id),
    status           VARCHAR(30)    NOT NULL DEFAULT 'CONFIRMED'
        CHECK (status IN ('CONFIRMED', 'PARTIALLY_CANCELLED', 'CANCELLED')),
    subtotal         NUMERIC(10, 2) NOT NULL,
    discount_amount  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total_amount     NUMERIC(10, 2) NOT NULL,
    discount_code_id BIGINT         REFERENCES discount_codes(id),
    payment_method   VARCHAR(20)    NOT NULL,
    payment_status   VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED')),
    provider_ref     VARCHAR(255),
    idempotency_key  VARCHAR(255) UNIQUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE show_seats (
    id                 BIGSERIAL PRIMARY KEY,
    show_id            BIGINT         NOT NULL REFERENCES shows(id),
    seat_id            BIGINT         NOT NULL REFERENCES seats(id),
    status             VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    hold_id            BIGINT         REFERENCES holds(id),
    held_until         TIMESTAMPTZ,
    locked_base_price  NUMERIC(10, 2),
    booking_id         BIGINT         REFERENCES bookings(id),
    version            BIGINT         NOT NULL DEFAULT 0,
    UNIQUE (show_id, seat_id)
);

CREATE INDEX idx_show_seats_show_status ON show_seats (show_id, status);

CREATE TABLE booking_seats (
    id             BIGSERIAL PRIMARY KEY,
    booking_id     BIGINT         NOT NULL REFERENCES bookings(id),
    show_seat_id   BIGINT         NOT NULL REFERENCES show_seats(id),
    seat_id        BIGINT         NOT NULL REFERENCES seats(id),
    price_paid     NUMERIC(10, 2) NOT NULL,
    refund_amount  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'CANCELLED')),
    UNIQUE (booking_id, show_seat_id)
);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    booking_id  BIGINT       REFERENCES bookings(id),
    type        VARCHAR(30)  NOT NULL
        CHECK (type IN ('BOOKING_CONFIRMATION', 'BOOKING_CANCELLED', 'SHOW_REMINDER', 'SHOW_CANCELLED')),
    channel     VARCHAR(20)  NOT NULL DEFAULT 'LOG',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    payload     JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at     TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user ON notifications (user_id);
CREATE INDEX idx_notifications_booking_type ON notifications (booking_id, type);

CREATE TABLE app_config (
    config_key  VARCHAR(100) PRIMARY KEY,
    value       JSONB NOT NULL
);

INSERT INTO app_config (config_key, value) VALUES (
    'pricing',
    '{"regularPrice": 200, "premiumPrice": 350, "weekendMultiplier": 1.25}'::jsonb
);
