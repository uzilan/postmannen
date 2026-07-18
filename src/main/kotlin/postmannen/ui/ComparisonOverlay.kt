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
import postmannen.model.EnvironmentDetail
import java.util.concurrent.atomic.AtomicBoolean

private const val MISSING_MARKER = "⚠ missing"

class ComparisonOverlay(
    details: List<EnvironmentDetail>,
    private val onDismiss: () -> Unit
) : BasicWindow("Compare Environments") {

    init {
        setHints(setOf(Window.Hint.CENTERED))
        val panel = Panel(LinearLayout(Direction.VERTICAL))

        val keys = details.flatMap { it.values.keys }.toSortedSet()
        val columnWidths = details.map { env ->
            val widest = (listOf(env.name.length, MISSING_MARKER.length) + env.values.values.map { it.length }).max()
            widest + 2
        }

        val headerLine = keys.maxOfOrNull { it.length }.let { keyColumnWidth ->
            buildString {
                append("".padEnd((keyColumnWidth ?: 0) + 2))
                details.forEachIndexed { i, env -> append(env.name.padEnd(columnWidths[i])) }
            }
        }
        panel.addComponent(Label(headerLine))

        val keyColumnWidth = (keys.maxOfOrNull { it.length } ?: 0) + 2
        keys.forEach { key ->
            val line = buildString {
                append(key.padEnd(keyColumnWidth))
                details.forEachIndexed { i, env ->
                    val cell = env.values[key] ?: MISSING_MARKER
                    append(cell.padEnd(columnWidths[i]))
                }
            }
            panel.addComponent(Label(line))
        }

        panel.addComponent(Label(""))
        panel.addComponent(Label("[esc] close"))

        addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, key: KeyStroke, hasBeenHandled: AtomicBoolean) {
                if (key.keyType == KeyType.Escape) {
                    onDismiss()
                    hasBeenHandled.set(true)
                }
            }
        })

        component = panel
    }
}
