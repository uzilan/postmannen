package postmannen.service

import kotlinx.coroutines.test.runTest
import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.EnvironmentDetail
import postmannen.model.EnvironmentValue
import kotlin.test.Test
import kotlin.test.assertEquals

// Counts calls per method so tests can assert the delegate was hit exactly
// once per key, even across multiple reads through the cache.
private class CountingPostmanApiService(private val delegate: PostmanApiService) : PostmanApiService {
    val getWorkspacesCalls = mutableListOf<Unit>()
    val getCollectionsCalls = mutableListOf<String>()
    val getCollectionDetailCalls = mutableListOf<String>()
    val getEnvironmentsCalls = mutableListOf<String>()
    val getEnvironmentDetailCalls = mutableListOf<String>()

    override suspend fun getWorkspaces() = delegate.getWorkspaces().also { getWorkspacesCalls.add(Unit) }
    override suspend fun getCollections(workspaceId: String) =
        delegate.getCollections(workspaceId).also { getCollectionsCalls.add(workspaceId) }
    override suspend fun getCollectionDetail(collectionUid: String) =
        delegate.getCollectionDetail(collectionUid).also { getCollectionDetailCalls.add(collectionUid) }
    override suspend fun getEnvironments(workspaceId: String) =
        delegate.getEnvironments(workspaceId).also { getEnvironmentsCalls.add(workspaceId) }
    override suspend fun getEnvironmentDetail(environmentUid: String) =
        delegate.getEnvironmentDetail(environmentUid).also { getEnvironmentDetailCalls.add(environmentUid) }
    override suspend fun updateEnvironment(detail: EnvironmentDetail) = delegate.updateEnvironment(detail)
    override suspend fun createEnvironment(workspaceId: String, name: String) = delegate.createEnvironment(workspaceId, name)
    override suspend fun createCollection(workspaceId: String, name: String) = delegate.createCollection(workspaceId, name)
    val deleteCollectionCalls = mutableListOf<String>()
    override suspend fun deleteCollection(uid: String) = delegate.deleteCollection(uid).also { deleteCollectionCalls.add(uid) }
    val deleteEnvironmentCalls = mutableListOf<String>()
    override suspend fun deleteEnvironment(uid: String) = delegate.deleteEnvironment(uid).also { deleteEnvironmentCalls.add(uid) }
    val renameCollectionCalls = mutableListOf<Pair<String, String>>()
    override suspend fun renameCollection(uid: String, name: String) =
        delegate.renameCollection(uid, name).also { renameCollectionCalls.add(uid to name) }
    val renameEnvironmentCalls = mutableListOf<Pair<String, String>>()
    override suspend fun renameEnvironment(uid: String, name: String) =
        delegate.renameEnvironment(uid, name).also { renameEnvironmentCalls.add(uid to name) }
}

class CachingPostmanApiServiceTest {

