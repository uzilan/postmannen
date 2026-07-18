package postmannen.ui

import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
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
    private val itemListBox = ActionListBox()
    private val statusBar = StatusBar()
    private val window = BasicWindow("postmannen")

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
        bottomPanel.addComponent(Label("  [←][→] tabs  q-quit"))
        root.addComponent(bottomPanel, BorderLayout.Location.BOTTOM)

        window.component = root

        var applyingState = false
        workspaceDropdown.addListener { selectedIndex, _, changedByUserInteraction ->
            if (changedByUserInteraction && !applyingState) {
                viewModel.selectWorkspace(selectedIndex)
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
        if (itemsChanged || tabChanged) {
            lastCollections = state.collections
            lastEnvironments = state.environments
            lastActiveTab = state.activeTab
            itemListBox.clearItems()
            val names = if (state.activeTab == Tab.COLLECTIONS) {
                state.collections.map { it.name }
            } else {
                state.environments.map { it.name }
            }
            names.forEach { name -> itemListBox.addItem(name) {} }
        }

        statusBar.setText(if (state.loading) "Loading..." else state.statusMessage)
    }

    private fun buildTabBar(active: Tab): String {
        val collectionsLabel = if (active == Tab.COLLECTIONS) "[Collections]" else " Collections "
        val environmentsLabel = if (active == Tab.ENVIRONMENTS) "[Environments]" else " Environments "
        return " $collectionsLabel  $environmentsLabel"
    }
}
