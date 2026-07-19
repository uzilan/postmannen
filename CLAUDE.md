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
- **Collections tree**: `CollectionNode` (sealed: `Folder`/`RequestItem`)
  and `CollectionDetail` model a collection's folder/request tree,
  fetched per-collection via `getCollectionDetail` and flattened for
  display by `TabbedListPanel.flatten`/`flattenChildren`. Node ids are
  **position-based paths** (`collectionUid`, `collectionUid/0`,
  `collectionUid/0/1`, ...), not name- or content-based — Postman allows
  duplicate sibling names and folders/requests carry no stable id of
  their own. `AppViewModel.collectFolderIds` uses the identical scheme to
  seed `collapsedNodeIds` (everything starts collapsed); the two must
  stay in lockstep or a folder collapsed in one place won't match the row
  the UI tries to toggle. Accepted trade-off: reordering items in Postman
  between loads can make a previously-expanded folder appear collapsed
  again.
- **`AppState`** is the one place all UI-relevant state lives — workspace
  list, selected index, collections + their trees (`collectionDetails`,
  `collapsedNodeIds`), environments, active tab, the marked-for-compare
  selection set, `environmentPanelDetails` (whatever the right panel is
  currently showing for the Environments tab), loading, status message.
  `App` never holds its own copy of anything the ViewModel already owns;
  it only tracks `last*` snapshot fields to skip redundant Lanterna
  widget rebuilds (see below), plus a couple of UI-only concerns the
  ViewModel has no business knowing (`gridFocused`, pending focus after a
  workspace switch).
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
  `q`-quit, `r`-refresh, and arrow-key tab switching all work regardless
  of which widget currently has focus. `Ctrl+N`/`Ctrl+D` are
  context-sensitive there: `App` tracks a `gridFocused: Boolean` (see the
  `EnvironmentGridPanel` bullet below) and routes those keys to either
  "create new environment" or "add/delete a key row" depending on it.
  Popup windows (`NamePromptOverlay`, `ConfirmOverlay`) get their own
  listener for their own keys (`Escape`, `Enter`); they don't share the
  main window's listener.
- **`Tab` cannot be caught at the window level.** Lanterna reserves `Tab`
  for its own default focus-cycling and claims it before
  `onUnhandledInput` ever sees it — the same class of problem as the
  `TextBox` gotcha below, just one level up. `App` uses `Tab` to move
  focus from the tree list into the environment-editing grid (see next
  bullet), and the only place that reliably works is intercepting it
  inside `TabbedListPanel`'s `itemListBox.handleKeyStroke` override
  (`onTabKey`, alongside the existing `onSpaceKey`/`onEnterKey`) — same
  pattern, same reason. Exiting the grid uses `Escape` instead, which
  *does* reach the window listener reliably (proven by the old
  `ComparisonOverlay` popup, which relied on exactly this for its own
  dismiss behavior) — don't try to make `Tab` bidirectional at the widget
  level; the asymmetry is deliberate, not an oversight.
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
  right after construction — see `EnvironmentGridPanel`'s `keyBox`/`valueBox`
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
  `EnvironmentGridPanel`'s `handleAddDeleteShortcut` (`Ctrl+N`/`Ctrl+D`).
- **`DetailPanel`** is the right-hand panel (`App.kt` splits its center
  area into `tabbedListPanel` + `detailPanel`, hidden entirely when the
  active tab's list is empty). It dispatches on a `DetailContent` sealed
  type: `None`/`Loading`/`CollectionVariables` (Collections tab, driven
  by whatever's highlighted) and `Environments` (Environments tab). The
  `Environments` case is the one to be careful with: it hosts an
  `EnvironmentGridPanel` — ported from what used to be a separate popup
  window (`ComparisonOverlay`, deleted) into an embeddable `Panel` — and
  **rebuilds it only when the shown environment uid *set* changes**,
  patching values in place (`applyDetails`) otherwise. This matters
  because a fresh `Panel` has no Lanterna focus of its own (unlike
  opening a brand-new popup window, which Lanterna auto-focuses),
  while patching must *not* touch focus at all or it fights the user's
  own in-grid `Tab` navigation mid-edit. `applyContent` returns whether a
  rebuild happened so `App` knows whether it must explicitly refocus.
- **Which environment(s) the right panel shows** is entirely reactive,
  no keypress required: `AppViewModel.refreshEnvironmentPanel(highlightedId)`
  mirrors the old `v`/`c` split automatically — 0 or 1 marked
  environments follow the cursor, 2+ marked show the marked set
  regardless of cursor. It's called from `App`'s `refreshDetailPanel()`,
  itself invoked both reactively (every `AppState` emission) and directly
  from cursor-movement (`onSelectionMaybeChanged`) since pure list
  navigation never touches `AppState`. An equality guard skips redundant
  fetches when the target set hasn't changed — but note that guard only
  helps once a fetch has *succeeded* (`CachingPostmanApiService` doesn't
  cache failures), so a repeatedly-failing highlighted environment will
  keep re-hitting the network on unrelated state changes; accepted as a
  minor, bounded cost, not a bug to chase.
- **Service layer**: `PostmanApiService` is the interface; `PostmanApiServiceImpl`
  is the real Ktor-backed implementation (one shared `HttpClient` with
  `X-Api-Key` auth baked into `defaultRequest`); `FakePostmanApiService`
  (test-only) is a fixture-based fake used by `AppViewModelTest` — no
  mocking library, just a hand-rolled fake with mutable `*Result` fields
  the test can override per-case. Every service method returns
  `Result<T>`, never throws past the `runCatching` boundary in the impl.
  `CachingPostmanApiService` decorates `PostmanApiServiceImpl` (composed
  in `Main.kt`) with an in-memory, session-lifetime, read/write-through
  cache — every read caches on success; `updateEnvironment`/`createEnvironment`
  write through on success instead of invalidating, so the app's own
  edits never go stale. `PostmanApiService.invalidateWorkspace(workspaceId)`
  has a default no-op body specifically so `PostmanApiServiceImpl` and
  `FakePostmanApiService` need zero changes for it — only the caching
  decorator overrides it meaningfully, driven by the `r`-refresh key.
- **Concurrent fetches**: two call sites launch several requests in
  parallel via `scope.async { ... }.awaitAll()` rather than a sequential
  loop — `loadCollections`'s per-collection tree fetch and
  `AppViewModel.refreshEnvironmentPanel`'s per-environment detail fetch.
  Both are **per-item resilient, not all-or-nothing**: a single failure
  is dropped (`mapNotNull { it.getOrNull() }`) and its name surfaced in
  `statusMessage`, while whatever succeeded still populates state. This
  was a deliberate choice over the all-or-nothing pattern an earlier,
  now-removed method (`openComparison`) used — these are passive
  background/reactive fetches, not a single user-initiated action across
  a small fixed set, so one bad item shouldn't blank everything else.

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
