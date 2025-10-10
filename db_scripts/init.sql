CREATE DATABASE booked;

\c booked;

CREATE TABLE IF NOT EXISTS users
(
    id         SERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
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
    user_id    INTEGER   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);


CREATE TABLE IF NOT EXISTS shelves
(
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(250) NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE shelves
    ADD COLUMN is_deletable BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE shelves
    ADD COLUMN reading_status TEXT NULL,
    ADD CONSTRAINT chk_books_status
        CHECK (reading_status IN ('TO_READ', 'READING', 'FINISHED'));


CREATE TABLE IF NOT EXISTS books
(
    id            SERIAL PRIMARY KEY,
    google_id     VARCHAR(250) NOT NULL,
    title         TEXT         NOT NULL,
    authors       TEXT[]       NOT NULL,
    thumbnail_url TEXT,
    current_page  INTEGER      NOT NULL,
    page_count    INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id       INTEGER      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    shelf_id      INTEGER      NOT NULL REFERENCES shelves (id) ON DELETE CASCADE
);

ALTER TABLE books
    ADD CONSTRAINT uq_books_user_google UNIQUE (user_id, google_id);

ALTER TABLE books
    ADD COLUMN updated_at TIMESTAMPTZ NULL;

ALTER TABLE books
    ADD COLUMN finished_at TIMESTAMPTZ NULL;