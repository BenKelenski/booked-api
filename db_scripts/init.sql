CREATE DATABASE booked;

\c booked;

CREATE TABLE IF NOT EXISTS book
(
    id         SERIAL PRIMARY KEY,
    title      VARCHAR(250) NOT NULL,
    author     VARCHAR(250) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS shelf
(
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(250),
    created_at  TIMESTAMPTZ  NOT NULL
);