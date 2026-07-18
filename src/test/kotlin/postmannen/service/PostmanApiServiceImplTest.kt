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
