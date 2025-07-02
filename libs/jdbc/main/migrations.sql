CREATE TABLE IF NOT EXISTS migrations
(
    version    INTEGER      unique not null,
    filename   varchar(128) unique not null,
    checksum   varchar(256)      unique not null,
    created_at timestamp           not null,
    success    boolean             not null
);
