# postmanen: workspace dropdown + collection list — design

## Purpose & scope

`postmanen` is a terminal UI for browsing Postman data, talking to Postman's
remote MCP server. Modeled directly on `~/dev/breui` (Kotlin + Lanterna +
coroutines + MVVM), reusing its architecture, layering, and theming approach.

v1 scope: a workspace dropdown in the top-left driving a collection list panel.
Selecting a workspace loads and displays its collections. No collection
drilldown, no request sending, no theme picker yet — those are future specs.

## Stack & project setup

Mirrors breui's `build.gradle.kts` almost verbatim:

- Kotlin JVM (toolchain 25), `kotlin("plugin.serialization")`, shadow jar,
  `application` plugin.
- `kotlinx-coroutines-core` for the StateFlow/coroutine plumbing.
- New dependencies breui doesn't have:
  - `io.modelcontextprotocol:kotlin-sdk-client:0.14.0` — official Kotlin MCP
    client SDK (confirmed present on Maven Central under this exact
    coordinate).
  - Ktor HTTP client (`ktor-client-cio` or `ktor-client-java`) +
    `ktor-client-sse`, required by the SDK's `StreamableHttpClientTransport`.
- `mainClass`: `postmanen.MainKt`.

## MCP integration layer

`service/PostmanMcpService`:

```kotlin
interface PostmanMcpService {
    suspend fun getWorkspaces(): Result<List<Workspace>>
    suspend fun getCollections(workspaceId: String): Result<List<Collection>>
}
```

`PostmanMcpServiceImpl`:

- Builds a Ktor `HttpClient` with `install(SSE)` and a default `Authorization:
  Bearer $POSTMAN_API_KEY` header.
- Connects an MCP `Client` over `StreamableHttpClientTransport` pointed at
  `POSTMAN_MCP_URL` (env override, defaults to `https://mcp.postman.com/minimal`
  — the real, already-configured Postman MCP endpoint).
- `getWorkspaces()` calls the `getWorkspaces` tool with no arguments;
  `getCollections(workspaceId)` calls the `getCollections` tool with
  `{"workspace": workspaceId}`. Both tool names and their input schemas were
  verified against the live server during design (not guessed).
- Each tool call returns one `content` block of `type: "text"` containing a
  markdown-formatted table (confirmed via live probe — the server does not
  return structured JSON). The impl extracts that text and hands it to the
  markdown table parser, then maps rows into domain models.

Env vars:

- `POSTMAN_API_KEY` — required. `Main` reads it before starting the screen;
  if unset, prints a clear message to stderr and exits 1. Never hardcoded,
  never logged.
- `POSTMAN_MCP_URL` — optional, defaults to `https://mcp.postman.com/minimal`.

## Markdown table parsing

`util/MarkdownTable.kt`:

```kotlin
fun parseMarkdownTable(text: String): List<Map<String, String>>
```

Finds the `| header | ... |` line followed by a `|---|...|` separator line,
then parses each subsequent `|`-delimited row until a blank line or end of
input. Cell values are trimmed and HTML-unescaped (`&#39;`, `&quot;`, `&amp;`,
etc. — the real server output includes these, confirmed via live probe).
Pure function, no I/O — trivially unit-testable.

## Domain models

```kotlin
data class Workspace(val id: String, val name: String, val type: String)
data class Collection(val id: String, val name: String)
```

Built from parsed row maps in the service layer using the real column names
observed live: `getWorkspaces` rows have `id`, `name`, `type`, ...;
`getCollections` rows have `id`, `name`, `owner`, ...

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
- MCP call failures (network, tool error) → caught in `AppViewModel`,
  surfaced via `statusMessage`; state's existing lists are preserved.

## Testing

- `FakePostmanMcpService` — fixture-based fake, mirrors breui's
  `FakeBrewService`, used in `AppViewModelTest` via `runTest`.
- `MarkdownTableParserTest` — unit tests using the literal fixture strings
  captured from real `getWorkspaces`/`getCollections` responses during
  design, so the parser is verified against actual server output rather than
  a guessed format.
- `PostmanMcpServiceImplTest` — one integration smoke test that calls the
  real `getWorkspaces` tool against the live server, gated on `POSTMAN_API_KEY`
  being set (mirrors breui's `BrewServiceImplTest` smoke test against real
  `brew`).
- UI layer (Lanterna panels) not tested — same rationale as breui, headless
  testing isn't practical.
