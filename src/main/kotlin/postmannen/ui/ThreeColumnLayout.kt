package postmannen.ui

import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractInteractableComponent
import com.googlecode.lanterna.gui2.Component
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.InteractableRenderer
import com.googlecode.lanterna.gui2.LayoutManager
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.MouseAction
import com.googlecode.lanterna.input.MouseActionType

private const val SPLITTER_WIDTH = 1

class ThreeColumnLayout(private val minColumnWidth: Int = 10) : LayoutManager {
    // null = auto (share of whatever space is left after other overrides); non-null = user-dragged explicit width
    private var leftOverride: Int? = null
    private var middleOverride: Int? = null
    private var rightOverride: Int? = null
    private var changed = true

    fun onSplitterDragged(splitterIndex: Int, deltaColumns: Int, totalAvailableWidth: Int) {
        if (deltaColumns == 0) return
        val widths = columnWidths(totalAvailableWidth)
        when (splitterIndex) {
            0 -> {
                val (newLeft, newMiddle) = resizeColumns(widths[0], widths[1], deltaColumns, minColumnWidth)
                leftOverride = newLeft
                middleOverride = newMiddle
            }
            1 -> {
                val (newMiddle, newRight) = resizeColumns(widths[1], widths[2], deltaColumns, minColumnWidth)
                middleOverride = newMiddle
                rightOverride = newRight
            }
        }
        changed = true
    }

    private fun columnWidths(totalAvailableWidth: Int): List<Int> {
        val usable = (totalAvailableWidth - 2 * SPLITTER_WIDTH).coerceAtLeast(0)
        val setCount = listOfNotNull(leftOverride, middleOverride, rightOverride).size
        if (setCount == 0) {
            val third = usable / 3
            return listOf(third, third, usable - 2 * third)
        }
        val knownSum = (leftOverride ?: 0) + (middleOverride ?: 0) + (rightOverride ?: 0)
        val unsetCount = 3 - setCount
        val share = if (unsetCount > 0) (usable - knownSum) / unsetCount else 0
        val left = leftOverride ?: share
        val middle = middleOverride ?: share
        val right = rightOverride ?: (usable - left - middle)
        return listOf(left.coerceAtLeast(0), middle.coerceAtLeast(0), right.coerceAtLeast(0))
    }

    override fun getPreferredSize(components: List<Component>): TerminalSize {
        val maxRows = components.maxOfOrNull { it.preferredSize.rows } ?: 0
        return TerminalSize(components.sumOf { it.preferredSize.columns }, maxRows)
    }

    override fun hasChanged(): Boolean = changed

    override fun doLayout(area: TerminalSize, components: MutableList<Component>) {
        require(components.size == 5) { "ThreeColumnLayout requires exactly 5 components: col, splitter, col, splitter, col" }
        val widths = columnWidths(area.columns)
        val heights = area.rows
        var x = 0
        val positions = listOf(widths[0], SPLITTER_WIDTH, widths[1], SPLITTER_WIDTH, widths[2])
        components.forEachIndexed { index, component ->
            val width = positions[index]
            component.position = TerminalPosition(x, 0)
            component.size = TerminalSize(width.coerceAtLeast(0), heights)
            x += width
        }
        changed = false
    }
}

class ColumnSplitter(private val layout: ThreeColumnLayout, private val splitterIndex: Int) :
    AbstractInteractableComponent<ColumnSplitter>() {

    private var dragStartColumn: Int? = null

    override fun isFocusable(): Boolean = true

    override fun createDefaultRenderer(): InteractableRenderer<ColumnSplitter> =
        object : InteractableRenderer<ColumnSplitter> {
            override fun getCursorLocation(component: ColumnSplitter): TerminalPosition? = null
            override fun getPreferredSize(component: ColumnSplitter): TerminalSize = TerminalSize(SPLITTER_WIDTH, 1)
            override fun drawComponent(graphics: TextGUIGraphics, component: ColumnSplitter) {
                graphics.setForegroundColor(TextColor.ANSI.WHITE)
                graphics.fill('│')
            }
        }

    override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
        if (keyStroke !is MouseAction) return Interactable.Result.UNHANDLED
        val column = keyStroke.position.column
        when (keyStroke.actionType) {
            MouseActionType.CLICK_DOWN -> {
                dragStartColumn = column
            }
            MouseActionType.DRAG -> {
                val start = dragStartColumn ?: return Interactable.Result.UNHANDLED
                val delta = column - start
                if (delta != 0) {
                    val parentWidth = parent?.size?.columns ?: return Interactable.Result.HANDLED
                    layout.onSplitterDragged(splitterIndex, delta, parentWidth)
                    dragStartColumn = column
                    invalidate()
                }
            }
            MouseActionType.CLICK_RELEASE -> {
                dragStartColumn = null
            }
            else -> return Interactable.Result.UNHANDLED
        }
        return Interactable.Result.HANDLED
    }
}
