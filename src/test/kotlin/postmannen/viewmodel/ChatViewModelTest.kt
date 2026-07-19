package postmannen.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import postmannen.model.ChatContext
import postmannen.model.ChatMessage
import postmannen.model.ClaudeStreamEvent
import postmannen.service.FakeClaudeCliSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @Test
    fun `sendMessage appends user and assistant messages and clears sending`() = runTest {
        val fake = FakeClaudeCliSession().apply {
            events = listOf(
                ClaudeStreamEvent.TextDelta("Hi "),
                ClaudeStreamEvent.TextDelta("there"),
                ClaudeStreamEvent.TurnComplete
            )
        }
        var mutated = false
        val vm = ChatViewModel(fake, onWorkspaceMutated = { mutated = true }, scope = this)

        vm.sendMessage("hello", ChatContext())
        advanceUntilIdle()

        assertEquals(2, vm.state.value.messages.size)
        assertEquals(ChatMessage.User("hello"), vm.state.value.messages[0])
        assertEquals(ChatMessage.Assistant("Hi there", emptyList(), false), vm.state.value.messages[1])
        assertFalse(vm.state.value.sending)
        assertFalse(mutated)
    }

    @Test
    fun `sendMessage prepends context when provided`() = runTest {
        val fake = FakeClaudeCliSession()
        val vm = ChatViewModel(fake, onWorkspaceMutated = {}, scope = this)

        vm.sendMessage("add a key", ChatContext(workspaceName = "Engineering", workspaceId = "ws-1", highlightedLabel = "environment: Staging"))
        advanceUntilIdle()

        val prompt = fake.lastPrompt!!
        assertTrue(prompt.contains("Engineering"))
        assertTrue(prompt.contains("ws-1"))
        assertTrue(prompt.contains("environment: Staging"))
        assertTrue(prompt.endsWith("add a key"))
    }

    @Test
    fun `write tool usage triggers onWorkspaceMutated`() = runTest {
        val fake = FakeClaudeCliSession().apply {
            events = listOf(ClaudeStreamEvent.ToolUse("update_environment"), ClaudeStreamEvent.TurnComplete)
        }
        var mutated = false
        val vm = ChatViewModel(fake, onWorkspaceMutated = { mutated = true }, scope = this)

        vm.sendMessage("change base_url", ChatContext())
        advanceUntilIdle()

        assertTrue(mutated)
        assertEquals(listOf("update_environment"), (vm.state.value.messages[1] as ChatMessage.Assistant).toolsUsed)
    }

    @Test
    fun `read-only tool usage does not trigger onWorkspaceMutated`() = runTest {
        val fake = FakeClaudeCliSession().apply {
            events = listOf(ClaudeStreamEvent.ToolUse("get_environment"), ClaudeStreamEvent.TurnComplete)
        }
        var mutated = false
        val vm = ChatViewModel(fake, onWorkspaceMutated = { mutated = true }, scope = this)

        vm.sendMessage("what's in staging", ChatContext())
        advanceUntilIdle()

        assertFalse(mutated)
    }

    @Test
    fun `session failure marks the assistant message errored and clears sending`() = runTest {
        val fake = FakeClaudeCliSession().apply {
            result = Result.failure(RuntimeException("claude exited with code 1"))
        }
        val vm = ChatViewModel(fake, onWorkspaceMutated = {}, scope = this)

        vm.sendMessage("hello", ChatContext())
        advanceUntilIdle()

        val assistant = vm.state.value.messages[1] as ChatMessage.Assistant
        assertTrue(assistant.errored)
        assertTrue(assistant.text.contains("claude exited with code 1"))
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun `sendMessage is a no-op while a turn is already in flight`() = runTest {
        val fake = FakeClaudeCliSession().apply {
            events = listOf(ClaudeStreamEvent.TextDelta("first reply"), ClaudeStreamEvent.TurnComplete)
        }
        val vm = ChatViewModel(fake, onWorkspaceMutated = {}, scope = this)

        vm.sendMessage("first", ChatContext())
        vm.sendMessage("second", ChatContext())
        advanceUntilIdle()

        assertEquals(2, vm.state.value.messages.size)
        assertEquals(ChatMessage.User("first"), vm.state.value.messages[0])
        assertEquals("first", fake.lastPrompt)
    }

    @Test
    fun `blank text is ignored`() = runTest {
        val fake = FakeClaudeCliSession()
        val vm = ChatViewModel(fake, onWorkspaceMutated = {}, scope = this)

        vm.sendMessage("   ", ChatContext())
        advanceUntilIdle()

        assertTrue(vm.state.value.messages.isEmpty())
    }
}
