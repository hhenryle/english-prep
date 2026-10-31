-- Identity module: user accounts and refresh tokens.
-- The table is "app_user" because "user" is a reserved word in PostgreSQL.

CREATE TABLE app_user (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         varchar(255) NOT NULL,
    password_hash varchar(100) NOT NULL,
    time_zone     varchar(64)  NOT NULL DEFAULT 'UTC',
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

-- Emails are unique ignoring case: A@x.com and a@x.com are the same account.
CREATE UNIQUE INDEX ux_app_user_email_lower ON app_user (lower(email));

-- Only the SHA-256 hash of a refresh token is stored, never the token itself.
CREATE TABLE refresh_token (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_token_user_id ON refresh_token (user_id);
