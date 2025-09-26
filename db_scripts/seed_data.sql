\c booked;

INSERT INTO users (id, email, name, created_at)
VALUES (1, 'jane@example.com', 'Jane Smith123', CURRENT_TIMESTAMP);

INSERT INTO auth_identities (id, user_id, provider, provider_user_id, email, password_hash, created_at)
VALUES (1, 1, 'email', 'jane@example.com', 'jane@example.com',
        '$2a$12$J8pZpMNaQnYnrN79pcPEHu6uP6at0Lhlu3TraUTuHlfSMRnFeZsJa', CURRENT_TIMESTAMP);

INSERT INTO refresh_tokens (id, user_id, token_hash, created_at, expires_at)
VALUES ('f080daa4-cc11-4739-a5f7-990999650d4a', 1, '$2a$12$dFvXqG4n0iCc.Hxj.gjqmONTCWcdhWWaGoZJp90scrdXtbQ7nrH1y',
        CURRENT_TIMESTAMP, (CURRENT_TIMESTAMP + INTERVAL '1 day'));


INSERT INTO shelves (id, user_id, name, description, created_at, is_deletable)
VALUES (1, 1, 'To Read', null, CURRENT_TIMESTAMP, false),
       (2, 1, 'Reading', null, CURRENT_TIMESTAMP, false),
       (3, 1, 'Finished', null, CURRENT_TIMESTAMP, false);

INSERT INTO books (id, google_id, title, authors, thumbnail_url, created_at, user_id, shelf_id, status,
                   progress_percent, updated_at, finished_at)
VALUES (1, 'nPF9n0SwstMC', 'Red Rising', '{Pierce Brown}',
        'https://books.google.com/books/content?id=nPF9n0SwstMC&printsec=frontcover&img=1&zoom=1&edge=curl&imgtk=AFLRE73cCFeXZlhiknDwfYezOGZPKyI7keBAxGS0i3yOCOWZ3DCbI3UwwyQj8U2pj_3Mr-dCjIakijR_NRenTnk9tzS5Iw8VaHdtO9k83pX70tG4Xel9mORC-XWXvjxXII0Wx7wLu5Ge&source=gbs_api',
        CURRENT_TIMESTAMP, 1, 1, 'TO_READ', null, null, null),
       (2, 'B4zIAgAAQBAJ', 'Golden Son', '{Pierce Brown}',
        'https://books.google.com/books/publisher/content?id=B4zIAgAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&imgtk=AFLRE720eaYUe-8gxMQ2J0V38z2LvETet1yRSOVEJTYMiOke_aVXT0GKpW84Pm-kEnTjLw3ijXuRHXyw00i7fbItRO4WIN5h-nL5MoppEooioUNn9sLFUbZH8pGSsLrwCAJA_EcWvq2N&source=gbs_api',
        CURRENT_TIMESTAMP, 1, 1, 'TO_READ', null, null, null),
       (3, 'pn7ZCwAAQBAJ', 'Morning Star', '{Pierce Brown}',
        'https://books.google.com/books/publisher/content?id=pn7ZCwAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&imgtk=AFLRE71j3O88K-6Dj3WFWJM2KtHxJ6MNel9fMMnni0InHkYptNmtVdsn0oFBYmG0j9KYiqmfcK8RiqC-zB6ETaNo-2sLquigdBr-DJPkUfDik2nkeYzM5-_0x4VJUi4ZeSlE5J-zcSP6&source=gbs_api',
        CURRENT_TIMESTAMP, 1, 2, 'TO_READ', null, null, null),
       (4, 'UMqXDQAAQBAJ', 'Iron Gold', '{Pierce Brown}',
        'https://books.google.com/books/publisher/content?id=UMqXDQAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&imgtk=AFLRE713eZs35QS7pZT5sHGHt-Rpjg8dxZ6dcAJkMUfl3MGMzcxIUhVZvSKbKMbZFaeSQwIge_Xj3tBcejyDAQhuZBez6Xf0-LF1CTJ5R1YkwJ5UzgTLZKRfeE_vopql2ATxnC3xdAJq&source=gbs_api',
        CURRENT_TIMESTAMP, 1, 2, 'TO_READ', null, null, null),
       (5, 'AmNDDwAAQBAJ', 'Dark Age', '{Pierce Brown}',
        'https://books.google.com/books/publisher/content?id=AmNDDwAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&imgtk=AFLRE70u-2JeR_PxK0fQKJElIymNx3KI0PqjItUPHfsbC5nddYZI3pxl_Wj_OMo8foJw7LwWScOf1alnbcMQUW9LEE6oViBOOGy6CgzqYgb2tKK9cnRGthBok7IjAIpMllPm3t3EZ6y8&source=gbs_api',
        CURRENT_TIMESTAMP, 1, 2, 'TO_READ', null, null, null),
       (6, 'LdDsEAAAQBAJ', 'Light Bringer', '{Pierce Brown}',
        'https://books.google.com/books/publisher/content?id=LdDsEAAAQBAJ&printsec=frontcover&img=1&zoom=1&edge=curl&imgtk=AFLRE71TmGYI6OoY-OH3aTU_mlkvCycX3gQbbVnZqLKpOGdhrOjjJwNQdqzwdLBNJLsYgFYH0VqvQt5Av3kV3civnQL-4IAyBYuTuT_OQfvNhz50mEIGLbyLoFM8E316-G4-pPmV-4Y3&source=gbs_api',
        CURRENT_TIMESTAMP, 1, 2, 'TO_READ', null, null, null);

-- Reset sequences to avoid ID conflicts
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('auth_identities_id_seq', (SELECT MAX(id) FROM auth_identities));
SELECT setval('shelves_id_seq', (SELECT MAX(id) FROM shelves));
SELECT setval('books_id_seq', (SELECT MAX(id) FROM books));
