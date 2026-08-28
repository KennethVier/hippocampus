CREATE TABLE user_password_credentials (
    user_id UUID NOT NULL,
    password_hash VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_password_credentials_pkey PRIMARY KEY (user_id),
    CONSTRAINT user_password_credentials_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);
