# Setup

The old bootstrap guide that lived here is out of date (it listed lucide-react, axios and a Spring/H2/Render matrix the app no longer uses). Setup now lives with each tier:

- Web: [rentrix-web/README.md](../../rentrix-web/README.md) (run locally, scripts, design system, API base URL).
- API: [rentrix-server/README.md](../../rentrix-server/README.md).

## Spring Boot 4 / Jackson 3 notes

- Jackson 3 moved from `com.fasterxml.jackson.databind` → `tools.jackson.databind`
- Annotations (`@JsonProperty`, `@JsonIgnore`) are UNCHANGED — still `com.fasterxml.jackson.annotation.*`
- `JavaTimeModule` is no longer needed as a separate module — Java Time support is built into databind
- JJWT 0.12.x still uses Jackson 2 internally — both libraries coexist, no action needed
