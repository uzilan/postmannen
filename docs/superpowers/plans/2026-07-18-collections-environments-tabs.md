# Collections / Environments Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second tab (Environments) to postmannen's center panel,
switched with left/right arrow keys, scoped to the currently selected
workspace — same "list only" treatment as the existing Collections tab.

**Architecture:** Extends the existing MVVM layering. `Environment` is a
new domain model identical in shape to `Collection`. `PostmanApiService`
gains `getEnvironments(workspaceId)`. `AppState` gains `environments` and
`activeTab: Tab`. `AppViewModel` loads both collections and environments
together (eager) whenever the selected workspace changes, and exposes
`setActiveTab(tab)` as a pure state update. The UI's CENTER panel becomes
a small vertical stack (tab-bar label + the existing list box), mirroring
breui's `DetailPanel` tab-bar convention.

**Tech Stack:** Same as the existing project — Kotlin JVM, Lanterna,
kotlinx-coroutines, Ktor client, JUnit5/kotlin-test. No new dependencies.

## Global Constraints

- `GET /environments?workspace={workspaceId}` → `{"environments":
  [{"id", "name", "uid"}]}` — separate endpoint from collections (not
  nested in the workspace-detail response). Only `id`/`name` map into
  `Environment`; `uid` unused, same treatment as `Collection`.
- `loadWorkspaces()` and `selectWorkspace(index)` both trigger
  `loadCollections` **and** `loadEnvironments` for the target workspace —
  eager, not lazy per-tab.
- `setActiveTab(tab: Tab)` is a pure state update — no service call.
- Environment fetch failures follow the exact same pattern as collection
  failures: `statusMessage = "Error: ..."`, last-known-good `environments`
  list preserved.
- Tab switching is left/right arrow keys, handled at the window level
  (`onUnhandledInput`), same mechanism as breui's `DetailTab` cycling.
- No drilldown into either list, no editing — list-only scope, same as
  the existing Collections tab.

---

## File Structure

- `src/main/kotlin/postmannen/model/Environment.kt` — new, `Environment`
  data class.
- `src/main/kotlin/postmannen/model/Tab.kt` — new, `Tab` enum
  (`COLLECTIONS`, `ENVIRONMENTS`).
- `src/main/kotlin/postmannen/model/AppState.kt` — modify, add
  `environments` and `activeTab` fields.
- `src/main/kotlin/postmannen/service/PostmanApiService.kt` — modify, add
  `getEnvironments` to the interface.
- `src/main/kotlin/postmannen/service/PostmanApiServiceImpl.kt` — modify,
  implement `getEnvironments`.
- `src/main/kotlin/postmannen/viewmodel/AppViewModel.kt` — modify, add
  `loadEnvironments`, `setActiveTab`, wire eager loading.
- `src/main/kotlin/postmannen/ui/App.kt` — modify, tab-bar UI + arrow-key
  switching + key-hint bar update.
- `src/test/kotlin/postmannen/service/FakePostmanApiService.kt` — modify,
  add `environmentsResult` / `FIXTURE_ENVIRONMENTS`.
- `src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt` — modify, add
  environment-related test cases.

---

### Task 1: Environment domain model, service interface, and fake service

**Files:**
- Create: `src/main/kotlin/postmannen/model/Environment.kt`
- Modify: `src/main/kotlin/postmannen/service/PostmanApiService.kt`
- Modify: `src/test/kotlin/postmannen/service/FakePostmanApiService.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `data class Environment(val id: String, val name: String)`;
  `PostmanApiService.getEnvironments(workspaceId: String):
  Result<List<Environment>>`; `FakePostmanApiService.environmentsResult`
  (mutable, defaults to `Result.success(FIXTURE_ENVIRONMENTS)`) and
  `FIXTURE_ENVIRONMENTS` companion list — consumed by `AppViewModelTest`
  in Task 2.

- [ ] **Step 1: Create the `Environment` domain model**

```kotlin
// src/main/kotlin/postmannen/model/Environment.kt
package postmannen.model

data class Environment(val id: String, val name: String)
```

- [ ] **Step 2: Add `getEnvironments` to the service interface**

Modify `src/main/kotlin/postmannen/service/PostmanApiService.kt` to:

```kotlin
package postmannen.service