    @Test
    fun `getWorkspaces only hits the delegate once across repeated calls`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)

        cache.getWorkspaces()
        cache.getWorkspaces()
        cache.getWorkspaces()

        assertEquals(1, counting.getWorkspacesCalls.size)
    }

    @Test
    fun `getCollections caches per workspace id`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)

        cache.getCollections("ws-1")
        cache.getCollections("ws-1")
        cache.getCollections("ws-2")

        assertEquals(listOf("ws-1", "ws-2"), counting.getCollectionsCalls)
    }

    @Test
    fun `getCollectionDetail caches per collection uid`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)

        cache.getCollectionDetail("col-1-uid")
        cache.getCollectionDetail("col-1-uid")

        assertEquals(listOf("col-1-uid"), counting.getCollectionDetailCalls)
    }

    @Test
    fun `getEnvironments caches per workspace id`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)

        cache.getEnvironments("ws-1")
        cache.getEnvironments("ws-1")

        assertEquals(listOf("ws-1"), counting.getEnvironmentsCalls)
    }

    @Test
    fun `getEnvironmentDetail caches per environment uid`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)

        cache.getEnvironmentDetail("env-1-uid")
        cache.getEnvironmentDetail("env-1-uid")

        assertEquals(listOf("env-1-uid"), counting.getEnvironmentDetailCalls)
    }

    @Test
    fun `updateEnvironment writes through so a following getEnvironmentDetail doesn't hit the delegate again`() = runTest {
        val fake = FakePostmanApiService()
        val counting = CountingPostmanApiService(fake)
        val cache = CachingPostmanApiService(counting)
        cache.getEnvironmentDetail("env-1-uid")
        val updated = FakePostmanApiService.FIXTURE_ENVIRONMENT_DETAIL_STAGING.copy(
            values = listOf(EnvironmentValue(key = "NEW_KEY", value = "new_value", enabled = true, type = "default"))
        )

        cache.updateEnvironment(updated)
        val result = cache.getEnvironmentDetail("env-1-uid")

        assertEquals(updated, result.getOrThrow())
        assertEquals(listOf("env-1-uid"), counting.getEnvironmentDetailCalls)
    }

    @Test
    fun `createEnvironment appends to an already-cached environments list`() = runTest {
        val fake = FakePostmanApiService()
        fake.createEnvironmentResult = Result.success(Environment(id = "env-9", name = "QA", uid = "env-9-uid"))
        val counting = CountingPostmanApiService(fake)
        val cache = CachingPostmanApiService(counting)
        cache.getEnvironments("ws-1")

        cache.createEnvironment("ws-1", "QA")
        val result = cache.getEnvironments("ws-1")

        assertEquals(FakePostmanApiService.FIXTURE_ENVIRONMENTS + Environment(id = "env-9", name = "QA", uid = "env-9-uid"), result.getOrThrow())
        assertEquals(listOf("ws-1"), counting.getEnvironmentsCalls)
    }

    @Test
    fun `createCollection appends to an already-cached collections list`() = runTest {
        val fake = FakePostmanApiService()
        fake.createCollectionResult = Result.success(Collection(id = "col-9", name = "QA API", uid = "col-9-uid"))
        val counting = CountingPostmanApiService(fake)
        val cache = CachingPostmanApiService(counting)
        cache.getCollections("ws-1")

        cache.createCollection("ws-1", "QA API")
        val result = cache.getCollections("ws-1")

        assertEquals(FakePostmanApiService.FIXTURE_COLLECTIONS + Collection(id = "col-9", name = "QA API", uid = "col-9-uid"), result.getOrThrow())
        assertEquals(listOf("ws-1"), counting.getCollectionsCalls)
    }

    @Test
    fun `invalidateWorkspace clears that workspace's collections, environments, and their details`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)
        cache.getCollections("ws-1")
        cache.getEnvironments("ws-1")
        cache.getCollectionDetail("col-1-uid")
        cache.getEnvironmentDetail("env-1-uid")

        cache.invalidateWorkspace("ws-1")
        cache.getCollections("ws-1")
        cache.getEnvironments("ws-1")
        cache.getCollectionDetail("col-1-uid")
        cache.getEnvironmentDetail("env-1-uid")

        assertEquals(listOf("ws-1", "ws-1"), counting.getCollectionsCalls)
        assertEquals(listOf("ws-1", "ws-1"), counting.getEnvironmentsCalls)
        assertEquals(listOf("col-1-uid", "col-1-uid"), counting.getCollectionDetailCalls)
        assertEquals(listOf("env-1-uid", "env-1-uid"), counting.getEnvironmentDetailCalls)
    }

    @Test
    fun `invalidateWorkspace on an unvisited workspace id is a no-op`() = runTest {
        val cache = CachingPostmanApiService(FakePostmanApiService())
        cache.invalidateWorkspace("never-visited")
    }

    @Test
    fun `deleteCollection removes the collection from its cached workspace list and clears its detail cache`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)
        cache.getCollections("ws-1")
        cache.getCollectionDetail("col-1-uid")

        cache.deleteCollection("col-1-uid")
        val collections = cache.getCollections("ws-1")
        cache.getCollectionDetail("col-1-uid")

        assertEquals(listOf(FakePostmanApiService.FIXTURE_COLLECTIONS[1]), collections.getOrThrow())
        assertEquals(listOf("ws-1"), counting.getCollectionsCalls)
        assertEquals(listOf("col-1-uid", "col-1-uid"), counting.getCollectionDetailCalls)
    }

    @Test
    fun `deleteEnvironment removes the environment from its cached workspace list and clears its detail cache`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)
        cache.getEnvironments("ws-1")
        cache.getEnvironmentDetail("env-1-uid")

        cache.deleteEnvironment("env-1-uid")
        val environments = cache.getEnvironments("ws-1")
        cache.getEnvironmentDetail("env-1-uid")

        assertEquals(listOf(FakePostmanApiService.FIXTURE_ENVIRONMENTS[1]), environments.getOrThrow())
        assertEquals(listOf("ws-1"), counting.getEnvironmentsCalls)
        assertEquals(listOf("env-1-uid", "env-1-uid"), counting.getEnvironmentDetailCalls)
    }

    @Test
    fun `renameCollection patches the cached workspace list and detail cache without hitting the delegate again`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)
        cache.getCollections("ws-1")
        cache.getCollectionDetail("col-1-uid")

        cache.renameCollection("col-1-uid", "Renamed")
        val collections = cache.getCollections("ws-1")
        val detail = cache.getCollectionDetail("col-1-uid")

        assertEquals("Renamed", collections.getOrThrow().single { it.uid == "col-1-uid" }.name)
        assertEquals("Renamed", detail.getOrThrow().name)
        assertEquals(listOf("ws-1"), counting.getCollectionsCalls)
        assertEquals(listOf("col-1-uid"), counting.getCollectionDetailCalls)
    }

    @Test
    fun `renameEnvironment patches the cached workspace list and detail cache without hitting the delegate again`() = runTest {
        val counting = CountingPostmanApiService(FakePostmanApiService())
        val cache = CachingPostmanApiService(counting)
        cache.getEnvironments("ws-1")
        cache.getEnvironmentDetail("env-1-uid")

        cache.renameEnvironment("env-1-uid", "Renamed")
        val environments = cache.getEnvironments("ws-1")
        val detail = cache.getEnvironmentDetail("env-1-uid")

        assertEquals("Renamed", environments.getOrThrow().single { it.uid == "env-1-uid" }.name)
        assertEquals("Renamed", detail.getOrThrow().name)
        assertEquals(listOf("ws-1"), counting.getEnvironmentsCalls)
        assertEquals(listOf("env-1-uid"), counting.getEnvironmentDetailCalls)
    }
}
