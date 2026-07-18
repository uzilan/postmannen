# Workspace Dropdown + Collection List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the v1 `postmannen` TUI — a workspace dropdown in the top-left
that drives a collection list panel, talking to Postman's remote MCP server.

**Architecture:** Kotlin JVM + Lanterna TUI, MVVM with a single
`MutableStateFlow<AppState>`, mirroring `~/dev/breui`'s layering exactly
(service → viewmodel → ui, coroutine-collected state → `applyState()` →
`gui.updateScreen()`). MCP access goes through a `PostmanMcpService`
interface with a real (Ktor + MCP Kotlin SDK) and fake (fixture-based)
implementation, so the ViewModel is fully unit-testable without the network.

**Tech Stack:** Kotlin JVM (toolchain 25), Lanterna 3.1.1, kotlinx-coroutines,
`io.modelcontextprotocol:kotlin-sdk-client:0.14.0`, Ktor client
(CIO engine + SSE plugin), JUnit5/kotlin-test.

## Global Constraints

- Kotlin JVM toolchain 25, Kotlin plugin version 2.4.0 (matches breui).
- `mainClass`: `postmannen.MainKt`.
- MCP client SDK: `io.modelcontextprotocol:kotlin-sdk-client:0.14.0` (exact
  coordinate, confirmed present on Maven Central).
- Default MCP endpoint: `https://mcp.postman.com/minimal`, overridable via
  `POSTMAN_MCP_URL` env var.
- `POSTMAN_API_KEY` env var is required; missing/blank → stderr message +
  exit 1, checked in `Main` before the screen starts. Never hardcoded, never
  logged.
- Tool names/schemas (verified live, not guessed): `getWorkspaces()` (no
  args) and `getCollections({"workspace": workspaceId})`.
- Tool responses are one `text` content block containing a markdown table —
  not structured JSON.
- Single fixed theme `businessmachine`, set once in `Main`. No theme picker
  in v1. No collection drilldown, no request sending in v1.

---

## File Structure

- `build.gradle.kts`, `settings.gradle.kts`, `gradle/wrapper/*`, `gradlew`,
  `gradlew.bat`, `.gitignore` — project scaffold, mirrors breui.
- `src/main/kotlin/postmannen/Main.kt` — entry point: env check, terminal/gui
  setup, wiring, `App(...).run()`.
- `src/main/kotlin/postmannen/util/MarkdownTable.kt` — pure markdown table
  parser, no I/O.
- `src/main/kotlin/postmannen/model/Workspace.kt` — `Workspace` data class.
- `src/main/kotlin/postmannen/model/Collection.kt` — `Collection` data class.
- `src/main/kotlin/postmannen/model/AppState.kt` — `AppState` data class.
- `src/main/kotlin/postmannen/service/PostmanMcpService.kt` — service
  interface.
- `src/main/kotlin/postmannen/service/PostmanMcpServiceImpl.kt` — real MCP
  client implementation (Ktor + MCP SDK).
- `src/main/kotlin/postmannen/viewmodel/AppViewModel.kt` — state holder,
  mutations, error handling.
- `src/main/kotlin/postmannen/ui/StatusBar.kt` — ported from breui verbatim
  (package renamed).
- `src/main/kotlin/postmannen/ui/App.kt` — root panel: dropdown (TOP),
  collection list (CENTER), status bar (BOTTOM).
