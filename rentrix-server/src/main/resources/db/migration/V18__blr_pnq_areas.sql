-- =============================================================================
-- Areas of Bangalore and Pune (captain 2026-11-24, LIVE bug: area + PIN auto-fill
-- did nothing there). V11 seeded areas for HYD, GGN, DEL, NOI and MUM only, so BLR
-- and PNQ had areas only from the dev demo seed, and LIVE had none. These are main
-- residential localities, ACTIVE reference rows (not demo data, so every profile
-- gets them): 82 in BLR (ids 201-282) and 81 in PNQ (ids 301-381).
--
-- PIN source: India Post "All India Pincode Directory" (post office list with PIN
-- codes, Department of Posts, Open Government Data Platform India):
--   https://data.gov.in/resource/all-india-pincode-directory-till-last-month
--   file: https://www.data.gov.in/sites/default/files/all_india_PO_list_without_APS_offices_ver2_lat_long.csv
--   (sha256 6cb2edb44b568077b9d8a2962cffba05dafc1799e536c34fe9a30c75484612c1, read 2026-11-24)
-- Every PIN below is the pincode of the India Post office(s) named in the comment
-- above its area (districts Bangalore / Bangalore Rural with PIN 560xxx, and Pune
-- with PIN 410xxx-412xxx); none is typed by hand.
--
-- A row is skipped when its city already has an area of that name, an area named
-- like one of its aliases, or an area that lists this name among its aliases (for
-- example a user's PENDING proposal, or the demo seed's rows on dev), so the
-- migration never breaks a unique key and never duplicates a place. Existing rows
-- are not changed. An area's PIN codes are only added when its own row was added.
-- Ids are fixed small numbers, far below the time-ordered ids the app makes (D-12).
-- Same portable subset as V1 (D-10).
-- =============================================================================

