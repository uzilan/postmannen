package postmannen.ui

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
    private val itemListPanel = ItemListPanel()
    private val statusBar = StatusBar()
    private val hintLabel = Label("")
    private val window = BasicWindow("postmannen")
    private var comparisonWindow: ComparisonOverlay? = null

    fun run() {
        window.setHints(setOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))

        val root = Panel(BorderLayout())
        root.addComponent(workspaceDropdown, BorderLayout.Location.TOP)
        root.addComponent(itemListPanel.withBorder(Borders.singleLine()), BorderLayout.Location.CENTER)

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

        itemListPanel.onSpaceKey = {
            val state = viewModel.state.value
            if (state.activeTab == Tab.ENVIRONMENTS) {
                state.environments.getOrNull(itemListPanel.selectedIndex)?.let {
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

    private fun applyState(state: AppState) {
        if (state.workspaces != lastWorkspaces) {
            lastWorkspaces = state.workspaces
            workspaceDropdown.clearItems()
            state.workspaces.forEach { workspaceDropdown.addItem(it) }
        }
        if (state.workspaces.isNotEmpty()) {
            workspaceDropdown.selectedIndex = state.selectedWorkspaceIndex.coerceIn(0, state.workspaces.size - 1)
        }

        itemListPanel.applyState(state)

        hintLabel.text = when {
            state.comparisonVisible -> "  [esc] close"
            state.activeTab == Tab.ENVIRONMENTS -> "  [space] select  [c] compare (${state.selectedEnvironmentIds.size})  [←][→] tabs  q-quit"
            else -> "  [←][→] tabs  q-quit"
        }

        statusBar.setText(if (state.loading) "Loading..." else state.statusMessage)

        if (state.comparisonVisible && comparisonWindow == null) {
            val win = ComparisonOverlay(
                initialDetails = state.comparisonDetails,
                onValueChanged = { uid, key, newValue -> viewModel.updateEnvironmentValue(uid, key, newValue) },
                onEnabledToggled = { uid, key -> viewModel.toggleEnvironmentValueEnabled(uid, key) },
                onDismiss = { viewModel.closeComparison() }
            )
            comparisonWindow = win
            gui.addWindow(win)
        } else if (!state.comparisonVisible && comparisonWindow != null) {
            comparisonWindow?.close()
            comparisonWindow = null
        } else if (state.comparisonVisible && comparisonWindow != null) {
            comparisonWindow?.applyDetails(state.comparisonDetails)
        }
    }
}
