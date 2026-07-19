package postmannen.ui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import postmannen.model.AppState
import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.Tab

class ItemListPanel : Panel(LinearLayout(Direction.VERTICAL)) {
    private val tabBar = Label("")

    @Volatile var onSpaceKey: (() -> Unit)? = null

    private val itemListBox = object : ActionListBox() {
        override fun handleKeyStroke(key: KeyStroke): Interactable.Result {
            if (key.keyType == KeyType.Character && key.character == ' ') {
                onSpaceKey?.invoke()
                return Interactable.Result.HANDLED
            }
            return super.handleKeyStroke(key)
        }
    }.apply { setListItemRenderer(itemHighlightRenderer) }

    val selectedIndex: Int get() = itemListBox.selectedIndex

    init {
        addComponent(tabBar)
        addComponent(itemListBox)
    }

    private var lastCollections: List<Collection> = emptyList()
    private var lastEnvironments: List<Environment> = emptyList()
    private var lastActiveTab: Tab? = null
    private var lastSelectedEnvironmentIds: Set<String> = emptySet()

    fun applyState(state: AppState) {
        tabBar.text = buildTabBar(state.activeTab)

        val itemsChanged = state.collections != lastCollections || state.environments != lastEnvironments
        val tabChanged = state.activeTab != lastActiveTab
        val selectionChanged = state.selectedEnvironmentIds != lastSelectedEnvironmentIds
        if (itemsChanged || tabChanged || selectionChanged) {
            val previousIndex = itemListBox.selectedIndex
            lastCollections = state.collections
            lastEnvironments = state.environments
            lastActiveTab = state.activeTab
            lastSelectedEnvironmentIds = state.selectedEnvironmentIds
            itemListBox.clearItems()
            val labels = if (state.activeTab == Tab.COLLECTIONS) {
                state.collections.map { it.name }
            } else {
                state.environments.map { env ->
                    val checkbox = if (env.id in state.selectedEnvironmentIds) "[x]" else "[ ]"
                    "$checkbox ${env.name}"
                }
            }
            labels.forEach { label -> itemListBox.addItem(label) {} }
            if (!tabChanged && labels.isNotEmpty()) {
                itemListBox.selectedIndex = previousIndex.coerceIn(0, labels.size - 1)
            }
        }
    }

    private fun buildTabBar(active: Tab): String {
        val collectionsLabel = if (active == Tab.COLLECTIONS) "[Collections]" else " Collections "
        val environmentsLabel = if (active == Tab.ENVIRONMENTS) "[Environments]" else " Environments "
        return " $collectionsLabel  $environmentsLabel"
    }

    companion object {
        private val itemHighlightRenderer = object : AbstractListBox.ListItemRenderer<Runnable, ActionListBox>() {
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
