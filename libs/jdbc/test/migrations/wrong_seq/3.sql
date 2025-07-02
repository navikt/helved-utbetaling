CREATE TABLE TEST_TABLE2
(
    id            UUID PRIMARY KEY,
    kind          VARCHAR     NOT NULL,
    payload       VARCHAR     NOT NULL,
    status        VARCHAR(20) NOT NULL,
    attempt       BIGINT      NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    scheduled_for TIMESTAMP   NOT NULL,
    message       VARCHAR
);
