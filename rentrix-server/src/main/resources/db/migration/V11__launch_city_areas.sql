-- =============================================================================
-- Areas of the launch cities (captain 2026-11-23, #53 click-test bug 3; V8).
-- Hyderabad, Gurugram, Delhi, Noida and Mumbai went live with no areas, so every
-- user there had to suggest one. These are well-known localities with their PIN
-- codes, ACTIVE reference rows (not demo data, so every profile gets them). A
-- row is skipped when its city already has an area of that name (for example a
-- user's PENDING proposal), so the migration never breaks a unique key. Ids are
-- fixed small numbers, far below the time-ordered ids the app makes (D-12).
-- Same portable subset as V1 (D-10).
-- =============================================================================

-- HYD
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 101, '01a205b3-d000-7c13-b970-56d89362da5c', code, 'Gachibowli', 'gachibowli', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'gachibowli');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500032', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 101;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 102, '01a205b3-d000-7f6f-9f9b-7739a7ace732', code, 'Madhapur', 'madhapur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'madhapur');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500081', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 102;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 103, '01a205b3-d000-728f-b405-6c372c63888f', code, 'HITEC City', 'hitec city', 'Hitech City', 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'hitec city');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500081', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 103;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 104, '01a205b3-d000-78f7-a284-66e716bc1732', code, 'Kondapur', 'kondapur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'kondapur');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500084', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 104;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 105, '01a205b3-d000-7da6-98df-5bbe609e467a', code, 'Banjara Hills', 'banjara hills', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'banjara hills');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500034', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 105;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 106, '01a205b3-d000-7bc6-81e1-f0372f960063', code, 'Jubilee Hills', 'jubilee hills', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'jubilee hills');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500033', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 106;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 107, '01a205b3-d000-7a78-84b9-d3defab36728', code, 'Kukatpally', 'kukatpally', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'kukatpally');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500072', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 107;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 108, '01a205b3-d000-719d-a9c4-9d576cace4d5', code, 'Begumpet', 'begumpet', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'begumpet');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500016', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 108;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 109, '01a205b3-d000-7458-8716-627cba2dde83', code, 'Manikonda', 'manikonda', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'manikonda');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500089', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 109;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 110, '01a205b3-d000-7ac8-8365-e0afcdc83011', code, 'Miyapur', 'miyapur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'miyapur');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500049', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 110;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 111, '01a205b3-d000-7482-9ddd-da0b5796a670', code, 'Kompally', 'kompally', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'HYD' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'HYD' AND name_key = 'kompally');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '500014', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 111;

-- GGN
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 112, '01a205b3-d000-7a93-82e0-289553a12945', code, 'DLF Phase 1', 'dlf phase 1', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'dlf phase 1');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122002', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 112;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 113, '01a205b3-d000-7f2e-b083-6f080f8a7abf', code, 'DLF Phase 4', 'dlf phase 4', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'dlf phase 4');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122009', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 113;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 114, '01a205b3-d000-745a-9e27-06bd0ce8fcc9', code, 'Sushant Lok', 'sushant lok', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'sushant lok');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122009', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 114;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 115, '01a205b3-d000-7d28-b073-02bfdd2ee150', code, 'Golf Course Road', 'golf course road', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'golf course road');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122002', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 115;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122011', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 115;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 116, '01a205b3-d000-790b-8f0e-b8d4fb14628e', code, 'Sohna Road', 'sohna road', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'sohna road');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122018', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 116;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 117, '01a205b3-d000-7322-b91b-b589d419c772', code, 'Palam Vihar', 'palam vihar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'palam vihar');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122017', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 117;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 118, '01a205b3-d000-7b9c-a5bc-36a17a2a3b87', code, 'South City', 'south city', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'south city');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122001', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 118;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 119, '01a205b3-d000-71d3-a8c3-d0c0bbeeb636', code, 'Nirvana Country', 'nirvana country', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'nirvana country');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122018', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 119;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 120, '01a205b3-d000-7525-9a39-6242ccb2b416', code, 'Sector 56', 'sector 56', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'sector 56');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122011', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 120;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 121, '01a205b3-d000-7678-97ab-5c0e84c4b877', code, 'MG Road', 'mg road', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'GGN' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'GGN' AND name_key = 'mg road');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '122002', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 121;

