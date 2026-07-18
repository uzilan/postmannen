package postmannen.ui

import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.BorderLayout
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.ComboBox
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.screen.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import postmannen.model.AppState
import postmannen.model.Collection
import postmannen.model.Workspace
import postmannen.viewmodel.AppViewModel

class App(
    private val gui: MultiWindowTextGUI,
    private val screen: Screen,
    private val viewModel: AppViewModel,
    private val scope: CoroutineScope
) {
    private val workspaceDropdown = ComboBox<Workspace>()
    private val collectionListBox = ActionListBox()
    private val statusBar = StatusBar()
    private val window = BasicWindow("postmannen")

    fun run() {
        window.setHints(setOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))

        val root = Panel(BorderLayout())
        root.addComponent(workspaceDropdown, BorderLayout.Location.TOP)
        root.addComponent(
            collectionListBox.withBorder(Borders.singleLine("Collections")),
            BorderLayout.Location.CENTER
        )
        root.addComponent(statusBar, BorderLayout.Location.BOTTOM)

        window.component = root

        var applyingState = false
        workspaceDropdown.addListener { selectedIndex, _, changedByUserInteraction ->
            if (changedByUserInteraction && !applyingState) {
                viewModel.selectWorkspace(selectedIndex)
            }
        }

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

    private fun applyState(state: AppState) {
        if (state.workspaces != lastWorkspaces) {
            lastWorkspaces = state.workspaces
            workspaceDropdown.clearItems()
            state.workspaces.forEach { workspaceDropdown.addItem(it) }
        }
        if (state.workspaces.isNotEmpty()) {
            workspaceDropdown.selectedIndex = state.selectedWorkspaceIndex.coerceIn(0, state.workspaces.size - 1)
        }

        if (state.collections != lastCollections) {
            lastCollections = state.collections
            collectionListBox.clearItems()
            state.collections.forEach { collection ->
                collectionListBox.addItem(collection.name) {}
            }
        }

        statusBar.setText(if (state.loading) "Loading..." else state.statusMessage)
    }
}
