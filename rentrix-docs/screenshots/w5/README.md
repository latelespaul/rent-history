# W5 screenshots: Landlord properties and tenancies

Taken with a headless Chromium against the local dev stack with the demo seed (4 Bangalore +
4 Pune fictional homes, tenancies and rent records from B5). The flow was clicked through end to
end: add a property with the location pickers, invite a tenant, confirm a pending request,
propose a rent change as the landlord, confirm it as the tenant, and see it in the public rent
history.

| File | Page | Signed in as |
|---|---|---|
| `landlord-dashboard-desktop.png` | `/me/dashboard` (also `/me/properties`): counts, a tenancy request waiting for the landlord, every listing with Edit / Invite a tenant / Archive | Demo Landlord PNQ-1 |
| `property-form-pickers-desktop.png` | `/me/properties/new`: city, PIN code, area → society → tower → wing pickers with a proposed wing ("Pending review"); photo upload placeholder (B9) | Demo Landlord PNQ-1 |
| `tenancy-request-desktop.png` | `/tenancies/new`: invite a tenant by email | Demo Landlord PNQ-1 |
| `tenancy-confirm-desktop.png` | `/tenancies/:id`: a tenant's request with Confirm / Decline | Demo Landlord PNQ-1 |
| `my-tenancies-landlord-desktop.png` | `/me/tenancies`: needs-action first, then tabs by state | Demo Landlord PNQ-1 |
| `rent-records-desktop.png` | `/tenancies/:id`: rent records and "Add a rent change" (a new one waits for confirmation) | Demo Landlord PNQ-1 |
| `my-tenancies-tenant-desktop.png` | `/me/tenancies` for a tenant | Demo Tenant PNQ-1 |
| `rent-records-tenant-confirm-desktop.png` | `/tenancies/:id`: the tenant confirms or disputes the landlord's rent change | Demo Tenant PNQ-1 |
| `tenant-rent-history-desktop.png` | `/p/:id` rent history after both sides confirmed: months and rent only, no names | Demo Tenant PNQ-1 |

## Run it locally

```sh
# API (Java 21): SQLite file, demo seed, dev login, port 8080
cd rentrix-server
SPRING_PROFILES_ACTIVE=dev SEED_DEMO=true DEV_LOGIN_ENABLED=true ./mvnw spring-boot:run

# Web (Node 20+), in a second terminal: Vite on port 5173 proxies /api to localhost:8080
cd rentrix-web
npm ci
npm run dev
```

Open http://localhost:5173/login and pick a seed user in the dev-login panel:

- **Demo Landlord PNQ-1** → Dashboard (`/me/dashboard`): confirm the waiting request on Demo
  Towers E, "Add property" (`/me/properties/new`), "Invite a tenant" (`/tenancies/new`), open a
  tenancy and add a rent change.
- **Demo Tenant PNQ-1** → My tenancies (`/me/tenancies`): open Demo Towers E and confirm the
  rent change; the home page `/p/:id` then shows it in the rent history. On any other published
  home, "I rent or rented this home" sends a tenancy request to its landlord.

Demo users pass the approval gate. For a user who is not approved, confirming a tenancy or adding
rent shows "Your … account is pending approval" with a link to the verification status
(`403 approval_required`); declining and cancelling are never gated.
