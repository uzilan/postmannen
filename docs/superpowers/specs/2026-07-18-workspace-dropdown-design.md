# postmannen: workspace dropdown + collection list — design

## Purpose & scope

`postmannen` is a terminal UI for browsing Postman data, talking to Postman's
public REST API (`https://api.getpostman.com`). Modeled directly on
`~/dev/breui` (Kotlin + Lanterna + coroutines + MVVM), reusing its
architecture, layering, and theming approach.

v1 scope: a workspace dropdown in the top-left driving a collection list panel.
Selecting a workspace loads and displays its collections. No collection
drilldown, no request sending, no theme picker yet — those are future specs.

## Stack & project setup

Mirrors breui's `build.gradle.kts` almost verbatim:

- Kotlin JVM (toolchain 25), `kotlin("plugin.serialization")`, shadow jar,
  `application` plugin.
- `kotlinx-coroutines-core` for the StateFlow/coroutine plumbing.
- New dependencies breui doesn't have:
  - Ktor HTTP client (`ktor-client-cio`) + `ktor-client-content-negotiation`
    + `ktor-serialization-kotlinx-json`, for calling the Postman REST API
    and decoding its JSON responses.
- `mainClass`: `postmannen.MainKt`.

## Postman API integration layer

`service/PostmanApiService`:

```kotlin
interface PostmanApiService {
    suspend fun getWorkspaces(): Result<List<Workspace>>
    suspend fun getCollections(workspaceId: String): Result<List<Collection>>
}
```

`PostmanApiServiceImpl`:

- Builds a Ktor `HttpClient` (CIO engine) with `ContentNegotiation` +
  `json()`, and a default `X-Api-Key: $POSTMAN_API_KEY` header (confirmed
  header name/format from Postman's own API auth docs — not guessed).
- Base URL is the fixed constant `https://api.getpostman.com` — Postman's
  public API has one address, so no env-var override (no requested use case
  for pointing at a different host).
- `getWorkspaces()` calls `GET /workspaces`, decoding
  `{"workspaces": [{"id", "name", "type"}]}` (confirmed shape from Postman's
  API reference/community examples).
- `getCollections(workspaceId)` calls `GET /workspaces/{workspaceId}`,
  decoding `{"workspace": {..., "collections": [{"id", "name", "uid"}]}}`
  and mapping each entry's `id`/`name` into a `Collection` (`uid` unused in
  v1).

Env vars:

- `POSTMAN_API_KEY` — required. `Main` reads it before starting the screen;
  if unset, prints a clear message to stderr and exits 1. Never hardcoded,
  never logged.

## Domain models

```kotlin
data class Workspace(val id: String, val name: String, val type: String)
data class Collection(val id: String, val name: String)
```

Built from the decoded JSON DTOs in the service layer.

## State & ViewModel

```kotlin
data class AppState(
    val workspaces: List<Workspace> = emptyList(),
    val selectedWorkspaceIndex: Int = 0,
    val collections: List<Collection> = emptyList(),
    val loading: Boolean = false,
    val statusMessage: String = ""
)
```

`AppViewModel(service, scope)`, single `MutableStateFlow<AppState>`, all
mutations via `update { copy(...) }`:

- `loadWorkspaces()` — called once from `Main` (like breui's
  `loadInstalled()`). On success, also triggers `loadCollections` for the
  first workspace if any exist.
- `selectWorkspace(index: Int)` — updates `selectedWorkspaceIndex`, triggers
  `loadCollections(workspaces[index].id)`.
- `loadCollections(workspaceId: String)` — sets `loading = true`, calls the
  service, updates `collections` on success.
- Failures from either call are caught and surfaced via `statusMessage =
  "Error: ..."`; the last-known-good list (`workspaces`/`collections`) is left
  untouched rather than cleared, so a transient failure doesn't blank the UI.

## UI layer

`ui/App.kt`, same shape as breui's:

- Root `Panel(BorderLayout())`.
- `TOP`: a small panel holding a Lanterna `ComboBox<Workspace>` — a real
  dropdown widget. `Workspace.toString()` renders `"name (type)"`. A
  selection listener calls `viewModel.selectWorkspace(index)`.
- `CENTER`: collection list panel (`ActionListBox`, selection-only, no
  actions yet) rendering each `Collection.name`, bordered
  `Borders.singleLine("Collections")`.
- `BOTTOM`: `StatusBar`, ported from breui, showing `statusMessage` or a
  spinner while `loading`.
- Same collection pattern as breui: a coroutine collects `viewModel.state`,
  calls `applyState()` then `gui.updateScreen()` under `synchronized(gui)`.
- Single fixed theme (`businessmachine`, matching breui's default) set once
  in `Main` via `LanternaThemes.getRegisteredTheme(...)`. No picker in v1.

## Error handling

- Missing/invalid `POSTMAN_API_KEY` → fail fast in `Main`, before the screen
  starts: stderr message, exit 1.
- API call failures (network, non-2xx) → caught in `AppViewModel`,
  surfaced via `statusMessage`; state's existing lists are preserved.

## Testing

- `FakePostmanApiService` — fixture-based fake, mirrors breui's
  `FakeBrewService`, used in `AppViewModelTest` via `runTest`.
- `PostmanApiServiceImplTest` — one integration smoke test that calls the
  real `getWorkspaces` endpoint against the live API, gated on
  `POSTMAN_API_KEY` being set (mirrors breui's `BrewServiceImplTest` smoke
  test against real `brew`).
- UI layer (Lanterna panels) not tested — same rationale as breui, headless
  testing isn't practical.