import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.Workspace

interface PostmanApiService {
    suspend fun getWorkspaces(): Result<List<Workspace>>
    suspend fun getCollections(workspaceId: String): Result<List<Collection>>
    suspend fun getEnvironments(workspaceId: String): Result<List<Environment>>
}
```

- [ ] **Step 3: Extend the fake service**

Modify `src/test/kotlin/postmannen/service/FakePostmanApiService.kt` to:

```kotlin
package postmannen.service

import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.Workspace

class FakePostmanApiService : PostmanApiService {
    var workspacesResult: Result<List<Workspace>> = Result.success(FIXTURE_WORKSPACES)
    var collectionsResult: Result<List<Collection>> = Result.success(FIXTURE_COLLECTIONS)
    var environmentsResult: Result<List<Environment>> = Result.success(FIXTURE_ENVIRONMENTS)
    var lastRequestedWorkspaceId: String? = null

    override suspend fun getWorkspaces(): Result<List<Workspace>> = workspacesResult

    override suspend fun getCollections(workspaceId: String): Result<List<Collection>> {
        lastRequestedWorkspaceId = workspaceId
        return collectionsResult
    }

    override suspend fun getEnvironments(workspaceId: String): Result<List<Environment>> {
        lastRequestedWorkspaceId = workspaceId
        return environmentsResult
    }

    companion object {
        val FIXTURE_WORKSPACES = listOf(
            Workspace(id = "ws-1", name = "Engineering", type = "team"),
            Workspace(id = "ws-2", name = "Personal", type = "personal")
        )
        val FIXTURE_COLLECTIONS = listOf(
            Collection(id = "col-1", name = "Auth API"),
            Collection(id = "col-2", name = "Billing API")
        )
        val FIXTURE_ENVIRONMENTS = listOf(
            Environment(id = "env-1", name = "Staging"),
            Environment(id = "env-2", name = "Production")
        )
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileTestKotlin`
Expected: `BUILD SUCCESSFUL`

Note: this will show unresolved-reference errors until Task 2 implements
`getEnvironments` on the real `PostmanApiServiceImpl` — if
`compileTestKotlin` fails specifically because `PostmanApiServiceImpl`
doesn't yet override the new interface method, that's expected at this
point; `compileKotlin` (main sourceset only) should still succeed since
`PostmanApiServiceImpl` lives in main, not test. Run `./gradlew
compileKotlin` instead if `compileTestKotlin` fails for that specific
reason, and note it in your report — Task 3 fixes it.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/postmannen/model/Environment.kt src/main/kotlin/postmannen/service/PostmanApiService.kt src/test/kotlin/postmannen/service/FakePostmanApiService.kt
git commit -m "feat: add Environment model, service interface method, and fake service support"
```

---

### Task 2: Tab enum, AppState, and AppViewModel (TDD)

**Files:**
- Create: `src/main/kotlin/postmannen/model/Tab.kt`
- Modify: `src/main/kotlin/postmannen/model/AppState.kt`
- Modify: `src/main/kotlin/postmannen/viewmodel/AppViewModel.kt`
- Modify: `src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt`

**Interfaces:**
- Consumes: `Environment`, `PostmanApiService.getEnvironments`,
  `FakePostmanApiService.environmentsResult`/`FIXTURE_ENVIRONMENTS` (all
  Task 1).
- Produces: `enum class Tab { COLLECTIONS, ENVIRONMENTS }`; `AppState`
  gains `environments: List<Environment> = emptyList()` and `activeTab:
  Tab = Tab.COLLECTIONS`; `AppViewModel` gains `fun
  loadEnvironments(workspaceId: String)` and `fun setActiveTab(tab:
  Tab)` — consumed by `App` (Task 4).

- [ ] **Step 1: Create the `Tab` enum**

```kotlin
// src/main/kotlin/postmannen/model/Tab.kt
package postmannen.model

enum class Tab { COLLECTIONS, ENVIRONMENTS }
```

- [ ] **Step 2: Update `AppState`**

Modify `src/main/kotlin/postmannen/model/AppState.kt` to:

```kotlin
package postmannen.model

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

- [ ] **Step 3: Write the failing ViewModel tests**

Add these test cases to `src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt`
(inside the existing `AppViewModelTest` class, alongside the existing
tests — do not remove any existing test):

```kotlin
    @Test
    fun `loadWorkspaces also loads environments for the first workspace`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals(FakePostmanApiService.FIXTURE_ENVIRONMENTS, vm.state.value.environments)
    }

