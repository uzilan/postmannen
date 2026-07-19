package postmannen.model

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false
)
