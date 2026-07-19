package postmannen.model

sealed class ClaudeStreamEvent {
    data class TextDelta(val text: String) : ClaudeStreamEvent()
    data class ToolUse(val name: String) : ClaudeStreamEvent()
    data class Error(val message: String) : ClaudeStreamEvent()
    object TurnComplete : ClaudeStreamEvent()
}