-- BLR
-- Koramangala: India Post Koramangala S.O 560034, Koramangala VI Bk S.O 560095
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 201, '01a205b3-d000-71ec-bcb9-ae5127dd55cf', code, 'Koramangala', 'koramangala', 'Kormangala', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'koramangala' OR name_key = 'kormangala' OR LOWER(aliases) LIKE '%koramangala%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560034', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 201;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560095', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 201;
-- HSR Layout: India Post HSR Layout S.O 560102
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 202, '01a205b3-d000-70b2-9f74-d48bae2d2ee8', code, 'HSR Layout', 'hsr layout', 'HSR', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'hsr layout' OR name_key = 'hsr' OR LOWER(aliases) LIKE '%hsr layout%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560102', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 202;
-- Whitefield: India Post Whitefield S.O 560066
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 203, '01a205b3-d000-7e2e-a96c-ae51e2659cdd', code, 'Whitefield', 'whitefield', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'whitefield' OR LOWER(aliases) LIKE '%whitefield%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560066', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 203;
-- Indiranagar: India Post Indiranagar S.O (Bangalore) 560038
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 204, '01a205b3-d000-7209-addd-369bb2a83b03', code, 'Indiranagar', 'indiranagar', 'Indira Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'indiranagar' OR name_key = 'indira nagar' OR LOWER(aliases) LIKE '%indiranagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560038', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 204;
-- Jayanagar: India Post Jayangar III Block S.O 560011, Jayanagar H.O 560041
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 205, '01a205b3-d000-726f-937a-9728e3c31a32', code, 'Jayanagar', 'jayanagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'jayanagar' OR LOWER(aliases) LIKE '%jayanagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560011', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 205;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560041', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 205;
-- Malleswaram: India Post Malleswaram S.O 560003, Malleswaram West S.O 560055
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 206, '01a205b3-d000-7137-8a74-d70f6461ce71', code, 'Malleswaram', 'malleswaram', 'Malleshwaram', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'malleswaram' OR name_key = 'malleshwaram' OR LOWER(aliases) LIKE '%malleswaram%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560003', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 206;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560055', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 206;
-- Rajajinagar: India Post Rajajinagar H.O 560010
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 207, '01a205b3-d000-7115-bcad-c404bd39a177', code, 'Rajajinagar', 'rajajinagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'rajajinagar' OR LOWER(aliases) LIKE '%rajajinagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560010', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 207;
-- Basavanagudi: India Post Basavanagudi H.O 560004
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 208, '01a205b3-d000-7a43-8931-da8b92ccc69b', code, 'Basavanagudi', 'basavanagudi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'basavanagudi' OR LOWER(aliases) LIKE '%basavanagudi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560004', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 208;
-- Banashankari: India Post Banashankari S.O 560050, B Sk II Stage S.O 560070, Banashankari III Stage S.O 560085
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 209, '01a205b3-d000-763c-88e4-bad2a2a6643a', code, 'Banashankari', 'banashankari', 'BSK', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'banashankari' OR name_key = 'bsk' OR LOWER(aliases) LIKE '%banashankari%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560050', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 209;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560070', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 209;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560085', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 209;
-- JP Nagar: India Post J P Nagar S.O 560078, JP Nagar VIII phase S.O 560076
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 210, '01a205b3-d000-7300-b447-ed7c95e8dc3c', code, 'JP Nagar', 'jp nagar', 'J P Nagar, Jayaprakash Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'jp nagar' OR name_key = 'j p nagar' OR name_key = 'jayaprakash nagar' OR LOWER(aliases) LIKE '%jp nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560076', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 210;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560078', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 210;
-- BTM Layout: India Post Dharmaram College S.O 560029, Mico Layout S.O 560076
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 211, '01a205b3-d000-7d66-837b-bc5b91c31239', code, 'BTM Layout', 'btm layout', 'BTM', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'btm layout' OR name_key = 'btm' OR LOWER(aliases) LIKE '%btm layout%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560029', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 211;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560076', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 211;
-- Electronic City: India Post Electronics City S.O 560100
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 212, '01a205b3-d000-7001-9d7f-8ec5121203ea', code, 'Electronic City', 'electronic city', 'Electronics City', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'electronic city' OR name_key = 'electronics city' OR LOWER(aliases) LIKE '%electronic city%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560100', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 212;
-- Bellandur: India Post Bellandur S.O 560103
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 213, '01a205b3-d000-724f-bfb5-57dbc0116b52', code, 'Bellandur', 'bellandur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'bellandur' OR LOWER(aliases) LIKE '%bellandur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560103', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 213;
-- Marathahalli: India Post Marathahalli Colony S.O 560037
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 214, '01a205b3-d000-7172-89c0-5cdc189365f1', code, 'Marathahalli', 'marathahalli', 'Marathalli', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'marathahalli' OR name_key = 'marathalli' OR LOWER(aliases) LIKE '%marathahalli%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560037', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 214;
-- Mahadevapura: India Post Mahadevapura S.O 560048
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 215, '01a205b3-d000-7c14-9524-2f1ca489e912', code, 'Mahadevapura', 'mahadevapura', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'mahadevapura' OR LOWER(aliases) LIKE '%mahadevapura%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560048', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 215;
-- Hoodi: India Post Hoodi B.O 560048
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 216, '01a205b3-d000-7506-bc7c-9cdfe671a1bb', code, 'Hoodi', 'hoodi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'hoodi' OR LOWER(aliases) LIKE '%hoodi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560048', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 216;
-- KR Puram: India Post Krishnarajapuram S.O 560036, Krishnarajapuram R S S.O 560016
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 217, '01a205b3-d000-7651-882d-88ee65f26f7e', code, 'KR Puram', 'kr puram', 'K R Puram, Krishnarajapuram', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'kr puram' OR name_key = 'k r puram' OR name_key = 'krishnarajapuram' OR LOWER(aliases) LIKE '%kr puram%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560016', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 217;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560036', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 217;
-- Ramamurthy Nagar: India Post Ramamurthy Nagar S.O 560016
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 218, '01a205b3-d000-70f2-8c39-55fc7853a573', code, 'Ramamurthy Nagar', 'ramamurthy nagar', 'Ramamurthynagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'ramamurthy nagar' OR name_key = 'ramamurthynagar' OR LOWER(aliases) LIKE '%ramamurthy nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560016', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 218;
-- Banaswadi: India Post Banaswadi S.O 560043
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 219, '01a205b3-d000-7583-9038-e5480ae40b65', code, 'Banaswadi', 'banaswadi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'banaswadi' OR LOWER(aliases) LIKE '%banaswadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560043', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 219;
-- Kalyan Nagar: India Post Kalyananagar S.O 560043
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 220, '01a205b3-d000-7547-8b28-22509af5096b', code, 'Kalyan Nagar', 'kalyan nagar', 'Kalyananagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'kalyan nagar' OR name_key = 'kalyananagar' OR LOWER(aliases) LIKE '%kalyan nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560043', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 220;
-- Horamavu: India Post Horamavu B.O 560043
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 221, '01a205b3-d000-74bf-a73e-85c40bfe7a4e', code, 'Horamavu', 'horamavu', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'horamavu' OR LOWER(aliases) LIKE '%horamavu%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560043', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 221;
-- Hebbal: India Post Hebbal Kempapura S.O 560024
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 222, '01a205b3-d000-7b89-aafe-688f811a79bd', code, 'Hebbal', 'hebbal', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'hebbal' OR LOWER(aliases) LIKE '%hebbal%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560024', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 222;
-- RT Nagar: India Post R T Nagar H.O 560032
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 223, '01a205b3-d000-76db-abef-bede3e08bdbb', code, 'RT Nagar', 'rt nagar', 'R T Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'rt nagar' OR name_key = 'r t nagar' OR LOWER(aliases) LIKE '%rt nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560032', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 223;
-- Sahakara Nagar: India Post Sahakaranagar P.O S.O 560092
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 224, '01a205b3-d000-7468-80c5-cfe002d22f85', code, 'Sahakara Nagar', 'sahakara nagar', 'Sahakar Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'sahakara nagar' OR name_key = 'sahakar nagar' OR LOWER(aliases) LIKE '%sahakara nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560092', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 224;
-- Yelahanka: India Post Yelahanka S.O 560064, Yelahanka Satellite Town S.O 560064
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 225, '01a205b3-d000-7400-ac8a-de63998168e7', code, 'Yelahanka', 'yelahanka', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'yelahanka' OR LOWER(aliases) LIKE '%yelahanka%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560064', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 225;
-- Jakkur: India Post Jakkur B.O 560064
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 226, '01a205b3-d000-786e-b648-e25d1799426d', code, 'Jakkur', 'jakkur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'jakkur' OR LOWER(aliases) LIKE '%jakkur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560064', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 226;
-- Vidyaranyapura: India Post Vidyaranyapura S.O 560097
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 227, '01a205b3-d000-781c-90e2-94cf08816a89', code, 'Vidyaranyapura', 'vidyaranyapura', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'vidyaranyapura' OR LOWER(aliases) LIKE '%vidyaranyapura%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560097', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 227;
-- Yeshwanthpur: India Post Yeswanthpura S.O 560022, Yeshwanthpur Bazar S.O 560022
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 228, '01a205b3-d000-7784-9352-bd34c8f0b076', code, 'Yeshwanthpur', 'yeshwanthpur', 'Yeswanthpur, Yeshwantpur', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'yeshwanthpur' OR name_key = 'yeswanthpur' OR name_key = 'yeshwantpur' OR LOWER(aliases) LIKE '%yeshwanthpur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560022', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 228;
-- Mathikere: India Post Mathikere S.O 560054
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 229, '01a205b3-d000-704a-bf84-6abe1b9e3d3c', code, 'Mathikere', 'mathikere', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'mathikere' OR LOWER(aliases) LIKE '%mathikere%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560054', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 229;
-- Sadashivanagar: India Post Sadashivanagar S.O 560080
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 230, '01a205b3-d000-73b1-a9ef-01e973a08caa', code, 'Sadashivanagar', 'sadashivanagar', 'Sadashiva Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'sadashivanagar' OR name_key = 'sadashiva nagar' OR LOWER(aliases) LIKE '%sadashivanagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560080', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 230;
-- Vasanth Nagar: India Post Vasanthanagar S.O 560001
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 231, '01a205b3-d000-7987-8abc-d10e326b42d3', code, 'Vasanth Nagar', 'vasanth nagar', 'Vasanthanagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'vasanth nagar' OR name_key = 'vasanthanagar' OR LOWER(aliases) LIKE '%vasanth nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560001', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 231;
-- Frazer Town: India Post Fraser Town S.O 560005
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 232, '01a205b3-d000-7ae5-96cf-96e69e95b7a9', code, 'Frazer Town', 'frazer town', 'Fraser Town, Pulikeshi Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'frazer town' OR name_key = 'fraser town' OR name_key = 'pulikeshi nagar' OR LOWER(aliases) LIKE '%frazer town%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560005', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 232;
-- Benson Town: India Post Benson Town S.O 560046
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 233, '01a205b3-d000-7811-9997-78fb59b3922c', code, 'Benson Town', 'benson town', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'benson town' OR LOWER(aliases) LIKE '%benson town%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560046', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 233;
-- Richmond Town: India Post Richmond Town S.O 560025
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 234, '01a205b3-d000-7b43-ac3d-506eba527404', code, 'Richmond Town', 'richmond town', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'richmond town' OR LOWER(aliases) LIKE '%richmond town%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560025', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 234;
-- Shanthinagar: India Post Shanthinagar S.O 560027
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 235, '01a205b3-d000-777f-8f59-111a94d9f08b', code, 'Shanthinagar', 'shanthinagar', 'Shanti Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'shanthinagar' OR name_key = 'shanti nagar' OR LOWER(aliases) LIKE '%shanthinagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560027', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 235;
-- Wilson Garden: India Post Wilson Garden S.O 560027
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 236, '01a205b3-d000-7f89-a2ba-9b863e31cd57', code, 'Wilson Garden', 'wilson garden', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'wilson garden' OR LOWER(aliases) LIKE '%wilson garden%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560027', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 236;
-- Ulsoor: India Post Hulsur Bazaar S.O 560008
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 237, '01a205b3-d000-710c-bde0-f1ad63486ac1', code, 'Ulsoor', 'ulsoor', 'Halasuru, Hulsur', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'ulsoor' OR name_key = 'halasuru' OR name_key = 'hulsur' OR LOWER(aliases) LIKE '%ulsoor%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560008', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 237;
-- Domlur: India Post Domlur S.O 560071
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 238, '01a205b3-d000-7e96-8881-743a8b451c8f', code, 'Domlur', 'domlur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'domlur' OR LOWER(aliases) LIKE '%domlur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560071', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 238;
-- HAL 2nd Stage: India Post H.A.L II Stage H.O 560008
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 239, '01a205b3-d000-7ea8-9fc5-733e1e6e291d', code, 'HAL 2nd Stage', 'hal 2nd stage', 'HAL II Stage', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'hal 2nd stage' OR name_key = 'hal ii stage' OR LOWER(aliases) LIKE '%hal 2nd stage%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560008', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 239;
-- CV Raman Nagar: India Post C.V.Raman Nagar S.O 560093
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 240, '01a205b3-d000-7806-a434-0a619d724fae', code, 'CV Raman Nagar', 'cv raman nagar', 'C V Raman Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'cv raman nagar' OR name_key = 'c v raman nagar' OR LOWER(aliases) LIKE '%cv raman nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560093', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 240;
-- New Thippasandra: India Post New Thippasandra S.O 560075
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 241, '01a205b3-d000-74b0-8abb-1acc3775aade', code, 'New Thippasandra', 'new thippasandra', 'Thippasandra', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'new thippasandra' OR name_key = 'thippasandra' OR LOWER(aliases) LIKE '%new thippasandra%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560075', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 241;
-- Jeevan Bima Nagar: India Post Jeevanbhimanagar S.O 560075
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 242, '01a205b3-d000-76a5-b1ba-3fe8fffc4a28', code, 'Jeevan Bima Nagar', 'jeevan bima nagar', 'Jeevanbhimanagar, JB Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'jeevan bima nagar' OR name_key = 'jeevanbhimanagar' OR name_key = 'jb nagar' OR LOWER(aliases) LIKE '%jeevan bima nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560075', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 242;
-- Vijayanagar: India Post Vijayanagar S.O (Bangalore) 560040
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 243, '01a205b3-d000-7210-ab16-6e1f1ec33754', code, 'Vijayanagar', 'vijayanagar', 'Vijaynagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'vijayanagar' OR name_key = 'vijaynagar' OR LOWER(aliases) LIKE '%vijayanagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560040', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 243;
-- Chandra Layout: India Post Chandra Lay Out S.O 560040
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 244, '01a205b3-d000-7fea-8d0c-8a05e6a318ca', code, 'Chandra Layout', 'chandra layout', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'chandra layout' OR LOWER(aliases) LIKE '%chandra layout%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560040', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 244;
-- Nagarbhavi: India Post Nagarbhavi S.O 560072
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 245, '01a205b3-d000-7eba-894e-f53f704bc8c3', code, 'Nagarbhavi', 'nagarbhavi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'nagarbhavi' OR LOWER(aliases) LIKE '%nagarbhavi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560072', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 245;
-- Basaveshwara Nagar: India Post Basaveshwaranagar S.O 560079
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 246, '01a205b3-d000-70af-8cad-fcf08fff6887', code, 'Basaveshwara Nagar', 'basaveshwara nagar', 'Basaveshwaranagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'basaveshwara nagar' OR name_key = 'basaveshwaranagar' OR LOWER(aliases) LIKE '%basaveshwara nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560079', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 246;
-- Kamakshipalya: India Post Kamakshipalya S.O 560079
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 247, '01a205b3-d000-720e-8015-06d3cf6412e2', code, 'Kamakshipalya', 'kamakshipalya', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'kamakshipalya' OR LOWER(aliases) LIKE '%kamakshipalya%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560079', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 247;
-- Mahalakshmi Layout: India Post Mahalakshmipuram Layout S.O 560086
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 248, '01a205b3-d000-756f-adda-96deac499a10', code, 'Mahalakshmi Layout', 'mahalakshmi layout', 'Mahalakshmipuram', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'mahalakshmi layout' OR name_key = 'mahalakshmipuram' OR LOWER(aliases) LIKE '%mahalakshmi layout%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560086', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 248;
-- Rajarajeshwari Nagar: India Post Rajarajeshwarinagar S.O 560098
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 249, '01a205b3-d000-78af-a272-334e206e274c', code, 'Rajarajeshwari Nagar', 'rajarajeshwari nagar', 'RR Nagar, Rajarajeshwarinagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'rajarajeshwari nagar' OR name_key = 'rr nagar' OR name_key = 'rajarajeshwarinagar' OR LOWER(aliases) LIKE '%rajarajeshwari nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560098', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 249;
-- Kengeri: India Post Kengeri S.O 560060
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 250, '01a205b3-d000-7d8c-8a9c-4f5f2e30223e', code, 'Kengeri', 'kengeri', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'kengeri' OR LOWER(aliases) LIKE '%kengeri%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560060', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 250;
-- Padmanabhanagar: India Post Padmanabhnagar S.O 560070
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 251, '01a205b3-d000-7470-ba0c-70896fbd3fce', code, 'Padmanabhanagar', 'padmanabhanagar', 'Padmanabha Nagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'padmanabhanagar' OR name_key = 'padmanabha nagar' OR LOWER(aliases) LIKE '%padmanabhanagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560070', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 251;
-- Girinagar: India Post Girinagar S.O (Bangalore) 560085
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 252, '01a205b3-d000-71a4-ad09-565f05907296', code, 'Girinagar', 'girinagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'girinagar' OR LOWER(aliases) LIKE '%girinagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560085', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 252;
-- Kumaraswamy Layout: India Post Kumaraswamy Layout S.O 560078
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 253, '01a205b3-d000-7380-97b2-40df89e083af', code, 'Kumaraswamy Layout', 'kumaraswamy layout', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'kumaraswamy layout' OR LOWER(aliases) LIKE '%kumaraswamy layout%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560078', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 253;
-- Bommanahalli: India Post Bommanahalli S.O (Bangalore) 560068
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 254, '01a205b3-d000-7c9c-965c-549f2f086910', code, 'Bommanahalli', 'bommanahalli', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'bommanahalli' OR LOWER(aliases) LIKE '%bommanahalli%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560068', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 254;
-- Begur: India Post Begur B.O 560068
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 255, '01a205b3-d000-7e13-890b-3a5cb70a3efa', code, 'Begur', 'begur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'begur' OR LOWER(aliases) LIKE '%begur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560068', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 255;
-- Madiwala: India Post Madivala S.O 560068
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 256, '01a205b3-d000-7a54-9162-a444fecfcefa', code, 'Madiwala', 'madiwala', 'Madivala', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'madiwala' OR name_key = 'madivala' OR LOWER(aliases) LIKE '%madiwala%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560068', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 256;
-- Hulimavu: India Post Hulimavu B.O 560076, Hulimavu S.O 560076
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 257, '01a205b3-d000-72be-9674-6c52d5f84223', code, 'Hulimavu', 'hulimavu', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'hulimavu' OR LOWER(aliases) LIKE '%hulimavu%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560076', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 257;
-- Bannerghatta Road: India Post Bannerghatta Road S.O 560076
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 258, '01a205b3-d000-7799-8204-d6e4d502ee32', code, 'Bannerghatta Road', 'bannerghatta road', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'bannerghatta road' OR LOWER(aliases) LIKE '%bannerghatta road%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560076', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 258;
-- Gottigere: India Post Gottigere S.O 560083
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 259, '01a205b3-d000-7fc7-b52a-fa913b96907b', code, 'Gottigere', 'gottigere', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'gottigere' OR LOWER(aliases) LIKE '%gottigere%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560083', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 259;
-- Varthur: India Post Vartur S.O 560087
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 260, '01a205b3-d000-766e-9d60-709415e5d1f0', code, 'Varthur', 'varthur', 'Vartur', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'varthur' OR name_key = 'vartur' OR LOWER(aliases) LIKE '%varthur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560087', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 260;
-- Gunjur: India Post Gunjur B.O 560087
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 261, '01a205b3-d000-76d6-b2ba-e6d16c14e2f4', code, 'Gunjur', 'gunjur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'gunjur' OR LOWER(aliases) LIKE '%gunjur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560087', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 261;
-- Kadugodi: India Post Kadugodi S.O 560067
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 262, '01a205b3-d000-77ce-922e-f0a111e45080', code, 'Kadugodi', 'kadugodi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'kadugodi' OR LOWER(aliases) LIKE '%kadugodi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560067', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 262;
-- Kundalahalli: India Post Kundalahalli S.O 560037
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 263, '01a205b3-d000-7251-bf31-0dab4c63b82c', code, 'Kundalahalli', 'kundalahalli', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'kundalahalli' OR LOWER(aliases) LIKE '%kundalahalli%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560037', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 263;
-- Doddanekkundi: India Post Doddanekkundi B.O 560037
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 264, '01a205b3-d000-7944-a1db-6224afaeccc2', code, 'Doddanekkundi', 'doddanekkundi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'doddanekkundi' OR LOWER(aliases) LIKE '%doddanekkundi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560037', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 264;
-- Panathur: India Post Panathur B.O 560103
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 265, '01a205b3-d000-73e7-9e09-3e3593817e91', code, 'Panathur', 'panathur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'panathur' OR LOWER(aliases) LIKE '%panathur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560103', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 265;
-- Kothanur: India Post Kothanur S.O 560077
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 266, '01a205b3-d000-76f9-8f94-28c81fb9529e', code, 'Kothanur', 'kothanur', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'kothanur' OR LOWER(aliases) LIKE '%kothanur%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560077', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 266;
-- Lingarajapuram: India Post Lingarajapuram S.O 560084
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 267, '01a205b3-d000-766f-97a8-e1aba8997b0d', code, 'Lingarajapuram', 'lingarajapuram', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'lingarajapuram' OR LOWER(aliases) LIKE '%lingarajapuram%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560084', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 267;
-- Jalahalli: India Post Jalahalli H.O 560013
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 268, '01a205b3-d000-7c2e-baa3-59d1267270a5', code, 'Jalahalli', 'jalahalli', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'jalahalli' OR LOWER(aliases) LIKE '%jalahalli%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560013', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 268;
-- Seshadripuram: India Post Seshadripuram S.O 560020
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 269, '01a205b3-d000-7f67-b710-89cfd75a4a85', code, 'Seshadripuram', 'seshadripuram', 'Sheshadripuram', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'seshadripuram' OR name_key = 'sheshadripuram' OR LOWER(aliases) LIKE '%seshadripuram%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560020', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 269;
-- Srirampuram: India Post Srirampuram S.O 560021
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 270, '01a205b3-d000-7f96-a397-73a56e76a6d3', code, 'Srirampuram', 'srirampuram', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'srirampuram' OR LOWER(aliases) LIKE '%srirampuram%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560021', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 270;
-- Chamarajpet: India Post Chamrajpet S.O (Bangalore) 560018
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 271, '01a205b3-d000-7720-ba92-13a010e2f62d', code, 'Chamarajpet', 'chamarajpet', 'Chamrajpet', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'chamarajpet' OR name_key = 'chamrajpet' OR LOWER(aliases) LIKE '%chamarajpet%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560018', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 271;
-- Vivek Nagar: India Post Viveknagar S.O (Bangalore) 560047
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 272, '01a205b3-d000-70c2-ad27-6086074c3b50', code, 'Vivek Nagar', 'vivek nagar', 'Viveknagar, Ejipura', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'vivek nagar' OR name_key = 'viveknagar' OR name_key = 'ejipura' OR LOWER(aliases) LIKE '%vivek nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560047', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 272;
-- Austin Town: India Post Austin Town S.O 560047
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 273, '01a205b3-d000-791b-af35-41fe2aba1571', code, 'Austin Town', 'austin town', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'austin town' OR LOWER(aliases) LIKE '%austin town%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560047', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 273;
-- Adugodi: India Post Adugodi S.O 560030
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 274, '01a205b3-d000-79ac-8060-cb399902cf3c', code, 'Adugodi', 'adugodi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'adugodi' OR LOWER(aliases) LIKE '%adugodi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560030', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 274;
-- Agara: India Post Agara B.O 560034
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 275, '01a205b3-d000-763b-8557-a1c637f9421b', code, 'Agara', 'agara', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'agara' OR LOWER(aliases) LIKE '%agara%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560034', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 275;
-- Subramanyapura: India Post Subramanyapura S.O 560061
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 276, '01a205b3-d000-727d-8e0f-0b0c978bc50c', code, 'Subramanyapura', 'subramanyapura', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'subramanyapura' OR LOWER(aliases) LIKE '%subramanyapura%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560061', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 276;
-- Konanakunte: India Post Konanakunte S.O 560062
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 277, '01a205b3-d000-7c1b-bd10-5b78b5e71e4e', code, 'Konanakunte', 'konanakunte', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'konanakunte' OR LOWER(aliases) LIKE '%konanakunte%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560062', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 277;
-- Anjanapura: India Post Anjanapura S.O 560108
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 278, '01a205b3-d000-791d-b7a2-ec77890b82bc', code, 'Anjanapura', 'anjanapura', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'anjanapura' OR LOWER(aliases) LIKE '%anjanapura%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560108', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 278;
-- Talaghattapura: India Post Thalaghattapura S.O 560109
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 279, '01a205b3-d000-773d-a591-b1940d3dd02f', code, 'Talaghattapura', 'talaghattapura', 'Thalaghattapura', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'talaghattapura' OR name_key = 'thalaghattapura' OR LOWER(aliases) LIKE '%talaghattapura%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560109', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 279;
-- Ullal: India Post Ullalu Upanagar S.O 560110
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 280, '01a205b3-d000-72ae-a923-80ff488f8a27', code, 'Ullal', 'ullal', 'Ullalu Upanagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'ullal' OR name_key = 'ullalu upanagar' OR LOWER(aliases) LIKE '%ullal%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560110', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 280;
-- Sampangi Rama Nagar: India Post Sampangiramnagar S.O 560027
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 281, '01a205b3-d000-7b1d-914b-f24be7658116', code, 'Sampangi Rama Nagar', 'sampangi rama nagar', 'Sampangiramnagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'sampangi rama nagar' OR name_key = 'sampangiramnagar' OR LOWER(aliases) LIKE '%sampangi rama nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560027', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 281;
-- Vyalikaval: India Post Vyalikaval Extn S.O 560003
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 282, '01a205b3-d000-7157-b7a8-e7cadc626de3', code, 'Vyalikaval', 'vyalikaval', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'BLR' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'BLR' AND (name_key = 'vyalikaval' OR LOWER(aliases) LIKE '%vyalikaval%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '560003', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 282;

-- PNQ
-- Baner: India Post Baner Gaon B.O 411045
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 301, '01a205b3-d000-7e58-a963-bed229d8e085', code, 'Baner', 'baner', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'baner' OR LOWER(aliases) LIKE '%baner%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411045', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 301;
-- Hinjewadi: India Post Infotech  Park (Hinjawadi) S.O 411057
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 302, '01a205b3-d000-73d3-a2fa-25b38efb1803', code, 'Hinjewadi', 'hinjewadi', 'Hinjawadi', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'hinjewadi' OR name_key = 'hinjawadi' OR LOWER(aliases) LIKE '%hinjewadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411057', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 302;
-- Kothrud: India Post Kothrud S.O 411038
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 303, '01a205b3-d000-7f85-95e3-3a4c5a47e69b', code, 'Kothrud', 'kothrud', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'kothrud' OR LOWER(aliases) LIKE '%kothrud%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411038', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 303;
-- Viman Nagar: India Post Viman nagar S.O 411014
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 304, '01a205b3-d000-78a5-8dc8-c3a8287c3de8', code, 'Viman Nagar', 'viman nagar', 'Vimannagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'viman nagar' OR name_key = 'vimannagar' OR LOWER(aliases) LIKE '%viman nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411014', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 304;
-- Aundh: India Post Aundh B.O 411007
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 305, '01a205b3-d000-7436-9773-77ea3d911888', code, 'Aundh', 'aundh', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'aundh' OR LOWER(aliases) LIKE '%aundh%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411007', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 305;
-- Balewadi: India Post Baner Gaon B.O 411045
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 306, '01a205b3-d000-7705-8118-3bbfa8727893', code, 'Balewadi', 'balewadi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'balewadi' OR LOWER(aliases) LIKE '%balewadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411045', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 306;
-- Wakad: India Post Wakad B.O 411057
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 307, '01a205b3-d000-7486-acd6-be59affe1225', code, 'Wakad', 'wakad', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'wakad' OR LOWER(aliases) LIKE '%wakad%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411057', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 307;
-- Pimple Saudagar: India Post Sangavi S.O 411027
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 308, '01a205b3-d000-7aaf-8138-8221e5fbe576', code, 'Pimple Saudagar', 'pimple saudagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'pimple saudagar' OR LOWER(aliases) LIKE '%pimple saudagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411027', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 308;
-- Pimple Nilakh: India Post Sangavi S.O 411027
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 309, '01a205b3-d000-77b9-8b14-cde997672d17', code, 'Pimple Nilakh', 'pimple nilakh', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'pimple nilakh' OR LOWER(aliases) LIKE '%pimple nilakh%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411027', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 309;
-- Sangvi: India Post Sangavi S.O 411027
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 310, '01a205b3-d000-7374-afd0-ff816f9ae781', code, 'Sangvi', 'sangvi', 'Sangavi', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'sangvi' OR name_key = 'sangavi' OR LOWER(aliases) LIKE '%sangvi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411027', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 310;
-- Pimple Gurav: India Post Pimple Gurav S.O 411061
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 311, '01a205b3-d000-7ee7-88c0-15b75abfbbde', code, 'Pimple Gurav', 'pimple gurav', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'pimple gurav' OR LOWER(aliases) LIKE '%pimple gurav%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411061', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 311;
-- Bavdhan: India Post Bavdhan B.O 411021
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 312, '01a205b3-d000-7628-a0dc-7d4080364c52', code, 'Bavdhan', 'bavdhan', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'bavdhan' OR LOWER(aliases) LIKE '%bavdhan%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411021', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 312;
-- Sus: India Post Sus B.O 411021
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 313, '01a205b3-d000-7595-97ef-1f083e1ad1c4', code, 'Sus', 'sus', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'sus' OR LOWER(aliases) LIKE '%sus%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411021', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 313;
-- Warje: India Post Warje S.O 411058
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 314, '01a205b3-d000-7685-bbdf-2e98a9607bb1', code, 'Warje', 'warje', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'warje' OR LOWER(aliases) LIKE '%warje%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411058', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 314;
-- Karve Nagar: India Post Karvenagar S.O 411052
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 315, '01a205b3-d000-7fc1-a949-2997cf5f049a', code, 'Karve Nagar', 'karve nagar', 'Karvenagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'karve nagar' OR name_key = 'karvenagar' OR LOWER(aliases) LIKE '%karve nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411052', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 315;
-- Erandwane: India Post Deccan Gymkhana S.O 411004
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 316, '01a205b3-d000-7e6d-aab2-2e5c55614a06', code, 'Erandwane', 'erandwane', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'erandwane' OR LOWER(aliases) LIKE '%erandwane%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411004', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 316;
-- Deccan Gymkhana: India Post Deccan Gymkhana S.O 411004
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 317, '01a205b3-d000-7fa2-9353-c1a08f92041d', code, 'Deccan Gymkhana', 'deccan gymkhana', 'Deccan', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'deccan gymkhana' OR name_key = 'deccan' OR LOWER(aliases) LIKE '%deccan gymkhana%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411004', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 317;
-- Shivajinagar: India Post Shivajinagar S.O (Pune) 411005
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 318, '01a205b3-d000-7166-be77-5aa7ef59c3a5', code, 'Shivajinagar', 'shivajinagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'shivajinagar' OR LOWER(aliases) LIKE '%shivajinagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411005', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 318;
-- Model Colony: India Post Model Colony S.O 411016
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 319, '01a205b3-d000-761d-bef6-712b77bdd8cd', code, 'Model Colony', 'model colony', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'model colony' OR LOWER(aliases) LIKE '%model colony%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411016', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 319;
-- Gokhale Nagar: India Post Gokhalenagar B.O 411016
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 320, '01a205b3-d000-7ab8-a3ba-67986286af07', code, 'Gokhale Nagar', 'gokhale nagar', 'Gokhalenagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'gokhale nagar' OR name_key = 'gokhalenagar' OR LOWER(aliases) LIKE '%gokhale nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411016', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 320;
-- Koregaon Park: India Post Pune H.O 411001
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 321, '01a205b3-d000-70aa-a9aa-71f6342e1377', code, 'Koregaon Park', 'koregaon park', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'koregaon park' OR LOWER(aliases) LIKE '%koregaon park%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411001', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 321;
-- Camp: India Post Pune Cantt East S.O 411001
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 322, '01a205b3-d000-744d-b411-b8dff9112560', code, 'Camp', 'camp', 'Pune Camp, Pune Cantonment', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'camp' OR name_key = 'pune camp' OR name_key = 'pune cantonment' OR LOWER(aliases) LIKE '%camp%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411001', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 322;
-- Kalyani Nagar: India Post Yerwada S.O 411006
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 323, '01a205b3-d000-73f0-9068-3f664e0a4d56', code, 'Kalyani Nagar', 'kalyani nagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'kalyani nagar' OR LOWER(aliases) LIKE '%kalyani nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411006', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 323;
-- Yerawada: India Post Yerwada S.O 411006
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 324, '01a205b3-d000-71b8-a3c0-56cf3b0a6a1b', code, 'Yerawada', 'yerawada', 'Yerwada', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'yerawada' OR name_key = 'yerwada' OR LOWER(aliases) LIKE '%yerawada%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411006', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 324;
-- Kharadi: India Post Vadgaon Sheri S.O 411014
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 325, '01a205b3-d000-77f7-8efc-3edd77c7b8b0', code, 'Kharadi', 'kharadi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'kharadi' OR LOWER(aliases) LIKE '%kharadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411014', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 325;
-- Vadgaon Sheri: India Post Vadgaon Sheri S.O 411014
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 326, '01a205b3-d000-70e4-af1d-a563a8d43ed1', code, 'Vadgaon Sheri', 'vadgaon sheri', 'Wadgaon Sheri', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'vadgaon sheri' OR name_key = 'wadgaon sheri' OR LOWER(aliases) LIKE '%vadgaon sheri%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411014', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 326;
-- Wagholi: India Post Vagholi S.O 412207
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 327, '01a205b3-d000-76e3-bee2-6040a433a893', code, 'Wagholi', 'wagholi', 'Vagholi', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'wagholi' OR name_key = 'vagholi' OR LOWER(aliases) LIKE '%wagholi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '412207', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 327;
-- Lohegaon: India Post Lohogaon S.O 411047
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 328, '01a205b3-d000-7730-8cf8-873cd8b06798', code, 'Lohegaon', 'lohegaon', 'Lohogaon', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'lohegaon' OR name_key = 'lohogaon' OR LOWER(aliases) LIKE '%lohegaon%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411047', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 328;
-- Dhanori: India Post Dhanori B.O 411015
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 329, '01a205b3-d000-7080-9921-cb328dd5b696', code, 'Dhanori', 'dhanori', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'dhanori' OR LOWER(aliases) LIKE '%dhanori%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411015', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 329;
-- Vishrantwadi: India Post Vishrantwadi S.O 411015
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 330, '01a205b3-d000-7683-9be5-6eb666417bc2', code, 'Vishrantwadi', 'vishrantwadi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'vishrantwadi' OR LOWER(aliases) LIKE '%vishrantwadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411015', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 330;
-- Mundhwa: India Post Mundhva B.O 411036
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 331, '01a205b3-d000-7cf6-8903-0e65d3bb9091', code, 'Mundhwa', 'mundhwa', 'Mundhva', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'mundhwa' OR name_key = 'mundhva' OR LOWER(aliases) LIKE '%mundhwa%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411036', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 331;
-- Magarpatta: India Post Hadpsar I.E. S.O 411013
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 332, '01a205b3-d000-7b0d-9a85-75d07fe19ba7', code, 'Magarpatta', 'magarpatta', 'Magarpatta City', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'magarpatta' OR name_key = 'magarpatta city' OR LOWER(aliases) LIKE '%magarpatta%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411013', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 332;
-- Hadapsar: India Post Hadapsar S.O 411028
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 333, '01a205b3-d000-7e91-aa1d-d8e4f0f13531', code, 'Hadapsar', 'hadapsar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'hadapsar' OR LOWER(aliases) LIKE '%hadapsar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411028', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 333;
-- Wanowrie: India Post Wanowarie S.O 411040
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 334, '01a205b3-d000-77fb-b814-2fdb42643269', code, 'Wanowrie', 'wanowrie', 'Wanowarie', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'wanowrie' OR name_key = 'wanowarie' OR LOWER(aliases) LIKE '%wanowrie%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411040', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 334;
-- Undri: India Post Mohamadwadi S.O 411060
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 335, '01a205b3-d000-7fc5-a5cd-31d0a7021395', code, 'Undri', 'undri', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'undri' OR LOWER(aliases) LIKE '%undri%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411060', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 335;
-- Mohammadwadi: India Post Mohamadwadi S.O 411060
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 336, '01a205b3-d000-7019-b394-1680b2f7cb63', code, 'Mohammadwadi', 'mohammadwadi', 'Mohamadwadi', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'mohammadwadi' OR name_key = 'mohamadwadi' OR LOWER(aliases) LIKE '%mohammadwadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411060', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 336;
-- NIBM Road: India Post N I B M S.O 411048
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 337, '01a205b3-d000-75ad-96ca-77a4e6be2198', code, 'NIBM Road', 'nibm road', 'NIBM', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'nibm road' OR name_key = 'nibm' OR LOWER(aliases) LIKE '%nibm road%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411048', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 337;
-- Kondhwa: India Post Kondhwa BK B.O 411048, Khondhwa KH B.O 411048
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 338, '01a205b3-d000-7075-8647-30a123128dd6', code, 'Kondhwa', 'kondhwa', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'kondhwa' OR LOWER(aliases) LIKE '%kondhwa%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411048', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 338;
-- Katraj: India Post Katraj S.O 411046
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 339, '01a205b3-d000-75ac-b7fb-7b1787b9d7a1', code, 'Katraj', 'katraj', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'katraj' OR LOWER(aliases) LIKE '%katraj%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411046', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 339;
-- Ambegaon Budruk: India Post Ambegaon BK B.O 411046
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 340, '01a205b3-d000-75d9-9c43-9a0980acf38a', code, 'Ambegaon Budruk', 'ambegaon budruk', 'Ambegaon', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'ambegaon budruk' OR name_key = 'ambegaon' OR LOWER(aliases) LIKE '%ambegaon budruk%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411046', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 340;
-- Dhankawadi: India Post Dhankawadi S.O 411043
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 341, '01a205b3-d000-7ee5-99c6-72dfccb6bbbc', code, 'Dhankawadi', 'dhankawadi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'dhankawadi' OR LOWER(aliases) LIKE '%dhankawadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411043', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 341;
-- Bibwewadi: India Post Bibvewadi S.O 411037
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 342, '01a205b3-d000-73c5-b1bf-511bbdb19e8e', code, 'Bibwewadi', 'bibwewadi', 'Bibvewadi', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'bibwewadi' OR name_key = 'bibvewadi' OR LOWER(aliases) LIKE '%bibwewadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411037', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 342;
-- Market Yard: India Post Market Yard S.O (Pune) 411037
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 343, '01a205b3-d000-7d2f-a7d3-013f7998dfb2', code, 'Market Yard', 'market yard', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'market yard' OR LOWER(aliases) LIKE '%market yard%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411037', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 343;
-- Salisbury Park: India Post Salisbury Park S.O 411037
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 344, '01a205b3-d000-73e2-bd15-17351b5b16df', code, 'Salisbury Park', 'salisbury park', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'salisbury park' OR LOWER(aliases) LIKE '%salisbury park%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411037', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 344;
-- Swargate: India Post Swargate S.O 411042
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 345, '01a205b3-d000-75a5-afd8-f7e0cbeff68f', code, 'Swargate', 'swargate', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'swargate' OR LOWER(aliases) LIKE '%swargate%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411042', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 345;
-- Parvati: India Post Parvati S.O 411009
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 346, '01a205b3-d000-7cc3-8e52-54574f087b28', code, 'Parvati', 'parvati', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'parvati' OR LOWER(aliases) LIKE '%parvati%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411009', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 346;
-- Sadashiv Peth: India Post Sadashiv Peth S.O 411030
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 347, '01a205b3-d000-756d-82d4-e0f15a93ebb4', code, 'Sadashiv Peth', 'sadashiv peth', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'sadashiv peth' OR LOWER(aliases) LIKE '%sadashiv peth%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411030', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 347;
-- Narayan Peth: India Post Narayan Peth S.O 411030
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 348, '01a205b3-d000-7558-9003-7cb595991551', code, 'Narayan Peth', 'narayan peth', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'narayan peth' OR LOWER(aliases) LIKE '%narayan peth%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411030', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 348;
-- Shaniwar Peth: India Post Shaniwar Peth S.O (Pune) 411030
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 349, '01a205b3-d000-79e7-84bd-07c80e734741', code, 'Shaniwar Peth', 'shaniwar peth', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'shaniwar peth' OR LOWER(aliases) LIKE '%shaniwar peth%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411030', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 349;
-- Kasba Peth: India Post Kasba Peth S.O 411011
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 350, '01a205b3-d000-7def-bf44-a74f0a3746cc', code, 'Kasba Peth', 'kasba peth', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'kasba peth' OR LOWER(aliases) LIKE '%kasba peth%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411011', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 350;
-- Rasta Peth: India Post Rasta Peth S.O 411011
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 351, '01a205b3-d000-7a88-9df8-d147051791c7', code, 'Rasta Peth', 'rasta peth', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'rasta peth' OR LOWER(aliases) LIKE '%rasta peth%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411011', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 351;
-- Shukrawar Peth: India Post Shukrawar Peth S.O (Pune) 411002
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 352, '01a205b3-d000-727f-bc44-d0b630039e7f', code, 'Shukrawar Peth', 'shukrawar peth', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'shukrawar peth' OR LOWER(aliases) LIKE '%shukrawar peth%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411002', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 352;
-- Nana Peth: India Post Nana Peth S.O 411002
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 353, '01a205b3-d000-75ab-bf00-965a56f13e21', code, 'Nana Peth', 'nana peth', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'nana peth' OR LOWER(aliases) LIKE '%nana peth%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411002', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 353;
-- Lokmanya Nagar: India Post Lokmanyanagar S.O 411030
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 354, '01a205b3-d000-7004-8650-5db51e6cec14', code, 'Lokmanya Nagar', 'lokmanya nagar', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'lokmanya nagar' OR LOWER(aliases) LIKE '%lokmanya nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411030', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 354;
-- Khadki: India Post Khadki S.O 411003
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 355, '01a205b3-d000-751b-82b7-f210bb6ff51b', code, 'Khadki', 'khadki', 'Kirkee', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'khadki' OR name_key = 'kirkee' OR LOWER(aliases) LIKE '%khadki%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411003', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 355;
-- Range Hills: India Post Range Hills S.O 411020
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 356, '01a205b3-d000-7fb5-853d-a23272830509', code, 'Range Hills', 'range hills', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'range hills' OR LOWER(aliases) LIKE '%range hills%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411020', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 356;
-- Dapodi: India Post Dapodi S.O 411012
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 357, '01a205b3-d000-7823-b0ba-32d3e64a275b', code, 'Dapodi', 'dapodi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'dapodi' OR LOWER(aliases) LIKE '%dapodi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411012', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 357;
-- Kasarwadi: India Post Kasarwadi S.O 411034
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 358, '01a205b3-d000-7c25-b800-e98fafc38597', code, 'Kasarwadi', 'kasarwadi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'kasarwadi' OR LOWER(aliases) LIKE '%kasarwadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411034', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 358;
-- Pimpri: India Post Pimpri Colony S.O 411017, Pimpri P F S.O 411018
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 359, '01a205b3-d000-746e-b266-c4db7917985f', code, 'Pimpri', 'pimpri', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'pimpri' OR LOWER(aliases) LIKE '%pimpri%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411017', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 359;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411018', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 359;
-- Chinchwad: India Post Chinchwad East S.O 411019, Chinchwadgaon S.O 411033
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 360, '01a205b3-d000-79dc-9f67-74fe06bb914c', code, 'Chinchwad', 'chinchwad', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'chinchwad' OR LOWER(aliases) LIKE '%chinchwad%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411019', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 360;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411033', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 360;
-- Akurdi: India Post Akurdi S.O 411035
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 361, '01a205b3-d000-7053-8a3c-d19ca786e40e', code, 'Akurdi', 'akurdi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'akurdi' OR LOWER(aliases) LIKE '%akurdi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411035', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 361;
-- Nigdi: India Post Yamunanagar S.O 411044, P.C.N.T. S.O 411044
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 362, '01a205b3-d000-7e05-a31c-1a1ff6b9ec46', code, 'Nigdi', 'nigdi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'nigdi' OR LOWER(aliases) LIKE '%nigdi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411044', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 362;
-- Bhosari: India Post Bhosarigoan S.O 411039, Bhosari I.E. S.O 411026
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 363, '01a205b3-d000-7542-b4d1-43b40c8620c1', code, 'Bhosari', 'bhosari', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'bhosari' OR LOWER(aliases) LIKE '%bhosari%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411026', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 363;
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411039', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 363;
-- Moshi: India Post Moshi B.O 412105
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 364, '01a205b3-d000-7dfe-9bf9-e6190b87b716', code, 'Moshi', 'moshi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'moshi' OR LOWER(aliases) LIKE '%moshi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '412105', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 364;
-- Chikhali: India Post Chikhali S.O 411062
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 365, '01a205b3-d000-78bf-a819-547ceaff5cc1', code, 'Chikhali', 'chikhali', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'chikhali' OR LOWER(aliases) LIKE '%chikhali%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411062', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 365;
-- Ravet: India Post Ravet B.O 412101
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 366, '01a205b3-d000-76b0-b11c-fee7c425a820', code, 'Ravet', 'ravet', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'ravet' OR LOWER(aliases) LIKE '%ravet%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '412101', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 366;
-- Punawale: India Post Punawale B.O 411033
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 367, '01a205b3-d000-771d-846e-00073b70ff3a', code, 'Punawale', 'punawale', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'punawale' OR LOWER(aliases) LIKE '%punawale%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411033', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 367;
-- Tathawade: India Post Thathawade B.O 411033
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 368, '01a205b3-d000-7abb-b51c-e6614a52be89', code, 'Tathawade', 'tathawade', 'Thathawade', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'tathawade' OR name_key = 'thathawade' OR LOWER(aliases) LIKE '%tathawade%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411033', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 368;
-- Thergaon: India Post Thergaon B.O 411033
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 369, '01a205b3-d000-7d58-95a4-123031edc03f', code, 'Thergaon', 'thergaon', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'thergaon' OR LOWER(aliases) LIKE '%thergaon%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411033', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 369;
-- Kalewadi: India Post Kalewadi B.O 411017
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 370, '01a205b3-d000-701c-acf9-ad7c096f7453', code, 'Kalewadi', 'kalewadi', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'kalewadi' OR LOWER(aliases) LIKE '%kalewadi%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411017', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 370;
-- Kiwale: India Post Kiwale B.O 412101
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 371, '01a205b3-d000-7696-a163-f2f30f436813', code, 'Kiwale', 'kiwale', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'kiwale' OR LOWER(aliases) LIKE '%kiwale%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '412101', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 371;
-- Dehu Road: India Post Dehu Road Cantt S.O 412101
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 372, '01a205b3-d000-7070-a1fa-067e3f7e8b6c', code, 'Dehu Road', 'dehu road', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'dehu road' OR LOWER(aliases) LIKE '%dehu road%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '412101', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 372;
-- Talegaon Dabhade: India Post Talegaon Dabhade S.O 410506
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 373, '01a205b3-d000-7944-984c-874318f1f656', code, 'Talegaon Dabhade', 'talegaon dabhade', 'Talegaon', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'talegaon dabhade' OR name_key = 'talegaon' OR LOWER(aliases) LIKE '%talegaon dabhade%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '410506', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 373;
-- Chakan: India Post Chakan S.O 410501
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 374, '01a205b3-d000-7b94-825e-d629e1588697', code, 'Chakan', 'chakan', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'chakan' OR LOWER(aliases) LIKE '%chakan%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '410501', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 374;
-- Dhayari: India Post Dhayari B.O 411041
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 375, '01a205b3-d000-74c8-8238-57c90f55eb52', code, 'Dhayari', 'dhayari', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'dhayari' OR LOWER(aliases) LIKE '%dhayari%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411041', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 375;
-- Nanded: India Post Nanded B.O 411041
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 376, '01a205b3-d000-7a3e-b0b8-ad5adf2842c8', code, 'Nanded', 'nanded', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'nanded' OR LOWER(aliases) LIKE '%nanded%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411041', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 376;
-- Vadgaon Budruk: India Post Vadgaon Budruk S.O 411041
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 377, '01a205b3-d000-704e-89da-1262c2498091', code, 'Vadgaon Budruk', 'vadgaon budruk', 'Wadgaon Budruk', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'vadgaon budruk' OR name_key = 'wadgaon budruk' OR LOWER(aliases) LIKE '%vadgaon budruk%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411041', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 377;
-- Khadakwasla: India Post Khadakwasla R.S. S.O 411024
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 378, '01a205b3-d000-7aca-8529-e42a0446b634', code, 'Khadakwasla', 'khadakwasla', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'khadakwasla' OR LOWER(aliases) LIKE '%khadakwasla%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411024', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 378;
-- Uttam Nagar: India Post Uttamnagar B.O 411023
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 379, '01a205b3-d000-7951-b99f-ba39c8b94110', code, 'Uttam Nagar', 'uttam nagar', 'Uttamnagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'uttam nagar' OR name_key = 'uttamnagar' OR LOWER(aliases) LIKE '%uttam nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411023', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 379;
-- Shivane: India Post Shivane B.O 411023
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 380, '01a205b3-d000-7ed3-b073-489a5c88f71f', code, 'Shivane', 'shivane', NULL, 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'shivane' OR LOWER(aliases) LIKE '%shivane%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411023', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 380;
-- Sasane Nagar: India Post Sasanenagar S.O 411028
INSERT INTO areas (id, public_id, city_code, name, name_key, aliases, status, created_by, is_demo, created_at, updated_at)
SELECT 381, '01a205b3-d000-7df9-98b8-2f4acf9c391a', code, 'Sasane Nagar', 'sasane nagar', 'Sasanenagar', 'ACTIVE', NULL, FALSE, '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM cities
WHERE code = 'PNQ' AND NOT EXISTS (SELECT 1 FROM areas WHERE city_code = 'PNQ' AND (name_key = 'sasane nagar' OR name_key = 'sasanenagar' OR LOWER(aliases) LIKE '%sasane nagar%'));
INSERT INTO area_pincodes (area_id, pincode, created_at, updated_at) SELECT id, '411028', '2026-11-24 00:00:00.000', '2026-11-24 00:00:00.000' FROM areas WHERE id = 381;
