package postmannen.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import postmannen.model.ChatContext
import postmannen.model.ChatMessage
import postmannen.model.ChatState
import postmannen.model.ClaudeStreamEvent
import postmannen.service.ClaudeCliSession

class ChatViewModel(
    private val session: ClaudeCliSession,
    private val onWorkspaceMutated: () -> Unit,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private fun update(block: ChatState.() -> ChatState) = _state.update(block)

    fun sendMessage(text: String, context: ChatContext) {
        if (text.isBlank() || _state.value.sending) return
        val prompt = buildPrompt(text, context)
        update { copy(messages = messages + ChatMessage.User(text), sending = true) }

        scope.launch {
            val textBuilder = StringBuilder()
            val toolsUsed = mutableListOf<String>()
            var errored = false

            val result = session.sendMessage(prompt) { event ->
                when (event) {
                    is ClaudeStreamEvent.TextDelta -> textBuilder.append(event.text)
                    is ClaudeStreamEvent.ToolUse -> toolsUsed.add(event.name)
                    is ClaudeStreamEvent.Error -> {
                        errored = true
                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                        textBuilder.append("Error: ${event.message}")
                    }
                    ClaudeStreamEvent.TurnComplete -> Unit
                }
            }

            if (result.isFailure) {
                errored = true
                if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                textBuilder.append("Error: ${result.exceptionOrNull()?.message}")
            }

            update {
                copy(
                    messages = messages + ChatMessage.Assistant(textBuilder.toString(), toolsUsed, errored),
                    sending = false
                )
            }

            if (!errored && toolsUsed.any(::isWriteTool)) {
                onWorkspaceMutated()
            }
        }
    }

    fun close() = session.close()

    private fun buildPrompt(text: String, context: ChatContext): String {
        val contextLines = buildList {
            if (context.workspaceName != null) add("workspace: ${context.workspaceName} (${context.workspaceId})")
            if (context.highlightedLabel != null) add("highlighted: ${context.highlightedLabel}")
        }
        return if (contextLines.isEmpty()) text else "Context — ${contextLines.joinToString("; ")}\n\n$text"
    }

    companion object {
        private val WRITE_TOOL_PREFIXES = listOf("create_", "update_", "delete_", "put_", "patch_")

        fun isWriteTool(name: String): Boolean = WRITE_TOOL_PREFIXES.any { name.startsWith(it) }
    }
}
