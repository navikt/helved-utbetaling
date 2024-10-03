INSERT INTO migrations (version, filename, checksum, created_at, success)
VALUES (1, 'V1__create_oppdrag_lager.sql', '589ad1fc9acf17f292d7ea1e4e23d9af', now(), true)
ON CONFLICT (version) DO NOTHING;

INSERT INTO migrations (version, filename, checksum, created_at, success)
VALUES (2, 'V2__simuleringslager.sql', 'c23bc83cbc97c66889134e2f9ad3e317', now(), true)
ON CONFLICT (version) DO NOTHING;

INSERT INTO migrations (version, filename, checksum, created_at, success)
VALUES (3, 'V3__opprett_mellomlagring_konsistensavstemming.sql', '169bb3ee0722ab1ef47f988fdfdef67a', now(), true)
ON CONFLICT (version) DO NOTHING;

INSERT INTO migrations (version, filename, checksum, created_at, success)
VALUES (4, 'V4__privileges.sql', '31680997117a887e8c5faa29856d6780', now(), true)
ON CONFLICT (version) DO NOTHING;

INSERT INTO migrations (version, filename, checksum, created_at, success)
VALUES (5, 'V5__iverksetting_id.sql', '63a66059fa3c0b4cc3dd8031613d304e', now(), true)
ON CONFLICT (version) DO NOTHING;

INSERT INTO migrations (version, filename, checksum, created_at, success)
VALUES (6, 'V6__fjern_personident.sql', 'fb66a0ab9cace76e9f89142ca0f78083', now(), true)
ON CONFLICT (version) DO NOTHING;
