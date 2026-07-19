package postmannen.ui

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import postmannen.model.ChatMessage
import postmannen.model.ChatState

class ChatPanelRenderTest {

    @Test
    fun `assistant reply renders when the panel has more than its default preferred height`() {
        val terminal = DefaultVirtualTerminal(TerminalSize(58, 18))
        val screen = TerminalScreen(terminal)
        screen.startScreen()
        val gui = MultiWindowTextGUI(screen)

        val chatPanel = ChatPanel(onSubmit = {})
        val window = BasicWindow()
        window.component = chatPanel
        // Without CanGrow on transcriptBox, it never uses space beyond its own tiny
        // preferred size, so this regression only shows up once the container is
        // bigger than that preferred size — matching the real app's full-screen layout.
        window.setFixedSize(TerminalSize(58, 18))
        gui.addWindow(window)
        gui.updateScreen()

        chatPanel.applyState(
            ChatState(
                messages = listOf(
                    ChatMessage.User("hello"),
                    ChatMessage.Assistant("Hi there!", emptyList(), false)
                ),
                sending = false
            )
        )
        gui.updateScreen()

        val rows = (0 until 18).map { row -> (0 until 58).map { col -> terminal.getCharacter(col, row).characterString }.joinToString("") }
        assertTrue(rows.any { it.contains("You: hello") }, "expected full untruncated user message in rendered output:\n${rows.joinToString("\n")}")
        assertTrue(rows.any { it.contains("Claude: Hi there!") }, "expected assistant reply in rendered output:\n${rows.joinToString("\n")}")
    }
}