    @Test
    fun `selectWorkspace reloads environments too`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        fake.environmentsResult = Result.success(listOf(postmannen.model.Environment(id = "env-3", name = "QA")))
        vm.selectWorkspace(1)
        advanceUntilIdle()
        assertEquals(listOf(postmannen.model.Environment(id = "env-3", name = "QA")), vm.state.value.environments)
    }

    @Test
    fun `loadEnvironments on failure sets status message and preserves existing list`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        fake.environmentsResult = Result.failure(RuntimeException("env fetch error"))
        vm.loadEnvironments("ws-1")
        advanceUntilIdle()
        assertTrue(vm.state.value.statusMessage.contains("env fetch error"))
        assertEquals(FakePostmanApiService.FIXTURE_ENVIRONMENTS, vm.state.value.environments)
    }

    @Test
    fun `setActiveTab updates activeTab without calling the service`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.setActiveTab(postmannen.model.Tab.ENVIRONMENTS)
        assertEquals(postmannen.model.Tab.ENVIRONMENTS, vm.state.value.activeTab)
        assertEquals(null, fake.lastRequestedWorkspaceId)
    }
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew test --tests "postmannen.viewmodel.AppViewModelTest"`
Expected: FAIL (compile error — `loadEnvironments`/`setActiveTab` not
defined on `AppViewModel`, `Tab` not imported/used correctly)

- [ ] **Step 5: Update `AppViewModel`**

Modify `src/main/kotlin/postmannen/viewmodel/AppViewModel.kt` to:

```kotlin
package postmannen.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import postmannen.model.AppState
import postmannen.model.Tab
import postmannen.service.PostmanApiService

