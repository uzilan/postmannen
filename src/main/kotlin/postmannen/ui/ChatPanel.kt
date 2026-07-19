package postmannen.ui

import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextBox
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import postmannen.model.ChatMessage
import postmannen.model.ChatState

class ChatPanel(
    private val onSubmit: (String) -> Unit
) : Panel(LinearLayout(Direction.VERTICAL)) {

    private val transcriptBox = TextBox("", TextBox.Style.MULTI_LINE).apply {
        isReadOnly = true
    }

    // Lanterna's default TextBox.handleKeyStroke claims Enter (moves focus) before it
    // reaches the window's onUnhandledInput — same issue documented for
    // NamePromptOverlay.nameBox and EnvironmentGridPanel's Ctrl+N/Ctrl+D. Intercept here.
    private val inputBox = object : TextBox() {
        override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
            if (keyStroke.keyType == KeyType.Enter) {
                val submitted = text
                if (submitted.isNotBlank()) {
                    onSubmit(submitted)
                    text = ""
                }
                return Interactable.Result.HANDLED
            }
            return super.handleKeyStroke(keyStroke)
        }
    }

    private var lastMessages: List<ChatMessage> = emptyList()
    private var lastWidth: Int = -1

    init {
        addComponent(Label("Chat"))
        // Without Fill+CanGrow, transcriptBox only ever takes its own tiny preferred
        // size (effectively 1 visible row) no matter how much vertical space this
        // panel's container actually has — the reply text is there in the state, it's
        // just clipped out of view.
        addComponent(transcriptBox, LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow))
        addComponent(inputBox, LinearLayout.createLayoutData(LinearLayout.Alignment.Fill))
        (transcriptBox.renderer as? TextBox.DefaultTextBoxRenderer)?.setUnusedSpaceCharacter(' ')
        (inputBox.renderer as? TextBox.DefaultTextBoxRenderer)?.setUnusedSpaceCharacter(' ')
    }

    fun applyState(state: ChatState) {
        val width = transcriptBox.size.columns
        if (state.messages != lastMessages || width != lastWidth) {
            lastMessages = state.messages
            lastWidth = width
            transcriptBox.text = wrapText(render(state.messages), width)
            val lines = transcriptBox.lineCount
            if (lines > 0) transcriptBox.setCaretPosition(lines - 1, 0)
        }
        inputBox.isReadOnly = state.sending
    }

    fun focusInput() {
        inputBox.takeFocus()
    }

    private fun render(messages: List<ChatMessage>): String =
        messages.joinToString("\n\n") { message ->
            when (message) {
                is ChatMessage.User -> "You: ${message.text}"
                is ChatMessage.Assistant -> buildString {
                    append("Claude: ${message.text}")
                    message.toolsUsed.forEach { append("\n  → used tool: $it") }
                    if (message.errored) append("\n  [error]")
                }
            }
        }
}
