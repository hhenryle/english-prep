-- "member" replaces "user" as the name of the account type (owner decision).
-- V2 was already merged into main, so it is not edited; this migration renames instead.

ALTER TABLE app_user RENAME TO member;
ALTER TABLE member RENAME COLUMN person_id TO member_id;
ALTER TABLE member RENAME CONSTRAINT app_user_pkey TO member_pkey;
ALTER TABLE member RENAME CONSTRAINT app_user_person_id_fkey TO member_member_id_fkey;

ALTER TABLE refresh_token RENAME COLUMN user_id TO member_id;
ALTER TABLE refresh_token RENAME CONSTRAINT refresh_token_user_id_fkey TO refresh_token_member_id_fkey;
ALTER INDEX ix_refresh_token_user_id RENAME TO ix_refresh_token_member_id;

-- The kind of an existing person row follows the new name.
UPDATE person SET person_type = 'MEMBER' WHERE person_type = 'USER';
