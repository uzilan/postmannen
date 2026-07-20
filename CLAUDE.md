# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`postmannen` is a Kotlin/Ktor REST API plus a React/TypeScript frontend
for browsing Postman data (workspaces, collections, environments) via
Postman's public REST API (`https://api.getpostman.com`). It was
originally a Lanterna terminal UI; that TUI has been replaced by this
REST+React stack (see `docs/superpowers/specs/2026-07-19-rest-api-react-migration-design.md`
for the rationale — accumulating Lanterna widget-level workarounds, not
a fundamental problem with the app's navigation needs). Chat (the
third UI panel, backed by `ChatRoutes.kt` + `ClaudeCliService`) has
since been ported — see the Chat subsection under Backend/Frontend
architecture below.

## Repo layout

- **`src/main/kotlin/postmannen/`** — the Kotlin/JVM side.
  - `model/` — plain data classes (`Workspace`, `Collection`,
    `CollectionDetail`, `CollectionNode`, `Environment`,
    `EnvironmentDetail`, etc.), all `@Serializable` so the server can
    `call.respond(...)` them directly with no separate DTO layer.
    `CollectionNode` is a `@Serializable sealed class` with
    `@SerialName("folder")`/`@SerialName("item")` on its subtypes —
    kotlinx.serialization's automatic polymorphism handles the
    `"type"` discriminator.
  - `service/` — `PostmanApiService` (interface), `PostmanApiServiceImpl`
    (real Ktor-client-backed implementation), `CachingPostmanApiService`
    (in-memory read/write-through cache decorator). Unchanged by the
    REST migration — this layer was already UI-agnostic.
  - `server/` — the Ktor server: `ServerMain.kt` (entry point,
    `embeddedServer(CIO, port = 8080)`), one route file per resource
    area (`WorkspaceRoutes.kt`, `CollectionRoutes.kt`,
    `EnvironmentRoutes.kt`), and `RouteSupport.kt` (shared
    `Result<T>` → HTTP response mapping).
- **`web/`** — Vite + React + TypeScript + MUI frontend. Plain `fetch()`
  calls via `web/src/api.ts`, no state library — component-local state
  only (`useState`), no server-side session.

## Commands

Backend:
```bash
./gradlew build              # compile + run all tests
./gradlew test                                          # unit tests only
./gradlew test --tests "postmannen.server.EnvironmentRoutesTest"  # one class
POSTMAN_API_KEY=... ./gradlew run      # run the REST API on :8080
./gradlew shadowJar           # build the fat jar (build/libs/postmannen.jar)
java -jar build/libs/postmannen.jar   # run the fat jar directly (once mainClass points at ServerMainKt)
```

Frontend:
```bash
cd web
npm run dev     # Vite dev server; proxies /api/* to http://localhost:8080 (see vite.config.ts)
npm test        # Vitest + React Testing Library
npx tsc -b --noEmit   # type-check
```

`POSTMAN_API_KEY` is required at runtime — `ServerMain` fails fast
(stderr + exit 1) if it's missing or blank, before the server starts.
Never hardcode or log it. The server is localhost-only with no
auth — this is a single-user local tool, same trust model as the old
TUI reading the env var directly.

There is one integration test, `PostmanApiServiceImplTest`, tagged
`@Tag("integration")`. It calls the real Postman API and self-skips via
`assumeTrue` when `POSTMAN_API_KEY` isn't set, so `./gradlew test` is
safe to run without a key — the test just reports as skipped, not
failed.

## Architecture

### Backend (`server/`)

Thin routes delegating straight to `PostmanApiService` — no business
logic lives in the route layer:

- `GET /api/workspaces`, `GET /api/workspaces/{id}` (→ that workspace's
  collections), `POST /api/workspaces/{id}/refresh` (→
  `invalidateWorkspace`, cache-bust equivalent of the old TUI's `r` key).
- `GET /api/collections/{uid}` (→ folder/request tree + variables).
- `GET /api/environments?workspaceId=`, `GET /api/environments/{uid}`,
  `PUT /api/environments/{uid}`, `POST /api/environments`,
  `DELETE /api/environments/{uid}` (mirrors `DELETE /api/collections/{uid}`).

`RouteSupport.kt`'s `respondResult`/`respondUnitResult` map
`Result<T>`/`Result<Unit>` from the service layer to HTTP responses —
success → the value (or 204 for Unit), failure → 502 + a JSON
`{"error": "..."}` body. Every route handler is a one-liner built on
top of these.

**Ktor content-negotiation gotcha**: `install(ContentNegotiation) { json(Json { ... }) }`
requires calling the `json(...)` function with the `Json` instance —
merely constructing a `Json { ... }` object inside the block and
discarding it registers no converter and silently breaks
(de)serialization. This bit the route tests once; every
`testApplication` block and `ServerMain.kt` must call
`json(Json { ignoreUnknownKeys = true })`, not just `Json { ... }`.

No compare-specific endpoint exists — the client fires parallel
`GET /api/environments/{uid}` requests for each marked environment
(same per-item-resilient pattern the old `AppViewModel.refreshEnvironmentPanel`
used: one failing environment doesn't blank the others).

**Chat (`ChatRoutes.kt` / `ClaudeCliService.kt`)** — `POST /api/chat`
shells out to the `claude` CLI (`ClaudeCliServiceImpl`, non-interactive
`-p` mode, `--output-format stream-json`, optional `--resume
<sessionId>` to continue a turn). Two things keep the chat's context
scoped to Postman data only, not this repo:
- The subprocess's cwd is an isolated temp dir (`ensureWorkDir()`),
  never the repo root — so it can't auto-load this project's
  `CLAUDE.md` or see `git log`/branch state.
- `--allowedTools mcp__postman` restricts the CLI to the Postman MCP
  server (`ensureMcpConfig()` spawns `npx @postman/postman-mcp-server`
  with `POSTMAN_API_KEY`) — no Bash/Read/Grep on the local filesystem.
  There used to be a `--permission-mode bypassPermissions` flag
  instead; that granted full tool access and was the cause of chat
  responses leaking repo/branch/commit context. Don't reintroduce
  `bypassPermissions` without re-adding an equivalent `--allowedTools`
  scope.

`ChatRoutes.kt`'s `buildPrompt` prepends only `workspaceName`/
`workspaceId`/`highlightedLabel` (from `ChatContextDto`) to the user's
prompt — no collection/environment bodies. The frontend sends this via
`sendChatMessage` in `api.ts`, built from the currently-selected
workspace and detail-panel selection in `App.tsx`'s `handleSendChat`.

### Frontend (`web/`)

- **`api.ts`** is the single fetch-wrapper module — every component
  imports typed request functions from here rather than calling
  `fetch` directly. Grows one function per backend route; don't create
  a second parallel API-calling convention.
- **Collection tree** (`CollectionTree.tsx`): the collection itself is
  a collapsible root node (starts collapsed, chevron to expand) with
  its folder/request tree nested one level deeper — not a static
  heading above the tree. `collectNodeIds(parentId, nodes)` is the
  single source of truth for position-based node ids
  (`collectionUid`, `collectionUid/0`, `collectionUid/0/1`, ...),
  used both to seed the default-collapsed set and to toggle nodes —
  avoiding the two-call-site drift the old `AppViewModel.collectFolderIds`
  vs `TabbedListPanel.flatten` split risked. Indentation for a node at
  `depth` is `(depth + 1) * 2` (in MUI spacing units) specifically so a
  depth-1 folder/item visibly indents past the collection row's own
  default padding — using plain `depth * 2` makes depth-1 rows land at
  the same horizontal position as the collection row above them.
  Request leaf nodes show a color-coded HTTP method label
  (`MethodLabel` in `CollectionTree.tsx`, `METHOD_COLORS` map — GET
  green, POST orange, PUT blue, PATCH purple, DELETE red, unknown
  methods fall back to grey) instead of a generic file icon, with a
  fixed `minWidth` + `mr` so longer method names (`DELETE`) still leave
  a gap before the request name. Both `CollectionTree.tsx`'s root row
  and `EnvironmentList.tsx`'s rows use the same hover-reveal delete
  icon pattern — `sx={{ '&:hover .delete-*-button': { opacity: 1 } }}`
  on the row plus an `IconButton` with `opacity: 0` and `e.stopPropagation()`
  in its `onClick` so deleting doesn't also trigger the row's own
  select/expand handler — both go through `App.tsx`'s shared
  `ConfirmDialog` (`collectionPendingDelete`/`environmentPendingDelete`
  state) before calling `deleteCollection`/`deleteEnvironment`.
- **Left panel legend**: both the Collections and Environments tabs
  render inside one shared bordered `<fieldset>`/`<legend>` in `App.tsx`,
  with the legend text switching on `activeTab` — don't duplicate the
  fieldset per tab, just swap the legend string and inner content.
- **Right panel (`DetailPanel.tsx`)** dispatches on a `DetailContent`
  discriminated union (`none`/`loading`/`collectionVariables`/
  `environments`/`request`), mirroring the old Lanterna `DetailContent`
  sealed class. The collection-variables view, the environment
  key/value grid, and a selected request's headers/body each render
  inside their own bordered `<fieldset>` — legend is `"Variables"` for
  collection variables, the comma-joined shown environment name(s)
  (e.g. `"Staging"` or `"Staging, Production"`) for the environment
  grid, so the compare case is self-labeling without a separate
  "Compare" concept, and `"Headers"`/`"Body"` for the request view.
  Each of those two request fieldsets is only rendered when there's
  content (`item.headers.length > 0`, `item.body != null`) — an empty
  section isn't shown as an empty box. The body is rendered through
  `JsonBody`, a dependency-free regex tokenizer (not `JSON.parse`,
  since Postman request bodies can carry `//` comments and so aren't
  strict JSON) that colors comments/keys/strings/literals/numbers
  VS-Code-dark-style; no syntax-highlighting npm package is installed
  for this.
- **Add/delete key row is a fan-out**: editing a key across the
  currently-shown environment set calls `updateEnvironment` once per
  shown environment (`Promise.all` in `App.tsx`'s `handleAddKey`/
  `handleDeleteKey`) — a single-environment view is just the N=1 case.
  No rollback-on-partial-failure (unlike the old TUI's
  `AppViewModel.fanOutKeyUpdate`) — a failed fan-out call surfaces via
  `statusMessage`, per the "preserve last-known-good data on failure"
  principle, not a hard requirement to re-add.
- **Tab switching clears the right panel**: `App.tsx` resets
  `detailContent` to `{ kind: 'none' }` and clears
  `highlightedEnvironmentId`/`markedEnvironmentIds` in a `useEffect`
  keyed on `activeTab` — without this, switching from Environments back
  to Collections (or vice versa) leaves stale content in the detail
  panel until a new selection is made.
- **No server-side session/UI state** — collapsed-tree-ids,
  highlighted/marked environment ids all live in React component state.
  This was a deliberate scope decision (see the migration spec); chat's
  context-passing (`workspaceName`/`workspaceId`/`highlightedLabel`)
  reads straight from that same client-side state per turn rather than
  server-tracked session state.

### Toolchain gotchas discovered building this

- **MUI needs an explicit `ThemeProvider` + `CssBaseline`.** The Vite
  React-TS scaffold's `index.css` sets a dark background under
  `prefers-color-scheme: dark`; MUI defaults to its light theme (dark
  text) when no theme is provided. Without wiring up a theme that
  tracks the system color scheme (`main.tsx`'s `useMediaQuery('(prefers-color-scheme: dark)')`
  + `createTheme({ palette: { mode } })`), text renders at near-zero
  contrast in dark mode. `index.css` was stripped down to a minimal
  reset for the same reason — the scaffold's landing-page CSS
  (`#root` width/centering, custom CSS vars) fights MUI's own layout
  assumptions.
- **`verbatimModuleSyntax` (on by default in the Vite React-TS
  template's `tsconfig`) requires type-only imports for types**, even
  mixed with value imports from the same module — `import { Foo, type Bar } from 'x'`
  or a separate `import type { Bar } from 'x'` line, not a bare
  `import { Foo, Bar } from 'x'` where `Bar` is a type. Forgetting this
  fails `tsc -b` (not `vitest`, which doesn't type-check) — always run
  `npx tsc -b --noEmit` after adding new type imports, not just the
  test suite.
- **`vite.config.ts` must import `defineConfig` from `'vitest/config'`**,
  not `'vite'`, once a `test: {...}` block is added — the plain `vite`
  export's config type doesn't know about the `test` key and `tsc -b`
  rejects it.
- **The dev proxy** (`vite.config.ts`'s `server.proxy['/api']`) is what
  lets the Vite dev server on its own port talk to the Ktor server on
  `:8080` without CORS — if `/api` calls 502 with an *empty* body (not
  a JSON `{"error": ...}` one), that's Vite's proxy failing to reach
  the backend at all (backend not running), not a route-level failure.

## API integration notes

Response shapes were verified against Postman's real API during
development, not guessed — treat these as load-bearing (unchanged by
the REST migration; `PostmanApiServiceImpl` is what actually calls
these):

- `GET /workspaces` → `{"workspaces": [{"id","name","type"}]}`
- `GET /workspaces/{id}` → `{"workspace": {..., "collections": [{"id","name","uid"}]}}`
- `GET /environments?workspace={id}` → `{"environments": [{"id","name","uid"}]}`
- `GET /environments/{uid}` → `{"environment": {"id","name","values": [{"key","value","enabled","type"}]}}` — only `enabled == true` entries are kept when building the `Map<String,String>`.

`ContentNegotiation` is configured with `ignoreUnknownKeys = true` —
required, because Postman's real responses carry more fields than the
DTOs declare (`visibility`, `owner`, timestamps, etc.). Don't remove it.

## Design docs

Design specs and implementation plans for past and in-progress features
live under `docs/superpowers/` (specs/ and plans/), but that whole
directory is gitignored — it's a local working record, not part of the
shipped project. Don't expect it to be present in a fresh clone.
