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

    private class Row(
        val originalKey: String,
        val keyBox: TextBox,
        val cellsByUid: MutableMap<String, Pair<CheckBox, TextBox>>
    )

    private val environments = initialDetails
    private val rows = mutableListOf<Row>()
    private val panel = Panel(GridLayout(1 + initialDetails.size * 2))

    init {
        setHints(setOf(Window.Hint.CENTERED))

        panel.addComponent(Label(""))
        environments.forEach { detail ->
            val nameLabel = Label(detail.name)
            nameLabel.layoutData = GridLayout.createLayoutData(
                GridLayout.Alignment.BEGINNING, GridLayout.Alignment.CENTER, false, false, 2, 1
            )
            panel.addComponent(nameLabel)
        }

        val keys = environments.flatMap { detail -> detail.values.map { it.key } }.toSortedSet()
        keys.forEach { key -> buildRow(key) }

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

    private fun buildRow(originalKey: String): Row {
        val keyBox = object : TextBox(originalKey) {
            private var textOnFocus = text
            override fun afterEnterFocus(direction: Interactable.FocusChangeDirection, previouslyInFocus: Interactable?) {
                textOnFocus = text
                super.afterEnterFocus(direction, previouslyInFocus)
            }
            override fun afterLeaveFocus(direction: Interactable.FocusChangeDirection, nextInFocus: Interactable?) {
                if (text != textOnFocus) {
                    onKeyRenamed(textOnFocus, text)
                }
                super.afterLeaveFocus(direction, nextInFocus)
            }
        }
        (keyBox.renderer as? TextBox.DefaultTextBoxRenderer)?.setUnusedSpaceCharacter(' ')
        keyBox.preferredSize = TerminalSize(KEY_COLUMN_WIDTH, 1)
        keyBox.setCaretPosition(0, 0)
        panel.addComponent(keyBox)

        val row = Row(originalKey, keyBox, mutableMapOf())
        rows.add(row)

        environments.forEach { detail ->
            val existing = detail.values.firstOrNull { it.key == originalKey }

            val checkBox = CheckBox()
            checkBox.isChecked = existing?.enabled ?: false
            checkBox.addListener { _ -> onEnabledToggled(detail.uid, row.keyBox.text) }
            panel.addComponent(checkBox)

            val valueBox = object : TextBox(existing?.value ?: "") {
                private var textOnFocus = text
                override fun afterEnterFocus(direction: Interactable.FocusChangeDirection, previouslyInFocus: Interactable?) {
                    textOnFocus = text
                    super.afterEnterFocus(direction, previouslyInFocus)
                }
                override fun afterLeaveFocus(direction: Interactable.FocusChangeDirection, nextInFocus: Interactable?) {
                    if (text != textOnFocus) {
                        onValueChanged(detail.uid, row.keyBox.text, text)
                    }
                    super.afterLeaveFocus(direction, nextInFocus)
                }
            }
            (valueBox.renderer as? TextBox.DefaultTextBoxRenderer)?.setUnusedSpaceCharacter(' ')
            valueBox.preferredSize = TerminalSize(VALUE_COLUMN_WIDTH, 1)
            valueBox.setCaretPosition(0, 0)
            panel.addComponent(valueBox)

            row.cellsByUid[detail.uid] = checkBox to valueBox
        }

        return row
    }

    fun applyDetails(details: List<EnvironmentDetail>) {
        rows.forEach { row ->
            val liveKey = row.keyBox.text
            row.cellsByUid.forEach { (uid, cell) ->
                val (checkBox, valueBox) = cell
                val value = details.firstOrNull { it.uid == uid }?.values?.firstOrNull { it.key == liveKey }
                if (value != null) {
                    if (!valueBox.isFocused && valueBox.text != value.value) valueBox.text = value.value
                    if (!checkBox.isFocused && checkBox.isChecked != value.enabled) checkBox.isChecked = value.enabled
                }
            }
            // The key box is tracked by its original construction-time key. This app
            // never truly deletes a key (only adds or renames one) as far as this class
            // is concerned in this task — if that original key still appears anywhere in
            // the current details, any rename attempt for it either never happened or
            // failed and was rolled back, so reset the display back to the original. If
            // it no longer appears anywhere, the rename succeeded and the box already
            // shows what the user typed — leave it alone.
            val stillExists = details.any { detail -> detail.values.any { it.key == row.originalKey } }
            if (stillExists && !row.keyBox.isFocused && row.keyBox.text != row.originalKey) {
                row.keyBox.text = row.originalKey
            }
        }
    }
}
