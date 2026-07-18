# postmannen: Collections / Environments tabs — design

## Purpose & scope

Extend the v1 workspace dropdown + collection list with a second tab for
environments. The center panel becomes two tabs — Collections and
Environments — switched with the left/right arrow keys, both scoped to the
currently selected workspace. No drilldown into either list, no editing —
same "list only" scope as v1's Collections panel.

## Data model & service

New domain model, same shape as `Collection`:

```kotlin
data class Environment(val id: String, val name: String)
```

`PostmanApiService` gains one method:

```kotlin
suspend fun getEnvironments(workspaceId: String): Result<List<Environment>>
```

`PostmanApiServiceImpl` implements it via `GET /environments?workspace={workspaceId}`,
decoding `{"environments": [{"id", "name", "uid"}]}` (confirmed shape from
Postman's API docs — a separate endpoint from collections, not nested in
the workspace-detail response the way collections currently is). Only
`id`/`name` map into `Environment`; `uid` unused, same as `Collection`.

## State & ViewModel

```kotlin
enum class Tab { COLLECTIONS, ENVIRONMENTS }

data class AppState(
    val workspaces: List<Workspace> = emptyList(),
    val selectedWorkspaceIndex: Int = 0,
    val collections: List<Collection> = emptyList(),
    val environments: List<Environment> = emptyList(),
    val activeTab: Tab = Tab.COLLECTIONS,
    val loading: Boolean = false,
    val statusMessage: String = ""
)
```

- `loadWorkspaces()` and `selectWorkspace(index)` both trigger
  `loadCollections(workspaceId)` **and** `loadEnvironments(workspaceId)` —
  eager, loaded together whenever the selected workspace changes.
- `loadEnvironments(workspaceId)` follows the exact same pattern as
  `loadCollections`: sets `loading = true`, calls the service, updates
  `environments` on success; on failure sets `statusMessage = "Error:
  ..."` and leaves the last-known-good `environments` list untouched.
- New `setActiveTab(tab: Tab)` — pure state update, no fetch (data for
  both tabs is already loaded eagerly).

## UI layer

The CENTER panel changes from a single bordered `ActionListBox` to a
`Panel(LinearLayout(Direction.VERTICAL))` (mirrors breui's `DetailPanel`):

- A tab-bar `Label` on top, rendering `[Collections]  Environments` or
  `Collections  [Environments]` — active tab bracketed, same convention as
  breui's `DetailTab` tab bar.
- The existing `ActionListBox` below it (renamed conceptually to "item
  list"), repopulated from `state.collections` or `state.environments`
  depending on `state.activeTab` whenever that list or the active tab
  changes.
- Left/Right arrow keys cycle `activeTab` via the window's
  `onUnhandledInput` handler — same mechanism breui uses for its
  `DetailTab` cycling; arrow keys aren't consumed by the list box or the
  workspace dropdown when neither is in an active navigation state that
  needs them.
- The key-hint bar at the bottom gains `[←][→] tabs` alongside the
  existing `q-quit`.

## Error handling

Same as the existing Collections path: `getEnvironments` failures are
caught in `AppViewModel`, surfaced via `statusMessage`, and the existing
`environments` list is left untouched rather than cleared.

## Testing

- `FakePostmanApiService` gains `environmentsResult` (mutable, defaults to
  a new `FIXTURE_ENVIRONMENTS` companion list) and records the requested
  workspace ID the same way `lastRequestedWorkspaceId` already does for
  collections (shared field, since both calls target the same workspace).
- `AppViewModelTest` gains cases: `loadWorkspaces` populates `environments`
  alongside `collections` for the first workspace; `selectWorkspace`
  reloads both; `setActiveTab` updates `activeTab` with no service call;
  an environments-fetch failure preserves the last-known-good
  `environments` list and surfaces `statusMessage`.
- UI layer (Lanterna panels) stays untested — same rationale as v1,
  headless testing isn't practical.
