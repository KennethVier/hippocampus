CREATE TABLE users (
    id UUID NOT NULL,
    email VARCHAR NOT NULL,
    display_name VARCHAR NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);
