package postmannen.ui

import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextBox
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.atomic.AtomicBoolean

class NamePromptOverlay(
    private val onSubmit: (String) -> Unit,
    private val onCancel: () -> Unit
) : BasicWindow("New Environment") {

    // Lanterna's default TextBox.handleKeyStroke claims Enter (moves focus)
    // and never lets it reach the window's onUnhandledInput while the box
    // has focus (which it does by default here) — same issue documented in
    // ComparisonOverlay for Ctrl+N/Ctrl+D. Intercept Enter/Escape here first.
    private val nameBox = object : TextBox() {
        override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
            when (keyStroke.keyType) {
                KeyType.Enter -> { onSubmit(text); return Interactable.Result.HANDLED }
                KeyType.Escape -> { onCancel(); return Interactable.Result.HANDLED }
                else -> {}
            }
            return super.handleKeyStroke(keyStroke)
        }
    }

    init {
        setHints(setOf(Window.Hint.CENTERED))
        val panel = Panel(LinearLayout(Direction.VERTICAL))
        panel.addComponent(Label("Environment name:"))
        panel.addComponent(nameBox)
        panel.addComponent(Label(""))
        panel.addComponent(Label("[enter] create   [esc] cancel"))

        addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, key: KeyStroke, hasBeenHandled: AtomicBoolean) {
                when (key.keyType) {
                    KeyType.Enter -> { onSubmit(nameBox.text); hasBeenHandled.set(true) }
                    KeyType.Escape -> { onCancel(); hasBeenHandled.set(true) }
                    else -> {}
                }
            }
        })

        (nameBox.renderer as? TextBox.DefaultTextBoxRenderer)?.setUnusedSpaceCharacter(' ')

        component = panel
        nameBox.takeFocus()
    }
}
