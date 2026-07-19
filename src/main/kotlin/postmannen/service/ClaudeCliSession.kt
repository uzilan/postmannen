package postmannen.service

import postmannen.model.ClaudeStreamEvent

interface ClaudeCliSession {
    suspend fun sendMessage(prompt: String, onEvent: (ClaudeStreamEvent) -> Unit): Result<Unit>
    fun close()
}
