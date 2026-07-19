package postmannen.service

import kotlinx.coroutines.test.runTest
import postmannen.model.Environment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostmanApiServiceImplFakeTest {

    @Test
    fun `createEnvironment records the requested workspace and name and returns the fixture result`() = runTest {
        val fake = FakePostmanApiService()
        fake.createEnvironmentResult = Result.success(Environment(id = "env-9", name = "QA", uid = "env-9-uid"))

        val result = fake.createEnvironment("ws-1", "QA")

        assertEquals("ws-1", fake.lastCreatedEnvironmentWorkspaceId)
        assertEquals("QA", fake.lastCreatedEnvironmentName)
        assertEquals(Environment(id = "env-9", name = "QA", uid = "env-9-uid"), result.getOrThrow())
    }

    @Test
    fun `getCollectionDetail returns the fixture registered for that uid`() = runTest {
        val fake = FakePostmanApiService()

        val result = fake.getCollectionDetail("col-1-uid")

        assertEquals(FakePostmanApiService.FIXTURE_COLLECTION_DETAIL_AUTH, result.getOrThrow())
    }

    @Test
    fun `getCollectionDetail fails for an unregistered uid`() = runTest {
        val fake = FakePostmanApiService()

        val result = fake.getCollectionDetail("does-not-exist")

        assertTrue(result.isFailure)
    }
}
