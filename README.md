# Aventyrs API

Light API exposing the [Aventyrs](https://github.com/perlamber/aventyrs-core) tabletop RPG rules
engine over HTTP and WebSocket. It currently provides CRUD persistence for `Player`,
`CharacterSheet` and `Scene`; real-time gameplay actions (skill rolls, movement) are planned on
top of the WebSocket transport already wired in.

## Tech stack

- Java 17, Spring Boot 4.1 (Web MVC, WebSocket/STOMP, Validation)
- MongoDB via Spring Data, schema/index changes managed with Liquibase (`liquibase-mongodb`
  extension)
- [`aventyrs-core`](../aventyrs-core) — the framework-free rules engine library, consumed as a
  Maven dependency
- springdoc-openapi — OpenAPI 3 spec + Swagger UI, generated from the controllers/DTOs
- Testcontainers (`MongoDBContainer`) for integration tests
- Gradle

## Prerequisites

- JDK 17
- Docker — required both to run a local MongoDB instance for `bootRun` and to run the test
  suite (every test class boots the full Spring context against a Testcontainers-managed
  MongoDB; there is currently no test that runs without Docker)
- The [`aventyrs-core`](../aventyrs-core) sibling repository, checked out alongside this one and
  **published to your local Maven repository** — this project resolves it as
  `org.aventyrs.core:aventyrs-core:<version>` from `mavenLocal()`, not Maven Central:

  ```bash
  cd ../aventyrs-core
  ./gradlew publishToMavenLocal
  ```

  Check `build.gradle` here for the exact version this project currently depends on, and make
  sure that version has been published before building.

## Running locally

Start a MongoDB instance matching `spring.data.mongodb.uri` in
`src/main/resources/application.properties` (defaults to `mongodb://localhost:27017/aventyrs`):

```bash
docker run --rm -p 27017:27017 mongo:7.0
```

Then run the app:

```bash
./gradlew bootRun
```

On startup, `MongoLiquibaseRunner` applies every changelog under
`src/main/resources/db/changelog/` directly against MongoDB — Spring Boot's built-in Liquibase
autoconfiguration only drives a JDBC `DataSource`, so this project runs Liquibase's Mongo
extension programmatically instead (see that class for details).

## Testing

```bash
./gradlew test
```

Every test spins up a real MongoDB via Testcontainers (`@ServiceConnection` + `MongoDBContainer`),
so **Docker must be running**. The one exception during development was `HexGridTest` /
`RangeBandTest`, which were pure-logic and needed no Docker — they now live in `aventyrs-core`
alongside the grid/range code they test, since that's a plain `java-library` with no
Spring/Mongo dependency at all.

## API documentation

With the app running:

- Interactive Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Raw OpenAPI 3 spec (JSON): `http://localhost:8080/v3/api-docs`

Docs are generated from the controllers and DTOs (including Bean Validation constraints like
`@NotBlank`/`@Min`/`@Max`), so they stay in sync with the code without hand-maintained
annotations beyond the `@Tag` grouping on each controller.

## REST endpoints

All CRUD resources follow the same shape: `POST` (create), `GET /{id}`, `GET` (list),
`PUT /{id}` (full replace), `DELETE /{id}`.

| Resource | Base path | Notes |
|---|---|---|
| Player | `/api/players` | `name` + `login`; `login` is unique (violations return `409`) |
| CharacterSheet | `/api/character-sheets` | `character` (name, race, sexo, tendencia) is embedded directly rather than referenced by id — Mongo's document model makes that natural, and nothing modifies a Character independently of its sheet. `playerId` is still a reference, validated against `/api/players`. Create only takes the character + player; everything else (experience, resource pools, fama, temporary bonuses) starts at zero, mirroring `CharacterSheet.of(character, player)` in core. Supports `?playerId=` filtering |
| Scene | `/api/scenes` | Participants reference a `characterSheetId` (validated to exist), an initiative value, an ally sub-group (`group`, a `UUID` — participants sharing one are allies, mirroring core's `InitiativeEntry#group`), and a grid position. Positions must be unique within a Scene and within the fixed 100×100 grid. Also exposes `GET /{id}/groups` (participants partitioned by `group`) |

Adding participants to a Scene: `PUT /api/scenes/{id}` replaces the full participant list in one
call (name, participants, round/turn cursor together) and requires each participant's grid
`position` explicitly — use it when repositioning or reordering. `POST
/api/scenes/{id}/participants` instead joins a single participant to an already-existing Scene:
just `characterSheetId`, `initiativeValue`, and `group`, no `position` — the server assigns the
first free grid cell automatically and returns the created participant (`201`).

Validation/reference errors return `400`, missing resources `404`, unique-constraint violations
`409` — see `org.aventyrs.api.common.GlobalExceptionHandler`.

## Grid mechanics

`Scene` participants are placed on a fixed 100×100 hex grid — flat-top hexagons, "even-q" offset
coordinates (`x`/`y`, both non-negative). The coordinate system, hex-distance calculation, and
the mapping from hex distance to core's `Range` bands (`RangeBand`, assuming 1 hex step = 1
Unidade de Distância) live in `aventyrs-core`, under `org.aventyrs.core.scene.grid` — not in
this repo — since it's rules-adjacent logic the Android client will need too, not API/web
plumbing.

## WebSocket

STOMP over WebSocket is configured (`org.aventyrs.api.config.WebSocketConfig`) but not yet
carrying any application messages:

- Handshake endpoint: `/ws` (no SockJS fallback)
- Broker destinations: `/topic/**` (server → client broadcasts)
- Application prefix: `/app/**` (client → server, once handlers exist)

The intended shape once actions land: REST gives clients the full CharacterSheet/Scene snapshot
(on load or reconnect); WebSocket carries small, typed per-action events (e.g. a resolved skill
roll, a participant's new grid position) rather than re-broadcasting whole objects. See commit
history / prior design discussion for the reasoning — embedding a full `Character` graph in
every broadcast was measured at roughly 100–200x more data than an event-based message.

## Project structure

```
org.aventyrs.api
├── common       — NotFoundException, ApiError, GlobalExceptionHandler
├── config       — MongoLiquibaseRunner, WebSocketConfig, OpenApiConfig
├── player       — Player CRUD (document, repository, service, controller, DTOs)
├── sheet        — CharacterSheet CRUD
└── scene        — Scene CRUD (participants reference CharacterSheet + grid position)
```

Persistence documents (`*Document`) are separate from the core domain model on purpose — core
(`aventyrs-core`) stays a framework-free rules engine library with no Mongo/Spring/Jackson
coupling; this API owns the mapping between the two.

## Related repositories

- [`aventyrs-core`](../aventyrs-core) — the rules engine (Character, CharacterSheet, Scene,
  skills, abilities, grid/range math). Required build dependency, published to `mavenLocal()`.
- `aventyrsapp` — the Android client. Not yet wired to this API or to `aventyrs-core`.
