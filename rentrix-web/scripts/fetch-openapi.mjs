// Refreshes openapi/openapi.json from a running dev/test API (springdoc is off in prod, D-64).
//   SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   (in rentrix-server)
//   npm run gen:api:fetch                               (here; OPENAPI_URL overrides the source)
import { mkdir, writeFile } from "node:fs/promises"

const url = process.env.OPENAPI_URL ?? "http://localhost:8080/api/v1/v3/api-docs"
const response = await fetch(url)
if (!response.ok) {
  console.error(`GET ${url} -> ${response.status}. Is the API running with the dev profile?`)
  process.exit(1)
}
const doc = await response.json()
// The generated server URL is the local host:port; keep the snapshot independent of it.
doc.servers = [{ url: "/api/v1" }]
await mkdir("openapi", { recursive: true })
await writeFile("openapi/openapi.json", `${JSON.stringify(doc, null, 2)}\n`)
console.log(`wrote openapi/openapi.json from ${url}`)
