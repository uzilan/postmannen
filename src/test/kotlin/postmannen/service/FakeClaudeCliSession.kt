package postmannen.service

import postmannen.model.ClaudeStreamEvent

class FakeClaudeCliSession : ClaudeCliSession {
    var events: List<ClaudeStreamEvent> = listOf(ClaudeStreamEvent.TurnComplete)
    var result: Result<Unit> = Result.success(Unit)
    var lastPrompt: String? = null
    var closed: Boolean = false

    override suspend fun sendMessage(prompt: String, onEvent: (ClaudeStreamEvent) -> Unit): Result<Unit> {
        lastPrompt = prompt
        events.forEach(onEvent)
        return result
    }

    override fun close() {
        closed = true
    }
}
