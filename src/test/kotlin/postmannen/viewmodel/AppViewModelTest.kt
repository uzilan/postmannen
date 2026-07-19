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
    fun `refreshWorkspace invalidates the current workspace and reloads its collections and environments`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()

        vm.refreshWorkspace()
        advanceUntilIdle()

        assertEquals(listOf("ws-1"), fake.invalidateWorkspaceCalls)
        assertEquals(FakePostmanApiService.FIXTURE_COLLECTIONS, vm.state.value.collections)
        assertEquals(FakePostmanApiService.FIXTURE_ENVIRONMENTS, vm.state.value.environments)
    }

    @Test
    fun `refreshWorkspace with no workspace selected is a no-op`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)

        vm.refreshWorkspace()
        advanceUntilIdle()

        assertEquals(emptyList(), fake.invalidateWorkspaceCalls)
    }

    @Test
    fun `refreshWorkspace targets the currently selected workspace, not always the first`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.selectWorkspace(1)
        advanceUntilIdle()

        vm.refreshWorkspace()
        advanceUntilIdle()

        assertEquals(listOf("ws-2"), fake.invalidateWorkspaceCalls)
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
    fun `loadWorkspaces also fetches each collection's full tree and seeds collapsedNodeIds`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals(
            listOf(
                FakePostmanApiService.FIXTURE_COLLECTION_DETAIL_AUTH,
                FakePostmanApiService.FIXTURE_COLLECTION_DETAIL_BILLING
            ),
            vm.state.value.collectionDetails
        )
        assertEquals(setOf("col-1-uid", "col-2-uid", "col-1-uid/0"), vm.state.value.collapsedNodeIds)
    }

    @Test
    fun `loadCollections tree fetch failure for one collection is skipped but others still populate`() = runTest {
        val fake = FakePostmanApiService()
        fake.collectionDetailResults = mapOf(
            "col-1-uid" to Result.failure(RuntimeException("boom")),
            "col-2-uid" to Result.success(FakePostmanApiService.FIXTURE_COLLECTION_DETAIL_BILLING)
        )
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertEquals(listOf(FakePostmanApiService.FIXTURE_COLLECTION_DETAIL_BILLING), vm.state.value.collectionDetails)
        assertTrue(vm.state.value.statusMessage.contains("Auth API"))
    }

    @Test
    fun `toggleNodeCollapsed adds then removes a node id`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        assertTrue("col-1-uid" in vm.state.value.collapsedNodeIds)
        vm.toggleNodeCollapsed("col-1-uid")
        assertFalse("col-1-uid" in vm.state.value.collapsedNodeIds)
        vm.toggleNodeCollapsed("col-1-uid")
        assertTrue("col-1-uid" in vm.state.value.collapsedNodeIds)
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
    fun `refreshEnvironmentPanel with nothing marked and nothing highlighted stays empty`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.environmentPanelDetails)
    }

    @Test
    fun `refreshEnvironmentPanel with a highlighted id and nothing marked fetches that environment`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.refreshEnvironmentPanel("env-1")
        advanceUntilIdle()
        assertEquals(listOf(FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING), vm.state.value.environmentPanelDetails)
    }

    @Test
    fun `refreshEnvironmentPanel with an unknown highlighted id stays empty`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.refreshEnvironmentPanel("does-not-exist")
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.environmentPanelDetails)
    }

    @Test
    fun `refreshEnvironmentPanel with 2+ marked shows the marked set regardless of highlighted id`() = runTest {
        val vm = AppViewModel(FakePostmanApiService(), this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel("does-not-matter")
        advanceUntilIdle()
        assertEquals(
            setOf(FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING, FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_PRODUCTION),
            vm.state.value.environmentPanelDetails.toSet()
        )
    }

    @Test
    fun `refreshEnvironmentPanel keeps the environments that succeeded and surfaces an error for the one that failed`() = runTest {
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
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        assertEquals(listOf(FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING), vm.state.value.environmentPanelDetails)
        assertTrue(vm.state.value.statusMessage.contains("Production"))
    }

    @Test
    fun `refreshEnvironmentPanel called again with the same resulting set does not refetch`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.refreshEnvironmentPanel("env-1")
        advanceUntilIdle()
        fake.environmentDetailResults = mapOf("env-1-uid" to Result.failure(RuntimeException("should not be called")))
        vm.refreshEnvironmentPanel("env-1")
        advanceUntilIdle()
        assertEquals(listOf(FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING), vm.state.value.environmentPanelDetails)
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
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        vm.updateEnvironmentValue("env-1-uid", "BASE_URL", "https://new-staging.example.com")
        advanceUntilIdle()
        val updated = vm.state.value.environmentPanelDetails.first { it.uid == "env-1-uid" }
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
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        vm.updateEnvironmentValue("env-2-uid", "API_KEY", "prod_xxx")
        advanceUntilIdle()
        val updated = vm.state.value.environmentPanelDetails.first { it.uid == "env-2-uid" }
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
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        vm.toggleEnvironmentValueEnabled("env-1-uid", "BASE_URL")
        advanceUntilIdle()
        val updated = vm.state.value.environmentPanelDetails.first { it.uid == "env-1-uid" }
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
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val before = vm.state.value.environmentPanelDetails
        vm.toggleEnvironmentValueEnabled("env-2-uid", "API_KEY")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.environmentPanelDetails)
        assertEquals(null, fake.lastUpdatedEnvironmentDetail)
    }

    @Test
    fun `updateEnvironmentValue on failure leaves environmentPanelDetails untouched and surfaces error`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val before = vm.state.value.environmentPanelDetails
        fake.updateEnvironmentResult = Result.failure(RuntimeException("save failed"))
        vm.updateEnvironmentValue("env-1-uid", "BASE_URL", "https://changed.example.com")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.environmentPanelDetails)
        assertTrue(vm.state.value.statusMessage.contains("save failed"))
    }

    @Test
    fun `toggleEnvironmentValueEnabled on failure leaves environmentPanelDetails untouched and surfaces error`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val before = vm.state.value.environmentPanelDetails
        fake.updateEnvironmentResult = Result.failure(RuntimeException("toggle failed"))
        vm.toggleEnvironmentValueEnabled("env-1-uid", "BASE_URL")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.environmentPanelDetails)
        assertTrue(vm.state.value.statusMessage.contains("toggle failed"))
    }

    @Test
    fun `concurrent edits to different keys in the same environment do not clobber each other`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        vm.updateEnvironmentValue("env-1-uid", "BASE_URL", "https://a.example.com")
        vm.updateEnvironmentValue("env-1-uid", "API_KEY", "new_key")
        advanceUntilIdle()
        val updated = vm.state.value.environmentPanelDetails.first { it.uid == "env-1-uid" }
        assertEquals("https://a.example.com", updated.values.first { it.key == "BASE_URL" }.value)
        assertEquals("new_key", updated.values.first { it.key == "API_KEY" }.value)
    }

    @Test
    fun `renameEnvironmentKey is a no-op when newKey is blank or unchanged`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val before = vm.state.value.environmentPanelDetails
        vm.renameEnvironmentKey("BASE_URL", "")
        vm.renameEnvironmentKey("BASE_URL", "BASE_URL")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.environmentPanelDetails)
        assertEquals(null, fake.lastUpdatedEnvironmentDetail)
    }

    @Test
    fun `renameEnvironmentKey renames the key in every environment that has it`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        vm.renameEnvironmentKey("BASE_URL", "NEW_BASE_URL")
        advanceUntilIdle()
        val staging = vm.state.value.environmentPanelDetails.first { it.uid == "env-1-uid" }
        val production = vm.state.value.environmentPanelDetails.first { it.uid == "env-2-uid" }
        assertTrue(staging.values.any { it.key == "NEW_BASE_URL" })
        assertTrue(staging.values.none { it.key == "BASE_URL" })
        assertTrue(production.values.any { it.key == "NEW_BASE_URL" })
        assertTrue(production.values.none { it.key == "BASE_URL" })
    }

    @Test
    fun `renameEnvironmentKey rejects a collision before sending any request`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val before = vm.state.value.environmentPanelDetails
        // Staging already has API_KEY, so renaming BASE_URL -> API_KEY collides there.
        vm.renameEnvironmentKey("BASE_URL", "API_KEY")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.environmentPanelDetails)
        assertEquals(null, fake.lastUpdatedEnvironmentDetail)
        assertTrue(vm.state.value.statusMessage.contains("Staging"))
    }

    @Test
    fun `renameEnvironmentKey rolls back the succeeded environment when another fails`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val before = vm.state.value.environmentPanelDetails
        fake.updateEnvironmentHandler = { detail ->
            if (detail.uid == "env-2-uid") Result.failure(RuntimeException("boom")) else Result.success(Unit)
        }
        vm.renameEnvironmentKey("BASE_URL", "NEW_BASE_URL")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.environmentPanelDetails)
        assertTrue(vm.state.value.statusMessage.contains("boom"))
    }

    @Test
    fun `renameEnvironmentKey surfaces a distinct message when rollback also fails`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        // env-2's rename call always fails. env-1's rename call (its first call)
        // succeeds, triggering a rollback — but env-1's rollback call (its second
        // call) also fails, exercising the rollback-also-failed path.
        val callCounts = mutableMapOf<String, Int>()
        fake.updateEnvironmentHandler = { detail ->
            val count = (callCounts[detail.uid] ?: 0) + 1
            callCounts[detail.uid] = count
            when {
                detail.uid == "env-2-uid" -> Result.failure(RuntimeException("rename failed"))
                detail.uid == "env-1-uid" && count == 1 -> Result.success(Unit)
                else -> Result.failure(RuntimeException("rollback failed too"))
            }
        }
        vm.renameEnvironmentKey("BASE_URL", "NEW_BASE_URL")
        advanceUntilIdle()
        assertTrue(vm.state.value.statusMessage.contains("Postman"))
    }

    @Test
    fun `renameEnvironmentKey and updateEnvironmentValue for different keys do not clobber each other`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        vm.renameEnvironmentKey("BASE_URL", "NEW_BASE_URL")
        vm.updateEnvironmentValue("env-1-uid", "API_KEY", "changed_key_value")
        advanceUntilIdle()
        val staging = vm.state.value.environmentPanelDetails.first { it.uid == "env-1-uid" }
        assertTrue(staging.values.any { it.key == "NEW_BASE_URL" })
        assertEquals("changed_key_value", staging.values.first { it.key == "API_KEY" }.value)
    }

    @Test
    fun `deleteEnvironmentKey removes the key from every environment that has it`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        vm.deleteEnvironmentKey("BASE_URL")
        advanceUntilIdle()
        val staging = vm.state.value.environmentPanelDetails.first { it.uid == "env-1-uid" }
        val production = vm.state.value.environmentPanelDetails.first { it.uid == "env-2-uid" }
        assertTrue(staging.values.none { it.key == "BASE_URL" })
        assertTrue(production.values.none { it.key == "BASE_URL" })
    }

    @Test
    fun `deleteEnvironmentKey is a no-op when the key doesn't exist anywhere`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val before = vm.state.value.environmentPanelDetails
        vm.deleteEnvironmentKey("NOT_A_REAL_KEY")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.environmentPanelDetails)
        assertEquals(null, fake.lastUpdatedEnvironmentDetail)
    }

    @Test
    fun `deleteEnvironmentKey rolls back the succeeded environment when another fails`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val before = vm.state.value.environmentPanelDetails
        fake.updateEnvironmentHandler = { detail ->
            if (detail.uid == "env-2-uid") Result.failure(RuntimeException("boom")) else Result.success(Unit)
        }
        vm.deleteEnvironmentKey("BASE_URL")
        advanceUntilIdle()
        assertEquals(before, vm.state.value.environmentPanelDetails)
        assertTrue(vm.state.value.statusMessage.contains("boom"))
    }

    @Test
    fun `deleteEnvironmentKey surfaces a distinct message when rollback also fails`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        val callCounts = mutableMapOf<String, Int>()
        fake.updateEnvironmentHandler = { detail ->
            val count = (callCounts[detail.uid] ?: 0) + 1
            callCounts[detail.uid] = count
            when {
                detail.uid == "env-2-uid" -> Result.failure(RuntimeException("delete failed"))
                detail.uid == "env-1-uid" && count == 1 -> Result.success(Unit)
                else -> Result.failure(RuntimeException("rollback failed too"))
            }
        }
        vm.deleteEnvironmentKey("BASE_URL")
        advanceUntilIdle()
        assertTrue(vm.state.value.statusMessage.contains("Postman"))
    }

    @Test
    fun `deleteEnvironmentKey and updateEnvironmentValue for different keys do not clobber each other`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        vm.toggleEnvironmentSelection("env-1")
        vm.toggleEnvironmentSelection("env-2")
        vm.refreshEnvironmentPanel(null)
        advanceUntilIdle()
        vm.deleteEnvironmentKey("BASE_URL")
        vm.updateEnvironmentValue("env-1-uid", "API_KEY", "changed_key_value")
        advanceUntilIdle()
        val staging = vm.state.value.environmentPanelDetails.first { it.uid == "env-1-uid" }
        assertTrue(staging.values.none { it.key == "BASE_URL" })
        assertEquals("changed_key_value", staging.values.first { it.key == "API_KEY" }.value)
    }

    @Test
    fun `createEnvironment appends the new environment on success`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        val before = vm.state.value.environments
        fake.createEnvironmentResult = Result.success(
            postmannen.model.Environment(id = "env-9", name = "QA", uid = "env-9-uid")
        )

        vm.createEnvironment("QA")
        advanceUntilIdle()

        assertEquals(before + postmannen.model.Environment(id = "env-9", name = "QA", uid = "env-9-uid"), vm.state.value.environments)
        assertEquals("ws-1", fake.lastCreatedEnvironmentWorkspaceId)
        assertEquals("QA", fake.lastCreatedEnvironmentName)
    }

    @Test
    fun `createEnvironment with a blank name is a no-op`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        val before = vm.state.value.environments

        vm.createEnvironment("   ")
        advanceUntilIdle()

        assertEquals(before, vm.state.value.environments)
        assertEquals(null, fake.lastCreatedEnvironmentName)
    }

    @Test
    fun `createEnvironment on failure sets status message and leaves environments untouched`() = runTest {
        val fake = FakePostmanApiService()
        val vm = AppViewModel(fake, this)
        vm.loadWorkspaces()
        advanceUntilIdle()
        val before = vm.state.value.environments
        fake.createEnvironmentResult = Result.failure(RuntimeException("boom"))

        vm.createEnvironment("QA")
        advanceUntilIdle()

        assertEquals(before, vm.state.value.environments)
        assertTrue(vm.state.value.statusMessage.contains("boom"))
    }
}
