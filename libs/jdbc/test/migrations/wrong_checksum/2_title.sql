CREATE TABLE TEST_TABLE2
(
    id            UUID PRIMARY KEY,
    kind          VARCHAR     NOT NULL,
    scheduled_for TIMESTAMP   NOT NULL,
    message       VARCHAR
);
