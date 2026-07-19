package postmannen.service

import kotlinx.coroutines.test.runTest
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
}
