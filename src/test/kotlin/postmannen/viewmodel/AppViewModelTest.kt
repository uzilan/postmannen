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
