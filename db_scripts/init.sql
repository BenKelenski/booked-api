CREATE DATABASE booked;

\c booked;

CREATE TABLE IF NOT EXISTS users
(
    id         SERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auth_identities
(
    id               SERIAL PRIMARY KEY,
    user_id          INTEGER      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(50)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    password_hash    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (provider, provider_user_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id         UUID PRIMARY KEY,
    user_id    INTEGER     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE IF NOT EXISTS shelves
(
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(250) NULL,
    shelf_type  TEXT         NOT NULL DEFAULT 'CUSTOM',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_shelf_status CHECK (shelf_type IN ('TO_READ', 'READING', 'FINISHED', 'CUSTOM')),
    UNIQUE (user_id, name)
);
CREATE UNIQUE INDEX idx_user_shelf_type_unique_non_custom
    ON shelves (user_id, shelf_type)
    WHERE shelf_type != 'CUSTOM';

CREATE TABLE IF NOT EXISTS books
(
    id            SERIAL PRIMARY KEY,
    user_id       INTEGER      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    shelf_id      INTEGER      NOT NULL REFERENCES shelves (id) ON DELETE CASCADE,
    google_id     VARCHAR(250) NOT NULL,
    title         TEXT         NOT NULL,
    authors       TEXT[]       NOT NULL,
    thumbnail_url TEXT,
    current_page  INTEGER      NULL,
    page_count    INTEGER      NULL,
    rating        INTEGER      NULL,
    review        TEXT         NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NULL,
    finished_at   TIMESTAMPTZ  NULL,
    CONSTRAINT uq_books_user_google UNIQUE (user_id, google_id)
);
CREATE INDEX idx_books_user_shelf ON books (user_id, shelf_id);