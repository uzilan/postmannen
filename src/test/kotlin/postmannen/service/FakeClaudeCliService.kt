package postmannen.service

import postmannen.model.McpTool

class FakeClaudeCliService : ClaudeCliService {
    var result: ChatTurnResult = ChatTurnResult(text = "ack", toolsUsed = emptyList(), errored = false, sessionId = "session-1")
    var lastPrompt: String? = null
    var lastResumeSessionId: String? = null
    var availableToolsResult: Result<List<McpTool>> = Result.success(
        listOf(McpTool(name = "getCollections", description = "List collections"))
    )

    override suspend fun sendMessage(prompt: String, resumeSessionId: String?): ChatTurnResult {
        lastPrompt = prompt
        lastResumeSessionId = resumeSessionId
        return result
    }

    override suspend fun getAvailableTools(): Result<List<McpTool>> = availableToolsResult
}
