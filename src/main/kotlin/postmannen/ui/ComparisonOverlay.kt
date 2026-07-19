package postmannen.ui

import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.CheckBox
import com.googlecode.lanterna.gui2.GridLayout
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextBox
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import postmannen.model.EnvironmentDetail
import java.util.concurrent.atomic.AtomicBoolean

class ComparisonOverlay(
    initialDetails: List<EnvironmentDetail>,
    private val onValueChanged: (environmentUid: String, key: String, newValue: String) -> Unit,
    private val onEnabledToggled: (environmentUid: String, key: String) -> Unit,
    private val onDismiss: () -> Unit
) : BasicWindow("Compare Environments") {

    private val checkBoxes = mutableMapOf<Pair<String, String>, CheckBox>()
    private val textBoxes = mutableMapOf<Pair<String, String>, TextBox>()

    init {
        setHints(setOf(Window.Hint.CENTERED))

        val keys = initialDetails.flatMap { detail -> detail.values.map { it.key } }.toSortedSet()
        val columns = 1 + initialDetails.size * 2
        val panel = Panel(GridLayout(columns))

        panel.addComponent(Label(""))
        initialDetails.forEach { detail ->
            val nameLabel = Label(detail.name)
            nameLabel.layoutData = GridLayout.createLayoutData(
                GridLayout.Alignment.BEGINNING, GridLayout.Alignment.CENTER, false, false, 2, 1
            )
            panel.addComponent(nameLabel)
        }

        keys.forEach { key ->
            panel.addComponent(Label(key))
            initialDetails.forEach { detail ->
                val existing = detail.values.firstOrNull { it.key == key }
                val cellKey = detail.uid to key

                val checkBox = CheckBox()
                checkBox.isChecked = existing?.enabled ?: false
                checkBox.addListener { _ -> onEnabledToggled(detail.uid, key) }
                checkBoxes[cellKey] = checkBox
                panel.addComponent(checkBox)

                val textBox = object : TextBox(existing?.value ?: "") {
                    private var textOnFocus = text
                    override fun afterEnterFocus(direction: Interactable.FocusChangeDirection, previouslyInFocus: Interactable?) {
                        textOnFocus = text
                        super.afterEnterFocus(direction, previouslyInFocus)
                    }
                    override fun afterLeaveFocus(direction: Interactable.FocusChangeDirection, nextInFocus: Interactable?) {
                        if (text != textOnFocus) {
                            onValueChanged(detail.uid, key, text)
                        }
                        super.afterLeaveFocus(direction, nextInFocus)
                    }
                }
                textBoxes[cellKey] = textBox
                panel.addComponent(textBox)
            }
        }

        panel.addComponent(Label(""))
        val footer = Label("[esc] close")
        footer.layoutData = GridLayout.createLayoutData(
            GridLayout.Alignment.BEGINNING, GridLayout.Alignment.CENTER, false, false, columns - 1, 1
        )
        panel.addComponent(footer)

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

    fun applyDetails(details: List<EnvironmentDetail>) {
        details.forEach { detail ->
            detail.values.forEach { value ->
                val cellKey = detail.uid to value.key
                textBoxes[cellKey]?.let { box -> if (!box.isFocused) box.text = value.value }
                checkBoxes[cellKey]?.let { box -> if (!box.isFocused) box.isChecked = value.enabled }
            }
        }
    }
}
