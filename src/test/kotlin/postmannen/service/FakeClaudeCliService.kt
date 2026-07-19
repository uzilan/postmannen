package postmannen.service

class FakeClaudeCliService : ClaudeCliService {
    var result: ChatTurnResult = ChatTurnResult(text = "ack", toolsUsed = emptyList(), errored = false, sessionId = "session-1")
    var lastPrompt: String? = null
    var lastResumeSessionId: String? = null

    override suspend fun sendMessage(prompt: String, resumeSessionId: String?): ChatTurnResult {
        lastPrompt = prompt
        lastResumeSessionId = resumeSessionId
        return result
    }
}
