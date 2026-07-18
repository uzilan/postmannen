package postmannen.ui

import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.ComboBox
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.input.KeyStroke
import postmannen.model.Workspace

class WorkspaceDropdown : ComboBox<Workspace>() {

    init {
        setRenderer(comboBoxRenderer)
    }

    // ComboBox's popup listbox is a private field with no public extension point.
    // Grab it via reflection after the popup opens so the open dropdown's selected
    // row gets the same highlight as the closed combo box, matching sdkui's look.
    override fun showPopup(keyStroke: KeyStroke) {
        super.showPopup(keyStroke)
        try {
            val popupField = ComboBox::class.java.getDeclaredField("popupWindow")
            popupField.isAccessible = true
            val popupWindow = popupField.get(this) as? BasicWindow ?: return
            val listBoxField = popupWindow.javaClass.declaredFields.first { it.type == ActionListBox::class.java }
            listBoxField.isAccessible = true
            val listBox = listBoxField.get(popupWindow) as? ActionListBox ?: return
            listBox.setListItemRenderer(highlightRenderer)
        } catch (_: Exception) {}
    }

    companion object {
        private val comboBoxRenderer = object : ComboBoxRenderer<Workspace>() {
            override fun getCursorLocation(comboBox: ComboBox<Workspace>): TerminalPosition? = null
            override fun getPreferredSize(comboBox: ComboBox<Workspace>): TerminalSize {
                val longest = (0 until comboBox.itemCount).maxOfOrNull { comboBox.getItem(it).toString().length } ?: 0
                return TerminalSize(longest + 3, 1)
            }
            override fun drawComponent(graphics: TextGUIGraphics, comboBox: ComboBox<Workspace>) {
                val w = graphics.size.columns
                if (comboBox.isFocused) {
                    graphics.setForegroundColor(TextColor.ANSI.BLACK)
                    graphics.setBackgroundColor(TextColor.ANSI.GREEN)
                } else {
                    graphics.applyThemeStyle(comboBox.themeDefinition.normal)
                }
                graphics.fill(' ')
                val text = if (comboBox.selectedIndex >= 0) comboBox.getItem(comboBox.selectedIndex).toString() else ""
                graphics.putString(0, 0, text.take(w - 3))
                graphics.putString(w - 3, 0, "▼")
                graphics.putString(w - 1, 0, "│")
            }
        }

        private val highlightRenderer = object : AbstractListBox.ListItemRenderer<Runnable, ActionListBox>() {
            override fun drawItem(graphics: TextGUIGraphics, lb: ActionListBox, index: Int, item: Runnable, selected: Boolean, focused: Boolean) {
                val label = getLabel(lb, index, item)
                val width = graphics.size.columns
                val text = label.take(width).padEnd(width)
                if (selected && focused) {
                    graphics.setForegroundColor(TextColor.ANSI.BLACK)
                    graphics.setBackgroundColor(TextColor.ANSI.GREEN)
                    graphics.putString(0, 0, text)
                } else {
                    super.drawItem(graphics, lb, index, item, selected, focused)
                }
            }
        }
    }
}
