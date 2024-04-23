DO
$do$
BEGIN
    IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'cloudsqliamuser') THEN
        GRANT ALL ON ALL tables IN SCHEMA public TO cloudsqliamuser;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON tables TO cloudsqliamuser;
    END IF;
END
$do$;
