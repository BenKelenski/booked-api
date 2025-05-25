CREATE DATABASE test_db;

\c test_db;

CREATE TABLE book
(
    id         SERIAL PRIMARY KEY,
    title      VARCHAR(250) NOT NULL,
    author     VARCHAR(250) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);