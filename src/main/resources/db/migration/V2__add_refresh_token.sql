CREATE TABLE refresh_token (
   id BIGSERIAL PRIMARY KEY,
   user_id BIGINT NOT NULL,
   token_hash VARCHAR(64) NOT NULL,
   token_version BIGINT NOT NULL,
   expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
   revoked_at TIMESTAMP WITH TIME ZONE,
   created_at TIMESTAMP WITH TIME ZONE NOT NULL,

   CONSTRAINT fk_refresh_token_user
       FOREIGN KEY (user_id)
           REFERENCES users(id)
           ON DELETE CASCADE,

   CONSTRAINT uq_refresh_token_hash
       UNIQUE (token_hash),

   CONSTRAINT ck_refresh_token_hash_length
       CHECK (char_length(token_hash) = 64)
);

CREATE INDEX idx_refresh_token_user
    ON refresh_token(user_id);

CREATE INDEX idx_refresh_token_expiration
    ON refresh_token(expires_at)
    WHERE revoked_at IS NULL;