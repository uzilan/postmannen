package postmannen.server

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import postmannen.service.ClaudeCliService

@Serializable
data class ChatContextDto(
    val workspaceName: String? = null,
    val workspaceId: String? = null,
    val highlightedLabel: String? = null
)

@Serializable
data class ChatRequest(
    val prompt: String,
    val resumeSessionId: String? = null,
    val context: ChatContextDto = ChatContextDto()
)

@Serializable
data class ChatResponse(
    val reply: String,
    val toolsUsed: List<String>,
    val errored: Boolean,
    val sessionId: String?
)

fun Route.chatRoutes(service: ClaudeCliService) {
    post("/api/chat") {
        val request = call.receive<ChatRequest>()
        val fullPrompt = buildPrompt(request.prompt, request.context)
        val result = service.sendMessage(fullPrompt, request.resumeSessionId)
        call.respond(
            ChatResponse(
                reply = result.text,
                toolsUsed = result.toolsUsed,
                errored = result.errored,
                sessionId = result.sessionId
            )
        )
    }
}

private fun buildPrompt(text: String, context: ChatContextDto): String {
    val contextLines = buildList {
        if (context.workspaceName != null) add("workspace: ${context.workspaceName} (${context.workspaceId})")
        if (context.highlightedLabel != null) add("highlighted: ${context.highlightedLabel}")
    }
    return if (contextLines.isEmpty()) text else "Context — ${contextLines.joinToString("; ")}\n\n$text"
}
