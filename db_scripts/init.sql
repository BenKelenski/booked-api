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
    password_hash    VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (provider, provider_user_id)
);


CREATE TABLE IF NOT EXISTS shelves
(
    id          SERIAL PRIMARY KEY,
    user_id     VARCHAR(128) NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(250) NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS books
(
    id         SERIAL PRIMARY KEY,
    user_id    VARCHAR(128) NOT NULL,
    title      VARCHAR(250) NOT NULL,
    author     VARCHAR(250) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    shelf_id   INTEGER      NOT NULL,
    FOREIGN KEY (shelf_id)
        REFERENCES shelves (id)
        ON DELETE CASCADE
);