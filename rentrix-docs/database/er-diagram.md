# Rentrix — ER diagrams (design v3)

Status: **current**. Date: 2026-11-22.
Decisions: [`../architecture/SYSTEM_DESIGN.md`](../architecture/SYSTEM_DESIGN.md) §5 and §7.4. Column lists, keys and indexes: [`schema.md`](schema.md).
These diagrams show the design-v3 parts (location hierarchy, owners, roll-ups, identity) and the V1 tables they touch. GitHub renders the Mermaid blocks.

## 1. Location hierarchy

```
City            cities              BLR Bangalore
 └─ Area        areas (+ PIN codes) Koramangala · 560034, 560095
     ├─ Society       societies         Demo Greens (gated community)
     │   └─ Tower/Block   society_towers    Tower 1
     │       ├─ Wing          society_wings     Wing A
     │       │   └─ Unit/Flat     properties        Demo Residency A · unit D-101 (private)
     │       └─ (no wing)     properties        a unit directly in a tower without wings
     └─ Standalone    properties        house, villa or building outside any society
```

```mermaid
erDiagram
    cities ||--o{ areas : "has"
    areas ||--|{ area_pincodes : "covers"
    areas ||--o{ societies : "has"
    societies ||--o{ society_towers : "has"
    society_towers ||--o{ society_wings : "has"
    areas ||--o{ properties : "area_id (required)"
    societies |o--o{ properties : "society_id (optional)"
    society_towers |o--o{ properties : "tower_id (optional)"
    society_wings |o--o{ properties : "wing_id (optional)"
    users ||--o{ properties : "landlord_id: one owner, many properties"

    cities {
        varchar code PK
    }
    areas {
        bigint id PK
        varchar city_code FK
        varchar name_key
        varchar status
    }
    area_pincodes {
        bigint area_id PK
        char pincode PK
    }
    societies {
        bigint id PK
        bigint area_id FK
        varchar city_code
        varchar name_key
        varchar kind
        varchar status
    }
    society_towers {
        bigint id PK
        bigint society_id FK
        varchar name_key
        varchar kind
    }
    society_wings {
        bigint id PK
        bigint tower_id FK
        varchar name_key
    }
    properties {
        bigint id PK
        bigint landlord_id FK
        bigint area_id FK
        bigint society_id FK
        bigint tower_id FK
        bigint wing_id FK
        varchar unit_label "private"
    }
```

Rules: `wing ⇒ tower ⇒ society ⇒ area ⇒ city`, checked by the API (D-110). Standalone properties have `society_id`, `tower_id`, `wing_id` null.

## 2. Ratings and roll-ups

```mermaid
erDiagram
    tenancies ||--o{ reviews : "0..3 per tenancy"
    properties ||--o{ reviews : "TENANT_TO_PROPERTY"
    reviews }o--|| rating_aggregates : "PROPERTY"
    reviews }o--|| rating_aggregates : "SOCIETY (via property.society_id)"
    reviews }o--|| rating_aggregates : "AREA (via property.area_id)"
    reviews }o--|| rating_aggregates : "OWNER_HOMES (via property.landlord_id)"
    rating_aggregates {
        varchar subject_type PK
        bigint subject_id PK
        int review_count
        numeric average
        numeric bayes_score
    }
```

```
 one counted TENANT_TO_PROPERTY review on Demo Residency A
        │ same transaction (D-33, D-111)
        ├─▶ PROPERTY     Demo Residency A
        ├─▶ SOCIETY      Demo Greens
        ├─▶ AREA         Koramangala
        └─▶ OWNER_HOMES  Demo Landlord BLR-1      (LANDLORD person rating stays separate, D-18)
```

## 3. Identity verification

```mermaid
erDiagram
    users ||--o{ identity_verifications : "one row per method"
    users ||--o{ phone_otp_challenges : "OTP sends"
    users ||--o{ verification_applications : "one per role"
    identity_verifications ||--o{ verification_documents : "ID_PROOF file (PAN, masked Aadhaar)"
    verification_applications ||--o{ rental_agreements : "onboarding agreement (design v4, D-123)"

    identity_verifications {
        bigint id PK
        bigint user_id FK
        varchar method "AADHAAR PAN PHONE_OTP LINKEDIN TRUECALLER"
        varchar status
        varchar last4
        char value_hmac "PAN, phone only"
    }
    phone_otp_challenges {
        bigint id PK
        bigint user_id FK
        char code_hmac
        timestamp expires_at
        int attempts
    }
    verification_applications {
        bigint id PK
        bigint user_id FK
        varchar role
        varchar status
    }
    verification_documents {
        bigint id PK
        bigint identity_verification_id FK
        varchar kind
        varchar id_type
    }
```

"ID verified" badge = any `AADHAAR`, `PAN` or `PHONE_OTP` row `VERIFIED` (D-99). "LinkedIn verified" badge = the `LINKEDIN` row `VERIFIED` (D-100). Trust writes need an `APPROVED` application for the role and an ID-verified user (D-91, D-105).
