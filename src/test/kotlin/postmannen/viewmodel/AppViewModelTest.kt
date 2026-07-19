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
        fake.environmentsResult = Result.success(listOf(postmannen.model.Environment(id = "env-3", name = "QA", uid = "env-3-uid")))
        vm.selectWorkspace(1)
        advanceUntilIdle()
        assertEquals(listOf(postmannen.model.Environment(id = "env-3", name = "QA", uid = "env-3-uid")), vm.state.value.environments)
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

    @Test
    fun `toggleEnvironmentSelection adds then removes an id`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.toggleEnvironmentSelection("env-1")
        assertEquals(setOf("env-1"), vm.state.value.selectedEnvironmentIds)
        vm.toggleEnvironmentSelection("env-1")
        assertEquals(emptySet(), vm.state.value.selectedEnvironmentIds)
    }

    @Test
    fun `openComparison with fewer than 2 selected sets status message and does not open`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.toggleEnvironmentSelection("env-1")
        vm.openComparison()
        advanceUntilIdle()
        assertEquals("Select at least 2 environments to compare", vm.state.value.statusMessage)
        assertFalse(vm.state.value.comparisonVisible)
    }

    @Test
    fun `openComparison with 2 selected fetches both details and opens`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        assertTrue(vm.state.value.comparisonVisible)
        assertEquals(
            setOf(FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING, FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_PRODUCTION),
            vm.state.value.comparisonDetails.toSet()
        )
    }

    @Test
    fun `openComparison aborts and surfaces error when one fetch fails`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        fake.environmentDetailResults = mapOf(
            "env-1-uid" to Result.success(FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING),
            "env-2-uid" to Result.failure(RuntimeException("detail fetch failed"))
        )
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        assertFalse(vm.state.value.comparisonVisible)
        assertTrue(vm.state.value.statusMessage.contains("detail fetch failed"))
    }

    @Test
    fun `closeComparison hides the overlay`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        vm.closeComparison()
        assertFalse(vm.state.value.comparisonVisible)
    }

    @Test
    fun `loadWorkspaces clears selectedEnvironmentIds`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.toggleEnvironmentSelection("env-1")
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals(emptySet(), vm.state.value.selectedEnvironmentIds)
    }

    @Test
    fun `selectWorkspace clears selectedEnvironmentIds`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.selectWorkspace(1)
        advanceUntilIdle()
        assertEquals(emptySet(), vm.state.value.selectedEnvironmentIds)
    }

    @Test
    fun `updateEnvironmentValue replaces an existing entry's value and preserves enabled and type`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        vm.updateEnvironmentValue("env-1-uid", "BASE_URL", "https://new-staging.example.com")
        advanceUntilIdle()
        val updated = vm.state.value.comparisonDetails.first { it.uid == "env-1-uid" }
        val entry = updated.values.first { it.key == "BASE_URL" }
        assertEquals("https://new-staging.example.com", entry.value)
        assertTrue(entry.enabled)
        assertEquals("default", entry.type)
    }

    @Test
    fun `updateEnvironmentValue on a missing key appends a new enabled entry`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        vm.updateEnvironmentValue("env-2-uid", "API_KEY", "prod_xxx")
        advanceUntilIdle()
        val updated = vm.state.value.comparisonDetails.first { it.uid == "env-2-uid" }
        val entry = updated.values.first { it.key == "API_KEY" }
        assertEquals("prod_xxx", entry.value)
        assertTrue(entry.enabled)
        assertEquals("default", entry.type)
    }

    @Test
    fun `toggleEnvironmentValueEnabled flips only the targeted entry`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        vm.toggleEnvironmentValueEnabled("env-1-uid", "BASE_URL")
        advanceUntilIdle()
        val updated = vm.state.value.comparisonDetails.first { it.uid == "env-1-uid" }
        assertFalse(updated.values.first { it.key == "BASE_URL" }.enabled)
        assertTrue(updated.values.first { it.key == "API_KEY" }.enabled)
    }

    @Test
    fun `toggleEnvironmentValueEnabled on a missing key is a no-op`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        val before = vm.state.value.comparisonDetails
        vm.toggleEnvironmentValueEnabled("env-2-uid", "API_KEY")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.comparisonDetails)
        assertEquals(null, fake.lastUpdatedEnvironmentDetail)
    }

    @Test
    fun `updateEnvironmentValue on failure leaves comparisonDetails untouched and surfaces error`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        val before = vm.state.value.comparisonDetails
        fake.updateEnvironmentResult = Result.failure(RuntimeException("save failed"))
        vm.updateEnvironmentValue("env-1-uid", "BASE_URL", "https://changed.example.com")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.comparisonDetails)
        assertTrue(vm.state.value.statusMessage.contains("save failed"))
    }

    @Test
    fun `toggleEnvironmentValueEnabled on failure leaves comparisonDetails untouched and surfaces error`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.openComparison()
        advanceUntilIdle()
        val before = vm.state.value.comparisonDetails
        fake.updateEnvironmentResult = Result.failure(RuntimeException("toggle failed"))
        vm.toggleEnvironmentValueEnabled("env-1-uid", "BASE_URL")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.comparisonDetails)
        assertTrue(vm.state.value.statusMessage.contains("toggle failed"))
    }
}
