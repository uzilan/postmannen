package postmannen.server

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import postmannen.model.McpTool
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

@OptIn(ExperimentalKtorApi::class)
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
    }.describe {
        summary = "Send a chat message"
        description = "Shells out to the claude CLI, scoped to Postman MCP tools only"
        requestBody {
            description = "The chat prompt, optional session id to resume, and workspace/selection context"
            schema = jsonSchema<ChatRequest>()
        }
        responses {
            HttpStatusCode.OK {
                description = "The assistant's reply"
                schema = jsonSchema<ChatResponse>()
            }
        }
    }

    get("/api/chat/tools") {
        call.respondResult(service.getAvailableTools())
    }.describe {
        summary = "List the Postman MCP server's available tools"
        responses {
            HttpStatusCode.OK {
                description = "The available tools"
                schema = jsonSchema<List<McpTool>>()
            }
            HttpStatusCode.BadGateway {
                description = "Failed to query the Postman MCP server"
            }
        }
    }
}

private fun buildPrompt(text: String, context: ChatContextDto): String {
    val contextLines = buildList {
        if (context.workspaceName != null) add("workspace: ${context.workspaceName} (${context.workspaceId})")
        if (context.highlightedLabel != null) add("highlighted: ${context.highlightedLabel}")
    }
    return if (contextLines.isEmpty()) text else "Context — ${contextLines.joinToString("; ")}\n\n$text"
}