-- DEL
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 122, '01a205b3-d000-7ff5-a545-2df7e2ad0478', code, 'Saket', 'saket', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'saket');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110017', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 122;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 123, '01a205b3-d000-7a6e-9ad2-ddace179f773', code, 'Malviya Nagar', 'malviya nagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'malviya nagar');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110017', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 123;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 124, '01a205b3-d000-764b-830a-33b0b94722af', code, 'Hauz Khas', 'hauz khas', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'hauz khas');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110016', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 124;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 125, '01a205b3-d000-7899-b50d-477d83229050', code, 'Lajpat Nagar', 'lajpat nagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'lajpat nagar');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110024', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 125;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 126, '01a205b3-d000-7a39-90dc-a8a984a67245', code, 'Greater Kailash', 'greater kailash', 'GK', 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'greater kailash');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110048', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 126;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 127, '01a205b3-d000-74f1-881c-c6ac4c8b1eb4', code, 'Vasant Kunj', 'vasant kunj', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'vasant kunj');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110070', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 127;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 128, '01a205b3-d000-70e3-ab24-4c3d547c17d3', code, 'Dwarka', 'dwarka', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'dwarka');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110075', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 128;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110078', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 128;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 129, '01a205b3-d000-7651-ab0c-ceda7e9bf77c', code, 'Rohini', 'rohini', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'rohini');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110085', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 129;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 130, '01a205b3-d000-7ca3-8e36-2274b47a5c1d', code, 'Karol Bagh', 'karol bagh', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'karol bagh');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110005', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 130;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 131, '01a205b3-d000-7449-b321-ae9903c472ca', code, 'Connaught Place', 'connaught place', 'CP', 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'connaught place');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110001', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 131;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 132, '01a205b3-d000-76c2-9df7-788adf88ae4d', code, 'Mayur Vihar', 'mayur vihar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'mayur vihar');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110091', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 132;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 133, '01a205b3-d000-7a22-a021-5e39ec925f09', code, 'Janakpuri', 'janakpuri', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'DEL' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'DEL' AND name_key = 'janakpuri');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '110058', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 133;

-- NOI
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 134, '01a205b3-d000-7c3d-aa80-cdb68b8c82f1', code, 'Sector 15', 'sector 15', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 15');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201301', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 134;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 135, '01a205b3-d000-74ce-8bf5-ec8ebb262a93', code, 'Sector 18', 'sector 18', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 18');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201301', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 135;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 136, '01a205b3-d000-7307-ac60-c95ba16ec121', code, 'Sector 50', 'sector 50', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 50');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201301', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 136;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 137, '01a205b3-d000-7e83-9ca6-b54f7891da33', code, 'Sector 44', 'sector 44', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 44');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201303', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 137;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 138, '01a205b3-d000-78f3-ab7d-0e3db27c1c74', code, 'Sector 62', 'sector 62', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 62');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201309', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 138;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 139, '01a205b3-d000-7a29-8afb-7685e5a9f6b1', code, 'Sector 93', 'sector 93', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 93');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201304', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 139;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 140, '01a205b3-d000-7d27-bc68-e34c68340c65', code, 'Sector 128', 'sector 128', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 128');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201304', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 140;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 141, '01a205b3-d000-7b97-8817-f8bd08c1d781', code, 'Sector 137', 'sector 137', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 137');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201305', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 141;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 142, '01a205b3-d000-7d00-ab2d-c37642762334', code, 'Sector 150', 'sector 150', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'sector 150');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201310', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 142;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 143, '01a205b3-d000-71f6-be71-2e4cb21b1c2e', code, 'Noida Extension', 'noida extension', 'Greater Noida West', 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'NOI' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'NOI' AND name_key = 'noida extension');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '201318', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 143;

-- MUM
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 144, '01a205b3-d000-76d9-8f3a-c20c8744ee92', code, 'Andheri West', 'andheri west', 'Andheri', 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'andheri west');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400053', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 144;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 145, '01a205b3-d000-756e-addd-a95a29ca4bb9', code, 'Andheri East', 'andheri east', 'Andheri', 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'andheri east');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400069', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 145;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 146, '01a205b3-d000-7b33-92da-36e9a2bb831d', code, 'Bandra West', 'bandra west', 'Bandra', 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'bandra west');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400050', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 146;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 147, '01a205b3-d000-7405-b7f9-b156ea44bd0d', code, 'Powai', 'powai', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'powai');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400076', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 147;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 148, '01a205b3-d000-784b-99c3-073455555e31', code, 'Juhu', 'juhu', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'juhu');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400049', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 148;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 149, '01a205b3-d000-72f8-a290-a09ff0d379d7', code, 'Goregaon', 'goregaon', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'goregaon');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400062', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 149;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400063', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 149;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 150, '01a205b3-d000-76f8-a9fe-008a4eedef2a', code, 'Malad West', 'malad west', 'Malad', 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'malad west');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400064', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 150;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 151, '01a205b3-d000-735d-a778-6291adff32f5', code, 'Chembur', 'chembur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'chembur');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400071', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 151;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 152, '01a205b3-d000-7048-8e5b-7e8c6763df9c', code, 'Lower Parel', 'lower parel', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'lower parel');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400013', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 152;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 153, '01a205b3-d000-70b7-b527-729ce615dc02', code, 'Worli', 'worli', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'worli');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400018', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 153;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 154, '01a205b3-d000-77f1-8e44-d8c21216b6dc', code, 'Colaba', 'colaba', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'colaba');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '400005', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 154;
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 155, '01a205b3-d000-7183-9540-d0990f66fa84', code, 'Mira Road', 'mira road', NULL, 'ACTIVE', NULL, FALSE, '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM cities
WHERE code = 'MUM' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'MUM' AND name_key = 'mira road');
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '401107', '2026-11-23 00:00:00.000', '2026-11-23 00:00:00.000' FROM areas WHERE id = 155;
