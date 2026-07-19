package postmannen.service

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

@Tag("integration")
class ClaudeCliServiceImplTest {

    @Test
    fun `sendMessage returns reply text for a simple prompt`() = runTest {
        val claudeOnPath = runCatching { ProcessBuilder("claude", "--version").start().waitFor() == 0 }.getOrDefault(false)
        assumeTrue(claudeOnPath, "claude CLI not found on PATH, skipping integration test")
        val apiKey = System.getenv("POSTMAN_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank(), "POSTMAN_API_KEY not set, skipping integration test")

        val service = ClaudeCliServiceImpl(apiKey)

        val result = service.sendMessage("Reply with the single word: ack", resumeSessionId = null)

        assertTrue(!result.errored, "Expected success but got errored text: ${result.text}")
        assertTrue(result.text.isNotBlank())
    }
}
