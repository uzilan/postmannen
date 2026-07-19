package postmannen.service

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import postmannen.model.ClaudeStreamEvent
import kotlin.test.Test
import kotlin.test.assertTrue

@Tag("integration")
class ClaudeCliSessionImplTest {

    @Test
    fun `sendMessage streams at least one text delta for a simple prompt`() = runTest {
        val claudeOnPath = runCatching { ProcessBuilder("claude", "--version").start().waitFor() == 0 }.getOrDefault(false)
        assumeTrue(claudeOnPath, "claude CLI not found on PATH, skipping integration test")
        val apiKey = System.getenv("POSTMAN_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank(), "POSTMAN_API_KEY not set, skipping integration test")

        val session = ClaudeCliSessionImpl(apiKey)
        val events = mutableListOf<ClaudeStreamEvent>()

        val result = session.sendMessage("Reply with the single word: ack") { events.add(it) }
        session.close()

        assertTrue(result.isSuccess, "Expected success but got: ${result.exceptionOrNull()?.message}")
        assertTrue(events.any { it is ClaudeStreamEvent.TextDelta }, "Expected at least one text delta, got: $events")
    }
}
