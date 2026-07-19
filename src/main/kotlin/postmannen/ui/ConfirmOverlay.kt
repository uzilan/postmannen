package postmannen.ui

import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import java.util.concurrent.atomic.AtomicBoolean

class ConfirmOverlay(
    message: String,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit
) : BasicWindow("Confirm") {
    init {
        setHints(setOf(Window.Hint.CENTERED))
        val panel = Panel(LinearLayout(Direction.VERTICAL))
        panel.addComponent(Label(message))
        panel.addComponent(Label(""))
        panel.addComponent(Label("[enter] yes   [esc] no"))

        addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, key: KeyStroke, hasBeenHandled: AtomicBoolean) {
                when (key.keyType) {
                    KeyType.Enter -> { onConfirm(); hasBeenHandled.set(true) }
                    KeyType.Escape -> { onCancel(); hasBeenHandled.set(true) }
                    else -> {}
                }
            }
        })

        component = panel
    }
}
