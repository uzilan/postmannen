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
    private val tabbedListPanel = TabbedListPanel()
    private val detailPanel = DetailPanel()
    private val statusBar = StatusBar()
    private val hintLabel = Label("")
    private val window = BasicWindow("postmannen")
    private var comparisonWindow: ComparisonOverlay? = null
    private var comparisonWindowUids: Set<String> = emptySet()
    private var namePromptWindow: NamePromptOverlay? = null

    fun run() {
        window.setHints(setOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS))

        val root = Panel(BorderLayout())
        val topPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        topPanel.addComponent(Label("Workspace:"))
        topPanel.addComponent(workspaceDropdown)
        root.addComponent(topPanel, BorderLayout.Location.TOP)
        val centerPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        centerPanel.addComponent(tabbedListPanel.withBorder(Borders.singleLine()))
        centerPanel.addComponent(detailPanel.withBorder(Borders.singleLine()))
        root.addComponent(centerPanel, BorderLayout.Location.CENTER)

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

        tabbedListPanel.onSpaceKey = {
            val state = viewModel.state.value
            if (state.activeTab == Tab.ENVIRONMENTS) {
                state.environments.getOrNull(tabbedListPanel.selectedIndex)?.let {
                    viewModel.toggleEnvironmentSelection(it.id)
                }
            }
        }

        tabbedListPanel.onEnterKey = {
            if (viewModel.state.value.activeTab == Tab.COLLECTIONS) {
                tabbedListPanel.selectedNodeId?.let { viewModel.toggleNodeCollapsed(it) }
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
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'v' && viewModel.state.value.activeTab == Tab.ENVIRONMENTS -> {
                        viewModel.state.value.environments.getOrNull(tabbedListPanel.selectedIndex)?.let {
                            viewModel.viewEnvironment(it.id)
                        }
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'c' && viewModel.state.value.activeTab == Tab.ENVIRONMENTS -> {
                        viewModel.openComparison()
                        hasBeenHandled.set(true)
                    }
                    keyStroke.keyType == KeyType.Character && keyStroke.character == 'n' && keyStroke.isCtrlDown &&
                        viewModel.state.value.activeTab == Tab.ENVIRONMENTS && namePromptWindow == null &&
                        !viewModel.state.value.comparisonVisible -> {
                        openNamePrompt()
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

    private fun openNamePrompt() {
        val win = NamePromptOverlay(
            onSubmit = { name ->
                namePromptWindow?.close()
                namePromptWindow = null
                viewModel.createEnvironment(name)
            },
            onCancel = {
                namePromptWindow?.close()
                namePromptWindow = null
            }
        )
        namePromptWindow = win
        gui.addWindow(win)
    }

    private fun applyState(state: AppState) {
        if (state.workspaces != lastWorkspaces) {
            lastWorkspaces = state.workspaces
            workspaceDropdown.clearItems()
            state.workspaces.forEach { workspaceDropdown.addItem(it) }
        }
        if (state.workspaces.isNotEmpty()) {
            workspaceDropdown.selectedIndex = state.selectedWorkspaceIndex.coerceIn(0, state.workspaces.size - 1)
        }

        tabbedListPanel.applyState(state)

        val highlightedId = tabbedListPanel.selectedNodeId
        val highlightedVariables = state.collections.firstOrNull { it.uid == highlightedId }
            ?.let { collection -> state.collectionDetails.firstOrNull { it.uid == collection.uid }?.variables }
            ?: emptyList()
        detailPanel.applyVariables(highlightedVariables)

        hintLabel.text = when {
            state.comparisonVisible -> "  [esc] close  ^N add key  ^D delete key"
            state.activeTab == Tab.ENVIRONMENTS -> "  [space] select  [v] view  [c] compare (${state.selectedEnvironmentIds.size})  ^N new  [←][→] tabs  q-quit"
            state.activeTab == Tab.COLLECTIONS -> "  [enter] expand/collapse  [←][→] tabs  q-quit"
            else -> "  [←][→] tabs  q-quit"
        }

        statusBar.setText(if (state.loading) "Loading..." else state.statusMessage)

        val comparisonUids = state.comparisonDetails.map { it.uid }.toSet()
        if (state.comparisonVisible && (comparisonWindow == null || comparisonUids != comparisonWindowUids)) {
            // The overlay's columns/rows are fixed at construction time from
            // initialDetails (see ComparisonOverlay.applyDetails's doc comment) — if
            // the set of environments being shown changes (e.g. [v]iewing a different
            // single environment right after a [c]ompare), patching in place would
            // leave stale columns/keys from the environments no longer in view. Rebuild
            // the window whenever the uid set changes, not just on the visibility edge.
            comparisonWindow?.close()
            val win = ComparisonOverlay(
                gui = gui,
                initialDetails = state.comparisonDetails,
                onValueChanged = { uid, key, newValue -> viewModel.updateEnvironmentValue(uid, key, newValue) },
                onEnabledToggled = { uid, key -> viewModel.toggleEnvironmentValueEnabled(uid, key) },
                onKeyRenamed = { oldKey, newKey -> viewModel.renameEnvironmentKey(oldKey, newKey) },
                onKeyDeleted = { key -> viewModel.deleteEnvironmentKey(key) },
                onDismiss = { viewModel.closeComparison() }
            )
            comparisonWindow = win
            comparisonWindowUids = comparisonUids
            gui.addWindow(win)
        } else if (!state.comparisonVisible && comparisonWindow != null) {
            comparisonWindow?.close()
            comparisonWindow = null
            comparisonWindowUids = emptySet()
        } else if (state.comparisonVisible && comparisonWindow != null) {
            comparisonWindow?.applyDetails(state.comparisonDetails)
        }
    }
}
