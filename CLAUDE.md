# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`postmannen` is a Kotlin/Lanterna terminal UI for browsing Postman data
(workspaces, collections, environments) via Postman's public REST API
(`https://api.getpostman.com`). It is modeled directly on `~/dev/breui`
(same author, same architecture) — when in doubt about a pattern, check
that repo for a proven-working precedent before inventing a new one.

## Commands

```bash
./gradlew build              # compile + run all tests
./gradlew test                                          # unit tests only
./gradlew test --tests "postmannen.viewmodel.AppViewModelTest"  # one class
./gradlew test --tests "postmannen.viewmodel.AppViewModelTest.loadWorkspaces sets workspaces and clears loading"  # one test
./gradlew run                # run the TUI (needs POSTMAN_API_KEY set)
./gradlew shadowJar           # build the fat jar (build/libs/postmannen.jar)
java -jar build/libs/postmannen.jar   # run the fat jar directly
```

`POSTMAN_API_KEY` is required at runtime — `Main` fails fast (stderr +
exit 1) if it's missing or blank, before the screen starts. Never
hardcode or log it.

There is one integration test, `PostmanApiServiceImplTest`, tagged
`@Tag("integration")`. It calls the real Postman API and self-skips via
`assumeTrue` when `POSTMAN_API_KEY` isn't set, so `./gradlew test` is
safe to run without a key — the test just reports as skipped, not
failed.

## Architecture

MVVM, one direction of data flow, single source of truth:

```
service (PostmanApiService) → viewmodel (AppViewModel) → ui (App)
```

- **`AppViewModel`** holds one `MutableStateFlow<AppState>`. Every mutation
  goes through `update { copy(...) }` — never touch `_state` directly from
  outside that pattern. All `load*` methods follow the same shape: set
  `loading = true`, call the service, `onSuccess` update the relevant
  field, `onFailure` set `statusMessage = "Error: ..."` and leave the
  existing list untouched. That "preserve last-known-good data on
  failure" rule is deliberate — a transient network error should never
  blank a list the user was already looking at.
- **`AppState`** is the one place all UI-relevant state lives — workspace
  list, selected index, collections, environments, active tab, selection
  set for comparison, overlay visibility, loading, status message. `App`
  never holds its own copy of anything the ViewModel already owns; it
  only tracks `last*` snapshot fields to skip redundant Lanterna widget
  rebuilds (see below).
- **`App.kt`** is the single Lanterna window. A background coroutine
  collects `viewModel.state` and calls `applyState()` under
  `synchronized(gui)`, then `gui.updateScreen()`. `applyState()` diffs
  incoming state against `last*` fields before touching any widget —
  Lanterna list boxes lose their focus/selection position on
  `clearItems()`, so a naive "always rebuild" approach causes visible
  selection jumps (this bit us once — see `itemListBox`'s
  `previousIndex` capture/restore in `applyState`).
- **Keybindings** are handled at the window level via a
  `WindowListenerAdapter.onUnhandledInput`, not per-widget — this is how
  `q`-quit, arrow-key tab switching, and `c`-compare all work regardless
  of which widget currently has focus. Overlay windows (`ComparisonOverlay`)
  get their own listener for their own keys (`Escape`); they don't share
  the main window's listener.
- **Custom Lanterna widgets** (`WorkspaceDropdown`, the highlighted
  `itemListBox` in `App.kt`) use a `ListItemRenderer`/`ComboBoxRenderer`
  override to get the black-on-`TextColor.ANSI.GREEN` highlight style
  used throughout the app. `WorkspaceDropdown` also reaches into
  `ComboBox`'s private `popupWindow` field via reflection to apply the
  same highlight to the open dropdown's popup list — Lanterna gives no
  public extension point for that, so the reflection is intentional, not
  a hack to clean up.
- **`TextBox`**: Lanterna's default `TextBox` renderer fills unused width
  with `.` characters, which reads as a distracting placeholder/mask in
  this app's UI. Every `TextBox` we create must call
  `(box.renderer as? TextBox.DefaultTextBoxRenderer)?.setUnusedSpaceCharacter(' ')`
  right after construction — see `ComparisonOverlay`'s `keyBox`/`valueBox`
  and `NamePromptOverlay`'s `nameBox` for the pattern.
  Separately, Lanterna's default `TextBox.handleKeyStroke` claims `Enter`
  (moves focus to the next component) and `Ctrl`+character combos as
  literal text input, so those keystrokes never reach the containing
  window's `onUnhandledInput` while the box has focus — which is most of
  the time, since these boxes take focus on open. Any `TextBox` that needs
  to react to `Enter`, `Escape`, or a `Ctrl`+key shortcut must override
  `handleKeyStroke` on the box itself and intercept before falling through
  to `super.handleKeyStroke(...)`; a window-level listener alone is not
  enough. See `NamePromptOverlay.nameBox` (`Enter`/`Escape`) and
  `ComparisonOverlay`'s `handleAddDeleteShortcut` (`Ctrl+N`/`Ctrl+D`).
- **Service layer**: `PostmanApiService` is the interface; `PostmanApiServiceImpl`
  is the real Ktor-backed implementation (one shared `HttpClient` with
  `X-Api-Key` auth baked into `defaultRequest`); `FakePostmanApiService`
  (test-only) is a fixture-based fake used by `AppViewModelTest` — no
  mocking library, just a hand-rolled fake with mutable `*Result` fields
  the test can override per-case. Every service method returns
  `Result<T>`, never throws past the `runCatching` boundary in the impl.
- **Concurrent fetches**: `AppViewModel.openComparison()` fetches multiple
  environments' details concurrently via `scope.async { ... }.awaitAll()`,
  not a sequential loop — and aborts the whole operation (no partial
  state) if any one fetch fails. This is the only place in the codebase
  doing concurrent I/O; follow this pattern if a similar "fetch N things,
  all-or-nothing" need comes up again.

## API integration notes

Response shapes were verified against Postman's real API during
development, not guessed — treat these as load-bearing:

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
