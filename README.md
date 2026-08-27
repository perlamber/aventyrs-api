# Aventyrs API

Light API exposing the [Aventyrs](https://github.com/perlamber/aventyrs-core) tabletop RPG rules
engine over HTTP and WebSocket. It provides CRUD persistence for `Player`, `CharacterSheet`,
`MonsterSheet`, and `Scene`, plus real-time gameplay events (token movement, turn order, combat
status, grid resize) broadcast over STOMP. There is currently **no authentication** — `PlayerRole`
(`PLAYER`/`GM`) is a client-side hint only, not an enforced permission.

## 1. Tech stack

- Java 21, Spring Boot 4.1 (Web MVC, WebSocket/STOMP, Validation)
- MongoDB via Spring Data, schema/index changes managed with Liquibase (`liquibase-mongodb`
  extension)
- SeaweedFS (S3 gateway) for image storage
- [`aventyrs-core`](../aventyrs-core) — the framework-free rules engine library, consumed as a
  Maven dependency
- springdoc-openapi — OpenAPI 3 spec + Swagger UI, generated from the controllers/DTOs
- Testcontainers (`MongoDBContainer`) for integration tests
- Gradle

## 2. Prerequisites

- JDK 21
- Docker — runs local MongoDB/SeaweedFS for `bootRun` and backs every integration test (every
  test class boots the full Spring context against a Testcontainers-managed MongoDB)
- The [`aventyrs-core`](../aventyrs-core) sibling repository, checked out alongside this one and
  **published to your local Maven repository**:

  ```bash
  cd ../aventyrs-core
  ./gradlew publishToMavenLocal
  ```

  Check `build.gradle` here for the exact version this project depends on.

## 3. Running locally

```bash
docker compose up -d   # Mongo (27017) + SeaweedFS (master/volume/filer/S3 gateway)
./gradlew bootRun       # app listens on :27018
```

On startup, `MongoLiquibaseRunner` applies every changelog under
`src/main/resources/db/changelog/` directly against MongoDB — Spring Boot's built-in Liquibase
autoconfiguration only drives a JDBC `DataSource`, so this project runs Liquibase's Mongo
extension programmatically instead.

## 4. Configuration

Key properties in `src/main/resources/application.properties`:

- `server.port` — `27018`
- `spring.mongodb.uri` — defaults to `mongodb://localhost:27017/aventyrs`
- `seaweedfs.filer-url` / `seaweedfs.s3.*` — SeaweedFS endpoints; S3 credentials fall back to
  `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`S3_BUCKET` env vars

## 5. Testing

```bash
./gradlew test
```

Every test spins up a real MongoDB via Testcontainers (`@ServiceConnection` + `MongoDBContainer`),
so **Docker must be running**. Pure-logic tests (grid/range math) live in `aventyrs-core` instead,
since that module has no Spring/Mongo dependency to spin up.

## 6. API documentation

With the app running:

- Interactive Swagger UI: `http://localhost:27018/swagger-ui/index.html`
- Raw OpenAPI 3 spec (JSON): `http://localhost:27018/v3/api-docs`

Docs are generated from the controllers and DTOs (including Bean Validation constraints), so they
stay in sync with the code.

## 7. REST endpoints

All CRUD resources follow the same shape: `POST` (create), `GET /{id}`, `GET` (list),
`PUT /{id}` (full replace), `DELETE /{id}`.

| Resource | Base path | Notes |
|---|---|---|
| Player | `/api/players` | `login` is unique (`409` on conflict); also `GET /by-login/{login}`. `role` (`PLAYER`/`GM`) is a UI hint only |
| CharacterSheet | `/api/character-sheets` | Embeds its `character` (name, race, sexo, tendencia); references a `playerId`. New sheets start with zeroed stats, mirroring `CharacterSheet.of(...)` in core. Supports `?playerId=` filtering |
| MonsterSheet | `/api/monster-sheets` | GM-authored stat blocks, same pattern as CharacterSheet. Supports `?playerId=` filtering |
| Scene | `/api/scenes` | Participants reference a `characterSheetId`, an initiative value, an ally `group` (`UUID`), and a grid position, unique within the Scene's `width`×`height` grid (each ≤ 100, set at creation, later changed only via the live grid-resize event). Also exposes `GET /{id}/groups` |
| Image | `POST /api/images` | Multipart upload, stored in SeaweedFS and returned as a public URL (`201`). Only PNG/JPEG/GIF/BMP are accepted, verified from the file's own bytes, not the client-supplied Content-Type |
| Skill | `GET /api/skills` | Lists all `SkillType` values from core |

Validation/reference errors return `400`, missing resources `404`, unique-constraint violations
`409` — see `org.aventyrs.api.common.GlobalExceptionHandler`.

## 8. Real-time Scene events (WebSocket/STOMP)

Configured in `org.aventyrs.api.config.WebSocketConfig`: handshake at `/ws` (no SockJS), broker
destinations `/topic/**`, application prefix `/app/**`. `SceneRealtimeController` handles, per
Scene, all persisted then broadcast to every subscribed client:

| Action | Destination (send) | Broadcast (subscribe) |
|---|---|---|
| Move a token | `/app/scenes/{id}/move` | `/topic/scenes/{id}/moves` |
| Change combat status | `/app/scenes/{id}/status` | `/topic/scenes/{id}/status` |
| Advance the turn | `/app/scenes/{id}/turn` | `/topic/scenes/{id}/turn` |
| Resize the grid | `/app/scenes/{id}/grid` | `/topic/scenes/{id}/grid` |
| Sonar ping (unpersisted) | `/app/scenes/{id}/ping` | `/topic/scenes/{id}/pings` |

Rejected actions (unknown participant, occupied cell, a resize that would strand a token) are
logged and silently dropped rather than reported back — there's no auth yet to address a
rejection to a single caller. REST gives clients the full Scene/CharacterSheet snapshot on
load/reconnect; WebSocket carries only small, typed per-action deltas.

## 9. Grid mechanics

`Scene` participants are placed on a flat-top hex grid — "even-q" offset coordinates (`x`/`y`,
both non-negative), sized per-Scene (`width`×`height`, each ≤ 100). The coordinate system,
hex-distance calculation, and mapping from hex distance to core's `RangeBand` live in
`aventyrs-core`, under `org.aventyrs.core.scene.grid` — not in this repo — since it's
rules-adjacent logic the Android client will need too.

## 10. Project structure

```
org.aventyrs.api
├── common       — NotFoundException, ApiError, GlobalExceptionHandler
├── config       — MongoLiquibaseRunner, WebSocketConfig, OpenApiConfig
├── player       — Player CRUD
├── sheet        — CharacterSheet CRUD
├── monster      — MonsterSheet CRUD
├── scene        — Scene CRUD + SceneRealtimeController (WebSocket)
├── skill        — read-only SkillType listing
└── image        — image upload to SeaweedFS
```

Persistence documents (`*Document`) are separate from the core domain model on purpose — core
(`aventyrs-core`) stays a framework-free rules engine library with no Mongo/Spring/Jackson
coupling; this API owns the mapping between the two.

## 11. Related repositories

- [`aventyrs-core`](../aventyrs-core) — the rules engine (Character, CharacterSheet, Scene,
  skills, abilities, grid/range math). Required build dependency, published to `mavenLocal()`.
- `aventyrsapp` — the Android client. Not yet wired to this API or to `aventyrs-core`.
