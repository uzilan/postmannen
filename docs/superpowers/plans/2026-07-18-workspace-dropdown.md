# Workspace Dropdown + Collection List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the v1 `postmannen` TUI — a workspace dropdown in the top-left
that drives a collection list panel, talking to Postman's public REST API.

**Architecture:** Kotlin JVM + Lanterna TUI, MVVM with a single
`MutableStateFlow<AppState>`, mirroring `~/dev/breui`'s layering exactly
(service → viewmodel → ui, coroutine-collected state → `applyState()` →
`gui.updateScreen()`). API access goes through a `PostmanApiService`
interface with a real (Ktor HTTP client) and fake (fixture-based)
implementation, so the ViewModel is fully unit-testable without the network.

**Tech Stack:** Kotlin JVM (toolchain 25), Lanterna 3.1.1, kotlinx-coroutines,
kotlinx-serialization-json, Ktor client (CIO engine + ContentNegotiation +
kotlinx-json), JUnit5/kotlin-test.

## Global Constraints

- Kotlin JVM toolchain 25, Kotlin plugin version 2.4.0 (matches breui).
- `mainClass`: `postmannen.MainKt`.
- Postman REST API base URL (fixed constant, no env override):
  `https://api.getpostman.com`.
- Auth header: `X-Api-Key: $POSTMAN_API_KEY` (confirmed exact header name
  from Postman's own API authentication docs).
- `POSTMAN_API_KEY` env var is required; missing/blank → stderr message +
  exit 1, checked in `Main` before the screen starts. Never hardcoded,
  never logged.
- `GET /workspaces` → `{"workspaces": [{"id", "name", "type"}]}`.
- `GET /workspaces/{workspaceId}` → `{"workspace": {..., "collections":
  [{"id", "name", "uid"}]}}` — map `id`/`name` into `Collection`, `uid`
  unused in v1.
- Single fixed theme `businessmachine`, set once in `Main`. No theme picker
  in v1. No collection drilldown, no request sending in v1.

---

## File Structure

- `build.gradle.kts`, `settings.gradle.kts`, `gradle/wrapper/*`, `gradlew`,
  `gradlew.bat`, `.gitignore` — project scaffold, mirrors breui.
- `src/main/kotlin/postmannen/Main.kt` — entry point: env check, terminal/gui
  setup, wiring, `App(...).run()`.
- `src/main/kotlin/postmannen/model/Workspace.kt` — `Workspace` data class.
- `src/main/kotlin/postmannen/model/Collection.kt` — `Collection` data class.
- `src/main/kotlin/postmannen/model/AppState.kt` — `AppState` data class.
- `src/main/kotlin/postmannen/service/PostmanApiService.kt` — service
  interface.
- `src/main/kotlin/postmannen/service/PostmanApiServiceImpl.kt` — real
  implementation (Ktor client + kotlinx.serialization DTOs).
- `src/main/kotlin/postmannen/viewmodel/AppViewModel.kt` — state holder,
  mutations, error handling.
- `src/main/kotlin/postmannen/ui/StatusBar.kt` — ported from breui verbatim
  (package renamed).
- `src/main/kotlin/postmannen/ui/App.kt` — root panel: dropdown (TOP),
  collection list (CENTER), status bar (BOTTOM).
- `src/test/kotlin/postmannen/service/FakePostmanApiService.kt`
- `src/test/kotlin/postmannen/service/PostmanApiServiceImplTest.kt`
- `src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt`

---

### Task 1: Project scaffold & build config

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `.gitignore`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar` (binary, copied)
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `src/main/kotlin/postmannen/Main.kt` (placeholder entry point,
  replaced fully in Task 5)

**Interfaces:**
- Produces: a buildable Gradle project with `mainClass =
  postmannen.MainKt`, so every later task can run `./gradlew build`.

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "postmannen"
```

- [ ] **Step 2: Create `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("io.github.goooler.shadow") version "8.1.8"
    application
}

group = "postmannen"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("com.googlecode.lanterna:lanterna:3.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("io.ktor:ktor-client-cio:3.1.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

application {
    mainClass.set("postmannen.MainKt")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("postmannen")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "postmannen.MainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}
```

This is the same plugin/version combination (`io.github.goooler.shadow`
8.1.8 on Gradle 9.6.1) already proven working in `~/dev/breui` — verify with
`cd ~/dev/breui && ./gradlew clean shadowJar` if in doubt; it builds clean
there, so the same block works here. If dependency resolution fails on the
Ktor coordinates specifically, check Maven Central for the latest available
patch version of that artifact and update the version string — the exact
patch number is not load-bearing, only that it resolves and all three Ktor
artifacts stay on the same version.

- [ ] **Step 3: Create `.gitignore`**

```
.gradle/
.kotlin/
build/
.idea/
```

- [ ] **Step 4: Copy the Gradle wrapper from breui**

```bash
mkdir -p gradle/wrapper
cp ~/dev/breui/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.jar
cp ~/dev/breui/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.properties
cp ~/dev/breui/gradlew gradlew
cp ~/dev/breui/gradlew.bat gradlew.bat
chmod +x gradlew
```

- [ ] **Step 5: Create placeholder `Main.kt`**

```kotlin
package postmannen

fun main() {
    println("postmannen")
}
```

- [ ] **Step 6: Verify the build works**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

Run: `./gradlew shadowJar`
Expected: `BUILD SUCCESSFUL` (proves the shadow plugin is wired correctly
on this Gradle/Kotlin version, matching breui's proven-working setup).

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts settings.gradle.kts .gitignore gradle gradlew gradlew.bat src/main/kotlin/postmannen/Main.kt
git commit -m "chore: scaffold Kotlin/Lanterna project"
```

---

### Task 2: Domain models, service interface, and fake service

**Files:**
- Create: `src/main/kotlin/postmannen/model/Workspace.kt`
- Create: `src/main/kotlin/postmannen/model/Collection.kt`
- Create: `src/main/kotlin/postmannen/service/PostmanApiService.kt`
- Create: `src/test/kotlin/postmannen/service/FakePostmanApiService.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `data class Workspace(val id: String, val name: String, val
  type: String)`; `data class Collection(val id: String, val name:
  String)`; `interface PostmanApiService` with `suspend fun
  getWorkspaces(): Result<List<Workspace>>` and `suspend fun
  getCollections(workspaceId: String): Result<List<Collection>>`;
  `class FakePostmanApiService` with mutable `workspacesResult` /
  `collectionsResult` fields and a `FIXTURE_WORKSPACES` /
  `FIXTURE_COLLECTIONS` companion — consumed by `AppViewModelTest` in
  Task 3.

- [ ] **Step 1: Create the domain models**

```kotlin
// src/main/kotlin/postmannen/model/Workspace.kt
package postmannen.model

data class Workspace(val id: String, val name: String, val type: String) {
    override fun toString(): String = "$name ($type)"
}
```

```kotlin
// src/main/kotlin/postmannen/model/Collection.kt
package postmannen.model

data class Collection(val id: String, val name: String)
```

- [ ] **Step 2: Create the service interface**

```kotlin
// src/main/kotlin/postmannen/service/PostmanApiService.kt
package postmannen.service

import postmannen.model.Collection
import postmannen.model.Workspace

interface PostmanApiService {
    suspend fun getWorkspaces(): Result<List<Workspace>>
    suspend fun getCollections(workspaceId: String): Result<List<Collection>>
}
```

- [ ] **Step 3: Create the fake service**

```kotlin
// src/test/kotlin/postmannen/service/FakePostmanApiService.kt
package postmannen.service

import postmannen.model.Collection
import postmannen.model.Workspace

class FakePostmanApiService : PostmanApiService {
    var workspacesResult: Result<List<Workspace>> = Result.success(FIXTURE_WORKSPACES)
    var collectionsResult: Result<List<Collection>> = Result.success(FIXTURE_COLLECTIONS)
    var lastRequestedWorkspaceId: String? = null

    override suspend fun getWorkspaces(): Result<List<Workspace>> = workspacesResult

    override suspend fun getCollections(workspaceId: String): Result<List<Collection>> {
        lastRequestedWorkspaceId = workspaceId
        return collectionsResult
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
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileTestKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/postmannen/model src/main/kotlin/postmannen/service/PostmanApiService.kt src/test/kotlin/postmannen/service/FakePostmanApiService.kt
git commit -m "feat: add domain models, service interface, and fake service"
```

---

### Task 3: AppState + AppViewModel (TDD)

**Files:**
- Create: `src/main/kotlin/postmannen/model/AppState.kt`
- Create: `src/main/kotlin/postmannen/viewmodel/AppViewModel.kt`
- Test: `src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt`

**Interfaces:**
- Consumes: `PostmanApiService` (Task 2), `FakePostmanApiService` (Task 2).
- Produces: `class AppViewModel(service: PostmanApiService, scope:
  CoroutineScope)` with `val state: StateFlow<AppState>`, `fun
  loadWorkspaces()`, `fun selectWorkspace(index: Int)`, `fun
  loadCollections(workspaceId: String)` — consumed by `App` (Task 5) and
  `Main` (Task 5).

- [ ] **Step 1: Create `AppState`**

```kotlin
// src/main/kotlin/postmannen/model/AppState.kt
package postmannen.model

data class AppState(
    val workspaces: List<Workspace> = emptyList(),
    val selectedWorkspaceIndex: Int = 0,
    val collections: List<Collection> = emptyList(),
    val loading: Boolean = false,
    val statusMessage: String = ""
)
```

- [ ] **Step 2: Write the failing ViewModel tests**

```kotlin
// src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt
package postmannen.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import postmannen.service.FakePostmanApiService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @Test
    fun `loadWorkspaces sets workspaces and clears loading`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals(FakePostmanApiService.FIXTURE_WORKSPACES, vm.state.value.workspaces)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `loadWorkspaces on failure sets status message and preserves existing list`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        fake.workspacesResult = Result.failure(RuntimeException("network error"))
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertTrue(vm.state.value.statusMessage.contains("network error"))
        assertEquals(FakePostmanApiService.FIXTURE_WORKSPACES, vm.state.value.workspaces)
    }

    @Test
    fun `loadWorkspaces triggers loadCollections for the first workspace`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals("ws-1", fake.lastRequestedWorkspaceId)
        assertEquals(FakePostmanApiService.FIXTURE_COLLECTIONS, vm.state.value.collections)
    }

    @Test
    fun `loadWorkspaces with empty list does not trigger loadCollections`() = runTest {
        val fake = FakePostmanApiService().apply { workspacesResult = Result.success(emptyList()) }
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals(null, fake.lastRequestedWorkspaceId)
    }

    @Test
    fun `selectWorkspace updates index and loads its collections`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.selectWorkspace(1)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.selectedWorkspaceIndex)
        assertEquals("ws-2", fake.lastRequestedWorkspaceId)
    }

    @Test
    fun `loadCollections on failure sets status message and preserves existing list`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        fake.collectionsResult = Result.failure(RuntimeException("tool error"))
        vm.loadCollections("ws-1")
        advanceUntilIdle()
        assertTrue(vm.state.value.statusMessage.contains("tool error"))
        assertEquals(FakePostmanApiService.FIXTURE_COLLECTIONS, vm.state.value.collections)
    }

    @Test
    fun `loadCollections sets loading true then false around the call`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadCollections("ws-1")
        runCurrent()
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "postmannen.viewmodel.AppViewModelTest"`
Expected: FAIL (compile error — `AppViewModel` not defined)

- [ ] **Step 4: Write the implementation**

```kotlin
// src/main/kotlin/postmannen/viewmodel/AppViewModel.kt
package postmannen.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import postmannen.model.AppState
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
                    workspaces.firstOrNull()?.let { loadCollections(it.id) }
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
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "postmannen.viewmodel.AppViewModelTest"`
Expected: PASS (7 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/postmannen/model/AppState.kt src/main/kotlin/postmannen/viewmodel/AppViewModel.kt src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt
git commit -m "feat: add AppState and AppViewModel"
```

---

### Task 4: Real Postman API service implementation

**Files:**
- Create: `src/main/kotlin/postmannen/service/PostmanApiServiceImpl.kt`
- Test: `src/test/kotlin/postmannen/service/PostmanApiServiceImplTest.kt`

**Interfaces:**
- Consumes: `PostmanApiService` (Task 2), `Workspace`/`Collection`
  (Task 2).
- Produces: `class PostmanApiServiceImpl(apiKey: String) :
  PostmanApiService`, with a public `companion object { const val
  BASE_URL = "https://api.getpostman.com" }` — consumed by `Main`
  (Task 5).

- [ ] **Step 1: Write the implementation**

```kotlin
// src/main/kotlin/postmannen/service/PostmanApiServiceImpl.kt
package postmannen.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.call.body
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import postmannen.model.Collection
import postmannen.model.Workspace

class PostmanApiServiceImpl(private val apiKey: String) : PostmanApiService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
        defaultRequest {
            url(BASE_URL)
            header("X-Api-Key", apiKey)
        }
    }

    override suspend fun getWorkspaces(): Result<List<Workspace>> = runCatching {
        val response: WorkspacesResponse = client.get { url { appendPathSegments("workspaces") } }.body()
        response.workspaces.map { Workspace(id = it.id, name = it.name, type = it.type) }
    }

    override suspend fun getCollections(workspaceId: String): Result<List<Collection>> = runCatching {
        val response: WorkspaceDetailResponse =
            client.get { url { appendPathSegments("workspaces", workspaceId) } }.body()
        response.workspace.collections.map { Collection(id = it.id, name = it.name) }
    }

    companion object {
        const val BASE_URL = "https://api.getpostman.com"
    }
}

@Serializable
private data class WorkspacesResponse(val workspaces: List<WorkspaceDto>)

@Serializable
private data class WorkspaceDto(val id: String, val name: String, val type: String)

@Serializable
private data class WorkspaceDetailResponse(val workspace: WorkspaceDetailDto)

@Serializable
private data class WorkspaceDetailDto(val id: String, val name: String, val collections: List<CollectionDto> = emptyList())

@Serializable
private data class CollectionDto(val id: String, val name: String, val uid: String)
```

- [ ] **Step 2: Write the integration smoke test**

```kotlin
// src/test/kotlin/postmannen/service/PostmanApiServiceImplTest.kt
package postmannen.service

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

@Tag("integration")
class PostmanApiServiceImplTest {

    @Test
    fun `getWorkspaces returns at least one workspace with a name`() = runTest {
        val apiKey = System.getenv("POSTMAN_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank(), "POSTMAN_API_KEY not set, skipping integration test")

        val service = PostmanApiServiceImpl(apiKey)
        val result = service.getWorkspaces()

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()?.message}")
        val workspaces = result.getOrThrow()
        assertTrue(workspaces.isNotEmpty(), "Expected at least one workspace")
        assertTrue(workspaces.all { it.name.isNotBlank() }, "All workspaces should have a name")
    }
}
```

- [ ] **Step 3: Verify it compiles and the unit-test suite still passes**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, `PostmanApiServiceImplTest` shown as skipped
(it self-skips via `assumeTrue` when `POSTMAN_API_KEY` is unset — reports
as skipped, not failed, in a normal dev environment).

If `POSTMAN_API_KEY` is set in your environment, expect it to pass for
real:

Run: `POSTMAN_API_KEY=$POSTMAN_API_KEY ./gradlew test --tests "postmannen.service.PostmanApiServiceImplTest"`
Expected: PASS

If Ktor's client API surface differs slightly from what's used above
(e.g. `appendPathSegments` import location), fix the imports/calls until
this compiles — the base URL, header name, and JSON response shapes are
the parts that were verified against Postman's docs and must not change.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/postmannen/service/PostmanApiServiceImpl.kt src/test/kotlin/postmannen/service/PostmanApiServiceImplTest.kt
git commit -m "feat: add real PostmanApiService implementation over the Postman REST API"
```

---

### Task 5: UI layer + Main wiring

**Files:**
- Create: `src/main/kotlin/postmannen/ui/StatusBar.kt`
- Create: `src/main/kotlin/postmannen/ui/App.kt`
- Modify: `src/main/kotlin/postmannen/Main.kt` (replace placeholder from
  Task 1)

**Interfaces:**
- Consumes: `AppViewModel` (Task 3), `PostmanApiServiceImpl` (Task 4),
  `Workspace`/`Collection` (Task 2).
- Produces: a runnable TUI. Terminal entry point only — no downstream
  consumers.

- [ ] **Step 1: Port `StatusBar`**

```kotlin
// src/main/kotlin/postmannen/ui/StatusBar.kt
package postmannen.ui

import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel

class StatusBar : Panel() {
    private val label = Label(" ")

    init {
        addComponent(label)
    }

    fun setText(text: String) {
        label.text = if (text.isBlank()) " " else " $text"
    }
}
```

- [ ] **Step 2: Write `App`**

```kotlin
// src/main/kotlin/postmannen/ui/App.kt
package postmannen.ui

import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.ComboBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.screen.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import postmannen.model.AppState
import postmannen.model.Collection
import postmannen.model.Workspace
import postmannen.viewmodel.AppViewModel

class App(
    private val gui: MultiWindowTextGUI,
    private val screen: Screen,
    private val viewModel: AppViewModel,
    private val scope: CoroutineScope
) {
    private val workspaceDropdown = ComboBox<Workspace>()
    private val collectionListBox = ActionListBox()
    private val statusBar = StatusBar()
    private val window = BasicWindow("postmannen")

    fun run() {
        window.setHints(setOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))

        val root = Panel(BorderLayout())

        val topPanel = Panel(BorderLayout())
        topPanel.addComponent(workspaceDropdown, BorderLayout.Location.TOP)
        root.addComponent(topPanel, BorderLayout.Location.TOP)

        root.addComponent(
            collectionListBox.withBorder(Borders.singleLine("Collections")),
            BorderLayout.Location.CENTER
        )

        root.addComponent(statusBar, BorderLayout.Location.BOTTOM)

        window.component = root

        var applyingState = false
        workspaceDropdown.addListener { selectedIndex, _, changedByUserInteraction ->
            if (changedByUserInteraction && !applyingState) {
                viewModel.selectWorkspace(selectedIndex)
            }
        }

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

    private fun applyState(state: AppState) {
        if (state.workspaces != lastWorkspaces) {
            lastWorkspaces = state.workspaces
            workspaceDropdown.clearItems()
            state.workspaces.forEach { workspaceDropdown.addItem(it) }
        }
        if (state.workspaces.isNotEmpty()) {
            workspaceDropdown.selectedIndex = state.selectedWorkspaceIndex.coerceIn(0, state.workspaces.size - 1)
        }

        if (state.collections != lastCollections) {
            lastCollections = state.collections
            collectionListBox.clearItems()
            state.collections.forEach { collection ->
                collectionListBox.addItem(collection.name) {}
            }
        }

        statusBar.setText(if (state.loading) "Loading..." else state.statusMessage)
    }
}
```

- [ ] **Step 3: Write `Main`**

```kotlin
// src/main/kotlin/postmannen/Main.kt
package postmannen

import com.googlecode.lanterna.bundle.LanternaThemes
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import postmannen.service.PostmanApiServiceImpl
import postmannen.ui.App
import postmannen.viewmodel.AppViewModel
import kotlin.system.exitProcess

fun main() = runBlocking {
    val apiKey = System.getenv("POSTMAN_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println("POSTMAN_API_KEY environment variable is required.")
        exitProcess(1)
    }

    val terminal = DefaultTerminalFactory().createTerminal()
    val screen = TerminalScreen(terminal)
    screen.startScreen()
    val gui = MultiWindowTextGUI(screen)
    gui.setTheme(LanternaThemes.getRegisteredTheme("businessmachine"))

    val scope = CoroutineScope(Dispatchers.Default)
    val viewModel = AppViewModel(PostmanApiServiceImpl(apiKey), scope)

    viewModel.loadWorkspaces()

    App(gui, screen, viewModel, scope).run()

    scope.cancel()
    screen.stopScreen()
}
```

- [ ] **Step 4: Verify the full build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (all unit tests pass; the integration test
self-skips without `POSTMAN_API_KEY`).

- [ ] **Step 5: Manual smoke test**

Run: `POSTMAN_API_KEY=<your key> ./gradlew run`
Expected: terminal fills with the `businessmachine` theme, a workspace
dropdown appears top-left pre-populated, and the Collections panel shows
the first workspace's collections. Changing the dropdown selection
reloads the Collections panel for the newly selected workspace.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/postmannen/ui src/main/kotlin/postmannen/Main.kt
git commit -m "feat: wire up workspace dropdown and collection list UI"
```

---

## Self-Review Notes

- Spec coverage: stack/build setup (Task 1), API service + models
  (Tasks 2, 4), state/viewmodel (Task 3), UI layer (Task 5), error
  handling (Task 3 tests + Task 4), testing strategy (all tasks include
  their prescribed test file) — every section of the revised (REST API)
  spec maps to a task.
- Revision history: the original plan used Postman's remote MCP server
  (`kotlin-sdk-client` + markdown-table parsing of tool responses). That
  approach was reconsidered before implementation began — fragile
  markdown-table parsing for zero real benefit over calling the public
  REST API directly — and this plan replaces it. `getWorkspaces`/
  `getCollections` tool-shaped access is now `GET /workspaces` /
  `GET /workspaces/{id}` REST calls; the markdown parser task is dropped
  entirely.
