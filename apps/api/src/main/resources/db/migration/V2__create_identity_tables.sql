-- Identity module: persons, users and refresh tokens.
--
-- Every table has the same audit columns (created_at, created_by, updated_at, updated_by,
-- exclusion_cd). They are filled automatically by JPA auditing; the defaults below cover plain SQL.
--   created_by / updated_by : id of the person who made the change, 0 = system.
--   exclusion_cd            : IN = in use, EX = excluded (soft delete).

-- Base table of the hierarchy. A "user" is one kind of person (person_type = 'USER');
-- other kinds (e.g. 'ADMIN') can be added later without changing this table.
CREATE TABLE person (
    person_id     bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    person_type   varchar(20)  NOT NULL,
    first_nm      varchar(100) NOT NULL,
    last_nm       varchar(100) NOT NULL,
    user_nm       varchar(50)  NOT NULL,
    email         varchar(255) NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    bigint       NOT NULL DEFAULT 0,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint       NOT NULL DEFAULT 0,
    exclusion_cd  varchar(2)   NOT NULL DEFAULT 'IN',
    CONSTRAINT ck_person_exclusion_cd CHECK (exclusion_cd IN ('IN', 'EX'))
);

-- Unique ignoring case, and only among rows in use: an excluded (EX) account
-- does not block someone else from taking the same email or user name.
CREATE UNIQUE INDEX ux_person_email_lower   ON person (lower(email))   WHERE exclusion_cd = 'IN';
CREATE UNIQUE INDEX ux_person_user_nm_lower ON person (lower(user_nm)) WHERE exclusion_cd = 'IN';

-- Subtype table: same primary key as person (primary key and foreign key at once).
-- Its audit columns live in the person row.
CREATE TABLE app_user (
    person_id bigint      PRIMARY KEY REFERENCES person (person_id),
    time_zone varchar(64) NOT NULL DEFAULT 'UTC'
);

-- Only the SHA-256 hash of a refresh token is stored, never the token itself.
CREATE TABLE refresh_token (
    refresh_token_id bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint      NOT NULL REFERENCES app_user (person_id) ON DELETE CASCADE,
    token_hash       varchar(64) NOT NULL UNIQUE,
    expires_at       timestamptz NOT NULL,
    revoked_at       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       bigint      NOT NULL DEFAULT 0,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    updated_by       bigint      NOT NULL DEFAULT 0,
    exclusion_cd     varchar(2)  NOT NULL DEFAULT 'IN',
    CONSTRAINT ck_refresh_token_exclusion_cd CHECK (exclusion_cd IN ('IN', 'EX'))
);

CREATE INDEX ix_refresh_token_user_id ON refresh_token (user_id);
