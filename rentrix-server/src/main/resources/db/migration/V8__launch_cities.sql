-- =============================================================================
-- Launch cities (captain 2026-11-22 23:40, tracker R14; D-20, D-82 – D-85).
-- Mumbai, Hyderabad and Delhi NCR (Delhi, Gurugram, Noida) go live beside
-- Bangalore and Pune. Cities stay data: the web builds its copy from
-- GET /cities and names no city. The nullable region groups cities the web
-- shows together, e.g. "Delhi NCR (Delhi · Gurugram · Noida)". Pincode
-- prefixes follow V3 (D-84): Delhi 11, Gurugram 122, Noida 201, Mumbai 40,
-- Hyderabad 50. Same portable subset as V1 (D-10).
-- =============================================================================
ALTER TABLE cities ADD COLUMN region VARCHAR(80);

INSERT INTO cities (code, name, aliases, state, live, pincode_prefixes, region, created_at, updated_at)
VALUES ('MUM', 'Mumbai', 'Bombay', 'Maharashtra', TRUE, '40', NULL, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000');
INSERT INTO cities (code, name, aliases, state, live, pincode_prefixes, region, created_at, updated_at)
VALUES ('HYD', 'Hyderabad', 'Secunderabad', 'Telangana', TRUE, '50', NULL, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000');
INSERT INTO cities (code, name, aliases, state, live, pincode_prefixes, region, created_at, updated_at)
VALUES ('DEL', 'Delhi', 'New Delhi', 'Delhi', TRUE, '11', 'Delhi NCR', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000');
INSERT INTO cities (code, name, aliases, state, live, pincode_prefixes, region, created_at, updated_at)
VALUES ('GGN', 'Gurugram', 'Gurgaon', 'Haryana', TRUE, '122', 'Delhi NCR', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000');
INSERT INTO cities (code, name, aliases, state, live, pincode_prefixes, region, created_at, updated_at)
VALUES ('NOI', 'Noida', NULL, 'Uttar Pradesh', TRUE, '201', 'Delhi NCR', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000');
