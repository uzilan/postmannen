package postmannen.ui

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.WindowListenerAdapter
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import postmannen.model.AppState
import postmannen.model.Collection
import postmannen.model.Environment
import postmannen.model.Tab
import postmannen.model.Workspace
import postmannen.viewmodel.AppViewModel
import java.util.concurrent.atomic.AtomicBoolean

class App(
    private val gui: MultiWindowTextGUI,
    private val screen: Screen,
    private val viewModel: AppViewModel,
    private val scope: CoroutineScope
) {
    private val workspaceDropdown = WorkspaceDropdown()
    private val tabBar = Label("")

    @Volatile private var onSpaceKey: (() -> Unit)? = null

    private val itemListBox = object : ActionListBox() {
        override fun handleKeyStroke(key: KeyStroke): Interactable.Result {
            if (key.keyType == KeyType.Character && key.character == ' ') {
                onSpaceKey?.invoke()
                return Interactable.Result.HANDLED
            }
            return super.handleKeyStroke(key)
        }
    }.apply { setListItemRenderer(itemHighlightRenderer) }

    private val statusBar = StatusBar()
    private val hintLabel = Label("")
    private val window = BasicWindow("postmannen")
    private var comparisonWindow: ComparisonOverlay? = null

    fun run() {
        window.setHints(setOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))

        val root = Panel(BorderLayout())
        root.addComponent(workspaceDropdown, BorderLayout.Location.TOP)

        val centerPanel = Panel(LinearLayout(Direction.VERTICAL))
        centerPanel.addComponent(tabBar)
        centerPanel.addComponent(itemListBox)
        root.addComponent(centerPanel.withBorder(Borders.singleLine()), BorderLayout.Location.CENTER)

        val bottomPanel = Panel(LinearLayout(Direction.VERTICAL))
        bottomPanel.addComponent(statusBar)
        bottomPanel.addComponent(hintLabel)
        root.addComponent(bottomPanel, BorderLayout.Location.BOTTOM)

        window.component = root

        var applyingState = false
        workspaceDropdown.addListener { selectedIndex, _, changedByUserInteraction ->
            if (changedByUserInteraction && !applyingState) {
                viewModel.selectWorkspace(selectedIndex)
            }
        }

        onSpaceKey = {
            val state = viewModel.state.value
            if (state.activeTab == Tab.ENVIRONMENTS) {
                state.environments.getOrNull(itemListBox.selectedIndex)?.let {
                    viewModel.toggleEnvironmentSelection(it.id)
                }
            }
        }

        window.addWindowListener(object : WindowListenerAdapter() {
            override fun onUnhandledInput(basePane: Window, keyStroke: KeyStroke, hasBeenHandled: AtomicBoolean) {
                when {
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'q' -> {
                        window.close()
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.ArrowLeft || keyStroke.keyType == KeyType.ArrowRight -> {
                        val next = if (viewModel.state.value.activeTab == Tab.COLLECTIONS) Tab.ENVIRONMENTS else Tab.COLLECTIONS
                        viewModel.setActiveTab(next)
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'c' && viewModel.state.value.activeTab == Tab.ENVIRONMENTS -> {
                        viewModel.openComparison()
                        hasBeenHandled.set(true)
                    }
                }
            }
        })

        scope.launch {
            viewModel.state.collect { state ->
                synchronized(gui) {
                    applyingState = true
                    applyState(state)
                    applyingState = false
                    try { gui.updateScreen() } catch (_: Exception) {}
                }
            }
        }

        gui.addWindowAndWait(window)
    }

    private var lastWorkspaces: List<Workspace> = emptyList()
    private var lastCollections: List<Collection> = emptyList()
    private var lastEnvironments: List<Environment> = emptyList()
    private var lastActiveTab: Tab? = null
    private var lastSelectedEnvironmentIds: Set<String> = emptySet()

    private fun applyState(state: AppState) {
        if (state.workspaces != lastWorkspaces) {
            lastWorkspaces = state.workspaces
            workspaceDropdown.clearItems()
            state.workspaces.forEach { workspaceDropdown.addItem(it) }
        }
        if (state.workspaces.isNotEmpty()) {
            workspaceDropdown.selectedIndex = state.selectedWorkspaceIndex.coerceIn(0, state.workspaces.size - 1)
        }

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

        hintLabel.text = if (state.activeTab == Tab.ENVIRONMENTS) {
            "  [space] select  [c] compare (${state.selectedEnvironmentIds.size})  [←][→] tabs  q-quit"
        } else {
            "  [←][→] tabs  q-quit"
        }

        statusBar.setText(if (state.loading) "Loading..." else state.statusMessage)

        if (state.comparisonVisible && comparisonWindow == null) {
            val win = ComparisonOverlay(state.comparisonDetails) { viewModel.closeComparison() }
            comparisonWindow = win
            gui.addWindow(win)
        } else if (!state.comparisonVisible && comparisonWindow != null) {
            comparisonWindow?.close()
            comparisonWindow = null
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