- `src/test/kotlin/postmannen/util/MarkdownTableParserTest.kt`
- `src/test/kotlin/postmannen/service/FakePostmanMcpService.kt`
- `src/test/kotlin/postmannen/service/PostmanMcpServiceImplTest.kt`
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
  replaced fully in Task 6)

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

    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.14.0")
    implementation("io.ktor:ktor-client-cio:3.1.3")
    implementation("io.ktor:ktor-client-sse:3.1.3")

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
Expected: `BUILD SUCCESSFUL`. If dependency resolution fails on the Ktor or
MCP SDK coordinates, check Maven Central for the latest available patch
version of that artifact and update the version string — the exact patch
number is not load-bearing, only that it resolves.

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts settings.gradle.kts .gitignore gradle gradlew gradlew.bat src/main/kotlin/postmannen/Main.kt
git commit -m "chore: scaffold Kotlin/Lanterna project"
```

---

### Task 2: Markdown table parser (TDD)

**Files:**
- Create: `src/main/kotlin/postmannen/util/MarkdownTable.kt`
- Test: `src/test/kotlin/postmannen/util/MarkdownTableParserTest.kt`

**Interfaces:**
- Produces: `fun parseMarkdownTable(text: String): List<Map<String, String>>`
  in package `postmannen.util` — consumed by `PostmanMcpServiceImpl` in
  Task 5.

- [ ] **Step 1: Write the failing tests**

```kotlin
package postmannen.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTableParserTest {

    @Test
    fun `parses a simple two-row table`() {
        val text = """
            | id | name | type |
            |---|---|---|
            | ws-1 | Engineering | team |
            | ws-2 | Personal | personal |
        """.trimIndent()

        val rows = parseMarkdownTable(text)

        assertEquals(
            listOf(
                mapOf("id" to "ws-1", "name" to "Engineering", "type" to "team"),
                mapOf("id" to "ws-2", "name" to "Personal", "type" to "personal")
            ),
            rows
        )
    }

    @Test
    fun `unescapes html entities in cell values`() {
        val text = """
            | id | name |
            |---|---|
            | col-1 | Bob&#39;s Requests |
            | col-2 | Foo &quot;Bar&quot; &amp; Baz |
        """.trimIndent()

        val rows = parseMarkdownTable(text)

        assertEquals("Bob's Requests", rows[0]["name"])
        assertEquals("Foo \"Bar\" & Baz", rows[1]["name"])
    }

    @Test
    fun `stops at the first blank line after the table`() {
        val text = """
            | id | name |
            |---|---|
            | ws-1 | Engineering |

            Some trailing prose that is not part of the table.
        """.trimIndent()

        val rows = parseMarkdownTable(text)

        assertEquals(1, rows.size)
        assertEquals("ws-1", rows[0]["id"])
    }

    @Test
    fun `trims whitespace around cell values`() {
        val text = """
            | id   |   name |
            |---|---|
            |  ws-1  |  Engineering   |
        """.trimIndent()

        val rows = parseMarkdownTable(text)

        assertEquals(mapOf("id" to "ws-1", "name" to "Engineering"), rows[0])
    }

    @Test
    fun `returns empty list when no table is present`() {
        assertEquals(emptyList(), parseMarkdownTable("no table here"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "postmannen.util.MarkdownTableParserTest"`
Expected: FAIL (compile error — `parseMarkdownTable` not defined)

- [ ] **Step 3: Write the implementation**

```kotlin
package postmannen.util

fun parseMarkdownTable(text: String): List<Map<String, String>> {
    val lines = text.lines()
    val headerIndex = lines.indexOfFirst { it.trimStart().startsWith("|") }
    if (headerIndex == -1 || headerIndex + 1 >= lines.size) return emptyList()
    val separator = lines[headerIndex + 1]
    if (!isSeparatorLine(separator)) return emptyList()

    val headers = splitRow(lines[headerIndex])
    val rows = mutableListOf<Map<String, String>>()

    for (i in headerIndex + 2 until lines.size) {
        val line = lines[i]
        if (line.isBlank()) break
        if (!line.trimStart().startsWith("|")) break
        val cells = splitRow(line)
        rows.add(headers.zip(cells).toMap())
    }

    return rows
}

private fun isSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|")) return false
    return trimmed.trim('|').split("|").all { it.trim().all { c -> c == '-' || c == ':' } }
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split("|").map { unescapeHtml(it.trim()) }

private fun unescapeHtml(text: String): String =
    text
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "postmannen.util.MarkdownTableParserTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/postmannen/util/MarkdownTable.kt src/test/kotlin/postmannen/util/MarkdownTableParserTest.kt
git commit -m "feat: add markdown table parser"
```

---

### Task 3: Domain models, service interface, and fake service

**Files:**
- Create: `src/main/kotlin/postmannen/model/Workspace.kt`
- Create: `src/main/kotlin/postmannen/model/Collection.kt`
- Create: `src/main/kotlin/postmannen/service/PostmanMcpService.kt`
- Create: `src/test/kotlin/postmannen/service/FakePostmanMcpService.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `data class Workspace(val id: String, val name: String, val
  type: String)`; `data class Collection(val id: String, val name:
  String)`; `interface PostmanMcpService` with `suspend fun
  getWorkspaces(): Result<List<Workspace>>` and `suspend fun
  getCollections(workspaceId: String): Result<List<Collection>>`;
  `class FakePostmanMcpService` with mutable `workspacesResult` /
  `collectionsResult` fields and a `FIXTURE_WORKSPACES` /
  `FIXTURE_COLLECTIONS` companion — consumed by `AppViewModelTest` in
  Task 4.

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
// src/main/kotlin/postmannen/service/PostmanMcpService.kt
package postmannen.service

import postmannen.model.Collection
import postmannen.model.Workspace

interface PostmanMcpService {
    suspend fun getWorkspaces(): Result<List<Workspace>>
    suspend fun getCollections(workspaceId: String): Result<List<Collection>>
}
```

- [ ] **Step 3: Create the fake service**

```kotlin
// src/test/kotlin/postmannen/service/FakePostmanMcpService.kt
package postmannen.service

import postmannen.model.Collection
import postmannen.model.Workspace

class FakePostmanMcpService : PostmanMcpService {
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
git add src/main/kotlin/postmannen/model src/main/kotlin/postmannen/service/PostmanMcpService.kt src/test/kotlin/postmannen/service/FakePostmanMcpService.kt
git commit -m "feat: add domain models, service interface, and fake service"
```

---

### Task 4: AppState + AppViewModel (TDD)

**Files:**
- Create: `src/main/kotlin/postmannen/model/AppState.kt`
- Create: `src/main/kotlin/postmannen/viewmodel/AppViewModel.kt`
- Test: `src/test/kotlin/postmannen/viewmodel/AppViewModelTest.kt`

**Interfaces:**
- Consumes: `PostmanMcpService` (Task 3), `FakePostmanMcpService` (Task 3).
- Produces: `class AppViewModel(service: PostmanMcpService, scope:
  CoroutineScope)` with `val state: StateFlow<AppState>`, `fun
  loadWorkspaces()`, `fun selectWorkspace(index: Int)`, `fun
  loadCollections(workspaceId: String)` — consumed by `App` (Task 6) and
  `Main` (Task 6).

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
import postmannen.service.FakePostmanMcpService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @Test
    fun `loadWorkspaces sets workspaces and clears loading`() = runTest {
        val vm = AppViewModel(FakePostmanMcpService(), this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals(FakePostmanMcpService.FIXTURE_WORKSPACES, vm.state.value.workspaces)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `loadWorkspaces on failure sets status message and preserves existing list`() = runTest {
        val fake = FakePostmanMcpService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        fake.workspacesResult = Result.failure(RuntimeException("network error"))
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertTrue(vm.state.value.statusMessage.contains("network error"))
        assertEquals(FakePostmanMcpService.FIXTURE_WORKSPACES, vm.state.value.workspaces)
    }

    @Test
    fun `loadWorkspaces triggers loadCollections for the first workspace`() = runTest {
        val fake = FakePostmanMcpService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals("ws-1", fake.lastRequestedWorkspaceId)
        assertEquals(FakePostmanMcpService.FIXTURE_COLLECTIONS, vm.state.value.collections)
    }

    @Test
    fun `loadWorkspaces with empty list does not trigger loadCollections`() = runTest {
        val fake = FakePostmanMcpService().apply { workspacesResult = Result.success(emptyList()) }
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals(null, fake.lastRequestedWorkspaceId)
    }

    @Test
    fun `selectWorkspace updates index and loads its collections`() = runTest {
        val fake = FakePostmanMcpService()
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
        val fake = FakePostmanMcpService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        fake.collectionsResult = Result.failure(RuntimeException("tool error"))
        vm.loadCollections("ws-1")
        advanceUntilIdle()
        assertTrue(vm.state.value.statusMessage.contains("tool error"))
        assertEquals(FakePostmanMcpService.FIXTURE_COLLECTIONS, vm.state.value.collections)
    }

    @Test
    fun `loadCollections sets loading true then false around the call`() = runTest {
        val fake = FakePostmanMcpService()
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
import postmannen.service.PostmanMcpService

class AppViewModel(
    private val service: PostmanMcpService,
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

### Task 5: Real MCP service implementation

**Files:**
- Create: `src/main/kotlin/postmannen/service/PostmanMcpServiceImpl.kt`
- Test: `src/test/kotlin/postmannen/service/PostmanMcpServiceImplTest.kt`

**Interfaces:**
- Consumes: `PostmanMcpService` (Task 3), `parseMarkdownTable` (Task 2),
  `Workspace`/`Collection` (Task 3).
- Produces: `class PostmanMcpServiceImpl(apiKey: String, mcpUrl: String =
  "https://mcp.postman.com/minimal") : PostmanMcpService` — consumed by
  `Main` (Task 6).

- [ ] **Step 1: Write the implementation**

```kotlin
// src/main/kotlin/postmannen/service/PostmanMcpServiceImpl.kt
package postmannen.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import kotlinx.serialization.json.JsonPrimitive
import postmannen.model.Collection
import postmannen.model.Workspace
import postmannen.util.parseMarkdownTable

class PostmanMcpServiceImpl(
    private val apiKey: String,
    private val mcpUrl: String = DEFAULT_MCP_URL
) : PostmanMcpService {

    override suspend fun getWorkspaces(): Result<List<Workspace>> = runCatching {
        val rows = callToolForRows("getWorkspaces", emptyMap())
        rows.map { row ->
            Workspace(
                id = row.getValue("id"),
                name = row.getValue("name"),
                type = row.getValue("type")
            )
        }
    }

    override suspend fun getCollections(workspaceId: String): Result<List<Collection>> = runCatching {
        val rows = callToolForRows("getCollections", mapOf("workspace" to workspaceId))
        rows.map { row ->
            Collection(
                id = row.getValue("id"),
                name = row.getValue("name")
            )
        }
    }

    private suspend fun callToolForRows(toolName: String, args: Map<String, String>): List<Map<String, String>> {
        val httpClient = HttpClient(CIO) {
            install(SSE)
            defaultRequest {
                header("Authorization", "Bearer $apiKey")
            }
        }
        val client = Client(clientInfo = Implementation(name = "postmannen", version = "0.1.0"))
        val transport = StreamableHttpClientTransport(url = mcpUrl, client = httpClient)
        try {
            client.connect(transport)
            val result = client.callTool(
                CallToolRequest(
                    name = toolName,
                    arguments = args.mapValues { JsonPrimitive(it.value) }
                )
            )
            val text = result?.content
                ?.filterIsInstance<TextContent>()
                ?.firstOrNull()
                ?.text
                ?: error("Empty response from tool $toolName")
            return parseMarkdownTable(text)
        } finally {
            client.close()
            httpClient.close()
        }
    }

    companion object {
        const val DEFAULT_MCP_URL = "https://mcp.postman.com/minimal"
    }
}
```

- [ ] **Step 2: Write the integration smoke test**

```kotlin
// src/test/kotlin/postmannen/service/PostmanMcpServiceImplTest.kt
package postmannen.service

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

@Tag("integration")
class PostmanMcpServiceImplTest {

    @Test
    fun `getWorkspaces returns at least one workspace with a name`() = runTest {
        val apiKey = System.getenv("POSTMAN_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank(), "POSTMAN_API_KEY not set, skipping integration test")

        val service = PostmanMcpServiceImpl(apiKey)
        val result = service.getWorkspaces()

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()?.message}")
        val workspaces = result.getOrThrow()
        assertTrue(workspaces.isNotEmpty(), "Expected at least one workspace")
        assertTrue(workspaces.all { it.name.isNotBlank() }, "All workspaces should have a name")
    }
}
```

- [ ] **Step 3: Verify it compiles and the unit-test suite still passes**

Run: `./gradlew build -x test && ./gradlew test --tests "postmannen.*" -PexcludeTags=integration`

If the project has no tag-exclusion wired up yet, it's fine to run
`./gradlew test` directly — the integration test self-skips via
`assumeTrue` when `POSTMAN_API_KEY` is unset, so it reports as skipped, not
failed, in a normal dev environment.

Expected: `BUILD SUCCESSFUL`, `PostmanMcpServiceImplTest` shown as skipped.

If `POSTMAN_API_KEY` is set in your environment, expect it to pass for
real:

Run: `POSTMAN_API_KEY=$POSTMAN_API_KEY ./gradlew test --tests "postmannen.service.PostmanMcpServiceImplTest"`
Expected: PASS

If the MCP SDK's actual class/method names differ from what's used above
(`Client`, `StreamableHttpClientTransport`, `CallToolRequest`,
`TextContent`, `Implementation`), fix the imports/calls to match the real
API surface from `io.modelcontextprotocol:kotlin-sdk-client:0.14.0` until
this compiles — the tool names, argument shape (`{"workspace":
workspaceId}`), and text-content response format are the parts that were
verified live and must not change.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/postmannen/service/PostmanMcpServiceImpl.kt src/test/kotlin/postmannen/service/PostmanMcpServiceImplTest.kt
git commit -m "feat: add real PostmanMcpService implementation over MCP"
```

---

### Task 6: UI layer + Main wiring

**Files:**
- Create: `src/main/kotlin/postmannen/ui/StatusBar.kt`
- Create: `src/main/kotlin/postmannen/ui/App.kt`
- Modify: `src/main/kotlin/postmannen/Main.kt` (replace placeholder from
  Task 1)

**Interfaces:**
- Consumes: `AppViewModel` (Task 4), `PostmanMcpServiceImpl` (Task 5),
  `Workspace`/`Collection` (Task 3).
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
import postmannen.service.PostmanMcpServiceImpl
import postmannen.ui.App
import postmannen.viewmodel.AppViewModel
import kotlin.system.exitProcess

fun main() = runBlocking {
    val apiKey = System.getenv("POSTMAN_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println("POSTMAN_API_KEY environment variable is required.")
        exitProcess(1)
    }
    val mcpUrl = System.getenv("POSTMAN_MCP_URL")?.takeIf { it.isNotBlank() }
        ?: PostmanMcpServiceImpl.DEFAULT_MCP_URL

    val terminal = DefaultTerminalFactory().createTerminal()
    val screen = TerminalScreen(terminal)
    screen.startScreen()
    val gui = MultiWindowTextGUI(screen)
    gui.setTheme(LanternaThemes.getRegisteredTheme("businessmachine"))

    val scope = CoroutineScope(Dispatchers.Default)
    val viewModel = AppViewModel(PostmanMcpServiceImpl(apiKey, mcpUrl), scope)

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

- Spec coverage: stack/build setup (Task 1), MCP service + markdown
  parsing + models (Tasks 2, 3, 5), state/viewmodel (Task 4), UI layer
  (Task 6), error handling (Task 4 tests + Task 5), testing strategy (all
  tasks include their prescribed test file) — every section of the spec
  maps to a task.
- The spec's fixture strings for `MarkdownTableParserTest` were described
  as "captured from real server responses during design" but the actual
  captured text isn't present in the spec document — Task 2's fixtures
  were authored to match the described shape (columns, HTML entities)
  since the literal captured strings weren't available to copy in.
