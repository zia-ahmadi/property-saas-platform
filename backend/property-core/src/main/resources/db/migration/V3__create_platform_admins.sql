CREATE TABLE platform_admins (
    user_id     UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_admins_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
