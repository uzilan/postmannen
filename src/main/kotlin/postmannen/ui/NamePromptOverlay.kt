package postmannen.ui

import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Direction
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

    private val nameBox = TextBox()

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

        component = panel
        nameBox.takeFocus()
    }
}
