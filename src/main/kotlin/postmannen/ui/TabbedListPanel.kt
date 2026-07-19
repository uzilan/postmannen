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
import postmannen.model.CollectionDetail
import postmannen.model.CollectionNode
import postmannen.model.Environment
import postmannen.model.Tab

class TabbedListPanel : Panel(LinearLayout(Direction.VERTICAL)) {
    val tabBar = Label("")

    @Volatile var onSpaceKey: (() -> Unit)? = null
    @Volatile var onEnterKey: (() -> Unit)? = null
    @Volatile var onTabKey: (() -> Unit)? = null
    @Volatile var onArrowLeftKey: (() -> Unit)? = null
    @Volatile var onArrowRightKey: (() -> Unit)? = null
    @Volatile var onArrowUpAtTop: (() -> Unit)? = null

    // Arrow-key navigation moves Lanterna's internal selectedIndex directly,
    // with no listener hook of its own — so anything outside this class that
    // depends on "which row is highlighted" (the detail panel) would go stale
    // on plain up/down movement unless notified explicitly here.
    @Volatile var onSelectionMaybeChanged: (() -> Unit)? = null

    private val itemListBox = object : ActionListBox() {
        override fun handleKeyStroke(key: KeyStroke): Interactable.Result {
            if (key.keyType == KeyType.Character && key.character == ' ') {
                onSpaceKey?.invoke()
                return Interactable.Result.HANDLED
            }
            if (key.keyType == KeyType.Enter) {
                onEnterKey?.invoke()
                return Interactable.Result.HANDLED
            }
            if (key.keyType == KeyType.Tab) {
                onTabKey?.invoke()
                return Interactable.Result.HANDLED
            }
            // Lanterna's window claims unhandled Arrow keys for its own built-in
            // spatial focus-shift (jumping to whatever's positioned left/right of
            // this list — the column splitter, detail panel) before onUnhandledInput
            // ever sees them, same gotcha as the Tab key above. Must intercept here.
            if (key.keyType == KeyType.ArrowLeft) {
                onArrowLeftKey?.invoke()
                return Interactable.Result.HANDLED
            }
            if (key.keyType == KeyType.ArrowRight) {
                onArrowRightKey?.invoke()
                return Interactable.Result.HANDLED
            }
            // Pressing Up already at the top row moves focus back to the workspace
            // dropdown, mirroring how it's the first thing focused on startup.
            if (key.keyType == KeyType.ArrowUp && selectedIndex <= 0) {
                onArrowUpAtTop?.invoke()
                return Interactable.Result.HANDLED
            }
            val result = super.handleKeyStroke(key)
            onSelectionMaybeChanged?.invoke()
            return result
        }
    }.apply { setListItemRenderer(itemHighlightRenderer) }

    val selectedIndex: Int get() = itemListBox.selectedIndex

    fun focusList() {
        itemListBox.takeFocus()
    }

    private data class Row(val nodeId: String, val label: String)

    private var collectionRows: List<Row> = emptyList()

    val selectedNodeId: String? get() = collectionRows.getOrNull(itemListBox.selectedIndex)?.nodeId

    init {
        addComponent(itemListBox)
    }

    private var lastCollections: List<Collection> = emptyList()
    private var lastCollectionDetails: List<CollectionDetail> = emptyList()
    private var lastCollapsedNodeIds: Set<String> = emptySet()
    private var lastEnvironments: List<Environment> = emptyList()
    private var lastActiveTab: Tab? = null
    private var lastSelectedEnvironmentIds: Set<String> = emptySet()
    private var lastSelectedWorkspaceIndex: Int = -1

    fun applyState(state: AppState) {
        tabBar.text = buildTabBar(state.activeTab)

        val itemsChanged = state.collections != lastCollections ||
            state.collectionDetails != lastCollectionDetails ||
            state.collapsedNodeIds != lastCollapsedNodeIds ||
            state.environments != lastEnvironments
        val tabChanged = state.activeTab != lastActiveTab
        val workspaceChanged = state.selectedWorkspaceIndex != lastSelectedWorkspaceIndex
        val selectionChanged = state.selectedEnvironmentIds != lastSelectedEnvironmentIds
        if (itemsChanged || tabChanged || workspaceChanged || selectionChanged) {
            val previousIndex = itemListBox.selectedIndex
            lastCollections = state.collections
            lastCollectionDetails = state.collectionDetails
            lastCollapsedNodeIds = state.collapsedNodeIds
            lastEnvironments = state.environments
            lastActiveTab = state.activeTab
            lastSelectedEnvironmentIds = state.selectedEnvironmentIds
            lastSelectedWorkspaceIndex = state.selectedWorkspaceIndex
            itemListBox.clearItems()
            val labels = if (state.activeTab == Tab.COLLECTIONS) {
                collectionRows = flatten(state.collections, state.collectionDetails, state.collapsedNodeIds)
                collectionRows.map { it.label }
            } else {
                collectionRows = emptyList()
                state.environments.map { env ->
                    val checkbox = if (env.id in state.selectedEnvironmentIds) "[x]" else "[ ]"
                    "$checkbox ${env.name}"
                }
            }
            labels.forEach { label -> itemListBox.addItem(label) {} }
            if (!tabChanged && !workspaceChanged && labels.isNotEmpty()) {
                itemListBox.selectedIndex = previousIndex.coerceIn(0, labels.size - 1)
            }
        }
    }

    private fun buildTabBar(active: Tab): String {
        val collectionsLabel = if (active == Tab.COLLECTIONS) "[Collections]" else " Collections "
        val environmentsLabel = if (active == Tab.ENVIRONMENTS) "[Environments]" else " Environments "
        return " $collectionsLabel  $environmentsLabel"
    }

    // Walks state.collections (always known immediately) rather than
    // state.collectionDetails (populated asynchronously, per-collection) so a
    // collection whose tree hasn't loaded yet — or whose fetch failed — still
    // shows up as a row, just with a blank arrow slot (same rendering as a
    // leaf request) instead of vanishing from the list.
    private fun flatten(collections: List<Collection>, details: List<CollectionDetail>, collapsed: Set<String>): List<Row> {
        val detailsByUid = details.associateBy { it.uid }
        val rows = mutableListOf<Row>()
        collections.forEach { collection ->
            val id = collection.uid
            val items = detailsByUid[id]?.items ?: emptyList()
            val arrow = if (items.isEmpty()) " " else if (id in collapsed) ">" else "v"
            rows.add(Row(id, "$arrow ${collection.name}"))
            if (id !in collapsed) flattenChildren(items, id, 1, collapsed, rows)
        }
        return rows
    }

    private fun flattenChildren(nodes: List<CollectionNode>, parentId: String, depth: Int, collapsed: Set<String>, rows: MutableList<Row>) {
        nodes.forEachIndexed { i, node ->
            val id = "$parentId/$i"
            val indent = "  ".repeat(depth)
            when (node) {
                is CollectionNode.Folder -> {
                    val arrow = if (id in collapsed) ">" else "v"
                    rows.add(Row(id, "$indent$arrow ${node.name}"))
                    if (id !in collapsed) flattenChildren(node.children, id, depth + 1, collapsed, rows)
                }
                is CollectionNode.RequestItem -> rows.add(Row(id, "$indent  ${node.name}"))
            }
        }
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