class AppViewModel(
    private val service: PostmanApiService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun update(block: AppState.() -> AppState) = _state.update(block)

    fun loadWorkspaces() {
        scope.launch {
            update { copy(loading = true) }
            service.getWorkspaces()
                .onSuccess { workspaces ->
                    update { copy(workspaces = workspaces, loading = false, selectedWorkspaceIndex = 0) }
                    workspaces.firstOrNull()?.let {
                        loadCollections(it.id)
                        loadEnvironments(it.id)
                    }
                }
                .onFailure { e ->
                    update { copy(loading = false, statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun selectWorkspace(index: Int) {
        val workspace = _state.value.workspaces.getOrNull(index) ?: return
        update { copy(selectedWorkspaceIndex = index) }
        loadCollections(workspace.id)
        loadEnvironments(workspace.id)
    }

    fun loadCollections(workspaceId: String) {
        scope.launch {
            update { copy(loading = true) }
            service.getCollections(workspaceId)
                .onSuccess { collections ->
                    update { copy(collections = collections, loading = false) }
                }
                .onFailure { e ->
                    update { copy(loading = false, statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun loadEnvironments(workspaceId: String) {
        scope.launch {
            update { copy(loading = true) }
            service.getEnvironments(workspaceId)
                .onSuccess { environments ->
                    update { copy(environments = environments, loading = false) }
                }
                .onFailure { e ->
                    update { copy(loading = false, statusMessage = "Error: ${e.message}") }
                }
        }
    }

    fun setActiveTab(tab: Tab) {
        update { copy(activeTab = tab) }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "postmannen.viewmodel.AppViewModelTest"`
Expected: PASS (11 tests — the 7 existing plus the 4 new ones)

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/postmannen/model/Tab.kt src/main/kotlin/postmannen/model/AppState.kt src/main/kotlin/postmannen/viewmodel/AppViewModel.kt src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt
git commit -m "feat: add Tab enum, extend AppState, load environments in AppViewModel"
```

---

### Task 3: Real `getEnvironments` implementation

**Files:**
- Modify: `src/main/kotlin/postmannen/service/PostmanApiServiceImpl.kt`

**Interfaces:**
- Consumes: `PostmanApiService.getEnvironments` (Task 1), `Environment`
  (Task 1).
- Produces: `PostmanApiServiceImpl.getEnvironments` override — no
  downstream consumers beyond satisfying the interface (already wired
  through `AppViewModel` in Task 2, real end-to-end path completes here).

- [ ] **Step 1: Add the `getEnvironments` override and its DTOs**

Modify `src/main/kotlin/postmannen/service/PostmanApiServiceImpl.kt`.
Add the import and the new method to the class, and the new DTOs at file
scope:

```kotlin
import postmannen.model.Environment
```

Add inside the `PostmanApiServiceImpl` class, alongside the existing
`getCollections`:

```kotlin
    override suspend fun getEnvironments(workspaceId: String): Result<List<Environment>> = runCatching {
        val response: EnvironmentsResponse =
            client.get {
                url {
                    appendPathSegments("environments")
                    parameters.append("workspace", workspaceId)
                }
            }.body()
        response.environments.map { Environment(id = it.id, name = it.name) }
    }
```

Add at file scope, alongside the existing private DTOs:

```kotlin
@Serializable
private data class EnvironmentsResponse(val environments: List<EnvironmentDto>)

@Serializable
private data class EnvironmentDto(val id: String, val name: String, val uid: String)
```

- [ ] **Step 2: Verify the full build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (all unit tests pass, including the 4 new
ones from Task 2; the existing `PostmanApiServiceImplTest` integration
test still self-skips without `POSTMAN_API_KEY`).

If `parameters.append` isn't the right Ktor 3.1.0 call for adding a query
parameter inside a `url { }` block, fix it to whatever the actual API is
(e.g. `parameter("workspace", workspaceId)` at the request-builder level
instead of inside `url { }`) — the endpoint path (`environments`), query
key (`workspace`), and response shape (`{"environments": [{"id",
"name", "uid"}]}`) are the parts that were externally verified and must
not change.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/postmannen/service/PostmanApiServiceImpl.kt
git commit -m "feat: implement getEnvironments against the Postman REST API"
```

---

### Task 4: UI — tabs, arrow-key switching, key hints

**Files:**
- Modify: `src/main/kotlin/postmannen/ui/App.kt`

**Interfaces:**
- Consumes: `AppState.environments`/`activeTab` (Task 2),
  `AppViewModel.setActiveTab` (Task 2), `Tab` enum (Task 2).
- Produces: a runnable TUI with two tabs. Terminal entry point only — no
  downstream consumers.

- [ ] **Step 1: Rewrite `App.kt`**

Replace the full contents of `src/main/kotlin/postmannen/ui/App.kt` with:

```kotlin
package postmannen.ui

import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import postmannen.model.AppState
import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.Tab
import postmannen.model.Workspace
import postmannen.viewmodel.AppViewModel
import java.util.concurrent.atomic.AtomicBoolean

class App(
    private val gui: MultiWindowTextGUI,
    private val screen: Screen,
    private val viewModel: AppViewModel,
    private val scope: CoroutineScope
) {
    private val workspaceDropdown = WorkspaceDropdown()
    private val tabBar = Label("")
    private val itemListBox = ActionListBox()
    private val statusBar = StatusBar()
    private val window = BasicWindow("postmannen")

    fun run() {
        window.setHints(setOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))

        val root = Panel(BorderLayout())
        root.addComponent(workspaceDropdown, BorderLayout.Location.TOP)

        val centerPanel = Panel(LinearLayout(Direction.VERTICAL))
        centerPanel.addComponent(tabBar)
        centerPanel.addComponent(itemListBox)
        root.addComponent(centerPanel.withBorder(Borders.singleLine()), BorderLayout.Location.CENTER)

        val bottomPanel = Panel(LinearLayout(Direction.VERTICAL))
        bottomPanel.addComponent(statusBar)
        bottomPanel.addComponent(Label("  [←][→] tabs  q-quit"))
        root.addComponent(bottomPanel, BorderLayout.Location.BOTTOM)

        window.component = root

        var applyingState = false
        workspaceDropdown.addListener { selectedIndex, _, changedByUserInteraction ->
            if (changedByUserInteraction && !applyingState) {
                viewModel.selectWorkspace(selectedIndex)
            }
        }

        window.addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, keyStroke: KeyStroke, hasBeenHandled: AtomicBoolean) {
                when {
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'q' -> {
                        window.close()
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.ArrowLeft || keyStroke.keyType == KeyType.ArrowRight -> {
                        val next = if (viewModel.state.value.activeTab == Tab.COLLECTIONS) Tab.ENVIRONMENTS else Tab.COLLECTIONS
                        viewModel.setActiveTab(next)
                        hasBeenHandled.set(true)
                    }
                }
            }
        })

        scope.launch {
            viewModel.state.collect { state ->
                synchronized(gui) {
                    applyingState = true
                    applyState(state)
                    applyingState = false
                    try { gui.updateScreen() } catch (_: Exception) {}
                }
            }
        }

        gui.addWindowAndWait(window)
    }

    private var lastWorkspaces: List<Workspace> = emptyList()
    private var lastCollections: List<Collection> = emptyList()
    private var lastEnvironments: List<Environment> = emptyList()
    private var lastActiveTab: Tab? = null

    private fun applyState(state: AppState) {
        if (state.workspaces != lastWorkspaces) {
            lastWorkspaces = state.workspaces
            workspaceDropdown.clearItems()
            state.workspaces.forEach { workspaceDropdown.addItem(it) }
        }
        if (state.workspaces.isNotEmpty()) {
            workspaceDropdown.selectedIndex = state.selectedWorkspaceIndex.coerceIn(0, state.workspaces.size - 1)
        }

        tabBar.text = buildTabBar(state.activeTab)

        val itemsChanged = state.collections != lastCollections || state.environments != lastEnvironments
        val tabChanged = state.activeTab != lastActiveTab
        if (itemsChanged || tabChanged) {
            lastCollections = state.collections
            lastEnvironments = state.environments
            lastActiveTab = state.activeTab
            itemListBox.clearItems()
            val names = if (state.activeTab == Tab.COLLECTIONS) {
                state.collections.map { it.name }
            } else {
                state.environments.map { it.name }
            }
            names.forEach { name -> itemListBox.addItem(name) {} }
        }

        statusBar.setText(if (state.loading) "Loading..." else state.statusMessage)
    }

    private fun buildTabBar(active: Tab): String {
        val collectionsLabel = if (active == Tab.COLLECTIONS) "[Collections]" else " Collections "
        val environmentsLabel = if (active == Tab.ENVIRONMENTS) "[Environments]" else " Environments "
        return " $collectionsLabel  $environmentsLabel"
    }
}
```

- [ ] **Step 2: Verify the full build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Manual smoke test**

Run: `POSTMAN_API_KEY=<your key> ./gradlew run`
Expected: the CENTER panel now shows a tab bar (`[Collections]
Environments`) above the item list. Pressing left or right arrow toggles
the bracketed tab and repopulates the list with collection names or
environment names for the currently selected workspace. Changing the
workspace dropdown reloads both lists; whichever tab is active shows the
new workspace's data immediately, the other tab's data is ready when you
switch to it. The bottom key-hint line reads `[←][→] tabs  q-quit`.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/postmannen/ui/App.kt
git commit -m "feat: add Collections/Environments tabs with arrow-key switching"
```

---

## Self-Review Notes

- Spec coverage: data model + service (Task 1, Task 3), state/viewmodel +
  eager loading (Task 2), UI tabs + arrow-key switching + key hints
  (Task 4), error handling (Task 2 tests), testing strategy (Task 1 fake
  service, Task 2 ViewModel tests) — every section of the spec maps to a
  task.
- Type consistency checked: `Environment(id, name)` matches across
  Task 1's model, Task 1's fake, Task 2's tests, Task 3's real impl, and
  Task 4's UI usage (`.name` only, no drilldown). `Tab` enum values
  (`COLLECTIONS`, `ENVIRONMENTS`) used identically in Task 2 and Task 4.
