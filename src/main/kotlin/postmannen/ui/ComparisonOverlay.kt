package postmannen.ui

import com.googlecode.lanterna.TerminalSize
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

// Lanterna's GridLayout has no minimum-width protection when the total row
// width exceeds the terminal: it shrinks every column by 1, round-robin,
// including the checkbox's fixed 3-wide "[ ]" — so this must stay small
// enough that (key column + N * (3 + this)) rarely exceeds the terminal
// width, or the checkbox column gets eaten into and its "]" disappears.
private const val VALUE_COLUMN_WIDTH = 18
private const val KEY_COLUMN_WIDTH = 18

class ComparisonOverlay(
    initialDetails: List<EnvironmentDetail>,
    private val onValueChanged: (environmentUid: String, key: String, newValue: String) -> Unit,
    private val onEnabledToggled: (environmentUid: String, key: String) -> Unit,
    private val onKeyRenamed: (oldKey: String, newKey: String) -> Unit,
    private val onDismiss: () -> Unit
) : BasicWindow("Compare Environments") {

    private val checkBoxes = mutableMapOf<Pair<String, String>, CheckBox>()
    private val textBoxes = mutableMapOf<Pair<String, String>, TextBox>()
    private val keyTextBoxes = mutableMapOf<String, TextBox>()

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
            val keyTextBox = object : TextBox(key) {
                private var textOnFocus = text
                override fun afterEnterFocus(direction: Interactable.FocusChangeDirection, previouslyInFocus: Interactable?) {
                    textOnFocus = text
                    super.afterEnterFocus(direction, previouslyInFocus)
                }
                override fun afterLeaveFocus(direction: Interactable.FocusChangeDirection, nextInFocus: Interactable?) {
                    if (text != textOnFocus) {
                        onKeyRenamed(key, text)
                    }
                    super.afterLeaveFocus(direction, nextInFocus)
                }
            }
            (keyTextBox.renderer as? TextBox.DefaultTextBoxRenderer)?.setUnusedSpaceCharacter(' ')
            keyTextBox.preferredSize = TerminalSize(KEY_COLUMN_WIDTH, 1)
            keyTextBox.setCaretPosition(0, 0)
            keyTextBoxes[key] = keyTextBox
            panel.addComponent(keyTextBox)
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
                (textBox.renderer as? TextBox.DefaultTextBoxRenderer)?.setUnusedSpaceCharacter(' ')
                textBox.preferredSize = TerminalSize(VALUE_COLUMN_WIDTH, 1)
                textBox.setCaretPosition(0, 0)
                textBoxes[cellKey] = textBox
                panel.addComponent(textBox)
            }
        }

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
                textBoxes[cellKey]?.let { box -> if (!box.isFocused && box.text != value.value) box.text = value.value }
                checkBoxes[cellKey]?.let { box -> if (!box.isFocused && box.isChecked != value.enabled) box.isChecked = value.enabled }
            }
        }
        // A key TextBox is tracked by its original construction-time key. This app
        // never truly deletes a key (only adds or renames one), so if that original
        // key still appears anywhere in the current details, any rename attempt for
        // it either never happened or failed and was rolled back — reset the display
        // back to the original. If it no longer appears anywhere, the rename
        // succeeded and the box already shows what the user typed — leave it alone.
        keyTextBoxes.forEach { (originalKey, box) ->
            val stillExists = details.any { detail -> detail.values.any { it.key == originalKey } }
            if (stillExists && !box.isFocused && box.text != originalKey) {
                box.text = originalKey
            }
        }
    }
}
