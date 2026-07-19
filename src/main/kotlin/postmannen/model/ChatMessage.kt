package postmannen.model

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Assistant(
        val text: String,
        val toolsUsed: List<String> = emptyList(),
        val errored: Boolean = false
    ) : ChatMessage()
}
